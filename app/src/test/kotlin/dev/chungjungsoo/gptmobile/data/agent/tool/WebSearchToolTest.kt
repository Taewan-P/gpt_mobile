package dev.chungjungsoo.gptmobile.data.agent.tool

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import io.ktor.client.engine.cio.CIO
import java.net.InetSocketAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchToolTest {

    private val servers = mutableListOf<HttpServer>()
    private val networkClients = mutableListOf<NetworkClient>()
    private val clock = Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC)

    @After
    fun stopServers() {
        servers.forEach { it.stop(0) }
        networkClients.forEach { it().close() }
    }

    @Test
    fun `definition exposes web search schema to the model`() {
        val tool = tool(WebSearchProvider.FIRECRAWL, "http://127.0.0.1:1/v2/search")

        val properties = tool.definition.inputSchema["properties"]!!.jsonObject

        assertEquals("web_search", tool.definition.name)
        assertEquals(setOf("query"), tool.definition.inputSchema["required"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet())
        assertEquals(1, properties["maxResults"]!!.jsonObject["minimum"]!!.jsonPrimitive.int)
        assertEquals(10, properties["maxResults"]!!.jsonObject["maximum"]!!.jsonPrimitive.int)
        assertEquals("array", properties["includeDomains"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("array", properties["excludeDomains"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(0, properties["recencyDays"]!!.jsonObject["minimum"]!!.jsonPrimitive.int)
    }

    @Test
    fun `firecrawl search sends v2 payload and normalizes result`() = runBlocking {
        val response = """
            {
              "success": true,
              "data": {
                "web": [
                  {
                    "title": "Firecrawl title",
                    "url": "https://allowed.example/fire",
                    "description": "Firecrawl snippet",
                    "publishedDate": "2026-07-31"
                  }
                ]
              }
            }
        """.trimIndent()
        val server = server("/v2/search", response)
        val tool = tool(WebSearchProvider.FIRECRAWL, server.url("/v2/search"))

        val result = tool.execute("fire-call", arguments())

        assertEquals("POST", server.request.method)
        assertEquals("/v2/search", server.request.path)
        assertEquals("Bearer firecrawl-key", server.request.authorization)
        assertJsonEquals(
            """
                {
                  "query": "latest kotlin",
                  "limit": 3,
                  "includeDomains": ["allowed.example"],
                  "tbs": "cdr:1,cd_min:07/30/2026,cd_max:08/01/2026"
                }
            """.trimIndent(),
            server.request.body
        )
        assertNormalized(result.content, "Firecrawl title", "https://allowed.example/fire", "Firecrawl snippet", "2026-07-31")
    }

    @Test
    fun `firecrawl search omits domain filters when both lists are empty`() = runBlocking {
        val server = server("/v2/search", """{"success":true,"data":{"web":[]}}""")
        val tool = tool(WebSearchProvider.FIRECRAWL, server.url("/v2/search"))

        tool.execute(
            "fire-empty-domains-call",
            buildJsonObject {
                put("query", "latest kotlin")
                put("maxResults", 3)
            }
        )

        assertJsonEquals(
            """
                {
                  "query": "latest kotlin",
                  "limit": 3
                }
            """.trimIndent(),
            server.request.body
        )
    }

    @Test
    fun `perplexity search sends search api payload and normalizes result`() = runBlocking {
        val response = """
            {
              "results": [
                {
                  "title": "Perplexity title",
                  "url": "https://allowed.example/perplexity",
                  "snippet": "Perplexity snippet",
                  "date": "2026-07-30"
                }
              ]
            }
        """.trimIndent()
        val server = server("/search", response)
        val tool = tool(WebSearchProvider.PERPLEXITY, server.url("/search"))

        val result = tool.execute("perplexity-call", arguments())

        assertEquals("Bearer perplexity-key", server.request.authorization)
        assertJsonEquals(
            """
                {
                  "query": "latest kotlin",
                  "max_results": 3,
                  "search_domain_filter": ["allowed.example"],
                  "search_after_date_filter": "07/30/2026"
                }
            """.trimIndent(),
            server.request.body
        )
        assertNormalized(result.content, "Perplexity title", "https://allowed.example/perplexity", "Perplexity snippet", "2026-07-30")
    }

    @Test
    fun `exa search sends contents payload and normalizes result`() = runBlocking {
        val response = """
            {
              "results": [
                {
                  "title": "Exa title",
                  "url": "https://allowed.example/exa",
                  "publishedDate": "2026-07-29",
                  "highlights": ["Exa snippet"]
                }
              ]
            }
        """.trimIndent()
        val server = server("/search", response)
        val tool = tool(WebSearchProvider.EXA, server.url("/search"))

        val result = tool.execute("exa-call", arguments())

        assertEquals("", server.request.authorization)
        assertEquals("exa-key", server.request.apiKey)
        assertJsonEquals(
            """
                {
                  "query": "latest kotlin",
                  "numResults": 3,
                  "includeDomains": ["allowed.example"],
                  "startPublishedDate": "2026-07-30T12:00:00Z",
                  "contents": {"highlights": true}
                }
            """.trimIndent(),
            server.request.body
        )
        assertNormalized(result.content, "Exa title", "https://allowed.example/exa", "Exa snippet", "2026-07-29")
    }

    @Test
    fun `validation errors return bounded tool error without calling provider`() = runBlocking {
        val server = server("/search", """{"results": []}""")
        val tool = tool(WebSearchProvider.EXA, server.url("/search"))

        val result = tool.execute(
            "bad-call",
            buildJsonObject {
                put("query", " ")
                put("maxResults", 11)
                put("includeDomains", JsonArray(listOf(JsonPrimitive("allowed.example"))))
                put("excludeDomains", JsonArray(listOf(JsonPrimitive("blocked.example"))))
                put("recencyDays", -1)
            }
        )

        assertTrue(result.isError)
        assertEquals("bad-call", result.callId)
        assertTrue((result.content as ToolResultContent.Text).text.contains("query is required"))
        assertEquals("", server.request.body)
    }

    @Test
    fun `invalid argument types return tool error without calling provider`() = runBlocking {
        val server = server("/search", """{"results": []}""")
        val tool = tool(WebSearchProvider.EXA, server.url("/search"))

        val result = tool.execute(
            "bad-type-call",
            buildJsonObject {
                put("query", "latest kotlin")
                put("maxResults", "lots")
                put("includeDomains", "allowed.example")
                put("recencyDays", "soon")
            }
        )

        assertTrue(result.isError)
        val text = (result.content as ToolResultContent.Text).text
        assertTrue(text.contains("maxResults must be an integer"))
        assertTrue(text.contains("includeDomains must be an array"))
        assertTrue(text.contains("recencyDays must be an integer"))
        assertEquals("", server.request.body)
    }

    @Test
    fun `numeric arguments reject json strings without calling provider`() = runBlocking {
        val server = server("/search", """{"results": []}""")
        val tool = tool(WebSearchProvider.EXA, server.url("/search"))

        val result = tool.execute(
            "string-number-call",
            buildJsonObject {
                put("query", "latest kotlin")
                put("maxResults", "3")
                put("recencyDays", "2")
            }
        )

        assertTrue(result.isError)
        val text = (result.content as ToolResultContent.Text).text
        assertTrue(text.contains("maxResults must be an integer"))
        assertTrue(text.contains("recencyDays must be an integer"))
        assertEquals("", server.request.body)
    }

    @Test
    fun `domain arrays reject non string elements without calling provider`() = runBlocking {
        val server = server("/search", """{"results": []}""")
        val tool = tool(WebSearchProvider.EXA, server.url("/search"))

        val result = tool.execute(
            "bad-domain-element-call",
            buildJsonObject {
                put("query", "latest kotlin")
                put("includeDomains", JsonArray(listOf(JsonPrimitive("allowed.example"), JsonPrimitive(3))))
                put("excludeDomains", JsonArray(listOf(JsonNull)))
            }
        )

        assertTrue(result.isError)
        val text = (result.content as ToolResultContent.Text).text
        assertTrue(text.contains("domains must be strings"))
        assertEquals("", server.request.body)
    }

    @Test
    fun `provider http errors return tool error`() = runBlocking {
        val server = server("/search", """{"error":"rate limit"}""", status = 429)
        val tool = tool(WebSearchProvider.EXA, server.url("/search"))

        val result = tool.execute("http-call", arguments())

        assertTrue(result.isError)
        assertEquals("Web search failed: HTTP 429.", (result.content as ToolResultContent.Text).text)
    }

    @Test
    fun `missing required result fields return tool error`() = runBlocking {
        val server = server("/search", """{"results":[{"title":"Missing URL"}]}""")
        val tool = tool(WebSearchProvider.EXA, server.url("/search"))

        val result = tool.execute("missing-call", arguments())

        assertTrue(result.isError)
        assertEquals("Web search failed: missing required result fields.", (result.content as ToolResultContent.Text).text)
    }

    private fun tool(provider: WebSearchProvider, endpointUrl: String): WebSearchTool {
        val networkClient = NetworkClient(CIO)
        networkClients += networkClient
        return WebSearchTool(
            config = WebSearchProviderConfig(provider, "${provider.name.lowercase()}-key", endpointUrl),
            networkClient = networkClient,
            clock = clock
        )
    }

    private fun arguments(): JsonObject = buildJsonObject {
        put("query", "latest kotlin")
        put("maxResults", 3)
        put("includeDomains", JsonArray(listOf(JsonPrimitive("allowed.example"))))
        put("recencyDays", 2)
    }

    private fun server(path: String, response: String, status: Int = 200): RecordingServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val recorder = RecordingServer(server)
        server.createContext(path) { exchange ->
            recorder.request = RecordedRequest(
                method = exchange.requestMethod,
                path = exchange.requestURI.path,
                authorization = exchange.requestHeaders.getFirst("Authorization").orEmpty(),
                apiKey = exchange.requestHeaders.getFirst("x-api-key").orEmpty(),
                body = exchange.requestBody.bufferedReader().readText()
            )
            exchange.respond(status, response)
        }
        server.start()
        servers += server
        return recorder
    }

    private fun HttpExchange.respond(status: Int, body: String) {
        val bytes = body.toByteArray()
        responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private fun assertNormalized(
        content: ToolResultContent,
        title: String,
        url: String,
        snippet: String,
        publishedDate: String
    ) {
        val result = (content as ToolResultContent.Json).value.jsonObject["results"]!!.jsonArray.single().jsonObject
        assertEquals(title, result["title"]!!.jsonPrimitive.content)
        assertEquals(url, result["url"]!!.jsonPrimitive.content)
        assertEquals(snippet, result["snippet"]!!.jsonPrimitive.content)
        assertEquals(publishedDate, result["publishedDate"]!!.jsonPrimitive.content)
    }

    private fun assertJsonEquals(expected: String, actual: String) {
        assertEquals(NetworkClient.json.parseToJsonElement(expected), NetworkClient.json.parseToJsonElement(actual))
    }
}

private class RecordingServer(private val server: HttpServer) {
    var request: RecordedRequest = RecordedRequest("", "", "", "", "")

    fun url(path: String): String = "http://127.0.0.1:${server.address.port}$path"
}

private data class RecordedRequest(
    val method: String,
    val path: String,
    val authorization: String,
    val apiKey: String,
    val body: String
)
