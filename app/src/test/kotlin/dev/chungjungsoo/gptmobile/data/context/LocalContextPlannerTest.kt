package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalContextPlannerTest {
    @Test
    fun `large tool catalog fits a 1024 context with response and result headroom`() {
        val tools = (1..40).map { AgentToolDefinition("tool$it", "description ".repeat(20), buildJsonObject {}) }
        val plan = LocalContextPlanner.plan(emptyList(), "Hi", "Be helpful", tools, 1024, null)
        assertTrue(plan.tools.isNotEmpty())
        assertTrue(plan.omittedTools > 0)
        assertTrue(plan.estimatedPromptTokens < 1024 - 256 - 128 - 128)
        assertEquals(256, plan.toolResultBytes)
    }

    @Test
    fun `single oversized anchor is evicted instead of bypassing compaction`() {
        val huge = turn("x".repeat(40000))
        val plan = LocalContextPlanner.plan(listOf(huge), "Current question", "System", emptyList(), 1024, null)
        assertTrue(plan.priorTurns.isEmpty())
        assertEquals(1, plan.omittedTurns)
    }

    @Test
    fun `oversized anchor cannot exclude recent turns that fit`() {
        val recent = turn("Keep this recent question")
        val plan = LocalContextPlanner.plan(listOf(turn("x".repeat(40000)), recent), "Hi", "", emptyList(), 1024, null)
        assertEquals(listOf(recent), plan.priorTurns)
    }

    @Test
    fun `current prompt is rejected intact when fixed input cannot fit`() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalContextPlanner.plan(emptyList(), "x".repeat(40000), "System", emptyList(), 8192, null)
        }
    }

    @Test
    fun `unicode estimates evict history that an English character ratio would retain`() {
        val prior = turn("界".repeat(1000))
        val plan = LocalContextPlanner.plan(listOf(prior), "Hello", "", emptyList(), 1024, null)
        assertTrue(plan.priorTurns.isEmpty())
    }

    @Test
    fun `output preference never becomes the engine context size`() {
        val plan = LocalContextPlanner.plan(listOf(turn("x".repeat(2000))), "Hi", "", emptyList(), 8192, 100)
        assertFalse(plan.priorTurns.isEmpty())
    }

    private fun turn(text: String) = ConversationTurn(MessageV2(content = text, platformType = null), null, false)
}
