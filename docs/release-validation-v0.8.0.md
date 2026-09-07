# GPT Mobile 0.8.0 validation

This file records fixture, emulator, and opt-in live-smoke evidence without credentials. It was finalized before the stacked PRs were submitted for review.

## Local gates

| Gate | Result |
| --- | --- |
| `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:lintDebug :app:assembleDebug` | Passed on the release branch |
| `./gradlew :app:assembleRelease :app:bundleRelease` | Passed; release APK and signed AAB tasks completed |
| `./gradlew :app:connectedDebugAndroidTest` on API 31 | Passed: 16 tests on `Pixel_6_API_31` |
| `./gradlew :app:connectedDebugAndroidTest` on API 37 | Passed: 16 tests on `Pixel_7_API_37` |
| `ktlint "app/src/**/*.kt"` | Passed |

## Release journeys

| Journey | Evidence |
| --- | --- |
| Upgrade seeded schema 6→7 and continue the chat | Passed on API 31 and 37 with `MigrationTestHelper`. Schema 6 and early broken-v2 fixtures preserve chats, profiles, prompts, messages, attachments, selected models, and plaintext keys for verified one-way vault migration. New tool/run tables start empty. |
| No-tool multi-provider chat remains unchanged | Passed manually on API 37 with two unbound profiles and distinct deterministic OpenAI-compatible responses in the existing comparison tabs. |
| Parallel agents, traces, cancellation, and retry | Passed through provider/runner/trace fixtures plus API 37 parallel profile runs. Partial output survived cancellation, the run became `CANCELED`, and retry displayed the tool side-effect warning. |
| Firecrawl, Perplexity, Exa, and hardened URL reading | Passed deterministic adapter/security fixtures, including normalized output, malformed responses, redirect limits, private/metadata destinations, DNS lookup failure, and output/download bounds. |
| Public, bearer, and OAuth MCP | Passed deterministic HTTP/SSE fixtures for initialization, session reuse, pagination, bearer auth, PKCE/state validation, registration, refresh/retry, namespacing, and text/JSON/resource-link results. The connection UI was inspected on API 37. |
| Foreground notification, background reattachment, cancel, and timeout | Passed manually on API 37. The notification and cancel action remained visible in the shade while backgrounded, the UI reattached, and cancellation removed the service/notification while retaining partial output. Runner timeout fixtures cover the fixed 15-minute ceiling. |
| Force-stop/relaunch marks interrupted without replay | Passed manually on API 37: a running fixture was force-stopped, relaunched as `INTERRUPTED`, retained partial text, and did not restart its service, notification, or tool work. |
| Delete, duplicate, export, connection rotation, and vault cleanup | Export opened the Android share sheet; duplication created a copy; deleting the copy retained the original. Room and vault fixtures verify regenerated run/event IDs, ownership cascades, historical snapshots, connection-secret rotation, and deletion cleanup. |
| Android 17 local-network permission | Passed manually on API 37. Denial preserved the unsent prompt; approval allowed the LAN request; failed local connection produced a persisted failed run with retry affordance. |

## Optional live smoke tests

Live credentials are never committed or printed. Missing credentials do not override deterministic fixture results.

| Integration | Exercised |
| --- | --- |
| OpenAI | Attempted; the locally available key was rejected with HTTP 401 `invalid_api_key` |
| Anthropic | Not exercised; no local credential was set |
| Gemini | Not exercised; no local credential was set |
| OpenAI-compatible/Groq | Deterministic local fixture exercised; no live credential was set |
| Firecrawl | Not exercised live; no local credential was set |
| Perplexity | Not exercised live; no local credential was set |
| Exa | Not exercised live; no local credential was set |
| MCP Streamable HTTP | Deterministic local server exercised; no live endpoint was configured |
