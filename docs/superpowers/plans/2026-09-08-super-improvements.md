# Context, background, streaming, and recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Follow the approved spec and execute these tasks with focused checks.

**Goal:** Deliver persistent model-aware compaction, reliable active background inference, visually smooth streaming, and bounded network recovery.

**Architecture:** Keep the full Room transcript authoritative. Maintain a derived context checkpoint per chat/platform and use provider-native compaction or a same-platform summary. Reuse the foreground coordinator and separate live UI snapshots from durable writes.

**Tech Stack:** Kotlin 2.3.21, Compose, Room 2.8.4, Ktor 3.5.1, LiteRT-LM 0.11.0, JUnit/coroutine tests and Android instrumentation.

**Spec:** `docs/superpowers/specs/2026-09-08-super-improvements-design.md`

## Global Constraints

- Implementation subagents use `xai/grok-4.6` through opencodex.
- Preserve unrelated `.serena/project.yml` and `.ui-test/` changes.
- Keep full messages, revisions, attachments and traces; compaction never deletes the transcript.
- Never mix different Platforms' context or move Local Platform summarization to the cloud.
- Automatic compaction plus manual action; ask once for unknown model context limits.
- Native where verified supported; same-platform text fallback; failed compaction pauses without lossy truncation.
- Short adaptive streaming smoothing, approximately 100 ms bounded visual delay; user judges final phone smoothness.
- Emulator is verified 60 Hz. No unmeasured 90/120 Hz claims.
- Ordinary streaming remains default; supported OpenAI resumable replies are opt-in.
- Keep existing cancellation, tool execution limits, and non-replay on process restart.
- Reuse installed dependencies and source-backed APIs. Parent owns integration/commits; workers do not commit shared files.

## Task 1: Active local lifecycle and foreground wakefulness

**Ownership:** `GPTMobileApp.kt`, `LocalRuntime.kt`, `LocalEngineHolder.kt`, `AgentRunForegroundService.kt`, `AndroidManifest.xml`, related focused tests. Avoid the coordinator and chat UI.

**Interface:** Add a safe trim/idle-unload operation through the existing LocalRuntime boundary; preserve forceful explicit unload/cancel semantics. The foreground service already observes `coordinator.activeRuns` and can scope wakefulness to that lifetime.

- [ ] Extend `LocalEngineHolderTest` to start a suspended generation, request trim, and assert the text stream completes without cancellation; verify idle trim unloads.
- [ ] Run the focused test and record the expected failure before implementation.
- [ ] Implement the smallest shared trim operation that does not interrupt active inference; route `onTrimMemory` to it.
- [ ] Add bounded partial wake-lock ownership to the existing foreground service, cleaning up every terminal path; avoid battery-exemption flows.
- [ ] Run focused lifecycle/service tests and review race/cancellation behavior.

## Task 2: Live streaming and rendering

**Ownership:** `ApiStateFlowExtensions.kt`, `AgentRunCoordinator.kt`, streaming-related portions of `ChatViewModel.kt`, `ChatMarkdown.kt`, optional focused presentation helper, streaming tests. Parent waits before editing these files for compaction UI.

**Interfaces:** Coordinator exposes immutable live response snapshots keyed by run/message identity. Persisted rows remain authoritative for completed data; live overlays must never shrink text when Room lags. The existing text/tool timeline is preserved.

- [ ] Add a failing controlled-burst test for frequent live updates versus 250 ms persistence and final flushing.
- [ ] Publish live snapshots without awaiting Room writes. Maintain durable checkpoint cadence and terminal persistence.
- [ ] Add bounded adaptive frame-paced presentation for received text, preserving Unicode boundaries and fast catch-up.
- [ ] Remove demonstrated hot-path identity churn and unnecessary full reparsing where the installed renderer supports reuse; retain complete Markdown/math/code rendering.
- [ ] Run focused stream/scroll/Markdown tests and provide a reproducible instrumentation scenario with frame timing and artificial high-throughput input. Parent handles final device runs and phone review.

## Task 3: Persistent compaction core and model capacity

**Ownership:** Parent: `data/context/`, Room checkpoint/capacity entities and DAOs, database migration/schema, repository wiring, settings/model-limit UI after Task 2 releases shared files.

**Interfaces:** A checkpoint contains chat/platform identity, source-prefix fingerprint, compatible endpoint/model key, covered turn count, representation kind, and serialized working context. A capacity record is keyed by platform/endpoint/model identity. A prepared context contains current instructions, retained conversation turns/tool evidence, optional native items, and token budget metadata.

- [ ] Add failing tests for retaining a fact beyond ten turns, platform isolation, selected revisions, summary-plus-tail selection, invalidation, and checkpoint failure preserving old state.
- [ ] Remove turn-count truncation while preserving PR #270 filtering and actual attachment validation.
- [ ] Implement model-capacity discovery where documented, with an editable unknown-model limit instead of a guessed cap; separate context capacity from output limits.
- [ ] Persist derived context atomically in Room and restore on later requests. Invalidate by relevant source/endpoint/model changes; rebuild large historical prefixes using model-sized batches.
- [ ] Integrate token accounting, current-input/output reserve, complete tool-pair boundaries, and a bounded summarization target.
- [ ] Add a manual Compact now action and transient status through existing UI patterns; pause on unresolved capacity/compaction failures with original data intact.
- [ ] Validate migration and edit/retry/restart/duplication behavior with focused tests.

## Task 4: Provider compaction and bounded recovery

**Ownership:** Parent initially: provider adapters/contracts and network DTOs/APIs. After integration interfaces stabilize, a Grok worker may own the network-recovery slice exclusively.

**Interfaces:** Provider compaction accepts normalized history, current instructions/tools and budget, and returns the actual native working window or a same-platform textual summary. Transport recovery receives typed status/exception information and an explicit replay boundary; it must not infer safety from an error string alone.

- [ ] Add request/response contract tests for native OpenAI/Anthropic state and same-platform text summary requests without executable tools.
- [ ] Implement verified public native contracts, retain native state exactly as required, and fall back on unsupported capability to same-platform text. Do not copy Codex-private V2 protocol assumptions.
- [ ] Integrate Local Platform summarization with its existing exclusive engine and fingerprint/rebuild logic.
- [ ] Add bounded request retries for transient pre-output failures, supported Retry-After, backoff/jitter and cancellation; leave uploads/MCP writes out of this policy.
- [ ] Add opt-in supported OpenAI background streaming, sequence cursors, duplicate-event suppression and remote cancellation. Preserve partial output on unsupported/non-resumable interruptions.
- [ ] Test failures before/mid-output and around tool execution, permanent errors, duplicate events, cancellation and compaction fallback.

## Task 5: Integration and evidence

- [ ] Review each worker diff against its task/spec; resolve integration concerns before claiming completion.
- [ ] Run focused changed-code tests, then `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin` with the installed Android SDK.
- [ ] Run ktlint over changed Kotlin sources; inspect actual failures rather than assuming an environment problem.
- [ ] Run Android instrumentation on `emulator-5554` and exercise background/lock/cancellation and streaming with fast bursts, long Markdown, tools and scrolling.
- [ ] Capture release-representative frame/lag measurements, build an APK for later phone review, and retain reproducible test inputs.
- [ ] Run a final independent code review, address valid findings, and report completed behavior and evidence separately from pending user visual acceptance.

## Dependency and concurrency review

| Tasks | Shared surface | Sequencing |
|---|---|---|
| 1 and 2 | Coordinator is read by the service | Worker 1 does not edit coordinator; independent writes. |
| 1 and 4 | LocalRuntime | Parent waits for worker 1 before extending local compaction integration. |
| 2 and 3 | ChatViewModel / chat UI | Parent implements data/context first and adds compaction UI after worker 2 finishes. |
| 3 and 4 | Context preparation / provider adapters | Parent integrates sequentially before any network-only delegation. |
| 1–4 and 5 | Build outputs and tests | Centralize full builds; focused worker checks are coordinated to avoid simultaneous Gradle output writes. |

Each task's tests target its stated behavior. The approved spec permits disjoint background and streaming workers in parallel; this is the concurrency policy for this plan.
