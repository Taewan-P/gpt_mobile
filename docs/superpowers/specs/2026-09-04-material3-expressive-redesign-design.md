# GPT Mobile Material 3 Expressive redesign

Date: 2026-09-04

Status: Design approved in chat; awaiting review of this written specification

## Summary

GPT Mobile will adopt a calm Material 3 Expressive language across the app, beginning with Settings and its child pages. The redesign strengthens hierarchy, grouping, touch feedback, empty and error states, and motion while keeping chat content quiet and primary.

The work reuses the current Compose, Material 3, navigation, ViewModel, repository, and route structure. It does not add a design-system module, a new UI dependency, or speculative navigation.

## Goals

- Give Settings and every child page a clear, consistent information hierarchy.
- Apply the same typography, shape, surface, spacing, action, and motion language to onboarding, Home, Chat, and migration.
- Keep completed chat answers visually dominant.
- Keep thinking and tool execution available in chronological order behind an explicit details disclosure.
- Move completed-message actions behind one accessible per-message action entry point.
- Make loading, empty, missing, error, and destructive states understandable and recoverable.
- Make the platform splash screen match the selected light, dark, or system theme mode.
- Preserve current features, data flow, provider behavior, progressive setup, and chat history semantics.
- Verify the result screen by screen in light and dark modes and with accessibility settings.

## Non-goals

- No route, database, repository, or provider-protocol redesign.
- No tablet two-pane navigation or new navigation destinations.
- No new font, illustration, animation, or design-system dependency.
- No motion added solely for decoration.
- No dynamic-wallpaper color on the platform splash. The pre-Compose splash will match light or dark mode; the existing Compose theme may continue using dynamic color after launch.
- No reordering or reconstruction of legacy chat timelines whose original order is unavailable.

## Evidence and constraints

- The app uses Material 3 `1.4.0`, Compose BOM `2026.06.00`, Navigation Compose `2.9.8`, and a single `GPTMobileTheme` composition root.
- `GPTMobileTheme` already owns static light, static dark, and dynamic color selection, but typography is the Material default and no project shape system is supplied. Medium/high-contrast palettes exist in source but are not selected by the theme.
- Settings is currently a flat sequence of rows. Provider details can become a dense list without group hierarchy, and a missing provider can render a blank page.
- Tool setup already models the required progressive choice between Web Search and MCP Server. That flow remains the source of truth.
- Assistant message controls currently occupy permanent space after every completed answer. Thinking and tool blocks are already expandable, and timeline data already records chronological text, thinking, and tool items.
- Both `values/themes.xml` and `values-night/themes.xml` currently use the same green splash background. The saved `ThemeMode` is loaded later from DataStore and only affects Compose.
- The minimum SDK is 31, so Android's per-application night qualifier is available without a compatibility layer.

## Design principles

### Calm expressive

Expression comes from clear type hierarchy, confident rounded shapes, tonal surfaces, and purposeful spring motion. It does not come from filling every region with a card or animating every interaction.

### Content before controls

Primary content and the next useful action appear first. Secondary controls remain discoverable through a visible disclosure or overflow action, never through long press alone.

### Progressive disclosure

Advanced provider controls, tool configuration, thinking, tool traces, and message actions appear when requested. Current values and state remain legible before expansion.

### One state, one signal

A running operation uses one progress treatment. Terminal failure, interruption, and cancellation remain visible with a recovery path. Color and motion never carry state alone.

### Platform behavior first

Material components, Android resource qualifiers, system animator scale, native dialogs and sheets, and existing Compose primitives are preferred over custom infrastructure.

## Foundation

### Color and surfaces

- Preserve the currently active light, dark, and dynamic color schemes. Leave the unused medium/high-contrast palette definitions unchanged and outside this redesign.
- Use `background` for the page, `surfaceContainerLow` for grouped preferences and quiet cards, `surfaceContainerHigh` for selected or elevated detail, and `primaryContainer` for the single emphasized action or state.
- Use semantic error colors only for failures and destructive actions.
- Do not use accent color on static copy that could be mistaken for a control.
- Keep at most one filled screen-level primary action in a view; repeated per-item actions use tonal or text emphasis.

### Typography

- Continue using the platform font; add no font dependency.
- Keep Material 3's default `Typography()` metrics as the single type scale and pass it through `GPTMobileTheme`; do not fork a near-identical custom scale.
- Large screen titles use headline roles. Section titles use title roles. Preference names use body-large or title-small roles. Supporting copy uses body-medium. Compact status and metadata use label roles.
- Important values wrap or remain reachable in an editor; they are not irreversibly truncated.

### Shape and spacing

- Add restrained 12dp, 20dp, and 28dp rounded shape tiers: 12dp for compact controls and fields, 20dp for grouped rows and cards, and 28dp for prominent containers and the composer. Pills and circular controls retain full rounding where Material specifies it.
- Use the existing Material component shape when it already communicates the correct role.
- Use an 8dp internal rhythm, 16dp phone content margins, and at least 24dp between semantic groups.
- On wider windows, center settings, setup, and form content at a maximum width of 720dp instead of stretching rows edge to edge.

### Shared components

- Improve the existing `SettingItem`, `DestinationCard`, `RadioItem`, and `PrimaryLongButton` rather than replacing them.
- Add one reusable settings-section container because it is shared by Settings, provider details, local models, tool connections, and About.
- Add one reusable empty/error state because Home and multiple settings children require the same orientation and recovery pattern.
- Keep screen-specific app bars and complex rows local. A wrapper is added only when the same structure has at least three real consumers.

## Motion

- Mirror only the installed Material 3 expressive spring values the app uses: default spatial `dampingRatio = 0.8`, `stiffness = 380`; fast spatial `0.6`, `800`; and fast effects `1.0`, `3800`. Do not call the library's internal `MotionScheme` or `MaterialExpressiveTheme` APIs.
- Forward navigation uses a short leading-to-trailing slide with fade; back navigation reverses it. Navigation remains fast and interruptible.
- Expandable sections use a spatial spring for size and a faster effects spring for opacity and icon rotation.
- Bottom sheets and Material dialogs retain platform Material motion.
- Existing Material ripple handles ordinary press feedback. Animate only meaningful selection and state-container changes with the fast effects spec; high-frequency typing, search, scrolling, copying, and toolbar actions receive no ornamental animation.
- List placement animates only for an actual insertion, removal, or reorder.
- Initial screen composition does not stagger ordinary lists.
- Compose and Material animations obey Android's animator-duration scale. At zero scale, custom route, expansion, rotation, and state-container animations snap to their end state; the ordinary Material ripple remains as non-spatial press feedback.

## Splash screen and theme mode

### Root cause

The launch theme is selected by Android before Compose or DataStore loads. Both current day and night themes reference `@color/ic_gpt_mobile_background`, so the full splash stays green regardless of configuration. The later Compose theme cannot repaint the platform starting window.

### Design

- Introduce a semantic splash-window background using the existing static app backgrounds: `#F5FBF5` in `values` and `#0F1512` in `values-night`.
- Keep `ic_gpt_mobile_background` as the green icon background; do not use it for the full window.
- Synchronize the loaded and newly selected `ThemeMode` with `UiModeManager.setApplicationNightMode`:
  - `LIGHT` maps to `MODE_NIGHT_NO`.
  - `DARK` maps to `MODE_NIGHT_YES`.
  - `SYSTEM` maps to `MODE_NIGHT_AUTO`. AOSP's API 31 and current service implementation translate this value to an undefined package night qualifier, so the package inherits the system configuration; explicit-to-System transitions are device-tested because the public constant description is broader.
- `MainActivity` owns the `UiModeManager` side effect and extends its existing splash barrier. It uses the same `ThemeViewModel` instance as `ThemeSettingProvider` and releases the splash only after startup routing is ready, the persisted theme has loaded, and the mode call has returned.
- The application-night-mode call is idempotent. If it recreates the Activity, the new instance repeats the same loaded-mode check and releases only after the now-matching configuration is applied.
- Theme-mode persistence completes before a configuration-changing application-night-mode update is requested.
- On the first launch after upgrading from a version that never synchronized the platform qualifier, hold the splash through synchronization and verify the resulting configuration change; subsequent cold starts must start in the selected mode.
- The splash icon and its green background remain unchanged; only the surrounding window follows light or dark mode.

## Settings information architecture

### Settings index

The screen remains one scrollable destination with an expressive large app bar and four groups:

1. Appearance
   - Theme shows the current dynamic-color and light/dark selection in its summary.
   - Selection opens a modal sheet with the existing radio choices.
2. Providers
   - Add platform is the single emphasized creation action.
   - Local models remains a destination row in this group.
   - Configured providers follow as grouped rows with provider type and explicit Enabled or Disabled text.
   - With no providers, concise orientation copy appears beside the existing add action.
3. Tools
   - Tool connections opens the existing connection list and setup flow.
4. App
   - About opens app identity, legal, link, and support information.

Rows merge their title, summary, state, and click action into one accessible semantic node. Chevron and decorative leading icons are not separately announced.

### Add platform

- Preserve the progressive API-type then connection-details flow.
- Present choices as selectable tonal cards with clear selected, supported, and unavailable states.
- Keep one stable primary action, with validation next to the field that needs correction.
- Preserve IME and safe-area handling.

### Provider details

- Put Enable platform in a prominent top container.
- Group remaining controls as Connection, Model and generation, Instructions, Runtime, and Tools, showing only groups applicable to that provider.
- Preserve current applicability: URL, API key, timeout, and extended thinking stay remote-only; top-k, max tokens, and accelerator stay local-only; Gemini safety stays Google-only; existing model, temperature, top-p, system prompt, and tool enablement rules remain unchanged.
- Keep advanced generation controls collapsed by default with a summary of their current values.
- Keep field editing in the existing focused dialogs.
- Never reveal any API-key character in the row summary; show only Key set or Key not set.
- Keep Delete in the top-bar overflow with its current explicit confirmation.
- Render a progress state while the provider is loading and a not-found state with Back and Retry instead of a blank page.

### Tool connections

- Preserve the initial setup choice as exactly Web Search followed by MCP Server, in that order.
- Web Search continues through provider selection and details. MCP Server continues through details and authentication.
- Empty state explains the page and provides Add connection as its one action.
- Existing connections use grouped rows with edit, connect or OAuth, and delete actions.
- Busy state appears only on the affected connection. Do not add a second global spinner.
- Errors state what failed and provide Retry, Edit, or Dismiss according to what is recoverable.

### Add and edit tool connection

- Use the existing editor destination and `ToolConnectionSetupFlow` for both modes.
- Keep the first add step as exactly Web Search followed by MCP Server. Web Search then shows provider choice and connection details; MCP Server shows connection details and authentication.
- Show the current step in the title and keep one full-width Next or Save action at the bottom, above the IME and safe-area inset.
- Group Name and alias under Identity, endpoint and cleartext permission under Connection, and credential, OAuth client ID, and credential-clearing controls under Authentication. Render only fields applicable to the selected path and auth type.
- Put validation beside the affected field. During save, disable the primary action and show one inline busy state; on failure keep entered values and show a retryable inline error.
- Editing opens directly on the existing connection's applicable details. A missing connection shows the shared not-found state with Back and Retry.
- Preserve the existing local-network permission requirement and request timing; explain why access is needed immediately before the permission-triggering connect action.

### Local models

- Show the Hugging Face account state first, storage usage second, and available or downloaded model cards third.
- Model cards expose size, memory requirement, capability chips, state, and one context-appropriate action.
- Loading uses one centered progress state. Empty and unavailable states explain the next step.
- Cancel and delete remain distinct; deletion retains confirmation.

### About and licenses

- Group app identity and version, Legal, Project links, and Support.
- License remains its current child destination.
- External destinations have explicit labels and accessible roles.

### Required Settings states

| Destination | States that must be designed and verified |
| --- | --- |
| Settings | no providers, configured providers, light/dark/dynamic summaries |
| Add platform | type choice, details, invalid field, saving, save failure |
| Provider details | loading, content, unsupported groups omitted, not found, delete confirmation |
| Local models | loading, signed out, catalog empty, available, downloading, downloaded, failure |
| Tool connections | empty, configured, row-level OAuth busy, failure |
| Add/edit tool connection | each progressive step, invalid field, saving, failure, edit not found |
| About and Licenses | content, external destination, back navigation |

## Chat

### Default message presentation

- User and assistant content remain the strongest visual elements.
- Completed assistant messages no longer show the permanent copy, select, edit, retry, and revision toolbar.
- Each completed user or assistant message exposes one low-emphasis, 48dp Message actions button that opens a Material modal bottom sheet. Long press may remain as a shortcut but is never the only path.
- The user-message sheet contains Copy and Edit. The assistant-message sheet contains only currently supported actions among Copy, Select text, Edit, Retry, and revision navigation. Existing enablement rules remain unchanged.
- The retry warning appears beside Retry in the sheet instead of occupying every completed message.
- Actions remain hidden while that response is streaming.

### Thinking and tools

- When a response has thinking or tool events, show one compact Details disclosure after the primary answer.
- Collapsed is the default for completed messages.
- Expanding Details replaces the quiet content view with the existing chronological timeline view, preserving text, thinking, and tool positions rather than grouping all tools elsewhere.
- Thinking and each tool trace remain independently expandable inside the timeline for their stored details.
- Long argument, result, or error previews keep the existing bounded inline rendering and expose View full, reusing the existing scrollable selectable-text sheet rather than introducing another detail destination.
- Active tool work retains one compact live status so the user is not left without progress.
- Legacy content retains the order-unavailable notice and never invents chronology.

### Message-state behavior

| State | Quiet view | Details | Actions |
| --- | --- | --- | --- |
| Streaming before answer text | one loading treatment | collapsed by default; available once thinking or tool data exists | hidden |
| Streaming answer | answer text and one streaming affordance | collapsed by default; may be opened without stopping generation | hidden |
| Active tool work | answer-so-far plus one compact live tool status | collapsed by default; opening shows the live chronological timeline | hidden |
| Completed without details | answer | no disclosure | one Message actions button |
| Completed with details | answer plus collapsed Details | expansion replaces the quiet answer region with the chronological timeline, so answer text is not duplicated | one Message actions button |
| Failed, canceled, or interrupted | terminal state and visible recovery where recoverable | available when recorded detail exists | only valid completed-state actions; Retry remains visible outside the sheet for failure |
| Legacy order unavailable | answer and order notice | expansion shows the stored order-unavailable notice, never reconstructed chronology | same valid actions as the selected revision |

Selecting another revision switches answer, status, and details together. Disclosure state is keyed to the selected revision. A timeline item whose referenced tool event is unavailable renders a quiet Details unavailable notice instead of disappearing or crashing.

### Status and recovery

- Queued and running status chips remain omitted when existing loading UI already communicates progress.
- Canceled, interrupted, and failed terminal states remain visible.
- A failed response shows a visible recovery action; recovery is not hidden only in the message sheet.
- For a failed response, the visible Retry replaces Retry in the sheet so the same recovery action is not duplicated.
- Provider selection for multi-provider chats remains near the assistant identity but uses consistent tonal selection styling.

### Composer

- Treat the composer as one floating surface with consistent container color, 28dp outer shape, safe-area padding, attachment affordance, and one Send or Stop action.
- Keep attachment previews and failure feedback visible without adding a second toolbar.

## Remaining screens

### Start and setup

- Start uses one calm hero, concise copy, and one Get started action.
- Setup screens share the same title placement, content width, selectable-card language, and stable bottom action.
- The wizard shows compact step progress and preserves its current conditional fields and local-model path.
- Completion summarizes configured providers and ends with one Start chatting action.

### Home

- Keep the expressive large Chats title, search mode, selection mode, and New chat action.
- Chat rows use one quiet tonal surface, consistent metadata, and clear pressed or selected state.
- When there are no chats, show orientation copy and one Start a chat action; do not duplicate it with a second FAB.
- Search-empty and load-error states explain how to recover.

### Migration

- Explain the migration once, show compact per-step status, and provide one primary completion action.
- Errors remain inline with Retry. Success proceeds through the existing route behavior.
- Do not combine a spinner, progress bar, and status chip for the same operation.

## Accessibility

- Interactive targets are at least 48dp where possible and never below the platform minimum.
- Icon-only actions have localized names; decorative icons have no content description.
- Clickable rows merge descendants and expose their role and state once.
- Switch rows expose one switch semantic owner rather than a clickable row plus separately announced nested switch.
- Headings identify major sections for accessibility services.
- State uses text or an icon in addition to color.
- Text survives large font scale without fixed-height clipping; critical values remain reachable.
- Layout and motion remain usable with RTL, large text, TalkBack, keyboard focus, and animations disabled.

## State and data boundaries

- Existing ViewModels remain responsible for data, validation, and business actions.
- Extend those existing ViewModels only where the current nullable or list state cannot distinguish loading, empty, missing, and failure. Do not add a parallel presentation data layer or new ViewModel.
- `PlatformSettingViewModel` must distinguish loading, loaded, not found, and failure; `HomeViewModel` must expose list-load failure and Retry; `ToolConnectionsViewModel` must identify the busy connection UID instead of representing OAuth work as an unscoped global busy flag.
- Screen-local disclosure, sheet, and expanded-section state uses `rememberSaveable` keyed to the relevant message or entity identity.
- Existing callbacks and enablement predicates are passed into redesigned presentation components unchanged.
- No provider, model, tool, chat, or migration data is duplicated for presentation.
- Theme-mode synchronization is the only new platform side effect; DataStore remains the source of truth for the user's selected mode.

## Delivery sequence

1. Theme foundation and splash-mode synchronization.
2. Shared preference and state components.
3. Settings index and every Settings child page.
4. Chat details and message-action presentation.
5. Start, setup, Home, composer, and migration alignment.
6. Screen-by-screen visual, accessibility, motion, and build verification.

Each stage remains buildable and retains all current behavior.

## Canonical route and variant coverage

- Root routes: Get Started, Migration, Chat list, and Chat room.
- Setup routes: Platform list, Platform type, Platform wizard, Local models opened from setup, and Setup complete.
- Settings routes: Settings index, Add platform, Platform details, Local models opened from Settings or Chat, Tool connections, Add tool connection, Edit tool connection, About, and Licenses.
- Provider detail and setup rendering covers all current `ClientType` values: OpenAI, Anthropic, Google, Groq, OpenRouter, Ollama, Custom, and LiteRT LM. Equivalent remote-provider layouts may share one evidence case, but Google-specific, OpenAI reasoning, Custom endpoint, Ollama, and LiteRT-local branches each require their own state check.
- Tool setup covers Web Search provider selection plus details, and MCP Server details plus each supported authentication state.

## Verification and acceptance criteria

### Automated checks

- Unit-test light, dark, and system-to-application-night-mode mapping.
- Unit-test any new state transformation with branching behavior.
- Run `./gradlew :app:testDebugUnitTest`.
- Run `./gradlew :app:compileDebugKotlin`.
- Run `./gradlew :app:lintDebug`.
- Run `./gradlew :app:assembleDebug`.
- Run the repository's ktlint 1.3.1 check against changed Kotlin files.
- Add focused Compose/instrumented checks only for stable interaction contracts that unit tests cannot cover, such as opening Message actions and toggling Details without duplicating answer content. Keep device-specific splash, TalkBack, and visual checks in the named manual matrix.

### Screen-by-screen checks

- Inspect Settings, theme sheet, Add platform, each provider detail variant, Local models, Tool connections, both tool setup paths, About, and Licenses.
- Inspect Start, every setup step, setup completion, Home empty and populated states, Chat streaming and completed states, message action sheet, expanded details, revisions, terminal failures, attachment composer, and Migration.
- Check light, dark, and dynamic themes; small phone width; large font scale; RTL; and animations disabled.
- Verify every empty and error state has a clear next action.
- Verify exactly one progress treatment represents each running operation.
- Capture named screenshots and semantics dumps by route and state on the existing 1080x2400 phone target. Repeat layout checks at a 720dp-or-wider window, 1.3x font scale, RTL, and animator scale zero; retain only evidence needed to demonstrate each distinct behavior.
- Seed deterministic test data through the existing app/database paths. Before clean-start splash cases, clear app data when appropriate, select the requested theme through the UI, force-stop, then relaunch; use the System selection itself to clear an explicit application override before testing device light/dark changes.

### Splash checks

- Cold-start in System mode with the device in light and dark configurations.
- Cold-start in explicit Light while the device is dark.
- Cold-start in explicit Dark while the device is light.
- Cold-start once with an existing explicit mode after upgrading from a build that did not synchronize the platform qualifier.
- Switch explicit Light to System and explicit Dark to System, then verify both manual and scheduled system-theme changes are inherited.
- Force-stop before each launch so the platform starting window, rather than a warm Activity, is tested.
- Confirm the splash window matches the selected mode, the icon background stays green, and the first Compose frame does not flash the opposite mode. The one-time unsynchronized-upgrade case may begin with the system-qualified starting frame because no new app code has run yet; it must settle before app content and every subsequent cold start must be correct.

### Chat checks

- Confirm a completed answer shows no permanent action toolbar.
- Confirm Message actions exposes the same valid actions and enablement behavior as before.
- Confirm normal answers hide Retry warning until the action sheet is opened.
- Confirm failed answers keep an immediately visible recovery path.
- Confirm collapsed Details is quiet and expanded Details preserves recorded chronology.
- Confirm legacy records still state that order is unavailable.
- Confirm loading content does not show completed-message actions or duplicate status indicators.

## Completion definition

The redesign is complete only when the full build checks pass and every affected screen and required state above has current rendered evidence. Source-only inspection is not sufficient for an app-wide UI completion claim.
