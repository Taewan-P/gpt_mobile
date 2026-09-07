# Material 3 Expressive Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the approved calm Material 3 Expressive redesign across GPT Mobile, including quiet expandable chat details and a platform splash that follows the selected theme mode.

**Architecture:** Keep the existing single-activity Compose, Navigation, Hilt, repository, and ViewModel structure. Add only three small shared presentation seams—app shapes/motion, grouped settings surfaces, and shared empty/error content—then restyle existing screens in place. Add explicit state only where the current nullable/list state cannot distinguish loading, missing, and failure.

**Tech Stack:** Kotlin 2.3.21, Jetpack Compose BOM 2026.06.00, Material 3 1.4.0, Navigation Compose 2.9.8, Android core-splashscreen 1.2.0, Hilt, StateFlow, JUnit 4, Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-09-04-material3-expressive-redesign-design.md`

## Global Constraints

- Minimum SDK remains 31. Do not add AppCompat or any new dependency.
- Do not change routes, database schemas, repositories, provider protocols, or stored chat chronology.
- Preserve the active light, dark, and dynamic color schemes; leave unused medium/high-contrast palettes unchanged.
- Use 12dp, 20dp, and 28dp shapes; 16dp phone margins; 24dp section gaps; 720dp maximum form/settings width; 48dp interactive targets.
- App-owned motion uses only default spatial `0.8f/380f`, fast spatial `0.6f/800f`, and fast effects `1.0f/3800f`; never call internal Material `MotionScheme` or `MaterialExpressiveTheme` APIs.
- Tool setup starts with exactly Web Search followed by MCP Server and keeps its existing progressive state machine.
- Chat stays content-first: actions are hidden behind one visible per-message entry point, Details is collapsed by default, expanded details preserve recorded chronology, and missing legacy order is never invented.
- Keep one progress signal per operation; keep failed, canceled, and interrupted terminal status and recovery visible.
- Never display any API-key character.
- Preserve unrelated `.serena/project.yml` and `.ui-test/` work.
- Follow TDD for branching/state behavior: write the focused test, observe the expected failure, add the smallest implementation, then observe the pass.
- Every new JVM ViewModel test installs `UnconfinedTestDispatcher` as `Dispatchers.Main` in `@Before` and calls `Dispatchers.resetMain()` in `@After`; instrumented ViewModel tests use the real Main dispatcher plus bounded `StateFlow.first` waits.
- Execute Tasks 1–8 sequentially with one implementation subagent at a time. Shared `strings.xml` and `NavigationGraph.kt` changes build on prior commits; stage only files listed by the current task.

---

### Task 1: Theme, motion, navigation, and splash synchronization

**Files:**
- Create: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/theme/Motion.kt`
- Create: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/theme/ThemeModeSync.kt`
- Create: `app/src/main/res/values/splash_window_background.xml`
- Create: `app/src/main/res/values-night/splash_window_background.xml`
- Create: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/presentation/theme/ThemeModeSyncTest.kt`
- Create: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/presentation/common/ThemeViewModelTest.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/theme/Theme.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/common/ThemeViewModel.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/common/NavigationGraph.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/main/MainActivity.kt`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/values-night/themes.xml`

**Interfaces:**
- Produces: theme-provided app shapes, `defaultSpatialSpec<T>()`, `fastSpatialSpec<T>()`, `fastEffectsSpec<T>()`, `ThemeMode.toApplicationNightMode(): Int`, and `ThemeViewModel.loadState: StateFlow<ThemeLoadState>`.
- Consumers: navigation in this task; setup and chat animation in Tasks 6 and 8.

- [ ] **Step 1: Write the failing theme-mode mapping test**

```kotlin
class ThemeModeSyncTest {
    @Test
    fun themeModesMapToApplicationNightModes() {
        assertEquals(UiModeManager.MODE_NIGHT_NO, ThemeMode.LIGHT.toApplicationNightMode())
        assertEquals(UiModeManager.MODE_NIGHT_YES, ThemeMode.DARK.toApplicationNightMode())
        assertEquals(UiModeManager.MODE_NIGHT_AUTO, ThemeMode.SYSTEM.toApplicationNightMode())
    }
}
```

- [ ] **Step 2: Run the focused test and confirm the expected RED result**

Run: `./gradlew :app:testDebugUnitTest --tests '*ThemeModeSyncTest'`

Expected: FAIL because `toApplicationNightMode` does not exist.

- [ ] **Step 3: Add the minimum public-version-safe theme primitives**

```kotlin
internal val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp)
)

internal fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
    spring(dampingRatio = 0.8f, stiffness = 380f)

internal fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> =
    spring(dampingRatio = 0.6f, stiffness = 800f)

internal fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> =
    spring(dampingRatio = 1f, stiffness = 3800f)

internal fun ThemeMode.toApplicationNightMode(): Int = when (this) {
    ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
    ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
    ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
}
```

Pass `shapes = AppShapes` to the existing `MaterialTheme`; do not change color selection or `AppTypography`.

- [ ] **Step 4: Verify the mapping test is GREEN**

Run: `./gradlew :app:testDebugUnitTest --tests '*ThemeModeSyncTest'`

Expected: PASS.

- [ ] **Step 5: Write the failing load, fallback, and persist-before-publish ViewModel tests**

Use a private `RecordingSettingRepository` implementing `SettingRepository`; every uncalled method throws `error("unused")`. Gate `updateThemes` with a `CompletableDeferred<Unit>` and assert the in-memory mode stays unchanged until the gate completes.

```kotlin
@Test
fun updateThemeModePublishesOnlyAfterPersistence() = runTest {
    val gate = CompletableDeferred<Unit>()
    val repository = RecordingSettingRepository(ThemeSetting(), gate)
    val viewModel = ThemeViewModel(repository)
    advanceUntilIdle()

    viewModel.updateThemeMode(ThemeMode.DARK)
    runCurrent()
    assertEquals(ThemeMode.SYSTEM, viewModel.themeSetting.value.themeMode)

    gate.complete(Unit)
    advanceUntilIdle()
    assertEquals(ThemeMode.DARK, viewModel.themeSetting.value.themeMode)
    assertEquals(ThemeMode.DARK, repository.updated.single().themeMode)
}
```

Add a second test whose repository throws from `fetchThemes()`. Assert `loadState` becomes `FALLBACK_SYSTEM`, the effective setting remains `ThemeMode.SYSTEM`, and startup therefore degrades explicitly instead of hanging or claiming a persisted mode was loaded.

- [ ] **Step 6: Implement the theme startup barrier state**

```kotlin
enum class ThemeLoadState { LOADING, READY, FALLBACK_SYSTEM }

private val _loadState = MutableStateFlow(ThemeLoadState.LOADING)
val loadState = _loadState.asStateFlow()

private fun fetchThemes() {
    viewModelScope.launch {
        try {
            _themeSetting.value = settingRepository.fetchThemes()
            _loadState.value = ThemeLoadState.READY
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            _themeSetting.value = ThemeSetting(themeMode = ThemeMode.SYSTEM)
            _loadState.value = ThemeLoadState.FALLBACK_SYSTEM
        }
    }
}

fun updateThemeMode(theme: ThemeMode) {
    viewModelScope.launch {
        val updated = _themeSetting.value.copy(themeMode = theme)
        settingRepository.updateThemes(updated)
        _themeSetting.value = updated
    }
}
```

Use the same persist-before-publish order for dynamic theme updates so concurrent UI state does not advertise an unsaved selection.

- [ ] **Step 7: Make the platform launch resources theme-aware**

```xml
<!-- res/values/splash_window_background.xml -->
<resources><color name="splash_window_background">#F5FBF5</color></resources>

<!-- res/values-night/splash_window_background.xml -->
<resources><color name="splash_window_background">#0F1512</color></resources>
```

Change only `windowSplashScreenBackground` to `@color/splash_window_background`; retain the green icon background resource and icon attributes.

- [ ] **Step 8: Synchronize the platform qualifier before releasing the existing splash gate**

Hoist `ThemeViewModel` with `by viewModels()`, pass it to `ThemeSettingProvider`, and replace the single splash flag with `isThemeModeReady` and `isStartupRouteReady`. `setKeepOnScreenCondition` returns `!isThemeModeReady || !isStartupRouteReady`. Treat `READY` as the persisted choice and `FALLBACK_SYSTEM` as an explicit System fallback; only `LOADING` keeps the theme barrier closed. The mode side effect belongs to `MainActivity`:

```kotlin
val isThemeResolved = themeLoadState != ThemeLoadState.LOADING
LaunchedEffect(isThemeResolved, themeMode) {
    if (isThemeResolved) {
        getSystemService(UiModeManager::class.java)
            .setApplicationNightMode(themeMode.toApplicationNightMode())
        isThemeModeReady = true
    }
}
```

Keep the call idempotent across Activity recreation. Do not add a second splash Activity or synchronous preference mirror.

Replace the indefinite startup event collector with a one-shot suspending route function using `mainViewModel.event.first()`. After it performs Intro/Migration navigation or accepts Home, set `isStartupRouteReady = true`; do not release the splash merely because the collector was launched.

- [ ] **Step 9: Add short RTL-aware root navigation transitions**

```kotlin
enterTransition = {
    fadeIn(fastEffectsSpec()) +
        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, fastSpatialSpec()) { it / 12 }
},
exitTransition = { fadeOut(fastEffectsSpec()) },
popEnterTransition = { fadeIn(fastEffectsSpec()) },
popExitTransition = {
    fadeOut(fastEffectsSpec()) +
        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, fastSpatialSpec()) { it / 12 }
}
```

Use the exact locally installed Navigation 2.9.8 overloads if parameter order differs; retain Start/End directions.

- [ ] **Step 10: Run focused and compilation checks**

Run: `./gradlew :app:testDebugUnitTest --tests '*ThemeModeSyncTest' --tests '*ThemeViewModelTest' :app:compileDebugKotlin`

Expected: all tests PASS and Kotlin compilation succeeds.

- [ ] **Step 11: Commit Task 1**

```bash
git add app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/theme/Motion.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/theme/ThemeModeSync.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/theme/Theme.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/common/ThemeViewModel.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/common/NavigationGraph.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/main/MainActivity.kt app/src/main/res/values/splash_window_background.xml app/src/main/res/values-night/splash_window_background.xml app/src/main/res/values/themes.xml app/src/main/res/values-night/themes.xml app/src/test/kotlin/dev/chungjungsoo/gptmobile/presentation/theme/ThemeModeSyncTest.kt app/src/test/kotlin/dev/chungjungsoo/gptmobile/presentation/common/ThemeViewModelTest.kt
git commit -m "feat: synchronize expressive theme and splash"
```

### Task 2: Shared settings surfaces and Settings index

**Files:**
- Create: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/common/SettingsSection.kt`
- Create: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/common/EmptyErrorState.kt`
- Create: `app/src/androidTest/kotlin/dev/chungjungsoo/gptmobile/presentation/common/SettingsComponentsInstrumentedTest.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/common/SettingItem.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/common/DestinationCard.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/common/PrimaryLongButton.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/common/RadioItem.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/SettingScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: `SettingsSection`, `EmptyErrorState`, selected/enabled `DestinationCard`, and corrected merged `SettingItem` semantics.
- Consumers: Tasks 3–5 and 7.

- [ ] **Step 1: Write the failing merged-row semantics test**

```kotlin
@Test
fun settingItemIsOneClickableNodeAndChevronIsDecorative() {
    var clicked = false
    composeRule.setContent {
        GPTMobileTheme {
            SettingItem(
                title = "Theme",
                description = "System default",
                onItemClick = { clicked = true },
                showTrailingIcon = true,
                showLeadingIcon = false
            )
        }
    }

    composeRule.onNodeWithText("Theme").assertHasClickAction().performClick()
    assertTrue(clicked)
    composeRule.onNodeWithContentDescription("Arrow Icon").assertDoesNotExist()
}
```

- [ ] **Step 2: Run the instrumented test and confirm RED**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.presentation.common.SettingsComponentsInstrumentedTest`

Expected: FAIL because the current chevron is announced and row descendants are not merged.

- [ ] **Step 3: Implement the two shared layout primitives**

```kotlin
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() }.padding(vertical = 8.dp)
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
                content = { Column(content = content) }
            )
        }
    }
}
```

`EmptyErrorState` uses this single signature and renders one filled primary button, an optional text button, and error semantics when applicable. Do not create separate empty and error implementations.

```kotlin
@Composable
fun EmptyErrorState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    isError: Boolean = false
)
```

- [ ] **Step 4: Correct existing common components in place**

Use `Modifier.semantics(mergeDescendants = true) { }` plus one enabled clickable owner on `SettingItem`; make leading decorations and chevrons silent. Give `DestinationCard` `selected` and `enabled` parameters, a 20dp Material shape, selected `secondaryContainer` color, and one `Role.Button`/ripple owner. Keep `PrimaryLongButton` 56dp high and allow callers to own outer padding.

- [ ] **Step 5: Rebuild Settings as four grouped sections**

Render Appearance, Providers, Tools, and App in that order. Keep every existing navigation callback. Appearance shows dynamic mode plus Light/Dark/System and opens the existing choices in a `ModalBottomSheet`. Providers shows Add platform, Local models, configured rows, and no-provider orientation. Use a single emphasized Add action and no delete action on index rows.

- [ ] **Step 6: Run the semantics test and compile**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.presentation.common.SettingsComponentsInstrumentedTest :app:compileDebugKotlin`

Expected: PASS.

- [ ] **Step 7: Commit Task 2**

```bash
git add app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/common/SettingsSection.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/common/EmptyErrorState.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/common/SettingItem.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/common/DestinationCard.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/common/PrimaryLongButton.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/common/RadioItem.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/SettingScreen.kt app/src/main/res/values/strings.xml app/src/androidTest/kotlin/dev/chungjungsoo/gptmobile/presentation/common/SettingsComponentsInstrumentedTest.kt
git commit -m "feat: group expressive settings"
```

### Task 3: Add Platform and provider details

**Files:**
- Create: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/SettingViewModelV2Test.kt`
- Create: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/PlatformSettingScreenTest.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/SettingViewModelV2.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/AddPlatformScreen.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/PlatformSettingViewModel.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/PlatformSettingScreen.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/common/NavigationGraph.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `SettingsSection`, `EmptyErrorState`, updated `DestinationCard`, and Task 1 motion.
- Produces: `AddPlatformSaveState`, `PlatformLoadState`, `retryLoadPlatform()`, and `apiKeySummary(token: String?, set: String, notSet: String): String`.

- [ ] **Step 1: Write failing save/load/API-key behavior tests**

```kotlin
@Test
fun apiKeySummaryNeverReturnsTokenCharacters() {
    assertEquals("Key set", apiKeySummary("secret", "Key set", "Key not set"))
    assertEquals("Key not set", apiKeySummary(null, "Key set", "Key not set"))
}
```

Extend `PlatformSettingViewModelTest` to assert `Loading -> Loaded`, `Loading -> NotFound`, repository exception -> `Error`, and Retry -> `Loaded`. In `SettingViewModelV2Test`, gate repository persistence and assert navigation success runs only after persistence; on failure assert entered screen state remains and `errorMessage` is present.

- [ ] **Step 2: Run focused tests and confirm RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*PlatformSettingScreenTest' --tests '*PlatformSettingViewModelTest' --tests '*SettingViewModelV2Test'`

Expected: FAIL for missing states/helper and immediate save navigation behavior.

- [ ] **Step 3: Add the minimum explicit existing-ViewModel states**

```kotlin
sealed interface PlatformLoadState {
    data object Loading : PlatformLoadState
    data object Loaded : PlatformLoadState
    data object NotFound : PlatformLoadState
    data class Error(val message: String) : PlatformLoadState
}

data class AddPlatformSaveState(
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)
```

Keep existing `platformState` and dialog/tool state for edit callbacks. `retryLoadPlatform()` re-enters Loading and catches non-cancellation failure. `addPlatform(platform, onSuccess)` sets saving, persists, then invokes success; it clears saving and exposes failure without navigating.

- [ ] **Step 4: Make Navigation wait for a successful add**

Pass the parent `SettingViewModelV2` into `AddPlatformScreen`; navigate up only from `onSuccess`. Do not change route strings or add destinations.

- [ ] **Step 5: Redesign Add Platform in place**

Keep `API_TYPE -> DETAILS`, all eight `ClientType` choices, LiteRT local-model behavior, and existing validation. Use selected tonal destination cards, 720dp centered content, inline field errors, and one bottom Save action. Disable it and show one inline busy state during persistence; keep values after failure.

- [ ] **Step 6: Group provider details and fix secret disclosure**

Always render the app bar. Render Loading, NotFound with Back/Retry, Error with Back/Retry, or grouped loaded content. Use groups Connection, Model and generation, Instructions, Runtime, Tools; collapse advanced generation by default with `rememberSaveable(platform.uid)`. Preserve current ClientType predicates and dialogs. Replace the `token[0]` path with:

```kotlin
internal fun apiKeySummary(token: String?, set: String, notSet: String): String =
    if (token.isNullOrEmpty()) notSet else set
```

- [ ] **Step 7: Run focused tests and compilation**

Run: `./gradlew :app:testDebugUnitTest --tests '*PlatformSettingScreenTest' --tests '*PlatformSettingViewModelTest' --tests '*SettingViewModelV2Test' :app:compileDebugKotlin`

Expected: PASS.

- [ ] **Step 8: Commit Task 3**

```bash
git add app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/SettingViewModelV2.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/AddPlatformScreen.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/PlatformSettingViewModel.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/PlatformSettingScreen.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/common/NavigationGraph.kt app/src/main/res/values/strings.xml app/src/test/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/SettingViewModelV2Test.kt app/src/test/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/PlatformSettingScreenTest.kt app/src/test/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/PlatformSettingViewModelTest.kt
git commit -m "feat: redesign provider settings"
```

### Task 4: Tool Connections list and progressive editor

**Files:**
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/ToolConnectionsViewModel.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/ToolConnectionsScreen.kt`
- Modify: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/ToolConnectionsViewModelTest.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `SettingsSection`, `EmptyErrorState`, `PrimaryLongButton`, `DestinationCard`.
- Produces: row-scoped `busyConnectionUid`, `rowErrorConnectionUid`, `rowErrorMessage`, plus list loading and editor saving state on `ToolConnectionsUiState`.

- [ ] **Step 1: Write failing row-scoped state tests**

Add focused tests proving initial refresh clears `isLoading`, a failed refresh exposes retryable error, OAuth for `mcp-1` never marks `mcp-2` busy, browser/callback failure stays associated with `mcp-1`, dismiss clears only that row, and save busy state always clears while success callback runs only after persistence.

```kotlin
assertEquals("mcp-1", viewModel.uiState.value.busyConnectionUid)
assertEquals("mcp-1", viewModel.uiState.value.rowErrorConnectionUid)
viewModel.clearRowError("mcp-1")
assertNull(viewModel.uiState.value.rowErrorConnectionUid)
```

- [ ] **Step 2: Run the Tool Connections tests and confirm RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*ToolConnectionsViewModelTest'`

Expected: FAIL because row-scoped and loading/saving fields do not exist.

- [ ] **Step 3: Replace the global busy bit with scoped state**

```kotlin
data class ToolConnectionsUiState(
    val connections: List<ToolConnection> = emptyList(),
    val isLoading: Boolean = true,
    val busyConnectionUid: String? = null,
    val rowErrorConnectionUid: String? = null,
    val rowErrorMessage: String? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)
```

Keep OAuth single-flight and remember the initiating UID through browser launch/callback. Clear busy in `finally`; keep global error only for list/editor operations with no row owner.

- [ ] **Step 4: Redesign the list states and rows**

Show one initial loading treatment, shared empty/error content, then grouped Web Search and MCP connections. For configured rows, show edit, connect/reconnect, and delete; show progress/error actions only on the matching UID. When empty, use the body Add connection action and omit the duplicate top-bar add action.

- [ ] **Step 5: Redesign the existing editor without changing its state machine**

When `connectionUid` is present, render loading until the initial refresh completes; only then may absence become Not found with Back and Retry. Keep the first cards in exact Web Search/MCP Server order. Keep Web Search provider selection and MCP details/authentication steps. Move Next/Save to one full-width bottom action above IME/navigation insets. Group fields under Identity, Connection, and Authentication, keep only applicable fields, preserve cleartext and credential rules, and keep values after save failure. Replace the editor's save-error dialog with an inline retryable error tied to the unchanged form values. Do not request local-network permission until the existing connect action requires it.

- [ ] **Step 6: Run focused tests and compilation**

Run: `./gradlew :app:testDebugUnitTest --tests '*ToolConnectionsViewModelTest' :app:compileDebugKotlin`

Expected: PASS.

- [ ] **Step 7: Commit Task 4**

```bash
git add app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/ToolConnectionsViewModel.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/ToolConnectionsScreen.kt app/src/main/res/values/strings.xml app/src/test/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/ToolConnectionsViewModelTest.kt
git commit -m "feat: polish progressive tool settings"
```

### Task 5: Local Models, About, and Licenses

**Files:**
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/LocalModelsViewModel.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/LocalModelCatalogUi.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/LocalModelsScreen.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/AboutScreen.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/LicenseScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: shared grouped settings surfaces and empty/error content.
- Produces: `LocalModelsUiState.loadError: String?` and `LocalModelsViewModel.retryLoad()`; no repository change.

- [ ] **Step 1: Write the failing catalog failure/retry test**

Extend `LocalModelsViewModelTest` with a catalog repository that fails once then returns the existing fixture. Assert the first completed load has `isLoading == false` and a non-null `loadError`; `retryLoad()` clears the error and publishes the catalog items.

- [ ] **Step 2: Run the focused test and confirm RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*LocalModelsViewModelTest'`

Expected: FAIL because catalog errors currently become an indistinguishable empty list and no retry exists.

- [ ] **Step 3: Expose failure/retry without changing repositories**

Add `loadError: String? = null` to `LocalModelsUiState` and its private list state. Move the existing reconcile/catalog/combined-flow body into `retryLoad()`: cancel the prior load job, set loading and clear the error, fetch the catalog, then collect models. On non-cancellation failure set `isLoading = false` and `loadError` to `throwable.message.orEmpty()`; the screen uses its localized generic resource when that value is blank. Do not convert a thrown catalog load to an empty success.

- [ ] **Step 4: Restyle Local Models using existing state and actions**

Render load failure through `EmptyErrorState` with Retry. Otherwise keep the order Account, storage, models. Put account and each model in 20dp `surfaceContainerLow` groups within 720dp centered content. Reuse `LocalModelRequirements`, capability chips, and `LocalModelDownloadStatus`. Keep initial loading centered and per-card downloading/checking progress local. Preserve download/cancel/retry/delete and delete confirmation.

- [ ] **Step 5: Group About and retain native behaviors**

Use App identity/version, Legal, Project links, and Support sections. Keep clipboard version behavior, current URLs, `LocalUriHandler`, and License navigation. Decorative icons have null descriptions; the merged rows carry localized accessible labels.

- [ ] **Step 6: Apply shared width/app-bar treatment to Licenses**

Keep `produceLibraries(R.raw.aboutlibraries)` and `LibrariesContainer`; change only content width, background, spacing, and back-target treatment.

- [ ] **Step 7: Run model tests and compile**

Run: `./gradlew :app:testDebugUnitTest --tests '*LocalModelsViewModelTest' :app:compileDebugKotlin`

Expected: PASS.

- [ ] **Step 8: Commit Task 5**

```bash
git add app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/LocalModelsViewModel.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/LocalModelCatalogUi.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/LocalModelsScreen.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/AboutScreen.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/LicenseScreen.kt app/src/main/res/values/strings.xml app/src/test/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/LocalModelsViewModelTest.kt
git commit -m "feat: polish settings child pages"
```

### Task 6: Quiet chat details and message action sheets

**Files:**
- Create: `app/src/androidTest/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/chat/ChatMessagePresentationInstrumentedTest.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/chat/ChatBubble.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/chat/ChatDialogs.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/chat/ChatScreen.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/chat/ThinkingBlock.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/chat/ToolTraceBlock.kt`
- Modify: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/chat/ToolTraceBlockTest.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: Task 1 motion specs and existing `effectiveContent/effectiveThoughts/effectiveTimeline/effectiveRunId` utilities.
- Produces: `MessageActionsSheet`, quiet/expanded assistant presentation, revision-keyed disclosure, and `ToolTraceBlock(events: List<ToolEvent>, modifier: Modifier, contentIdentity: Any, onViewFull: (String) -> Unit)`.

- [ ] **Step 1: Write failing tool-detail and Compose presentation tests**

Extend `ToolTraceBlockTest` for an unresolved tool sequence helper and for detection that a value exceeding the inline limit requires View full. In the Compose test, assert: completed message exposes one Message actions button; action labels appear only after click; streaming hides assistant actions; failed Retry stays outside and is absent from the sheet; Details expansion shows chronological thinking/text/tool content with the answer exactly once; missing tool reference shows Details unavailable.

- [ ] **Step 2: Run focused unit/instrumented tests and confirm RED**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*ToolTraceBlockTest'
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.presentation.ui.chat.ChatMessagePresentationInstrumentedTest
```

Expected: FAIL because quiet disclosure/action-sheet/View-full behavior is missing.

- [ ] **Step 3: Add the single message actions sheet**

Place it with the existing chat dialogs instead of adding another framework:

```kotlin
internal enum class MessageActionRole { USER, ASSISTANT }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MessageActionsSheet(
    role: MessageActionRole,
    canCopy: Boolean,
    canEdit: Boolean,
    canSelectText: Boolean,
    canRetry: Boolean,
    revisionIndexLabel: String?,
    canShowPreviousRevision: Boolean,
    canShowNextRevision: Boolean,
    onCopy: () -> Unit,
    onSelectText: () -> Unit,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
    onPreviousRevision: () -> Unit,
    onNextRevision: () -> Unit,
    onDismissRequest: () -> Unit
)
```

User actions are Copy/Edit. Assistant actions are only valid Copy/Select/Edit/Retry/revision controls. Preserve current `canEdit`, `canRetry`, and `isError` predicates; set `canCopy` and `canSelectText` false for the existing error case. Failed inline Retry replaces sheet Retry. Close the action sheet before opening the existing selectable-text sheet.

- [ ] **Step 4: Replace permanent controls with quiet and expanded branches**

Add one 48dp low-emphasis action button to completed user and assistant messages. A response has Details only when it has thinking, tool, or legacy-order content—not for text-only timeline. Collapsed renders answer/attachments plus one compact live tool summary if active. Expanded renders the existing chronological timeline instead of the quiet answer branch, preventing duplicate answer text. Keep notice chips and terminal `AgentRunStatusBlock` outside both branches.

Make progress ownership explicit: `GPTMobileIcon` becomes a static identity icon. With no active tool event, the existing trailing streaming dot is the sole live signal. With an active tool event, suppress that dot and show one progress indicator plus status in the compact Details row. Add this single-signal behavior to the Compose test.

- [ ] **Step 5: Preserve nested/revision identity and missing-event visibility**

Use `rememberSaveable(contentIdentity)` for Details, Thinking, and Tool Trace expansion. Keep content identity based on message/platform/run/revision, not streaming text. When a TOOL timeline item cannot resolve its sequence, render `details_unavailable` rather than silently omitting it. NOTICE stays outside the timeline and LEGACY_ORDER never reconstructs chronology.

- [ ] **Step 6: Reuse the selectable-text sheet for full values**

```kotlin
@Composable
fun ToolTraceBlock(
    events: List<ToolEvent>,
    modifier: Modifier = Modifier,
    contentIdentity: Any = events,
    onViewFull: (String) -> Unit = {}
)
```

Keep 1024-character/six-line inline safety. Show View full when character truncation or `TextLayoutResult.hasVisualOverflow` occurs and pass the original argument/result/error to `openSelectTextSheet`.

- [ ] **Step 7: Polish the existing composer without changing callbacks**

Keep one 28dp floating surface, attachments, safe-area ownership, Attach, Send, and Stop. Do not add a second toolbar or duplicate IME/navigation padding.

- [ ] **Step 8: Run focused tests and compilation**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*ToolTraceBlockTest' --tests '*AssistantTimelineTest' --tests '*AgentRunStatusBlockTest'
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.presentation.ui.chat.ChatMessagePresentationInstrumentedTest
./gradlew :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 9: Commit Task 6**

```bash
git add app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/chat/ChatBubble.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/chat/ChatDialogs.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/chat/ChatScreen.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/chat/ThinkingBlock.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/chat/ToolTraceBlock.kt app/src/main/res/values/strings.xml app/src/test/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/chat/ToolTraceBlockTest.kt app/src/androidTest/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/chat/ChatMessagePresentationInstrumentedTest.kt
git commit -m "feat: make chat details quietly expandable"
```

### Task 7: Home and migration states

**Files:**
- Create: `app/src/androidTest/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/home/HomeViewModelInstrumentedTest.kt`
- Create: `app/src/androidTest/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/migrate/MigrateViewModelInstrumentedTest.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/home/HomeScreen.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/migrate/MigrateViewModel.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/migrate/MigrateScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `EmptyErrorState`, Material shapes/colors, existing repositories and callbacks.
- Produces: explicit chat-list loading/error/retry and per-step migration error/retry.

- [ ] **Step 1: Write failing Home load/failure/retry tests**

Use a private `QueueChatRepository` whose uncalled methods throw `error("unused")`, a private `EmptySettingRepository` whose `fetchPlatformV2s()` returns an empty list and other uncalled methods throw, and a real `AgentRunCoordinator(InstrumentationRegistry.getInstrumentation().targetContext, repository)`. Use `runBlocking`, `withTimeout`, and `StateFlow.first { predicate }`; do not add `kotlinx-coroutines-test` to the instrumented configuration.

```kotlin
@Test
fun failedChatLoadCanRetryWithoutLosingTheScreen() = runBlocking {
    val repository = QueueChatRepository(
        ArrayDeque(listOf(Result.failure(IOException("offline")), Result.success(emptyList())))
    )
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val viewModel = HomeViewModel(
        chatRepository = repository,
        settingRepository = EmptySettingRepository(),
        agentRunCoordinator = AgentRunCoordinator(context, repository)
    )

    viewModel.fetchChats()
    val failed = withTimeout(5_000) {
        viewModel.chatListState.first { it.loadError == "offline" }
    }
    assertEquals("offline", failed.loadError)

    viewModel.retryFetchChats()
    val ready = withTimeout(5_000) {
        viewModel.chatListState.first { !it.isLoading && it.loadError == null }
    }
    assertNull(ready.loadError)
    assertFalse(ready.isLoading)
}
```

- [ ] **Step 2: Write failing migration failure/retry tests**

In an instrumented test, gate the existing migration repository calls and assert a platform or chat failure leaves only that step in Error with an inline message; Retry clears the message, re-enters Migrating, and preserves the platform-before-chat dependency. Instrumentation keeps the current `android.util.Log` path valid without adding a logging abstraction.

- [ ] **Step 3: Run both focused suites and confirm RED**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.presentation.ui.home.HomeViewModelInstrumentedTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.presentation.ui.migrate.MigrateViewModelInstrumentedTest
```

Expected: FAIL because load/error/retry state is absent.

- [ ] **Step 4: Add minimal Home state and render priority**

Add `isLoading` and `loadError` to `ChatListState`; make `fetchChats()` cancellation-safe, clear error on retry/success, and expose `retryFetchChats()`. Render initial loading, load error + Retry, search-empty, empty + one Start a chat action, then rows. Suppress the FAB for loaded-empty/error so the action is not duplicated. Keep search/selection behavior, stable list keys, and active-run gating.

- [ ] **Step 5: Add per-step migration error copy and one progress signal**

Extend the existing state instead of adding a controller:

```kotlin
data class MigrationUIState(
    val platformState: MigrationState = MigrationState.READY,
    val chatState: MigrationState = MigrationState.BLOCKED,
    val numberOfPlatforms: Int = 0,
    val numberOfChats: Int = 0,
    val platformErrorMessage: String? = null,
    val chatErrorMessage: String? = null
)
```

Clear only the retried step's error. In `MigrationCard`, show inline error plus Retry, one progress treatment while migrating, and existing ready/migrated state; preserve navigation after completion.

- [ ] **Step 6: Run focused tests and compilation**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.presentation.ui.home.HomeViewModelInstrumentedTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.presentation.ui.migrate.MigrateViewModelInstrumentedTest
./gradlew :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 7: Commit Task 7**

```bash
git add app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/home/HomeViewModel.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/home/HomeScreen.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/migrate/MigrateViewModel.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/migrate/MigrateScreen.kt app/src/main/res/values/strings.xml app/src/androidTest/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/home/HomeViewModelInstrumentedTest.kt app/src/androidTest/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/migrate/MigrateViewModelInstrumentedTest.kt
git commit -m "feat: add expressive empty and recovery states"
```

### Task 8: Start, setup, completion, and full app verification

**Files:**
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/startscreen/StartScreen.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setup/SetupAppBar.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setup/SetupPlatformListScreen.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setup/SetupPlatformTypeScreen.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setup/SetupPlatformWizardScreen.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setup/SetupCompleteScreen.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/common/NavigationGraph.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: Task 1 motion/shapes, Task 2 destination cards/buttons/empty state, and existing `SetupViewModelV2` state.
- Produces: no new route or data interface.

- [ ] **Step 1: Run the existing setup behavior tests as a regression gate**

Run: `./gradlew :app:testDebugUnitTest --tests '*SetupViewModelV2Test'`

Expected: PASS. This task changes presentation only; do not add tests that merely freeze existing step counts.

- [ ] **Step 2: Align Start and setup surfaces**

Use one calm Start hero and one Get started action. Center content at 720dp; use shared 16/24dp rhythm and existing illustrations. Apply one setup app-bar treatment, 20dp selectable cards, explicit selected states, 48dp controls, and one stable bottom action. Preserve platform list deletion, all current provider choices, and local-model navigation.

- [ ] **Step 3: Apply app motion to the wizard only where state moves spatially**

Replace the wizard's hard-coded slide/fade with Task 1 `defaultSpatialSpec` and `fastEffectsSpec`, using Start/End direction. Do not stagger lists or animate typing/search/copy actions.

- [ ] **Step 4: Polish completion without adding state**

Change the call to `SetupCompleteScreen(platforms: List<PlatformV2>, onNavigate: (String) -> Unit, onBackAction: () -> Unit)`. Center at 720dp, summarize configured/pending provider names from that existing setup state, and use one Start chatting action that retains the existing `Route.CHAT_LIST` navigation.

- [ ] **Step 5: Run all automated checks**

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:compileDebugKotlin
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

Run ktlint 1.3.1 against changed Kotlin files and fix only violations introduced by this branch.

- [ ] **Step 6: Run the rendered device matrix**

On the existing 1080x2400 emulator/device, capture named PNGs and semantics dumps for every route/state listed in the spec. Repeat representative layouts at 720dp+ width, 1.3x font scale, RTL, and animator scale zero. Verify one progress treatment, 48dp targets, merged rows, and no clipping.

- [ ] **Step 7: Run the splash cold-start matrix**

Select each theme in-app, force-stop, and relaunch: System/device light, System/device dark, explicit Light/device dark, explicit Dark/device light, Light-to-System, and Dark-to-System. Verify window color, green icon background, correct first Compose frame, and correct subsequent cold starts after the one-time unsynchronized-upgrade case.

- [ ] **Step 8: Commit Task 8**

```bash
git add app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/startscreen/StartScreen.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setup/SetupAppBar.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setup/SetupPlatformListScreen.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setup/SetupPlatformTypeScreen.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setup/SetupPlatformWizardScreen.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setup/SetupCompleteScreen.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/common/NavigationGraph.kt app/src/main/res/values/strings.xml
git commit -m "feat: finish expressive app experience"
```
