package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.agent.AgentRunLimits
import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolExecutionBudget
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.model.ModelDelegationSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalResearchWorkflowTest {
    private val config = ModelDelegationSettings(enabled = true, maxPages = 2, crawlDepth = 1, maxSearchQueries = 1)
    private fun tool(name: String, action: suspend (String, JsonObject) -> AgentToolResult): ResolvedAgentTool {
        val tool = object : AgentTool {
            override val definition = AgentToolDefinition(name, "", Json.parseToJsonElement(if (name == "read_url") """{"properties":{"url":{"type":"string"},"includeLinks":{"type":"boolean"}},"required":["url"]}""" else """{"properties":{"query":{"type":"string"}},"required":["query"]}""").jsonObject)
            override suspend fun execute(callId: String, arguments: JsonObject) = action(callId, arguments)
        }
        return ResolvedAgentTool(tool, null, name, name, name)
    }
    private fun response(id: String, json: String) = AgentToolResult(id, ToolResultContent.Json(Json.parseToJsonElement(json)), false)
    private fun worker(prompt: String): String = when {
        prompt.startsWith("Plan public-web") -> """{"queries":["local model latency"],"urls":[]}"""
        prompt.startsWith("Choose up to") -> """{"ids":["S1","S999"]}"""
        else -> "Measured latency is 42 ms [S1]."
    }

    @Test fun `non web tasks return to tool capable delegation without an ungrounded text answer`() = runTest {
        var generated = 0
        val result = LocalResearchWorkflow(config, emptyList(), { _, _ ->
            generated++
            """{"queries":[],"urls":[]}"""
        }).run("Inspect my repository and fix the failing test", "repository")
        assertEquals(LocalResearchOutcome.NO_RESEARCH_NEEDED, result.outcome)
        assertEquals("", result.handoff)
        assertEquals(1, generated)
    }

    @Test fun `local worker searches reads and crawls within host and page limits before handing off`() = runTest {
        val calls = mutableListOf<String>()
        val search = tool("web_search") { id, args ->
            calls += "search"
            assertEquals("local model latency", args.getValue("query").jsonPrimitive.content)
            response(id, """{"results":[{"title":"Official benchmark","url":"https://example.org/start","snippet":"Measured latency"}]}""")
        }
        val reader = tool("read_url") { id, args ->
            val url = args.getValue("url").jsonPrimitive.content
            calls += url
            assertEquals("true", args.getValue("includeLinks").jsonPrimitive.content)
            response(id, """{"content":"Latency is exactly 42 ms.","links":["https://example.org/details","https://other.example/outside","https://example.org/third"]}""")
        }
        val result = LocalResearchWorkflow(config, listOf(search, reader), { prompt, _ -> worker(prompt) }).run("Research local model latency", "turn")
        assertEquals(listOf("search", "https://example.org/start", "https://example.org/details"), calls)
        assertEquals(2, result.pagesRead)
        assertEquals(1, result.searches)
        assertTrue(result.handoff.contains("42 ms"))
        assertFalse(result.handoff.contains("S999"))
        assertFalse(result.handoff.contains("other.example"))
        assertTrue(result.handoff.toByteArray().size <= config.handoffTokens * 3)
    }

    @Test fun `disabled search and invalid plans never send guessed task text to an engine`() = runTest {
        var calls = 0
        val search = tool("web_search") { _, _ ->
            calls++
            error("Must not run")
        }
        val result = LocalResearchWorkflow(config, listOf(search), { _, _ -> "not JSON" }).run("Private task content", "bad", automatic = true)
        assertEquals(0, calls)
        assertTrue(result.handoff.contains("no guessed query"))
        val denied = LocalResearchWorkflow(config, emptyList(), { prompt, _ -> worker(prompt) }).run("Research latency", "disabled")
        assertTrue(denied.handoff.contains("not enabled"))
    }

    @Test fun `shared tool allowance prevents page fetching and parent cancellation propagates`() = runTest {
        var pageCalls = 0
        val budget = ToolExecutionBudget(AgentRunLimits(maxToolCalls = 1))
        val search = tool("web_search") { id, _ -> response(id, """{"results":[{"title":"One","url":"https://example.org/one","snippet":"Snippet"}]}""") }
        val boundedSearch = search.copy(tool = budget.bind(search.tool))
        val reader = tool("read_url") { id, _ ->
            pageCalls++
            response(id, "{}")
        }
        val result = LocalResearchWorkflow(config, listOf(boundedSearch, reader), { prompt, _ -> worker(prompt) }).run("Research latency", "budget")
        assertEquals(0, pageCalls)
        assertTrue(result.handoff.contains("tool budget"))
        val cancelled = runCatching { LocalResearchWorkflow(config, emptyList(), { _, _ -> throw CancellationException("stop") }).run("test", "cancel") }.exceptionOrNull()
        assertTrue(cancelled is CancellationException)
    }

    @Test fun `timeout retains fetched sources and reports incomplete research`() = runTest {
        val search = tool("web_search") { id, _ -> response(id, """{"results":[{"title":"One","url":"https://example.org/one","snippet":"A verified search snippet"}]}""") }
        val result = LocalResearchWorkflow(config.copy(timeoutSeconds = 5, maxLocalModelCalls = 1, maxPages = 0), listOf(search), { prompt, _ ->
            if (prompt.startsWith("Plan")) {
                worker(prompt)
            } else {
                // The workflow timeout intentionally includes serialized-worker headroom
                // (one worker timeout plus transport grace), so exceed that full window.
                delay(36_000)
                "late"
            }
        }).run("Research latency", "timeout")
        assertTrue(result.handoff.contains("timed out"))
        assertTrue(result.handoff.contains("https://example.org/one"))
        assertEquals(0, result.pagesRead)
    }

    @Test fun `legacy research limits are clamped to the search tool contract`() = runTest {
        var requestedLimit = 0
        val search = tool("web_search") { id, args ->
            requestedLimit = args.getValue("maxResults").jsonPrimitive.content.toInt()
            response(id, """{"results":[{"title":"One","url":"https://example.org/one","snippet":"Verified snippet"}]}""")
        }
        val result = LocalResearchWorkflow(config.copy(searchResultsPerEngine = 28, maxPages = 0), listOf(search), { prompt, _ -> worker(prompt) }).run("Research latency", "legacy")
        assertEquals(10, requestedLimit)
        assertEquals(LocalResearchOutcome.SUCCESS, result.outcome)
    }

    @Test fun `planned searches are not skipped merely because early search results cross the old URL threshold`() = runTest {
        var searches = 0
        val search = tool("web_search") { id, _ ->
            searches++
            response(
                id,
                """{"results":[{"title":"A","url":"https://example.org/$searches-a","snippet":"A"},{"title":"B","url":"https://example.org/$searches-b","snippet":"B"},{"title":"C","url":"https://example.org/$searches-c","snippet":"C"}]}"""
            )
        }
        val result = LocalResearchWorkflow(
            config.copy(maxSearchQueries = 3, maxPages = 0),
            listOf(search),
            { prompt, _ ->
                if (prompt.startsWith("Plan")) """{"queries":["one","two","three"],"urls":[]}""" else "Evidence [S1]."
            }
        ).run("Research all three query angles", "threshold")

        assertEquals(3, searches)
        assertEquals(3, result.searches)
        assertFalse(result.handoff.contains("remaining searches were skipped"))
    }

    @Test fun `unreadable first source falls through to another candidate without consuming verified page target`() = runTest {
        val reads = mutableListOf<String>()
        val search = tool("web_search") { id, _ ->
            response(
                id,
                """{"results":[{"title":"Blocked","url":"https://example.org/blocked","snippet":"blocked snippet"},{"title":"Readable","url":"https://example.org/readable","snippet":"readable snippet"}]}"""
            )
        }
        val reader = tool("read_url") { id, args ->
            val url = args.getValue("url").jsonPrimitive.content
            reads += url
            if (url.endsWith("/blocked")) {
                AgentToolResult(id, ToolResultContent.Text("Read URL failed: consent wall."), true)
            } else {
                response(id, """{"content":"Verified article says latency is 42 ms.","links":[]}""")
            }
        }
        val result = LocalResearchWorkflow(
            config.copy(maxPages = 1, crawlDepth = 0),
            listOf(search, reader),
            { prompt, _ ->
                when {
                    prompt.startsWith("Plan") -> """{"queries":["latency"],"urls":[]}"""
                    prompt.startsWith("Choose up to") -> """{"ids":["S1"]}"""
                    else -> "Latency is 42 ms [S2]."
                }
            }
        ).run("Research latency", "fallback-source")

        assertEquals(listOf("https://example.org/blocked", "https://example.org/readable"), reads)
        assertEquals(1, result.pagesRead)
        assertTrue(result.handoff.contains("42 ms"))
    }

    @Test fun `research authorized at start is not aborted when live settings change mid run`() = runTest {
        var enabledChecks = 0
        var searches = 0
        val search = tool("web_search") { id, _ ->
            searches++
            response(id, """{"results":[{"title":"One","url":"https://example.org/one","snippet":"Verified snippet"}]}""")
        }
        val result = LocalResearchWorkflow(
            config.copy(maxPages = 0),
            listOf(search),
            { prompt, _ -> if (prompt.startsWith("Plan")) """{"queries":["one"],"urls":[]}""" else "Verified [S1]." },
            stillEnabled = {
                enabledChecks++
                enabledChecks == 1
            }
        ).run("Research one", "settings-race")

        assertEquals(1, searches)
        assertEquals(LocalResearchOutcome.SUCCESS, result.outcome)
        assertFalse(result.handoff.contains("disabled before research completed"))
    }

    @Test fun `unusable search providers stop early without claiming successful research`() = runTest {
        for (isError in listOf(true, false)) {
            var searches = 0
            val search = tool("web_search") { id, _ ->
                searches++
                AgentToolResult(id, ToolResultContent.Text("No usable results"), isError)
            }
            val result = LocalResearchWorkflow(config.copy(maxSearchQueries = 12), listOf(search), { _, _ ->
                """{"queries":["one","two","three","four"],"urls":[]}"""
            }).run("Research latency", "unavailable")
            assertEquals(2, searches)
            assertEquals(LocalResearchOutcome.NO_USEFUL_OUTPUT, result.outcome)
            assertTrue(result.handoff.contains("Search stopped"))
        }
    }
}
