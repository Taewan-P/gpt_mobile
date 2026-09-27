# GPT Mobile AI (Improved)

An enhanced, high-performance, and feature-rich Android client for Large Language Models (LLMs), autonomous tool calling (MCP), on-device Qualcomm Snapdragon NPU acceleration, local document RAG, and sandboxed AI execution.

> **Fork Overview**: This version is a modernized, high-performance fork of [GPT_Mobile_AI](https://github.com/chungjungsoo/GPT_Mobile). It delivers a significantly smoother user experience, autonomous agent tools (MCP), on-device privacy AI with adaptive hardware acceleration (Qualcomm Hexagon NPU / GPU / CPU), real-time in-chat hardware diagnostics, background execution persistence, multi-key credential failover, encrypted backup vaults, local document RAG, sandboxed interactive artifacts, voice session coordination, conversation pinning, swipe gestures, and major battery and performance improvements.

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-36-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%202.x-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4.svg)](https://developer.android.com/jetpack/compose)

---

## 🌟 What's New & Different in this Fork? (Sorted by User Impact)

Here is a comprehensive breakdown of new features and enhancements in this fork, ordered from the most impactful everyday user experiences down to under-the-hood engine improvements:

### 1. 📌 Conversation Pinning, Swipe Gestures & Three-Tier Sorting
- **Three-Tier Home List Organization**: Conversations are dynamically sorted in real time into three clear tiers:
  1. *Active/Generating Runs*: Live background agent executions stay pinned to the very top with animated spinners.
  2. *Pinned Conversations*: Important chats pinned via 1-second hold stay directly below active runs, decorated with a distinct `PushPin` badge.
  3. *Recent Conversations*: All other chats automatically follow, ordered by `updatedAt DESC`.
- **1-Second Long-Press Pinning**: Pressing and holding any chat item for 1000ms toggles its pinned status with haptic feedback (`HapticFeedbackType.LongPress`) and immediate toast confirmation.
- **Material 3 Swipe Actions (`SwipeToDismissBox`)**:
  - **Swipe Right (`StartToEnd`)**: Quickly archives the conversation with archive container visuals.
  - **Swipe Left (`EndToStart`)**: Prompts a safe confirmation dialog before permanent deletion, guarded against ongoing agent runs with `agentRunCoordinator.withChatGate`.

### 2. ⚡ Ultra-Smooth Streaming, Visual Enhancements & Continuation
- **Zero-Lag High-Refresh Display**: Even with ultra-fast models delivering hundreds of tokens per second (Cerebras, Groq, Claude 3.7), scrolling and typing remain buttery smooth at 60fps/120fps. Adaptive token batching eliminates UI stutter and thread lockups.
- **Instant Bottom Anchoring (`rememberChatListState`)**: Entering a conversation immediately lands at the newest message with zero jump or scroll delay. Navigating via search or a favorited message smoothly preserves focus on the target message.
- **Bottom-Right Timestamp Alignment**: Streamlined chat message bubble geometry with clean right-aligned timestamp anchors across user and assistant messages.
- **Pulsating Continuation Glow Chip**: Conversational continuation trigger detection (`"more"`, `"keep going"`, `"continue"`, `"proceed"`, `"elaborate"`) renders an animated glowing chip for one-tap context-preserving thought expansion.
- **Live Active Run Spinners**: Animated circular loading indicators directly on conversation cards in the chat list provide instant feedback on in-progress background agent executions.
- **Collapsible Reasoning & Tool Details**: Clean, modern assistant bubbles featuring spring-animated collapsible details (`DetailsButton`), full-bubble cyan focus highlights, and continuous streaming activity indicators positioned cleanly above user message bubbles.
- **In-Chat Hardware Diagnostics HUD**: View live SoC identifier, system RAM metrics, battery status, thermal throttle state, and NPU readiness directly within the chat bubble when Debug Mode is turned on.

### 3. 🧠 Autonomous Agent Tools, Dedicated MCP Tools Screen & Marketplace
- **Agent Flight Recorder**: Active Gateway jobs now expose a compact, expandable operational HUD in chat with live stage, round, tool-call productivity, checkpoint, current route/tool, recovery state, and a deduplicated recent-activity trail. It surfaces operational telemetry without exposing private chain-of-thought.
- **Adaptive Work-State UI**: Gateway telemetry is translated into human-readable states—Starting, Exploring, Focused, Using tools, Recovering, Synthesizing, and Finalizing—so long autonomous jobs communicate what they are doing instead of showing a generic spinner.
- **Built-in Agent Runtime**: AI models can dynamically run multi-step reasoning, safely invoke tools, handle external errors, and synthesize complete answers.
- **Visual Agent Plan Cards (`AgentPlanCard`)**: Clean step-by-step progress cards displaying multi-step workflows, tool execution states, and sub-task status.
- **Dedicated Full-Screen MCP Tools Selection (`McpToolsSelectionScreen`)**: Browse tools by server category, inspect function parameters and capability icons, and independently toggle master, remote, and local tool switches.
- **Tool Connection Platform Badges**: Visual platform branding icons (OpenAI, Anthropic, Gemini, Groq, Ollama, OpenRouter, LiteRT-LM) integrated into connection headers.
- **In-App MCP Marketplace**: Discover, install, and toggle tools from a full-screen marketplace in one tap.
- **Granular Tool Control (Room Schema 19)**: Configure `disable_remote_tools` and `disable_local_tools` individually per AI platform to tailor capabilities to specific tasks.
- **Zero-Config Resilient Web Search**: Built-in `droid-mcp-web` online search (`web_search` and `fetch_webpage`) supporting Firecrawl, Perplexity, Exa, and resilient DuckDuckGo scraping fallback with multi-endpoint redundancy and URL decode safety—no API keys or search configurations required.
- **Safety Ceiling & Watchdog**: Configurable tool call ceiling and 45-second execution watchdog prevent infinite loops and runaway costs.
- **Gemini MCP Schema Sanitization**: Automatically strips unsupported keywords (`x-mcp-*`, `$schema`, `propertyNames`, `additionalProperties`) ensuring seamless compatibility with Google Gemini function calling.

### 4. 🎨 Interactive Sandboxed Artifacts & Voice Coordination
- **Sandboxed Artifact Preview (`SandboxedArtifactView`)**: Safely renders interactive HTML, CSS, JavaScript, and SVG artifacts generated by models in an isolated web environment.
- **Full-Duplex Voice Session Coordinator (`VoiceSessionCoordinator`)**: Low-latency voice session state management for smooth bidirectional conversational voice interactions.

### 5. 📚 Local Document RAG Engine (`DocumentRagEngine`)
- **On-Device Document Retrieval**: Chunk, index, and query local documents (PDFs, Markdown, text files) with hybrid keyword and vector retrieval entirely on-device for maximum privacy.
- **Zero Cloud Leakage**: Document embeddings and matching run locally without uploading document contents to third-party servers.

### 6. 🚀 Qualcomm AI Engine Direct (QNN) & On-Device Hexagon NPU Acceleration
- **Native Qualcomm NPU Serving**: Integrates Qualcomm QNN SDK and LiteRT delegate runtime to run quantized LLMs directly on Qualcomm Hexagon cDSP/NPU hardware.
- **FastRPC Native Integration**: Configured `QnnEnvironment` with `ADSP_LIBRARY_PATH` and native skeleton verification (`libQnnHtpV79Skel.so`), yielding blazing-fast local tokens/sec and dramatic battery savings.
- **Dynamic Hardware Governor**: Automatically selects optimal execution targets (NPU / GPU / CPU) based on device SoC capabilities, thermal state, and battery level.

### 7. 🌙 Resilient Background & Screen-Off Execution
- **Run Tasks with Screen Off**: Long research queries, multi-step tool workflows, web scraping, and on-device model generation continue without interruption when the screen turns off or the phone is locked.
- **High-Priority Heads-Up Alerts (`CHANNEL_AGENT_COMPLETION`)**: Dispatches `IMPORTANCE_HIGH` heads-up notification banners with sound upon task completion so you can multitask freely.
- **Engineered for Android 14+**: Uses `AgentRunForegroundService` with `dataSync` compliance and temporary partial CPU `WAKE_LOCK` management to prevent battery-optimization process termination.

### 8. 🔑 Multi-Key Round-Robin Rotation, Auto-Failover & Circuit Breaker
- **Never Hit Rate Limits**: Add multiple API keys per provider in the dynamic `+API` credential manager.
- **Smart Failover & Circuit Breaker**: The `ApiCredentialRotator` automatically distributes load across your keys and instantly switches to backup keys when receiving rate-limit (HTTP 429), quota-exhausted, or payment-required (HTTP 402) errors.
- **Circuit Breaker Error Classification**: Structured circuit breaker propagation across `ChatRepository` and `AgentRunner` prevents repetitive doomed retries on degraded endpoints.

### 9. 🎛️ Dedicated AI Platforms Hub & Interactive Model Sorting
- **Dedicated AI Platforms Management Screen (`AiPlatformsScreen`)**: Manage, toggle, edit, and organize all your cloud and local AI platforms in a clean, dedicated hub situated directly above Local Models in Settings.
- **Interactive Platform Sorting**: Quickly filter and sort models on the home screen using interactive chips (`DEFAULT`, `NAME`, `PROVIDER`, `ENABLED_FIRST`).
- **Reactive State Syncing**: Instant synchronization between platform list cards, settings detail toggles, and chat model pickers.

### 10. 🔀 OpenRouter Advanced Routing & Reasoning Options
- **Provider Routing Controls**: Select preferred providers, fallback orders, ignore specific hosts, and control data privacy / logging preferences directly from platform settings.
- **Custom Reasoning Tokens**: Fine-tune reasoning effort, max thinking tokens, and temperature parameters for models like Claude 3.7 Sonnet Thinking, DeepSeek R1, and OpenAI o-series.

### 11. 🔒 High-Performance Local AI (LiteRT-LM & Remote/Local Ollama)
- **Private On-Device Chat**: Run local models (`.bin`, `.tflite`) entirely offline on your phone with zero data sent to the cloud, or connect to local or remote Ollama servers.
- **Warm Engine Retention & Phase Scheduling**: Keeps model weights warm across turns to eliminate reload latency, paired with separate `PREFILL` and `GENERATING` phase management.
- **Resilient Background Model Downloader**: WorkManager-backed downloads with SHA-256 integrity verification, pause/resume support, and notification progress updates.

### 12. ⭐ Rich Favorites Management & Custom Categorization
- **Personal Knowledge Hub**: Bookmark important messages, code snippets, and explanations.
- **Custom Group Filters**: Organize favorites with custom group filter chips ("All", user-defined categories, "+ Add Group").
- **Full-Screen Reader**: Rich Markdown, LaTeX math equations, syntax-highlighted code blocks, and one-tap deep linking straight back to the original message in the chat thread.

### 13. 🛡️ Rolling Context Window Compactor (No Overflow Crashes)
- **Infinite Conversations**: `RollingContextWindowCompactor` dynamically compacts older chat turns while strictly preserving your initial prompt anchor (Turn 0) and system instructions.
- **Zero Token Overflow Crashes**: Automatically stays within the model's exact context limit without dropping system rules.

### 14. 🔐 Keystore Encryption & Encrypted Vault Backups
- **Device Credential Security**: All API keys and secrets are protected using Android Keystore-backed AES-256-GCM encryption (`SecretVault`) stored in secure `noBackupFilesDir`.
- **Passphrase Vault Backups**: Export and restore your complete database with PBKDF2-HMAC-SHA256 key derivation and authenticated AES-256-GCM encryption.

### 15. 🗄️ Instant Search & Robust Database (`ChatDatabaseV2`)
- **Fast Full-Text Search**: Instant search indexing across all conversation histories and tool executions.
- **Safe Room Migrations**: Powered by `ChatDatabaseV2` (Schema v19) with verified automated migrations guaranteeing zero data loss across updates.

### 16. 📉 Up to 60% Smaller App Download Size
- **Native ABI Splits**: Published as targeted `arm64-v8a` and `x86_64` release APK packages alongside universal APKs, saving storage space and cellular download data.

---

## ⚡ Quick Comparison

| Feature / Capability | Upstream Original | This Improved Fork |
| :--- | :--- | :--- |
| **Chat Pinning & Sorting** | ❌ None | 📌 Three-tier sorting (Active > Pinned > Recency) + PushPin badge |
| **Swipe Gestures** | ❌ None | 👆 Swipe-right to archive, swipe-left to delete with confirmation |
| **High-Speed Streaming** | Stutters on fast token bursts | 🧈 Smooth 60/120 fps adaptive buffer |
| **Initial Chat Scroll** | Delayed jump or layout lag | ⚡ Instant bottom anchoring (`rememberChatListState`) |
| **Chat Bubble Layout & Timestamps** | Left/floating timestamps | ⏱️ Bottom-right aligned timestamps & semantic colors |
| **Conversational Continuation** | ❌ Manual re-prompt | ✨ Glowing pulsating continue chip with full context preservation |
| **Live Chat Status** | ❌ None | 🔄 Animated progress spinner on active chat cards |
| **Agent Tools & MCP** | ❌ Not supported | ✅ Built-in Agent Engine + Model Context Protocol |
| **Agent Plan Visualization** | ❌ Not supported | 📋 Visual `AgentPlanCard` step-by-step progress tracking |
| **Interactive Artifacts** | ❌ Raw code blocks only | 🖼️ `SandboxedArtifactView` for interactive HTML/SVG rendering |
| **Local Document RAG** | ❌ Not supported | 📑 `DocumentRagEngine` on-device hybrid keyword & vector retrieval |
| **Voice Conversations** | Basic / single-turn | 🎙️ `VoiceSessionCoordinator` low-latency full-duplex state |
| **Dedicated MCP Tools View** | ❌ Not supported | ✅ Full-screen `McpToolsSelectionScreen` with granular remote/local toggles |
| **MCP Marketplace** | ❌ Not supported | ✅ Full-screen in-app marketplace with 1-click install |
| **Tool Execution Limits** | ❌ None | ✅ Configurable ceiling + 45s execution watchdog |
| **Qualcomm Hexagon NPU** | ❌ None | ✅ Native Qualcomm QNN runtime + FastRPC cDSP HTP delegate |
| **In-Chat Diagnostics HUD** | ❌ None | ✅ Real-time SoC, RAM, battery, thermal, and NPU telemetry badge |
| **Background / Screen-Off Run** | ❌ Killed on screen lock | ✅ Resilient `AgentRunForegroundService` + WakeLock |
| **Completion Notifications** | ❌ None | 🔔 `IMPORTANCE_HIGH` heads-up notification banners with sound |
| **Multi-Key API Failover** | ❌ Single key only | ✅ `ApiCredentialRotator` round-robin, rate-limit fallback & circuit breaker error propagation |
| **AI Platforms Hub & Sorting** | ❌ Basic list | ✅ Dedicated `AiPlatformsScreen` + interactive sorting chips (`DEFAULT`, `NAME`, `PROVIDER`, `ENABLED_FIRST`) |
| **OpenRouter Advanced Routing** | ❌ Basic completions only | ✅ Provider preferences, fallbacks, and reasoning controls |
| **Local Inference Engine** | Basic / Limited | ✅ LiteRT-LM & Qualcomm QNN (NPU/GPU/CPU), thermal governor, warm engine retention |
| **Long Context Conversations** | Vulnerable to context overflows | ✅ Rolling context compaction with Turn 0 anchor preservation |
| **Credential Security** | Plaintext / basic storage | ✅ Android Keystore AES-256-GCM (`SecretVault`) |
| **Encrypted Backups** | ❌ Not supported | ✅ Passphrase-protected PBKDF2 + AES-GCM export/import |
| **Favorites Management** | ❌ Basic or none | ✅ Custom group chips, rich Markdown dialog, chat jump |
| **Web Search** | ❌ Manual setup / none | ✅ Zero-config `droid-mcp-web` (DuckDuckGo, Firecrawl, Perplexity, Exa) |
| **Search & Database** | Monolithic legacy database | ✅ Modern `ChatDatabaseV2` (Schema v19) with instant search |
| **APK Footprint** | Large universal APK | ✅ Up to 60% lighter native ABI split APKs |

---

## 🌐 Supported Providers

- **Cloud**: OpenAI (`gpt-4o`, `o1`, `o3-mini`), Anthropic Claude (`claude-3-7-sonnet`, `claude-3-5-haiku` with reasoning blocks), Google Gemini (`gemini-2.0-flash`, `gemini-1.5-pro` with MCP schema sanitization), Groq (ultra-fast LPU inference with reasoning extraction), OpenRouter (complete catalog, provider routing, fallback, and reasoning parameters), and any OpenAI-compatible API.
- **Local / Self-Hosted**: Qualcomm Hexagon NPU & LiteRT on-device LLMs (NPU/GPU/CPU accelerated), Ollama (custom IP and port, local & remote server support).

---

## 🛠️ Architecture & Tech Stack

- **UI**: Jetpack Compose, Material Design 3 (fully optimized with stability contracts)
- **Language**: Kotlin 2.x, Coroutines, StateFlow
- **Networking**: Ktor Client with OkHttp on Android, Server-Sent Events (SSE)
- **Persistence**: Room Database (`ChatDatabaseV2`, Schema v23), DataStore Preferences
- **Dependency Injection**: Hilt / Dagger with KSP
- **Security**: Android Keystore AES-256-GCM credential encryption (`SecretVault`)
- **Inference**: Qualcomm QNN SDK & Google LiteRT-LM with dynamic hardware governor
- **Background Execution**: Android Foreground Service (`dataSync`), CPU Partial WakeLock, WorkManager

---

## 🏗️ Getting Started

### Prerequisites
- Android Studio with AGP 9.4 support, or the Gradle wrapper for command-line builds
- JDK 21
- Android SDK 37.0 (compile) / Target SDK 36 / Min SDK 31

### Building from Source
```bash
# Clone the repository
git clone https://github.com/tailscale-signin/GPT_Mobile_AI-improved.git
cd GPT_Mobile_AI-improved

# Build debug APK
./gradlew assembleDebug

# Build release APK (ABI splits & universal)
./gradlew assembleRelease

# Run unit tests
./gradlew testDebugUnitTest
```

---

## 📄 License

This project is licensed under the GNU General Public License v3.0 (GPL-3.0) - see the [LICENSE](LICENSE) file for details.
