# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

GPT Mobile is an Android chat application that supports chatting with multiple AI models simultaneously (OpenAI GPT, Anthropic Claude, Google Gemini, Groq, and Ollama). Built with 100% Kotlin, Jetpack Compose, and follows Modern Android App Architecture patterns.

## Build Commands

### Development Build
```bash
./gradlew assembleDebug
```

### Release Build
```bash
./gradlew assembleRelease
./gradlew bundleRelease  # For Google Play Store AAB format
```

### Testing
```bash
./gradlew test                      # Unit tests
./gradlew connectedAndroidTest      # Instrumented tests
```

### Code Quality
- **Linting**: Handled by GitHub Actions with ktlint 1.3.1
- **Security**: CodeQL analysis runs automatically on main branch changes

## Architecture

### Core Structure
- **MVVM Pattern**: ViewModels handle UI state, Repositories manage data
- **Dependency Injection**: Hilt for all dependency management
- **Database**: Room for local chat history storage
- **Networking**: Ktor client with OkHttp engine for API calls
- **UI**: Jetpack Compose with Material 3 design system

### Data Flow
1. **Chat Flow**: `ChatViewModel` → `ChatRepository` → API clients (OpenAI, Anthropic, etc.) → Streaming responses
2. **Persistence**: All chat data stored locally via Room database
3. **Settings**: DataStore for user preferences and API configurations

### Key Components
- **ChatRepositoryImpl**: Handles all AI platform communications with streaming support
- **NetworkModule**: Configures Ktor client and Anthropic API
- **Platform Support**: Each AI service has dedicated completion methods with consistent `Flow<ApiState>` responses
- **Message Transformation**: Platform-specific message format conversion for API compatibility

### Module Structure
- `data/`: Models, DTOs, database entities, repositories, network clients
- `di/`: Hilt dependency injection modules
- `presentation/`: ViewModels, UI components, navigation, theming
- `util/`: Extensions, utilities, string resources

## Development Notes

- **Min SDK**: 31 (Android 12)
- **Target SDK**: 35
- **Java Version**: 17
- **Build System**: Gradle with Kotlin DSL
- **Material You**: Dynamic theming support without activity restart
- **Internationalization**: Multiple language support via string resources