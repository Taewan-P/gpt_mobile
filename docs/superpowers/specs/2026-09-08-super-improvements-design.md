# GPT Mobile: context, background execution, streaming, and recovery

Status: approved by the user on 2026-09-08 after the async grilling session.

Scope: improvements 1, 2, 4, and 5 from the fork README comparison. Worktree: `/Users/taewanpark/.codex/worktrees/e8ff/gpt_mobile`, branch `codex/super-improvements`, starting revision `2cfdaed`. Implementation subagents use `xai/grok-4.6` through opencodex. Existing unrelated `.serena/project.yml` and `.ui-test/` work must be preserved.

Research: [Codex source analysis](../../research/codex-context-compaction.md), pinned to official Codex revision `6750f5bd1356fe1553c0fcc9f2632704f3055946`.

## 1. Background and screen-off execution

An active user-started generation or agent run should survive leaving the chat, hiding the app, and locking the screen while Android permits the process/service to run. Routine UI-hidden and trim callbacks must not cancel active Local Platform inference. Idle engine resources can be released, while explicit cancellation, model switching, and shutdown retain their intended behavior.

Reuse the existing foreground service and application-scoped coordinator. Keep CPU awake only while active work requires it, with bounded wake-lock lifetime and cleanup on success, error, cancellation, service destruction, and timeout. Do not add blanket battery-optimization exemptions. Keep existing run/tool limits and interruption recovery; do not automatically replay work after process death.

Respect the existing warm-conversation ADR: ordinary successive local turns reuse engine/KV state, and compaction or real history divergence rebuilds the conversation when needed.

Validation: exercise active generation versus idle trim, leaving the screen, cancellation, service timeout, and returning to the chat. Verify all terminal paths release resources and preserve received text/tool results. Emulator evidence is functional evidence, not a guarantee for every OEM power policy.

## 2. Model-aware persistent compaction

Replace fixed 6/8/10-turn context selection with token-budgeted working context. Full messages, attachment metadata, revisions, and tool traces remain stored and available to the UI. Persist derived context checkpoints in Room, scoped to a chat and Platform, using transactions and the existing database architecture.

A checkpoint records its compaction representation, covered source boundary/fingerprint, model/endpoint compatibility, and useful usage/budget metadata. Subsequent requests use the valid checkpoint plus newer eligible messages and completed tool exchanges. A restart restores this context instead of resummarizing from scratch.

Automatic compaction runs near the effective model limit, including between tool rounds at safe complete-exchange boundaries. Also provide a manual Compact now action and visible compaction status. Resolve the real context capacity from reliable model/endpoint/catalog metadata. If unknown, ask once for an editable model-specific token limit. Keep context capacity separate from an output-token setting. Prefer provider token counts/usage when available; clearly identify estimates, reserve room for output/instructions/tool definitions, and handle oversize current inputs without silently discarding them.

Use native compaction when the endpoint and selected model have verified support: public OpenAI Responses contracts and supported Anthropic Messages compaction, each with its own returned-state and replay rules. Use the same Platform's model to produce a structured text summary elsewhere, including fully on-device Local Platforms. Unsupported native features may fall back to text within the same Platform; transient failures get bounded retries. Never send a Local Platform's history to a cloud summarizer automatically.

Summaries retain relevant user goals, decisions, constraints, unresolved work, useful tool outcomes, and source references. Preserve platform isolation, current system instructions, effective assistant revisions, meaningful partial responses, attachment-only messages, and error-note filtering from PR #270. Keep tool data attributed as evidence; compaction must not promote it into system instructions or new authorization. Native state remains within its compatible endpoint/model boundary.

Edits, retries, revision restoration, model/endpoint changes, and other changes to a covered prefix invalidate affected derived context. Rebuild from authoritative eligible history when required, using bounded model-sized batches for large historical prefixes. Do not replay the discarded future of a retry or reuse another Platform's summary. Preserve original history during a rebuild. Integrate the compacted prefix with Local Platform conversation fingerprints so normal warm turns remain fast.

If compaction cannot complete after the permitted retries/fallback, pause the pending reply with a retry action. Keep the last valid checkpoint and full transcript. No silent fallback to a recent-turn cutoff. If the current message alone cannot fit, preserve the draft and explain the capacity problem.

Validation: small model budgets; multiple compactions; app restart; corrupt/failed checkpoint writes; failed summarization; native replay contracts; unsupported endpoints; pure failures and partial answers; per-platform isolation; attachments; retained tool sources; edit/retry/revision/model divergence; and a Local Platform warm-session rebuild. Demonstrate recall of a fact outside the former ten-turn window after compaction.

## 3. Visually smooth streaming

Deliver active response snapshots directly to the UI without waiting for Room's current 250 ms publication cadence. Keep durable checkpoints separate from visual cadence, preserve at least the existing checkpoint frequency under load, and always persist received content on normal terminal paths.

Use short adaptive smoothing of already-received text across real display frames. Target approximately 100 ms of bounded visual delay with fast catch-up; avoid a fixed characters-per-second typing effect. Completion, errors, and cancellation must preserve all received text and leave no permanent display backlog. Text segmentation must not split surrogate pairs or visibly corrupt Unicode clusters.

Profile Markdown/code/math parsing and layout, stabilize rendering identities, and reuse existing rendering components where possible. Preserve the chronological text/tool timeline and existing auto-follow behavior: follow at the bottom, stop following when the user scrolls away, and resume when they return. Keep accessibility, selection, copying, and revisions working.

Validation: a controlled high-throughput stream with uniform tokens and bursts; long prose, code fences, tables, math, and Unicode; tool transitions; simultaneous Platforms; active scrolling; and terminal states. Measure actual frame timings/jank and publication lag, rather than infer smoothness from a timer value. Use a build representative of release behavior for performance evidence.

Only a 60 Hz emulator is currently connected. Test there first, report that limitation, and deliver an APK and reproducible scenarios for the user's later phone review. Do not claim 90/120 Hz validation from emulator settings or interpolated video. The user decides whether the final visual result is smooth; automated measurements do not replace that acceptance.

## 4. Bounded network recovery

Use the installed networking stack's retry capabilities where they satisfy the policy. Automatically retry transient failures before output starts, with a small retry budget, exponential backoff/jitter, and supported Retry-After handling. Do not retry authentication/validation failures or user cancellation. Scope retries to intended generation requests, not arbitrary MCP operations, uploads, or other writes.

After output starts, resume only with a verified provider cursor/response contract. Otherwise preserve the partial response and present Retry. Do not append a restarted generation to old text as if it were a continuation, or blindly repeat tool actions. Preserve the existing distinction between a new user-requested retry and reconnecting the same response.

Add opt-in Resumable replies for supported OpenAI Platforms, leaving ordinary streaming as the default. This uses provider-side background generation with temporarily retained response state, response identity/sequence tracking, duplicate-event protection, and explicit remote cancellation. Respect endpoint/model capability checks and local run timeouts. Failed remote cancellation must not be reported as confirmed cancellation of server computation. Other Platforms retain the safe partial-response behavior unless their resume contract is verified and implemented.

Validation: connection failure before output; retryable response status; Retry-After; permanent failure; cancellation during backoff; disconnection mid-text; disconnection around a tool boundary; resumable cursor replay/duplicate events; remote cancellation; and preserved partial responses on unsupported providers.

## Integration and delivery

Implement as separable changes with focused tests, followed by full relevant unit tests, Android lint, compilation, and device validation. Background lifecycle and streaming can be assigned to disjoint Grok implementation workers while compaction/storage is developed; sequence overlapping provider-network changes to avoid conflicting edits. Review and integrate each worker's changes against this design.

No ABI split, marketplace, unlimited agent runtime, unrelated provider caching subsystem, or vector database is part of this scope. Do not claim runtime behavior from static inspection or claim final smoothness before the user's review.

The requested grilling workflow requires explicit confirmation that this design reflects the shared understanding before implementation begins.
