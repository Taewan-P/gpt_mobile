# Material 3 Feedback Refinement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the approved Start, splash, Home search, and Chat detail refinements to PR #327 and refresh its rendered evidence.

**Architecture:** Keep the existing single-activity Compose architecture and edit the current presentation seams in place. Restore the Start layout locally, reuse Android day/night resources for splash branding, reuse the app's established Back icon, and split the assistant's process presentation from its stable final-answer region without changing stored timelines.

**Tech Stack:** Kotlin 2.3.21, Jetpack Compose, Material 3, Android core-splashscreen, Compose UI tests, Gradle, adb/Android CLI.

**Spec:** `docs/superpowers/specs/2026-09-06-material3-feedback-refinement-design.md`

## Global Constraints

- Work only in `.worktrees/material3-expressive-implementation` on `codex/material3-expressive-implementation`.
- Preserve unrelated `.serena/project.yml`; never stage or revert it.
- Add no dependency, route, database, repository, provider, or stored-timeline change.
- Keep 48dp interactive targets and localized content descriptions.
- Details is collapsed by default; answer and attachments render exactly once.
- Known process items retain their recorded relative order; legacy order remains explicitly unavailable.
- Use existing Material motion specs and keep one progress signal per operation.
- Implementation and review subagents use `xai/grok-4.6` with reasoning effort `xhigh`.

---

### Task 1: Restore Start and retone the splash

**Files:**
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/startscreen/StartScreen.kt`
- Modify: `app/src/main/res/values/splash_window_background.xml`
- Modify: `app/src/main/res/values-night/splash_window_background.xml`

**Interfaces:**
- Consumes: existing `PrimaryLongButton`, `GptMobileStartScreen`, starting theme, and platform night-mode synchronization.
- Produces: the original Start layout plus light `#00A67D` and dark `#003828` splash-window resources.

- [ ] **Step 1: Confirm the visual-only test boundary**

Do not add a source-text or fixed-geometry test. Such a test would detect intentional redesign rather than broken behavior. Use the existing visible labels and tap behavior as the behavior contract, and verify this exact restoration through rendered screenshots.

- [ ] **Step 2: Restore the original Start composition**

Restore `StartScreen`, `StartScreenLogo`, and `WelcomeText` from `origin/main`. Because shared button padding was intentionally moved to callers in this branch, keep that shared component unchanged and call:

```kotlin
PrimaryLongButton(
    modifier = Modifier.padding(20.dp),
    onClick = onStartClick,
    text = stringResource(R.string.get_started)
)
```

- [ ] **Step 3: Apply the approved splash tones**

Use the existing semantic resource in both qualifiers:

```xml
<!-- res/values/splash_window_background.xml -->
<resources>
    <color name="splash_window_background">@color/ic_gpt_mobile_background</color>
</resources>
```

```xml
<!-- res/values-night/splash_window_background.xml -->
<resources>
    <color name="splash_window_background">#003828</color>
</resources>
```

Do not change the icon, `themes.xml`, `MainActivity`, or theme-mode synchronization.

- [ ] **Step 4: Compile the task**

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit only owned files**

```bash
git add app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/startscreen/StartScreen.kt app/src/main/res/values/splash_window_background.xml app/src/main/res/values-night/splash_window_background.xml
git commit -m "fix: refine start and splash branding"
```

### Task 2: Distinguish Home search exit from query clearing

**Files:**
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/home/HomeScreen.kt`
- Create: `app/src/androidTest/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/home/HomeScreenPresentationInstrumentedTest.kt`

**Interfaces:**
- Consumes: `HomeTopAppBar`, `R.string.go_back`, `R.string.clear`, and the existing `navigationOnClick`/`onSearchQueryChanged` callbacks.
- Produces: a leading Back action and conditional trailing Clear action in search mode.

- [ ] **Step 1: Write the failing Compose test**

Create a test using `createComposeRule` and `GPTMobileTheme`. Render `HomeTopAppBar` with `isSearchMode = true` and `searchQuery = "Tokyo"`, then assert:

```kotlin
composeRule.onNodeWithContentDescription("Go back").assertExists()
composeRule.onNodeWithContentDescription("Clear").assertExists()
composeRule.onNodeWithContentDescription("Close").assertDoesNotExist()
```

The remaining parameters use inert callbacks, `isSelectionMode = false`, zero selected chats, and `TopAppBarDefaults.pinnedScrollBehavior()`.

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.presentation.ui.home.HomeScreenPresentationInstrumentedTest
```

Expected: FAIL because search exit is currently announced and drawn as Close.

- [ ] **Step 3: Make the minimal implementation**

Reuse the icon already used throughout the app:

```kotlin
import androidx.compose.material.icons.automirrored.filled.ArrowBack

Icon(
    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
    contentDescription = stringResource(R.string.go_back)
)
```

Change only the `isSearchMode` navigation icon. Keep selection mode Close and the text field's conditional trailing Clear X unchanged.

- [ ] **Step 4: Run the focused test and compile**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.presentation.ui.home.HomeScreenPresentationInstrumentedTest
./gradlew :app:compileDebugKotlin
```

Expected: both commands pass.

- [ ] **Step 5: Commit only owned files**

```bash
git add app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/home/HomeScreen.kt app/src/androidTest/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/home/HomeScreenPresentationInstrumentedTest.kt
git commit -m "fix: clarify Home search actions"
```

### Task 3: Put Chat process details before the final answer

**Files:**
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/chat/ChatBubble.kt`
- Modify: `app/src/androidTest/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/chat/ChatMessagePresentationInstrumentedTest.kt`

**Interfaces:**
- Consumes: `AssistantTimelineItem`, `ThinkingBlock`, `ToolTraceBlock`, `QuietAssistantContent`, `DetailsButton`, and existing message-action callbacks.
- Produces: a stable Details/process/answer/actions visual order with one answer rendering.

- [ ] **Step 1: Change the existing test and verify RED**

Rename `detailsSwapQuietAnswerForChronologicalNestedContent` to `expandedDetailsKeepProcessesAboveSingleAnswer`. Keep its deliberately interleaved fixture `THINKING, TEXT, TOOL`. Before expansion, assert one Answer. After tapping Details, assert one Answer and these top positions:

```kotlin
val detailsTop = composeRule.onNodeWithText("Details").fetchSemanticsNode().boundsInRoot.top
val thinkingTop = composeRule.onNodeWithText("View thinking process").fetchSemanticsNode().boundsInRoot.top
val toolTop = composeRule.onNodeWithContentDescription("Expand tool trace").fetchSemanticsNode().boundsInRoot.top
val answerTop = composeRule.onNodeWithText("Answer").fetchSemanticsNode().boundsInRoot.top

assertTrue(detailsTop < thinkingTop)
assertTrue(thinkingTop < toolTop)
assertTrue(toolTop < answerTop)
```

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.presentation.ui.chat.ChatMessagePresentationInstrumentedTest
```

Expected: FAIL because the current Details row is after the answer and the interleaved tool appears after the answer.

- [ ] **Step 2: Reorder the assistant presentation**

In `OpponentChatBubble`:

1. Place a row containing only `DetailsButton` before the animated content when details exist.
2. Keep `AnimatedContent`, but make its expanded branch render process content followed by `QuietAssistantContent`; its collapsed branch renders only `QuietAssistantContent`.
3. Keep a separate trailing row for `MessageActionsButton` after the animated content.
4. Keep inline Retry after message actions.

Rename the private timeline renderer to `AssistantProcessContent` and make `TEXT`, `NOTICE`, and `LEGACY_ORDER` branches no-ops. It still iterates the stored timeline, so thinking and tool items retain their recorded relative order.

Split the private legacy renderer into process-only content: order-unavailable notice, thinking, and tool trace. The shared `QuietAssistantContent` owns final text and attachments for both known and legacy timelines.

For streaming, keep the final-answer indicator on the answer once answer text exists. When expanded before answer text exists, let the process block own that indicator and suppress a duplicate dot in the empty answer region.

- [ ] **Step 3: Run the focused Chat tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*ToolTraceBlockTest' --tests '*AssistantTimelineTest' --tests '*AgentRunStatusBlockTest'
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.presentation.ui.chat.ChatMessagePresentationInstrumentedTest
./gradlew :app:compileDebugKotlin
```

Expected: all commands pass; the Compose test still verifies nested thinking/tool expansion and missing tool details.

- [ ] **Step 4: Commit only owned files**

```bash
git add app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/chat/ChatBubble.kt app/src/androidTest/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/chat/ChatMessagePresentationInstrumentedTest.kt
git commit -m "fix: place Chat details before answers"
```

### Task 4: Verify and refresh PR evidence

**Files:**
- Modify: `docs/screenshots/material3-expressive/01-start.png`
- Modify: `docs/screenshots/material3-expressive/13-start-dark.png`
- Modify: `docs/screenshots/material3-expressive/28-home-search.png`
- Modify: `docs/screenshots/material3-expressive/29-chat-quiet.png`
- Modify: `docs/screenshots/material3-expressive/31-chat-details.png`
- Modify: `docs/screenshots/material3-expressive/32-chat-tool-trace.png`
- Modify: `docs/screenshots/material3-expressive/34-splash-dark.png`
- Modify: `docs/screenshots/material3-expressive/35-splash-light.png`

**Interfaces:**
- Consumes: final debug APK, existing emulator/demo-data workflow, and PR #327's current screenshot tables.
- Produces: current rendered evidence and a PR body whose image URLs point to the final screenshot commit.

- [ ] **Step 1: Run the full local verification gate**

Run sequentially:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.presentation.ui.home.HomeScreenPresentationInstrumentedTest,dev.chungjungsoo.gptmobile.presentation.ui.chat.ChatMessagePresentationInstrumentedTest
```

Expected: all commands pass with zero failures.

- [ ] **Step 2: Capture affected states**

Install the verified APK on the existing emulator. Capture both Start themes, both cold-start splash themes, populated Home search with a non-empty query, collapsed Chat details, expanded Chat details, and expanded tool trace. Confirm visually that controls are reachable, labels are not clipped, the answer appears once, and the two search actions have distinct icons.

- [ ] **Step 3: Commit screenshot and planning evidence**

Stage only the eight screenshots plus this plan and its spec, then commit:

```bash
git commit -m "docs: refresh refinement screenshots"
```

- [ ] **Step 4: Push and refresh the PR body**

Push `codex/material3-expressive-implementation`. Replace the immutable screenshot commit in all PR image URLs with the final screenshot commit so the five existing horizontal tables continue rendering all 38 images.

- [ ] **Step 5: Verify the live PR**

Read PR #327 back through GitHub's API. Confirm exactly one Screenshots heading, 38 image elements, five tables, and no unresolved review thread introduced by this change. Report current checks without claiming pending checks are green.
