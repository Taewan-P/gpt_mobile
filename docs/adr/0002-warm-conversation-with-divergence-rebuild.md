# Warm LiteRT-LM Conversation with divergence-triggered rebuild

gpt_mobile's adapters are stateless — every turn re-sends the full chat history, which is what makes edit/retry/revisions/parallel columns work. LiteRT-LM's `Conversation` is the opposite: the engine holds the KV cache and re-reading history (prefill) costs seconds-to-tens-of-seconds on a phone. We resolve the mismatch by keeping the `Conversation` alive per (chat, platform profile) and sending only the new message on the happy path, while tracking which persisted messages the conversation has consumed; if the incoming context does not exactly extend that prefix (edit, retry, revision switch, mid-stream cancellation, app restart, model change), the conversation is closed and rebuilt from history via `initialMessages`.

## Considered Options

- **Rebuild every turn** (architecturally pure, matches the stateless adapters): rejected because every message would pay full prefill, making multi-turn chat feel broken on exactly the hardware this feature targets.
- **Engine owns history, never rebuild**: rejected because engine state cannot be rewound, which would force disabling edit/retry for local chats.

## Consequences

- The local adapter is the only stateful adapter in the codebase; the "conversation reflects messages up to X" bookkeeping is deliberate, not an oversight — do not "simplify" it into per-turn rebuilds without re-reading this ADR.
- Related lifecycle decisions: the engine lazy-loads on first local run, stays warm across chats and screens, and unloads only on model switch or `onTrimMemory`. Local generations are globally single-flight (one engine, one run; extra local runs queue serially).
