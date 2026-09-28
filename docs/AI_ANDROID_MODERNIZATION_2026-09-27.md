# AI and Android modernization

Research checked on 27 September 2026 against official releases, Maven metadata, Android documentation, and upstream source. This follows the [inference investigation](LOCAL_INFERENCE_PERFORMANCE_2026-09-27.md), [search integration](WEB_SEARCH_INTEGRATION.md), [delegation redesign](LOCAL_MODEL_DELEGATION.md), and [memory improvements](MEMORY_IMPROVEMENTS.md).

## Implemented

- Upgrade LiteRT-LM from 0.16.1 to 0.17.1. The September 16 patch fixes integer tool-call arguments; the September 9 release reduces local-attention memory overhead and expands Gemma 4 capabilities. Apple-specific optimizations are not claimed as Android improvements. [0.17.1 release](https://github.com/google-ai-edge/LiteRT-LM/releases/tag/v0.17.1), [0.17.0 release](https://github.com/google-ai-edge/LiteRT-LM/releases/tag/v0.17.0).
- Add speculative decoding **Model default / Off / On** in Local models → Settings → Performance tuning. Auto remains the default. On requires a compatible model package; it does not manufacture a draft model or guarantee a gain. A changed setting invalidates a warm engine. LiteRT process-global engine/conversation flags are serialized and restored after errors. [Versioned upstream flag contract](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/kotlin/java/com/google/ai/edge/litertlm/ExperimentalFlags.kt).
- Add optional native prefill/decode token counts and rates to local response notices and saved benchmark samples. These counters describe the **last native segment**, which may exclude earlier tool-call generations. They do not replace whole-turn usage accounting or the benchmark score's observed text-delivery rate. Estimated notices now explicitly say end-to-end and first callback. Benchmark comparison fingerprints include runtime version and native/speculative settings. [Native counter contract](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/kotlin/java/com/google/ai/edge/litertlm/Benchmark.kt).
- Forecast thermal pressure ten seconds ahead, cache queries across both backends, discard invalid/unsupported readings, and use device-provided moderate thresholds on API 35+. Android defines 1.0 as the severe-throttling threshold; it is not a percentage of CPU usage. Limits tighten when pressure is sampled; context and sampler changes apply to new requests. Relaxing limits requires 30 seconds of stable lower pressure. This also avoids repeated context-driven engine reloads near a threshold. Actual thermal, battery, power-saver, and low-memory checks still apply. [PowerManager](https://developer.android.com/reference/android/os/PowerManager#getThermalHeadroom(int)), [ADPF practices](https://developer.android.com/games/optimize/adpf/best-practices-adpf).
- Modernize the settings tabs with Material 3 PrimaryTabRow and update Compose, markdown rendering, license UI, browser, lifecycle, work scheduling, storage, and networking dependencies. Retain stable Material 3 1.4.0; 1.5 alpha is not a production requirement. AboutLibraries 15 supplies the new Compose license UI through the app's existing LibrariesContainer. [Material 3 releases](https://developer.android.com/jetpack/androidx/releases/compose-material3), [AboutLibraries migration](https://github.com/mikepenz/AboutLibraries/blob/15.2.0/MIGRATION.md).
- Upgrade MapLibre to 13.6.1 with its explicitly named OpenGL artifact. Version 13 changed the default artifact to Vulkan; retaining the existing rendering backend avoids an unmeasured driver migration. The current release includes label/glyph fixes and earlier releases reduce symbol memory and fix Android rendering stalls. [Android changelog](https://github.com/maplibre/maplibre-native/blob/main/platform/android/CHANGELOG.md).

No device or model weights were attached for sustained inference benchmarking. Build/test success does not establish an on-device latency, battery, quality, or memory improvement.

## Dependency decisions

Versions were checked against Google Maven or Maven Central, excluding alpha/beta/RC/snapshot builds. The catalog now includes previously inline document, map, Material Views, and Gson dependencies. A library's newest release is considered usable only after checking its requirements and compiling this app.

| Component | Before | Selected |
| --- | --- | --- |
| LiteRT-LM | 0.16.1 | 0.17.1 |
| Android Gradle plugin / Gradle | 9.2.1 / 9.4.1 | 9.4.1 / 9.6.0 |
| Compile SDK / target SDK | 36 / 36 | 37 / 36 |
| Kotlin / KSP | 2.3.21 / 2.3.4 | 2.4.20 / 2.3.12 |
| Core | 1.17.0 core-ktx | 1.19.1 core (KTX merged upstream) |
| Compose BOM | 2026.06.00 | 2026.09.00 |
| Lifecycle / Navigation | 2.10.0 / 2.9.8 | 2.11.0 / 2.10.2 |
| Dagger Hilt / AndroidX Hilt | 2.59.2 / 1.3.0 | 2.60.1 / 1.4.0 |
| WorkManager / Room / Browser | 2.11.2 / 2.8.4 / 1.9.0 | 2.12.0 / 2.8.5 / 1.10.0 |
| Ktor | 3.5.1 | 3.6.0 |
| AboutLibraries / Markdown renderer | 14.2.1 / 0.41.0 | 15.2.0 / 0.45.0 |
| Material Views | 1.12.0 | 1.14.0 |
| MapLibre | 11.11.0 | 13.6.1 OpenGL |
| Apache POI / Gson | 5.4.1 / 2.11.0 | 5.5.1 / 2.14.0 |
| Mockito / Robolectric | 5.23.0 / 4.16.1 | 5.24.0 / 4.17 |
| Qualcomm QAIRT | 2.47.0 | 2.50.0, with the LiteRT-LM-pinned Qualcomm dispatch rebuilt against QAIRT 2.50.0.260828 and the native-library audit regenerated |

AGP 9.4 requires Gradle 9.6 and supports API 37; Java 21 remains the project toolchain. Compile SDK upgrades enable dependency/API compatibility, while target SDK remains 36 pending an Android 17 behavior-change/device pass. [AGP compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes), [Core releases](https://developer.android.com/jetpack/androidx/releases/core), [Hilt releases](https://github.com/google/dagger/releases).

QAIRT remains pinned because the packaged HTP host libraries, stubs, skeletons, dispatch library, and SoC-compiled model artifacts must be considered together. The host/runtime set is now coordinated at 2.50.0; this does not convert existing NPU models or prove AOT context compatibility. Test the pinned model on the exact phone before release. Libraries already on their current stable versions remain pinned, including MCP Kotlin SDK 0.15.0, coroutines 1.11.0, serialization 1.11.0, DataStore 1.2.1, Activity 1.13.0, AppAuth 0.11.1, and PDFBox Android 2.0.27.0.

## Wider GitHub and research assessment

These are technical assessments of fit, not benchmarks of this app. Adding every engine would increase APK size, native-library conflicts, model conversion work, and validation burden without demonstrating a gain.

| Project / primary source | Relevant capability | Decision for this app |
| --- | --- | --- |
| [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM) | Existing Kotlin runtime, integrated speculative decoding, native counters, updated local attention | Upgrade and expose supported controls now; keep the shared engine and bounded local workers |
| [llama.cpp Snapdragon backend](https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/snapdragon/README.md) | Adreno OpenCL and Hexagon paths; wider GGUF ecosystem | Strong candidate for an isolated future adapter; current .litertlm packages cannot be passed to it |
| [MNN](https://github.com/alibaba/MNN/releases) | Android inference and EAGLE-3 support; recent cache/correctness fixes | Compare converted matching models on a device before adding a second engine; published Mac gains do not predict Android gains |
| [mllm](https://github.com/UbiquitousLearning/mllm) | Mobile multimodal inference, CPU/QNN experiments | Research candidate requiring separate native build and model conversion; no drop-in Kotlin/LiteRT compatibility |
| [ExecuTorch](https://docs.pytorch.org/executorch/stable/using-executorch-android.html) | Android AAR and CPU/GPU/vendor backends | Candidate for PyTorch-exported .pte models; conversion, tokenization, tool schemas, cancellation and backend packaging need a dedicated adapter |
| [MLC LLM](https://github.com/mlc-ai/mlc-llm/blob/main/android/README.md) | Android application with compiled model/runtime artifacts | Useful benchmark comparator; adopting TVM-generated model libraries is a separate architecture change |
| [PowerInfer](https://github.com/Tiiny-AI/PowerInfer) | Sparse activation inference, smartphone research and specialized model work | Relevant research, but desktop/pocket-device claims are not evidence for this phone or arbitrary dense packages |
| [Graphiti](https://github.com/getzep/graphiti/tree/main/mcp_server) | Temporal graph memory through MCP | Added as a self-hosted marketplace option with bounded opt-in scoped recall; no server provisioned automatically |
| [Mem0](https://docs.mem0.ai/platform/mem0-mcp) / [Supermemory](https://supermemory.ai/docs/supermemory-mcp/mcp) | Persistent scoped memory search | Existing marketplace connections now participate in opt-in automatic memory recall with validated schemas and privacy settings |

The local model research worker already reduces remote prompt size by planning searches, selecting observed sources, reading/crawling bounded pages, and constructing a compact cited evidence handoff. Memory learning is source-bound and recall is budgeted. These improvements are directly compatible with the current app; additional native engines remain independent experiments.

## Validation and device acceptance

Host checks completed September 27, 2026:

- `testDebugUnitTest`: all 1,208 tests passed; zero failures, errors or skips.
- `lintDebug`: passed with warnings, principally 924 missing translations and 327 unused resources.
- `assembleDebug`: passed for ARM64, x86-64 and universal APKs. Pinned runtime payload hashes matched; all 34 Android host libraries across the three APKs passed 16 KB ELF LOAD checks. `zipalign -c -P 16 4` passed on each APK.
- `scripts/check_room_schemas.py`, `scripts/check_android_resources.py`, `git diff --check`, and ktlint on all 28 changed Kotlin and Kotlin script files passed.

These are host build checks. A phone was not attached. Device acceptance still needs a cold/warm matrix for the same model and prompt, speculative Auto/Off/On quality checks, repeated ten-minute thermal runs, a real integer tool-call fixture, background/resume behavior, and NPU testing with matching QAIRT artifacts. Compare observed text latency, native prefill/decode, peak memory, thermal status and battery conditions; do not rank models using a character estimate alone.

Native dependencies must also be checked on a 16 KB Android image/device. ELF alignment and APK packaging can be inspected on the host, but do not replace runtime testing. [Android 16 KB guidance](https://developer.android.com/guide/practices/page-sizes).
