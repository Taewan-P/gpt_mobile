# GPT Mobile AI 0.9.24.0

## Interactive delegation recovery
- Add an in-conversation recovery dialog when a delegate fails, becomes unavailable, or can no longer continue.
- Show eligible replacement models with their saved Delegation benchmark scores.
- Let users switch the active delegate for the rest of the turn or cancel delegation and have the primary model finish alone.
- Keep recovery scoped to the active conversation and prevent silent substitution when manual recovery is required.
- Preserve automatic fallback behavior for headless and benchmark paths.

## Delegation reliability and research quality
- Strengthen worker quarantine and failover handling for unavailable, disconnected, retired, or invalid models.
- Keep delegated research running across search providers and page fallbacks instead of degrading prematurely to partial evidence.
- Improve evidence handoffs, warning classification, factual grounding, and handling of JS-heavy or consent-heavy pages.
- Preserve JSON-LD article bodies while stripping scripts and consent-manager noise.
- Pin authorized delegation settings through a research pass so mid-run settings refreshes do not interrupt valid work.
- Isolate delegated gateway workers from gateway memory and local MCP state and enforce no-thinking/no-tool fast paths where appropriate.

## Delegation benchmarking
- Add multi-select sequential delegate testing.
- Expand the delegation scoreboard with throughput, latency, successful tool-use, reliability, and diagnostic-event measurements.
- Persist delegation benchmark diagnostics and expose explainable performance insights.
- Keep benchmark comparisons settings-aware and preserve local runtime safeguards during batches.

## Gateway
- Update gateway v12 delegation handling with improved worker isolation, bounded child requests, and more reliable no-thinking/no-tool execution.
- Expand gateway regression coverage for delegated worker markers and isolation behavior.

## Stability
- Fix stale ChatViewModel test construction after dependency changes.
- Harden URL HTML extraction with a JVM-safe fallback when Android HTML parsing is unavailable.
- Preserve completed work when delegation fails rather than replaying already-finished operations.

## Build
- Version: 0.9.24.0
- Version code: 93
- Signed Android release artifacts are built, verified, attested, and published by the repository release workflow.
