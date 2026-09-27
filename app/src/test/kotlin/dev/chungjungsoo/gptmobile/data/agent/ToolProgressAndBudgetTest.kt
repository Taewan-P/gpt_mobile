package dev.chungjungsoo.gptmobile.data.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ToolProgressAndBudgetTest {
    @Test fun `orchestrators release permits for budgeted children at concurrency one`() = runBlocking {
        val budget = ToolExecutionBudget(AgentRunLimits(maxConcurrentTools = 1, maxToolCalls = 4, toolTimeoutMillis = 100))
        val child = budget.bind(object : AgentTool {
            override val definition = AgentToolDefinition("read", "", buildJsonObject {})
            override suspend fun execute(callId: String, arguments: JsonObject) = AgentToolResult(callId, ToolResultContent.Text("observed source"), false)
        })
        val parent = budget.bind(object : AgentTool {
            override val definition = AgentToolDefinition("research", "", buildJsonObject {})
            override val managesExecutionBudget = true
            override suspend fun execute(callId: String, arguments: JsonObject) = child.execute("$callId:child", arguments)
        })
        val result = kotlinx.coroutines.withTimeout(1000) { parent.execute("parent", buildJsonObject {}) }
        assertFalse(result.isError)
        assertEquals("observed source", (result.content as ToolResultContent.Text).text)
    }

    @Test fun `search excerpts remain visible when a result is truncated`() = runBlocking {
        val budget = ToolExecutionBudget(AgentRunLimits(maxToolOutputBytes = 48))
        val tool = budget.bind(object : AgentTool {
            override val definition = AgentToolDefinition("web_search", "", buildJsonObject {})
            override suspend fun execute(callId: String, arguments: JsonObject) =
                AgentToolResult(callId, ToolResultContent.Text("Kotlin documentation https://kotlinlang.org " + "snippet ".repeat(30)), false)
        })
        val result = tool.execute("search", buildJsonObject {})
        assertTrue(result.outputBudgetExhausted)
        assertTrue((result.content as ToolResultContent.Text).text.contains("https://kotlinlang.org"))
        val trace = (result.traceContent as ToolResultContent.Text).text
        assertTrue(trace.contains("https://kotlinlang.org"))
        assertTrue(trace.contains("truncated"))
        val exhausted = tool.execute("read-next", buildJsonObject {})
        assertTrue(exhausted.isError)
        assertTrue(exhausted.outputBudgetExhausted)
        assertEquals(OUTPUT_BUDGET_EXHAUSTED, (exhausted.content as ToolResultContent.Text).text)
    }

    @Test fun `zero or sub-codepoint budgets never produce an empty error`() = runBlocking {
        for (bytes in listOf(0, 1, 3)) {
            val tool = ToolExecutionBudget(AgentRunLimits(maxToolOutputBytes = bytes)).bind(object : AgentTool {
                override val definition = AgentToolDefinition("read_url", "", buildJsonObject {})
                override suspend fun execute(callId: String, arguments: JsonObject) = AgentToolResult(callId, ToolResultContent.Text("😀"), true)
            })
            val result = tool.execute("read", buildJsonObject {})
            assertTrue(result.isError)
            assertTrue((result.content as ToolResultContent.Text).text.isNotBlank())
        }
    }

    @Test fun `truncation preserves explicitly redacted trace content`() = runBlocking {
        val tool = ToolExecutionBudget(AgentRunLimits(maxToolOutputBytes = 8)).bind(object : AgentTool {
            override val definition = AgentToolDefinition("private", "", buildJsonObject {})
            override suspend fun execute(callId: String, arguments: JsonObject) =
                AgentToolResult(callId, ToolResultContent.Text("sensitive-result"), false, traceContent = ToolResultContent.Text("Hidden"))
        })
        val trace = (tool.execute("private", buildJsonObject {}).traceContent as ToolResultContent.Text).text
        assertTrue(trace.startsWith("Hidden"))
        assertFalse(trace.contains("sensitive"))
    }

    @Test fun `progress occurs at ten distinct completions and includes failures without payloads`() {
        val tracker = ToolProgressTracker()
        (1..9).forEach { assertNull(tracker.complete("$it", "search", false)) }
        assertNull(tracker.complete("1", "duplicate", false))
        val first = tracker.complete("10", "read_file", true)!!
        assertTrue(first.startsWith("10 tool calls"))
        assertTrue(first.contains("1 of the last 10 calls failed"))
        (11..19).forEach { assertNull(tracker.complete("$it", "search", false)) }
        assertTrue(tracker.complete("20", "search", false)!!.startsWith("20 tool calls"))
    }

    @Test fun `public progress tags survive every possible stream split`() {
        val full = "Answer before.<progress_update>I checked sources. Next I will compare.</progress_update>Answer after."
        for (split in 0..full.length) {
            val parser = PublicProgressParser()
            val pieces = parser.accept(full.take(split)) + parser.accept(full.drop(split)) + parser.accept("", flush = true)
            assertEquals("Answer before.Answer after.", pieces.filterNot { it.first }.joinToString("") { it.second })
            assertEquals("I checked sources. Next I will compare.", pieces.filter { it.first }.joinToString("") { it.second })
        }
    }

    @Test fun `native and remote bindings share count and UTF8 output budget`() = runBlocking {
        var executions = 0
        val budget = ToolExecutionBudget(AgentRunLimits(maxToolCalls = 2, maxToolOutputBytes = 7))
        val tool = budget.bind(object : AgentTool {
            override val definition = AgentToolDefinition("read", "", buildJsonObject {})
            override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult {
                executions++
                return AgentToolResult(callId, ToolResultContent.Text("😀abc"), false)
            }
        })
        assertEquals("😀abc", (tool.execute("1", buildJsonObject {}).content as ToolResultContent.Text).text)
        assertTrue(tool.execute("2", buildJsonObject {}).isError)
        assertEquals(1, executions)
        assertEquals("😀", truncateUtf8("😀abc", 4))
        assertEquals("", truncateUtf8("😀", 3))
    }

    @Test fun `approval waiting is outside tool execution timeout and cancellation propagates`() = runBlocking {
        val budget = ToolExecutionBudget(AgentRunLimits(toolTimeoutMillis = 5))
        val tool = object : AgentTool {
            override val definition = AgentToolDefinition("write", "", buildJsonObject {})
            override suspend fun execute(callId: String, arguments: JsonObject) = AgentToolResult(callId, ToolResultContent.Text("ok"), false)
        }
        assertFalse(
            budget.bind(tool) { _, _ ->
                delay(20)
                true
            }.execute("1", buildJsonObject {}).isError
        )
        try {
            budget.bind(tool) { _, _ -> throw CancellationException() }.execute("2", buildJsonObject {})
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
}
