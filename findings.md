# Findings: GPT Mobile V2 Refactoring

## Codebase Architecture Findings

### Data Model Structure

#### MessageV2 Entity
**Location**: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/database/entity/MessageV2.kt`

**Key Fields**:
- `files: List<String>` - Stores file paths/URIs as string list
- `thoughts: String` - New field for AI reasoning/thinking process
- `revisions: List<String>` - Tracks message edit history
- `platformType: String?` - UUID referencing PlatformV2.uid (changed from enum)

**Storage Method**:
- Uses `StringListConverter` for Room type conversion
- Lists stored as comma-separated strings in SQLite
- Empty list serialized as empty string, not null

**Migration Notes**:
- Old `imageData: String?` field (single base64 image) → `files: List<String>` (multiple file paths)
- Old image data NOT migrated to new files list (migration sets empty list)

#### PlatformV2 Entity
**Location**: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/database/entity/PlatformV2.kt`

**Key Insight**: Dynamic platform system using UUID for identification
- `uid: String` - UUID for cross-references
- `name: String` - User-defined display name (not tied to enum)
- `compatibleType: ClientType` - Determines which API client to use
- `reasoning: Boolean` - Flag for extended thinking models (o1, o3, etc.)

**ClientType Enum Values**:
1. `OPENAI` - For OpenAI, Groq (OpenAI-compatible)
2. `ANTHROPIC` - For Claude models
3. `GOOGLE` - For Gemini models
4. `OPENROUTER` - OpenRouter proxy service
5. `OLLAMA` - Local model server
6. `CUSTOM` - User-defined custom APIs

**Flexibility**:
- Can have multiple platforms of same ClientType
- User can name platforms freely ("My GPT-4", "Company Claude", etc.)
- Each platform has independent configuration (URL, token, model, params)

### API Implementation Patterns

#### Anthropic API (Reference Implementation)
**Location**: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/network/AnthropicAPIImpl.kt`

**Streaming Pattern**:
```kotlin
networkClient().sse(urlString, request) {
    incoming.collect { event ->
        event.data?.let { line -> emit(Json.decodeFromString(line)) }
    }
}
```

**Key Findings**:
- Uses Ktor SSE (Server-Sent Events) for streaming
- Returns `Flow<MessageResponseChunk>` (sealed class)
- Dynamic token and API URL configuration via setters
- Exception handling wraps errors in `ErrorResponseChunk`

**Response Chunk Types**:
1. `MessageStartResponseChunk` - Stream begins
2. `ContentDeltaResponseChunk` - Text chunk arrives
3. `MessageStopResponseChunk` - Stream ends
4. `ErrorResponseChunk` - Error occurred

**Message Format**:
- Supports `List<MessageContent>` per message
- Content types: `TextContent`, `ImageContent`
- Images: Base64-encoded with MIME type

#### Network Configuration
**Location**: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/network/NetworkClient.kt`

**Ktor Client Setup**:
- Engine: CIO (Coroutine-based I/O)
- Timeout: 5 minutes for long-running requests
- Plugins: ContentNegotiation (JSON), SSE, HttpTimeout, Logging
- JSON config: `ignoreUnknownKeys = true` for forward compatibility

### Settings Architecture

#### Old System (V1)
**Data Storage**: DataStore (Preferences API)
**Platform Identification**: `ApiType` enum (5 hardcoded values)
**Structure**: Key-value pairs in SharedPreferences

**Limitations**:
- Fixed to 5 platforms
- Cannot add custom platforms
- Settings scattered across multiple DataStore keys
- Difficult to manage platform-specific features

#### New System (V2)
**Data Storage**: Room database (`platform_v2` table)
**Platform Identification**: UUID strings
**Structure**: Normalized database table with entity relationships

**Advantages**:
- Unlimited platforms (database scalability)
- User can create custom platforms
- All settings centralized in single entity
- Supports advanced features (reasoning, custom timeouts)
- Foreign key relationships with messages/chats

#### Migration Strategy
**Location**: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/migrate/MigrateScreen.kt`

**Two-Phase Approach**:
1. **Phase 1**: Platform Migration (DataStore → PlatformV2 table)
   - Creates UUID for each platform
   - Maps ApiType to ClientType
   - Preserves all settings (URL, token, model, params)

2. **Phase 2**: Chat Migration (ChatRoom/Message → V2)
   - Must wait for Phase 1 (needs UUID mappings)
   - Converts platform enum references to UUID strings
   - Updates foreign key relationships

**State Machine**:
```
READY → MIGRATING → MIGRATED
  ↓
ERROR (retry available)
  ↓
BLOCKED (waiting for dependency)
```

### File Handling Utilities

#### Existing Helpers in ChatRepositoryImpl
**Location**: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/repository/ChatRepositoryImpl.kt:40-61`

**Functions Found**:
1. `isImageFile(extension: String): Boolean`
   - Supported: jpg, jpeg, png, gif, bmp, webp, tiff, svg

2. `isDocumentFile(extension: String): Boolean`
   - Supported: pdf, txt, doc, docx, xls, xlsx

3. `getMimeType(extension: String): String`
   - Maps extensions to MIME types
   - Returns `application/octet-stream` for unknown types

**Missing Functionality**:
- No file reading logic (need to implement)
- No base64 encoding utility (need to implement)
- No URI/path resolution for Android content:// URIs
- No file size validation

### API State Flow Pattern

#### ApiState Sealed Class
**Location**: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/dto/ApiState.kt`

**States**:
```kotlin
sealed class ApiState {
    object Loading : ApiState()
    data class Success(val textChunk: String) : ApiState()
    data class Error(val message: String) : ApiState()
    object Done : ApiState()
}
```

**Expected Flow Sequence**:
1. Emit `Loading` when request starts
2. Emit `Success(chunk)` for each text chunk from stream
3. Emit `Done` when stream completes successfully
4. Emit `Error(msg)` on any failure

**UI Integration**:
- ViewModel collects this flow
- UI updates in real-time as chunks arrive
- Loading indicator shown until first chunk
- Error state displays user-friendly message

### Platform API Compatibility Matrix

| Platform | API Format | Image Support | Document Support | Streaming | Status |
|----------|------------|---------------|------------------|-----------|--------|
| OpenAI | OpenAI | ✅ Base64 | ❓ Vision | ✅ SSE | Not Implemented |
| Anthropic | Claude | ✅ Base64 | ✅ PDF (Beta) | ✅ SSE | ✅ Implemented |
| Google | Gemini | ✅ Base64/URL | ✅ PDF | ✅ | Not Implemented |
| Groq | OpenAI-compatible | ✅ Base64 | ❌ | ✅ | Not Implemented |
| Ollama | Custom | ✅ Base64 | ❌ | ✅ | Not Implemented |
| OpenRouter | OpenAI-compatible | ✅ Base64 | Platform-dependent | ✅ | Not Implemented |
| Custom | Varies | Varies | Varies | Varies | Not Implemented |

**Key Findings**:
- Most platforms support base64 image encoding
- Document support varies widely (PDF only for some)
- All platforms support streaming (SSE or custom)
- OpenAI-compatible format covers multiple providers

### String Conversion Utilities

#### StringListConverter
**Location**: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/database/converter/StringListConverter.kt`

**Behavior**:
- Serialization: `List<String>` → comma-separated string
- Deserialization: comma-separated string → `List<String>`
- Empty list: stored as empty string ""
- Whitespace handling: trimmed during split

**Usage**:
- `MessageV2.files: List<String>`
- `MessageV2.revisions: List<String>`
- `ChatRoomV2.enabledPlatform: List<String>`

**Important**: File paths containing commas will break! Need to verify this edge case.

### Dependency Injection Setup

#### NetworkModule
**Location**: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/di/NetworkModule.kt`

**Current Providers**:
- `NetworkClient` (Ktor HttpClient wrapper)
- `AnthropicAPI` implementation

**Missing Providers**:
- OpenAI API
- Google API
- Ollama API
- OpenRouter API

**Injection Pattern**:
```kotlin
@Provides
@Singleton
fun provideXyzAPI(networkClient: NetworkClient): XyzAPI =
    XyzAPIImpl(networkClient)
```

### Settings Screen Structure

#### SettingViewModel
**Location**: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/SettingViewModel.kt`

**Current State**:
- Uses `List<Platform>` (old DTO) from DataStore
- Methods: `toggleAPI`, `updateURL`, `updateToken`, etc.
- Direct DataStore mutations

**Required Changes**:
- Switch to `List<PlatformV2>` from database
- Add CRUD operations (create, read, update, delete platforms)
- Use Flow for reactive updates
- Remove DataStore dependencies

#### SettingScreen UI
**Location**: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/SettingScreen.kt`

**Current Implementation**:
- Iterates `ApiType.entries` (hardcoded 5 platforms)
- Shows theme settings
- Navigates to `PlatformSettingScreen` with `ApiType` parameter

**Required Changes**:
- Iterate `platformState` from ViewModel (dynamic list)
- Add "Add Platform" button
- Add swipe-to-delete or edit actions
- Handle empty state (no platforms configured)
- Navigate with platform UID instead of ApiType

#### PlatformSettingScreen
**Location**: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/PlatformSettingScreen.kt`

**Current Implementation**:
- Takes `ApiType` as navigation parameter
- Loads old `Platform` from DataStore
- Form fields: enabled, apiUrl, token, model, temperature, topP, systemPrompt

**Required Changes**:
- Take platform UID (String) as parameter
- Load `PlatformV2` by UID from database
- Add field: Platform name (text input)
- Add field: ClientType selector (dropdown)
- Add field: Reasoning toggle (boolean)
- Add field: Timeout slider (Int)
- Update save logic to persist to database
- Add delete button

### Existing Code Quality Observations

**Positive Findings**:
- Clean MVVM architecture with clear separation
- Proper use of Hilt dependency injection
- Consistent naming conventions
- Well-structured DTOs with Kotlinx Serialization
- Migration system is well-designed and tested

**Areas for Improvement**:
- Limited error handling in some repository methods
- No retry logic for failed API calls
- File path storage in comma-separated strings (fragile)
- Missing input validation for platform settings
- No API response caching

### Testing Considerations

**Unit Testing Needs**:
- Message transformation logic (MessageV2 → platform request)
- File encoding/decoding utilities
- Platform routing logic
- Error handling paths

**Integration Testing Needs**:
- API streaming with real backends
- File upload with various formats
- Settings CRUD operations
- Migration path validation

**UI Testing Needs**:
- Platform creation/deletion flow
- Settings screen navigation
- Error message display
- Real-time chat updates

## Research Notes

### OpenAI API Compatibility
- Groq uses exact OpenAI format (same endpoints, request/response)
- OpenRouter uses OpenAI format with minor extensions
- Many custom APIs implement OpenAI-compatible endpoints
- **Implication**: Single OpenAI client can serve multiple platforms

### Android File Access
- Need ContentResolver for content:// URIs
- Need File API for file:// paths
- Should handle permission errors gracefully
- Consider file size limits (5MB for images is common)

### Streaming Response Handling
- SSE is standard for OpenAI, Anthropic
- Google Gemini uses custom streaming format
- Ollama uses JSON streaming (newline-delimited)
- Need abstraction layer for different stream formats

### Base64 Encoding Considerations
- Android provides `Base64` utility (android.util.Base64)
- Large files can cause OOM (out of memory)
- Should stream encoding for files > 1MB
- MIME type detection: Use Android MimeTypeMap

## Action Items from Findings

1. **Immediate**: Verify StringListConverter handles file paths correctly
2. **Before Implementation**: Research Google Gemini streaming API format
3. **Before Implementation**: Research Ollama API format and compatibility
4. **During Implementation**: Add file size validation (suggest 5MB limit)
5. **During Implementation**: Implement ContentResolver for Android URIs
6. **Post-Implementation**: Add retry logic with exponential backoff
7. **Post-Implementation**: Consider adding request/response caching

## Open Questions
- Does the app use Compose file picker, or custom implementation?
- Are there existing permissions for file access, or do we need to request?
- Should we support real-time file upload progress indicators?
- What's the expected behavior when a platform doesn't support images?
- Should we add a "test connection" feature for platform settings?

## Useful References
- Anthropic API Docs: https://docs.anthropic.com/claude/reference
- OpenAI API Docs: https://platform.openai.com/docs/api-reference
- Gemini API Docs: https://ai.google.dev/api/rest
- Ollama API Docs: https://github.com/ollama/ollama/blob/main/docs/api.md
- Ktor SSE Plugin: https://ktor.io/docs/sse.html
