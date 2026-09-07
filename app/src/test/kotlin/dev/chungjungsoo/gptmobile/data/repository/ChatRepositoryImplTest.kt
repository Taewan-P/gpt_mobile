package dev.chungjungsoo.gptmobile.data.repository

import android.content.ContextWrapper
import dev.chungjungsoo.gptmobile.data.agent.provider.LiteRtLmAdapter
import dev.chungjungsoo.gptmobile.data.agent.tool.AgentToolResolver
import dev.chungjungsoo.gptmobile.data.agent.tool.McpClientManager
import dev.chungjungsoo.gptmobile.data.agent.tool.McpOAuthClient
import dev.chungjungsoo.gptmobile.data.agent.tool.McpOAuthCoordinator
import dev.chungjungsoo.gptmobile.data.catalog.CatalogCapabilities
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.context.ContextBuilder
import dev.chungjungsoo.gptmobile.data.database.dao.AgentToolBindingWithConnection
import dev.chungjungsoo.gptmobile.data.database.dao.ToolConnectionDao
import dev.chungjungsoo.gptmobile.data.database.entity.AgentToolBinding
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionAuthType
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionType
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEvent
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEventStatus
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.MessageRequest
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.MessageResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.google.request.GenerateContentRequest
import dev.chungjungsoo.gptmobile.data.dto.google.response.Candidate
import dev.chungjungsoo.gptmobile.data.dto.google.response.GenerateContentResponse
import dev.chungjungsoo.gptmobile.data.dto.google.response.PromptFeedback
import dev.chungjungsoo.gptmobile.data.dto.groq.request.GroqChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.groq.response.GroqChatCompletionChunk
import dev.chungjungsoo.gptmobile.data.dto.groq.response.GroqChoice
import dev.chungjungsoo.gptmobile.data.dto.groq.response.GroqDelta
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponsesRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ChatCompletionChunk
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ChatFunctionDelta
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ChatToolCallDelta
import dev.chungjungsoo.gptmobile.data.dto.openai.response.Choice
import dev.chungjungsoo.gptmobile.data.dto.openai.response.Delta
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponsesStreamEvent
import dev.chungjungsoo.gptmobile.data.localruntime.FakeLocalRuntime
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntime
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntimeEvent
import dev.chungjungsoo.gptmobile.data.localruntime.ScriptedToolInvocation
import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.GeminiSafetySettings
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPI
import dev.chungjungsoo.gptmobile.data.network.GoogleAPI
import dev.chungjungsoo.gptmobile.data.network.GroqAPI
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.network.OpenAIAPI
import dev.chungjungsoo.gptmobile.data.network.ProviderRequestConfig
import dev.chungjungsoo.gptmobile.data.network.UploadedProviderFile
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import io.ktor.client.engine.cio.CIO
import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryImplTest {

    @Test(expected = IllegalStateException::class)
    fun `blank response input without encodable parts throws`() {
        validateResponseInputPartsOrThrow("", 0, 42)
    }

    @Test
    fun `response input with text does not throw when image encoding fails`() {
        validateResponseInputPartsOrThrow("hello", 0, 42)
    }

    @Test
    fun `response input with encoded image parts does not throw when text is blank`() {
        validateResponseInputPartsOrThrow("", 1, 42)
    }

    @Test
    fun `complete chat emits loading before request preparation`() = runBlocking {
        val repository = createRepository()
        val firstState = withTimeout(100) {
            repository.completeChat(
                userMessages = listOf(MessageV2(content = "Hi", platformType = null)),
                assistantMessages = emptyList(),
                platform = customPlatform(),
                runId = "test-run"
            ).first()
        }

        assertEquals(ApiState.Loading, firstState)
    }

    @Test
    fun `groq path uses groq api and emits parsed reasoning`() = runBlocking {
        val groqAPI = FakeGroqAPI(
            flowOf(
                GroqChatCompletionChunk(
                    choices = listOf(
                        GroqChoice(
                            index = 0,
                            delta = GroqDelta(
                                reasoning = "Plan",
                                content = "Answer"
                            )
                        )
                    )
                )
            )
        )
        val openAIAPI = RecordingOpenAIAPI()
        val repository = createRepository(
            groqAPI = groqAPI,
            openAIAPI = openAIAPI
        )

        val states = repository.completeChat(
            userMessages = listOf(MessageV2(content = "Hi", platformType = null)),
            assistantMessages = emptyList(),
            platform = groqPlatform(reasoning = true, model = "qwen/qwen3-32b"),
            runId = "test-run"
        ).toList()

        assertEquals(
            listOf(
                ApiState.Loading,
                ApiState.Thinking("Plan"),
                ApiState.Success("Answer"),
                ApiState.Done
            ),
            states
        )
        assertEquals(1, groqAPI.streamCalls)
        assertEquals(0, openAIAPI.streamChatCompletionCalls)
        assertEquals(8_192, groqAPI.lastRequest?.maxCompletionTokens)
    }

    @Test
    fun `litert lm path uses local runtime and streams thinking and text`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(
                listOf(
                    LocalRuntimeEvent.ThinkingDelta("plan"),
                    LocalRuntimeEvent.TextDelta("hello"),
                    LocalRuntimeEvent.Done
                )
            )
        }
        val repository = createRepository(
            localRuntime = runtime,
            localModelRepository = FakeLocalModelRepository(
                downloadedPaths = mapOf("gemma3-1b-it" to "/models/gemma.litertlm")
            )
        )

        val states = repository.completeChat(
            userMessages = listOf(MessageV2(content = "Hi", platformType = null)),
            assistantMessages = emptyList(),
            platform = localPlatform(),
            runId = "local-run"
        ).toList()

        assertEquals(
            listOf(
                ApiState.Loading,
                ApiState.Notice(LiteRtLmAdapter.DEFAULT_LOADING_MODEL),
                ApiState.Thinking("plan"),
                ApiState.Success("hello"),
                ApiState.Done
            ),
            states
        )
        assertEquals(listOf("Hi"), runtime.sendMessageCalls)
        assertEquals(1, runtime.loadEngineCalls.size)
    }

    @Test
    fun `litert lm tool capable run records engine owned tool calls on the timeline`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            scriptedEvents = listOf(
                listOf(
                    LocalRuntimeEvent.TextDelta("before"),
                    LocalRuntimeEvent.TextDelta("after"),
                    LocalRuntimeEvent.Done
                )
            )
            scriptedToolInvocations = listOf(
                listOf(ScriptedToolInvocation("current_date", "{}"))
            )
        }
        val traceDao = RecordingToolEventDao()
        val repository = createRepository(
            localRuntime = runtime,
            localModelRepository = FakeLocalModelRepository(
                downloadedPaths = mapOf("gemma3-1b-it" to "/models/gemma.litertlm")
            ),
            modelCatalogRepository = FakeModelCatalogRepository(
                listOf(CatalogEntry(id = "gemma3-1b-it", capabilities = CatalogCapabilities(tools = true)))
            ),
            toolEventRecorder = ToolEventRecorder(traceDao.asDao())
        )

        val states = repository.completeChat(
            userMessages = listOf(MessageV2(content = "Hi", platformType = null)),
            assistantMessages = emptyList(),
            platform = localPlatform(),
            runId = "run-local-tool"
        ).toList()

        assertEquals(
            listOf(
                ApiState.Loading,
                ApiState.Notice(LiteRtLmAdapter.DEFAULT_LOADING_MODEL),
                ApiState.Success("before"),
                ApiState.ToolCall(toolSequence = 0),
                ApiState.Success("after"),
                ApiState.Done
            ),
            states
        )
        assertEquals(1, runtime.sendMessageCalls.size)
        assertEquals(listOf("current_date"), runtime.createConversationCalls.single().tools.map { it.name })
        assertTrue(runtime.createConversationCalls.single().isConstrainedDecodingEnabled)
        val event = traceDao.events.single()
        assertEquals("run-local-tool", event.runId)
        assertEquals("current_date", event.toolName)
        assertEquals(ToolEventStatus.COMPLETED, event.status)
        assertFalse(event.isError)
    }

    @Test
    fun `groq token limit reports failure instead of completing with only thinking`() = runBlocking {
        val groqAPI = FakeGroqAPI(
            flowOf(
                GroqChatCompletionChunk(
                    choices = listOf(
                        GroqChoice(
                            index = 0,
                            delta = GroqDelta(reasoning = "Still reasoning"),
                            finishReason = "length"
                        )
                    )
                )
            )
        )
        val repository = createRepository(groqAPI = groqAPI)

        val states = repository.completeChat(
            userMessages = listOf(MessageV2(content = "Hi", platformType = null)),
            assistantMessages = emptyList(),
            platform = groqPlatform(reasoning = true, model = "qwen/qwen3.6-27b"),
            runId = "test-run"
        ).toList()

        assertEquals(
            listOf(
                ApiState.Loading,
                ApiState.Thinking("Still reasoning"),
                ApiState.Error("Groq reached the model output limit before producing a final answer."),
                ApiState.Done
            ),
            states
        )
    }

    @Test
    fun `groq raw think fallback populates thinking state`() = runBlocking {
        val groqAPI = FakeGroqAPI(
            flowOf(
                GroqChatCompletionChunk(
                    choices = listOf(
                        GroqChoice(
                            index = 0,
                            delta = GroqDelta(content = "<think>Secret</think>\nVisible")
                        )
                    )
                )
            )
        )
        val repository = createRepository(groqAPI = groqAPI)

        val states = repository.completeChat(
            userMessages = listOf(MessageV2(content = "Hi", platformType = null)),
            assistantMessages = emptyList(),
            platform = groqPlatform(reasoning = true, model = "qwen/qwen3-32b"),
            runId = "test-run"
        ).toList()

        assertEquals(
            listOf(
                ApiState.Loading,
                ApiState.Thinking("Secret"),
                ApiState.Success("Visible"),
                ApiState.Done
            ),
            states
        )
    }

    @Test
    fun `groq reasoning disabled hides qwen reasoning`() = runBlocking {
        val groqAPI = FakeGroqAPI(emptyFlow())
        val repository = createRepository(groqAPI = groqAPI)

        repository.completeChat(
            userMessages = listOf(MessageV2(content = "Hi", platformType = null)),
            assistantMessages = emptyList(),
            platform = groqPlatform(reasoning = false, model = "qwen/qwen3-32b"),
            runId = "test-run"
        ).toList()

        val request = groqAPI.lastRequest
        assertEquals("hidden", request?.reasoningFormat)
        assertNull(request?.includeReasoning)
        assertNull(request?.reasoningEffort)
    }

    @Test
    fun `groq reasoning disabled turns off gpt oss reasoning`() = runBlocking {
        val groqAPI = FakeGroqAPI(emptyFlow())
        val repository = createRepository(groqAPI = groqAPI)

        repository.completeChat(
            userMessages = listOf(MessageV2(content = "Hi", platformType = null)),
            assistantMessages = emptyList(),
            platform = groqPlatform(reasoning = false, model = "openai/gpt-oss-20b"),
            runId = "test-run"
        ).toList()

        val request = groqAPI.lastRequest
        assertNull(request?.reasoningFormat)
        assertEquals(false, request?.includeReasoning)
        assertNull(request?.reasoningEffort)
    }

    @Test
    fun `google request includes configured safety settings`() = runBlocking {
        val googleAPI = FakeGoogleAPI()
        val repository = createRepository(googleAPI = googleAPI)

        repository.completeChat(
            userMessages = listOf(MessageV2(content = "Hi", platformType = null)),
            assistantMessages = emptyList(),
            platform = googlePlatform(),
            runId = "test-run"
        ).toList()

        assertEquals(1, googleAPI.streamCalls)
        assertEquals(
            listOf(
                GeminiSafetySettings.HARM_CATEGORY_HARASSMENT to GeminiSafetySettings.BLOCK_LOW_AND_ABOVE,
                GeminiSafetySettings.HARM_CATEGORY_HATE_SPEECH to GeminiSafetySettings.BLOCK_MEDIUM_AND_ABOVE,
                GeminiSafetySettings.HARM_CATEGORY_SEXUALLY_EXPLICIT to GeminiSafetySettings.BLOCK_ONLY_HIGH,
                GeminiSafetySettings.HARM_CATEGORY_DANGEROUS_CONTENT to GeminiSafetySettings.BLOCK_NONE
            ),
            googleAPI.lastRequest?.safetySettings?.map { it.category to it.threshold }
        )
    }

    @Test
    fun `google prompt safety block emits error`() = runBlocking {
        val repository = createRepository(
            googleAPI = FakeGoogleAPI(
                flowOf(
                    GenerateContentResponse(
                        promptFeedback = PromptFeedback(blockReason = "SAFETY")
                    )
                )
            )
        )

        val states = repository.completeChat(
            userMessages = listOf(MessageV2(content = "Hi", platformType = null)),
            assistantMessages = emptyList(),
            platform = googlePlatform(),
            runId = "test-run"
        ).toList()

        assertEquals(
            listOf(
                ApiState.Loading,
                ApiState.Error("Gemini safety settings blocked the prompt: SAFETY"),
                ApiState.Done
            ),
            states
        )
    }

    @Test
    fun `google safety finish reason emits error`() = runBlocking {
        val repository = createRepository(
            googleAPI = FakeGoogleAPI(
                flowOf(
                    GenerateContentResponse(
                        candidates = listOf(Candidate(finishReason = "SAFETY"))
                    )
                )
            )
        )

        val states = repository.completeChat(
            userMessages = listOf(MessageV2(content = "Hi", platformType = null)),
            assistantMessages = emptyList(),
            platform = googlePlatform(),
            runId = "test-run"
        ).toList()

        assertEquals(
            listOf(
                ApiState.Loading,
                ApiState.Error("Gemini safety settings blocked the response."),
                ApiState.Done
            ),
            states
        )
    }

    @Test
    fun `failed historical turn is excluded from subsequent inline budget checks`() = runBlocking {
        val openAIAPI = RecordingOpenAIAPI()
        val repository = createRepository(openAIAPI = openAIAPI)
        val tempDir = kotlin.io.path.createTempDirectory("context-inline-budget").toFile().apply {
            deleteOnExit()
        }
        val missingAttachmentFile = File(tempDir, "oversized-${UUID.randomUUID()}.png")
        if (missingAttachmentFile.exists()) {
            missingAttachmentFile.delete()
        }
        assertFalse(missingAttachmentFile.exists())
        val failedTurnAttachment = ChatAttachment(
            localFilePath = missingAttachmentFile.absolutePath,
            preparedFilePath = missingAttachmentFile.absolutePath,
            displayName = "oversized.png",
            mimeType = "image/png",
            sizeBytes = 13L * 1024 * 1024
        )
        val customPlatform = customPlatform()

        val states = repository.completeChat(
            userMessages = listOf(
                MessageV2(
                    id = 1,
                    content = "",
                    platformType = null,
                    attachments = listOf(failedTurnAttachment)
                ),
                MessageV2(
                    id = 2,
                    content = "Try again with text only",
                    platformType = null
                )
            ),
            assistantMessages = listOf(
                listOf(
                    MessageV2(
                        id = 11,
                        content = "Error: These images are too large to upload safely on this provider.",
                        platformType = customPlatform.uid
                    )
                ),
                listOf(
                    MessageV2(
                        id = 12,
                        content = "",
                        platformType = customPlatform.uid
                    )
                )
            ),
            platform = customPlatform,
            runId = "test-run"
        ).toList()

        assertEquals(listOf(ApiState.Loading, ApiState.Done), states)
        assertEquals(1, openAIAPI.streamChatCompletionCalls)
    }

    @Test
    fun `complete chat executes assigned web search and persists its trace`() = runBlocking {
        val connection = ToolConnection(
            connectionUid = "search-1",
            name = "Fixture search",
            alias = "fixture_search",
            type = ToolConnectionType.FIRECRAWL,
            endpointUrl = "https://api.firecrawl.dev/v2/search",
            authType = ToolConnectionAuthType.BEARER,
            secretRef = null,
            oauthClientId = null
        )
        val toolDao = SingleToolConnectionDao(
            connection,
            AgentToolBinding(
                bindingUid = "binding-1",
                profileUid = "custom-platform",
                connectionUid = connection.connectionUid,
                toolName = "web_search"
            )
        )
        val vault = MapSecretVault(emptyMap())
        val traceDao = RecordingToolEventDao()
        val openAIAPI = RecordingOpenAIAPI(
            ArrayDeque(
                listOf(
                    flowOf(
                        ChatCompletionChunk(
                            choices = listOf(
                                Choice(
                                    index = 0,
                                    delta = Delta(
                                        content = "before",
                                        toolCalls = listOf(
                                            ChatToolCallDelta(
                                                index = 0,
                                                id = "call_exact",
                                                function = ChatFunctionDelta("web_search", "{\"query\":\"fixture\"}")
                                            )
                                        )
                                    ),
                                    finishReason = "tool_calls"
                                )
                            )
                        )
                    ),
                    flowOf(ChatCompletionChunk(choices = listOf(Choice(0, Delta(content = "after"), finishReason = "stop"))))
                )
            )
        )
        val repository = createRepository(
            openAIAPI = openAIAPI,
            agentToolResolver = toolResolver(toolDao, vault),
            toolEventRecorder = ToolEventRecorder(traceDao.asDao())
        )

        val states = repository.completeChat(
            userMessages = listOf(MessageV2(content = "Search", platformType = null)),
            assistantMessages = emptyList(),
            platform = customPlatform(),
            runId = "run-web"
        ).toList()

        assertEquals(
            listOf(
                ApiState.Loading,
                ApiState.Success("before"),
                ApiState.ToolCall(toolSequence = 0),
                ApiState.Success("after"),
                ApiState.Done
            ),
            states
        )
        assertEquals(listOf("current_date", "web_search"), openAIAPI.requests.first().tools!!.map { it.function.name }.sorted())
        assertEquals("call_exact", openAIAPI.requests.last().messages.takeLast(2).first().toolCalls!!.single().id)
        val event = traceDao.events.single()
        assertEquals("run-web", event.runId)
        assertEquals("call_exact", event.callId)
        assertEquals("Fixture search", event.connectionNameSnapshot)
        assertEquals(ToolEventStatus.FAILED, event.status)
        assertTrue(event.result.orEmpty().contains("missing credential"))
    }

    private fun createRepository(
        groqAPI: GroqAPI = FakeGroqAPI(emptyFlow()),
        openAIAPI: OpenAIAPI = RecordingOpenAIAPI(),
        googleAPI: GoogleAPI = FakeGoogleAPI(),
        agentToolResolver: AgentToolResolver = emptyToolResolver(),
        toolEventRecorder: ToolEventRecorder = ToolEventRecorder(proxy()),
        localRuntime: LocalRuntime = FakeLocalRuntime(),
        localModelRepository: LocalModelRepository = FakeLocalModelRepository(),
        modelCatalogRepository: ModelCatalogRepository = FakeModelCatalogRepository()
    ): ChatRepositoryImpl = ChatRepositoryImpl(
        context = ContextWrapper(null),
        chatRoomDao = proxy(),
        messageDao = proxy(),
        chatRoomV2Dao = proxy(),
        messageV2Dao = proxy(),
        chatPlatformModelV2Dao = proxy(),
        agentPersistenceDao = proxy(),
        agentRunDao = proxy(),
        settingRepository = proxy(),
        openAIAPI = openAIAPI,
        groqAPI = groqAPI,
        anthropicAPI = FakeAnthropicAPI(),
        googleAPI = googleAPI,
        attachmentUploadCoordinator = AttachmentUploadCoordinator(
            openAIAPI,
            FakeAnthropicAPI(),
            googleAPI
        ),
        contextBuilder = ContextBuilder(),
        agentToolResolver = agentToolResolver,
        toolEventRecorder = toolEventRecorder,
        localRuntime = localRuntime,
        localModelRepository = localModelRepository,
        modelCatalogRepository = modelCatalogRepository,
        deviceSocModel = ""
    )

    private fun emptyToolResolver(): AgentToolResolver {
        val vault = MapSecretVault(emptyMap())
        return toolResolver(SingleToolConnectionDao(), vault)
    }

    private fun toolResolver(toolDao: ToolConnectionDao, vault: SecretVault): AgentToolResolver {
        val repository = ToolConnectionRepository(toolDao, vault)
        val networkClient = NetworkClient(CIO)
        val manager = McpClientManager(networkClient())
        return AgentToolResolver(
            repository,
            vault,
            networkClient,
            manager,
            McpOAuthCoordinator(McpOAuthClient(networkClient()), repository, vault, manager)
        )
    }

    private fun groqPlatform(reasoning: Boolean, model: String) = PlatformV2(
        uid = "groq-platform",
        name = "Groq",
        compatibleType = ClientType.GROQ,
        apiUrl = "https://api.groq.com/openai/",
        model = model,
        reasoning = reasoning
    )

    private fun googlePlatform() = PlatformV2(
        uid = "google-platform",
        name = "Google",
        compatibleType = ClientType.GOOGLE,
        apiUrl = "https://generativelanguage.googleapis.com",
        model = "gemini-3-pro-preview",
        harassmentSafetyThreshold = GeminiSafetySettings.BLOCK_LOW_AND_ABOVE,
        hateSpeechSafetyThreshold = GeminiSafetySettings.BLOCK_MEDIUM_AND_ABOVE,
        sexuallyExplicitSafetyThreshold = GeminiSafetySettings.BLOCK_ONLY_HIGH,
        dangerousContentSafetyThreshold = GeminiSafetySettings.BLOCK_NONE
    )

    private fun localPlatform() = PlatformV2(
        uid = "local-platform",
        name = "Local",
        compatibleType = ClientType.LITERT_LM,
        apiUrl = "",
        model = "gemma3-1b-it",
        temperature = 1.0f,
        topP = 0.95f,
        topK = 40,
        maxTokens = 1024,
        accelerator = "gpu"
    )

    private fun customPlatform() = PlatformV2(
        uid = "custom-platform",
        name = "Custom",
        compatibleType = ClientType.CUSTOM,
        apiUrl = "https://example.com",
        model = "custom-model",
        stream = true
    )

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> proxy(): T {
        val handler = InvocationHandler { _, method, _ ->
            when (method.returnType) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                Float::class.javaPrimitiveType -> 0f
                Double::class.javaPrimitiveType -> 0.0
                Unit::class.java -> Unit
                else -> null
            }
        }

        return Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java),
            handler
        ) as T
    }

    private class FakeGroqAPI(
        private val chunks: Flow<GroqChatCompletionChunk>
    ) : GroqAPI {
        var streamCalls = 0
        var lastRequest: GroqChatCompletionRequest? = null

        override fun streamChatCompletion(
            request: GroqChatCompletionRequest,
            timeoutSeconds: Int,
            config: ProviderRequestConfig
        ): Flow<GroqChatCompletionChunk> {
            streamCalls += 1
            lastRequest = request
            return chunks
        }
    }

    private class RecordingOpenAIAPI(
        private val chatRounds: ArrayDeque<Flow<ChatCompletionChunk>> = ArrayDeque()
    ) : OpenAIAPI {
        var streamChatCompletionCalls = 0
        val requests = mutableListOf<ChatCompletionRequest>()

        override fun streamChatCompletion(
            request: ChatCompletionRequest,
            timeoutSeconds: Int,
            config: ProviderRequestConfig
        ): Flow<ChatCompletionChunk> {
            streamChatCompletionCalls += 1
            requests += request
            return chatRounds.removeFirstOrNull() ?: emptyFlow()
        }

        override fun streamResponses(
            request: ResponsesRequest,
            timeoutSeconds: Int,
            config: ProviderRequestConfig
        ): Flow<ResponsesStreamEvent> = emptyFlow()

        override suspend fun uploadFile(
            filePath: String,
            fileName: String,
            mimeType: String,
            config: ProviderRequestConfig
        ): UploadedProviderFile = UploadedProviderFile(id = "file-uploaded", mimeType = mimeType)

        override suspend fun isFileAvailable(fileId: String, config: ProviderRequestConfig): Boolean = false
    }

    private class FakeAnthropicAPI : AnthropicAPI {
        override fun streamChatMessage(
            messageRequest: MessageRequest,
            timeoutSeconds: Int,
            config: ProviderRequestConfig
        ): Flow<MessageResponseChunk> = emptyFlow()

        override suspend fun uploadFile(
            filePath: String,
            fileName: String,
            mimeType: String,
            config: ProviderRequestConfig
        ): UploadedProviderFile = UploadedProviderFile(id = "anthropic-file", mimeType = mimeType)

        override suspend fun isFileAvailable(fileId: String, config: ProviderRequestConfig): Boolean = false
    }

    private class FakeGoogleAPI(
        private val chunks: Flow<GenerateContentResponse> = emptyFlow()
    ) : GoogleAPI {
        var streamCalls = 0
        var lastRequest: GenerateContentRequest? = null

        override fun streamGenerateContent(
            request: GenerateContentRequest,
            model: String,
            timeoutSeconds: Int,
            config: ProviderRequestConfig
        ): Flow<GenerateContentResponse> {
            streamCalls += 1
            lastRequest = request
            return chunks
        }

        override suspend fun uploadFile(
            filePath: String,
            fileName: String,
            mimeType: String,
            config: ProviderRequestConfig
        ): UploadedProviderFile = UploadedProviderFile(id = "google-file", mimeType = mimeType)

        override suspend fun isFileAvailable(fileName: String, config: ProviderRequestConfig): Boolean = false
    }
}

private class MapSecretVault(
    private val values: Map<String, ByteArray>
) : SecretVault {
    override suspend fun put(secretRef: String, secret: ByteArray) = Unit

    override suspend fun read(secretRef: String): ByteArray? = values[secretRef]?.copyOf()

    override suspend fun delete(secretRef: String) = Unit
}

private class SingleToolConnectionDao(
    connection: ToolConnection? = null,
    binding: AgentToolBinding? = null
) : ToolConnectionDao {
    private val connections = listOfNotNull(connection)
    private val bindings = listOfNotNull(binding)

    override suspend fun listConnections(): List<ToolConnection> = connections

    override suspend fun getConnection(connectionUid: String): ToolConnection? = connections.firstOrNull { it.connectionUid == connectionUid }

    override suspend fun getConnectionsByUids(connectionUids: List<String>): List<ToolConnection> = connections.filter { it.connectionUid in connectionUids }

    override suspend fun upsertConnection(connection: ToolConnection) = Unit

    override suspend fun deleteConnectionByUid(connectionUid: String) = Unit

    override suspend fun listBindingsByProfile(profileUid: String): List<AgentToolBinding> = bindings.filter { it.profileUid == profileUid }

    override suspend fun insertBinding(binding: AgentToolBinding) = Unit

    override suspend fun deleteConnectionToolBindingsForTypes(profileUid: String, toolName: String, connectionTypes: List<String>) = Unit

    override suspend fun deleteBuiltInToolBinding(profileUid: String, toolName: String) = Unit

    override suspend fun deleteConnectionBindingsForType(profileUid: String, connectionType: String) = Unit

    override suspend fun listBindingsWithConnections(profileUid: String): List<AgentToolBindingWithConnection> = listBindingsByProfile(profileUid).map { row ->
        AgentToolBindingWithConnection(row, row.connectionUid?.let { uid -> connections.firstOrNull { it.connectionUid == uid } })
    }
}

private class RecordingToolEventDao {
    val events = mutableListOf<ToolEvent>()

    @Suppress("UNCHECKED_CAST")
    fun asDao(): dev.chungjungsoo.gptmobile.data.database.dao.AgentPersistenceDao {
        val handler = InvocationHandler { _, method, args ->
            when (method.name) {
                "insertToolEvent" -> {
                    events += args!![0] as ToolEvent
                    Unit
                }

                "finishToolEvent" -> {
                    val eventId = args!![0] as String
                    val index = events.indexOfFirst { it.eventId == eventId && it.callId == args[1] }
                    if (index < 0) {
                        0
                    } else {
                        events[index] = events[index].copy(
                            result = args[2] as String,
                            resultType = args[3] as String,
                            status = args[4] as String,
                            isError = args[5] as Boolean,
                            completedAt = args[6] as Long,
                            error = args[7] as String?
                        )
                        1
                    }
                }

                "getToolEventById" -> events.firstOrNull { it.eventId == args!![0] }

                "cancelActiveToolEvents" -> Unit

                else -> when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    Long::class.javaPrimitiveType -> 0L
                    Unit::class.java -> Unit
                    else -> null
                }
            }
        }
        return Proxy.newProxyInstance(
            dev.chungjungsoo.gptmobile.data.database.dao.AgentPersistenceDao::class.java.classLoader,
            arrayOf(dev.chungjungsoo.gptmobile.data.database.dao.AgentPersistenceDao::class.java),
            handler
        ) as dev.chungjungsoo.gptmobile.data.database.dao.AgentPersistenceDao
    }
}
