# GPT Mobile AI 0.9.18.0

## Local model marketplace
- Add Gemma 3 270M Instruct, Qwen3 0.6B, Qwen2.5 Coder 3B Instruct, Phi-4 Mini Instruct, Gemma 3n E4B Instruct and FastVLM 0.5B.
- Offer chipset-specific Qualcomm NPU packages for Gemma 270M and FastVLM alongside separate LiteRT CPU/GPU editions.
- Pin new downloads to verified repository revisions and file sizes, enforce context limits, and route supported NPU vision encoders correctly.
- Keep the newer bundled catalogue when the online catalogue is older. Exclude incompatible web, desktop, embedding and raw-checkpoint packages from chat discovery.
- Remove unsupported GGUF and unavailable catalogue entries. No image-generation models are added.

## Memory and conversations
- Add adjustable memory sensitivity to control what automatic memory capture retains.
- Simplify the composer with a background-matched input and subtle themed placeholder.
- Reorganize conversation settings with Options as the default tab, combining tool and response controls, and a searchable Models selector.

## AI platforms
- Add NVIDIA NIM provider configuration and model discovery.
- Group free providers in a collapsible section and simplify provider cards.
- Allow confirmed provider deletion while retaining historical conversations and messages.

## Reliability
- Improve handling of WebView renderer failures and repeated routing DNS failures.
- Stop unsolicited localhost search probes and strengthen QNN package checks before native loading.

Version code: **74**. Android 12 or newer. Use the ARM64 APK for most phones. Model access may require accepting the publisher's terms. Hardware acceleration depends on the phone, runtime and selected package; on-device execution of the new models has not been verified on every supported chipset.
