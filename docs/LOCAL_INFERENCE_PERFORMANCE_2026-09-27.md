# Local inference: performance research and implementation plan

Research date: 27 September 2026. Target: this Android app on the ASUS ROG Phone 9 Pro / Snapdragon 8 Elite (SM8750). Code baseline: `83fe6debc288ca7ba582e18db758959d47403d79`, LiteRT-LM 0.16.1 and QNN 2.47.0.

Follow-up implementation is documented in [AI and Android modernization](AI_ANDROID_MODERNIZATION_2026-09-27.md), including native counters, speculative-decoding controls, thermal forecasting, and dependency upgrades.

The best next experiments are native performance measurement, task-appropriate thinking, compatible GPU speculative decoding, and comparisons against the exact SM8750 NPU package. No device was attached during this investigation. Published measurements below describe other devices; they are evidence for experiments, not speed promises for this phone.

## What the repo and diagnostics establish

The app already retains a warm engine, caches compiled work, reuses compatible conversations, limits context, and serializes native inference. These are existing optimizations, not new recommendations. Relevant implementation files are `LocalEngineHolder.kt`, `LocalRuntimeImpl.kt`, `LiteRtLmAdapter.kt`, `LocalEngineMaxTokens.kt`, and `DeviceHardwareGovernor.kt`.

The supplied diagnostics show a Qwen conversation-template mismatch. Comparing the native old/new templates indicates that earlier thinking content disappeared when rendering subsequent history. This is an inference from the diagnostic strings, not a proven upstream root cause. Private prompts are intentionally excluded from this report.

The log also contains repeated provider errors and MCP connection timeouts. These can dominate perceived waiting time without indicating slow local inference. Native QNN teardown warnings and optional sampler fallback messages alone do not establish that a request failed or ran on a particular backend.

Current local throughput is an estimate: `LocalRuntimeImpl` derives token counts from visible characters/chunks and divides by total request time, including prefill. Its first callback can contain thinking rather than answer text. These numbers cannot establish native decode throughput or a fair model ranking. Short benchmark answers also magnify fixed overhead.

This change implements:

- Passing the profile reasoning preference and request restrictions to native `ThinkingConfig`. Previously the preference did not reach the local runtime. A preference change invalidates the conversation, while preserving compatible engine weights.
- One recovery attempt for a reused conversation with a template mismatch, rebuilding canonical history while retaining the engine. Recovery is allowed only before generated text/thinking or tool execution. Failed retries return a readable error without rendered prompts.
- Correct upstream Flow error handling so downstream collector exceptions are not re-emitted as provider errors or mistaken for credential-rotation failures.
- Stopping benchmark suites on execution errors/timeouts, retaining samples, and excluding stopped runs from comparisons and ratings.
- Waiting for LLM7's short local request-spacing interval, honoring genuine quota failures, applying NVIDIA Kimi K3 endpoint sampling restrictions, and bounding MCP initialization waits.

These are correctness and avoidable-latency fixes. Their on-device speed impact remains unmeasured.

## Priorities

| Priority | Change or experiment | Expected benefit | Evidence and constraint |
| --- | --- | --- | --- |
| 1 | Native counters and separate cold/warm measurements | Trustworthy optimization decisions | SDK 0.16.1 already exposes benchmark counters; current app counters are approximate |
| 2 | Thinking off for simple tasks; on when quality requires it | Less unnecessary generation | Native preference wiring fixed here; evaluate accuracy separately |
| 3 | Gemma 4 E2B GPU, speculative decoding Auto/Off/On | Higher decode throughput on compatible artifacts | Published task-dependent gains; the current model default may already enable it |
| 4 | Compare CPU/GPU with exact SM8750 NPU artifacts | Better latency, energy, and stability for each workload | Package, context, runtime, and device compatibility must all match |
| 5 | Smaller context allocations, stable prefix reuse, bounded tool context | Lower prefill/memory cost | Requires recall tests and explicit context management |
| 6 | Controlled LiteRT-LM 0.17.1 trial; later llama.cpp comparison | New optimizations and broader model choices | Separate experiments, native compatibility checks, and sustained device tests |

## Speculative decoding: strongest published opportunity

Google reports up to 2.2x decoding acceleration for Gemma 4 with MTP on an S26 Ultra GPU. Its implementation runs the drafter and target on the same hardware to avoid transfer and synchronization overhead. This supports testing the integrated runtime path rather than assuming a CPU/GPU/NPU split will help. [Google engineering article](https://developers.googleblog.com/blazing-fast-on-device-genai-with-litert-lm/)

The model publisher gives a more useful task breakdown:

| S26 Ultra, Gemma 4 E2B | Decode tokens/s |
| --- | ---: |
| GPU baseline, averaged across task types | 51.5 |
| GPU speculative, summarization | 91.7 |
| GPU speculative, code snippet | 84.4 |
| GPU speculative, free-form generation | 66.5 |
| CPU baseline, averaged across task types | 40.7 |
| CPU speculative, code snippet | 36.3 |

The baseline is aggregated, so these are not paired per-prompt speedup measurements. The CPU result also cautions against enabling speculation everywhere. The publisher requires an artifact downloaded on/after its May 5 update for the drafter. Its separate standard benchmark uses warm caches, 1,024 input tokens, 256 generated tokens, and a 2,048-token context; time to first token excludes model loading. [Model card](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm)

In the exact SDK used here, `ExperimentalFlags.enableSpeculativeDecoding` is nullable: `null` follows model policy, `true` fails if unsupported, and the flag is read at engine creation. Therefore absence of app configuration does **not** prove speculation is disabled. An implementation should expose Auto/Off/On only with capability validation, include the effective selection in the engine/benchmark identity, and isolate the global flag under the native-engine lock with restoration in `finally`. Confirm the active mode from runtime evidence before comparing results. [Pinned SDK source](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.16.1/kotlin/java/com/google/ai/edge/litertlm/ExperimentalFlags.kt)

## Backend and model selection

Google's overview reports Qwen2.5-1.5B on S25 Ultra at 298 prefill / 34 decode tokens/s on CPU versus 1,668 / 31 on GPU. This illustrates why prompt processing and generation must be measured independently: GPU selection can improve the first while slightly reducing the second. [Runtime overview](https://developers.google.com/edge/litert-lm/overview)

For this phone, test the app's existing generic CPU/GPU artifacts against their catalogued `sm8750` NPU variants, recording the actual loaded backend and any fallback. Google's NPU guide requires a SoC-specific `.litertlm` model plus the associated QAIRT and dispatch libraries; its Gemma 3 1B example has a compiled 1,280-token context. Raising an app setting cannot enlarge that compiled graph. [NPU setup](https://developers.google.com/edge/litert/next/litert_lm_npu)

Qualcomm's Qwen3 packages use its Genie/GenieX deployment stack. They are a separate integration path, not drop-in replacements for a LiteRT-LM model. Compare any such experiment with the same workload and retain its own SDK/package compatibility record. [Qualcomm model card](https://huggingface.co/qualcomm/Qwen3-1.7B)

Use a shortlist: a small model for extraction/routing, a general model for conversation, and a coding model only if it wins the actual coding tests. Switching resident models has a loading cost; measure complete workflows before adding automatic routing. Keep the current single-engine scheduling policy until concurrent execution demonstrates a benefit under the phone's shared memory and thermal limits.

Quantization reduces weight traffic and memory, but quality and kernel support determine whether it helps in practice. AWQ is primary research on activation-aware low-bit quantization and specialized inference kernels; its published acceleration is not a prediction for this Android stack. Test runtime-supported artifacts, with factuality, multilingual, JSON, tool-call, and code checks, rather than selecting the smallest file unconditionally. [AWQ paper](https://arxiv.org/abs/2306.00978)

## Thinking, context, and quality

Original Qwen3 supports switching between thinking and non-thinking modes; its maintainers describe both template controls and model-specific sampling advice. Use the model's supported mode rather than merely hiding thought text, which saves no generation work. [Qwen documentation](https://qwenlm.github.io/blog/qwen3/)

The new reasoning wiring is a direct improvement here. Suggested evaluation groups are simple extraction/rewriting with thinking disabled and difficult reasoning with it enabled. Avoid interpreting a shorter but incorrect answer as a speed improvement. Any future thinking budget should be separate from the user's answer-length preference and clearly represented in benchmark configuration.

The app currently requests the automatic context ceiling: up to 8,192 tokens on high-RAM phones, capped by the model and thermal policy. Experiment with 2,048 / 4,096 / 8,192 allocations for GPU/CPU; these are proposed test points, not established optimal settings. Keep output limits separate. Test long-history retrieval and explicit context-overflow behavior before changing defaults.

Preserve a stable system prefix and compatible native conversation whenever possible. Select only relevant tools and concise tool results, while preserving enough information to answer correctly. Repeatedly changing tool definitions or rebuilding history can erase cache benefits. The template-recovery fix is bounded because blindly replaying a tool-bearing request can repeat side effects.

Google describes native session save/restore as a way to avoid repeated prefill. Neither the pinned Kotlin `Conversation` API nor its inspected 0.17.1 version exposes a direct save/restore method, so persistent KV snapshots require an API/native integration investigation. Disk-backed snapshots would also need model/tokenizer/template/version invalidation and handling for private conversation data. They are not an existing feature of this patch. [Session-management description](https://developers.googleblog.com/blazing-fast-on-device-genai-with-litert-lm/)

## Runtime, thermal behavior, and app overhead

LiteRT-LM 0.17.0 advertises reduced local-attention memory overhead and longer-context support; 0.17.1 fixes integer tool-call handling. Trial the latter against 0.16.1 with the same model hashes, prompts, and native libraries. An upgrade needs NPU loading, multi-turn reasoning, cancellation, tool calls, and repeated unload/reload checks. The dependency is deliberately unchanged in the diagnostic fix. [0.17.0 release](https://github.com/google-ai-edge/LiteRT-LM/releases/tag/v0.17.0), [0.17.1 release](https://github.com/google-ai-edge/LiteRT-LM/releases/tag/v0.17.1)

The app already has thermal policy. Evaluate sustained sessions and add hysteresis before making it more aggressive: frequent context changes can rebuild engines, and lowering top-k changes sampling without a demonstrated large reduction in transformer work. Android recommends thermal-headroom monitoring and cautions against polling more frequently than once every ten seconds. Handle unsupported readings and record thermal conditions with performance results. [Android Thermal API](https://developer.android.com/games/optimize/adpf/thermal)

Profile UI delivery separately. The native callback currently feeds an unlimited channel. A possible improvement is bounded/coalesced **text delivery** at a measured UI cadence, while preserving every text delta and never dropping tool, error, or completion events. Do not replace the stream with a conflated channel that loses content. Profile queue depth and Compose work before implementing this.

The optional OpenCL sampler warning deserves a measured investigation: determine whether the fallback is material and whether an ABI-matched library is available from the pinned runtime distribution. A warning alone is insufficient reason to bundle an unrelated binary.

For a later comparison, upstream llama.cpp supports Snapdragon CPU, Adreno OpenCL, and an experimental Hexagon backend, including v79 artifacts. This offers a GGUF path but requires a new runtime adapter, packaging, and lifecycle work. Its documented NPU virtual-session addressing limits and supported operations matter even when the phone has ample RAM. Start with its benchmark tool and a small compatible model before considering an app migration. [Snapdragon backend documentation](https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/snapdragon/README.md)

## Reproducible experiment plan

1. Pin app commit, runtime/driver versions, model SHA-256, tokenizer/template, backend, context, threads, sampling, thinking, and speculative mode. Separate cold process/model load, warm engine/new conversation, and warm multi-turn reuse.
2. Add native counters using the pinned SDK's `enableBenchmark` plus `Conversation.getBenchmarkInfo()`. It provides initialization time, prefill/decode token counts and rates, and first-token time. Validate multi-round/tool accounting before summing fields. Treat benchmark instrumentation as its own engine configuration and label approximate fallback counters explicitly. [Native benchmark API](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.16.1/kotlin/java/com/google/ai/edge/litertlm/Benchmark.kt)
3. Measure model load, prompt processing, first callback, first visible answer, decode throughput, total completion time, peak memory, errors, and thermal state. Do not use whole-request approximate TPS as native decode speed. Use a proper power measurement method for energy claims; battery percentage alone is too coarse for short runs.
4. Begin with a small screen: one general model, CPU/GPU, default context and no tools. Run equal input/output workloads plus realistic completion tasks. Then vary one factor: speculative mode, context, or CPU threads. For NPU, respect each package's fixed context and compare within its common workload range.
5. Use at least five warm repeats per screening condition and report median and spread. Run longer sustained trials for finalists; use more repetitions before treating tail latency as stable. Alternate experiment order and control charge state, power mode, display, ambient conditions, and cooldown.
6. Keep quality evaluation independent: factual questions with known answers, multilingual responses, valid structured output, tool arguments, executable code tests, and multi-turn recall. Use native token counts within a model; use successful completed tasks per elapsed time for cross-model comparisons with different tokenizers.
7. Adopt a configuration only when its improvement exceeds run-to-run noise, quality meets the agreed task threshold, and cancellation, memory, and sustained thermal behavior remain acceptable. Save raw measurements with the configuration. Do not multiply vendor headline speedups together.

The next implementation should be the measurement layer and capability-aware speculative-decoding experiment. Those provide the evidence needed to choose the fastest reliable configuration for this specific phone.
