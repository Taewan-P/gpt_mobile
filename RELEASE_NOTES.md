# GPT Mobile AI 0.9.22.0

## Delegation and benchmarks
- Rewire research delegation, preserve direct-tool fallback, and isolate helper output from the primary answer.
- Add a dedicated delegation benchmark section with saved settings and repeatable fixtures. Recover valid benchmark history when an entry is damaged.
- Show live helper text in green in debug mode, with yellow server traces and remote connection indicators.
- Save comparable local model benchmark scores and display them on model cards; add MTP marketplace filtering.

## Tools and providers
- Enable tools on free profiles with per-profile, per-MCP-tool tracking consent, a lock, and a slide-to-accept dialog. Grants persist and can be reset in Options.
- Redesign approval dialogs with MCP branding and provider-wide approval controls.
- Allow free profile renaming, preserve custom names, and use provider names by default.
- Improve collapsible Remote, Local and Free provider categories and icon controls.
- Connect Brave Search directly with the user's API key. Add a direct GitHub API connector for repository, pull-request and Actions workflow operations, with approval checks for authenticated actions.

## Conversations
- Keep table cells fully visible with horizontal scrolling and padding.
- Align message action bars, fade idle controls, and animate streamed words over 1.5 seconds.
- Always show a visible response or error at completion, including empty and reasoning-only completions.
- Use themed loading icons and progress rings, activity summaries, animated dots and moving text gradients. Refresh activity every five seconds; an idle, already-loaded LiteRT/QNN engine can phrase a short summary without interrupting generation.
- Retain the centered archive caret, transparent background outside the input bubble, and bold/filled unread conversation indicators.

## Backup and restore
- Open section selection when backup or restore is invoked; remember choices and provide Select all. Restore file selection comes first.
- Include themes, Hugging Face credentials and selected settings in encrypted backups.
- Passwordless backups use authenticated AES-256-GCM encryption and a separate recovery-key file. Keep that key private and separate from the backup; it is required after reinstall or on another device. No encryption can honestly be promised uncrackable.
- Retain compatibility with legacy backups and optional password encryption.

## Local runtime
- Retain the current published Qualcomm QNN 2.50.0 packages, matching rebuilt dispatch libraries, strict device qualification, crash quarantine and fallback protection. Validate packaged native hashes and alignment during release.
- Real NPU execution and visual/device acceptance still require supported hardware; host CI does not establish that a device used its NPU.
