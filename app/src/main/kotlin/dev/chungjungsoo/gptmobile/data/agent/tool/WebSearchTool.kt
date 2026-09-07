package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class WebSearchProvider {
    FIRECRAWL,
    PERPLEXITY,
    EXA
}

data class WebSearchProviderConfig(
    val provider: WebSearchProvider,
    val bearerToken: String,
    val endpointUrl: String
)

class WebSearchTool(
    private val config: WebSearchProviderConfig,
    private val networkClient: NetworkClient,
    private val clock: Clock = Clock.systemUTC()
) : AgentTool {

    override val definition: AgentToolDefinition = AgentToolDefinition(
        name = "web_search",
        description = "Search the web and return normalized results with title, url, snippet, and optional publishedDate.",
        inputSchema = buildJsonObject {
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
                    put("includeDomains", domainArraySchema())
                    put("excludeDomains", domainArraySchema())
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

    override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult {
        val request = parseRequest(arguments) ?: return error(callId, "Invalid web search request: ${validationErrors(arguments).joinToString("; ")}.")
        return try {
            val response = networkClient().post(config.endpointUrl) {
                when (config.provider) {
                    WebSearchProvider.EXA -> header("x-api-key", config.bearerToken)
                    WebSearchProvider.FIRECRAWL, WebSearchProvider.PERPLEXITY -> bearerAuth(config.bearerToken)
                }
                setBody(payload(request))
            }
            if (response.status.value !in 200..299) {
                return error(callId, "Web search failed: HTTP ${response.status.value}.")
            }
            val content = runCatching { normalized(config.provider, response.bodyAsText()) }.getOrElse { exception ->
                return if (exception is MissingRequiredResultFieldException) {
                    error(callId, "Web search failed: missing required result fields.")
                } else {
                    error(callId, "Web search failed: malformed or unsupported provider response.")
                }
            }
            AgentToolResult(
                callId = callId,
                content = ToolResultContent.Json(content),
                isError = false
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            error(callId, "Web search failed: malformed or unsupported provider response.")
        }
    }

    private fun parseRequest(arguments: JsonObject): WebSearchRequest? {
        if (validationErrors(arguments).isNotEmpty()) return null
        return WebSearchRequest(
            query = arguments["query"]!!.jsonPrimitive.content.trim(),
            maxResults = intArgument(arguments, "maxResults") ?: 5,
            includeDomains = domains(arguments["includeDomains"]),
            excludeDomains = domains(arguments["excludeDomains"]),
            recencyDays = intArgument(arguments, "recencyDays")
        )
    }

    private fun validationErrors(arguments: JsonObject): List<String> {
        val errors = mutableListOf<String>()
        val query = stringArgument(arguments, "query")?.trim().orEmpty()
        val maxResults = intArgument(arguments, "maxResults")
        val recencyDays = intArgument(arguments, "recencyDays")
        val includeDomains = domains(arguments["includeDomains"])
        val excludeDomains = domains(arguments["excludeDomains"])

        if (query.isBlank()) errors += "query is required"
        if (arguments["maxResults"] != null && maxResults == null) errors += "maxResults must be an integer"
        if (maxResults != null && maxResults !in 1..10) errors += "maxResults must be between 1 and 10"
        if (arguments["recencyDays"] != null && recencyDays == null) errors += "recencyDays must be an integer"
        if (recencyDays != null && recencyDays < 0) errors += "recencyDays must be nonnegative"
        if (arguments["includeDomains"] != null && arguments["includeDomains"] !is JsonArray) errors += "includeDomains must be an array"
        if (arguments["excludeDomains"] != null && arguments["excludeDomains"] !is JsonArray) errors += "excludeDomains must be an array"
        if (!domainElementsAreStrings(arguments["includeDomains"]) || !domainElementsAreStrings(arguments["excludeDomains"])) errors += "domains must be strings"
        if (includeDomains.any { !it.isValidDomain() } || excludeDomains.any { !it.isValidDomain() }) errors += "domains must be host names"
        if (includeDomains.isNotEmpty() && excludeDomains.isNotEmpty()) errors += "includeDomains and excludeDomains cannot both be set"
        return errors
    }

    private fun payload(request: WebSearchRequest): JsonObject = when (config.provider) {
        WebSearchProvider.FIRECRAWL -> buildJsonObject {
            put("query", request.query)
            put("limit", request.maxResults)
            if (request.includeDomains.isNotEmpty()) put("includeDomains", request.includeDomains.toJsonArray())
            if (request.excludeDomains.isNotEmpty()) put("excludeDomains", request.excludeDomains.toJsonArray())
            request.recencyDays?.let { put("tbs", firecrawlTbs(it)) }
        }

        WebSearchProvider.PERPLEXITY -> buildJsonObject {
            put("query", request.query)
            put("max_results", request.maxResults)
            // Perplexity uses one signed domain filter list; excluded domains are represented with "-" prefixes.
            val domainFilter = request.includeDomains.ifEmpty { request.excludeDomains.map { "-$it" } }
            if (domainFilter.isNotEmpty()) put("search_domain_filter", domainFilter.toJsonArray())
            request.recencyDays?.let { put("search_after_date_filter", usDate(today().minusDays(it.toLong()))) }
        }

        WebSearchProvider.EXA -> buildJsonObject {
            put("query", request.query)
            put("numResults", request.maxResults)
            if (request.includeDomains.isNotEmpty()) put("includeDomains", request.includeDomains.toJsonArray())
            if (request.excludeDomains.isNotEmpty()) put("excludeDomains", request.excludeDomains.toJsonArray())
            request.recencyDays?.let { put("startPublishedDate", todayInstantMinusDays(it)) }
            put("contents", buildJsonObject { put("highlights", true) })
        }
    }

    private fun normalized(provider: WebSearchProvider, body: String): JsonObject {
        val root = NetworkClient.json.parseToJsonElement(body).jsonObject
        val rawResults = when (provider) {
            WebSearchProvider.FIRECRAWL -> root["data"]?.jsonObject?.get("web")?.jsonArray
            WebSearchProvider.PERPLEXITY -> root["results"]?.jsonArray
            WebSearchProvider.EXA -> root["results"]?.jsonArray
        } ?: throw IllegalArgumentException("missing results")
        val results = rawResults.map { element ->
            val value = element.jsonObject
            val title = value.string("title")
            val url = value.string("url")
            val snippet = value.string("snippet") ?: value.string("description") ?: value.highlights() ?: value.string("text") ?: value.string("summary")
            if (title == null || url == null || snippet == null) throw MissingRequiredResultFieldException()
            buildJsonObject {
                put("title", title)
                put("url", url)
                put("snippet", snippet)
                (value.string("publishedDate") ?: value.string("date"))?.let { put("publishedDate", it) }
            }
        }
        return buildJsonObject { put("results", JsonArray(results)) }
    }

    private fun domains(element: JsonElement?): List<String> = element
        ?.let { runCatching { it.jsonArray }.getOrNull() }
        ?.mapNotNull { stringValue(it)?.trim()?.lowercase(Locale.US) }
        .orEmpty()

    private fun today(): LocalDate = LocalDate.now(clock)

    private fun firecrawlTbs(days: Int): String {
        val start = today().minusDays(days.toLong())
        val end = today()
        return "cdr:1,cd_min:${usDate(start)},cd_max:${usDate(end)}"
    }

    private fun todayInstantMinusDays(days: Int): String = clock.instant().minusSeconds(days * 24L * 60L * 60L).toString()

    private fun usDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US))

    private fun error(callId: String, message: String): AgentToolResult = AgentToolResult(
        callId = callId,
        content = ToolResultContent.Text(message.take(240)),
        isError = true
    )

    private fun intArgument(arguments: JsonObject, name: String): Int? = runCatching {
        arguments[name]?.jsonPrimitive?.takeUnless { it.toString().startsWith("\"") }?.intOrNull
    }.getOrNull()

    private fun stringArgument(arguments: JsonObject, name: String): String? = runCatching {
        arguments[name]?.let { stringValue(it) }
    }.getOrNull()
}

private data class WebSearchRequest(
    val query: String,
    val maxResults: Int,
    val includeDomains: List<String>,
    val excludeDomains: List<String>,
    val recencyDays: Int?
)

private class MissingRequiredResultFieldException : Exception()

private fun domainArraySchema(): JsonObject = buildJsonObject {
    put("type", "array")
    put("items", buildJsonObject { put("type", "string") })
}

private fun List<String>.toJsonArray(): JsonArray = buildJsonArray {
    this@toJsonArray.forEach { add(JsonPrimitive(it)) }
}

private fun String.isValidDomain(): Boolean = isNotBlank() && !contains("/") && !contains(":") && none { it.isWhitespace() }

private fun domainElementsAreStrings(element: JsonElement?): Boolean = element !is JsonArray ||
    element.all { value ->
        stringValue(value) != null
    }

private fun JsonObject.string(name: String): String? = this[name]?.let { stringValue(it) }?.takeIf { it.isNotBlank() }

private fun JsonObject.highlights(): String? = this["highlights"]
    ?.let { runCatching { it.jsonArray }.getOrNull() }
    ?.mapNotNull { stringValue(it)?.takeIf { highlight -> highlight.isNotBlank() } }
    ?.joinToString("\n")
    ?.takeIf { it.isNotBlank() }

private fun stringValue(element: JsonElement): String? {
    val primitive = runCatching { element.jsonPrimitive }.getOrNull() ?: return null
    return primitive.contentOrNull?.takeIf { primitive.toString().startsWith("\"") }
}
