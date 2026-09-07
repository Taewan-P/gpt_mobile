# GPT Mobile 0.8.0

GPT Mobile profiles can now act as opt-in on-device agents while preserving the existing chat and multi-provider comparison experience.

## Added

- Native structured tool calling for OpenAI Responses, OpenAI-compatible/Groq, Anthropic Messages, and Gemini.
- Independent parallel agent runs for selected profiles, with persisted status, bounded tool traces, cancellation, retry warnings, and foreground notification progress.
- Firecrawl, Perplexity, and Exa behind one normalized `web_search` tool.
- A hardened built-in `read_url` tool.
- MCP Streamable HTTP discovery and calls with public, bearer-token, and OAuth 2.1/PKCE authentication.
- Per-profile tool assignment. Profiles without bindings remain chat-only.
- Android Keystore-backed credential storage and one-way migration of existing provider keys.

## Compatibility and safety

- The Room 6→7 migration preserves existing chat/profile/message IDs, prompts, models, attachments, and revisions. It also repairs early schema-v2 databases that were missing expected message or profile columns.
- Tool results remain scoped to their run and are not added to later conversation history.
- Canceling keeps partial text and traces. Interrupted runs are never resumed or replayed automatically.
- Exports include bounded traces and exclude credentials.
- Android 17 local-network access is requested only when a selected provider or MCP endpoint requires it.

See [Agent tools](agent-tools.md) for setup, limits, privacy details, and deferred capabilities. Release evidence is recorded in [0.8.0 validation](release-validation-v0.8.0.md).
