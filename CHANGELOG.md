# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.9.19.0] - 2026-09-27

- Consolidate benchmarking beside Usage in Debug and Statistics, with debug-mode AI profile shortcuts.
- Add controlled Quick and Full suites, weighted ratings, progress/cancellation, comparison charts and persistent run history.
- Measure speed, latency, reliability, consistency, task accuracy, JSON correctness and safe fixture-tool success; keep everyday observations separate.
- Distinguish local and remote profiles with labeled teal memory-chip and purple cloud icons, separate rankings and explicit rating targets.
- Preserve profile settings while running bounded benchmark requests and compare only matching configurations.
- Fix local package/chipset detection, GPU edition resolution, known broken MiniCPM5 NPU selection and native session cleanup.
- Improve opt-in runtime failure diagnostics and NPU startup guidance.
- Release version `0.9.19.0`, version code `76`.

## [0.9.17.0] - 2026-09-26

- Name Free profiles automatically and enable approved LLM7 integration.
- Improve anonymous Pollinations transport, bounded recovery, outage diagnostics and fresh connection testing.
- Fade assistant backgrounds and timestamps with activity; refine themed conversation controls, input and history.
- Open conversations at the bottom and align Favourite links to the selected response.
- Rebuild live diagnostics and usage with scatter/trend/outcome charts, profile rankings and request detail.
- Track actual profile attribution and terminal outcomes; migrate database 30 to 31 without losing backup compatibility.
- Acquire fresh native locations across enabled providers with a bounded cold-start window and reliable cancellation cleanup.

## [0.9.16.0] - 2026-09-26

- Rebuild backups against the current schema using named columns and safe defaults.
- Separate chipset-specific NPU downloads from GPU/CPU packages and filter compatible marketplace results.
- Add local-model and tool Settings tabs, existing-profile navigation, and saved theme profiles.
- Simplify delegation and refine themed conversation controls, loading text and error visibility.
- Improve Free provider availability handling and response-limit continuation.

## [0.9.13.0] - Release branch

### Added
- Colour wheel, hue/saturation/brightness sliders, presets and full palette preview.
- Shared provider API key editor with add/remove controls and round robin across new requests.
- Friendly animated tool activity with status icons, expandable details and technical debug output.
- Nearby OpenStreetMap place markers, tap-to-route walking/driving paths, distances and estimated travel times.
- Fact Vault learning/recall, cloud sharing, chat scope, review, category, capacity and retention controls.
- Local model performance controls, actual backend/hardware status, lazy catalog and direct AI profile creation.
- Reasoning is visible without debug mode; Advanced Settings can hide it independently.

### Changed
- Provider groups collapse; profile enable/delete controls remain on the profile page.
- Home back gestures dismiss selection/search or return to Chats, then keep the app open.
- Matching conversation icons, a local/server icon, half-sized profile labels and softer profile names.
- CPU thread/cache changes invalidate warm native engines; idle cleanup respects the configured interval.
- Release branches validate optimized unsigned APKs/AABs in CI before publishing.

### Release
- Version `0.9.13.0`, version code `68`. Retains the fixes merged in #496 and #497.
- This branch does not create a release tag or publish signed assets automatically.

## [0.9.12.0] - 2026-09-25

### Added
- Queued prompts, redesigned model controls, profile activation, themes, conversation statistics, document attachments, and expanded MCP marketplace branding.
- Shared read-only tool calls across concurrent models, configurable in Advanced Settings.
- Inline tool execution metrics and encrypted opt-in Fact Vault with cloud and LiteRT recall.

### Fixed
- Queue completion, input-draft preservation, shared-call JSON keys, location-map initialization, provider integration, and cumulative token usage.
- False fact extraction, contextual recall limits, and clearing unreadable vault data.
- Release bundle failures are fatal; bundle signing avoids copying a file onto itself; release updates use the tag name.

### Release
- Version `0.9.12.0`, version code `67`.
- Require persistent repository signing credentials and publish SHA-256 checksums with APK/AAB assets.

## [0.9.11.0] - 2026-09-25

### Added & Improved
- **App-wide Architecture, UX & Diagnostics Redesign**:
  - Live tool context tracking and active execution telemetry via `LiveToolContext`.
  - Comprehensive provider adapter hardening and normalized OpenRouter model routing.
  - Native device location freshness and live capability providers (`DeviceLocationProvider`, `LocationFreshness`).
  - Granular complete backup and restore framework (`CompleteBackupOptions`, `CompleteBackupSelection`, `CompleteBackupDatabase`).
  - Native Hugging Face model search and repository exploration (`HuggingFaceModelSearchClient`).
  - Local runtime router enhancements and hardware acceleration routing.
  - Granular application feature settings (`AppFeatureSettings`) controlling smart suggestion actions and foreground execution.
- **Creativity Slider & Custom Profile Labels**:
  - Unified single Creativity slider smoothly mapping temperature and top-p sampling.
  - Reusable colored profile badges across conversation cards, model pickers, and settings.
- **Version Bump**:
  - Bumped version name to `0.9.11.0` (versionCode `66`).

## [0.9.9.0] - 2026-09-24

### Added & Improved
- **Creativity Slider & Colored Profile Labels**:
  - Unified Creativity slider for model profiles with smooth sampling parameter translation.
  - Colored profile badges and quick filtering in new conversation model selection.
- **Reusable Provider Connections & Remote Agent Flight Recorder**:
  - Separated provider connection credentials from profile configurations.
  - Live remote tool telemetry and structured execution traces in Agent Flight Recorder.
- **Version Bump**:
  - Bumped version name to `0.9.9.0` (versionCode `65`).

## [0.9.8.0] - 2026-09-24

### Added & Improved
- **Agent Flight Recorder & Gateway Efficiency**:
  - Live tracking of active Gateway and agent execution state directly within chat.
  - Work-state UI: Starting, Exploring, Focused, Using Tools, Recovering, Synthesizing, Finalizing.
- **Combined Multi-Model Mode**:
  - Parallel multi-model conversation synthesis and Room schema v24 migration.
- **Passwordless Encrypted Complete Backup (GPTFULL2)**:
  - AES-256-GCM authenticated archives protected by Android Keystore.
- **Remote Streaming Longevity**:
  - Inactivity timeouts extended to 5 minutes with unbounded request deadlines.
- **Version Bump**:
  - Bumped version name to `0.9.8.0` (versionCode `64`).

## [0.9.7.0] - 2026-09-22

### Added & Improved
- **Persistent Mobile Agent Progress**:
  - Routed structured Gateway progress into the Android agent runtime.
  - Added Gateway stage, message, checkpoint, round, and tool-call tracking for active runs.
  - Added Android 16 progress-centric foreground notifications with milestone-style progress and indeterminate fallback.
- **Gateway Network Recovery**:
  - Added validated-network monitoring to reconcile durable Gateway jobs when connectivity returns.
  - Preserved the existing Gateway-owned job model without replaying the original user prompt.
  - Started recovery only after startup persistence reconciliation to avoid recovery/interruption races.
  - Hardened Wi-Fi/cellular handoffs by tracking the validated Android Network instance.
- **Verification**:
  - Added unit coverage for Gateway notification text and stage-to-progress mapping.
  - Existing APK build, PR validation, Kotlin lint, UI validation, CodeQL, and debug build checks passed before release stamping.
- **Version Bump**:
  - Bumped version name to `0.9.7.0` (versionCode `62`).


## [0.9.6.1] - 2026-09-21

### Added & Improved
- **Suggestion Button Hold-to-Highlight**:
  - Implemented `SuggestionHighlightManager` allowing users to press and hold suggestion buttons to highlight matching sentences in AI response markdown.
  - Added sentence matching algorithm and smooth transition progress in `ChatMarkdown`.
  - Added unit test suite covering highlight selection, matching, and clearing.
- **Interaction Source Handling**:
  - Fixed suggestion and chip click/hold interactions using dedicated `MutableInteractionSource` instances on `AssistChip` and `SuggestionChip`.
- **Gateway Progress SSE & Activity Bar**:
  - Added `GatewayProgress` DTO and `GatewayProgressUpdate` provider event for SSE progress streams from OpenAI-compatible gateways.
  - Persisted gateway-owned tool traces in `ChatRepositoryImpl` with explicit `GATEWAY` visual identity.
  - Added `GatewayActivityBar` composable in `ChatBubble` for live progress tracking during execution.
- **Location MCP Tools**:
  - Added built-in tools for IP geolocation, reverse geocoding, forward geocoding, address lookup, and distance calculation (`calculate_distance`).
- **Version Bump**:
  - Bumped version name to `0.9.6.1` (versionCode `60`).

## [0.9.6.0] - 2026-09-21

### Added & Improved
- **Release Automation & Stabilization**:
  - Version bump to 0.9.6.0 with optimized signed release distribution pipeline.

## [0.9.5.6] - 2026-09-20

### Added & Improved
- **Chat UI Opacity Tuning**:
  - Tuned chat bubble container, timestamp, and details toggle opacity tokens in `ChatAlphaTokens`.
- **OpenRouter Batch Processing**:
  - Persistent background execution via `OpenRouterBatchWorker` (WorkManager) with Room database cache eviction.
- **LiteRT-LM v0.16.1**:
  - Upgraded Google LiteRT-LM to v0.16.1 with Qualcomm Hexagon NPU acceleration.

## [0.9.5.3] - 2026-09-19

### Added & Improved
- **Message Queuing During Generation**:
  - Implemented `GenerationQueueManager` with FIFO message sequencing, max capacity limits (50 items), and overflow protection.
  - Users can compose and queue up messages while assistant responses are actively generating.
  - Added queue badge counters, stop-confirmation dialogs, and automated dequeueing.
- **Shared Platform Labels**:
  - Added `PlatformLabel` entity and `PlatformLabelManager` utility supporting cross-platform labels.
  - 12-color predefined palette, format validation (2–50 characters, hex colors), and platform usage count tracking.
- **Llama Model Selection in Router Mode**:
  - Implemented `LlamaModelInfo`, `LlamaModelStatus`, `LlamaRouterConfig`, and `LlamaModelMapper`.
  - Parses parameter counts (millions), quantization codes, context window tokens, and dynamic server load metrics.
- **MCP Tool Manifest & Structure**:
  - Created standardized `mcp/` directory structure with `mcp/tools/manifest.json`, `mcp/resources/manifest.json`, `mcp/index.html`, and `assets/mcp-downloads/`.
  - Added triple-verification process checking manifest schema, SHA digests, and read-after-write consistency.
- **Version Bump**:
  - Bumped version name to `0.9.5.3` (versionCode `55`).

## [0.9.5.2] - 2026-09-19

### Added & Improved
- **UI Gestures & Interaction Polish**:
  - Restored chat pinning on long-press with spot glow and haptic feedback.
  - Allowed active user typing during generation without fading or blocking input composers.
  - Hid static archive icon and revealed only on right swipe.
  - Added `FancySwipeChatCard` with smooth spring transitions and color badges.
- **Backup & Encryption Enhancements**:
  - Added favorites backup, advanced settings backup (UI preferences, local runtimes), and granular selection options.

## [0.9.5.1] - 2026-09-19

### Added & Improved
- **Build & Compilation Fixes**:
  - Resolved generic `TypeToken` type inference issue in `AdvancedSettingsViewModel`.
  - Fixed Material 3 button styles in `fragment_advanced_llama_settings.xml`.
  - Cleaned deprecated `extractNativeLibs` attribute in `AndroidManifest.xml`.

## [0.9.5.0] - 2026-09-19

### Added & Improved
- **Llama Platform Integration**:
  - Added Llama model platform with configurable router endpoint, GPU offloading, and advanced options.

## [0.9.4.6] - 2026-09-17

### Added & Improved
- **Core Performance & ViewModel Optimizations**:
  - Application startup, activity lifecycle, and viewmodel dispatch optimizations merged from core refactoring.
  - Reduced memory churn and background thread allocations during chat streaming.
- **Qualcomm QNN HTP & NPU Acceleration**:
  - Fixed operator precedence in `QnnEnvironment.kt` probe environment readiness verification.
  - Resolved double-handling and redundant engine allocation in `LocalRuntimeQnnImpl.kt` fallback path.
- **Conversation Pinning & Gesture Integration**:
  - Production verification of swipe-to-dismiss, archive gestures, and 1-second conversation hold-to-pin.
  - Integrated Circuit Breaker user error classification across `ChatRepository` and `AgentRunner`.
- **Build & Packaging Details**:
  - Bumped version to `0.9.4.6` (versionCode `51`).

## [0.9.4.5] - 2026-09-17

### Added & Improved
- **Conversation Pinning & Swipe-to-Dismiss Gestures**:
  - `SwipeableChatRow` with spring-physics swipe gestures: swipe right to archive, swipe left to delete/pin.
  - Threshold haptic feedback and 1-second long-press interaction to pin/unpin conversations with elevation spot glow.
  - Added conversation sorting by favorite/pinned status, draft indicators, and pinned badges.
- **Android Runtime Services & Assist Integration**:
  - Added `GptTileService` for Quick Settings tile control.
  - Added `VoiceInteractionService` and `VoiceInteractionSessionService` for system assistant invocation.
  - Added `VoiceAssistantActivity` handling `android.intent.action.ASSIST`.
  - Added vector drawable `ic_gpt_mobile`.
- **UI Contrast & Polish**:
  - Increased `ToolTraceBlock` opacity to `0.15f` for improved dark-theme visibility and contrast.
- **Version Bump**:
  - Bumped version to `0.9.4.5` (versionCode `49`).

## [0.9.4.4] - 2026-09-17

### Added & Improved
- **Ollama Auto-Continue for Truncated Generations**:
  - Native auto-continuation when Ollama responses truncate due to length limits.
- **Local Runtime & Hardware Acceleration**:
  - Qualcomm QNN NPU detection, OpenCL GPU, and multi-threaded CPU fallback optimizations.
- **Version Bump**:
  - Bumped version to `0.9.4.4` (versionCode `48`).

## [0.9.4.3] - 2026-09-16

### Added & Improved
- **OpenRouter Credits & Balance Card Integration**:
  - Fully integrated `FancyOpenRouterCreditsCard` into `PlatformSettingScreen`.
  - Added reactive credit retrieval and caching in `PlatformSettingViewModel`.
  - Polished error states for network and authentication failure modes.
- **Local Runtime & Hardware Acceleration**:
  - Restored `LocalAccelerators` and `LocalModelValidator` classes.
  - Verified Qualcomm QNN NPU runtime stability alongside OpenCL GPU and CPU fallback pathways.
- **UI & Presentation Polish**:
  - Refined layout, touch targets, and spacing in `HomeTopBar`.
- **Version Bump**:
  - Bumped version to `0.9.4.3` (versionCode `46`).

## [0.9.4.2-pre] - 2026-09-16

### Added & Improved
- **UI Cleanups & Presentation Polish**:
  - `HomeTopBar`: Cleaned up navigation bar layout and action item spacing.
  - Comprehensive documentation updates covering RAG architecture, Sandboxed Artifact rendering, and full-duplex Voice Session lifecycle.
- **CI / CD Pipeline Resilience**:
  - Automated pre-release generation and build artifact uploads for pre-release tags.
- **Version Bump**:
  - Bumped version code to `45` (`0.9.4.2-pre`).

## [0.9.4.1] - 2026-09-15

### Added & Improved
- **OpenRouter Account Balance & Credits Widget**:
  - Real-time balance and usage queries with animated UI card on Platform Settings.
  - Cached balance repository to avoid unnecessary network queries and rate-limit hits.
  - Graceful handling of network timeouts, credential errors, and invalid API keys.

## [0.9.4.0] - 2026-09-14

### Added & Improved
- **Qualcomm QNN NPU Hardware Acceleration**:
  - Runtime verification and detection for Qualcomm Neural Processing Unit (`QNN`) libraries.
  - Hardware diagnostics probe view under Debug Mode displaying SoC architecture and NPU acceleration availability.
  - Automatic fallback hierarchy: QNN (NPU) → OpenCL (GPU) → Multi-threaded CPU.

## [0.9.3.0] - 2026-09-13

### Added & Improved
- **Debug Mode & Diagnostics HUD**:
  - `SettingDataSource` and `SettingRepository`: Added `observeDebugMode()`, `getDebugMode()`, and `updateDebugMode(enabled: Boolean)` with full DataStore persistence.
  - SettingScreen: Added toggles for Debug Mode and real-time Diagnostics HUD.
  - ChatBubble & ChatScreen: Renders Diagnostics HUD for assistant responses, tracking generation latency, TTFT, token statistics, and memory/thermal state.
  - Test suites: Parity across all test fakes and repositories (`FakeSettingDataSource`, `BackupFakeSettingDataSource`, `PlatformSettingViewModelTest`).
- **Thinking UI Contrast Polish**:
  - `ThinkingBlock`: Refined background alpha from `0.5f` to `0.25f` for improved legibility and seamless integration with dynamic Material 3 surfaces.
- **Release Version Bump**:
  - Version bump to `0.9.3.0` (versionCode `40`).

## [0.9.2.4] - 2026-09-13

### Added & Improved
- **Voice Session Coordination (Full-Duplex Conversations)**:
  - `VoiceSessionCoordinator`: State machine managing full-duplex voice conversation lifecycle (`IDLE → LISTENING → TRANSCRIBING → THINKING → SPEAKING`).
  - User interruption handling with graceful state transitions and audio resource management.
  - Low-latency voice interaction pipeline for real-time conversational AI.
- **Sandboxed Artifact Previewing**:
  - `SandboxedArtifactView`: Safe interactive HTML/SVG artifact previewing with sandboxed WebView isolation.
  - Prevents XSS and malicious script execution while allowing rich interactive content display.
- **Multi-Step Agent Workflow Display**:
  - `AgentPlanCard`: Composable UI component for displaying multi-step autonomous agent workflows.
  - `AgentPlan` and `AgentTaskStep` models for structured multi-step task planning and execution tracking.
  - Visual progress indicators for agent task steps with status tracking.
- **Local Document RAG Engine**:
  - `DocumentRagEngine`: On-device document chunking, keyword retrieval (BM25), and vector retrieval (cosine similarity).
  - Local document indexing and retrieval without external API dependencies.
  - Supports document ingestion, chunking, and semantic search capabilities.
- **Resilient Streaming Client**:
  - `ResilientStreamingClient`: Enhanced streaming with automatic retry, exponential backoff, and connection recovery.
  - Improved reliability for long-running streaming sessions with graceful degradation.
- **Streaming Diff Parser**:
  - `StreamingDiffParser`: Real-time diff parsing for streaming responses with incremental updates.
  - Supports live code diff visualization and progressive content rendering.
- **Thermal & Memory Governor**:
  - `ThermalAndMemoryGovernor`: Dynamic thermal and memory management for sustained performance.
  - Adaptive throttling based on device thermal state and available memory.
  - Prevents thermal throttling and memory pressure during intensive AI workloads.

## [0.9.0] - 2026-09-10

### Added & Improved
- **LiteRT-LM Hardware Acceleration & Dynamic Inference Engine**:
  - Phase-split scheduling (`LocalInferencePhase.PREFILL` / `GENERATING`) with foreground notification updates displaying "Processing prompt…" and "Generating response…".
  - Cooperative thread yielding (`yield()`) during conversation creation and message evaluation to eliminate UI thread hitches.
  - DeviceHardwareGovernor: dynamic thermal and battery throttling adjusting context token clamps, stream intervals, and top-k sampling.
  - High-refresh 120Hz/144Hz token dispatching with 8ms frame budgets on capable hardware.
  - Idle memory auto-unloading (`unloadIfIdle`) in `LocalEngineHolder` to protect device memory after periods of inactivity.
  - Rolling Context Window Compactor (`RollingContextWindowCompactor`): Turn 0 anchor prefix preservation and rolling turn truncation preventing context ceiling exhaustion.
  - Real-time generation telemetry (`LocalInferenceMetrics`) exposing tok/s, TTFT, token counts, and thermal badges in the chat action row (`TelemetryBadge`).
  - Tier-aware context scaling up to 8,192 tokens on >=12GB/16GB devices, with SoC variant clamping for NPU targets.
- **OpenRouter Advanced Provider Routing & Reasoning**:
  - Configure provider ordering, fallback providers, sorting strategy (`price`, `throughput`, `latency`), data collection policies (`allow`, `deny`), and quantizations (`fp16`, `int8`, `int4`, `bf16`).
  - Added custom max reasoning tokens configuration.
  - Interactive UI with `OpenRouterAdvancedSettingsDialog` inside Platform Settings.
  - Added Room Schema 15 and `MIGRATION_14_15` (`open_router_routing` column on `platform_v2`).
- **Chat Presentation & Responsiveness**:
  - Instant bottom anchoring via `rememberChatListState` keyed on message counts with target message protection for favorites and deep links.
  - Collapsible details toggle button (`DetailsButton`) with accessible spring animations (`fastEffectsSpec`).
  - Continuous streaming heartbeat pulse (`●`) during response generation and tool execution.
- **Google Gemini Tool Calling Reliability**:
  - Recursive tool parameter sanitization (`geminiToolParameters`) stripping transport metadata (`x-mcp-header`, `x-mcp-param`) and unsupported schema keywords (`$schema`, `propertyNames`, `additionalProperties`).
  - Immediate detection of tool schema rejection errors (`throwIfToolDefinitionsRejected`) to prevent multi-API retry loops and timeouts.

## [0.8.9.1] - 2026-09-09

### Added & Improved
- **OpenRouter Advanced Provider Routing & Reasoning**:
  - Configure provider ordering, fallback providers, sorting strategy (`price`, `throughput`, `latency`), data collection policies (`allow`, `deny`), and quantizations (`fp16`, `int8`, `int4`, `bf16`).
  - Added custom max reasoning tokens configuration.
  - Interactive UI with `OpenRouterAdvancedSettingsDialog` inside Platform Settings.
  - Added Room Schema 15 and `MIGRATION_14_15` (`open_router_routing` column on `platform_v2`).
- **Chat Presentation & Responsiveness**:
  - Instant bottom anchoring via `rememberChatListState` keyed on message counts.
  - Collapsible details toggle button with accessible spring animations (`Motion.kt`).
  - Continuous streaming heartbeat pulse (`●`) during response generation and tool execution.

## [0.8.9] - 2026-09-09

### Added & Improved
- **Favorites Management & Deep Navigation**:
  - Rich Favorite Detail View with custom category groups ("All", user groups, "+ Add Group"), assignment dropdowns, Markdown/LaTeX/code rendering, and confirmation dialog for unfavoriting.
  - Reliable In-Chat Navigation: Tapping "View" in the favorite detail dialog seamlessly resolves chat rooms and navigates directly to the target favorited message.
  - Platform Tab Auto-Switching: Switching automatically to the favorited message's provider tab when navigating into multi-platform chat rooms.
  - Taller Highlight Container: `OpponentResponseContainer` with an animated cyan highlight surrounding the entire assistant response block (avatar, loading indicators, platform selection pills, and chat bubble).
  - Haptic feedback when favoriting messages.
- **Autonomous Agent Tooling & Line Slicing**:
  - Built-in `read_file_slice` tool for extracting bounded text line slices with 1-based indexing, range validation, and line count metadata.
  - MCP line slicing (`start_line`, `end_line`) for remote file-reading tools (e.g. GitHub `get_file_contents`) to prevent context window overflow and minimize token overhead.
  - Prebundled `droid-mcp-web` Online Search (`web_search`, `fetch_webpage`).
  - Restyled `ToolTraceBlock` with dark card backgrounds, brand icons, and collapsible tool outputs.
- **Multi-Key API Credential Rotation**:
  - `ApiCredentialRotator` with high-availability round-robin failover across multiple keys per provider.
  - Automatic fallback on HTTP 429, 402, 401, and quota exhaustion without interrupting streaming sessions.
  - Dynamic `+API` key management UI across Platform Settings, Setup Wizard, and MCP Tool Connections.
- **Architecture & Persistence**:
  - Room Database Schema v14 with full migrations supporting tool connections, timeline items, agent run persistence, agent tool bindings, and configurable platform tool-call limits (`max_tool_calls`).
  - Target Android 16 (API 36), Java 21 bytecode, and modern 64-bit ABIs (`arm64-v8a`, `x86_64`).
  - Local LiteRT-LM runtime integration with hardware acceleration selection (NPU, GPU, CPU) and background model downloading via WorkManager.

## [0.8.2] - 2026-09-05

### Added & Improved
- **Separate Application ID (`dev.chungjungsoo.gptmobile.improved`)**:
  - Allows direct, side-by-side installation alongside the original repository's app without signature conflicts or needing to uninstall.
  - Custom deep-linking scheme updated to `dev.chungjungsoo.gptmobile.improved://oauth/mcp/` for MCP OAuth callbacks.
- **Deterministic & Persistent Release Signing**:
  - Replaced ephemeral per-build keystores with a deterministic, consistent release signing pipeline.
  - All future releases and updates will install in-place over previous builds without prompting for uninstallation.
- **Uncapped Autonomous Agent Execution Limits**:
  - Uncapped execution loop: `maxRounds = Int.MAX_VALUE`, `maxToolCalls = Int.MAX_VALUE`.
  - Uncapped execution timeouts: `runTimeoutMillis = Long.MAX_VALUE`, `toolTimeoutMillis = Long.MAX_VALUE`.
  - Concurrency expanded to 32 parallel tool executions with uncapped buffer output (`Int.MAX_VALUE`).
- **Web Search & URL Tool Enhancements**:
  - `ReadUrlTool`: Body limit expanded to 100 MB, output limit to 50 MB, and redirect hops to 50.
  - `WebSearchTool`: Result ceiling expanded to 100 search results across Firecrawl, Perplexity, and Exa.
- **Model Context Protocol (MCP) Scalability**:
  - Uncapped discovery limits (`MAX_DISCOVERED_TOOLS` and `MAX_TOOL_PAGES` to `Int.MAX_VALUE`).
  - Expanded endpoint length limits to 32 KB and Authorization headers to 128 KB.

## [0.8.1] - 2026-09-04

### Added
- Configuration Backup & Restore in Settings:
  - Export and restore platform configurations, customized model parameters, URLs, prompts, and theme preferences via formatted JSON.
  - Integrated with `SecretVault` for secure credential persistence and re-encryption.
  - Interactive UI dialogs for backup export (with one-tap clipboard copy) and validated configuration restore.

## [0.8.0] - 2025-02-17

### Added
- Support for favorites in chat rooms (`is_favorite` flag and Room migration 11 to 12).
- Agent tools per provider profile (native tool calling with OpenAI, Groq, Anthropic, Gemini).
- Web search tool support via Firecrawl, Perplexity, or Exa.
- MCP (Model Context Protocol) Streamable HTTP server integration with bearer token and OAuth authentication.
- Zero-allocation SSE streaming parser (`SseUtils`).
- Edge-to-edge layout support with `WindowInsetsCompat`.
- Automated CI/CD release build pipeline for APK and AAB artifacts.
