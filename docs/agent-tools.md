# Agent tools

GPT Mobile 0.8.0 upgrades each existing provider profile into an optional on-device agent profile. Existing profiles, models, system prompts, chats, attachments, revisions, and multi-provider selection continue to work. A migrated profile has no assigned tools, so it remains chat-only until you opt in.

## Set up a profile

1. Open **Settings → Tool connections**.
2. Add a Firecrawl, Perplexity, or Exa search connection, or add an MCP server that uses Streamable HTTP.
3. For MCP, choose public access, a bearer token, or OAuth. Cleartext HTTP can be approved for any MCP endpoint; it is unencrypted, so credentials and tool data could be intercepted.
4. Open a provider profile and select **Tools**. Assign one search backend, the built-in `read_url` tool, and any discovered MCP tools you want that profile to use.
5. Start or continue a chat normally.

Only assigned tools are sent to the model. Assigned tools execute without a confirmation prompt and remain visible in the chat timeline. Newly discovered MCP tools stay disabled until you assign them.

When several profiles are selected, GPT Mobile runs one independent agent for each profile. You can leave the chat while they run, follow progress from the foreground notification, or cancel all active profile runs. Cancellation keeps partial text and the tool trace. A process restart marks unfinished runs as interrupted and never replays their tools.

Retrying creates a new run and may invoke tools again. Prior tool results are shown in traces and exports, but are not replayed into later model turns.

## Supported tools and limits

- `web_search`: Firecrawl, Perplexity, or Exa; one search backend per profile.
- `read_url`: HTTP(S) pages only, with private/loopback/metadata destinations blocked, up to five redirects, a 1 MiB download limit, and at most 64 KiB returned to the model.
- MCP: current Streamable HTTP servers, including JSON and SSE responses, bearer auth, and OAuth 2.1/PKCE.

Each profile run is limited to 15 minutes, eight model/tool rounds, 24 tool calls, and four concurrent tool calls. Individual calls time out after 60 seconds. Model-visible and persisted output is bounded to 64 KiB per tool call.

Models or endpoints that reject native tool definitions fall back once to chat-only before any tool runs. GPT Mobile does not parse tool calls from prompt-generated JSON.

## Privacy and credential security

- Chats and tool traces are stored in the app's local Room database.
- Provider, search, bearer, and OAuth credentials are encrypted with an Android Keystore AES-GCM key and stored under `noBackupFilesDir`. They are never included in chat export.
- Existing plaintext provider keys are moved into the encrypted vault only after a verification read succeeds. A failed migration keeps the original value and displays a recoverable warning.
- Android backup/transfer can restore chat history, but encrypted credentials intentionally require re-entry on another device.
- Requests are sent to the selected model provider. Opening a profile's Tools dialog contacts every saved MCP server to discover tools and may include its bearer or OAuth credential. During chats, requests are sent to the assigned search API, requested URL, or MCP server only when that tool is invoked.
- Deleting a connection removes its bindings and vault credential. Historical traces keep bounded connection/tool snapshots, not secrets.
- Deleting a chat removes its runs and traces. Duplicating a chat copies completed history with regenerated IDs. Markdown export includes bounded traces and excludes credentials.
- Credentials are not logged. Unsupported MCP binary/image blocks may be identified in the local trace but are not forwarded to the model.

Google search, legacy MCP HTTP+SSE, stdio MCP, multimodal MCP output forwarding, code execution, browser automation, scheduled jobs, image generation, device control, cross-device sync, and a cloud agent backend are outside 0.8.0.
