#!/usr/bin/env python3
"""Deterministic localhost OpenAI-compatible stream for Android visual testing."""

import argparse
import json
import threading
import time
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


TEXT = "# Streaming test\n\n" + "\n\n".join(
    f"## Section {number}\n\n"
    + "A fast response should stay readable while you scroll. " * 3
    + "한국어, 日本語, and emoji 👨‍👩‍👧‍👦 remain intact.\n\n"
    + "```kotlin\nval message = \"Hello\"\nprintln(message)\n```\n\n"
    + "| Item | Value |\n| --- | --- |\n| Progress | Streaming |\n\n"
    + "Inline math: $a^2 + b^2 = c^2$.\n\n$$E = mc^2$$"
    for number in range(1, 21)
)


class Handler(BaseHTTPRequestHandler):
    characters_per_second = 1200
    burst_size = 64

    def log_message(self, *_):
        pass  # Do not record credentials or conversation content.

    def do_GET(self):
        body = json.dumps({"object": "list", "data": [{
            "id": "benchmark-model", "object": "model", "context_window": 65536
        }]}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 <= length <= 2 * 1024 * 1024:
                self.send_error(413)
                return
            request = json.loads(self.rfile.read(length))
        except ValueError:
            self.send_error(400)
            return
        if not self.path.rstrip("/").endswith("chat/completions"):
            self.send_error(404)
            return

        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()
        self.close_connection = True
        messages = request.get("messages", [])
        tool_names = {tool.get("function", {}).get("name") for tool in request.get("tools", [])}
        wants_tool = any("benchmark tools" in str(message.get("content", "")) for message in messages if message.get("role") == "user")
        has_tool_result = any(message.get("role") == "tool" for message in messages)
        latest_user = next((str(message.get("content", "")) for message in reversed(messages) if message.get("role") == "user"), "")
        if "warmup" in latest_user:
            text = "Ready."
        elif "benchmark prose" in latest_user:
            text = "Streaming prose should stay readable while you scroll. 한국어, 日本語, emoji 👨‍👩‍👧‍👦. " * 100
        else:
            text = TEXT
        try:
            if wants_tool and not has_tool_result and "current_date" in tool_names:
                self.event({"content": "Checking the date before continuing.\n\n"})
                self.event({"tool_calls": [{
                    "index": 0, "id": "call_benchmark_date", "type": "function",
                    "function": {"name": "current_date", "arguments": "{}"}
                }]}, "tool_calls")
            else:
                for start in range(0, len(text), self.burst_size):
                    chunk = text[start:start + self.burst_size]
                    self.event({"content": chunk})
                    if self.characters_per_second:
                        time.sleep(len(chunk) / self.characters_per_second)
                self.event({}, "stop")
            self.wfile.write(b"data: [DONE]\n\n")
            self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError):
            pass

    def event(self, delta, finish_reason=None):
        payload = {"id": "benchmark-response", "object": "chat.completion.chunk", "model": "benchmark-model",
                   "choices": [{"index": 0, "delta": delta, "finish_reason": finish_reason}]}
        self.wfile.write(("data: " + json.dumps(payload, ensure_ascii=False) + "\n\n").encode())
        self.wfile.flush()


def self_test():
    Handler.characters_per_second = 0
    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    worker = threading.Thread(target=server.serve_forever, daemon=True)
    worker.start()
    try:
        url = f"http://127.0.0.1:{server.server_port}/v1/chat/completions"
        def send(messages, tools=None):
            data = json.dumps({"messages": messages, "tools": tools or []}).encode()
            with urllib.request.urlopen(urllib.request.Request(url, data=data), timeout=5) as response:
                return response.read().decode()
        response = send([{"role": "user", "content": "benchmark"}])
        events = [json.loads(line[6:]) for line in response.splitlines() if line.startswith("data: {")]
        assert "".join(event["choices"][0]["delta"].get("content", "") for event in events) == TEXT
        assert response.endswith("data: [DONE]\n\n")
        tools = [{"type": "function", "function": {"name": "current_date"}}]
        tool_response = send([{"role": "user", "content": "benchmark tools"}], tools)
        assert '"name": "current_date"' in tool_response and '"finish_reason": "tool_calls"' in tool_response
        assert "Section 20" in send([{"role": "tool", "content": "2026-09-08"}], tools)
        print("PASS: complete Unicode stream, terminal marker, tool transition, follow-up")
    finally:
        server.shutdown()
        server.server_close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8766)
    parser.add_argument("--characters-per-second", type=int, default=1200)
    parser.add_argument("--burst-size", type=int, default=64)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
    else:
        if args.characters_per_second <= 0 or args.burst_size <= 0:
            parser.error("Rate and burst size must be positive")
        Handler.characters_per_second = args.characters_per_second
        Handler.burst_size = args.burst_size
        print(f"Emulator URL: http://10.0.2.2:{args.port}/v1/ — model: benchmark-model — context: 65536", flush=True)
        ThreadingHTTPServer(("127.0.0.1", args.port), Handler).serve_forever()
