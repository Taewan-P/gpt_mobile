# Shared web search

Every enabled general web search tool from the marketplace participates in the
single model-facing `web_search`. Connections still need to be configured and
their tools selected in the active AI profile. Installing a connection does not
enable its tools or authorize paid usage automatically.

| Provider | Marketplace tools included |
| --- | --- |
| Built-in | `web_search` |
| Exa | `web_search_exa`, `web_search_advanced_exa`, legacy `deep_search_exa` |
| Tavily | `tavily_search` |
| Firecrawl | `firecrawl_search` |
| Jina | `search_web` |
| Brave | `brave_web_search` |
| Bright Data | `search_engine`, `search_engine_batch` |

Native Firecrawl, Perplexity, Exa, and Brave connections join the same search.
Native providers and multiple MCP search tools can be enabled together.
Specialized searches (private workspaces, code repositories, images, news-only
tools, and specific documentation or paper indexes) remain separate tools.

## Brave setup

For direct access, add a **Web search → Brave Search** connection under Tool
connections, enter a [Brave Search API key](https://api-dashboard.search.brave.com/app/keys),
and enable the connection in the profile's Web search selections. The client uses
Brave's GET API and `X-Subscription-Token` authentication; credentials are stored
in the existing secret vault and the header is redacted from diagnostics.

For MCP, deploy the official
[`@brave/brave-search-mcp-server`](https://github.com/brave/brave-search-mcp-server)
with `--transport http` and `BRAVE_API_KEY` configured on that server. Add its
reachable HTTP endpoint using the Brave marketplace preset, configure any
deployment authentication, discover tools, and enable `brave_web_search` in the
profile. The Brave API key is not the MCP server's bearer credential.

## Behavior and limits

- Provider schemas determine query, result count, domain, date, and batch
  parameters. Bright Data batches contain one query using Google. Tavily's
  minimum request count is respected, then sources are limited to the requested
  count locally.
- JSON, structured MCP content, Brave JSON text blocks, Exa/Tavily labeled text,
  Jina YAML fields, and Bright Data organic results normalize to title, URL,
  snippet, and optional publication date. URLs are deduplicated across engines.
- Domain restrictions are also applied locally using hostname boundaries. Date
  filtering uses provider parameters where supported; engines without them report
  `unsupportedFilters: ["recencyDays"]`. Search-engine freshness is not a verified
  publication-date guarantee.
- Up to four engines run concurrently. Each child retains its own permission
  gate, timeout, and the shared call/output budget. One failure does not discard
  successful results. Cancellation propagates.
- The chat's Web search switch disables all participating engines. Individual
  connection/tool switches and profile remote-tool restrictions still apply.
- Unknown required parameters or unsupported query shapes keep a tool separate
  instead of broadcasting an invalid request. Future marketplace web search
  additions must declare `webSearchToolNames` and add a contract fixture.

## Contract fixtures and verification

`app/src/test/resources/web_search/marketplace-contracts.json` contains reduced
input schemas and synthetic responses modeled on these official source revisions,
reviewed September 27, 2026. They contain no credentials or live user queries.

| Provider | Source revision |
| --- | --- |
| Brave | [a75d7eff](https://github.com/brave/brave-search-mcp-server/tree/a75d7eff34c260c7bba3bdc0163fd6982c34e370/src/tools/web) |
| Exa | [f3d71fb6](https://github.com/exa-labs/exa-mcp-server/tree/f3d71fb6b0ff4b4683f108f05bc2bae61a9f7e97/src/tools) |
| Tavily | [1c1d54c2](https://github.com/tavily-ai/tavily-mcp/tree/1c1d54c2a619afe52544f775c8d82a56ac6d8bb5/src) |
| Firecrawl | [f9ee82c8](https://github.com/firecrawl/firecrawl-mcp-server/blob/f9ee82c82c03048ce74b700ffaac482873fcfb7a/src/index.ts) |
| Jina | [5d6eb191](https://github.com/jina-ai/MCP/tree/5d6eb191a75d8e67b6e01ce427f0cc5c05c800aa/src) |
| Bright Data | [d33cfa5d](https://github.com/brightdata/brightdata-mcp/blob/d33cfa5da4a294d63a73ef96f0ee1fa90d892741/server.js) |

The adapter tests require coverage for every declared marketplace web search
tool. Integration tests cover fanout, response normalization, profile selection,
chat toggles, partial failures, permissions, and shared budgets. Brave HTTP tests
use a loopback fixture server. Run `testDebugUnitTest` and `lintDebug` in an Android
build environment before release; fixture verification does not establish live
account authentication or hosted service availability.
