# AGENTS.md

This file provides guidance to AI coding agents working on this Android codebase.

## Project Overview

GPT Mobile is an Android chat app supporting multiple AI providers (OpenAI, Anthropic, Google, Groq, Ollama, OpenRouter). Built with Kotlin, Jetpack Compose, MVVM architecture, and Hilt DI.

## Build Commands

```bash
# Development build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
./gradlew bundleRelease  # AAB format for Play Store

# Run all unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "dev.chungjungsoo.gptmobile.ExampleUnitTest"

# Run a single test method
./gradlew test --tests "dev.chungjungsoo.gptmobile.ExampleUnitTest.addition_isCorrect"

# Instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Run single instrumented test
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chungjungsoo.gptmobile.ExampleInstrumentedTest

# Clean build
./gradlew clean assembleDebug
```

## Linting

- **Tool**: ktlint 1.3.1 (enforced via GitHub Actions)
- **Style**: `android_studio` code style
- **Run locally**: Use ktlint CLI or IDE integration
- Linting runs automatically on PRs to main branch

## Code Style Guidelines

### Formatting

- **Indentation**: 4 spaces (no tabs)
- **Line length**: No hard limit (max_line_length = off)
- **Trailing commas**: Disabled
- **Final newline**: Required
- **Line endings**: LF

### Imports

Order imports as follows (alphabetically within each group):
1. Android imports (`android.*`)
2. AndroidX imports (`androidx.*`)
3. Third-party libraries (`ai.koog.*`, `com.*`, `io.ktor.*`)
4. Dagger/Hilt (`dagger.*`)
5. Project imports (`dev.chungjungsoo.gptmobile.*`)
6. Java stdlib (`java.*`)
7. Javax (`javax.*`)
8. Kotlin/Kotlinx (`kotlin.*`, `kotlinx.*`)

Wildcard imports allowed for: `java.util.*`, `kotlinx.android.synthetic.**`

### Naming Conventions

| Element | Convention | Example |
|---------|------------|---------|
| Classes | PascalCase | `ChatViewModel`, `MessageV2` |
| Interfaces | PascalCase | `ChatRepository`, `SettingDataSource` |
| Implementations | `*Impl` suffix | `ChatRepositoryImpl` |
| ViewModels | `*ViewModel` suffix | `HomeViewModel` |
| Composables | PascalCase | `ChatScreen`, `UserChatBubble` |
| Functions | camelCase | `fetchMessages()`, `updateChatTitle()` |
| Variables | camelCase | `chatRoom`, `enabledPlatforms` |
| Private backing fields | `_` prefix | `_chatRoom`, `_question` |
| Constants | SCREAMING_SNAKE_CASE | `DB_NAME`, `DB_NAME_V2` |
| Boolean flags | `is*` prefix | `isLoaded`, `isSelectionMode` |
| Entities (versioned) | `*V2` suffix | `MessageV2`, `ChatRoomV2` |

### File Organization

- One primary class per file
- File name matches primary class name
- Related private composables can be in same file
- Package structure: `data/`, `di/`, `presentation/`, `util/`

## Type Patterns

### StateFlow for UI State

```kotlin
private val _chatRoom = MutableStateFlow(ChatRoomV2(...))
val chatRoom = _chatRoom.asStateFlow()

// Update pattern
_chatRoom.update { it.copy(title = newTitle) }
```

### Sealed Classes for States

```kotlin
sealed class ApiState {
    data object Loading : ApiState()
    data class Success(val textChunk: String) : ApiState()
    data class Error(val message: String) : ApiState()
    data object Done : ApiState()
}
```

### Data Classes with Defaults

```kotlin
data class Platform(
    val name: ApiType,
    val selected: Boolean = false,
    val token: String? = null
)
```

## Error Handling

### Flow-based API Error Handling

```kotlin
return client.executeStreaming(prompt, model = model)
    .map { chunk -> ApiState.Success(chunk.text) }
    .catch { throwable -> emit(ApiState.Error(throwable.message ?: "Unknown Error")) }
    .onStart { emit(ApiState.Loading) }
    .onCompletion { emit(ApiState.Done) }
```

### Null Safety Patterns

```kotlin
// Elvis operator for defaults
val token = platform.token ?: ""

// Safe call with let
platform.systemPrompt?.let { system(it) }

// Safe collection access
assistantMessages.getOrNull(idx)?.takeIf { idx < userMessages.lastIndex }

// checkNotNull for required SavedStateHandle values
private val chatRoomId: Int = checkNotNull(savedStateHandle["chatRoomId"])
```

### Early Returns for Validation

```kotlin
fun retryChat(platformIndex: Int) {
    if (platformIndex >= enabledPlatformsInChat.size || platformIndex < 0) return
    val platform = _enabledPlatformsInApp.value.firstOrNull { ... } ?: return
    // proceed with valid data
}
```

## Coroutine Patterns

### ViewModel Scope

```kotlin
init {
    viewModelScope.launch { fetchMessages() }
}

fun askQuestion() {
    viewModelScope.launch {
        chatRepository.completeChat(...).handleStates(...)
    }
}
```

### Composable Side Effects

```kotlin
LaunchedEffect(isIdle) {
    listState.animateScrollToItem(...)
}

val scope = rememberCoroutineScope()
scope.launch { /* one-off action */ }
```

## Compose Patterns

### State Collection

```kotlin
val chatRoom by chatViewModel.chatRoom.collectAsStateWithLifecycle()
```

### Hilt ViewModel Injection

```kotlin
@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel = hiltViewModel(),
    onBackAction: () -> Unit
)
```

### Scaffold Structure

```kotlin
Scaffold(
    topBar = { ... },
    bottomBar = { ... },
    floatingActionButton = { ... }
) { innerPadding ->
    LazyColumn(modifier = Modifier.padding(innerPadding)) { ... }
}
```

### Modifier Chaining

```kotlin
Modifier
    .fillMaxWidth()
    .padding(horizontal = 16.dp)
    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
```

## Dependency Injection

### Module Pattern

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideNetworkClient(): NetworkClient = NetworkClient(CIO)
}
```

### Repository Binding

```kotlin
@Provides
@Singleton
fun provideChatRepository(...): ChatRepository = ChatRepositoryImpl(...)
```

## Architecture Notes

- **Min SDK**: 31 (Android 12)
- **Target SDK**: 36
- **Java**: 17
- **Pattern**: MVVM with Repository layer
- **DI**: Hilt
- **Database**: Room with schema versioning
- **Network**: Ktor with CIO engine
- **Serialization**: kotlinx.serialization
- **UI**: Material 3 with dynamic theming

## Testing

Test files location:
- Unit tests: `app/src/test/kotlin/`
- Instrumented tests: `app/src/androidTest/kotlin/`

Test naming: `methodName_condition_expectedResult` or simple descriptive names
