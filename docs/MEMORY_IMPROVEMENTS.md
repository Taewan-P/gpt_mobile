# Automatic memory and connected recall

Research and implementation: 27 September 2026.

## What changed

Automatic local capture recognizes explicit remember/save requests, stated identity, devices, preferences, ongoing projects and existing relationship patterns. It runs on the current user's text before the main response; it does not depend on the main model choosing a memory tool. Questions, fenced code, quoted blocks, credential-like statements and explicit learning opt-outs are excluded. Capture sensitivity and category controls still apply.

When an enabled private helper is configured in **Local models → Delegation**, optional local-model learning can select additional durable statements. The app accepts only whole statements actually present in the user input. It validates the category, applies capture limits, review settings and deletion tombstones, and deduplicates model-selected observations against deterministic extraction. Concurrent AI profiles share one enrichment attempt per message. No assistant or retrieved page content is learned by this path.

Recall ranks exact matches, related vocabulary and relation-specific personal questions. Short follow-up questions can use the last two user messages from the same conversation as retrieval context. This is lexical and relationship-aware retrieval, not a vector embedding engine. Facts must still pass scope, review, retention and destination checks. A separate recall budget bounds prompt size; the memory count can be adjusted up to 20.

At capacity, optional rotation makes room for fresh automatic facts by removing older automatic facts. Pinned and manually saved memories are protected. Exclusive facts such as current location can supersede older values; a retry of an older message cannot overwrite a newer correction. Deletion records prevent retries and alternate model-selected forms from silently restoring the same observation.

## Memory MCP support

**Graphiti Memory** is now available in the marketplace alongside Mem0 and Supermemory. The app's Streamable HTTP client connects to a configured Graphiti server; it does not install a Python process or graph database on the phone.

| Provider | Automatic read tool | Optional configured scope |
| --- | --- | --- |
| Mem0 | `search_memories` | User ID, or user filter when exposed by the discovered schema |
| Supermemory | `search_memory` | Space/container key |
| Graphiti | `search_memory_facts` | Group ID |

Add a connection, authenticate, select its search tool in the AI profile, then opt in under **Memory → Controls → Connected memory**. Select up to three connections for automatic per-turn recall. The adapter checks each discovered schema and rejects unsupported required parameters or a scope it cannot represent. It uses the existing per-chat switches, permissions, shared tool budget and cancellation handling. One unavailable provider does not discard another provider's results.

Selected servers receive up to 500 characters of the current question. Automatic recall does not upload the local vault, synchronize histories or invoke memory-write tools. Local-memory master/recall controls and Free-profile exclusions apply. Automatic connected recall is paused when cloud recall is disabled, original-chat-only recall is enabled, or review-before-use is required, because those local restrictions cannot be independently enforced on third-party memories. Remote saving remains an explicit provider tool operation subject to its connection permissions.

Remote results are bounded untrusted reference data. Their text is omitted from the persistent automatic-recall trace. Memory services maintain their own data and retention controls; deleting local memories does not delete copies explicitly saved in those accounts.

## Research basis and limitations

- [Mem0 official MCP documentation](https://docs.mem0.ai/platform/mem0-mcp) documents the hosted HTTPS endpoint, authentication and memory search tools. It provides account-backed memory; it is not a replacement for the encrypted local vault.
- [Supermemory official MCP tool reference](https://supermemory.ai/docs/supermemory-mcp/mcp) documents scoped semantic search and profile access. The app uses the search tool rather than fetching the whole account profile on every turn.
- [Graphiti upstream MCP server](https://github.com/getzep/graphiti/tree/6b4b56ff6f4b1e4e69c3c3c5487cf1b8762c483a/mcp_server) supports temporal facts, hybrid retrieval and HTTP transport. Its upstream implementation is experimental and needs a database plus configured generation/embedding models. Hosting alone does not ensure data remains local: configure both model endpoints accordingly.

Authenticated service calls and phone inference quality require configured accounts/models and device testing. No memory-recall accuracy or latency percentage is claimed from these implementation tests.
