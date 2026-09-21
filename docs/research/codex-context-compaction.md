# Codex context compaction: findings for GPT Mobile

Research date: 2026-09-08. This is the investigation supporting the design approved on 2026-09-08.

Sources inspected: official `openai/codex` source at `6750f5bd1356fe1553c0fcc9f2632704f3055946`; GPT Mobile at `2cfdaedd4135f5ff3de55afca1963a4c5ea5d45d`; GPT Mobile PR #270. The broken fork was inspected only through its README in the preceding comparison.

## How Codex decides to compact

Codex tracks the active model context separately from cumulative session usage. It combines the latest provider-reported token usage with estimates for newer items that have not yet been counted by the provider. Its estimator explicitly uses approximate byte-based counts rather than claiming tokenizer accuracy. Instructions, message content, tools, and supported multimodal items contribute to context use.

It checks limits before normal sampling and between model/tool rounds when more work remains. It can also compact on a switch to a smaller model or a change in native compaction compatibility. A manual action uses the same compaction machinery.

At this source revision, the default auto-compaction threshold is 90% of the resolved model context window, and an explicit threshold is clamped to that ceiling. The usable context window has a separately configured headroom percentage. GPT Mobile should adopt model-aware token accounting and a reserve for the next request/output, not copy these percentages or large-model constants indiscriminately.

Sources:
- [Token status and hard-cap accounting](https://github.com/openai/codex/blob/6750f5bd1356fe1553c0fcc9f2632704f3055946/codex-rs/core/src/session/context_window.rs)
- [Pre-turn and mid-turn integration](https://github.com/openai/codex/blob/6750f5bd1356fe1553c0fcc9f2632704f3055946/codex-rs/core/src/session/turn.rs#L1087)
- [Usage plus estimates](https://github.com/openai/codex/blob/6750f5bd1356fe1553c0fcc9f2632704f3055946/codex-rs/core/src/context_manager/history.rs#L677)
- [Model limits](https://github.com/openai/codex/blob/6750f5bd1356fe1553c0fcc9f2632704f3055946/codex-rs/protocol/src/openai_models.rs#L504)

## How it produces the compacted context

The provider capability determines the route. Remote compaction uses either the older explicit compact endpoint or a newer compaction-trigger path, depending on feature support. The result can include opaque compressed state and retained message items. Such state is not a portable plain-text summary for arbitrary providers or models.

When remote compaction is unsupported, Codex makes a normal model request with a summarization instruction. Its prompt asks for progress, decisions, constraints, preferences, remaining work, and critical references. In this terminology, "local compaction" means client-orchestrated summarization; it can still call a cloud model. GPT Mobile's Local Platforms must instead perform this generation on-device.

The text route rebuilds a working history from retained real user messages and a summary, using a 20,000-token retained-user-message budget at this revision. That exact budget and role placement are Codex-specific; they should not be copied to a small phone model. Native V2 also has retention logic for message identities, metadata, and images.

Current instructions/context are reconstructed around the compaction result, with different placement for pre-turn and mid-turn compaction. A summary is not a substitute for authoritative current configuration.

Sources:
- [Capability-based dispatch](https://github.com/openai/codex/blob/6750f5bd1356fe1553c0fcc9f2632704f3055946/codex-rs/core/src/session/turn.rs#L1248)
- [Model-written compaction](https://github.com/openai/codex/blob/6750f5bd1356fe1553c0fcc9f2632704f3055946/codex-rs/core/src/compact.rs#L248)
- [Summary prompt](https://github.com/openai/codex/blob/6750f5bd1356fe1553c0fcc9f2632704f3055946/codex-rs/prompts/templates/compact/prompt.md)
- [Native V2 retention](https://github.com/openai/codex/blob/6750f5bd1356fe1553c0fcc9f2632704f3055946/codex-rs/core/src/compact_remote_v2.rs#L486)
- [Public OpenAI compaction contract](https://developers.openai.com/api/docs/guides/compaction)

## How it stores and restores the result

`replace_compacted_history` installs the replacement model history and records a `CompactedItem`. The checkpoint contains the actual replacement history, context-window identifiers, associated response identity where available, retained context, and a token-usage checkpoint. This is distinct from erasing the historical conversation.

The current local thread-store implementation writes canonical rollout records to JSONL, flushes them, and then updates a rebuildable SQLite projection. SQLite must not get ahead of canonical persisted history. This storage arrangement is specific to Codex; GPT Mobile already has Room transactions and should use them rather than introduce a second JSONL database.

When resuming, Codex scans backward for the newest surviving replacement-history checkpoint, accounts for rolled-back turns, restores that context, and replays the surviving suffix. It restores or regenerates the current instruction baseline as needed. It does not summarize the complete transcript again on every resume.

Sources:
- [Checkpoint installation](https://github.com/openai/codex/blob/6750f5bd1356fe1553c0fcc9f2632704f3055946/codex-rs/core/src/session/mod.rs#L3771)
- [Checkpoint fields](https://github.com/openai/codex/blob/6750f5bd1356fe1553c0fcc9f2632704f3055946/codex-rs/history/src/lib.rs#L186)
- [Canonical write before projection](https://github.com/openai/codex/blob/6750f5bd1356fe1553c0fcc9f2632704f3055946/codex-rs/thread-store/src/local/live_writer.rs#L309)
- [Resume reconstruction](https://github.com/openai/codex/blob/6750f5bd1356fe1553c0fcc9f2632704f3055946/codex-rs/core/src/session/rollout_reconstruction.rs#L134)

## What to preserve from GPT Mobile

[PR #270](https://github.com/Taewan-P/gpt_mobile/pull/270) explicitly described its context windows as heuristic. Its useful safeguards are independent of those windows: select only the relevant platform's response, use the effective assistant revision, strip application-generated error notes while retaining useful partial answers, exclude pure failed historical turns, preserve meaningful attachment-only messages, and validate the actual selected attachment payload.

The existing Local Platform ADR requires keeping a warm conversation and rebuilding on divergence. Compaction introduces another legitimate rebuild boundary; it should not make every ordinary local turn replay its full history.

The candidate adaptation is an atomic Room checkpoint per chat/platform, tied to the covered source prefix and compatible model/endpoint state. Normal requests use the checkpoint plus newer eligible messages and tool exchanges. Editing/retrying/restoring a revision before that boundary invalidates affected derived context. Full messages, attachments, and traces remain available for display, export, and reconstruction.

The public native API contract, not Codex-private feature flags or training-specific prompt placement, governs each provider integration. Native outputs must be stored in the form required by the provider. Text summaries must preserve attribution and must not turn tool output into system instructions or new action authorization.

## Decisions confirmed so far

1. Automatic compaction near the limit, plus a manual action and visible status.
2. Native compaction for verified supported endpoints, with same-platform text fallback elsewhere. Local generation remains on-device.
3. Preserve relevant tool outcomes and source references within each platform's own context.
4. Discover model context limits where reliable; ask once for an editable limit when unknown.
5. If compaction fails after bounded retries and supported fallback, pause with a retry action; preserve the original transcript and last valid checkpoint.
6. Use short adaptive text smoothing across frames, with approximately 100 ms of bounded visual delay and fast catch-up.
7. Retry transient failures before output; afterward resume only with verified support, otherwise preserve partial output and pause. Never blindly replay tool actions.
8. Test the 60 Hz emulator first; the user will review the phone build later.
9. Add opt-in resumable replies for supported OpenAI Platforms, leaving ordinary streaming as default, with provider background state and explicit remote cancellation.

The user approved the [design](../superpowers/specs/2026-09-08-super-improvements-design.md) and implementation on 2026-09-08.

Anthropic also documents native compaction through its Messages API, using a provider-specific compaction block and beta contract. It requires supported models and has a minimum trigger size; manual compaction below that trigger needs the same-platform text route. This is separate from OpenAI's opaque compaction items. See [Anthropic compaction](https://platform.claude.com/docs/en/build-with-claude/compaction). Implementation should verify model support and endpoint behavior, preserve returned blocks, and follow that provider's replay rules.

## Other selected improvements: evidence and validation targets

- Background/local inference: `GPTMobileApp.onTrimMemory` unloads the runtime; `LocalEngineHolder.unloadEngine` cancels active inference before acquiring its generation lock. Investigate routine background/trim transitions and preserve active sessions appropriately. Existing foreground-service timeout and cancellation behavior must still terminate runs cleanly.
- Streaming: active runs publish through Room at 250 ms, and the screen observes Room. `ChatMarkdown` reparses growing content and regenerates display-math identities. Measure these paths; separate visible updates from durable checkpoints and preserve text/tool chronology, late-layout bottom following, and user scroll position.
- Network: providers flatten transport problems into error events and do not have a common bounded retry policy. Retry policy must distinguish reconnectable request failures from an interrupted generation or potentially executed side effect.
- Visual testing: only `emulator-5554` is currently connected, with an actual maximum display mode of 60 Hz. It cannot establish 120 Hz smoothness. Use real frame timings and live visual review; the user decides final smoothness. Include fast bursts, long Markdown/code/math, tool transitions, parallel platforms, scrolling, and completion/error/cancellation.

Implementation subagents must use `xai/grok-4.6` through opencodex. The requested grilling workflow requires the user's shared-understanding confirmation before implementation.
