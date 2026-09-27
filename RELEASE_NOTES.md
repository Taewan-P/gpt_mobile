# GPT Mobile AI 0.9.19.1

## Local model reliability

- Budget system instructions, the current prompt, earlier turns and tool definitions against the model's effective context capacity, with headroom for replies and tool results.
- Reject oversized current input before allocating a native engine and explain how to reduce it. Show a notice when earlier turns or tool definitions are omitted to fit.
- Bound native tool results and close conversations containing hidden tool exchanges that cannot be safely reconstructed, while retaining the loaded engine for later requests.
- Preserve saved output preferences, including unlimited output when unset. Actual context capacity remains a property of the model and runtime.

## Claude and benchmark fixes

- Omit unsupported temperature/top-p parameters for fixed-sampling Claude models, including Sonnet 5 with reasoning disabled.
- Preserve the original streaming failure without masking it with a Flow exception-transparency error or swallowing a collector exception.
- Validate local downloads and accelerator compatibility before starting a benchmark suite. Setup errors no longer create repeated failed tests or affect ratings.
- Use the same installed-model selection for benchmarks and inference, including legacy GPU/NPU editions.

## Diagnostic research

- Include a detailed [upgrade review](https://github.com/tailscale-signin/GPT_Mobile_AI-improved/blob/v0.9.19.1/docs/diagnostics-upgrade-review-2026-09-27.md) with ten ranked proposals, twelve primary sources, integration points and device acceptance checks.
- Runtime dependency upgrades and experimental acceleration options remain proposed follow-ups. This release does not claim to resolve native QNN teardown warnings; GPU/NPU inference and sustained thermal behavior still require physical-device verification.

The fixes passed 1,140 unit tests, Android lint, Kotlin formatting, APK/native-library integrity checks, CodeQL and remote diagnostics before merge. The signed-release workflow validates the exact release commit and verifies package identity and signing-certificate continuity before publishing.

Version code: **77**. Android 12 or newer. Use the ARM64 APK for most phones, including the ROG Phone 9 Pro.
