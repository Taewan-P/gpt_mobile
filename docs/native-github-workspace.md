# Native GitHub workspace

Open **Settings → Tool connections**, add or edit a **GitHub API** connection, then expand its card and select **Open GitHub workspace**. The workspace uses GitHub's REST API directly and does not require an MCP server.

## Account and repository access

The existing connection editor stores the token in `SecretVault`. Use a fine-grained GitHub token restricted to the repositories you want. Browsing needs Contents read access; commits need Contents write access; draft pull requests need Pull requests write access. Editing workflow files also requires the appropriate GitHub workflow permission. Repository permissions and the connection's read-only policy disable editing when appropriate.

This implementation reuses token authentication. It does not implement GitHub App installation or browser OAuth sign-in; those need a registered GitHub application and its deployment configuration.

## Workflow

1. Choose an accessible repository and branch. Repository and branch lists support loading additional pages.
2. Browse folders and read text files from a fixed commit snapshot. Preview limits are visible and incomplete files cannot be staged from their preview.
3. Use **Changes** to create a working branch. The default branch cannot receive workspace commits.
4. Edit files, stage changes, and review their complete before/after text in **Changes**. Review is a text comparison, not a syntax-aware diff editor.
5. Commit up to 20 text files together in one Git commit. The branch head must still match the snapshot used to prepare the edits. The API preserves the base tree and executable file modes and never force-updates the branch.
6. Create a draft pull request against the repository's default branch. Browse existing PRs and inspect changed-file patches and check results.

**Use repository in chats** saves a default repository and branch for this connection across conversations. AI calls that omit repository parameters receive this selection. Explicit repository parameters override it. Clear the selection with **Clear chat context**. This is a connection default, not per-conversation isolation.

## AI integration

The existing GitHub tool additionally exposes account discovery, repository/branch browsing, line-range code reading with source metadata, branch-head inspection, PR changed files, check runs, ref comparison, and atomic `commit_files`. These work through the app's built-in tool routing, including local and remote profiles where GitHub is enabled. New read actions are classified as reads by the approval policy; commits remain writes and use the existing approval flow.

Prefer `read_code` to repeated full-file reads. Results report the blob SHA, reference, source link, line range, total lines and whether more content remains. `commit_files` requires an explicit branch and the exact `expected_head_sha` obtained before preparing changes. Draft PRs are the default for `create_pull_request`.

## Limits and validation

- Text files: 256 KB each, 1 MB per staged change set; no binary files, deletes, symlink replacements, or submodule edits.
- Editor: up to 2,000 complete lines; larger files are preview-only. AI calls can request successive ranges.
- GitHub Contents API: directories are limited to 1,000 entries; the workspace displays a limit notice.
- PR inspection: previews one page of 30 changed files; additional files and unavailable patches must be inspected on GitHub.
- Drafts stay in memory and are discarded when the workspace closes. Closing with editor or staged changes requests confirmation.
- This workspace does not run repository builds on the phone or implement PR merging, release publishing, or a full Codex execution environment.

Focused tests cover atomic multi-file commits, executable modes, stale-head rejection, default-branch protection, authentication, traversal rejection, selected-context line reading and permission errors. Android regex, resources, Room schema and Kotlin formatting checks run independently. Full Android compilation and unit tests require the project's Gradle/JDK/Android SDK environment.
