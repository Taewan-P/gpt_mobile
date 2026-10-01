# GitHub integration v2

This branch upgrades GitHub from a generic remote tool into a compact native repository capability shared by chat agents and the GitHub workspace.

## Runtime architecture

```
AI / chat
   |
GitHubTool
   |
GitHubWorkspaceClient
   |-- compact REST reads
   |-- GraphQL repository overview
   |-- ETag conditional cache
   |-- decoded blob cache keyed by SHA
   |-- repository tree index
   |-- rate-limit state
   |-- safe multi-file Git commits
   `-- workflow/PR summaries
```

When a native GitHub API connection is configured, the anonymous built-in GitHub surface is not exposed beside it. If the GitHub Official MCP endpoint is also configured, that overlapping MCP surface is suppressed while the native connection is active. This prevents agents from repeating the same repository search, PR read, or branch lookup through multiple providers.

## Agent-first actions

Prefer the compact actions before lower-level REST-style actions:

- `repo_status` — GraphQL-first repository summary with open issue/PR counts, default-branch head and latest workflow.
- `repo_map` — paged source-oriented repository tree that excludes large binary assets.
- `find_symbol`, `find_references`, `find_tests` — scoped code discovery.
- `related_files` — lightweight path/test affinity ranking from the repository index.
- `read_code` — bounded line-range reads with blob SHA and source metadata.
- `pr_context` — PR metadata, changed files and checks in one compact result.
- `changed_since` — compact compare metadata and changed-file statistics.
- `rate_limit_status` — compact REST/search/GraphQL quota state.
- `plan_change` — deterministic safe workflow ordering for repository changes.

Existing actions remain accepted for compatibility.

## Native release management

Release operations are first-class GitHub actions rather than a model-generated fallback to local `gh` or the GitHub Web UI.

- `list_releases`, `get_release`, `get_release_by_tag` and `list_tags` provide compact release/tag reads.
- `create_tag`, `create_release` and `update_release` provide explicit write actions.
- `release_status` checks the requested tag, published/draft state, release assets, the detected release workflow and its latest run.
- `publish_release` is the preferred high-level action. In `auto` mode it detects an active workflow whose name/path indicates release publication, preferring signed-release workflows, and dispatches that workflow on the requested ref. If no release workflow applies, it falls back to the GitHub Releases API.
- `release_strategy=workflow` requires a release workflow; `release_strategy=direct` intentionally bypasses workflow discovery. `auto` falls back to direct publication only after workflow discovery succeeds and finds no applicable workflow; discovery/auth/rate-limit failures fail closed instead of bypassing the repository release pipeline.
- Existing releases are idempotent: published releases return `already_published`, while an existing draft can be promoted instead of creating a duplicate. Duplicate detection scans paginated release history before any write.
- When `app/build.gradle.kts` is present, workflow publication preflights `versionName` against the requested `v...` tag and refuses a mismatch before dispatch. This prevents a request for a future tag from accidentally running a workflow that would publish the repository's current older version.

The operation planner now emits `release_status -> publish_release -> release_verify` for release goals so the model has executable actions at every step. Release mutation actions remain separated from read actions for approval/policy handling.

## Caching and quota handling

GET responses with an ETag are cached in memory. Later calls send `If-None-Match`; a `304 Not Modified` reuses the parsed cached value. Decoded text file contents are cached by immutable blob SHA.

The client records GitHub rate-limit headers including resource, limit, remaining, used, reset and retry-after values. The agent can query a compact quota snapshot instead of waiting for a hard failure.

## API version

REST requests send:

```
X-GitHub-Api-Version: 2026-03-10
```

Keep the version constant covered by compatibility tests when GitHub announces a new REST version.

## GitHub App and webhooks

A production GitHub App is the preferred next authentication layer for organization deployment. It cannot be completed securely inside the Android application alone because the GitHub App private key must never ship on-device.

The backend contract should provide:

1. Browser installation/authorization for the GitHub App.
2. Exchange of the installation identity for a short-lived installation access token.
3. Token refresh without exposing the app private key to the device.
4. Webhook verification using the GitHub webhook secret.
5. Deduplication by `X-GitHub-Delivery`.
6. A compact event feed for push, pull_request, pull_request_review, issues, workflow_run and release events.

The Android client can then replace long-lived PAT use with a credential provider that supplies short-lived tokens to `GitHubWorkspaceClient`. Until that backend exists, the current encrypted token connection remains supported.

## Safety invariants

- Never force-update a branch.
- Never commit directly to the repository default branch through `commit_files`.
- Require `expected_head_sha` before an atomic change set.
- Re-read state after ambiguous write timeouts before retrying.
- Keep repository/code payloads bounded before they reach a model.
- Preserve the existing write-approval flow for mutating GitHub actions.
