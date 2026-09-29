# GPT Mobile AI 0.9.21.3

## Delegation and Tool Calling Improvements
- Enforce provider output caps and report configured/requested/effective limits.
- Delegate safety: cap delegated input tokens by default, preflight worker requests including tool schemas, and chunk oversized delegations.
- Stall and watchdog controls: adaptive worker runtime deadlines, time-to-first-progress and idle watchdogs.
- Expanded tool exposure: remove hardcoded tool caps from primary and delegated child runs, allowing context-driven tool scheduling.

## MCP Reliability & Agent Round Budget
- Added MCP endpoint health tracking, exponential backoff, and circuit breakers.
- Concurrently resolve independent MCP endpoints with dedicated initialization vs transport timeouts.
- Raised agent round budgets for complex workflows and finalized gracefully at step caps.
