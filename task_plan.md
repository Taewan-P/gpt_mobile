# Task Plan: Complete Chat API Implementation & Settings Screen Refactoring

## Goal
Implement the core `completeChat` method in ChatRepositoryImpl with multi-platform support (OpenAI-compatible, Anthropic, Google, etc.) and refactor the settings screen to use the new PlatformV2 database structure instead of the old DataStore-based Platform DTO.

## Context
- App is undergoing complete V2 refactoring from hardcoded 5-platform system to dynamic platform architecture
- Migration system already implemented and working
- Anthropic API implementation exists as reference pattern
- Settings screen currently uses old Platform DTO with DataStore, needs migration to PlatformV2

## Phases

### Phase 1: Design completeChat Implementation Strategy ⏳ `pending`
**Goal**: Plan the architecture for multi-platform API handling

**Tasks**:
- [ ] Review existing Anthropic API implementation pattern
- [ ] Design platform routing logic (ClientType → API client selection)
- [ ] Plan file/image handling strategy (reading files, base64 encoding)
- [ ] Define API client interface for different platforms
- [ ] Determine message transformation approach (MessageV2 → platform-specific format)

**Deliverables**:
- Architecture design for API client factory pattern
- Message transformation strategy documented

---

### Phase 2: Implement OpenAI-Compatible API Client ⏳ `pending`
**Goal**: Create OpenAI API client to support OpenAI, Groq, and custom platforms

**Tasks**:
- [ ] Create `OpenAIAPI` interface
- [ ] Implement `OpenAIAPIImpl` with streaming support
- [ ] Define OpenAI request/response DTOs (ChatCompletionRequest, etc.)
- [ ] Implement image support (base64 encoding)
- [ ] Handle file attachments (if supported by OpenAI API)
- [ ] Test streaming response parsing

**Files to Create**:
- `data/network/OpenAIAPI.kt`
- `data/network/OpenAIAPIImpl.kt`
- `data/dto/openai/request/*.kt`
- `data/dto/openai/response/*.kt`

**Dependencies**: Phase 1 complete

---

### Phase 3: Implement Google Gemini API Client ⏳ `pending`
**Goal**: Create API client for Google Gemini

**Note**: ✅ Ollama and OpenRouter use OpenAI-compatible APIs - no separate clients needed!

**Tasks**:
- [ ] Create Google Gemini API client (`GoogleAPI`, `GoogleAPIImpl`)
- [ ] Define Gemini-specific DTOs (request/response)
- [ ] Implement image support (base64 encoding)
- [ ] Handle Gemini streaming response format
- [ ] Test with actual Gemini API

**Files to Create**:
- `data/network/GoogleAPI.kt`, `GoogleAPIImpl.kt`
- `data/dto/google/request/*.kt`
- `data/dto/google/response/*.kt`

**Dependencies**: Phase 2 complete

---

### Phase 4: Implement File Handling Utilities ⏳ `pending`
**Goal**: Create utilities to read and process files from URIs/paths

**Tasks**:
- [ ] Implement file reading from URI/path (Android content resolver)
- [ ] Create base64 encoding utility for images
- [ ] Implement document file reading (if needed)
- [ ] Add file size validation
- [ ] Handle file access errors gracefully

**Files to Create/Modify**:
- `util/FileUtils.kt` (new)
- Update `ChatRepositoryImpl.kt` with file reading logic

**Dependencies**: Phase 1 complete

---

### Phase 5: Implement completeChat Core Logic ⏳ `pending`
**Goal**: Wire up all components in ChatRepositoryImpl.completeChat

**Tasks**:
- [ ] Implement platform routing (switch on ClientType)
- [ ] Transform MessageV2 list to platform-specific message format
- [ ] Read and encode files from MessageV2.files list
- [ ] Call appropriate API client based on platform
- [ ] Transform API responses to ApiState flow
- [ ] Handle errors and edge cases
- [ ] Add logging for debugging

**File to Modify**:
- `data/repository/ChatRepositoryImpl.kt`

**Dependencies**: Phases 2, 3, 4 complete

---

### Phase 6: Update Dependency Injection ⏳ `pending`
**Goal**: Register all API clients in Hilt DI container

**Tasks**:
- [ ] Add OpenAI API provider to NetworkModule
- [ ] Add Google API provider to NetworkModule
- [ ] Add Ollama API provider to NetworkModule
- [ ] Add OpenRouter API provider to NetworkModule
- [ ] Inject API clients into ChatRepositoryImpl

**File to Modify**:
- `di/NetworkModule.kt`

**Dependencies**: Phases 2, 3 complete

---

### Phase 7: Refactor SettingViewModel to Use PlatformV2 ⏳ `pending`
**Goal**: Update SettingViewModel to work with database-backed PlatformV2 instead of DataStore

**Tasks**:
- [ ] Replace `MutableStateFlow<List<Platform>>` with `StateFlow<List<PlatformV2>>`
- [ ] Fetch platforms from SettingRepository.fetchPlatformV2s()
- [ ] Update all platform update methods (toggleAPI, updateURL, etc.) to work with PlatformV2
- [ ] Add methods for creating/deleting platforms
- [ ] Remove DataStore-based logic
- [ ] Add sorting/filtering for platform list

**File to Modify**:
- `presentation/ui/setting/SettingViewModel.kt`

**Dependencies**: None (parallel with API implementation)

---

### Phase 8: Refactor SettingScreen UI ⏳ `pending`
**Goal**: Update Settings screen to display dynamic platform list from database

**Tasks**:
- [ ] Replace `ApiType.entries` iteration with `platformState` from ViewModel
- [ ] Display platform.name instead of hardcoded enum names
- [ ] Add "Add Platform" button to create new platforms
- [ ] Add delete/edit actions for platforms
- [ ] Handle empty platform list state
- [ ] Update navigation to pass platform UID instead of ApiType

**File to Modify**:
- `presentation/ui/setting/SettingScreen.kt`

**Dependencies**: Phase 7 complete

---

### Phase 9: Refactor PlatformSettingScreen ⏳ `pending`
**Goal**: Update per-platform settings screen to work with PlatformV2

**Tasks**:
- [ ] Change navigation parameter from ApiType to platform UID (String)
- [ ] Fetch PlatformV2 by UID from database
- [ ] Update all form fields to work with PlatformV2 properties
- [ ] Add ClientType selector dropdown
- [ ] Add platform name editor
- [ ] Add reasoning toggle (for extended thinking models)
- [ ] Add timeout configuration
- [ ] Update save logic to persist PlatformV2 to database
- [ ] Add delete platform functionality

**File to Modify**:
- `presentation/ui/setting/PlatformSettingScreen.kt`

**Dependencies**: Phases 7, 8 complete

---

### Phase 10: Update Navigation Graph ⏳ `pending`
**Goal**: Update navigation routes to pass platform UID instead of ApiType

**Tasks**:
- [ ] Update navigation route definition for PlatformSettingScreen
- [ ] Change parameter from `ApiType` to `String` (UID)
- [ ] Update all navigation calls to pass platform.uid
- [ ] Handle new platform creation route (UID = "new" or 0)

**File to Modify**:
- Navigation configuration file (likely in `presentation/navigation/`)

**Dependencies**: Phase 9 complete

---

### Phase 11: Testing & Validation ⏳ `pending`
**Goal**: Verify all implementations work correctly

**Tasks**:
- [ ] Test OpenAI API streaming with images
- [ ] Test Anthropic API with files
- [ ] Test Google/Ollama APIs
- [ ] Test settings screen CRUD operations
- [ ] Test platform creation/deletion
- [ ] Test migration from old settings
- [ ] Test error handling for invalid API keys
- [ ] Test file encoding/decoding

**Dependencies**: All previous phases complete

---

## Success Criteria
- [ ] `completeChat` method fully implemented with streaming support
- [ ] All major platforms supported (OpenAI, Anthropic, Google, Ollama, custom)
- [ ] Image and file attachments work correctly
- [ ] Settings screen uses PlatformV2 from database
- [ ] Users can create/edit/delete platforms dynamically
- [ ] No hardcoded ApiType dependencies in settings UI
- [ ] All existing migrations continue to work
- [ ] App builds without errors

## Errors Encountered
| Error | Attempt | Resolution |
|-------|---------|------------|
| | | |

## Open Questions
- [ ] Should we support document files (PDF, DOCX) in the initial implementation, or just images?
- [ ] How should we handle platforms that don't support image attachments?
- [ ] Should we add file size limits for attachments?
- [ ] Do we need a file picker UI update as well, or is that already handled?

## Notes
- Anthropic API implementation serves as reference pattern for streaming
- Migration system already handles old Platform → PlatformV2 conversion
- MigrateScreen implementation provides excellent reference for V2 patterns
- Keep backward compatibility during transition period
