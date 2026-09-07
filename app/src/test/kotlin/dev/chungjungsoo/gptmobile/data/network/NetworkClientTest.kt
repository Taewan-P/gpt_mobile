package dev.chungjungsoo.gptmobile.data.network

import io.ktor.client.plugins.logging.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkClientTest {

    @Test
    fun `network logging avoids request body logging`() {
        assertEquals(LogLevel.HEADERS, NetworkClient.resolveNetworkLogLevel())
    }

    @Test
    fun `sensitive provider credential headers are sanitized case insensitively`() {
        assertTrue(NetworkClient.isSensitiveHeader("Authorization"))
        assertTrue(NetworkClient.isSensitiveHeader("authorization"))
        assertTrue(NetworkClient.isSensitiveHeader("x-goog-api-key"))
        assertTrue(NetworkClient.isSensitiveHeader("X-Goog-Api-Key"))
        assertTrue(NetworkClient.isSensitiveHeader("x-api-key"))
        assertTrue(NetworkClient.isSensitiveHeader("X-API-KEY"))
        assertTrue(NetworkClient.isSensitiveHeader("Mcp-Session-Id"))
        assertTrue(NetworkClient.isSensitiveHeader("mcp-session-id"))
        assertFalse(NetworkClient.isSensitiveHeader("Content-Type"))
    }

    @Test
    fun `anthropic credential header is sanitized case insensitively`() {
        assertTrue(NetworkClient.isSensitiveHeader("x-api-key"))
        assertTrue(NetworkClient.isSensitiveHeader("X-Api-Key"))
    }
}
