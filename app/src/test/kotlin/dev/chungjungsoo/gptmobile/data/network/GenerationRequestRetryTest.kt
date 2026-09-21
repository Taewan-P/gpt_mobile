package dev.chungjungsoo.gptmobile.data.network

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import java.net.InetSocketAddress
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenerationRequestRetryTest {
    @Test
    fun `generation retries a transient rejection before any response text`() = withServer { server, requests, network ->
        server.createContext("/chat/completions") { exchange ->
            if (requests.incrementAndGet() == 1) {
                exchange.respond(503, """{"error":{"message":"temporarily unavailable"}}""")
            } else {
                exchange.respond(200, "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"recovered\"}}]}\n\ndata: [DONE]\n\n")
            }
        }
        server.start()

        val chunks = runBlocking {
            OpenAIAPIImpl(network).streamChatCompletion(
                ChatCompletionRequest(model = "test", messages = emptyList()),
                5,
                ProviderRequestConfig(baseUrl(server), null)
            ).toList()
        }

        assertEquals("recovered", chunks.flatMap { it.choices.orEmpty() }.joinToString("") { it.delta.content.orEmpty() })
        assertEquals(2, requests.get())
    }

    @Test
    fun `permanent generation errors and unrelated writes are not retried`() = withServer { server, requests, network ->
        server.createContext("/") { exchange ->
            requests.incrementAndGet()
            exchange.respond(if (exchange.requestURI.path == "/upload") 503 else 401, """{"error":{"message":"rejected"}}""")
        }
        server.start()

        runBlocking {
            OpenAIAPIImpl(network).streamChatCompletion(
                ChatCompletionRequest(model = "test", messages = emptyList()),
                5,
                ProviderRequestConfig(baseUrl(server), null)
            ).toList()
            network().post(baseUrl(server) + "upload")
        }

        assertEquals(2, requests.get())
    }

    @Test
    fun `retry after supports seconds and HTTP dates without negative delays`() {
        val now = Instant.parse("2026-09-08T00:00:00Z").toEpochMilli()

        assertEquals(5_000L, generationRetryAfterMillis("5", now))
        assertEquals(10_000L, generationRetryAfterMillis("Tue, 8 Sep 2026 00:00:10 GMT", now))
        assertEquals(0L, generationRetryAfterMillis("Mon, 7 Sep 2026 23:59:59 GMT", now))
        assertNull(generationRetryAfterMillis("-1", now))
        assertNull(generationRetryAfterMillis("invalid", now))
        assertEquals(Long.MAX_VALUE, generationRetryAfterMillis("9999999999999999999999999", now))
    }

    private fun withServer(block: (HttpServer, AtomicInteger, NetworkClient) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val network = NetworkClient(CIO)
        try {
            block(server, AtomicInteger(), network)
        } finally {
            network().close()
            server.stop(0)
        }
    }

    private fun baseUrl(server: HttpServer) = "http://127.0.0.1:${server.address.port}/"

    private fun HttpExchange.respond(status: Int, body: String) {
        requestBody.close()
        responseHeaders.add("Content-Type", if (status == 200) "text/event-stream" else "application/json")
        val bytes = body.toByteArray()
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}
