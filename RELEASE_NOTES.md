# GPT Mobile AI 0.9.25.0

## Delegated tools and gateway 12.1.1
- Preserve app-owned tool schemas in isolated delegated gateway requests so workers can execute authorized tools.
- Bound gateway queue, model-read, and stream waits, and surface interrupted streams instead of accepting empty success.
- Preserve backend token timing for buffered responses and avoid replaying completed tool work during retries.

## Recovery and response handling
- Apply first-response deadlines to local gateway workers and switch to eligible fallback models after failures.
- Exclude unavailable LiteRT models and prevent repeated recovery cycles through failed helpers.
- Accept short and array-form text responses and preserve exact values, identifiers, and sources during compaction.
- Retry transient DNS and connection failures before output with bounded delays.
- Omit unsupported model sampling parameters and retry explicitly rejected sampling fields.

## Delegation benchmarks
- Report token speed using native/backend timings where available, with clearly labelled estimates otherwise.
- Log delegation scores and rank, and distinguish transport failures from tool-capability failures.
- Preserve the underlying worker error in case results and keep fixture checks dependent on real tool execution.

## Installation
- Install the signed Android APK to use client repairs.
- Update the PC gateway script to gateway/gateway_v12.1.py (version 12.1.1) and restart it to use gateway repairs.
- Live phone and PC gateway behavior still requires verification after installation.

## Validation and build
- Android unit tests, Android and Kotlin lint, debug APK builds, and CodeQL passed for the repair commit.
- All 15 gateway behavior and contract tests passed.
- Version: 0.9.25.0; version code: 94.
- Signed artifacts are verified for package/version identity and signing-certificate continuity before publication.
