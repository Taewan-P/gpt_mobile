package dev.chungjungsoo.gptmobile.data.context

import com.sun.net.httpserver.HttpServer
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import io.ktor.client.engine.cio.CIO
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HttpRemoteContextWindowLookupTest {
    @Test
    fun `mistral lookup uses authenticated direct model get`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val network = NetworkClient(CIO)
        val requestMethod = AtomicReference("")
        val rawPath = AtomicReference("")
        val authorization = AtomicReference("")
        try {
            server.createContext("/") { exchange ->
                requestMethod.set(exchange.requestMethod)
                rawPath.set(exchange.requestURI.rawPath)
                authorization.set(exchange.requestHeaders.getFirst("Authorization").orEmpty())
                val body = """{"id":"team/model name","max_context_length":131072}"""
                val bytes = body.toByteArray()
                exchange.requestBody.close()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
            val platform = PlatformV2(
                name = "Mistral",
                compatibleType = ClientType.MISTRAL,
                apiUrl = "http://127.0.0.1:${server.address.port}/v1/",
                token = "mistral-test-key",
                model = "team/model name"
            )
            val detected = runBlocking {
                HttpRemoteContextWindowLookup(network).lookup(platform)
            }
            assertEquals("GET", requestMethod.get())
            assertEquals("/v1/models/team/model%20name", rawPath.get())
            assertEquals("Bearer mistral-test-key", authorization.get())
            assertEquals(131072, detected)
        } finally {
            network().close()
            server.stop(0)
        }
    }
}
