package dev.chungjungsoo.gptmobile.data.repository

import com.sun.net.httpserver.HttpServer
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileModelCatalogTest {
    @Test
    fun nvidiaDiscoversModelsUsingConfiguredEndpointAndBearerKey() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var authorization: String? = null
        server.createContext("/v1/models") { exchange ->
            authorization = exchange.requestHeaders.getFirst("Authorization")
            val bytes = """{"data":[{"id":"openai/gpt-oss-20b"}]}""".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val result = ProfileModelCatalog().load(PlatformV2(name = "NVIDIA", compatibleType = ClientType.NVIDIA, apiUrl = "http://127.0.0.1:${server.address.port}/v1/", token = "test-nvidia-key"))
            assertEquals("Bearer test-nvidia-key", authorization)
            assertEquals(listOf("openai/gpt-oss-20b"), result.map { it.id })
        } finally {
            server.stop(0)
        }
    }

    @Test fun googleModelDiscoveryUsesHeaderPaginationAndChatCapabilityFilter() = runBlocking {
        val requests = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1beta/models") { exchange ->
            requests += exchange.requestURI.toString()
            assertEquals("test-key", exchange.requestHeaders.getFirst("x-goog-api-key"))
            val json = if (exchange.requestURI.query.contains("pageToken")) {
                """{"models":[{"name":"models/second","displayName":"Second","supportedGenerationMethods":["generateContent"]}]}"""
            } else {
                """{"models":[{"name":"models/embed","supportedGenerationMethods":["embedContent"]},{"name":"models/first","displayName":"First","supportedGenerationMethods":["generateContent"]}],"nextPageToken":"next"}"""
            }
            val bytes = json.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val result = ProfileModelCatalog().load(PlatformV2(name = "Google", compatibleType = ClientType.GOOGLE, apiUrl = "http://127.0.0.1:${server.address.port}", token = "test-key"))
            assertEquals(listOf("first", "second"), result.map { it.id })
            assertEquals(2, requests.size)
        } finally {
            server.stop(0)
        }
    }
}
