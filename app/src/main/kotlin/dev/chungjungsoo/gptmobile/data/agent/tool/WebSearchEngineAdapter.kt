package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.catalog.McpPresetCatalog
import java.time.Clock
import java.time.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** Adapts the discovered schema, not the model-facing namespaced tool name. */
internal class WebSearchEngineAdapter private constructor(
    private val name: String,
    private val schema: JsonObject,
    private val properties: JsonObject,
    private val queryKey: String,
    private val querySchema: JsonObject
) {
    private val brave = name in setOf("brave_web_search", "brave_search")
    private val countKey = COUNT_KEYS.firstOrNull { it in properties }
    val supportsRecency = listOf("recencyDays", "startPublishedDate", "start_date", "tbs").any { it in properties } ||
        (brave && "freshness" in properties)

    fun arguments(request: JsonObject, clock: Clock): JsonObject {
        val query = (request.getValue("query") as JsonPrimitive).content.trim()
        fun domains(key: String) = (request[key] as? JsonArray).orEmpty().map { (it as JsonPrimitive).content.trim() }
        val includes = domains("includeDomains")
        val excludes = domains("excludeDomains")
        val searchQuery = if (brave) braveSearchQuery(query, includes, excludes) else query
        val count = (request["maxResults"] as? JsonPrimitive)?.intOrNull ?: 10
        val days = (request["recencyDays"] as? JsonPrimitive)?.intOrNull
        val mapped = buildJsonObject {
            put(queryKey, queryValue(searchQuery, querySchema))
            countKey?.let { key ->
                val field = properties[key] as? JsonObject
                val minimum = (field?.get("minimum") as? JsonPrimitive)?.intOrNull ?: 1
                val maximum = (field?.get("maximum") as? JsonPrimitive)?.intOrNull ?: Int.MAX_VALUE
                put(key, count.coerceIn(minimum, maximum))
            }
            for ((aliases, values) in listOf(INCLUDE_KEYS to includes, EXCLUDE_KEYS to excludes)) {
                aliases.firstOrNull { it in properties }?.let { key ->
                    if (values.isNotEmpty()) put(key, JsonArray(values.map(::JsonPrimitive)))
                }
            }
            if (days != null) {
                val start = LocalDate.now(clock).minusDays(days.toLong())
                when {
                    "recencyDays" in properties -> put("recencyDays", days)
                    brave && "freshness" in properties -> put("freshness", braveSearchFreshness(days, clock))
                    "startPublishedDate" in properties -> put("startPublishedDate", "${start}T00:00:00Z")
                    "start_date" in properties -> put("start_date", start.toString())
                    "tbs" in properties -> put("tbs", "qdr:d${days.coerceAtLeast(1)}")
                }
            }
            if (brave && "result_filter" in properties) {
                val field = properties["result_filter"] as? JsonObject ?: JsonObject(emptyMap())
                put("result_filter", if (field.acceptsType("array")) JsonArray(listOf(JsonPrimitive("web"))) else JsonPrimitive("web"))
            }
            if (name == "firecrawl_search" && "sources" in properties) {
                val items = (properties["sources"] as? JsonObject)?.get("items") as? JsonObject
                val source = if (items?.acceptsType("string") == true) JsonPrimitive("web") else buildJsonObject { put("type", "web") }
                put("sources", JsonArray(listOf(source)))
            }
            // Bright Data's older schemas require engine; newer ones default to Google.
            if (name == "search_engine" && "engine" in properties) put("engine", "google")
        }
        return JsonObject(
            mapped + requiredKeys(schema).filterNot { it in mapped }.associateWith { key ->
                requireNotNull((properties[key] as? JsonObject)?.get("default"))
            }
        )
    }

    companion object {
        private val marketplaceNames by lazy { McpPresetCatalog.presets.flatMap { it.webSearchToolNames }.toSet() }
        private val aliases = setOf("brave_search", "bing_search", "google_search", "tavily-search", "exa_search", "duckduckgo_search")
        private val COUNT_KEYS = listOf("maxResults", "max_results", "numResults", "num_results", "count", "limit", "num")
        private val INCLUDE_KEYS = listOf("includeDomains", "include_domains")
        private val EXCLUDE_KEYS = listOf("excludeDomains", "exclude_domains")

        fun forTool(realToolName: String, definition: AgentToolDefinition): WebSearchEngineAdapter? {
            val name = realToolName.lowercase()
            val genericWebName = name == "web_search" ||
                name.startsWith("web_search_") ||
                name.endsWith("_web_search") ||
                name == "search_web" ||
                name.startsWith("search_web_")
            val nonWebQualifier = name.split('_', '-').any { it in setOf("image", "images", "video", "videos", "news", "local", "workspace", "private") }
            val describedSearch = name == "search" &&
                listOf("web search", "search the web", "search web", "internet search").any {
                    definition.description.contains(it, ignoreCase = true)
                }
            if (name !in marketplaceNames && name !in aliases && !(genericWebName && !nonWebQualifier) && !describedSearch) return null
            val schema = definition.inputSchema
            val properties = schema["properties"] as? JsonObject ?: return null
            val queryKeys = if (name == "deep_search_exa") listOf("objective") else listOf("query", "q", "search_query", "searchQuery", "queries")
            val queryKey = queryKeys.firstOrNull { it in properties } ?: return null
            val querySchema = properties[queryKey] as? JsonObject ?: return null
            // Verify query and required parameter shapes before hiding the original tool.
            if (runCatching { queryValue("search", querySchema) }.isFailure) return null
            val countKey = COUNT_KEYS.firstOrNull { it in properties }
            val requiredSupported = requiredKeys(schema).all { key ->
                key == queryKey ||
                    key == countKey ||
                    (key == "engine" && name == "search_engine" && (properties[key] as? JsonObject)?.allowsGoogle() == true) ||
                    (properties[key] as? JsonObject)?.containsKey("default") == true
            }
            return if (requiredSupported) WebSearchEngineAdapter(name, schema, properties, queryKey, querySchema) else null
        }

        private fun requiredKeys(schema: JsonObject): List<String> = (schema["required"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

        private fun JsonObject.allowsGoogle(): Boolean = (this["enum"] as? JsonArray)?.contains(JsonPrimitive("google")) != false

        private fun JsonObject.acceptsType(type: String): Boolean {
            val declared = this["type"]
            if (declared == JsonPrimitive(type) || (declared is JsonArray && JsonPrimitive(type) in declared)) return true
            return listOf("anyOf", "oneOf").any { key ->
                (this[key] as? JsonArray).orEmpty().any { (it as? JsonObject)?.acceptsType(type) == true }
            }
        }

        private fun queryValue(query: String, field: JsonObject): JsonElement {
            // Prefer a single string for union schemas such as Jina's string | string[].
            if (field.isEmpty() || field.acceptsType("string")) return JsonPrimitive(query)
            val array = if (field["type"] == JsonPrimitive("array")) {
                field
            } else {
                listOf("anyOf", "oneOf").flatMap { (field[it] as? JsonArray).orEmpty() }
                    .filterIsInstance<JsonObject>().firstOrNull { it["type"] == JsonPrimitive("array") }
            }
            val item = array?.get("items") as? JsonObject ?: error("Unsupported search query schema")
            if (item.acceptsType("string")) return JsonArray(listOf(JsonPrimitive(query)))
            val properties = item["properties"] as? JsonObject ?: error("Unsupported batch query schema")
            require((properties["query"] as? JsonObject)?.acceptsType("string") == true)
            val engine = properties["engine"] as? JsonObject
            require(engine == null || engine.allowsGoogle())
            val value = buildJsonObject {
                put("query", query)
                if (engine != null) put("engine", "google")
                requiredKeys(item).filterNot { it == "query" || (it == "engine" && engine != null) }.forEach { key ->
                    put(key, requireNotNull((properties[key] as? JsonObject)?.get("default")))
                }
            }
            return JsonArray(listOf(value))
        }
    }
}

internal fun ResolvedAgentTool.isWebSearchEngine(): Boolean = WebSearchEngineAdapter.forTool(realToolName, tool.definition) != null
