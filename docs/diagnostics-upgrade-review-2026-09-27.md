# Diagnostic fixes and upgrade review — 27 September 2026

Reviewed repository baseline: `b2c08e0` (v0.9.19.0). Evidence: the supplied diagnostic session from approximately 06:05–06:58 UTC, plus the primary sources linked below, checked on 27 September 2026. Raw logs, private server addresses, account identifiers, prompts and credentials are deliberately excluded from this report.

The immediate failures are request construction and context accounting problems. The log also identifies useful native-runtime experiments, but does not establish that the GPU or NPU is globally unavailable.

## What the log establishes

| Evidence | Interpretation | Resolution in this PR |
| --- | --- | --- |
| Native input lengths of 3,723 and 1,842 against a 1,024-token capacity; later 12,333 against 8,192 | Increasing engine capacity alone did not fix admission of tools, history and fixed input. An output preference cannot enlarge a compiled model's context. | Plan local input against the effective engine capacity, including tools, history, system text and response/result reservations. Reject oversized fixed input before allocating native state. |
| 23 Anthropic failure traces accompanied by Flow exception-transparency errors; requests use Sonnet 5 with benchmark temperature zero | The provider rejects custom sampling; broad exception handlers then obscure the original failure. | Omit temperature/top-p for fixed-sampling Claude families, including when reasoning is disabled. Move stream error handling upstream of collection and outside chunk emission. |
| 40 missing-download attempts, each logged twice, during repeated benchmark suites | A setup problem is being measured repeatedly as model failure. | Resolve and validate the installed local package before creating a benchmark run. Share the same legacy GPU/NPU selection logic with inference. |
| GPU delegation reaches all 2,068 decode operations, 1,107 prefill operations and 1,477 vision operations in the observed runs | GPU initialization and delegation succeed. These lines do not prove every subsequent request succeeds. | Preserve GPU selection and working fallback behavior. |
| QNN creates/reuses contexts; 16 warnings mention a 2.44.0 context binary with runtime 2.47.0; repeated memory-handle deregistration errors occur during teardown | A compiler/runtime provenance and lifecycle investigation is justified. The warnings alone do not establish corruption, a leak or a failed inference. | Keep the current runtime versions; define a controlled compatibility experiment below. |
| GitHub MCP optional GET returns 405 while POST initialization/tool discovery succeeds | A missing optional SSE channel is allowed by the transport specification. | No speculative transport rewrite. |
| Another MCP optional GET returns 404; private endpoints later time out or refuse connections | Classify by HTTP method and session state. Host/network failure remains possible; an optional-channel error does not prove POST tools are unavailable. | Recommend targeted health/recovery work, without blanket retries. |
| Initial native-loader probe and linked-sampler fallback warnings are followed by successful loading | These warnings are not sufficient evidence of a missing operational native library. | Keep actionable failures visible; improve diagnostic classification separately. |
| ASUS restricted-setting access appears in a framework finalizer/GC stack | The excerpt does not establish an app-owned permission request or fatal application crash. | Do not request extra settings permissions as a proposed fix. |

Anthropic documents the Sonnet 5 sampling restriction [1]. Kotlin documents why a failed downstream emission must not be caught and followed by another emission [2]. MCP explicitly allows GET 405 for the optional server stream [3].

## Implemented behavior and limits

`LocalContextPlanner` reserves capacity before selecting tool schemas and complete historical turns. It retains the opening goal when it fits, then recent turns; an oversized opening turn no longer bypasses compaction. UTF-8 estimates replace an English-only character assumption. The current user message is never silently truncated. Omitted history/tools produce a persistent notice.

Native tool responses share a per-turn byte allowance. Original local tool error details still pass through that bound. A conversation containing native tool exchanges closes after the turn because the app's visible text-history fingerprint cannot reconstruct its hidden tool state; the loaded engine can remain warm. Plain text conversations remain reusable.

Saved output settings and unlimited-output preferences are unchanged. Context reservations are estimates, not a guarantee that an unlimited generation or arbitrarily long native tool loop can fit. Image cost is currently estimated at 256 tokens per image; actual visual tokenization can differ. Native token accounting and per-round admission remain the next context improvements.

Regression coverage includes oversized fixed prompts, a single oversized historical turn, multilingual history, large tool catalogs/results, native tool-session cleanup, installed model selection, Claude request serialization, HTTP/SSE collector failures and cancellation-compatible propagation. Benchmark setup errors are shown before any suite record is created; failures after successful setup still count normally.

## Prioritized upgrades

These are proposed follow-ups, not features claimed to be enabled by this PR. Effort is relative: small = isolated change; medium = several components plus device checks; large = native integration or catalog migration.

| Priority | Upgrade | Why it matters here | Effort / main uncertainty |
| --- | --- | --- | --- |
| P1 | LiteRT-LM 0.17.1 with coordinated native verification | Upstream tool integer fix and reduced local-attention memory overhead are relevant to local tools/context pressure. | Medium; ABI, dispatch and package compatibility |
| P1 | Exact context accounting and tool selection | Estimated admission cannot fully represent tokenizer, template, image or native tool-loop growth. | Large; usable counting hooks in the pinned runtime |
| P1 | QNN package/runtime compatibility manifest | The log mixes compiler 2.44.0 and runtime 2.47.0. Current catalog metadata cannot express the full tested tuple. | Medium; availability of matching compiled assets |
| P1 | Better native benchmark measurements | Existing timings cannot by themselves explain prefill, loading, tool waits or sustained memory/thermal behavior. | Medium; native metrics availability |
| P2 | Optional Gemma 4 speculative decoding | MTP can improve CPU/GPU decoding on supported model packages. | Medium; gains depend on model, task and device |
| P2 | Thermal headroom and hysteresis | Current pressure policy can shrink context to 1,024/2,048 and alter sampling, leading to rebuilds and changed behavior. | Medium; phone support and actual native pacing controls |
| P2 | MCP health classification and safe session recovery | Separate unsupported optional streams, expired sessions, authorization and host reachability. | Medium; SDK behavior under each failure mode |
| P2 | Demand-based vision initialization | Text-only work currently initializes vision for vision-capable models. The log shows a substantial vision graph. | Medium; cold-start tradeoff when an image first arrives |
| P2 | Provider capability policy | A model-name change should not repeatedly break sampling, thinking or benchmark settings. | Medium; metadata does not expose every restriction |
| P3 | Diagnostic event classification and warning aggregation | Repeated native warnings obscure the errors that actually fail a request. | Small; avoid hiding causal evidence |

### 1. Evaluate LiteRT-LM 0.17.1 as a complete runtime change

The repo pins 0.16.1. The latest release found is 0.17.1, published September 16, with a tool-call integer-type correction. Release 0.17.0, September 9, reports local-attention memory reductions, longer-context support and expanded Gemma 4 12B features [4]. These changes make 0.17.1 the first upgrade candidate; they are not a published fix for this log's QNN teardown messages.

Change `gradle/libs.versions.toml`, inspect `LocalRuntimeImpl` API changes, and update the native-library audit only after verifying the actual resolved APK contents. Keep the QNN libraries and dispatch plugin in a tested combination; do not independently replace one shared library. Use `scripts/check_local_runtime_apk.py` and the provenance recorded in `docs/audits/local-runtime-native-libraries.json`.

Acceptance: existing unit/lint/build gates, APK native integrity, then cold/warm GPU and NPU text, integer tool arguments, image input, cancellation, unload/reload and context-boundary runs on the target phone. Compare against 0.16.1 with identical model bytes. Larger-model support is not evidence that 12B should become the phone's default.

### 2. Replace estimates with observed context usage

Engine capacity covers input and generation, while conversation output limits are a separate control [5]. Investigate the pinned Kotlin/native API for exact token counting and remaining capacity. If unavailable, make estimates explicitly model-specific and record observed native counts where exposed.

Extend `LocalContextPlanner`, `LiteRtLmAdapter` and `LocalRuntime` with separate system/template, text, image, schema and tool-result costs. Select relevant tools or expose tool discovery instead of sending every schema. A query-relevance selector is a future improvement over the deterministic first-fit selection in this PR. Check space between native tool rounds before admitting another response; checkpoint a summary and start a fresh conversation when necessary. Preserve the user's original text and disclose omitted context.

Acceptance: multilingual/code/JSON inputs, ten-image boundaries, oversized single turns and repeated tool rounds near the actual model limit. Do not silently replay a tool with side effects after rebuilding a conversation.

### 3. Make QNN compatibility a versioned catalog contract

Google's NPU instructions require appropriate Qualcomm libraries, dispatch support and a model compiled for the target SoC. Their sample packages also have fixed context capacities [6]. General Qualcomm LiteRT support is useful background, but is not certification that any arbitrary LLM package will work [7].

Extend `CatalogEntry`/`SocVariant` with model SHA-256, compiler/QAIRT version, validated runtime and dispatch revision, context capacity, prefill signatures, modality support and the tested SoC. The repo already has per-SoC files and context fields; enrich them rather than adding a second catalog. Verify the selected tuple in `LocalModelCompatibility` and surface an actionable setup error.

Compare a regenerated 2.47-compatible package, if available, against the existing 2.44 package under the same runtime. Repeat generate/cancel/close/load cycles and sample PSS/FD counts. Only then attribute persistent growth or teardown failures to a particular combination. The log alone does not justify downgrading QNN.

### 4. Improve benchmark attribution before using speed rankings

The repo already retains median/p95 timing, configuration fingerprints, failures and separate local/remote scores. Keep those. Add engine-load, prefill, first-text, decode, tool-wait and cleanup durations; separate cold-engine, warm-engine/new-conversation and reused-conversation runs. A loaded unrelated model should not qualify a run as warm for the tested model.

`LocalRuntimeImpl` currently estimates tokens from characters/chunks and divides by total generation duration; benchmark text-decode timing is a different measure. Prefer native/provider token counts when supplied and label estimates explicitly. `peakClientPssKb` currently reflects samples taken after tests, not a measured in-flight peak: sample during execution or relabel it. Never present client PSS as remote-server memory use.

Add a sustained, repeatable local workload with temperature/headroom, battery/charging, actual accelerator, model hash and runtime tuple recorded. Compare task success as well as speed. Select the default backend per measured model/device/task instead of assuming NPU is universally faster.

### 5. Add opt-in speculative decoding for supported Gemma 4 packages

Google documents Gemma 4 MTP on CPU/GPU and reports workload-dependent gains up to 2.2× on mobile GPU and 1.5× on CPU [8]. Those are upstream results, not a prediction for this phone. The Android API documents `ExperimentalFlags.enableSpeculativeDecoding` set before engine initialization [9]. A draft/MTP graph appearing in a log is not proof the option is active.

Add a capability-gated experimental toggle. Set it under the engine initialization lock, include it in the engine key and benchmark fingerprint, and restore the intended setting when switching profiles. Do not toggle a process-global flag while an existing engine is running. Benchmark the installed E2B/E4B packages on GPU and CPU before enabling by default; do not extrapolate this path to QNN/NPU.

### 6. Govern sustained work using thermal headroom

Android recommends thermal headroom alongside thermal status. Headroom may be unavailable/NaN, and polling it more than once every ten seconds is discouraged [10]. Add one shared monitor with status fallback and hysteresis, then use it in `DeviceHardwareGovernor` and benchmark conditions.

The present governor clamps capacity and changes top-k under heat, low battery or power-save pressure. Evaluate reducing workload, deferring the next turn, or a supported native performance hint before shrinking KV capacity. UI publication throttling alone does not prove native compute is cooler. Keep any context or quality change visible, retain memory-pressure protection, and measure actual sustained throughput/rebuild count before altering the defaults.

### 7. Make MCP recovery method- and operation-aware

The repo already pins Kotlin MCP SDK 0.15.0, the newest release found [11], and `McpClientManager` already invalidates failed sessions. Build on that behavior.

Classify optional GET 405 as unsupported SSE, not a dead server. Distinguish an optional GET 404 from a session-expired response carrying an MCP session ID. The latter requires fresh initialization under the negotiated transport rules [3]. Test the SDK's behavior before adding app-level retries. Add a single safe reconnect path for discovery/read operations and separate DNS, refusal, timeout and authorization diagnostics. Preserve unknown outcomes for writes instead of automatically repeating them. Test network changes and app background/foreground transitions with a controllable server.

### 8. Initialize optional modalities only when needed

`LocalRuntimeImpl` already supplies separate vision and audio backends and a cache directory. Avoid presenting those as new features. Instead, evaluate selecting a text-only engine configuration until images appear, using the optional backend configuration described in the Android API [9]. Include modality state in the engine key and measure the cost of switching on the first image. Keep vision enabled when replayed history actually requires it. Separate text-only benchmarks from multimodal initialization overhead.

### 9. Centralize provider capabilities

Extend the existing Anthropic thinking/sampling policy into a tested capability table for aliases, dated snapshots and provider-prefixed IDs. Keep request serialization and settings controls consistent. Claude's models endpoint exposes input/output limits and capabilities such as thinking and structured output [12]; it does not eliminate the need for documented sampling policy. Cache metadata without assuming a temperature-support field exists.

Add tests for supported versus fixed sampling, explicit reasoning disablement, omitted-thinking defaults, tool replay and token limits when updating a model family. Benchmark descriptions must state when deterministic temperature zero is unavailable. Do not turn a rejected request into an automatic model/account switch.

### 10. Preserve useful diagnostics while reducing repetition

Aggregate identical native warnings by engine/run and retain first/last occurrence, count and the first related failing event. Distinguish initialization, prefill, decode, tool execution and cleanup failures. Show effective context capacity alongside requested output length. Keep raw export available with existing redaction; do not suppress all QNN or native warnings just because some are harmless in this session.

## Suggested delivery order

1. Merge the request/context/preflight fixes after repository gates pass. Reproduce the original Claude and local failures on the device.
2. Add benchmark attribution and compatibility metadata, then evaluate LiteRT-LM 0.17.1 against the existing baseline.
3. Test speculative decoding, thermal policy and demand-based vision independently so regressions have an identifiable cause.
4. Follow with exact context/tool-loop accounting and MCP recovery tests. Promote experimental defaults only with recorded device evidence.

CI can verify Kotlin behavior, formatting, Android lint and packaging. It cannot verify Qualcomm execution, actual tokenizer/image costs, sustained thermals or native memory lifetime without the physical device. This review does not claim those hardware checks have passed.

## Primary sources

1. [Claude Sonnet 5 migration guide](https://platform.claude.com/docs/en/models/sonnet-5/migration-guide)
2. [Kotlin Flow exception transparency](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-flow/)
3. [MCP 2025-11-25 Streamable HTTP transport](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports)
4. [LiteRT-LM releases](https://github.com/google-ai-edge/LiteRT-LM/releases)
5. [LiteRT-LM Kotlin configuration source](https://github.com/google-ai-edge/LiteRT-LM/blob/main/kotlin/java/com/google/ai/edge/litertlm/Config.kt) — moving main; verify APIs against the chosen release.
6. [LiteRT-LM NPU deployment](https://developers.google.com/edge/litert/next/litert_lm_npu)
7. [Qualcomm acceleration in LiteRT](https://developers.google.com/edge/litert/next/qualcomm)
8. [Gemma 4 in LiteRT-LM](https://developers.google.com/edge/litert-lm/models/gemma-4)
9. [LiteRT-LM Android API](https://developers.google.com/edge/litert-lm/android)
10. [Android thermal APIs](https://developer.android.com/games/optimize/adpf/thermal)
11. [Kotlin MCP SDK releases](https://github.com/modelcontextprotocol/kotlin-sdk/releases)
12. [Claude models API](https://platform.claude.com/docs/en/api/models/list)
