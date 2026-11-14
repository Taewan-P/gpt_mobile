package dev.chungjungsoo.gptmobile.data.agent.provider

import android.content.ContextWrapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.agent.AgentRunEvent
import dev.chungjungsoo.gptmobile.data.agent.AgentRunner
import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ProviderEvent
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.context.ConversationTurn
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.CompactResponsesRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.CompactResponsesResult
import dev.chungjungsoo.gptmobile.data.dto.openai.request.OpaqueResponseInput
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseFunctionCallInput
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseFunctionCallOutput
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseInputContent
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseInputMessage
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponsesRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ChatCompletionChunk
import dev.chungjungsoo.gptmobile.data.dto.openai.response.FunctionCallArgumentsDoneEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.OutputItem
import dev.chungjungsoo.gptmobile.data.dto.openai.response.OutputItemAddedEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.OutputTextDeltaEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseCompletedEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseCreatedEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseObject
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponsesStreamEvent
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.network.OpenAIAPI
import dev.chungjungsoo.gptmobile.data.network.OpenAIAPIImpl
import dev.chungjungsoo.gptmobile.data.network.ProviderRequestConfig
import dev.chungjungsoo.gptmobile.data.network.UploadedProviderFile
import io.ktor.client.engine.cio.CIO
import java.net.InetSocketAddress
import java.util.ArrayDeque
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIResponsesToolContinuationTest {
    @Test
    fun `stateless responses endpoint replays the tool round without previous_response_id`() = runBlocking {
        val api = scriptedResponsesApi()
        val session = OpenAIResponsesAdapter(api, attachmentEncoder()).openSession(
            turns(),
            platform(NVIDIA_API_URL)
        )

        val events = AgentRunner().run(session, listOf(searchTool())).toList()

        assertTrue(events.failureText(), events.failureText().isEmpty())
        assertEquals("Markets rose.", events.answerText())
        val followUp = NetworkClient.openAIJson.encodeToString(api.requests[1])
        assertFalse(followUp, followUp.contains("previous_response_id"))
        assertTrue(followUp.contains(PROMPT))
        assertTrue(followUp.contains("\"type\":\"function_call\""))
        assertTrue(followUp.contains("\"type\":\"function_call_output\""))
        assertTrue(followUp.contains(SEARCH_RESULT))
        assertEquals(listOf("user:$PROMPT", "call:call_exa", "output:call_exa"), api.requests[1].inputShape())
        assertNull(api.requests[0].previousResponseId)
    }

    @Test
    fun `official openai endpoint still chains the tool round with previous_response_id`() = runBlocking {
        val api = scriptedResponsesApi()
        val session = OpenAIResponsesAdapter(api, attachmentEncoder()).openSession(
            turns(),
            platform(ModelConstants.OPENAI_API_URL)
        )

        val events = AgentRunner().run(session, listOf(searchTool())).toList()

        assertTrue(events.failureText(), events.failureText().isEmpty())
        assertEquals("Markets rose.", events.answerText())
        assertNull(api.requests[0].previousResponseId)
        assertEquals("resp_tool", api.requests[1].previousResponseId)
        assertTrue(api.requests[1].input.all { it is ResponseFunctionCallOutput })
        assertEquals(SEARCH_RESULT, (api.requests[1].input.single() as ResponseFunctionCallOutput).output)
    }

    @Test
    fun `stateless responses endpoint replays assistant text written before the tool call`() = runBlocking {
        val api = RecordingOpenAIAPI(
            ArrayDeque(
                listOf(
                    round("resp_tool", text("I will search "), text("for that."), *functionCall("fc_1", "call_exa")),
                    round("resp_answer", text("Markets rose."))
                )
            )
        )
        val session = OpenAIResponsesAdapter(api, attachmentEncoder()).openSession(turns(), platform(NVIDIA_API_URL))

        val events = AgentRunner().run(session, listOf(searchTool())).toList()

        assertTrue(events.failureText(), events.failureText().isEmpty())
        assertEquals(
            listOf("user:$PROMPT", "assistant:I will search for that.", "call:call_exa", "output:call_exa"),
            api.requests[1].inputShape()
        )
    }

    @Test
    fun `stateless responses endpoint does not replay blank assistant text`() = runBlocking {
        val api = RecordingOpenAIAPI(
            ArrayDeque(
                listOf(
                    round("resp_tool", text("\n\n"), *functionCall("fc_1", "call_exa")),
                    round("resp_answer", text("Markets rose."))
                )
            )
        )
        val session = OpenAIResponsesAdapter(api, attachmentEncoder()).openSession(turns(), platform(NVIDIA_API_URL))

        val events = AgentRunner().run(session, listOf(searchTool())).toList()

        assertTrue(events.failureText(), events.failureText().isEmpty())
        assertEquals(listOf("user:$PROMPT", "call:call_exa", "output:call_exa"), api.requests[1].inputShape())
    }

    @Test
    fun `stateless responses endpoint replays parallel calls and every earlier round in order`() = runBlocking {
        val api = RecordingOpenAIAPI(multiRoundScript())
        val session = OpenAIResponsesAdapter(api, attachmentEncoder()).openSession(turns(), platform(NVIDIA_API_URL))

        val events = AgentRunner().run(session, listOf(searchTool())).toList()

        assertTrue(events.failureText(), events.failureText().isEmpty())
        assertEquals(listOf(null, null, null), api.requests.map { it.previousResponseId })
        val firstExchange = listOf("user:$PROMPT", "call:call_a", "call:call_b", "output:call_a", "output:call_b")
        assertEquals(firstExchange, api.requests[1].inputShape())
        assertEquals(
            firstExchange + listOf("assistant:Checking one more source.", "call:call_c", "output:call_c"),
            api.requests[2].inputShape()
        )
    }

    @Test
    fun `official openai endpoint chains every round and sends only the newest tool outputs`() = runBlocking {
        val api = RecordingOpenAIAPI(multiRoundScript())
        val session = OpenAIResponsesAdapter(api, attachmentEncoder()).openSession(
            turns(),
            platform(ModelConstants.OPENAI_API_URL)
        )

        val events = AgentRunner().run(session, listOf(searchTool())).toList()

        assertTrue(events.failureText(), events.failureText().isEmpty())
        assertEquals(listOf(null, "resp_1", "resp_2"), api.requests.map { it.previousResponseId })
        assertEquals(listOf("output:call_a", "output:call_b"), api.requests[1].inputShape())
        assertEquals(listOf("output:call_c"), api.requests[2].inputShape())
    }

    @Test
    fun `mock nvidia responses server completes exa search when previous_response_id is rejected`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val bodies = mutableListOf<String>()
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.use { String(it.readBytes(), Charsets.UTF_8) }
            synchronized(bodies) { bodies += body }
            if (body.contains("\"previous_response_id\"")) {
                val error = """{"message":"Validation: 'previous_response_id' is not supported.","type":"Not implemented","code":501}"""
                writeHttp(exchange, 501, "application/json", error)
            } else if (!body.contains("function_call_output")) {
                writeHttp(exchange, 200, "text/event-stream", TOOL_CALL_SSE)
            } else {
                writeHttp(exchange, 200, "text/event-stream", ANSWER_SSE)
            }
        }
        server.start()
        val network = NetworkClient(CIO)
        try {
            val port = server.address.port
            val events = runBlocking(Dispatchers.IO) {
                val session = OpenAIResponsesAdapter(OpenAIAPIImpl(network), attachmentEncoder()).openSession(
                    turns(),
                    platform("http://127.0.0.1:$port/v1/")
                )
                AgentRunner().run(session, listOf(searchTool())).toList()
            }
            val captured = synchronized(bodies) { bodies.toList() }

            assertEquals(captured.toString(), 2, captured.size)
            assertFalse(captured[0].contains("previous_response_id"))
            assertFalse(captured[1], captured[1].contains("previous_response_id"))
            assertTrue(captured[1].contains(PROMPT))
            assertTrue(captured[1].contains("function_call_output"))
            assertTrue(captured[1].contains(SEARCH_RESULT))
            assertTrue(events.failureText(), events.failureText().isEmpty())
            assertEquals("Markets rose.", events.answerText())
        } finally {
            network().close()
            server.stop(0)
        }
    }

    private fun scriptedResponsesApi(): RecordingOpenAIAPI = RecordingOpenAIAPI(
        ArrayDeque(
            listOf(
                flowOf(
                    ResponseCreatedEvent(ResponseObject(id = "resp_tool", status = "in_progress")),
                    OutputItemAddedEvent(
                        outputIndex = 0,
                        item = OutputItem(
                            type = "function_call",
                            id = "fc_1",
                            callId = "call_exa",
                            name = "web_search",
                            arguments = ""
                        )
                    ),
                    FunctionCallArgumentsDoneEvent(
                        itemId = "fc_1",
                        outputIndex = 0,
                        arguments = """{"query":"markets"}"""
                    ),
                    ResponseCompletedEvent(ResponseObject(id = "resp_tool", status = "completed"))
                ),
                flowOf(
                    OutputTextDeltaEvent(itemId = "msg_1", outputIndex = 0, contentIndex = 0, delta = "Markets rose."),
                    ResponseCompletedEvent(ResponseObject(id = "resp_answer", status = "completed"))
                )
            )
        )
    )

    private fun multiRoundScript(): ArrayDeque<Flow<ResponsesStreamEvent>> = ArrayDeque(
        listOf(
            round("resp_1", *functionCall("fc_a", "call_a", outputIndex = 0), *functionCall("fc_b", "call_b", outputIndex = 1)),
            round("resp_2", text("Checking one more source."), *functionCall("fc_c", "call_c", outputIndex = 1)),
            round("resp_3", text("Markets rose."))
        )
    )

    private fun round(responseId: String, vararg events: ResponsesStreamEvent): Flow<ResponsesStreamEvent> = flowOf(
        ResponseCreatedEvent(ResponseObject(id = responseId, status = "in_progress")),
        *events,
        ResponseCompletedEvent(ResponseObject(id = responseId, status = "completed"))
    )

    private fun text(delta: String) = OutputTextDeltaEvent(itemId = "msg_1", outputIndex = 0, contentIndex = 0, delta = delta)

    private fun functionCall(itemId: String, callId: String, outputIndex: Int = 0): Array<ResponsesStreamEvent> = arrayOf(
        OutputItemAddedEvent(
            outputIndex = outputIndex,
            item = OutputItem(type = "function_call", id = itemId, callId = callId, name = "web_search", arguments = "")
        ),
        FunctionCallArgumentsDoneEvent(itemId = itemId, outputIndex = outputIndex, arguments = """{"query":"markets"}""")
    )

    private fun ResponsesRequest.inputShape(): List<String> = input.map { item ->
        when (item) {
            is ResponseInputMessage -> "${item.role}:${(item.content as ResponseInputContent.Text).text}"
            is ResponseFunctionCallInput -> "call:${item.callId}"
            is ResponseFunctionCallOutput -> "output:${item.callId}"
            is OpaqueResponseInput -> "opaque"
        }
    }

    private fun turns() = listOf(
        ConversationTurn(
            userMessage = MessageV2(content = PROMPT, platformType = null),
            assistantMessage = null,
            isCurrentTurn = true
        )
    )

    private fun platform(apiUrl: String) = PlatformV2(
        uid = "nvidia",
        name = "NVIDIA",
        compatibleType = ClientType.OPENAI,
        apiUrl = apiUrl,
        token = "test-token",
        model = "nvidia/nemotron-3.5-super-vl-120b-a12b",
        timeout = 5
    )

    private fun attachmentEncoder() = ProviderAttachmentEncoder(ContextWrapper(null))

    private fun searchTool() = object : AgentTool {
        override val definition = AgentToolDefinition(
            name = "web_search",
            description = "Search the web",
            inputSchema = buildJsonObject { put("type", "object") }
        )

        override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult =
            AgentToolResult(callId, ToolResultContent.Text(SEARCH_RESULT), isError = false)
    }

    private fun List<AgentRunEvent>.answerText(): String = mapNotNull { event ->
        ((event as? AgentRunEvent.Provider)?.event as? ProviderEvent.TextDelta)?.text
    }.joinToString("")

    private fun List<AgentRunEvent>.failureText(): String = mapNotNull { event ->
        ((event as? AgentRunEvent.Provider)?.event as? ProviderEvent.Failed)?.message
    }.joinToString("\n")

    private class RecordingOpenAIAPI(
        private val rounds: ArrayDeque<Flow<ResponsesStreamEvent>>
    ) : OpenAIAPI {
        val requests = mutableListOf<ResponsesRequest>()

        override fun streamChatCompletion(
            request: ChatCompletionRequest,
            timeoutSeconds: Int,
            config: ProviderRequestConfig
        ): Flow<ChatCompletionChunk> = error("unused")

        override fun streamResponses(
            request: ResponsesRequest,
            timeoutSeconds: Int,
            config: ProviderRequestConfig
        ): Flow<ResponsesStreamEvent> {
            requests += request
            return rounds.removeFirst()
        }

        override suspend fun uploadFile(
            filePath: String,
            fileName: String,
            mimeType: String,
            config: ProviderRequestConfig
        ) = UploadedProviderFile("file", mimeType)

        override suspend fun isFileAvailable(fileId: String, config: ProviderRequestConfig) = false

        override suspend fun compactResponses(
            request: CompactResponsesRequest,
            timeoutSeconds: Int,
            config: ProviderRequestConfig
        ) = CompactResponsesResult()
    }

    private companion object {
        const val PROMPT = "Porque los mercados subieron con fuerza ayer?"
        const val SEARCH_RESULT = "markets rose on earnings"
        const val NVIDIA_API_URL = "https://integrate.api.nvidia.com/v1/"

        val TOOL_CALL_SSE = sse(
            """{"type":"response.created","response":{"id":"resp_super_vl","status":"in_progress"}}""",
            """{"type":"response.output_item.added","output_index":0,"item":{"type":"function_call","id":"fc_1","call_id":"call_exa","name":"web_search","arguments":""}}""",
            """{"type":"response.function_call_arguments.done","item_id":"fc_1","output_index":0,"arguments":"{\"query\":\"markets\"}"}""",
            """{"type":"response.completed","response":{"id":"resp_super_vl","status":"completed"}}"""
        )
        val ANSWER_SSE = sse(
            """{"type":"response.output_text.delta","item_id":"msg_1","output_index":0,"content_index":0,"delta":"Markets rose."}""",
            """{"type":"response.completed","response":{"id":"resp_answer","status":"completed"}}"""
        )

        fun sse(vararg events: String): String = events.joinToString(separator = "\n") { event -> "data: $event" } + "\n"

        fun writeHttp(
            exchange: HttpExchange,
            status: Int,
            contentType: String,
            body: String
        ) {
            val bytes = body.toByteArray()
            exchange.responseHeaders.set("Content-Type", contentType)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }
}
