# AI Memory

Persistent repository context for AI coding agents. Keep this file synchronized whenever repository files are added, modified, renamed, or deleted.

> Index status: comprehensive and actively maintained on `main`, `feat/v0.9.5.3-release`, and all active feature branches. Core architecture, Android targets, UI screens, encrypted backup/security, agent runtime/tools, Room V2 database & migrations, DataStore, local runtime & acceleration, network transports & SSE parsing, model catalogs, OpenRouter advanced routing/reasoning, Ollama advanced options & timeout resilience, WorkManager workers, DI modules, DTOs, and test roots are fully indexed.
>
> **Latest Target Release:** `0.9.5.6` (versionCode 58). Key additions: AETHERION MAX debug mode & hardware telemetry framework, GenerationQueueManager for message queuing during generation, PlatformLabel & PlatformLabelManager for shared platform tagging, LlamaModelInfo & LlamaModelMapper for Llama router mode, standardized MCP tools directory with triple-verification processes, Batch API integration framework (OpenRouter parallel client & Llama.cpp /v1/batch endpoint with BatchManager and BatchConfig), and KnowledgeGraphEngine for on-device knowledge graph memory retrieval.

## 1. Repository Overview

GPT Mobile AI (Improved) is a Kotlin Android application for chatting with cloud, self-hosted, and on-device large language models. It supports OpenAI-compatible services, Anthropic, Google Gemini, Groq, OpenRouter, Ollama, Llama router mode, and local LiteRT/Qualcomm QNN models. It also includes an autonomous agent runtime, Model Context Protocol (MCP) tools and marketplace, resilient streaming, background execution, chat search/history, multi-key API credential rotation with dynamic UI management and seamless streaming fallback, and encrypted credential storage.

### Architecture and stack

- **Architecture:** Clean MVVM with repository, domain-boundary abstractions, and data-source layers.
- **UI:** Jetpack Compose, Material 3, lifecycle-aware state collection (`collectAsStateWithLifecycle`), and Compose Navigation.
- **Motion & Design Tokens:** Theme motion primitives (`defaultSpatialSpec`, `fastSpatialSpec`, `fastEffectsSpec`) in `presentation.theme.Motion.kt` and alpha tokens (`ChatAlphaTokens`, `LocalChatAlpha`) in `presentation.theme.ChatAlphaTokens.kt`.
- **Language/runtime:** Kotlin 2.x, Java 21 bytecode, coroutines, Flow/StateFlow, and kotlinx.serialization.
- **Dependency injection:** Hilt/Dagger with KSP (`DiagnosticsModule` for Aetherion Max telemetry singletons).
- **Networking:** Ktor clients (OkHttp and CIO engines), Server-Sent Events (SSE) streaming support, resilient retry/exponential backoff, and `ApiCredentialRotator` for round-robin multi-key failover across `ProviderAdapters` (OpenAI, Anthropic, Gemini, Groq, OpenRouter, Ollama, Llama, and OpenAI-compatible services).
- **Persistence:** Room (`ChatDatabaseV2`, Schema version 32) with full-text search and DataStore preferences (`SettingDataSource`). Authoritative migration registry in `ChatDatabaseV2Migrations.ALL_MIGRATIONS`.
- **Knowledge Graph & RAG Memory:** Fully on-device Memory combines the encrypted `FactVaultRepository` policy/source-of-truth with a native Room knowledge-graph index (`MemoryGraphEntityRecord`, `MemoryGraphObservationRecord`, `MemoryGraphRelationRecord`) and `MemoryGraphRepository`. SQLite FTS5 is used when available with a local LIKE fallback. Native model tools expose the MCP server-memory-style `create_entities`, `create_relations`, `add_observations`, `search_nodes`, and `read_graph` operations without Node, Docker, cloud accounts, or an external MCP process. Existing review-before-recall, retention, chat scope, cloud-recall, tombstone, and capture-sensitivity controls remain authoritative.
- **Security:** Android Keystore-backed AES-256-GCM credential encryption (`SecretVault`); passphrase-protected user exports using PBKDF2-HMAC-SHA256 and AES-256-GCM (`AppBackupCrypto`).
- **Local inference & Acceleration:** Qualcomm AI Engine Direct (QNN) + LiteRT-LM (`LocalRuntimeImpl`, conversation fingerprinting, dynamic accelerator selection for NPU/GPU/CPU, warm engine retention, speculative decoding); Ollama and Llama supported for self-hosted network inference with timeout resilience and configurable advanced options.
- **Diagnostics & Telemetry (AETHERION MAX):** Real-time hardware telemetry (`HardwareDiagnosticsProvider`, `TelemetryCollector`, `TokenMetricsCollector`, `ToolMetricsCollector`, `DiagnosticsTelemetryProvider`, `ExportService`), HUD overlay (`DebugModeScreen`, `HardwareDiagnosticsPanel`, `LiveMetricsPanel`, `ToolAnalyticsPanel`, `TokenTimelinePanel`).
- **Message Queuing:** `GenerationQueueManager` with FIFO message queueing up to 50 items, status transitions (`PENDING`, `GENERATING`, `COMPLETED`, `FAILED`), and cancellation.
- **Platform Labels:** `PlatformLabel` and `PlatformLabelManager` supporting cross-platform tagging with 12 predefined hex colors and usage counting.
- **Batch Processing:** `BatchManager` orchestrator, `BatchConfig` / `BatchRequest` data models, `OpenRouterBatchClient` (parallel client-side semaphore requests with backoff), and `LlamaBatchClient` (native JSONL `/v1/batch` processing with fallback). Documented in `docs/BATCH_API_INTEGRATION_RESEARCH_DESIGN.md`.
- **MCP Integration:** Standardized tool structure in `mcp/tools/manifest.json`, `mcp/resources/manifest.json`, and triple-verification mechanism.
- **Android targets:** application ID `dev.melo.gptmobile.improved`, min SDK 31, compile/target SDK 36, arm64-v8a and x86_64 ABIs.
- **Build/release:** Gradle Kotlin DSL, R8/resource shrinking, ABI splits plus universal APK, and Room schema export (`app/schemas/`). Version `0.9.5.6` (versionCode 58).
