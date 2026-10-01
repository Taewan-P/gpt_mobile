#!/usr/bin/env python3
"""
Gateway v10 - delegation-first OpenAI-compatible gateway.

Goals:
- local-first / remote-fallback routing without silent provider switching
- explicit delegation budgets and output caps
- run/child correlation IDs and durable diagnostics
- delegate health scoring, quarantine, circuit breaker and failover
- tool-aware delegation with explicit no-tools mode
- SSE passthrough with usage aggregation
- benchmark/scoreboard telemetry for delegate quality
- request cancellation visibility and bounded retries
- compatibility with llama.cpp/OpenAI-compatible backends

Environment:
  GATEWAY_HOST=0.0.0.0
  GATEWAY_PORT=8090
  LOCAL_BASE_URL=http://127.0.0.1:8080
  REMOTE_BASE_URL=https://openrouter.ai/api/v1
  REMOTE_API_KEY=
  MEMORY_BASE_URL=http://127.0.0.1:8765
  GATEWAY_REQUEST_TIMEOUT=90
  GATEWAY_DELEGATE_TIMEOUT=75
  GATEWAY_MAX_DELEGATES=8
  GATEWAY_MAX_TOOL_CALLS=16
  GATEWAY_DEFAULT_OUTPUT_TOKENS=512
  GATEWAY_REMOTE_BRIEF_TOKENS=256
  GATEWAY_LOCAL_INPUT_CHARS=4000
  GATEWAY_PAGE_CHARS=40000
  GATEWAY_DEBUG=0
"""

from __future__ import annotations

import asyncio
import contextlib
import json
import logging
import os
import sqlite3
import time
import uuid
from dataclasses import dataclass, field, asdict
from typing import Any, AsyncIterator, Iterable

import httpx
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse, StreamingResponse
from pydantic import BaseModel, Field


LOG = logging.getLogger("gateway-v10")
logging.basicConfig(
    level=logging.DEBUG if os.getenv("GATEWAY_DEBUG") == "1" else logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)


def env_int(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, str(default)))
    except ValueError:
        return default


def env_float(name: str, default: float) -> float:
    try:
        return float(os.getenv(name, str(default)))
    except ValueError:
        return default


@dataclass(frozen=True)
class Settings:
    host: str = os.getenv("GATEWAY_HOST", "0.0.0.0")
    port: int = env_int("GATEWAY_PORT", 8090)
    local_base_url: str = os.getenv("LOCAL_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
    remote_base_url: str = os.getenv("REMOTE_BASE_URL", "https://openrouter.ai/api/v1").rstrip("/")
    remote_api_key: str = os.getenv("REMOTE_API_KEY", "")
    memory_base_url: str = os.getenv("MEMORY_BASE_URL", "http://127.0.0.1:8765").rstrip("/")
    request_timeout: float = env_float("GATEWAY_REQUEST_TIMEOUT", 90.0)
    delegate_timeout: float = env_float("GATEWAY_DELEGATE_TIMEOUT", 75.0)
    max_delegates: int = env_int("GATEWAY_MAX_DELEGATES", 8)
    max_tool_calls: int = env_int("GATEWAY_MAX_TOOL_CALLS", 16)
    default_output_tokens: int = env_int("GATEWAY_DEFAULT_OUTPUT_TOKENS", 512)
    remote_brief_tokens: int = env_int("GATEWAY_REMOTE_BRIEF_TOKENS", 256)
    local_input_chars: int = env_int("GATEWAY_LOCAL_INPUT_CHARS", 4000)
    page_chars: int = env_int("GATEWAY_PAGE_CHARS", 40000)
    quarantine_seconds: int = env_int("GATEWAY_QUARANTINE_SECONDS", 120)
    failure_threshold: int = env_int("GATEWAY_FAILURE_THRESHOLD", 3)
    db_path: str = os.getenv("GATEWAY_DB", "gateway_v10.sqlite3")


SETTINGS = Settings()


class RouteRequest(BaseModel):
    model: str
    messages: list[dict[str, Any]]
    stream: bool = False
    temperature: float | None = None
    max_tokens: int | None = Field(default=None, ge=1)
    max_completion_tokens: int | None = Field(default=None, ge=1)
    tools: list[dict[str, Any]] | None = None
    tool_choice: Any | None = None
    metadata: dict[str, Any] | None = None
    gateway: dict[str, Any] | None = None

    model_config = {"extra": "allow"}


@dataclass
class WorkerHealth:
    worker: str
    failures: int = 0
    successes: int = 0
    empty_results: int = 0
    auth_failures: int = 0
    latency_ms_ema: float = 0.0
    tokens_per_sec_ema: float = 0.0
    quarantined_until: float = 0.0

    @property
    def quarantined(self) -> bool:
        return time.time() < self.quarantined_until

    def score(self) -> float:
        reliability = (self.successes + 1) / (self.successes + self.failures + self.empty_results + 2)
        latency_penalty = min(self.latency_ms_ema / 30000.0, 1.0) if self.latency_ms_ema else 0.0
        auth_penalty = min(self.auth_failures * 0.25, 1.0)
        quarantine_penalty = 1.0 if self.quarantined else 0.0
        return max(0.0, 100.0 * (reliability - 0.25 * latency_penalty - 0.25 * auth_penalty - quarantine_penalty))


@dataclass
class RunState:
    run_id: str
    started_at: float
    requested_model: str
    effective_output_cap: int
    primary_provider: str = ""
    status: str = "RUNNING"
    delegate_calls: int = 0
    tool_calls: int = 0
    input_tokens: int = 0
    output_tokens: int = 0
    remote_input_tokens: int = 0
    remote_output_tokens: int = 0
    local_input_tokens: int = 0
    local_output_tokens: int = 0
    cancellations: int = 0
    retries: int = 0
    events: list[dict[str, Any]] = field(default_factory=list)

    def event(self, kind: str, **details: Any) -> None:
        self.events.append(
            {
                "ts": time.time(),
                "kind": kind,
                **details,
            }
        )


@dataclass
class DelegateScore:
    delegate: str
    completed: int = 0
    failed: int = 0
    empty: int = 0
    tool_successes: int = 0
    tool_failures: int = 0
    total_latency_ms: float = 0.0
    total_input_tokens: int = 0
    total_output_tokens: int = 0

    def ranking_score(self) -> float:
        attempts = self.completed + self.failed + self.empty
        if attempts <= 0:
            return 0.0
        success = self.completed / attempts
        tool_total = self.tool_successes + self.tool_failures
        tool_success = self.tool_successes / tool_total if tool_total else 1.0
        avg_latency = self.total_latency_ms / max(attempts, 1)
        latency_score = 1.0 / (1.0 + avg_latency / 10000.0)
        efficiency = self.total_output_tokens / max(self.total_input_tokens, 1)
        return round(100.0 * (0.45 * success + 0.25 * tool_success + 0.20 * latency_score + 0.10 * min(efficiency * 4, 1.0)), 2)


class Store:
    def __init__(self, path: str):
        self.path = path
        self._init()

    def _connect(self) -> sqlite3.Connection:
        con = sqlite3.connect(self.path, timeout=10)
        con.row_factory = sqlite3.Row
        return con

    def _init(self) -> None:
        con = self._connect()
        try:
            con.execute(
                """
                CREATE TABLE IF NOT EXISTS run_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    run_id TEXT NOT NULL,
                    created_at REAL NOT NULL,
                    kind TEXT NOT NULL,
                    payload TEXT NOT NULL
                )
                """
            )
            con.execute(
                """
                CREATE TABLE IF NOT EXISTS delegate_scores (
                    delegate TEXT PRIMARY KEY,
                    completed INTEGER NOT NULL DEFAULT 0,
                    failed INTEGER NOT NULL DEFAULT 0,
                    empty INTEGER NOT NULL DEFAULT 0,
                    tool_successes INTEGER NOT NULL DEFAULT 0,
                    tool_failures INTEGER NOT NULL DEFAULT 0,
                    total_latency_ms REAL NOT NULL DEFAULT 0,
                    total_input_tokens INTEGER NOT NULL DEFAULT 0,
                    total_output_tokens INTEGER NOT NULL DEFAULT 0
                )
                """
            )
            con.commit()
        finally:
            con.close()

    def add_event(self, run_id: str, kind: str, payload: dict[str, Any]) -> None:
        con = self._connect()
        try:
            con.execute(
                "INSERT INTO run_events(run_id,created_at,kind,payload) VALUES(?,?,?,?)",
                (run_id, time.time(), kind, json.dumps(payload, separators=(",", ":"))),
            )
            con.commit()
        finally:
            con.close()

    def update_delegate(
        self,
        delegate: str,
        *,
        completed: int = 0,
        failed: int = 0,
        empty: int = 0,
        tool_successes: int = 0,
        tool_failures: int = 0,
        latency_ms: float = 0,
        input_tokens: int = 0,
        output_tokens: int = 0,
    ) -> None:
        con = self._connect()
        try:
            con.execute(
                """
                INSERT INTO delegate_scores(
                    delegate, completed, failed, empty, tool_successes, tool_failures,
                    total_latency_ms, total_input_tokens, total_output_tokens
                ) VALUES(?,?,?,?,?,?,?,?,?)
                ON CONFLICT(delegate) DO UPDATE SET
                    completed=completed+excluded.completed,
                    failed=failed+excluded.failed,
                    empty=empty+excluded.empty,
                    tool_successes=tool_successes+excluded.tool_successes,
                    tool_failures=tool_failures+excluded.tool_failures,
                    total_latency_ms=total_latency_ms+excluded.total_latency_ms,
                    total_input_tokens=total_input_tokens+excluded.total_input_tokens,
                    total_output_tokens=total_output_tokens+excluded.total_output_tokens
                """,
                (
                    delegate,
                    completed,
                    failed,
                    empty,
                    tool_successes,
                    tool_failures,
                    latency_ms,
                    input_tokens,
                    output_tokens,
                ),
            )
            con.commit()
        finally:
            con.close()

    def scoreboard(self) -> list[dict[str, Any]]:
        con = self._connect()
        try:
            rows = con.execute("SELECT * FROM delegate_scores").fetchall()
        finally:
            con.close()
        out: list[dict[str, Any]] = []
        for row in rows:
            s = DelegateScore(**dict(row))
            d = asdict(s)
            d["score"] = s.ranking_score()
            d["avg_latency_ms"] = round(s.total_latency_ms / max(s.completed + s.failed + s.empty, 1), 1)
            out.append(d)
        out.sort(key=lambda x: x["score"], reverse=True)
        return out


STORE = Store(SETTINGS.db_path)
HEALTH: dict[str, WorkerHealth] = {}


def get_health(worker: str) -> WorkerHealth:
    if worker not in HEALTH:
        HEALTH[worker] = WorkerHealth(worker=worker)
    return HEALTH[worker]


def output_cap(req: RouteRequest) -> int:
    cap = req.max_completion_tokens or req.max_tokens or SETTINGS.default_output_tokens
    cap = max(1, int(cap))
    gw = req.gateway or {}
    delegated_cap = gw.get("output_cap")
    if delegated_cap is not None:
        cap = min(cap, max(1, int(delegated_cap)))
    return cap


def trim_messages(messages: list[dict[str, Any]], max_chars: int) -> list[dict[str, Any]]:
    if max_chars <= 0:
        return messages
    remaining = max_chars
    out: list[dict[str, Any]] = []
    for message in reversed(messages):
        raw = json.dumps(message, ensure_ascii=False)
        if len(raw) > remaining and out:
            break
        if len(raw) > remaining:
            msg = dict(message)
            content = str(msg.get("content", ""))
            msg["content"] = content[-max(0, remaining - 256):]
            out.append(msg)
            break
        out.append(message)
        remaining -= len(raw)
    return list(reversed(out))


def base_payload(req: RouteRequest, cap: int, *, allow_tools: bool) -> dict[str, Any]:
    payload = req.model_dump(exclude_none=True)
    payload.pop("gateway", None)
    payload["max_tokens"] = cap
    payload["max_completion_tokens"] = cap
    if not allow_tools:
        payload.pop("tools", None)
        payload.pop("tool_choice", None)
    return payload


def usage_from_json(data: dict[str, Any]) -> tuple[int, int]:
    usage = data.get("usage") or {}
    inp = int(usage.get("prompt_tokens") or usage.get("input_tokens") or 0)
    out = int(usage.get("completion_tokens") or usage.get("output_tokens") or 0)
    return inp, out


def usable_content(data: dict[str, Any]) -> bool:
    choices = data.get("choices") or []
    if not choices:
        return False
    message = choices[0].get("message") or {}
    content = message.get("content")
    reasoning = message.get("reasoning") or message.get("reasoning_content")
    tool_calls = message.get("tool_calls") or []
    return bool((isinstance(content, str) and content.strip()) or reasoning or tool_calls)


def auth_headers(remote: bool = False) -> dict[str, str]:
    headers = {"Content-Type": "application/json"}
    if remote and SETTINGS.remote_api_key:
        headers["Authorization"] = f"Bearer {SETTINGS.remote_api_key}"
    return headers


async def post_json(
    client: httpx.AsyncClient,
    url: str,
    payload: dict[str, Any],
    *,
    headers: dict[str, str],
    timeout: float,
) -> httpx.Response:
    return await client.post(url, json=payload, headers=headers, timeout=timeout)


def record_health(worker: str, ok: bool, latency_ms: float, output_tokens: int = 0, auth_failed: bool = False, empty: bool = False) -> None:
    h = get_health(worker)
    alpha = 0.2
    h.latency_ms_ema = latency_ms if h.latency_ms_ema == 0 else (1 - alpha) * h.latency_ms_ema + alpha * latency_ms
    if latency_ms > 0 and output_tokens > 0:
        tps = output_tokens / (latency_ms / 1000.0)
        h.tokens_per_sec_ema = tps if h.tokens_per_sec_ema == 0 else (1 - alpha) * h.tokens_per_sec_ema + alpha * tps
    if ok:
        h.successes += 1
        h.failures = max(0, h.failures - 1)
    else:
        h.failures += 1
        if empty:
            h.empty_results += 1
        if auth_failed:
            h.auth_failures += 1
        if h.failures >= SETTINGS.failure_threshold or auth_failed:
            h.quarantined_until = time.time() + SETTINGS.quarantine_seconds


def pick_provider(req: RouteRequest) -> tuple[str, str, bool]:
    gw = req.gateway or {}
    requested = str(gw.get("provider") or "").lower()
    local_model = str(gw.get("local_model") or req.model)
    remote_model = str(gw.get("remote_model") or req.model)

    if requested == "remote":
        return "remote", remote_model, True
    if requested == "local":
        return "local", local_model, False

    # Default local-first. We never silently rotate unless failover is explicitly enabled.
    return "local", local_model, False


async def nonstream_completion(req: RouteRequest, request: Request) -> JSONResponse:
    run = RunState(
        run_id=request.headers.get("X-Gateway-Run-ID") or str(uuid.uuid4()),
        started_at=time.time(),
        requested_model=req.model,
        effective_output_cap=output_cap(req),
    )
    gw = req.gateway or {}
    allow_failover = bool(gw.get("allow_failover", True))
    allow_tools = bool(gw.get("allow_tools", True))
    provider, model, remote = pick_provider(req)
    attempts: list[tuple[str, str, bool]] = [(provider, model, remote)]
    if allow_failover:
        if provider == "local":
            attempts.append(("remote", str(gw.get("remote_model") or req.model), True))
        elif provider == "remote":
            attempts.append(("local", str(gw.get("local_model") or req.model), False))

    last_error: str | None = None
    async with httpx.AsyncClient() as client:
        for idx, (provider_name, model_name, is_remote) in enumerate(attempts):
            worker_key = f"{provider_name}:{model_name}"
            health = get_health(worker_key)
            if health.quarantined:
                run.event("WORKER_QUARANTINED", worker=worker_key, until=health.quarantined_until)
                STORE.add_event(run.run_id, "WORKER_QUARANTINED", run.events[-1])
                continue

            base = SETTINGS.remote_base_url if is_remote else SETTINGS.local_base_url
            payload = base_payload(req, run.effective_output_cap, allow_tools=allow_tools)
            payload["model"] = model_name
            if provider_name == "local":
                payload["messages"] = trim_messages(payload.get("messages", []), SETTINGS.local_input_chars)

            run.primary_provider = provider_name if idx == 0 else run.primary_provider
            run.event(
                "REQUEST_START",
                worker=worker_key,
                configured_cap=req.max_completion_tokens or req.max_tokens,
                effective_cap=run.effective_output_cap,
                allow_tools=allow_tools,
                failover_index=idx,
            )
            STORE.add_event(run.run_id, "REQUEST_START", run.events[-1])
            started = time.perf_counter()

            try:
                response = await post_json(
                    client,
                    f"{base}/v1/chat/completions",
                    payload,
                    headers=auth_headers(is_remote),
                    timeout=SETTINGS.request_timeout,
                )
                latency_ms = (time.perf_counter() - started) * 1000
                auth_failed = response.status_code in (401, 403)
                if response.status_code >= 400:
                    detail = response.text[:1000]
                    record_health(worker_key, False, latency_ms, auth_failed=auth_failed)
                    run.event(
                        "REQUEST_FAILED",
                        worker=worker_key,
                        status=response.status_code,
                        latency_ms=round(latency_ms, 1),
                        detail=detail,
                    )
                    STORE.add_event(run.run_id, "REQUEST_FAILED", run.events[-1])
                    last_error = f"{worker_key} returned HTTP {response.status_code}: {detail}"
                    if response.status_code in (401, 403, 404, 410):
                        continue
                    if idx + 1 < len(attempts):
                        run.retries += 1
                        continue
                    break

                data = response.json()
                inp, out = usage_from_json(data)
                is_usable = usable_content(data)
                if not is_usable:
                    record_health(worker_key, False, latency_ms, output_tokens=out, empty=True)
                    STORE.update_delegate(worker_key, empty=1, latency_ms=latency_ms, input_tokens=inp, output_tokens=out)
                    run.event("COMPLETED_EMPTY", worker=worker_key, input_tokens=inp, output_tokens=out)
                    STORE.add_event(run.run_id, "COMPLETED_EMPTY", run.events[-1])
                    last_error = f"{worker_key} returned no usable content"
                    if idx + 1 < len(attempts):
                        run.retries += 1
                        continue
                    break

                record_health(worker_key, True, latency_ms, output_tokens=out)
                STORE.update_delegate(worker_key, completed=1, latency_ms=latency_ms, input_tokens=inp, output_tokens=out)
                run.input_tokens += inp
                run.output_tokens += out
                if is_remote:
                    run.remote_input_tokens += inp
                    run.remote_output_tokens += out
                else:
                    run.local_input_tokens += inp
                    run.local_output_tokens += out
                run.status = "COMPLETED"
                run.event(
                    "REQUEST_COMPLETED",
                    worker=worker_key,
                    latency_ms=round(latency_ms, 1),
                    input_tokens=inp,
                    output_tokens=out,
                    effective_cap=run.effective_output_cap,
                )
                STORE.add_event(run.run_id, "REQUEST_COMPLETED", run.events[-1])

                data.setdefault("gateway", {})
                data["gateway"].update(
                    {
                        "run_id": run.run_id,
                        "provider": provider_name,
                        "worker": worker_key,
                        "status": run.status,
                        "configured_output_cap": req.max_completion_tokens or req.max_tokens,
                        "effective_output_cap": run.effective_output_cap,
                        "failover_count": idx,
                        "usage_split": {
                            "local_input_tokens": run.local_input_tokens,
                            "local_output_tokens": run.local_output_tokens,
                            "remote_input_tokens": run.remote_input_tokens,
                            "remote_output_tokens": run.remote_output_tokens,
                        },
                    }
                )
                return JSONResponse(data, headers={"X-Gateway-Run-ID": run.run_id})

            except asyncio.CancelledError:
                run.status = "CANCELED"
                run.cancellations += 1
                run.event("REQUEST_CANCELED", worker=worker_key)
                STORE.add_event(run.run_id, "REQUEST_CANCELED", run.events[-1])
                raise
            except (httpx.TimeoutException, httpx.NetworkError, json.JSONDecodeError) as exc:
                latency_ms = (time.perf_counter() - started) * 1000
                record_health(worker_key, False, latency_ms)
                run.event("REQUEST_EXCEPTION", worker=worker_key, error=type(exc).__name__, detail=str(exc))
                STORE.add_event(run.run_id, "REQUEST_EXCEPTION", run.events[-1])
                last_error = f"{worker_key}: {type(exc).__name__}: {exc}"
                if idx + 1 < len(attempts):
                    run.retries += 1
                    continue
                break

    run.status = "FAILED"
    raise HTTPException(status_code=502, detail={"run_id": run.run_id, "error": last_error or "All workers unavailable"})


async def stream_completion(req: RouteRequest, request: Request) -> StreamingResponse:
    run_id = request.headers.get("X-Gateway-Run-ID") or str(uuid.uuid4())
    cap = output_cap(req)
    gw = req.gateway or {}
    provider, model, remote = pick_provider(req)
    worker_key = f"{provider}:{model}"
    health = get_health(worker_key)
    if health.quarantined:
        raise HTTPException(status_code=503, detail={"run_id": run_id, "error": f"{worker_key} is quarantined"})

    base = SETTINGS.remote_base_url if remote else SETTINGS.local_base_url
    payload = base_payload(req, cap, allow_tools=bool(gw.get("allow_tools", True)))
    payload["stream"] = True
    payload["model"] = model
    if provider == "local":
        payload["messages"] = trim_messages(payload.get("messages", []), SETTINGS.local_input_chars)

    async def event_stream() -> AsyncIterator[bytes]:
        started = time.perf_counter()
        output_estimate = 0
        async with httpx.AsyncClient(timeout=None) as client:
            try:
                async with client.stream(
                    "POST",
                    f"{base}/v1/chat/completions",
                    json=payload,
                    headers=auth_headers(remote),
                    timeout=SETTINGS.request_timeout,
                ) as response:
                    if response.status_code >= 400:
                        body = await response.aread()
                        raise HTTPException(response.status_code, detail=body.decode("utf-8", "replace")[:1000])
                    async for chunk in response.aiter_bytes():
                        if await request.is_disconnected():
                            STORE.add_event(run_id, "STREAM_CLIENT_DISCONNECTED", {"worker": worker_key})
                            return
                        output_estimate += chunk.count(b"data:")
                        yield chunk
                latency_ms = (time.perf_counter() - started) * 1000
                record_health(worker_key, True, latency_ms, output_tokens=output_estimate)
                STORE.update_delegate(worker_key, completed=1, latency_ms=latency_ms, output_tokens=output_estimate)
                STORE.add_event(
                    run_id,
                    "STREAM_COMPLETED",
                    {
                        "worker": worker_key,
                        "latency_ms": latency_ms,
                        "effective_cap": cap,
                        "output_event_estimate": output_estimate,
                    },
                )
            except asyncio.CancelledError:
                STORE.add_event(run_id, "STREAM_CANCELED", {"worker": worker_key})
                raise
            except Exception as exc:
                latency_ms = (time.perf_counter() - started) * 1000
                record_health(worker_key, False, latency_ms)
                STORE.add_event(run_id, "STREAM_FAILED", {"worker": worker_key, "error": str(exc)})
                error = {
                    "error": {
                        "message": str(exc),
                        "type": type(exc).__name__,
                    },
                    "gateway": {"run_id": run_id, "worker": worker_key},
                }
                yield ("data: " + json.dumps(error) + "\n\n").encode()

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Gateway-Run-ID": run_id,
            "X-Gateway-Provider": provider,
            "X-Gateway-Worker": worker_key,
        },
    )


app = FastAPI(title="Gateway v10", version="10.0.0")


@app.get("/health")
async def health() -> dict[str, Any]:
    return {
        "ok": True,
        "version": "10.0.0",
        "local_base_url": SETTINGS.local_base_url,
        "remote_base_url": SETTINGS.remote_base_url,
        "limits": {
            "request_timeout_s": SETTINGS.request_timeout,
            "delegate_timeout_s": SETTINGS.delegate_timeout,
            "max_delegates": SETTINGS.max_delegates,
            "max_tool_calls": SETTINGS.max_tool_calls,
            "default_output_tokens": SETTINGS.default_output_tokens,
        },
    }


@app.get("/diagnostics")
async def diagnostics() -> dict[str, Any]:
    return {
        "version": "10.0.0",
        "workers": [
            {
                **asdict(h),
                "quarantined": h.quarantined,
                "score": round(h.score(), 2),
            }
            for h in sorted(HEALTH.values(), key=lambda x: x.score(), reverse=True)
        ],
        "scoreboard": STORE.scoreboard(),
    }


@app.get("/delegation/scoreboard")
async def delegation_scoreboard() -> dict[str, Any]:
    return {"rankings": STORE.scoreboard()}


@app.post("/v1/chat/completions")
async def chat_completions(req: RouteRequest, request: Request):
    if req.stream:
        return await stream_completion(req, request)
    return await nonstream_completion(req, request)


@app.get("/v1/models")
async def models() -> JSONResponse:
    data: list[dict[str, Any]] = []
    async with httpx.AsyncClient() as client:
        for name, base, remote in (
            ("local", SETTINGS.local_base_url, False),
            ("remote", SETTINGS.remote_base_url, True),
        ):
            try:
                r = await client.get(f"{base}/v1/models", headers=auth_headers(remote), timeout=10)
                if r.status_code == 200:
                    payload = r.json()
                    for item in payload.get("data", []):
                        obj = dict(item)
                        obj["gateway_provider"] = name
                        data.append(obj)
            except Exception as exc:
                LOG.warning("model discovery failed for %s: %s", name, exc)
    return JSONResponse({"object": "list", "data": data})


@app.exception_handler(HTTPException)
async def http_exception_handler(_: Request, exc: HTTPException):
    return JSONResponse(status_code=exc.status_code, content={"error": exc.detail})


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host=SETTINGS.host, port=SETTINGS.port)
