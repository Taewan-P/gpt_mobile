package dev.chungjungsoo.gptmobile.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LocalNetworkAccessTest {
    @Test
    fun `local URLs require Android local network permission`() {
        assertTrue(requiresLocalNetworkAccess("http://localhost:11434"))
        assertTrue(requiresLocalNetworkAccess("http://10.0.2.2:8080/mcp"))
        assertTrue(requiresLocalNetworkAccess("https://192.168.1.10/mcp"))
        assertTrue(requiresLocalNetworkAccess("https://tools.local/mcp"))
        assertTrue(requiresLocalNetworkAccess("https://ollama:11434"))
        assertTrue(requiresLocalNetworkAccess("localhost:11434"))
        assertTrue(requiresLocalNetworkAccess("192.168.1.10:11434"))
    }

    @Test
    fun `public and malformed URLs do not request local network permission`() {
        assertFalse(requiresLocalNetworkAccess("https://api.openai.com/v1"))
        assertFalse(requiresLocalNetworkAccess("https://mcp.example.com"))
        assertFalse(requiresLocalNetworkAccess("https://fea.example.com"))
        assertFalse(requiresLocalNetworkAccess("https://fd.example.com"))
        assertFalse(requiresLocalNetworkAccess("not a URL"))
    }

    @Test
    fun `private IPv6 literals require Android local network permission`() {
        assertTrue(requiresLocalNetworkAccess("http://[fe80::1]:8080/mcp"))
        assertTrue(requiresLocalNetworkAccess("http://[fd00::1]:8080/mcp"))
    }

    @Test
    fun `connection lookup failure requires permission and reports the failure`() = runTest {
        val failure = IllegalStateException("database unavailable")
        var reportedFailure: Exception? = null

        val required = determineLocalNetworkAccessRequirement(
            providerNeedsAccess = false,
            toolNeedsAccess = { throw failure },
            onLookupFailure = { reportedFailure = it }
        )

        assertTrue(required)
        assertEquals(failure, reportedFailure)
    }

    @Test
    fun `connection lookup preserves coroutine cancellation`() = runTest {
        try {
            determineLocalNetworkAccessRequirement(
                providerNeedsAccess = false,
                toolNeedsAccess = { throw CancellationException("stopped") },
                onLookupFailure = { fail("Cancellation must not be reported as a lookup failure") }
            )
            fail("Expected cancellation")
        } catch (_: CancellationException) {
        }
    }
}
