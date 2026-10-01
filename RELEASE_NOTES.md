# GPT Mobile AI 0.9.23.0

## Delegation reliability
- Prevent terminal delegation failures from being retried repeatedly within the same turn.
- Quarantine disconnected, unavailable, retired, or not-downloaded delegate workers immediately and fail over to another eligible helper.
- Detect socket timeouts, DNS failures, connection resets, refused connections, broken pipes, no-route failures, and expired read/connect operations as worker transport failures.
- Preserve retries for genuinely transient one-off provider failures while stopping retry storms after terminal `CANCELED_NO_RESULT` states.
- Improve reasoning-only recovery by sizing output headroom from the effective request cost, including provider/system/tool overhead.
- Expand the next delegate output allowance after an empty or reasoning-only completion so the worker has room to return a usable final answer.
- Stop counting eligibility failures as wasted inference tokens when inference never actually started.

## Validation
- Add regression tests for missing local models, socket-timeout failover, reasoning-only recovery, and per-turn terminal circuit breaking.
- Keep completed work intact when a delegate becomes unavailable instead of replaying the job.
- Preserve the existing worker call, token, runtime, and privacy limits.

## Build
- Version: 0.9.23.0
- Version code: 92
- Signed Android release artifacts are built and verified by the repository release workflow.
