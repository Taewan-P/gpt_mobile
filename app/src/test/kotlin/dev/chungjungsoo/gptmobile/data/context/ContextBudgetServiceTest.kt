package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.EndpointLocality
import dev.chungjungsoo.gptmobile.data.model.endpointLocality
import dev.chungjungsoo.gptmobile.data.model.isPrivateDestination
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBudgetServiceTest {
    @Test fun `defaults preserve context and use fifty tool calls`() {
        val turns = (0..9).map { ConversationTurn(MessageV2(content = "history ".repeat(3000), platformType = null), null, it == 9) }
        val plan = ContextBudgetService.plan(turns, "system", emptyList(), TokenBudgetSettings())
        assertEquals(turns, plan.turns)
        assertNull(plan.outputTokens)
        assertEquals(Int.MAX_VALUE, TokenBudgetSettings().contextTokens)
        assertEquals(Int.MAX_VALUE, TokenBudgetSettings().totalRunTokens)
        assertEquals(50, dev.chungjungsoo.gptmobile.data.agent.AgentRunLimits().maxToolCalls)
        assertEquals(50, PlatformV2(name = "New model").maxToolCalls)
    }

    @Test fun `explicit output limits above the old cap are retained`() {
        val plan = ContextBudgetService.plan(emptyList(), "", emptyList(), TokenBudgetSettings(outputTokens = 100000))
        assertEquals(100000, plan.outputTokens)
    }

    @Test fun `context reservation does not silently lower explicit output limits`() {
        val plan = ContextBudgetService.plan(emptyList(), "", emptyList(), TokenBudgetSettings(contextTokens = 4096, outputTokens = 2048))
        assertEquals(2048, plan.outputTokens)
    }

    @Test fun `small text only contexts do not reserve space for unavailable tools`() {
        val current = ConversationTurn(MessageV2(content = "Hi", platformType = null), null, true)
        val plan = ContextBudgetService.plan(listOf(current), "Concise reply.", emptyList(), TokenBudgetSettings(contextTokens = 256))
        assertEquals(listOf(current), plan.turns)
        assertEquals(0, plan.toolResultBytes)
        assertNull(plan.outputTokens)
    }

    @Test
    fun `discovery only accepts a matching model and explicit bounded context limit`() {
        val body = """{"data":[{"id":"large","context_length":128000},{"id":"small","context_length":4096}]}"""
        assertEquals(4096, discoverContextCeiling(body, "small"))
        assertNull(discoverContextCeiling(body, "unknown"))
        assertNull(discoverContextCeiling("""{"context_length":-1}""", "model"))
    }

    @Test fun `tools history and output share one context reservation without dropping current input`() {
        val turns = (0..9).map { ConversationTurn(MessageV2(content = "history ".repeat(300), platformType = null), null, it == 9) }
        val plan = ContextBudgetService.plan(turns, "system", listOf(AgentToolDefinition("tool", "description".repeat(400), buildJsonObject {})), TokenBudgetSettings(contextTokens = 2048, outputTokens = 512))
        assertTrue(plan.turns.last().isCurrentTurn)
        assertTrue(plan.turns.size < turns.size)
        assertTrue(plan.notice.contains("omitted"))
        assertEquals(512, plan.outputTokens)
        assertEquals(256 * 3, plan.toolResultBytes)
    }

    @Test fun `oversize current message fails visibly rather than dropping the user request`() {
        assertTrue(
            runCatching {
                ContextBudgetService.plan(listOf(ConversationTurn(MessageV2(content = "x".repeat(50000), platformType = null), null, true)), "", emptyList(), TokenBudgetSettings(contextTokens = 2048))
            }.isFailure
        )
    }

    @Test fun `privacy depends on endpoint rather than llama or ollama label`() {
        assertFalse(PlatformV2(name = "remote", compatibleType = ClientType.OLLAMA, apiUrl = "https://external.example").isPrivateDestination())
        assertTrue(PlatformV2(name = "private", compatibleType = ClientType.CUSTOM, apiUrl = "http://192.168.1.2:8000").isPrivateDestination())
        assertEquals(EndpointLocality.EXTERNAL, endpointLocality("https://192.168.1.2.external.example"))
        assertEquals(EndpointLocality.EXTERNAL, endpointLocality("http://127.evil.example"))
        assertEquals(EndpointLocality.PRIVATE_NETWORK, endpointLocality("http://[fd00::1]:8000"))
    }
}
