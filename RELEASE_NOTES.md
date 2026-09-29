# GPT Mobile AI 0.9.21.5

## Delegation reliability
- Stop delegated-worker cascades after two consecutive empty responses instead of repeatedly spending tokens on unusable results.
- Quarantine delegated workers for the rest of the turn after HTTP 401/403 authorization failures.
- Detect reasoning-only delegated completions that consume output tokens without producing a usable final answer.
- Preserve observed provider input usage when accounting for failed delegated calls.

## Token efficiency
- Learn actual provider/system/tool request overhead from delegation usage telemetry.
- Reserve observed request overhead when sizing subsequent delegated prompts.
- Limit delegated tool definitions by available input budget while preserving higher-priority research tools first.
- Improve diagnostics for effective input size, observed overhead, circuit state, auth failures, and empty/reasoning-only results.

## Validation
- Added regression coverage for thrown empty-response failures, worker circuit breaking, and authorization quarantine.
- Android APK build, debug build, and Kotlin lint passed on the fix PR before merge.
