package dev.chungjungsoo.gptmobile.data.repository

import android.content.ContextWrapper
import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.agent.provider.LiteRtLmAdapter
import dev.chungjungsoo.gptmobile.data.agent.tool.AgentToolResolver
import dev.chungjungsoo.gptmobile.data.agent.tool.DeviceLocationTool
import dev.chungjungsoo.gptmobile.data.agent.tool.McpClientManager
import dev.chungjungsoo.gptmobile.data.agent.tool.McpOAuthClient
import dev.chungjungsoo.gptmobile.data.agent.tool.McpOAuthCoordinator
import dev.chungjungsoo.gptmobile.data.agent.tool.ResolvedAgentTool
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
import dev.chungjungsoo.gptmobile.data.model.FreeAiProvider
import dev.chungjungsoo.gptmobile.data.model.GeminiSafetySettings
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPI
import dev.chungjungsoo.gptmobile.data.network.GoogleAPI
import dev.chungjungsoo.gptmobile.data.network.GroqAPI
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.network.OpenAIAPI
import dev.chungjungsoo.gptmobile.data.network.ProviderRequestConfig
import dev.chungjungsoo.gptmobile.data.network.UploadedProviderFile
import dev.chungjungsoo.gptmobile.data.network.error.CircuitBreakerOpenException
import dev.chungjungsoo.gptmobile.data.rag.FactVaultRepository
import dev.chungjungsoo.gptmobile.data.rag.FactVaultSnapshot
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import io.ktor.client.engine.cio.CIO
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.regex.PatternSyntaxException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryImplTest {

    @Test
    fun `helpers honor conversation options and approvals with distinct action identities`() = runBlocking {
        val config = dev.chungjungsoo.gptmobile.data.model.ChatMcpToolConfig(maxToolCalls = 1).withToolDisabled("other")
        val resolver = mockk<AgentToolResolver>()
        var executions = 0
        val tool = object : AgentTool {
            override val definition = AgentToolDefinition("github__work", "GitHub", JsonObject(emptyMap()))
            override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult {
                executions++
                return AgentToolResult(callId, ToolResultContent.Text("Dispatched"), false)
            }
        }
        coEvery { resolver.resolve("custom-platform", config, null, null, any()) } returns listOf(
            ResolvedAgentTool(tool, "work", "Work account", "github", "github__work", false)
        )
        val approvals = mockk<dev.chungjungsoo.gptmobile.data.permissions.ToolApprovalManager>(relaxed = true)
        val ids = mutableListOf<String>()
        coEvery { approvals.authorize("work", "parent", capture(ids), "github", any()) } returns false
        val repository = createRepository(agentToolResolver = resolver, toolApprovals = approvals)
        assertTrue(repository.resolveDelegatedTools(customPlatform(), "parent", config.copy(allToolsDisabled = true)).isEmpty())
        val denied = repository.resolveDelegatedTools(customPlatform(), "parent", config).single().execute("same-call", JsonObject(emptyMap()))
        assertTrue(denied.isError)
        assertEquals(0, executions)
        coEvery { approvals.authorize("work", "parent", capture(ids), "github", any()) } returns true
        repeat(2) {
            val bound = repository.resolveDelegatedTools(customPlatform(), "parent", config).single()
            val result = bound.execute("same-call", JsonObject(emptyMap()))
            assertFalse(result.isError)
            assertEquals("same-call", result.callId)
            assertTrue(bound.execute("over-budget", JsonObject(emptyMap())).isError)
        }
        assertEquals(2, executions)
        assertEquals(3, ids.distinct().size)
        ids.drop(1).forEach { id -> io.mockk.coVerify { approvals.finish("parent", id, true) } }
    }

    @Test
    fun `primary tool ordering preserves catalogs larger than eight tools`() {
        val tools = (1..12).map { index ->
            val agentTool = object : AgentTool {
                override val definition = AgentToolDefinition("tool_$index", "Tool $index", JsonObject(emptyMap()))
                override suspend fun execute(callId: String, arguments: JsonObject) = AgentToolResult(
                    callId,
                    ToolResultContent.Text("unused"),
                    isError = false
                )
            }
            ResolvedAgentTool(
                tool = agentTool,
                connectionUid = null,
                connectionName = null,
                realToolName = "tool_$index",
                modelToolName = agentTool.definition.name
            )
        }

        val ordered = orderPrimaryTools(tools)

        assertEquals(12, ordered.size)
        assertEquals(tools.map { it.modelToolName }.toSet(), ordered.map { it.modelToolName }.toSet())
    }

    @Test
    fun `memory initialization failure and later class failures do not prevent chat replies`() = runBlocking {
        val facts = mockk<FactVaultRepository>(relaxed = true)
        every { facts.state } returns MutableStateFlow(FactVaultSnapshot(enabled = true))
        val api = RecordingOpenAIAPI(ArrayDeque(List(2) { flowOf(ChatCompletionChunk(choices = listOf(Choice(0, Delta(content = "Hello"), finishReason = "stop")))) }))
        val repository = createRepository(openAIAPI = api, factVault = facts)
        val failures = listOf(
            ExceptionInInitializerError(PatternSyntaxException("Invalid property", "[:]", 0)),
            NoClassDefFoundError("Could not initialize memory")
        )
        for ((index, error) in failures.withIndex()) {
            coEvery { facts.prepareTurn(any(), any(), any(), any(), any(), any(), any()) } throws error
            val states = repository.completeChat(
                userMessages = listOf(MessageV2(id = index + 1, chatId = 1, content = "Hello", platformType = null)),
                assistantMessages = emptyList(),
                platform = customPlatform(),
                runId = "memory-initializer-$index"
            ).toList()
            assertTrue(states.filterIsInstance<ApiState.Notice>().any { it.message.contains("Local memory is unavailable") })
            assertTrue(states.toString(), states.none { it is ApiState.Error || it is ApiState.MemoryRecalled })
            assertEquals("Hello", states.filterIsInstance<ApiState.Success>().joinToString("") { it.textChunk })
        }
        assertEquals(2, api.streamChatCompletionCalls)
    }

    @Test(expected = CancellationException::class)
    fun `memory cancellation still cancels the response`() = runBlocking {
        val facts = mockk<FactVaultRepository>(relaxed = true)
        coEvery { facts.prepareTurn(any(), any(), any(), any(), any(), any(), any()) } throws CancellationException("Stopped")
        createRepository(factVault = facts).completeChat(
            userMessages = listOf(MessageV2(id = 1, chatId = 1, content = "Hello", platformType = null)),
            assistantMessages = emptyList(),
            platform = customPlatform(),
            runId = "memory-cancelled"
        ).toList()
        Unit
    }

    @Test
    fun `Free requests neither recall saved facts nor capture new ones`() = runBlocking {
        val storage = object : SecretVault {
            var bytes: ByteArray? = null
            override suspend fun put(secretRef: String, secret: ByteArray) {
                bytes = secret.copyOf()
            }
            override suspend fun read(secretRef: String) = bytes?.copyOf()
            override suspend fun delete(secretRef: String) {
                bytes = null
            }
        }
        val facts = dev.chungjungsoo.gptmobile.data.rag.FactVaultRepository(storage, dev.chungjungsoo.gptmobile.data.rag.KnowledgeGraphEngine())
        facts.setEnabled(true)
        facts.prepareTurn("I prefer Kotlin", 1, 1)
        val saved = facts.state.value.facts
        assertTrue(saved.isNotEmpty())
        val api = RecordingOpenAIAPI()
        val states = createRepository(openAIAPI = api, factVault = facts).completeChat(
            userMessages = listOf(MessageV2(id = 2, chatId = 1, content = "I prefer Rust. What language do I prefer?", platformType = null)),
            assistantMessages = emptyList(),
            platform = FreeAiProvider.KILO.applyTo(customPlatform()).copy(systemPrompt = "Base instructions"),
            runId = "free-memory"
        ).toList()
        assertEquals(1, api.streamChatCompletionCalls)
        assertTrue(states.none { it is ApiState.MemoryRecalled })
        assertEquals(saved, facts.state.value.facts)
        assertFalse(api.requests.single().messages.toString().contains("Kotlin"))
        assertFalse(api.requests.single().messages.toString().contains("Saved local facts"))
    }

    @Test
    fun `Free attachments fail before any provider request`() = runBlocking {
        val api = RecordingOpenAIAPI()
        val attachment = ChatAttachment("/private.txt", "/private.txt", "Private", "text/plain", 40, extractedText = "Private memory")
        val states = createRepository(openAIAPI = api).completeChat(
            userMessages = listOf(MessageV2(id = 2, chatId = 1, content = "Summarize", platformType = null, attachments = listOf(attachment))),
            assistantMessages = emptyList(),
            platform = FreeAiProvider.KILO.applyTo(customPlatform()),
            runId = "free-attachment"
        ).toList()
        assertEquals(0, api.streamChatCompletionCalls)
        assertTrue(states.filterIsInstance<ApiState.Error>().any { it.message.contains("without attachments") })
    }

    @Test
    fun `first person facts inside attachments are never attributed to the user`() = runBlocking {
        val storage = object : SecretVault {
            var value: ByteArray? = null
            override suspend fun put(secretRef: String, secret: ByteArray) {
                value = secret.copyOf()
            }
            override suspend fun read(secretRef: String) = value?.copyOf()
            override suspend fun delete(secretRef: String) {
                value = null
            }
        }
        val facts = dev.chungjungsoo.gptmobile.data.rag.FactVaultRepository(storage, dev.chungjungsoo.gptmobile.data.rag.KnowledgeGraphEngine())
        facts.setEnabled(true)
        val api = FakeGroqAPI(emptyFlow())
        val attachment = ChatAttachment("/unused.txt", "/unused.txt", "Memo", "text/plain", 40, extractedText = "I prefer Rust. I live in Lisbon.")
        createRepository(groqAPI = api, factVault = facts).completeChat(
            userMessages = listOf(MessageV2(id = 9, chatId = 1, content = "Summarize this memo", platformType = null, attachments = listOf(attachment))),
            assistantMessages = emptyList(),
            platform = groqPlatform(false, "model"),
            runId = "attachment-memory"
        ).toList()
        assertEquals(1, api.streamCalls)
        assertTrue(facts.state.value.facts.isEmpty())
    }

    @Test
    fun `saved facts prefix both cloud and local system prompts`() = runBlocking {
        val storage = object : SecretVault {
            var bytes: ByteArray? = null
            override suspend fun put(secretRef: String, secret: ByteArray) {
                bytes = secret.copyOf()
            }
            override suspend fun read(secretRef: String) = bytes?.copyOf()
            override suspend fun delete(secretRef: String) {
                bytes = null
            }
        }
        val facts = dev.chungjungsoo.gptmobile.data.rag.FactVaultRepository(storage, dev.chungjungsoo.gptmobile.data.rag.KnowledgeGraphEngine())
        facts.setEnabled(true)
        facts.prepareTurn("I prefer Kotlin", 1, 1)
        val api = FakeGroqAPI(emptyFlow())
        val cloud = createRepository(groqAPI = api, factVault = facts)
        val cloudStates = cloud.completeChat(
            userMessages = listOf(MessageV2(id = 2, chatId = 1, content = "Help me with Kotlin", platformType = null)),
            assistantMessages = emptyList(),
            platform = groqPlatform(reasoning = false, model = "qwen/qwen3-32b").copy(systemPrompt = "Base instructions"),
            runId = "memory-cloud"
        ).toList()
        val system = api.lastRequest!!.messages.first()
        assertEquals(dev.chungjungsoo.gptmobile.data.dto.openai.common.Role.SYSTEM, system.role)
        assertTrue(system.content.toString().contains("Kotlin"))
        assertTrue(system.content.toString().contains("Base instructions"))
        assertEquals(1, cloudStates.filterIsInstance<ApiState.MemoryRecalled>().single().facts.size)

        val runtime = FakeLocalRuntime()
        val local = createRepository(localRuntime = runtime, localModelRepository = FakeLocalModelRepository(downloadedPaths = mapOf("gemma3-1b-it" to "/models/gemma.litertlm")), factVault = facts)
        local.completeChat(
            userMessages = listOf(MessageV2(id = 3, chatId = 1, content = "Kotlin", platformType = null)),
            assistantMessages = emptyList(),
            platform = localPlatform(),
            runId = "memory-local"
        ).toList()
        assertTrue(runtime.createConversationCalls.single().systemPrompt.orEmpty().startsWith("Saved local facts"))
        assertTrue(runtime.createConversationCalls.single().systemPrompt.orEmpty().contains("Kotlin"))
    }

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
        ).toList().filterNot { it is ApiState.GatewayProgressChanged || it is ApiState.ProgressCheckpoint || (it is ApiState.Notice && (it.message.startsWith("Context estimate:") || it.message.startsWith("Context: no app-imposed limit.") || it.message.startsWith("Local context:"))) }

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
        assertEquals(null, groqAPI.lastRequest?.maxCompletionTokens)
    }

    @Test
    fun `local documents reach native history and current prompt exactly once`() = runBlocking {
        val runtime = FakeLocalRuntime()
        val repository = createRepository(
            localRuntime = runtime,
            localModelRepository = FakeLocalModelRepository(downloadedPaths = mapOf("gemma3-1b-it" to "/models/gemma.litertlm"))
        )
        val document = ChatAttachment(
            localFilePath = "/unopened/document.pdf",
            preparedFilePath = "",
            displayName = "document.pdf",
            mimeType = "application/pdf",
            sizeBytes = 12,
            extractedText = "document contents"
        )
        val states = repository.completeChat(
            userMessages = listOf(
                MessageV2(id = 1, content = "First document", platformType = null, attachments = listOf(document.copy(extractedText = "prior document"))),
                MessageV2(id = 2, content = "Summarize", platformType = null, attachments = listOf(document))
            ),
            assistantMessages = listOf(listOf(MessageV2(content = "Prior answer", platformType = localPlatform().uid))),
            platform = localPlatform(),
            runId = "local-documents"
        ).toList()
        assertFalse(states.any { it is ApiState.Error })
        assertEquals(1, Regex("document contents").findAll(runtime.sendMessageCalls.single()).count())
        assertEquals(1, Regex("prior document").findAll(runtime.createConversationCalls.single().initialMessages.first().text).count())
        assertFalse(states.filterIsInstance<ApiState.Notice>().any { it.message == LiteRtLmAdapter.DEFAULT_IGNORED_ATTACHMENTS })
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
        ).toList().filterNot { it is ApiState.GatewayProgressChanged || it is ApiState.ProgressCheckpoint || (it is ApiState.Notice && (it.message.startsWith("Context estimate:") || it.message.startsWith("Context: no app-imposed limit.") || it.message.startsWith("Local context:"))) }

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
            // This timeline fixture needs room for the complete expanded tool surface.
            deviceRamGb = 12L
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
            toolEventRecorder = ToolEventRecorder(traceDao.asDao(), proxy())
        )

        val states = repository.completeChat(
            userMessages = listOf(MessageV2(content = "Hi", platformType = null)),
            assistantMessages = emptyList(),
            platform = localPlatform().copy(maxTokens = 16384),
            runId = "run-local-tool"
        ).toList().filterNot { it is ApiState.GatewayProgressChanged || it is ApiState.ProgressCheckpoint || (it is ApiState.Notice && (it.message.startsWith("Context estimate:") || it.message.startsWith("Context: no app-imposed limit."))) }

        assertEquals(
            listOf(
                ApiState.Loading,
                ApiState.Notice(LiteRtLmAdapter.DEFAULT_LOADING_MODEL),
                ApiState.Success("before"),
                ApiState.ToolCall(toolSequence = 0),
                ApiState.ToolCall(toolSequence = 0),
                ApiState.Success("after"),
                ApiState.Done
            ),
            states.map { if (it is ApiState.ToolCall) it.copy(metrics = null) else it }
        )
        val completedMetrics = states.filterIsInstance<ApiState.ToolCall>().last().metrics
        assertTrue(completedMetrics?.durationMs != null)
        assertTrue(completedMetrics?.resultBytes != null)
        assertEquals(1, runtime.sendMessageCalls.size)
        assertEquals(
            listOf("calculate_expression", "current_date", "github", "read_file_slice", "read_url", "web_search"),
            runtime.createConversationCalls.single().tools.map { it.name }.sorted()
        )
        assertTrue(runtime.createConversationCalls.single().isConstrainedDecodingEnabled)
        val event = traceDao.events.single()
        assertEquals("run-local-tool", event.runId)
        assertEquals("current_date", event.toolName)
        assertEquals(ToolEventStatus.COMPLETED, event.status)
        assertFalse(event.isError)
    }

    @Test
    fun `litert GPU package keeps MCP tools without catalog tool recommendation`() = runBlocking {
        val runtime = FakeLocalRuntime().apply {
            deviceRamGb = 12L
            scriptedEvents = listOf(listOf(LocalRuntimeEvent.TextDelta("tools visible"), LocalRuntimeEvent.Done))
        }
        val mcpTool = object : AgentTool {
            override val definition = AgentToolDefinition(
                name = "mcp__search__web_search",
                description = "Search the web through the connected MCP server.",
                inputSchema = JsonObject(emptyMap())
            )

            override suspend fun execute(callId: String, arguments: JsonObject) = AgentToolResult(
                callId,
                ToolResultContent.Text("unused"),
                isError = false
            )
        }
        val resolver = mockk<AgentToolResolver>()
        coEvery { resolver.resolve(any(), any(), any(), any(), any()) } returns listOf(
            ResolvedAgentTool(
                tool = mcpTool,
                connectionUid = "search-server",
                connectionName = "Search MCP",
                realToolName = "web_search",
                modelToolName = mcpTool.definition.name,
                shareableReadOnly = true
            )
        )
        val repository = createRepository(
            localRuntime = runtime,
            localModelRepository = FakeLocalModelRepository(
                downloadedPaths = mapOf("gemma3-1b-it-litert" to "/models/gemma-gpu.litertlm")
            ),
            modelCatalogRepository = FakeModelCatalogRepository(
                listOf(CatalogEntry(id = "gemma3-1b-it-litert", capabilities = CatalogCapabilities(tools = false)))
            ),
            agentToolResolver = resolver
        )

        repository.completeChat(
            userMessages = listOf(MessageV2(content = "What tools do you have?", platformType = null)),
            assistantMessages = emptyList(),
            platform = localPlatform().copy(maxTokens = 16384),
            runId = "run-local-litert-tools"
        ).toList()

        assertEquals(
            listOf("mcp__search__web_search"),
            runtime.createConversationCalls.single().tools.map { it.name }.sorted()
        )
        assertTrue(runtime.createConversationCalls.single().isConstrainedDecodingEnabled)
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
        ).toList().filterNot { it is ApiState.GatewayProgressChanged || it is ApiState.ProgressCheckpoint || (it is ApiState.Notice && (it.message.startsWith("Context estimate:") || it.message.startsWith("Context: no app-imposed limit."))) }

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
        ).toList().filterNot { it is ApiState.GatewayProgressChanged || it is ApiState.ProgressCheckpoint || (it is ApiState.Notice && (it.message.startsWith("Context estimate:") || it.message.startsWith("Context: no app-imposed limit."))) }

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
        ).toList().filterNot { it is ApiState.GatewayProgressChanged || it is ApiState.ProgressCheckpoint || (it is ApiState.Notice && (it.message.startsWith("Context estimate:") || it.message.startsWith("Context: no app-imposed limit."))) }

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
        ).toList().filterNot { it is ApiState.GatewayProgressChanged || it is ApiState.ProgressCheckpoint || (it is ApiState.Notice && (it.message.startsWith("Context estimate:") || it.message.startsWith("Context: no app-imposed limit."))) }

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
        ).toList().filterNot { it is ApiState.GatewayProgressChanged || it is ApiState.ProgressCheckpoint || (it is ApiState.Notice && (it.message.startsWith("Context estimate:") || it.message.startsWith("Context: no app-imposed limit."))) }

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
        ).toList().filterNot { it is ApiState.GatewayProgressChanged || it is ApiState.ProgressCheckpoint || (it is ApiState.Notice && (it.message.startsWith("Context estimate:") || it.message.startsWith("Context: no app-imposed limit."))) }

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
        ).toList().filterNot { it is ApiState.GatewayProgressChanged || it is ApiState.ProgressCheckpoint || (it is ApiState.Notice && (it.message.startsWith("Context estimate:") || it.message.startsWith("Context: no app-imposed limit."))) }

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
        ).toList().filterNot { it is ApiState.GatewayProgressChanged || it is ApiState.ProgressCheckpoint || (it is ApiState.Notice && (it.message.startsWith("Context estimate:") || it.message.startsWith("Context: no app-imposed limit."))) }

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
            toolEventRecorder = ToolEventRecorder(traceDao.asDao(), proxy())
        )

        val states = repository.completeChat(
            userMessages = listOf(MessageV2(content = "Search", platformType = null)),
            assistantMessages = emptyList(),
            platform = customPlatform(),
            runId = "run-web",
            chatToolConfig = dev.chungjungsoo.gptmobile.data.model.ChatMcpToolConfig(allowAllByDefault = false, enabledToolIds = setOf(connection.connectionUid))
        ).toList().filterNot { it is ApiState.GatewayProgressChanged || it is ApiState.ProgressCheckpoint || (it is ApiState.Notice && (it.message.startsWith("Context estimate:") || it.message.startsWith("Context: no app-imposed limit."))) }

        assertEquals(
            listOf(
                ApiState.Loading,
                ApiState.Success("before"),
                ApiState.ToolCall(toolSequence = 0),
                ApiState.ToolCall(toolSequence = 0),
                ApiState.Success("after"),
                ApiState.Done
            ),
            states.map { if (it is ApiState.ToolCall) it.copy(metrics = null) else it }
        )
        val completedMetrics = states.filterIsInstance<ApiState.ToolCall>().last().metrics
        assertTrue(completedMetrics?.durationMs != null)
        assertTrue(completedMetrics?.resultBytes != null)
        assertEquals(
            listOf("web_search"),
            openAIAPI.requests.first().tools!!.map { it.function.name }.sorted()
        )
        assertEquals("call_exact", openAIAPI.requests.last().messages.takeLast(2).first().toolCalls!!.single().id)
        val event = traceDao.events.single()
        assertEquals("run-web", event.runId)
        assertEquals("call_exact", event.callId)
        assertEquals("Multi-engine search", event.connectionNameSnapshot)
        assertEquals(ToolEventStatus.FAILED, event.status)
        assertTrue(event.result.orEmpty().contains("missing credential"))
    }

    @Test
    fun `complete chat propagates circuit breaker error as classified user message`() = runBlocking {
        val failingOpenAIAPI = object : OpenAIAPI by RecordingOpenAIAPI() {
            override fun streamChatCompletion(
                request: ChatCompletionRequest,
                timeoutSeconds: Int,
                config: ProviderRequestConfig
            ): Flow<ChatCompletionChunk> = kotlinx.coroutines.flow.flow {
                throw CircuitBreakerOpenException(5000L, "OpenAI")
            }
        }
        val repository = createRepository(openAIAPI = failingOpenAIAPI)

        val states = repository.completeChat(
            userMessages = listOf(MessageV2(content = "Hi", platformType = null)),
            assistantMessages = emptyList(),
            platform = customPlatform(),
            runId = "test-cb"
        ).toList().filterNot { it is ApiState.GatewayProgressChanged || it is ApiState.ProgressCheckpoint || (it is ApiState.Notice && (it.message.startsWith("Context estimate:") || it.message.startsWith("Context: no app-imposed limit."))) }

        assertEquals(
            listOf(
                ApiState.Loading,
                ApiState.Error("Service temporarily unavailable due to high error rates. Please wait a moment before trying again."),
                ApiState.Done
            ),
            states
        )
    }

    private fun createRepository(
        groqAPI: GroqAPI = FakeGroqAPI(emptyFlow()),
        openAIAPI: OpenAIAPI = RecordingOpenAIAPI(),
        googleAPI: GoogleAPI = FakeGoogleAPI(),
        agentToolResolver: AgentToolResolver = emptyToolResolver(),
        toolEventRecorder: ToolEventRecorder = ToolEventRecorder(proxy(), proxy()),
        localRuntime: LocalRuntime = FakeLocalRuntime(),
        localModelRepository: LocalModelRepository = FakeLocalModelRepository(),
        modelCatalogRepository: ModelCatalogRepository = FakeModelCatalogRepository(),
        factVault: dev.chungjungsoo.gptmobile.data.rag.FactVaultRepository? = null,
        toolApprovals: dev.chungjungsoo.gptmobile.data.permissions.ToolApprovalManager? = null
    ): ChatRepositoryImpl = ChatRepositoryImpl(
        context = ContextWrapper(null),
        chatRoomV2Dao = proxy(),
        messageV2Dao = proxy(),
        chatPlatformModelV2Dao = proxy(),
        agentPersistenceDao = proxy(),
        agentRunDao = proxy(),
        settingRepository = mockk<SettingRepository>(relaxed = true).also { settings ->
            io.mockk.coEvery { settings.getFeatureSettings() } returns dev.chungjungsoo.gptmobile.data.model.AppFeatureSettings()
        },
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
        deviceSocModel = "",
        factVault = factVault,
        toolApprovals = toolApprovals
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
            toolConnectionRepository = repository,
            settingRepository = mockk<SettingRepository>(relaxed = true).also { settings ->
                io.mockk.coEvery { settings.getFeatureSettings() } returns dev.chungjungsoo.gptmobile.data.model.AppFeatureSettings()
            },
            secretVault = vault,
            networkClient = networkClient,
            mcpClientManager = manager,
            mcpOAuthCoordinator = McpOAuthCoordinator(McpOAuthClient(networkClient()), repository, vault, manager),
            deviceLocationTool = DeviceLocationTool(mockk(relaxed = true), mockk(relaxed = true))
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
            when {
                method.name == "fetchPlatformV2s" -> emptyList<PlatformV2>()
                method.name == "getFeatureSettings" -> dev.chungjungsoo.gptmobile.data.model.AppFeatureSettings()
                else -> when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    Long::class.javaPrimitiveType -> 0L
                    Float::class.javaPrimitiveType -> 0f
                    Double::class.javaPrimitiveType -> 0.0
                    Unit::class.java -> Unit
                    else -> null
                }
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

    override suspend fun getAllConnections(): List<ToolConnection> = connections

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
