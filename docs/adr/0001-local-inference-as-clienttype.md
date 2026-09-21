# Local inference is a ClientType within PlatformV2, not a separate subsystem

On-device LiteRT-LM inference is integrated as `ClientType.LITERT_LM`: a Local Platform is an ordinary `PlatformV2` profile dispatched through a provider adapter, so it inherits parallel multi-platform runs, per-chat model overrides, system prompts, and settings for free. Model files are deliberately *not* owned by the profile — a separate model-management domain owns Local Model artifacts (download, storage, deletion), and profiles reference them, so two profiles can share one multi-GB file and deleting a profile never deletes a model.

## Considered Options

- **Separate local-chat subsystem** (like Google's AI Edge Gallery, which is model-centric): rejected because it would duplicate gpt_mobile's chat UI, history, and persistence for no benefit — the gallery is a model showcase, gpt_mobile is a chat app.
- **Embedded OpenAI-compatible HTTP shim reusing `ClientType.CUSTOM`**: rejected because it adds a loopback server, serialization overhead, and a fake network boundary purely to avoid writing one adapter.

## Consequences

- The adapter contract (`openSession` → `ProviderEvent` stream) is now proven runtime-agnostic; nothing in it assumes HTTP.
- "Serving" in this project means in-app inference only (engine load/warm/stream/unload). Exposing the model to other apps over HTTP is explicitly out of scope.
