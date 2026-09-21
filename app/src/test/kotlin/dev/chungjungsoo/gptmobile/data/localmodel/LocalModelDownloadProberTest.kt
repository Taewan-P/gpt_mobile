package dev.chungjungsoo.gptmobile.data.localmodel

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelDownloadProberTest {

    @Test
    fun `probe sends a ranged get and returns the status code`() {
        ProbeServer(statusCode = 206).use { server ->
            val status = LocalModelDownloadProberImpl().probe(server.url, accessToken = null)

            assertEquals(206, status)
            assertEquals("bytes=0-0", server.rangeHeader)
            assertEquals("identity", server.acceptEncoding)
        }
    }

    @Test
    fun `probe does not attach a bearer token to non-huggingface urls`() {
        ProbeServer(statusCode = 200).use { server ->
            val status = LocalModelDownloadProberImpl().probe(server.url, accessToken = "hf_secret")

            assertEquals(200, status)
            assertEquals(null, server.authorizationHeader)
        }
    }

    @Test
    fun `probe returns 401 without attaching a missing token`() {
        ProbeServer(statusCode = 401).use { server ->
            val status = LocalModelDownloadProberImpl().probe(server.url, accessToken = null)

            assertEquals(401, status)
            assertEquals(null, server.authorizationHeader)
        }
    }

    @Test
    fun `unreachable url is a network error`() {
        val status = LocalModelDownloadProberImpl().probe("http://127.0.0.1:1/missing", accessToken = null)
        assertEquals(LocalModelDownloadProberImpl.NETWORK_ERROR, status)
    }

    @Test
    fun `probe does not log the token`() {
        val output = capturingStderr {
            ProbeServer(statusCode = 200).use { server ->
                LocalModelDownloadProberImpl().probe(server.url, accessToken = "hf_must_not_appear")
            }
        }
        assertTrue("token leaked in logs:\n$output", !output.contains("hf_must_not_appear"))
    }

    private fun capturingStderr(block: () -> Unit): String {
        val original = System.err
        val buffer = java.io.ByteArrayOutputStream()
        System.setErr(java.io.PrintStream(buffer))
        return try {
            block()
            buffer.toString()
        } finally {
            System.setErr(original)
        }
    }
}

private class ProbeServer(
    private val statusCode: Int
) : AutoCloseable {
    var rangeHeader: String? = null
        private set
    var acceptEncoding: String? = null
        private set
    var authorizationHeader: String? = null
        private set

    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        createContext("/model") { exchange ->
            rangeHeader = exchange.requestHeaders.getFirst("Range")
            acceptEncoding = exchange.requestHeaders.getFirst("Accept-Encoding")
            authorizationHeader = exchange.requestHeaders.getFirst("Authorization")
            exchange.sendResponseHeaders(statusCode, -1)
            exchange.close()
        }
        start()
    }

    val url: String = "http://127.0.0.1:${server.address.port}/model"

    override fun close() {
        server.stop(0)
    }
}
