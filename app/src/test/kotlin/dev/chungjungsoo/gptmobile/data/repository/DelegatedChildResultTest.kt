package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DelegatedChildResultTest {
    @Test
    fun `direct text is a completed delegated result`() {
        val result = resolveDelegatedChildResult(
            rawText = "Useful delegated answer.",
            toolFallbacks = emptyList(),
            extractionFailed = false,
            providerFailure = null
        )

        assertEquals(DelegatedChildStatus.COMPLETED, result.status)
        assertEquals("Useful delegated answer.", result.text)
    }

    @Test
    fun `completed tool result rescues an otherwise empty delegate without another generation`() {
        val result = resolveDelegatedChildResult(
            rawText = "",
            toolFallbacks = listOf("Recovered tool result with useful evidence."),
            extractionFailed = false,
            providerFailure = null
        )

        assertEquals(DelegatedChildStatus.COMPLETED, result.status)
        assertEquals("Recovered tool result with useful evidence.", result.text)
    }

    @Test
    fun `usage or reasoning without usable text stays completed empty`() {
        val result = resolveDelegatedChildResult(
            rawText = "",
            toolFallbacks = emptyList(),
            extractionFailed = false,
            providerFailure = null
        )

        assertEquals(DelegatedChildStatus.COMPLETED_EMPTY, result.status)
        assertNull(result.text)
    }

    @Test
    fun `extraction failure is distinct from an empty completion`() {
        val result = resolveDelegatedChildResult(
            rawText = "",
            toolFallbacks = emptyList(),
            extractionFailed = true,
            providerFailure = null
        )

        assertEquals(DelegatedChildStatus.PARSE_FAILED, result.status)
        assertNull(result.text)
    }

    @Test
    fun `provider failure wins over partial content`() {
        val result = resolveDelegatedChildResult(
            rawText = "partial content",
            toolFallbacks = emptyList(),
            extractionFailed = false,
            providerFailure = "provider failed"
        )

        assertEquals(DelegatedChildStatus.FAILED, result.status)
        assertEquals("partial content", result.text)
    }

    @Test
    fun `structured completed tool output is extractable and tool errors are ignored`() {
        val json = AgentToolResult(
            callId = "json",
            content = ToolResultContent.Json(buildJsonObject { put("result", "useful") }),
            isError = false
        )
        val failed = AgentToolResult(
            callId = "failed",
            content = ToolResultContent.Text("failed tool output"),
            isError = true
        )

        assertEquals("{\"result\":\"useful\"}", usableDelegatedToolResult(json))
        assertNull(usableDelegatedToolResult(failed))
    }
}
