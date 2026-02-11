package dev.chungjungsoo.gptmobile.data.tool.builtin

import dev.chungjungsoo.gptmobile.data.dto.tool.Tool
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.tool.BuiltInTool
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class WebSearchTool @Inject constructor(
    private val networkClient: NetworkClient
) : BuiltInTool {

    override val definition: Tool = Tool(
        name = "web_search",
        description = "Search the web and return concise result snippets",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("properties", buildJsonObject {
                put("query", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Search query"))
                })
                put("count", buildJsonObject {
                    put("type", JsonPrimitive("integer"))
                    put("description", JsonPrimitive("Number of results to return (1-20)"))
                    put("default", JsonPrimitive(5))
                })
            })
            put("required", buildJsonArray { add(JsonPrimitive("query")) })
        }
    )

    override suspend fun execute(arguments: JsonObject): String {
        val query = arguments["query"]?.let { it.jsonPrimitive.content }?.trim().orEmpty()
        if (query.isBlank()) {
            return jsonError("Missing query parameter")
        }

        val count = (arguments["count"]?.let { it.jsonPrimitive.content.toIntOrNull() } ?: DEFAULT_COUNT)
            .coerceIn(1, MAX_RESULTS)

        return runCatching {
            val response = networkClient().get(SEARCH_URL) {
                parameter("q", query)
                parameter("format", "json")
                parameter("no_html", 1)
                parameter("skip_disambig", 1)
            }
            val body = response.body<String>()
            val root = Json.parseToJsonElement(body).jsonObject
            val results = mutableListOf<JsonObject>()

            appendPrimaryResult(root, results)
            appendRelatedResults(root["RelatedTopics"]?.jsonArray ?: JsonArray(emptyList()), results)

            val limited = results.take(count)
            buildJsonObject {
                put("query", JsonPrimitive(query))
                put("results", buildJsonArray {
                    limited.forEach { add(it) }
                })
            }.toString()
        }.getOrElse { throwable ->
            jsonError(throwable.message ?: "Failed to run web_search")
        }
    }

    private fun appendPrimaryResult(root: JsonObject, results: MutableList<JsonObject>) {
        val abstractText = root["AbstractText"]?.let { it.jsonPrimitive.content }.orEmpty().trim()
        val abstractUrl = root["AbstractURL"]?.let { it.jsonPrimitive.content }.orEmpty().trim()
        val heading = root["Heading"]?.let { it.jsonPrimitive.content }.orEmpty().trim()

        if (abstractText.isNotBlank() || abstractUrl.isNotBlank()) {
            results += buildJsonObject {
                put("title", JsonPrimitive(if (heading.isNotBlank()) heading else abstractUrl))
                put("url", JsonPrimitive(abstractUrl))
                put("snippet", JsonPrimitive(abstractText))
            }
        }
    }

    private fun appendRelatedResults(relatedTopics: JsonArray, results: MutableList<JsonObject>) {
        relatedTopics.forEach { item ->
            val obj = item.jsonObject
            val nestedTopics = obj["Topics"]
            if (nestedTopics != null) {
                appendRelatedResults(nestedTopics.jsonArray, results)
                return@forEach
            }

            val text = obj["Text"]?.let { it.jsonPrimitive.content }.orEmpty().trim()
            val firstUrl = obj["FirstURL"]?.let { it.jsonPrimitive.content }.orEmpty().trim()
            if (text.isBlank() && firstUrl.isBlank()) {
                return@forEach
            }

            results += buildJsonObject {
                put("title", JsonPrimitive(text.take(80)))
                put("url", JsonPrimitive(firstUrl))
                put("snippet", JsonPrimitive(text))
            }
        }
    }

    private fun jsonError(message: String): String = buildJsonObject {
        put("error", JsonPrimitive(message))
    }.toString()

    companion object {
        private const val SEARCH_URL = "https://api.duckduckgo.com/"
        private const val DEFAULT_COUNT = 5
        private const val MAX_RESULTS = 20
    }
}
