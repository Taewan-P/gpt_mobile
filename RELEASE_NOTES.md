# GPT Mobile AI 0.9.19.2

## Local model delegation

- Adds a dedicated **Delegation** tab under **Local Models**.
- Expands delegation controls for search breadth, webpage reads, crawl depth, parallel local tasks, local workload percentage, timeout, per-turn calls, and remote brief size.
- Keeps private destinations as the default delegation boundary and preserves the existing free-model and self-delegation safeguards.

## Memory and MCP

- Adds **Graphiti Memory** as an optional self-hosted MCP memory integration.
- Keeps the existing built-in encrypted Memory plus hosted Mem0 and Supermemory options without duplicating marketplace entries.
- Retains Brave Search and the current web-search marketplace providers already present on main.

## Local runtime

- Includes all LiteRT-LM and Qualcomm QNN upgrades already merged into main before this release, including runtime hardware detection, model/package validation, GPU/CPU execution paths, and automatic QNN-to-LiteRT fallback when NPU prerequisites are unavailable.
- No database schema changes are included in this release.

Version code: **78**. Android 12 or newer. Use the ARM64 APK for most modern Android phones.
