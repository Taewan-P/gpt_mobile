# Material 3 Feedback Refinement Design

## Goal

Apply the approved follow-up feedback to the open Material 3 Expressive redesign without changing routes, data models, provider behavior, or the rest of the approved UI.

## Approved decisions

### Get Started

- Restore the original Start screen composition from `origin/main`: the 400dp hero, 50dp top inset, original welcome spacing, and original content scale.
- Preserve the current shared `PrimaryLongButton` contract. Recreate the original 20dp button inset only at the Start screen call site so redesigned setup screens do not receive unintended double padding.

### Splash

- Light mode uses the app's signature green `#00A67D` for the full splash window.
- Dark mode uses the approved deep brand green `#003828` so launch remains branded without a bright flash.
- Keep the existing centered splash icon and `UiModeManager` synchronization. The saved Light, Dark, or System selection must continue selecting the matching Android resource qualifier before app content appears.

### Home search

- Search mode uses the established auto-mirrored Back arrow on the leading side to exit search.
- The trailing X remains only when the query is non-empty and clears the query.
- Accessible names remain distinct: `Go back` and `Clear`.

### Chat details

- Details remains collapsed by default and visually low emphasis.
- Put the Details disclosure before the final answer.
- When expanded, render recorded thinking and tool items after the disclosure and before the answer. Preserve the recorded order among those process items, but do not repeat timeline text fragments inside Details.
- Render the final answer and attachments exactly once in a stable region below the process details.
- Keep message actions and inline Retry after the final answer.
- Legacy records keep the existing order-unavailable notice; no chronology is invented.
- Keep the existing expansion and icon motion specs and the existing progress ownership rules.

## Verification

- Add one Home Compose test that distinguishes Back from Clear.
- Update the existing Chat Compose regression test so it fails unless Details, thinking, tools, and the single answer appear in that visual order.
- Run unit tests, lint, assembly, and the focused connected tests.
- Capture updated light/dark Start and splash states, Home search, and collapsed/expanded Chat detail states on the existing emulator.
- Replace the affected screenshots in the PR's horizontal tables and point the PR description at the final screenshot commit.
