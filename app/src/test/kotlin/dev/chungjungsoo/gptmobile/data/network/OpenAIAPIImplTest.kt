package dev.chungjungsoo.gptmobile.data.network

import com.sun.net.httpserver.HttpServer
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponsesRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseErrorEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.UnknownEvent
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIAPIImplTest {
    @Test
    fun `stopping on a Responses error preserves the original error`() = withServer(
        "data: {\"type\":\"error\",\"message\":\"original API error\",\"code\":\"server_error\"}\n\n"
    ) { api, config ->
        val events = api.streamResponses(ResponsesRequest("gpt-5.6", emptyList()), 5, config).take(1).toList()

        assertEquals(listOf(ResponseErrorEvent(message = "original API error", code = "server_error")), events)
    }

    @Test
    fun `Responses collector failures propagate unchanged`() = withServer(
        "data: {\"type\":\"error\",\"message\":\"original API error\"}\n\n"
    ) { api, config ->
        val failure = IllegalStateException("collector failed")
        val actual = runCatching {
            api.streamResponses(ResponsesRequest("gpt-5.6", emptyList()), 5, config).collect { throw failure }
        }.exceptionOrNull()

        assertSame(failure, actual)
    }

    @Test
    fun `Chat Completions collector failures propagate unchanged`() = withServer(
        "data: {\"id\":\"chat_1\",\"choices\":[]}\n\n"
    ) { api, config ->
        val failure = IllegalStateException("collector failed")
        val actual = runCatching {
            api.streamChatCompletion(ChatCompletionRequest("model", emptyList()), 5, config).collect { throw failure }
        }.exceptionOrNull()

        assertSame(failure, actual)
    }

    @Test
    fun `malformed Responses events do not hide the next API error`() = withServer(
        "data: invalid JSON\n\ndata: {\"type\":\"error\",\"message\":\"original API error\"}\n\n"
    ) { api, config ->
        val events = api.streamResponses(ResponsesRequest("gpt-5.6", emptyList()), 5, config).toList()

        assertEquals(listOf(UnknownEvent, ResponseErrorEvent(message = "original API error")), events)
    }

    @Test
    fun `HTTP rejection survives early collection termination`() = withServer(
        "{\"error\":{\"message\":\"unsupported parameter\"}}",
        status = 400
    ) { api, config ->
        val events = api.streamResponses(ResponsesRequest("gpt-5.6", emptyList()), 5, config).take(1).toList()

        assertEquals(listOf(ResponseErrorEvent(message = "unsupported parameter", code = "400")), events)
    }

    @Test
    fun `malformed Chat Completions chunks are skipped`() = withServer(
        "data: invalid JSON\n\ndata: {\"id\":\"chat_1\",\"choices\":[]}\n\n"
    ) { api, config ->
        val events = api.streamChatCompletion(ChatCompletionRequest("model", emptyList()), 5, config).toList()

        assertEquals(listOf("chat_1"), events.map { it.id })
    }

    @Test
    fun `plain text chat completion errors keep the HTTP status and body`() = withServer(
        "404 page not found",
        status = 404,
        contentType = "text/plain"
    ) { api, config ->
        val chunk = api.streamChatCompletion(ChatCompletionRequest("model", emptyList()), 5, config).toList().single()

        assertEquals("HTTP 404: 404 page not found", chunk.error?.message)
        assertEquals("404", chunk.error?.code)
    }

    @Test
    fun `plain text file upload errors keep the HTTP status and body`() {
        val image = File.createTempFile("upload", ".png").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        withServer(
            "404 page not found",
            status = 404,
            contentType = "text/plain"
        ) { api, config ->
            val error = runCatching {
                api.uploadFile(image.absolutePath, image.name, "image/png", config)
            }.exceptionOrNull()

            assertTrue(error?.message?.contains("Unexpected JSON token") != true)
            assertEquals("HTTP 404: 404 page not found", error?.message)
        }
    }

    @Test
    fun `connection failures are still emitted as network errors`() = withServer("") { api, config ->
        val port = ServerSocket(0).use { it.localPort }
        val unreachable = config.copy(apiUrl = "http://127.0.0.1:$port/")
        val responses = api.streamResponses(ResponsesRequest("model", emptyList()), 5, unreachable).toList()
        val completions = api.streamChatCompletion(ChatCompletionRequest("model", emptyList()), 5, unreachable).toList()

        assertEquals("network_error", (responses.single() as ResponseErrorEvent).code)
        assertEquals("network_error", completions.single().error?.type)
    }

    @Test
    fun `fatal upstream errors propagate unchanged in both streams`() = runBlocking(Dispatchers.IO) {
        val failure = LinkageError("engine initialization failed")
        val engine = object : HttpClientEngineFactory<HttpClientEngineConfig> {
            override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine = throw failure
        }
        val api = OpenAIAPIImpl(NetworkClient(engine))
        val config = ProviderRequestConfig("http://127.0.0.1/", null)
        val responses = runCatching {
            api.streamResponses(ResponsesRequest("model", emptyList()), 5, config).toList()
        }.exceptionOrNull()
        val completions = runCatching {
            api.streamChatCompletion(ChatCompletionRequest("model", emptyList()), 5, config).toList()
        }.exceptionOrNull()

        assertSame(failure, responses)
        assertSame(failure, completions)
    }

    private fun withServer(
        body: String,
        status: Int = 200,
        contentType: String? = null,
        block: suspend (OpenAIAPIImpl, ProviderRequestConfig) -> Unit
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            exchange.requestBody.use { it.readBytes() }
            val responseType = contentType ?: if (status == 200) "text/event-stream" else "application/json"
            exchange.responseHeaders.set("Content-Type", responseType)
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val network = NetworkClient(CIO)
        try {
            // Match AgentRunCoordinator's dispatcher so flowOn does not buffer the abort.
            runBlocking(Dispatchers.IO) {
                block(OpenAIAPIImpl(network), ProviderRequestConfig("http://127.0.0.1:${server.address.port}/", null))
            }
        } finally {
            network().close()
            server.stop(0)
        }
    }
}
