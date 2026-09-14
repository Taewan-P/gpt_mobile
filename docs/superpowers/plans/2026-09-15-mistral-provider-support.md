# Mistral Provider Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a first-class Mistral Platform that uses a dedicated streaming session, validates setup, discovers model capacity, and preserves the existing OpenAI-compatible wire machinery.

**Architecture:** Persist `ClientType.MISTRAL` with provider-specific defaults and UI copy. A dedicated `MistralAdapter` delegates to one private Chat Completions session function shared with `OpenAICompatibleAdapter`; context discovery and inline attachment safety reuse existing provider infrastructure.

**Tech Stack:** Kotlin, Coroutines/Flow, Ktor CIO, kotlinx.serialization, Jetpack Compose Material 3, Room, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-15-mistral-provider-support-design.md`

## Global Constraints

- Default name is exactly `Mistral`.
- Default API base URL is exactly `https://api.mistral.ai/v1/`.
- Default model is exactly `mistral-large-latest`.
- Mistral has a dedicated adapter/session seam but reuses Chat Completions DTOs, `OpenAIAPI`, attachment encoding, and event assembly.
- Do not add a Mistral SDK, new HTTP client, duplicate DTO stack, database migration, model catalog, or provider-preset abstraction.
- Existing Custom and OpenAI-compatible Platforms remain unchanged.
- New Mistral Platforms require a nonblank API key and a trimmed API URL ending in `/v1/`; the app never silently rewrites the URL.
- Mistral images stay inline and use the existing 12 MiB ceiling.
- A missing or invalid `max_context_length` remains unknown and uses the existing manual context-window flow.
- Amazon Bedrock, Gemini Enterprise Agent Platform, Mistral Agents, Connectors, OCR, prompt-cache controls, and non-stream transport work are out of scope.
- Only the base `values/strings.xml` resource file changes; untranslated locales fall back to it.
- Device verification targets only `emulator-5554`, installs in place, and never clears or resets app data.
- Follow red-green-refactor: each production behavior is preceded by a focused test that is run and observed failing for the expected reason.

---

### Task 1: Add Mistral identity and its dedicated streaming session

**Files:**
- Create: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/ModelConstantsTest.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/model/ClientType.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/ModelConstants.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/agent/provider/ProviderAdapters.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/repository/ChatRepositoryImpl.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/util/MapStringResources.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setup/SetupPlatformTypeScreen.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setup/SetupPlatformWizardScreen.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/AddPlatformScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/agent/provider/ProviderAdaptersTest.kt`

**Interfaces:**
- Consumes: existing `OpenAIAPI.streamChatCompletion`, `ProviderAttachmentEncoder.openAIChatMessages`, `ChatCompletionRequest`, `ChatCompletionsEventAssembler`, and `AgentProviderSession`.
- Produces: `ClientType.MISTRAL`; `ModelConstants.MISTRAL_API_URL`; `ModelConstants.MISTRAL_DEFAULT_MODEL`; `MistralAdapter.openSession(turns, platform)`; private shared `openChatCompletionsSession(api, initialMessages, platform)`.

- [ ] **Step 1: Write failing identity/default tests**

Create `ModelConstantsTest.kt`:

```kotlin
package dev.chungjungsoo.gptmobile.data

import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.util.getClientTypeDisplayName
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelConstantsTest {
    @Test
    fun `mistral defaults and display mapping`() {
        assertEquals("Mistral", ModelConstants.defaultPlatformName(ClientType.MISTRAL))
        assertEquals("https://api.mistral.ai/v1/", ModelConstants.defaultApiUrl(ClientType.MISTRAL))
        assertEquals("mistral-large-latest", ModelConstants.defaultModel(ClientType.MISTRAL))
        assertEquals("Mistral", getClientTypeDisplayName(ClientType.MISTRAL))
    }
}
```

- [ ] **Step 2: Write failing dedicated-session tests**

In `ProviderAdaptersTest.kt`, add:

```kotlin
@Test
fun `mistral adapter streams the initial chat completions request`() = runBlocking {
    val api = FakeOpenAIAPI(chatRounds = ArrayDeque(listOf(emptyFlow())))
    val platform = platform(ClientType.MISTRAL)

    MistralAdapter(api, attachmentEncoder())
        .openSession(turns(), platform)
        .streamRound(listOf(definition), emptyList())
        .toList()

    val request = api.chatRequests.single()
    assertEquals("model-test", request.model)
    assertTrue(request.stream)
    assertEquals("weather", request.tools!!.single().function.name)
    assertEquals(ProviderRequestConfig("https://provider.example/v1", "secret"), api.configs.single())
}

@Test
fun `mistral adapter replays exact tool call id on the next round`() = runBlocking {
    val api = FakeOpenAIAPI(
        chatRounds = ArrayDeque(
            listOf(
                flowOf(
                    ChatCompletionChunk(
                        choices = listOf(
                            Choice(
                                index = 0,
                                delta = Delta(
                                    toolCalls = listOf(
                                        ChatToolCallDelta(
                                            index = 0,
                                            id = "call_exact",
                                            function = ChatFunctionDelta("weather", "{\"city\":\"Tokyo\"}")
                                        )
                                    )
                                ),
                                finishReason = "tool_calls"
                            )
                        )
                    )
                ),
                flowOf(
                    ChatCompletionChunk(
                        choices = listOf(Choice(0, Delta(content = "done"), finishReason = "stop"))
                    )
                )
            )
        )
    )
    val session = MistralAdapter(api, attachmentEncoder())
        .openSession(turns(), platform(ClientType.MISTRAL))

    assertEquals(listOf(call, ProviderEvent.Completed), session.streamRound(listOf(definition), emptyList()).toList())
    assertEquals(
        listOf(ProviderEvent.TextDelta("done"), ProviderEvent.Completed),
        session.streamRound(
            listOf(definition),
            listOf(AgentToolExchange(listOf(call), listOf(result)))
        ).toList()
    )
    val continuation = api.chatRequests.last().messages.takeLast(2)
    assertEquals("call_exact", continuation[0].toolCalls!!.single().id)
    assertEquals("call_exact", continuation[1].toolCallId)
}
```

- [ ] **Step 3: Run the focused tests and verify RED**

Run:

```bash
ANDROID_HOME=/Users/taewanpark/Library/Android/sdk ANDROID_SDK_ROOT=/Users/taewanpark/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.data.ModelConstantsTest" --tests "dev.chungjungsoo.gptmobile.data.agent.provider.ProviderAdaptersTest"
```

Expected: compilation fails because `ClientType.MISTRAL` and `MistralAdapter` do not exist.

- [ ] **Step 4: Implement identity, defaults, and exhaustive display branches**

Add `MISTRAL` after `ANTHROPIC` in `ClientType`. Add:

```kotlin
const val MISTRAL_DEFAULT_MODEL = "mistral-large-latest"
const val MISTRAL_API_URL = "https://api.mistral.ai/v1/"
```

Map `ClientType.MISTRAL` to `"Mistral"`, `MISTRAL_API_URL`, and `MISTRAL_DEFAULT_MODEL` in the three `ModelConstants` functions, and to `"Mistral"` in `getClientTypeDisplayName`.

Add these base resources:

```xml
<string name="mistral" translatable="false">Mistral</string>
<string name="mistral_description">Chat with Mistral models through the Mistral API.</string>
<string name="client_type_mistral_desc">Mistral OpenAI-compatible Chat Completions API</string>
```

Add Mistral to `platformTypes`, map its Add Platform description, and map `getApiHelpUrl(ClientType.MISTRAL)` to:

```text
https://docs.mistral.ai/getting-started/quickstarts/developer/first-api-request
```

- [ ] **Step 5: Extract one shared Chat Completions session function**

In `ProviderAdapters.kt`, move only the non-Groq request/stream body into:

```kotlin
private fun openChatCompletionsSession(
    api: OpenAIAPI,
    initialMessages: List<ChatMessage>,
    platform: PlatformV2
): AgentProviderSession
```

The returned session must build each round from `initialMessages + exchanges.flatMap { it.toChatMessages() }`, create `ChatFunctionTool` values from the supplied tool definitions, call `api.streamChatCompletion` with `ProviderRequestConfig(platform.apiUrl, platform.token)`, preserve optional usage, and emit exactly one terminal `ProviderEvent.Completed` when no failure occurred.

Keep Groq's native branch and `GroqReasoningParser` inside `OpenAICompatibleAdapter`. Its non-Groq branch returns the shared session.

- [ ] **Step 6: Add the dedicated Mistral adapter and repository dispatch**

Add:

```kotlin
class MistralAdapter @Inject constructor(
    private val openAIAPI: OpenAIAPI,
    private val attachmentEncoder: ProviderAttachmentEncoder
) {
    suspend fun openSession(
        turns: List<ConversationTurn>,
        platform: PlatformV2
    ): AgentProviderSession = openChatCompletionsSession(
        openAIAPI,
        attachmentEncoder.openAIChatMessages(turns, platform.systemPrompt),
        platform
    )
}
```

Construct `mistralAdapter` beside the existing adapters in `ChatRepositoryImpl`, then add the explicit dispatch arm:

```kotlin
ClientType.MISTRAL -> mistralAdapter.openSession(preparedTurns, platform)
```

Do not add Mistral to the Groq/Ollama/OpenRouter/Custom arm.

- [ ] **Step 7: Run focused tests and compile**

Run the Step 3 command again, then:

```bash
ANDROID_HOME=/Users/taewanpark/Library/Android/sdk ANDROID_SDK_ROOT=/Users/taewanpark/Library/Android/sdk ./gradlew :app:compileDebugKotlin
```

Expected: focused tests and compilation pass; Kotlin reports no unhandled `ClientType.MISTRAL` branch.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/model/ClientType.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/ModelConstants.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/agent/provider/ProviderAdapters.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/repository/ChatRepositoryImpl.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/util/MapStringResources.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setup/SetupPlatformTypeScreen.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setup/SetupPlatformWizardScreen.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/AddPlatformScreen.kt app/src/main/res/values/strings.xml app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/ModelConstantsTest.kt app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/agent/provider/ProviderAdaptersTest.kt
git commit -m "feat: add Mistral provider session"
```

### Task 2: Enforce Mistral creation validation in both setup flows

**Files:**
- Create: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/common/PlatformInputValidation.kt`
- Create: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/presentation/common/PlatformInputValidationTest.kt`
- Create: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/AddPlatformScreenTest.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setup/SetupViewModelV2.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setup/SetupPlatformWizardScreen.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/AddPlatformScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setup/SetupViewModelV2Test.kt`

**Interfaces:**
- Consumes: `ClientType.MISTRAL`.
- Produces: `isPlatformApiUrlValid(clientType, apiUrl)`, `isPlatformApiKeyValid(clientType, apiKey)`, and testable `canSavePlatform(...)` used by Add Platform.

- [ ] **Step 1: Write failing pure validation tests**

Create `PlatformInputValidationTest.kt` with assertions:

```kotlin
@Test
fun `Mistral requires a trimmed URL ending with v1 slash`() {
    assertTrue(isPlatformApiUrlValid(ClientType.MISTRAL, " https://api.mistral.ai/v1/ "))
    assertFalse(isPlatformApiUrlValid(ClientType.MISTRAL, "https://api.mistral.ai/"))
    assertFalse(isPlatformApiUrlValid(ClientType.MISTRAL, "https://api.mistral.ai/v1"))
}

@Test
fun `Mistral requires a nonblank key while OpenAI remains optional`() {
    assertFalse(isPlatformApiKeyValid(ClientType.MISTRAL, "   "))
    assertTrue(isPlatformApiKeyValid(ClientType.MISTRAL, "secret"))
    assertTrue(isPlatformApiKeyValid(ClientType.OPENAI, ""))
}
```

- [ ] **Step 2: Write failing setup ViewModel tests**

In `SetupViewModelV2Test.kt`, add tests that:

1. select Mistral and assert the approved name, URL, and model prefills;
2. set the basics step URL to `https://api.mistral.ai/` and assert `canProceedFromStep(WIZARD_STEP_BASICS)` is false;
3. set a trimmed `https://api.mistral.ai/v1/` and assert the basics step is true;
4. advance to the API-key step, assert blank/whitespace is false, set `secret`, then assert true;
5. select OpenAI and assert its blank API-key step remains true;
6. call `savePlatform` with an invalid Mistral URL or blank key and assert `RecordingSettingRepository.addedPlatforms` remains empty;
7. call `savePlatform` with valid values and assert the saved Platform retains `ClientType.MISTRAL`, the exact URL, model, and token.

- [ ] **Step 3: Write failing Add Platform save-gate tests**

Extract `canSavePlatform` from the existing inline `isSaveEnabled` expression and test in `AddPlatformScreenTest.kt`:

```kotlin
@Test
fun `Mistral save gate requires v1 URL and key`() {
    assertFalse(canSavePlatform(false, ClientType.MISTRAL, "Mistral", "https://api.mistral.ai/", "secret", "mistral-large-latest", false))
    assertFalse(canSavePlatform(false, ClientType.MISTRAL, "Mistral", "https://api.mistral.ai/v1/", "", "mistral-large-latest", false))
    assertTrue(canSavePlatform(false, ClientType.MISTRAL, "Mistral", "https://api.mistral.ai/v1/", "secret", "mistral-large-latest", false))
}

@Test
fun `Custom save gate keeps API key optional`() {
    assertTrue(canSavePlatform(false, ClientType.CUSTOM, "Custom", "https://example.com/v1/", "", "model", false))
}
```

- [ ] **Step 4: Run focused tests and verify RED**

```bash
ANDROID_HOME=/Users/taewanpark/Library/Android/sdk ANDROID_SDK_ROOT=/Users/taewanpark/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.presentation.common.PlatformInputValidationTest" --tests "dev.chungjungsoo.gptmobile.presentation.ui.setup.SetupViewModelV2Test" --tests "dev.chungjungsoo.gptmobile.presentation.ui.setting.AddPlatformScreenTest"
```

Expected: tests fail because the predicates and Mistral-specific gates do not exist.

- [ ] **Step 5: Implement the two shared predicates**

Create:

```kotlin
internal fun isPlatformApiUrlValid(
    clientType: ClientType?,
    apiUrl: String
): Boolean = when (clientType) {
    ClientType.LITERT_LM -> true
    ClientType.MISTRAL -> apiUrl.trim().endsWith("/v1/")
    else -> apiUrl.isNotBlank()
}

internal fun isPlatformApiKeyValid(
    clientType: ClientType?,
    apiKey: String
): Boolean = clientType != ClientType.MISTRAL || apiKey.isNotBlank()
```

- [ ] **Step 6: Wire setup wizard progression and persistence guards**

Combine `_apiKey` and `_model` as a pair so both changes recalculate `canProceed`:

```kotlin
combine(_apiKey, _model) { apiKey, model -> apiKey to model }
```

Pass both values into `canProceedFromStep`. Use `isPlatformApiUrlValid` on the basics step and `isPlatformApiKeyValid` on the API-key step. At the start of `savePlatform`, reject blank names/models, invalid URLs, and invalid keys before setting `SaveStatus.Saving`.

- [ ] **Step 7: Wire Add Platform save and inline errors**

Replace the inline save expression with this top-level function, using the same two shared predicates for hosted Platforms:

```kotlin
internal fun canSavePlatform(
    isSaving: Boolean,
    clientType: ClientType?,
    platformName: String,
    apiUrl: String,
    apiKey: String,
    model: String,
    canSaveLocalModel: Boolean
): Boolean
```

It returns false while saving or when the name is blank; for LiteRT-LM it returns `canSaveLocalModel`; otherwise it requires a nonblank model plus valid URL and key predicates. Guard `savePlatform()` itself with the same result so a direct call cannot bypass the disabled button.

Add:

```xml
<string name="mistral_api_url_requirement">Mistral API URLs must end with /v1/.</string>
```

In both creation screens, set URL `isError` when a nonblank Mistral value fails the URL predicate and show `mistral_api_url_requirement`. Set API-key `isError` for blank Mistral keys and show `field_required`; retain existing supporting copy otherwise.

- [ ] **Step 8: Run focused tests and verify GREEN**

Run the Step 4 command. Expected: all focused validation tests pass.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/common/PlatformInputValidation.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setup/SetupViewModelV2.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setup/SetupPlatformWizardScreen.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/AddPlatformScreen.kt app/src/main/res/values/strings.xml app/src/test/kotlin/dev/chungjungsoo/gptmobile/presentation/common/PlatformInputValidationTest.kt app/src/test/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setup/SetupViewModelV2Test.kt app/src/test/kotlin/dev/chungjungsoo/gptmobile/presentation/ui/setting/AddPlatformScreenTest.kt
git commit -m "feat: validate Mistral platform setup"
```

### Task 3: Discover Mistral capacity and bound inline attachments

**Files:**
- Create: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/context/HttpRemoteContextWindowLookupTest.kt`
- Create: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/context/ProviderContextPolicyTest.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/context/RemoteContextWindowParser.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/context/HttpRemoteContextWindowLookup.kt`
- Modify: `app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/context/ProviderContextPolicy.kt`
- Test: `app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/context/RemoteContextWindowParserTest.kt`

**Interfaces:**
- Consumes: `ClientType.MISTRAL`, existing `modelsUrl`, `encodedModel`, `get`, and `NetworkClient`.
- Produces: `RemoteContextWindowParser.mistralMaxContextLength(body, model)`; Mistral lookup branch; 12 MiB Mistral inline limit.

- [ ] **Step 1: Write failing parser and policy tests**

Add:

```kotlin
@Test
fun `mistral uses documented max_context_length`() {
    val body = """{"id":"mistral-large-latest","max_context_length":131072,"capabilities":{"vision":true}}"""
    assertEquals(131072, RemoteContextWindowParser.mistralMaxContextLength(body, "mistral-large-latest"))
    assertNull(
        RemoteContextWindowParser.mistralMaxContextLength(
            """{"id":"mistral-large-latest","max_tokens":8192}""",
            "mistral-large-latest"
        )
    )
}
```

Create `ProviderContextPolicyTest.kt`:

```kotlin
@Test
fun `mistral policy applies 12 MiB inline ceiling`() {
    assertEquals(
        12L * 1024 * 1024,
        ProviderContextPolicy.forClientType(ClientType.MISTRAL).maxInlineAttachmentBytes
    )
}
```

- [ ] **Step 2: Write a failing authenticated lookup test**

Create a loopback `HttpServer` test using the existing `NetworkClient(CIO)` pattern. Configure a Mistral Platform with:

```kotlin
apiUrl = "http://127.0.0.1:${server.address.port}/v1/"
token = "mistral-test-key"
model = "team/model name"
```

Capture and assert:

```kotlin
assertEquals("GET", requestMethod)
assertEquals("/v1/models/team/model%20name", rawPath)
assertEquals("Bearer mistral-test-key", authorization)
assertEquals(131072, detected)
```

Return `{"id":"team/model name","max_context_length":131072}`. Stop the server and close the Ktor client in `finally`.

- [ ] **Step 3: Run focused tests and verify RED**

```bash
ANDROID_HOME=/Users/taewanpark/Library/Android/sdk ANDROID_SDK_ROOT=/Users/taewanpark/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests "dev.chungjungsoo.gptmobile.data.context.RemoteContextWindowParserTest" --tests "dev.chungjungsoo.gptmobile.data.context.HttpRemoteContextWindowLookupTest" --tests "dev.chungjungsoo.gptmobile.data.context.ProviderContextPolicyTest"
```

Expected: parser/lookup/policy tests fail because Mistral support is absent.

- [ ] **Step 4: Implement parser, direct lookup, and policy**

Add:

```kotlin
fun mistralMaxContextLength(body: String, model: String): Int? {
    val match = matchingModel(body, model) ?: return null
    return positiveInt(match["max_context_length"])
}
```

Add `ClientType.MISTRAL -> fetchMistral(platform)` to lookup. `fetchMistral` performs only the direct authenticated `modelsUrl(platform, platform.model)` GET and parses `mistralMaxContextLength`; do not add a list fallback or hardcoded model capacity.

Add `ClientType.MISTRAL` to the existing inline-limit arm in `ProviderContextPolicy.forClientType`. Leave `AttachmentUploadCoordinator.ensureMessageAttachmentsForPlatform` unchanged so Mistral attachments remain inline.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run the Step 3 command. Expected: all focused context and policy tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/context/RemoteContextWindowParser.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/context/HttpRemoteContextWindowLookup.kt app/src/main/kotlin/dev/chungjungsoo/gptmobile/data/context/ProviderContextPolicy.kt app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/context/RemoteContextWindowParserTest.kt app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/context/HttpRemoteContextWindowLookupTest.kt app/src/test/kotlin/dev/chungjungsoo/gptmobile/data/context/ProviderContextPolicyTest.kt
git commit -m "feat: add Mistral context metadata"
```

## Final verification

Run fresh, after all task reviews and fixes:

```bash
ANDROID_HOME=/Users/taewanpark/Library/Android/sdk ANDROID_SDK_ROOT=/Users/taewanpark/Library/Android/sdk ./gradlew :app:testDebugUnitTest :app:compileDebugKotlin :app:lintDebug :app:assembleDebug
```

Install only on the approved emulator, in place:

```bash
android run --debug --device=emulator-5554 --apks=app/build/outputs/apk/debug/app-debug.apk --activity=dev.chungjungsoo.gptmobile.presentation.ui.main.MainActivity
```

Use `android layout --device=emulator-5554 --pretty` and a screenshot to verify: Mistral is listed, defaults are exact, an invalid URL blocks save with inline copy, a blank key blocks save, valid values persist, and only the temporary test Platform is removed afterward. Do not clear, wipe, uninstall, or reset the application.
