# GPT Mobile AI 0.9.20.1

## Memory and chat fixes
- Fixes an Android regex incompatibility that prevented memory learning from initializing, including explicit save and remember requests.
- Keeps chat replies working if optional memory initialization fails, with a visible notice when memory is unavailable.
- Preserves cancellation when stopping a chat request instead of reporting it as a chat error.

## Regression coverage
- Adds tests for explicit memory requests, repeated memory initialization failures, and chat cancellation.
- Checks literal Kotlin regex patterns against ICU during pull request validation to catch Android compatibility problems before release.

Includes the local AI, search, and Android runtime improvements from 0.9.20.0.
