# Gateway v10

Standalone OpenAI-compatible gateway for the mobile app's current delegation architecture.

## Highlights

- **Local-first with explicit failover** to remote models. Provider rotation is recorded; it is never invisible.
- **Output-cap propagation** through `max_tokens` and `max_completion_tokens`, with a gateway delegation cap that can only tighten the request.
- **Delegate health/quarantine** for repeated failures, empty completions, 401/403 auth failures, 404/410 retired models, timeouts and network errors.
- **Explicit result states** such as completed, completed-empty, failed and canceled in durable SQLite diagnostics.
- **Tool policy** can be enabled/disabled per request; text-only delegated workers can be invoked with no tool schema at all.
- **Context bounding** for local workers to reduce repeated large prompt replay.
- **Run correlation** with `X-Gateway-Run-ID`.
- **Streaming cancellation visibility** and client-disconnect tracking.
- **Delegate scoreboard** at `/delegation/scoreboard`, combining completion reliability, tool-success data, latency and token efficiency.
- **Diagnostics endpoint** at `/diagnostics` with health score, quarantine state, latency EMA and token-generation speed EMA.

## Run

```powershell
cd gateway
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
$env:LOCAL_BASE_URL="http://127.0.0.1:8080"
$env:REMOTE_BASE_URL="https://openrouter.ai/api/v1"
$env:REMOTE_API_KEY="..."
python gateway_v10.py
```

Default bind is `0.0.0.0:8090`.

## Request controls

Existing OpenAI-compatible request fields are accepted. Optional gateway controls:

```json
{
  "gateway": {
    "provider": "local",
    "allow_failover": true,
    "allow_tools": true,
    "local_model": "local-model-id",
    "remote_model": "remote-model-id",
    "output_cap": 512
  }
}
```

`output_cap` cannot increase the model request's own `max_tokens` / `max_completion_tokens`; it only constrains it further.

## Important behavior

- A worker reporting generated tokens but returning no usable content is recorded as `COMPLETED_EMPTY`, not success.
- 401/403 immediately penalize/quarantine the worker so repeated auth failures do not burn a run budget.
- 404/410 move directly to the configured failover path.
- Local prompt replay is bounded with `GATEWAY_LOCAL_INPUT_CHARS` (default 4000).
- The database defaults to `gateway_v10.sqlite3` and can be changed with `GATEWAY_DB`.
