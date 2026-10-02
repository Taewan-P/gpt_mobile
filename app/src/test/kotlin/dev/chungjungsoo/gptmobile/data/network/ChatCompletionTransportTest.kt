package dev.chungjungsoo.gptmobile.data.network

import com.sun.net.httpserver.HttpServer
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import io.ktor.client.engine.cio.CIO
import java.net.InetSocketAddress
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCompletionTransportTest {
    @Test
    fun `non streaming completion exposes location calls usage and gateway metadata`() = withResponse(
        "application/json",
        """{"choices":[{"message":{"role":"assistant","tool_calls":[{"id":"gps-1","type":"function","function":{"name":"device_location","arguments":"{}"}}]},"finish_reason":"stop"}],"usage":{"prompt_tokens":12,"completion_tokens":8,"total_tokens":20}}"""
    ) { api, config, accept ->
        val chunk = runBlocking { api.streamChatCompletion(request(stream = false), 5, config).toList().single() }
        assertEquals("application/json", accept())
        assertEquals("gps-1", chunk.choices!!.single().effectiveDelta.toolCalls!!.single().id)
        assertEquals("job-1", chunk.gatewayMetadata?.jobId)
        assertEquals(20, chunk.usage?.totalTokens)
        assertEquals(null, chunk.error)
    }

    @Test
    fun `server returning JSON despite stream request preserves text and reasoning`() = withResponse(
        "application/json; charset=utf-8",
        """{"choices":[{"message":{"content":"Your location was retrieved.","reasoning_content":"Use the phone result."}}]}"""
    ) { api, config, _ ->
        val choice = runBlocking { api.streamChatCompletion(request(), 5, config).toList().single().choices!!.single() }
        assertEquals("Your location was retrieved.", choice.effectiveDelta.content)
        assertEquals("Use the phone result.", choice.effectiveDelta.effectiveReasoning)
        assertEquals("stop", choice.finishReason)
    }

    @Test
    fun `only explicit DONE signals successful completion of streamed calls`() {
        val body = "data: {\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"name\":\"device_location\",\"arguments\":\"{}\"}}]}}]}\n\n"
        listOf(false, true).forEach { done ->
            withResponse("text/event-stream", body + if (done) "data: [DONE]\n\n" else "") { api, config, _ ->
                val chunks = runBlocking { api.streamChatCompletion(request(), 5, config).toList() }
                assertEquals(done, chunks.any { it.streamFinished })
                assertEquals(!done, chunks.any { it.error != null })
                assertTrue(chunks.first().choices!!.single().effectiveDelta.toolCalls!!.isNotEmpty())
            }
        }
    }

    @Test
    fun `gateway progress alone is observational and not a callable tool`() = withResponse(
        "text/event-stream",
        "data: {\"gateway_progress\":{\"event\":\"tool_completed\",\"tool_name\":\"device_location\",\"tool_args\":{}}}\n\ndata: [DONE]\n\n"
    ) { api, config, _ ->
        val chunk = runBlocking { api.streamChatCompletion(request(), 5, config).toList().single { it.gatewayProgress != null } }
        assertEquals("device_location", chunk.gatewayProgress?.toolName)
        assertEquals(null, chunk.choices)
        assertFalse(chunk.streamFinished)
    }

    @Test
    fun `text block arrays and buffered timing data remain usable`() = withResponse(
        "application/json",
        """{"choices":[{"message":{"content":[{"type":"text","text":"PKG-"},{"type":"text","text":{"value":"a1b2c3d4"}}]}}],"timings":{"predicted_per_second":42.5}}"""
    ) { api, config, _ ->
        val chunk = runBlocking { api.streamChatCompletion(request(), 5, config).toList().single() }
        assertEquals("PKG-a1b2c3d4", chunk.choices!!.single().effectiveDelta.content)
        assertEquals(42.5, chunk.timings!!.decodeTokensPerSecond!!, .01)
    }

    private fun request(stream: Boolean = true) = ChatCompletionRequest(model = "llama", messages = emptyList(), stream = stream)

    private fun withResponse(contentType: String, body: String, block: (OpenAIAPI, ProviderRequestConfig, () -> String?) -> Unit) {
        var accept: String? = null
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            accept = exchange.requestHeaders.getFirst("Accept")
            exchange.requestBody.close()
            exchange.responseHeaders.add("Content-Type", contentType)
            exchange.responseHeaders.add("X-Gateway-Job-ID", "job-1")
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val client = NetworkClient(CIO)
        try {
            block(OpenAIAPIImpl(client), ProviderRequestConfig("http://127.0.0.1:${server.address.port}/", null)) { accept }
        } finally {
            client().close()
            server.stop(0)
        }
    }
}
