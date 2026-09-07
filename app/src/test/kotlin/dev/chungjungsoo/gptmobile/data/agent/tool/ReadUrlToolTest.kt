package dev.chungjungsoo.gptmobile.data.agent.tool

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Dns
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadUrlToolTest {

    private val servers = mutableListOf<HttpServer>()

    @After
    fun stopServers() {
        servers.forEach { it.stop(0) }
    }

    @Test
    fun `definition exposes only required url string`() {
        val tool = tool(dns = dns("fixture.test", "93.184.216.34"))

        val schema = tool.definition.inputSchema
        val properties = schema["properties"]!!.jsonObject

        assertEquals("read_url", tool.definition.name)
        assertEquals(setOf("url"), schema["required"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet())
        assertEquals("string", properties["url"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(false, schema["additionalProperties"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(setOf("url"), properties.keys)
    }

    @Test
    fun `special use helper rejects unsafe address ranges`() {
        val rejected = listOf(
            "127.0.0.1",
            "10.1.2.3",
            "172.16.0.1",
            "192.168.1.1",
            "169.254.169.254",
            "100.64.0.1",
            "198.18.0.1",
            "192.0.2.1",
            "198.51.100.1",
            "203.0.113.1",
            "224.0.0.1",
            "240.0.0.1",
            "::1",
            "fc00::1",
            "fd00::1",
            "2001:db8::1",
            "::ffff:10.1.2.3",
            "64:ff9b::7f00:1",
            "64:ff9b:1::1",
            "2002:7f00:1::",
            "2001:0:4136:e378:8000:63bf:3fff:fdd2",
            "::127.0.0.1"
        )

        rejected.forEach { address ->
            assertTrue(address, SpecialUseAddress.isSpecialUse(InetAddress.getByName(address)))
        }
        assertFalse(SpecialUseAddress.isSpecialUse(InetAddress.getByName("93.184.216.34")))
        assertFalse(SpecialUseAddress.isSpecialUse(InetAddress.getByName("2606:2800:220:1:248:1893:25c8:1946")))
    }

    @Test
    fun `loopback private and mixed DNS answers are rejected`() = runBlocking {
        val cases = listOf(
            dns("fixture.test", "127.0.0.1"),
            dns("fixture.test", "10.0.0.5"),
            dns("fixture.test", "93.184.216.34", "192.168.1.10")
        )

        cases.forEachIndexed { index, dns ->
            val result = tool(dns = dns).execute("call-$index", args("http://fixture.test/"))

            assertTrue(result.isError)
            assertContains(result.text(), "unsafe DNS")
        }
    }

    @Test
    fun `DNS answer is pinned despite fake rebind`() = runBlocking {
        val server = server { exchange ->
            exchange.respond(200, "text/plain; charset=utf-8", "first")
        }
        val dns = FlippingDns("fixture.test", "127.0.0.1", "203.0.113.1")

        val result = tool(dns = dns, allowTestLoopback = true).execute("call-1", args(server.url("fixture.test", "/")))

        assertEquals(false, result.isError)
        assertEquals("first", result.text())
        assertEquals(1, dns.lookupCount)
    }

    @Test
    fun `HTTP text success returns normalized text`() = runBlocking {
        val server = server { exchange ->
            exchange.respond(200, "text/plain; charset=utf-8", "  Hello,\n\n\tworld.  ")
        }

        val result = tool(allowTestLoopback = true).execute("call-1", args(server.url("fixture.test", "/text")))

        assertEquals(false, result.isError)
        assertEquals("Hello, world.", result.text())
    }

    @Test
    fun `declared charset decodes text with UTF-8 fallback`() = runBlocking {
        val latin1 = server { exchange ->
            exchange.respond(200, "text/plain; charset=iso-8859-1", byteArrayOf(0xE9.toByte()))
        }
        val fallback = server { exchange ->
            exchange.respond(200, "text/plain", "한글".toByteArray(Charsets.UTF_8))
        }

        val latin1Result = tool(allowTestLoopback = true).execute("call-1", args(latin1.url("fixture.test", "/latin1")))
        val fallbackResult = tool(allowTestLoopback = true).execute("call-2", args(fallback.url("fixture.test", "/utf8")))

        assertEquals(false, latin1Result.isError)
        assertEquals("é", latin1Result.text())
        assertEquals(false, fallbackResult.isError)
        assertEquals("한글", fallbackResult.text())
    }

    @Test
    fun `redirect validates second host`() = runBlocking {
        val server = server { exchange ->
            exchange.respond(302, "text/plain", "", "Location" to "http://private.test/secret")
        }
        val result = tool(
            dns = dns(
                "fixture.test" to listOf("127.0.0.1"),
                "private.test" to listOf("10.0.0.8")
            ),
            allowTestLoopback = true
        ).execute("call-1", args(server.url("fixture.test", "/redirect")))

        assertTrue(result.isError)
        assertContains(result.text(), "unsafe DNS")
    }

    @Test
    fun `redirect stops after five hops`() = runBlocking {
        val server = server { exchange ->
            val next = exchange.requestURI.path.removePrefix("/").toInt() + 1
            exchange.respond(302, "text/plain", "", "Location" to "/$next")
        }

        val result = tool(allowTestLoopback = true).execute("call-1", args(server.url("fixture.test", "/0")))

        assertTrue(result.isError)
        assertContains(result.text(), "too many redirects")
    }

    @Test
    fun `non-2xx missing location binary and oversize responses are errors`() = runBlocking {
        val notFound = server { it.respond(404, "text/plain", "no") }
        val missingLocation = server { it.respond(302, "text/plain", "") }
        val binary = server { it.respond(200, "application/octet-stream", "abc") }
        val oversize = server { it.respond(200, "text/plain", "a".repeat(1024 * 1024 + 1)) }

        val cases = listOf(
            notFound.url("fixture.test", "/") to "HTTP 404",
            missingLocation.url("fixture.test", "/") to "missing Location",
            binary.url("fixture.test", "/") to "binary content",
            oversize.url("fixture.test", "/") to "too large"
        )

        cases.forEachIndexed { index, (url, message) ->
            val result = tool(allowTestLoopback = true).execute("call-$index", args(url))

            assertTrue(result.isError)
            assertContains(result.text(), message)
        }
    }

    @Test
    fun `UTF-8 output cap does not split code points`() = runBlocking {
        val server = server { exchange ->
            exchange.respond(200, "text/plain; charset=utf-8", "a".repeat(65524) + "😀".repeat(4))
        }

        val result = tool(allowTestLoopback = true).execute("call-1", args(server.url("fixture.test", "/emoji")))

        val text = result.text()
        assertEquals(false, result.isError)
        assertEquals(65524 + 4 * 3, text.toByteArray(Charsets.UTF_8).size)
        assertEquals("a".repeat(65524) + "😀".repeat(3), text)
    }

    @Test
    fun `malformed URL arguments are rejected`() = runBlocking {
        val cases = listOf(
            "",
            "ftp://fixture.test/",
            "http:///",
            "http://user@fixture.test/",
            "http://fixture.test/path#fragment",
            "http://fixture test/"
        )

        cases.forEachIndexed { index, url ->
            val result = tool().execute("call-$index", args(url))

            assertTrue(result.isError)
        }
    }

    private fun tool(
        dns: Dns = dns("fixture.test", "127.0.0.1"),
        allowTestLoopback: Boolean = false
    ): ReadUrlTool = ReadUrlTool(
        dns = dns,
        allowAddress = { allowTestLoopback && it.isLoopbackAddress }
    )

    private fun args(url: String) = buildJsonObject {
        put("url", url)
    }

    private fun server(handler: (HttpExchange) -> Unit): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange -> handler(exchange) }
        server.start()
        servers += server
        return server
    }

    private fun HttpServer.url(host: String, path: String): String = "http://$host:$port$path"

    private val HttpServer.port: Int
        get() = address.port

    private fun HttpExchange.respond(
        status: Int,
        contentType: String,
        body: String,
        vararg headers: Pair<String, String>
    ) = respond(status, contentType, body.toByteArray(Charsets.UTF_8), *headers)

    private fun HttpExchange.respond(
        status: Int,
        contentType: String,
        body: ByteArray,
        vararg headers: Pair<String, String>
    ) {
        responseHeaders.add("Content-Type", contentType)
        headers.forEach { (name, value) -> responseHeaders.add(name, value) }
        sendResponseHeaders(status, body.size.toLong())
        responseBody.use { it.write(body) }
    }

    private fun dns(host: String, vararg addresses: String): Dns = dns(host to addresses.toList())

    private fun dns(vararg entries: Pair<String, List<String>>): Dns {
        val map = entries.toMap()
        return Dns { host ->
            map.getValue(host).map { InetAddress.getByName(it) }
        }
    }

    private fun dev.chungjungsoo.gptmobile.data.agent.AgentToolResult.text(): String = (content as ToolResultContent.Text).text

    private fun assertContains(value: String, expected: String) {
        assertTrue("Expected <$value> to contain <$expected>", value.contains(expected))
    }

    private class FlippingDns(
        private val host: String,
        private val firstAddress: String,
        private val reboundAddress: String
    ) : Dns {
        var lookupCount = 0

        override fun lookup(hostname: String): List<InetAddress> {
            check(hostname == host)
            lookupCount += 1
            return listOf(InetAddress.getByName(if (lookupCount == 1) firstAddress else reboundAddress))
        }
    }
}
