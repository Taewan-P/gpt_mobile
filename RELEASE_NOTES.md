# GPT Mobile AI 0.9.21.4

## Delegation context efficiency
- Bound primary tool-exchange replay so later model rounds no longer resend the complete accumulated raw tool history.
- Deduplicate older identical tool results while preserving tool-call/result pairing.
- Preserve untouched structured tool-result types, including JSON results such as device location payloads.
- Restore delegation eligibility through remote-balanced ownership modes instead of disabling it prematurely.
- Keep large completed tool results eligible for local compaction before remote synthesis.

## Token and context controls
- Add configurable primary replay budgets for total replay and per-result retention.
- Base context-limit accounting on the compacted replay view instead of unbounded accumulated raw tool payloads.
- Add `PRIMARY_REPLAY_COMPACTED` diagnostics with raw, replayed, and saved token estimates.

## Validation
- Added regression coverage for replay compaction, duplicate-result elimination, tool pairing, and JSON result preservation.
- PR validation, Kotlin lint, CodeQL, Android APK build, and debug build passed before merge.
