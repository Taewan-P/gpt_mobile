# QNN and LiteRT-LM integration audit

Audit date: 2026-09-25. Branch: `feat/queued-prompts-model-controls-debug-marketplace`.
Starting revision: `6a38bc8`, including the merged marketplace update in #495. Reconciled with `c5e10c1` to preserve the newer Fact Vault and inline tool traces, then `dbaa05b` to retain 0.9.12.0 release preparation and marketplace formatting.

## Result

The existing implementation had consequential integration defects. In particular, a QNN load could silently execute on GPU/CPU and keep showing QNN, including with automatic fallback disabled. This audit repairs those paths and adds regression coverage. Native execution on a physical phone remains a release verification requirement.

The September 27 modernization refreshes the JNI hash manifest from the published 0.17.1 AAR; QAIRT and the bundled dispatch library remain unchanged. See [upgrade validation](../AI_ANDROID_MODERNIZATION_2026-09-27.md).

QNN remains the saved default. Both runtime options now use **LiteRT-LM 0.17.1**; the QNN option selects its Qualcomm NPU dispatch path. **QAIRT 2.47.0** supplies the matching host, stub and skeleton libraries. QNN is not a separate LLM implementation, and CPU/GPU execution is reported as LiteRT-LM.

## Findings and repairs

| Priority | Finding | Repair |
| --- | --- | --- |
| High | LiteRT-LM retried accelerators internally, hiding failure from QNN fallback policy. The adapter could retry CPU after fallback was disabled. | Native loading uses the requested accelerator exactly. The router handles production retries, publishes the actual backend/spec, and persists LiteRT only after successful fallback. Disabled QNN fallback is terminal. |
| High | DI shared the same mutable native runtime behind both router branches. Send routing guessed from whichever conversation appeared open. | Separate runtime instances; only the successfully initialized instance receives conversation operations. Failed/cancelled loads release resources. |
| High | The APK mixed checked-in QNN libraries with different 2.47.0 Maven copies through `pickFirsts`. | One pinned QAIRT source. Removed the unused interpreter delegate, old host/stub/skeleton files, and compiler plugin. Compiled NPU packages use the dispatch/AOT route. APK hash checks now run in debug/release build workflows and the local validation script. |
| High | Readiness only checked a V79 skeleton and dispatch file; it did not require Qualcomm hardware or every dependency. | Device/ABI gate plus the correct HTP host/stub/skeleton set. Split APK extraction is supported and scoped to the app installation version. Readiness is labeled as prerequisites, not proof of execution. |
| High | Files truncated by up to 5% could be considered complete. Imports advertised GGUF and unrelated binary formats despite using LiteRT-LM. | Exact HTTP response/range length validation, package signature checks, strict ready-file length checks and `.litertlm` import filtering. Imports are written to a temporary file before replacing a valid model. |
| High | Cancelling a queued local run could cancel a different run that owned the engine. Conversation fingerprints were updated outside the generation lock. | Request cancellation, tool bindings and conversation bookkeeping stay under the exclusive generation lock. Native tool execution is tied to the active request job. |
| Medium | GPU library visibility declarations were missing. | Added optional `libOpenCL.so` and `libvndksupport.so` declarations, retaining `libcdsprpc.so` for Qualcomm. |
| Medium | QNN changed the spec to NPU plus a dispatch directory, but cache checks compared it with the original spec. Backend changes did not invalidate warm caches. | Track requested and dispatched specs; compare the current saved backend when checking cache validity. |
| Medium | Settings collected the backend preference without displaying its selector. Debug UI hardcoded NPU. | Restored QNN/LiteRT controls in Advanced Settings with active accelerator/context/fallback information; diagnostics no longer infer execution from a preference or installed library. |
| Medium | Context limits were changed invisibly inside native loading, after history compaction; smaller RAM devices could request more context than larger devices. | The same explicit budget reaches compaction and native loading. Low-memory pressure is exposed through the policy. App ceilings: 1024 below 6 GiB, 4096 for the middle tier, 8192 at 12 GiB+, with compiled NPU context limits taking precedence. |
| Medium | Selecting CPU/NPU for the LLM also selected it for the vision encoder. | Independent vision backend, defaulting to GPU for the current multimodal packages, matching the Gallery integration for Gemma 3n. |
| Medium | The idle-unload method had no scheduler and could cancel a busy request after checking a stale timestamp. | Periodic idle cleanup is wired; idle and memory-pressure cleanup acquire the generation lock without cancelling a busy engine. |
| Medium | Download/catalog UI offered vendor NPU packages whose runtimes were absent. New Qualcomm profiles selected GPU despite downloading the NPU variant. | Prefer a supported Qualcomm NPU by default and select the corresponding artifact; otherwise choose the generic CPU/GPU package. Disabled accelerator choices cannot be saved. |
| Medium | The hosted catalog URL pointed at a different repository. | Catalog updates now come from this repository's `main`, with existing cached/bundled fallbacks. |

## Feature wiring

| Feature | Verified path / boundary |
| --- | --- |
| QNN default and preference restore | `LocalRuntimeBackend.DEFAULT`, DataStore/repository observation, backup preferences and Advanced Settings. |
| Qualcomm support | SM8550/V73, SM8650/V75, SM8750/V79 and SM8850/V81; a matching catalog artifact is also required. Actual driver/model compatibility still requires device execution. |
| Other vendors / emulator | CPU/GPU through LiteRT-LM. Tensor/MediaTek NPU choices are disabled because their vendor dispatch libraries are not shipped. |
| Automatic fallback | QNN → LiteRT GPU → CPU, where the installed package supports those backends. Successful fallback changes the selected runtime. Failure/cancellation does not claim success. |
| Streaming / cancellation | Text, thought-channel deltas, phases, completion/error, request-scoped tool cancellation, native stream cleanup and serialized multi-profile access. Cancelled streams cannot mark a conversation successfully consumed. |
| Warm conversation reuse | Model/spec, profile, sampler, system instruction, tool definitions and history fingerprint must match. Backend preference changes reload. |
| Tools | Model capability and profile assignment gate registration; SDK automatic function calling invokes the bound app/MCP executor and emits tool timeline events. NPU uses the model's sampler defaults, as in Gallery. |
| Images | Capability-gated image bytes and history, maximum ten images per message, separate GPU vision executor. CPU language fallback does not imply CPU vision support. |
| Documents | The shared repository prepares document excerpts before context budgeting and the local adapter. Integration coverage checks prior/current document text reaches the native boundary once; raw document bytes are not sent to LiteRT-LM. |
| Audio | No local audio executor is configured. Audio inference is not claimed by this integration. |
| Token metrics | TTFT and durations are measured; token counts/speed are estimates from streamed text, not tokenizer-verified counts. |
| Imports | `.litertlm` package/header validation. Imported packages lack verified catalog vision/tool metadata and are handled conservatively. This is not a GGUF engine or model converter. |
| Model integrity | Full received byte count, minimum size and signature checks. Catalog artifacts are commit-pinned. Full cryptographic weight checks are not claimed; the optional SHA validator requires a trusted expected digest. |
| APK integrity | `scripts/check_local_runtime_apk.py` checks pinned library hashes, duplicate ZIP entries, absent old integrations and 16KB AArch64 LOAD alignment. The manifest is `local-runtime-native-libraries.json`. |

## Validation

Validated the runtime changes on top of `c5e10c1` before incorporating the subsequent version/signing-workflow and formatting-only commits:

- Full JVM/Robolectric suite: **913 tests passed, 0 failed, 0 skipped** (`:app:testDebugUnitTest`).
- Kotlin formatting: all 48 changed/new Kotlin files passed ktlint 1.3.1.
- Android resource preflight: 25 XML files passed; changed workflows parsed; validation shell syntax and `git diff --check` passed.
- Debug APK build passed (`:app:assembleDebug`): arm64-v8a, x86_64 and universal APKs.
- APK integrity checks passed on all three: 12 pinned runtime libraries in arm64, 1 in x86_64 and 13 in universal; no duplicate ZIP entries or obsolete delegate/compiler payloads; all checked AArch64 LOAD segments satisfy 16KB alignment.

Tests use fake/mock native engines; they verify routing and SDK configuration, not Hexagon execution. The document integration test uses the actual repository → adapter → native-boundary path. The final combined branch also runs the repository CI suite. No physical-device or optimized-release execution was performed for this audit.

## Physical-device follow-up

The most important remaining check is a real Qualcomm NPU run with the packaged APK, pinned model and device firmware. The existing Qualcomm dispatch binary is unchanged and hash-pinned; its source/build provenance and compatibility with this SDK combination remain unverified. A successful build cannot establish dispatch ABI, QNN graph compatibility, driver access, or native crash freedom.

1. Install the APK on an eligible arm64 Snapdragon device. Record the SoC and Android/firmware version. Download its matching Gemma 3 NPU package.
2. Choose QNN and disable automatic fallback. Generate two turns. Confirm Advanced Settings reports QNN/NPU and no GPU/CPU fallback; inspect Logcat for actual HTP initialization.
3. Repeat with fallback enabled and an unavailable NPU path. Confirm a successful GPU/CPU load changes the selection to LiteRT; an unsupported package reports failure rather than success. NPU-only artifacts may require importing/downloading their generic package for CPU/GPU use.
4. Switch the backend while an engine is warm; verify the next request reloads and the displayed active backend follows the execution.
5. Queue two local profiles, cancel the waiter, and verify the active response continues. Cancel during an MCP tool call and confirm the tool stops.
6. Run Gemma 3n vision with CPU language execution and GPU vision, then a supported tool model with location/web tools. Test a long document against its small context window.
7. Verify idle cleanup, background response continuity and memory-pressure behavior. Repeat with an optimized release APK and a 16KB-page device where available.

## Primary implementation references

- [LiteRT-LM Kotlin getting started, v0.16.1](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.16.1/docs/api/kotlin/getting_started.md)
- [LiteRT-LM Kotlin engine/configuration, v0.16.1](https://github.com/google-ai-edge/LiteRT-LM/tree/v0.16.1/kotlin/java/com/google/ai/edge/litertlm)
- [Official Qualcomm NPU LLM setup](https://developers.google.com/edge/litert/next/litert_lm_npu)
- [LiteRT Qualcomm HTP requirements and AOT libraries](https://github.com/google-ai-edge/LiteRT/blob/main/litert/vendors/qualcomm/doc/HTP_INSTRUCTIONS.md)
- [Gallery LLM/vision/sampler wiring at 61ab466](https://github.com/google-ai-edge/gallery/blob/61ab46679a2e13e2d5af26b67fcbe647d07aa309/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt)
