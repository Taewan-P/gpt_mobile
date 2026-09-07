package dev.chungjungsoo.gptmobile.data.network

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.chungjungsoo.gptmobile.data.agent.ToolDefinitionsRejectedException
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.MessageRole
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.TextContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.AnthropicTool
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.InputMessage
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.MessageRequest
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ErrorResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.google.common.Content
import dev.chungjungsoo.gptmobile.data.dto.google.common.Part
import dev.chungjungsoo.gptmobile.data.dto.google.common.Role as GoogleRole
import dev.chungjungsoo.gptmobile.data.dto.google.request.FunctionDeclaration
import dev.chungjungsoo.gptmobile.data.dto.google.request.GenerateContentRequest
import dev.chungjungsoo.gptmobile.data.dto.google.request.GoogleTool
import dev.chungjungsoo.gptmobile.data.dto.groq.request.GroqChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.common.Role
import dev.chungjungsoo.gptmobile.data.dto.openai.common.TextContent as OpenAITextContent
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatFunctionTool
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatMessage
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseFunctionTool
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseInputContent
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseInputMessage
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponsesRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseErrorEvent
import io.ktor.client.engine.cio.CIO
import java.net.InetSocketAddress
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderToolRejectionTest {
    private val schema = buildJsonObject { put("type", "object") }

    @Test
    fun `openai responses rejects tool definitions before emitting provider events`() = withServer(400, openAIError()) { baseUrl ->
        val api = OpenAIAPIImpl(NetworkClient(CIO))

        assertThrows(ToolDefinitionsRejectedException::class.java) {
            runBlocking {
                api.streamResponses(responsesRequest(withTools = true), 5, config(baseUrl)).toList()
            }
        }
        val event = runBlocking {
            api.streamResponses(responsesRequest(withTools = false), 5, config(baseUrl)).single()
        }

        assertEquals("400", (event as ResponseErrorEvent).code)
    }

    @Test
    fun `openai compatible rejects tool definitions before emitting provider events`() = withServer(422, openAIError()) { baseUrl ->
        val api = OpenAIAPIImpl(NetworkClient(CIO))

        assertThrows(ToolDefinitionsRejectedException::class.java) {
            runBlocking {
                api.streamChatCompletion(chatRequest(withTools = true), 5, config(baseUrl)).toList()
            }
        }
        val chunk = runBlocking {
            api.streamChatCompletion(chatRequest(withTools = false), 5, config(baseUrl)).single()
        }

        assertEquals("422", chunk.error?.code)
    }

    @Test
    fun `groq rejects tool definitions before emitting provider events`() = withServer(400, openAIError()) { baseUrl ->
        val api = GroqAPIImpl(NetworkClient(CIO))

        assertThrows(ToolDefinitionsRejectedException::class.java) {
            runBlocking {
                api.streamChatCompletion(groqRequest(withTools = true), 5, config(baseUrl)).toList()
            }
        }
        val chunk = runBlocking {
            api.streamChatCompletion(groqRequest(withTools = false), 5, config(baseUrl)).single()
        }

        assertEquals("400", chunk.error?.code)
    }

    @Test
    fun `anthropic rejects tool definitions before emitting provider events`() = withServer(422, anthropicError()) { baseUrl ->
        val api = AnthropicAPIImpl(NetworkClient(CIO))

        assertThrows(ToolDefinitionsRejectedException::class.java) {
            runBlocking {
                api.streamChatMessage(anthropicRequest(withTools = true), 5, config(baseUrl)).toList()
            }
        }
        val chunk = runBlocking {
            api.streamChatMessage(anthropicRequest(withTools = false), 5, config(baseUrl)).single()
        }

        assertEquals("Tool use is not supported by this model", (chunk as ErrorResponseChunk).error.message)
    }

    @Test
    fun `gemini rejects tool definitions before emitting provider events`() = withServer(400, googleError()) { baseUrl ->
        val api = GoogleAPIImpl(NetworkClient(CIO))

        assertThrows(ToolDefinitionsRejectedException::class.java) {
            runBlocking {
                api.streamGenerateContent(geminiRequest(withTools = true), "model", 5, config(baseUrl)).toList()
            }
        }
        val response = runBlocking {
            api.streamGenerateContent(geminiRequest(withTools = false), "model", 5, config(baseUrl)).single()
        }

        assertEquals(400, response.error?.code)
    }

    @Test
    fun `anthropic validation errors are surfaced instead of silently disabling tools`() = withServer(400, anthropicValidationError()) { baseUrl ->
        val chunk = runBlocking {
            AnthropicAPIImpl(NetworkClient(CIO))
                .streamChatMessage(anthropicRequest(withTools = true), 5, config(baseUrl))
                .single()
        }

        assertEquals("tools.0.input_schema is invalid", (chunk as ErrorResponseChunk).error.message)
    }

    @Test
    fun `gemini validation errors are surfaced instead of silently disabling tools`() = withServer(400, googleValidationError()) { baseUrl ->
        val response = runBlocking {
            GoogleAPIImpl(NetworkClient(CIO))
                .streamGenerateContent(geminiRequest(withTools = true), "model", 5, config(baseUrl))
                .single()
        }

        assertEquals("function_declarations[0].parameters is invalid", response.error?.message)
    }

    @Test
    fun `gemini sends api key only in a header`() {
        var apiKeyHeader: String? = null
        var query: String? = null
        withServer(
            status = 400,
            body = googleError(),
            onRequest = { exchange ->
                apiKeyHeader = exchange.requestHeaders.getFirst("x-goog-api-key")
                query = exchange.requestURI.rawQuery
            }
        ) { baseUrl ->
            runBlocking {
                GoogleAPIImpl(NetworkClient(CIO))
                    .streamGenerateContent(geminiRequest(withTools = false), "model", 5, config(baseUrl))
                    .single()
            }
        }

        assertEquals("token", apiKeyHeader)
        assertFalse(query.orEmpty().split('&').any { it.startsWith("key=") })
    }

    @Test
    fun `anthropic sends files and interleaved thinking beta features in one header`() {
        var betaHeader: String? = null
        withServer(
            status = 400,
            body = anthropicValidationError(),
            onRequest = { exchange -> betaHeader = exchange.requestHeaders.getFirst("anthropic-beta") }
        ) { baseUrl ->
            runBlocking {
                AnthropicAPIImpl(NetworkClient(CIO))
                    .streamChatMessage(
                        anthropicRequest(withTools = false),
                        5,
                        ProviderRequestConfig(
                            apiUrl = baseUrl,
                            token = "token",
                            anthropicBetaFeatures = setOf("interleaved-thinking-2025-05-14")
                        )
                    )
                    .single()
            }
        }

        assertEquals("files-api-2025-04-14,interleaved-thinking-2025-05-14", betaHeader)
    }

    @Test
    fun `openrouter unsupported tool endpoint response retries without tools`() {
        val requestsWithTools = mutableListOf<Boolean>()
        withOpenRouterFallbackServer(requestsWithTools) { baseUrl ->
            val api = OpenAIAPIImpl(NetworkClient(CIO))
            var retriedWithoutTools = false

            val chunks = runBlocking {
                try {
                    api.streamChatCompletion(chatRequest(withTools = true), 5, config(baseUrl)).toList()
                } catch (_: ToolDefinitionsRejectedException) {
                    retriedWithoutTools = true
                    api.streamChatCompletion(chatRequest(withTools = false), 5, config(baseUrl)).toList()
                }
            }

            assertTrue(retriedWithoutTools)
            assertEquals(listOf(true, false), requestsWithTools)
            assertEquals("chat fallback", chunks.single().choices!!.single().delta.content)
        }
    }

    @Test
    fun `unrelated openrouter 404 remains a surfaced provider error`() = withServer(404, """{"error":{"message":"Model route was not found"}}""") { baseUrl ->
        val chunk = runBlocking {
            OpenAIAPIImpl(NetworkClient(CIO))
                .streamChatCompletion(chatRequest(withTools = true), 5, config(baseUrl))
                .single()
        }

        assertEquals("404", chunk.error?.code)
        assertEquals("Model route was not found", chunk.error?.message)
    }

    private fun responsesRequest(withTools: Boolean) = ResponsesRequest(
        model = "model",
        input = listOf(ResponseInputMessage("user", ResponseInputContent.text("hello"))),
        tools = toolList(withTools) { ResponseFunctionTool("lookup", "Lookup", schema) }
    )

    private fun chatRequest(withTools: Boolean) = ChatCompletionRequest(
        model = "model",
        messages = chatMessages(),
        tools = toolList(withTools) { ChatFunctionTool("lookup", "Lookup", schema) }
    )

    private fun groqRequest(withTools: Boolean) = GroqChatCompletionRequest(
        model = "model",
        messages = chatMessages(),
        tools = toolList(withTools) { ChatFunctionTool("lookup", "Lookup", schema) }
    )

    private fun anthropicRequest(withTools: Boolean) = MessageRequest(
        model = "model",
        messages = listOf(InputMessage(MessageRole.USER, listOf(TextContent("hello")))),
        maxTokens = 16,
        tools = toolList(withTools) { AnthropicTool("lookup", "Lookup", schema) }
    )

    private fun geminiRequest(withTools: Boolean) = GenerateContentRequest(
        contents = listOf(Content(GoogleRole.USER, listOf(Part.text("hello")))),
        tools = toolList(withTools) {
            GoogleTool(listOf(FunctionDeclaration("lookup", "Lookup", schema)))
        }
    )

    private fun chatMessages() = listOf(
        ChatMessage(Role.USER, listOf(OpenAITextContent("hello")))
    )

    private fun config(baseUrl: String) = ProviderRequestConfig(baseUrl, "token")

    private fun <T> toolList(withTools: Boolean, create: () -> T): List<T>? = if (withTools) listOf(create()) else null

    private fun <T> withServer(
        status: Int,
        body: String,
        onRequest: (HttpExchange) -> Unit = {},
        block: (String) -> T
    ): T = withHttpServer(block) { exchange ->
        onRequest(exchange)
        exchange.requestBody.close()
        exchange.respond(status, "application/json", body)
    }

    private fun <T> withOpenRouterFallbackServer(
        requestsWithTools: MutableList<Boolean>,
        block: (String) -> T
    ): T = withHttpServer(block) { exchange ->
        val hasTools = exchange.requestBody.use { body ->
            body.readBytes().decodeToString().contains("\"tools\"")
        }
        requestsWithTools += hasTools
        if (hasTools) {
            exchange.respond(404, "application/json", """{"error":{"message":"No endpoints found that support tool use"}}""")
        } else {
            exchange.respond(
                200,
                "text/event-stream",
                "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"chat fallback\"}}]}\n\ndata: [DONE]\n\n"
            )
        }
    }

    private fun <T> withHttpServer(block: (String) -> T, handler: (HttpExchange) -> Unit): T {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange -> handler(exchange) }
        server.start()
        return try {
            block("http://127.0.0.1:${server.address.port}/")
        } finally {
            server.stop(0)
        }
    }

    private fun HttpExchange.respond(status: Int, contentType: String, body: String) {
        val bytes = body.toByteArray()
        responseHeaders.add("Content-Type", contentType)
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private fun openAIError() = """{"error":{"message":"This model does not support tools"}}"""

    private fun anthropicError() = """{"type":"error","error":{"type":"invalid_request_error","message":"Tool use is not supported by this model"}}"""

    private fun googleError() = """{"error":{"code":400,"message":"Tools are not supported by this model","status":"INVALID_ARGUMENT"}}"""

    private fun anthropicValidationError() = """{"type":"error","error":{"type":"invalid_request_error","message":"tools.0.input_schema is invalid"}}"""

    private fun googleValidationError() = """{"error":{"code":400,"message":"function_declarations[0].parameters is invalid","status":"INVALID_ARGUMENT"}}"""
}
