package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import java.net.URI
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** Children must already be bound to the run's shared budget and permission gate. */
class MultiEngineSearchTool(private val engines: List<ResolvedAgentTool>, private val clock: Clock = Clock.systemUTC()) : AgentTool {
    override val managesExecutionBudget = true
    override val definition = AgentToolDefinition(
        "web_search",
        "Search every enabled web search engine in parallel. Returns deduplicated sources and each engine's status. An unavailable engine does not discard other results.",
        buildJsonObject {
            put("type", "object")
            put(
                "properties",
                buildJsonObject {
                    put("query", buildJsonObject { put("type", "string") })
                    put(
                        "maxResults",
                        buildJsonObject {
                            put("type", "integer")
                            put("minimum", 1)
                            put("maximum", 10)
                        }
                    )
                    for (name in listOf("includeDomains", "excludeDomains")) {
                        put(
                            name,
                            buildJsonObject {
                                put("type", "array")
                                put("items", buildJsonObject { put("type", "string") })
                            }
                        )
                    }
                    put(
                        "recencyDays",
                        buildJsonObject {
                            put("type", "integer")
                            put("minimum", 0)
                        }
                    )
                }
            )
            put("required", JsonArray(listOf(JsonPrimitive("query"))))
            put("additionalProperties", false)
        }
    )

    override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult = coroutineScope {
        val query = (arguments["query"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull.orEmpty().trim()
        if (query.isEmpty()) return@coroutineScope AgentToolResult(callId, ToolResultContent.Text("A search query is required."), true)
        fun integer(name: String) = (arguments[name] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
        val maxResults = if (arguments.containsKey("maxResults")) integer("maxResults") else 10
        val recencyDays = integer("recencyDays")
        val domainNames = listOf("includeDomains", "excludeDomains")
        val validDomains = domainNames.all { name ->
            !arguments.containsKey(name) || (arguments[name] as? JsonArray)?.all { it is JsonPrimitive && it.isString } == true
        }
        if (maxResults == null ||
            maxResults !in 1..10 ||
            !validDomains ||
            (arguments.containsKey("recencyDays") && (recencyDays == null || recencyDays < 0))
        ) {
            return@coroutineScope AgentToolResult(callId, ToolResultContent.Text("Invalid search filters or result count. Use 1–10 results, nonnegative recency days, and domain lists."), true)
        }
        fun domains(name: String) = (arguments[name] as? JsonArray).orEmpty().map { (it as JsonPrimitive).content.trim() }
        val includeDomains = domains("includeDomains")
        val excludeDomains = domains("excludeDomains")
        if (includeDomains.isNotEmpty() && excludeDomains.isNotEmpty()) {
            return@coroutineScope AgentToolResult(callId, ToolResultContent.Text("Use either included or excluded domains."), true)
        }
        if (!(includeDomains + excludeDomains).all(::isSearchDomain) ||
            (recencyDays != null && runCatching { braveSearchFreshness(recencyDays, clock) }.isFailure)
        ) {
            return@coroutineScope AgentToolResult(callId, ToolResultContent.Text("Use valid host names and a recency within the supported calendar range."), true)
        }
        val permits = Semaphore(4)
        val responses = engines.mapIndexed { index, engine ->
            async {
                permits.withPermit {
                    try {
                        val adapter = requireNotNull(WebSearchEngineAdapter.forTool(engine.realToolName, engine.tool.definition))
                        val mapped = adapter.arguments(arguments, clock)
                        engine to engine.tool.execute("$callId:engine:$index", mapped)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        engine to AgentToolResult(callId, ToolResultContent.Text("Engine unavailable."), true)
                    }
                }
            }
        }.awaitAll()
        val seen = mutableSetOf<String>()
        val sources = mutableListOf<JsonElement>()
        val statuses = responses.map { (engine, result) ->
            val label = engine.connectionName ?: "Built-in search"
            val payload = when (val content = result.content) {
                is ToolResultContent.Json -> content.value
                is ToolResultContent.Text -> parseSearchPayload(content.text)
                is ToolResultContent.ResourceLinks -> JsonArray(
                    content.links.map { link ->
                        buildJsonObject {
                            put("url", link.uri)
                            put("title", link.name.orEmpty())
                        }
                    }
                )
            }
            val rawSources = if (result.isError) emptyList() else extractSearchSources(payload)
            val extracted = rawSources
                .filter { source -> matchesSearchDomains((source["url"] as? JsonPrimitive)?.contentOrNull.orEmpty(), includeDomains, excludeDomains) }
                .take(maxResults)
            extracted.forEach { source ->
                val url = (source["url"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
                if (seen.add(canonicalSearchUrl(url))) sources += JsonObject(source + ("engine" to JsonPrimitive(label)))
            }
            buildJsonObject {
                put("engine", label)
                put("tool", engine.realToolName)
                put("status", if (result.isError) "unavailable" else "completed")
                put("results", extracted.size)
                if (recencyDays != null && WebSearchEngineAdapter.forTool(engine.realToolName, engine.tool.definition)?.supportsRecency == false) {
                    put("unsupportedFilters", JsonArray(listOf(JsonPrimitive("recencyDays"))))
                }
                // MCP servers can return useful prose instead of structured sources.
                if (extracted.isEmpty()) {
                    val text = when (val content = result.content) {
                        is ToolResultContent.Text -> content.text
                        is ToolResultContent.Json -> content.value.toString()
                        is ToolResultContent.ResourceLinks -> content.links.joinToString { it.uri }
                    }
                    put("detail", if (!result.isError && rawSources.isNotEmpty()) "No sources matched the search filters." else text.take(6000))
                }
            }
        }
        AgentToolResult(
            callId,
            ToolResultContent.Json(
                buildJsonObject {
                    put("query", query)
                    put("engines", JsonArray(statuses))
                    put("results", JsonArray(sources))
                }
            ),
            isError = responses.all { it.second.isError },
            outputBudgetExhausted = responses.any { it.second.outputBudgetExhausted }
        )
    }
}

internal fun canonicalSearchUrl(url: String): String = runCatching {
    val uri = URI(url)
    val query = uri.rawQuery?.split('&')?.filterNot {
        val name = it.substringBefore('=').lowercase()
        name.startsWith("utm_") || name in setOf("fbclid", "gclid")
    }?.sorted()?.joinToString("&")?.takeIf { it.isNotEmpty() }
    URI(uri.scheme?.lowercase(), uri.userInfo, uri.host?.lowercase(), uri.port, uri.path.orEmpty().trimEnd('/'), query, null).toString()
}.getOrDefault(url)

/** Compose only after child authorization/budget wrappers have been installed. */
internal fun aggregateWebSearch(tools: List<ResolvedAgentTool>): List<ResolvedAgentTool> {
    val engines = tools.filter { it.isWebSearchEngine() }
    if (engines.isEmpty()) return tools
    val aggregate = MeasuredAgentTool(MultiEngineSearchTool(engines))
    return tools.filterNot { it in engines } + ResolvedAgentTool(aggregate, null, "Multi-engine search", "web_search", "web_search")
}
