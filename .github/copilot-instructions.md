# AI Coding Agent & Tool Guidelines

This repository enables autonomous coding agents and automated tools to freely assist with tasks.

## Tool & Agent Usage Policy
- **Agent Autonomy**: AI agents are encouraged to inspect files, execute searches, propose changes, and create/update workflows or code.
- **Workflow Limits**: Agents operating on conversational turns are guided to work efficiently within per-turn tool call budgets (e.g. up to 6 tool executions per turn) while maintaining unlimited access across conversation turns.
- **Scope & Freedom**: Unrestricted tool usage is permitted for refactoring, testing, documentation, releases, and dependency maintenance across the entire repository.

## Repository Architecture Map
- **Hilt Dependency Injection Bindings**: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/di/`
  - When adding or replacing repository interfaces, register `@Binds` in `RepositoryModule.kt` or a dedicated feature module (e.g., `OpenRouterSettingsModule.kt`).
- **Target SDK & Toolchain**:
  - `compileSdk = 37`, `targetSdk = 36`, `minSdk = 31`
  - Java 21, AGP 8.x, Gradle 8.13+
- **Pre-stripped Native Libraries (JNI Keep Rules)**:
  - Must not remove entries from `packaging.jniLibs.keepDebugSymbols` in `app/build.gradle.kts` (e.g., `**/libLiteRt.so`, `**/liblitertlm_jni.so`, `**/libQnn*.so`, etc.).
  - Qualcomm FastRPC cDSP requires `useLegacyPackaging = true` to load `libQnnHtpV79Skel.so` directly from the native library directory.
- **Database & Room Migrations**:
  - Main database: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/database/ChatDatabaseV2.kt`
  - Migrations: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/database/ChatDatabaseV2Migrations.kt`
  - Schema exports are configured with KSP (`room.schemaLocation`). Always wrap bulk database updates in `@Transaction` inside Room DAOs.
- **Concurrency & Coroutine Scope Lifecycle Rules**:
  - **LiteRT-LM**: On-device native model inference must run within dedicated background dispatchers (`Dispatchers.Default` / single-threaded worker contexts) and handle cancellations cleanly.
  - **Network & Background Sync**: Network calls and batch processing should be dispatched via `Dispatchers.IO`. Long-running, unconstrained, or deferred tasks should be scheduled with AndroidX `WorkManager` (see `dev/chungjungsoo/gptmobile/data/worker/`).
