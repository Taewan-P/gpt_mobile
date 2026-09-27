package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.agent.AgentRunLimits
import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolExecutionBudget
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiEngineSearchToolTest {
    @Test
    fun `all enabled marketplace engines dispatch through one web search and normalize results`() = runBlocking {
        val queried = mutableSetOf<String>()
        val fixtures = marketplaceSearchFixtures()
        val engines = fixtures.map { fixture ->
            val name = fixture.getValue("name").jsonPrimitive.content
            val tool = object : AgentTool {
                override val definition = AgentToolDefinition("mcp__provider__$name", "Web search", fixture.getValue("schema").jsonObject)
                override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult {
                    queried += name
                    assertEquals(name, fixture["expectedArguments"], arguments)
                    return AgentToolResult(callId, ToolResultContent.Text(fixture.getValue("response").jsonPrimitive.content), false)
                }
            }
            ResolvedAgentTool(tool, name, name, name, tool.definition.name)
        }
        assertEquals(listOf("web_search"), aggregateWebSearch(engines).map { it.modelToolName })
        val tool = MultiEngineSearchTool(engines, Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC))
        val result = tool.execute(
            "all",
            buildJsonObject {
                put("query", "local model speed")
                put("maxResults", 2)
                put("includeDomains", JsonArray(listOf(JsonPrimitive("example.org"))))
                put("recencyDays", 2)
            }
        )
        assertFalse(result.isError)
        assertEquals(fixtures.size, queried.size)
        val payload = (result.content as ToolResultContent.Json).value.jsonObject
        assertEquals(fixtures.size, (payload["results"] as JsonArray).size)
        val statuses = (payload["engines"] as JsonArray).map { it.jsonObject }
        assertTrue(statuses.all { it.getValue("status") == JsonPrimitive("completed") })
        assertEquals(4, statuses.count { "unsupportedFilters" in it })
    }

    private fun engine(
        name: String,
        realToolName: String = "web_search",
        extraProperties: JsonObject = JsonObject(emptyMap()),
        action: suspend (String, JsonObject) -> AgentToolResult
    ): ResolvedAgentTool {
        val tool = object : AgentTool {
            override val definition = AgentToolDefinition(
                name,
                "Web search",
                buildJsonObject {
                    put(
                        "properties",
                        buildJsonObject {
                            put("query", buildJsonObject { put("type", "string") })
                            extraProperties.forEach { (key, value) -> put(key, value) }
                        }
                    )
                    put("required", JsonArray(listOf(JsonPrimitive("query"))))
                }
            )
            override suspend fun execute(callId: String, arguments: JsonObject) = action(callId, arguments)
        }
        return ResolvedAgentTool(tool, name, name, realToolName, name)
    }

    @Test fun `official Brave MCP parameters and separate JSON blocks integrate as web search`() = runBlocking {
        val brave = engine(
            "Brave Search",
            "brave_web_search",
            buildJsonObject {
                put("count", buildJsonObject { put("type", "integer") })
                put("freshness", buildJsonObject { put("type", "string") })
                put("result_filter", buildJsonObject { put("type", "array") })
            }
        ) { id, args ->
            assertEquals(JsonPrimitive("Compose site:example.org"), args["query"])
            assertEquals(JsonPrimitive(2), args["count"])
            assertEquals(JsonPrimitive("2026-07-30to2026-08-01"), args["freshness"])
            assertEquals(JsonArray(listOf(JsonPrimitive("web"))), args["result_filter"])
            assertFalse(args.containsKey("maxResults"))
            assertFalse(args.containsKey("includeDomains"))
            mapMcpToolResult(
                id,
                CallToolResult(
                    content = listOf(
                        TextContent("""{"title":"One","url":"https://example.org/one","description":"First source"}"""),
                        TextContent("""{"title":"Two","url":"https://docs.example.org/two","description":"Second source"}"""),
                        TextContent("""{"title":"Other host","url":"https://example.org.other/three","description":"Excluded source"}""")
                    )
                )
            )
        }
        assertTrue(brave.isWebSearchEngine())
        val result = MultiEngineSearchTool(listOf(brave), Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC))
            .execute(
                "brave",
                buildJsonObject {
                    put("query", "Compose")
                    put("maxResults", 2)
                    put("includeDomains", JsonArray(listOf(JsonPrimitive("example.org"))))
                    put("recencyDays", 2)
                }
            )
        assertFalse(result.isError)
        val sources = (result.content as ToolResultContent.Json).value.jsonObject["results"] as JsonArray
        assertEquals(2, sources.size)
        assertEquals("First source", sources.first().jsonObject.getValue("snippet").jsonPrimitive.content)
        assertEquals("Brave Search", sources.first().jsonObject.getValue("engine").jsonPrimitive.content)
        assertTrue(result.content.toString().contains("https://docs.example.org/two"))
        assertFalse(result.content.toString().contains("example.org.other"))
    }

    @Test fun `invalid aggregate parameters never dispatch search requests`() = runBlocking {
        var calls = 0
        val tool = MultiEngineSearchTool(
            listOf(
                engine("Brave") { id, _ ->
                    calls++
                    AgentToolResult(id, ToolResultContent.Text("unexpected"), false)
                }
            )
        )
        for (arguments in listOf(
            buildJsonObject { put("query", true) },
            buildJsonObject {
                put("query", "news")
                put("maxResults", "2")
            },
            buildJsonObject {
                put("query", "news")
                put("maxResults", 0)
            },
            buildJsonObject {
                put("query", "news")
                put("includeDomains", JsonArray(listOf(JsonPrimitive(12))))
            }
        )) {
            assertTrue(tool.execute("invalid", arguments).isError)
        }
        assertEquals(0, calls)
    }

    @Test fun `every engine runs and duplicate sources merge despite partial failure`() = runBlocking {
        val queried = mutableSetOf<String>()
        fun search(name: String, url: String) = engine(name) { id, args ->
            queried += name
            assertEquals(JsonPrimitive("Compose"), args["query"])
            AgentToolResult(
                id,
                ToolResultContent.Json(
                    buildJsonObject {
                        put(
                            "results",
                            JsonArray(
                                listOf(
                                    buildJsonObject {
                                        put("title", name)
                                        put("url", url)
                                        put("snippet", "Found")
                                    }
                                )
                            )
                        )
                    }
                ),
                false
            )
        }
        val broken = engine("broken") { _, _ ->
            queried += "broken"
            error("unavailable")
        }
        val result = MultiEngineSearchTool(listOf(search("one", "https://example.org/doc?utm_source=one"), search("two", "https://example.org/doc#section"), broken))
            .execute("parent", buildJsonObject { put("query", "Compose") })
        assertEquals(setOf("one", "two", "broken"), queried)
        assertFalse(result.isError)
        val json = (result.content as ToolResultContent.Json).value.jsonObject
        assertEquals(1, (json["results"] as JsonArray).size)
        assertEquals(3, (json["engines"] as JsonArray).size)
        assertEquals("parent", result.callId)
    }

    @Test fun `child permissions and shared call budget cannot be bypassed by fanout`() = runBlocking {
        var dispatched = 0
        val budget = ToolExecutionBudget(AgentRunLimits(maxToolCalls = 2))
        val engines = (1..4).map { index ->
            val resolved = engine("engine$index") { id, _ ->
                dispatched++
                AgentToolResult(id, ToolResultContent.Text("source"), false)
            }
            resolved.copy(tool = budget.bind(resolved.tool, authorize = { _, _ -> index != 1 }))
        }
        val result = MultiEngineSearchTool(engines).execute("parent", buildJsonObject { put("query", "news") })
        assertEquals(1, dispatched)
        assertTrue(result.outputBudgetExhausted)
        assertFalse(result.isError)
    }

    @Test fun `private search and unsupported schemas are never broadcast targets`() {
        val web = engine("web") { _, _ -> error("not executed") }
        assertTrue(web.isWebSearchEngine())
        assertFalse(web.copy(realToolName = "slack_search").isWebSearchEngine())
        assertFalse(web.copy(realToolName = "github_search_code").isWebSearchEngine())
        assertTrue(web.copy(realToolName = "brave_web_search").isWebSearchEngine())
        assertEquals(listOf("web_search"), aggregateWebSearch(listOf(web)).map { it.modelToolName })
    }
}
