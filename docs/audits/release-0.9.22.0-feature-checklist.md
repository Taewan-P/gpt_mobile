# 0.9.22.0 feature audit

This records the implementation of the requested release features. Host regression tests and APK validation do not replace physical-device acceptance, especially for QNN and Compose animations.

| Request | Implementation / verification |
| --- | --- |
| 1 | Free MCP discovery and runtime eligibility in AgentToolResolver; exact profile/tool consent in FreeModelToolConsentStore and ChatViewModel; themed lock/slider dialog. Runtime regression tests cover grants and disabled tools. |
| 2 | ChatMarkdown table cells use unlimited lines and clipping rather than ellipsis; MarkdownTable provides horizontal scrolling and cell padding. |
| 3, 24 | ToolPermissionDialogs uses themed cards, MCP icon and allow-all-provider checkbox, backed by ToolApprovalManager. |
| 4 | FreeAiProvider preserves custom names and defaults to provider display names; profile settings permit renaming. Name regression tests. |
| 5 | ChatScreen archive control is a centered caret. |
| 6 | CompleteBackupManager exports preferences/themes and selected secrets including Hugging Face. |
| 7, 17 | Backup/restore selection dialogs; persisted checkbox choices and Select all; restore file chosen before options. GPTFULL3 authenticated encryption with separate portable key, legacy compatibility, corruption/wrong-key/reinstall tests. No mandatory password, no claim of unbreakable encryption. |
| 8 | Both message toolbars have matching horizontal padding; idle alpha 0.7 and active alpha 1.0. |
| 9 | Streamed words start transparent and fade over 1500 ms; final arrival timestamps survive completion. |
| 10 | ApiStateFlowExtensions maps empty/whitespace/thinking-only completion to a visible error; partial failures retain content and an error. Regression tests. |
| 11 | Brave Search uses its public endpoint and user token directly; no MCP host required. |
| 12 | LocalModelsViewModel displays persisted, comparable completed model benchmark scores. Delegation runs and cancelled runs are excluded. |
| 13 | QNN 2.50.0 is the current published Maven release checked for this audit; matching dispatch, qualified packages/devices and pinned native hashes are wired in the existing runtime. No unsupported version substitution. See local-runtime-integrity.md for hardware acceptance. |
| 14 | Catalog metadata and exact MTP tokens feed the MTP filter beside LiteRT/QNN; MediaTek does not imply MTP. Regression test. |
| 15 | AiPlatformsScreen has expandable Remote/Local/Free groups, animated layout and icon controls. |
| 16 | DelegationText streams independent helper snapshots into timeline entries; debug renderer uses green. Helper output is excluded from generic notices and primary answer. Snapshot tests. |
| 18 | ChatScreen input outer layer is transparent, bubble remains filled. |
| 19 | Actual tool/generation activity drives five-second summaries. Idle warm LiteRT/QNN can phrase a short label under an exclusive nonblocking lock, with a 1.5-second cap and 16-token budget; no remote request, model loading or user data. Dots and themed gradient animate while running. Busy/cold engine regression tests. |
| 20 | Direct GitHub API connector uses user token for repository/PR operations and Actions list, inspect, logs, artifacts, dispatch, rerun and cancel. Authenticated actions use approval policies; GitHub calls are never shared as read-only. Mock API tests cover credentials, parameters, writes and bounded logs. This is an app-specific integration, not a claim to duplicate ChatGPT's private connector implementation. |
| 21 | GPTMobileIcon and generation progress ring use theme colors. |
| 22 | ConversationReadStateStore and HomeScreen display filled themed icons and bold titles for completed unseen responses. |
| 23 | Delegation traces use yellow tint and server/local or remote network icons; live metadata determines remote status. |
| Delegation / benchmark request | Merged PR #564 rewires coordinator fallback and worker circuit breaking; ProfileBenchmarkScreen includes Delegation settings/metrics. BenchmarkStore recovers valid records without discarding damaged originals and reloads restored history. |

## Release checks

Run Kotlin lint, Android resource and regex preflights, the full JVM/Robolectric suite, Android lint, APK build and signed-release native/certificate/provenance checks. The release workflow publishes only after its checks succeed. Physical phone execution and visual acceptance remain separate from these host checks.
