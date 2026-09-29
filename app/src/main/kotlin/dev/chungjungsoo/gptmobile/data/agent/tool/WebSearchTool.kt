package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.agent.truncateUtf8
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
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
    EXA,
    BRAVE,
    AUTO
}

data class WebSearchProviderConfig(
    val provider: WebSearchProvider,
    val bearerToken: String,
    val endpointUrl: String,
    val allowLocalSearch: Boolean = false
)

class WebSearchTool(
    private val config: WebSearchProviderConfig,
    private val networkClient: NetworkClient,
    private val clock: Clock = Clock.systemUTC(),
    modelToolName: String = "web_search",
    private val autoSearchEndpointTemplates: List<String> = DEFAULT_AUTO_SEARCH_ENDPOINTS,
    private val autoSearchBlockedUntilMs: AtomicLong = DEFAULT_AUTO_SEARCH_BLOCKED_UNTIL_MS
) : AgentTool {
    private val authenticationBlockedUntilMs = perplexityAuthBlocks.computeIfAbsent(authenticationBlockKey()) { AtomicLong(0) }

    private fun authenticationBlockKey(): String {
        val value = "${config.endpointUrl.trimEnd('/')}|${config.bearerToken.trim()}"
        return MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
    }

    override val definition: AgentToolDefinition = AgentToolDefinition(
        name = modelToolName,
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

        return if (config.provider == WebSearchProvider.AUTO) {
            executeAutoSearch(callId, request)
        } else {
            executeConfiguredProvider(callId, request)
        }
    }

    private suspend fun executeConfiguredProvider(callId: String, request: WebSearchRequest): AgentToolResult {
        return try {
            if (config.provider == WebSearchProvider.PERPLEXITY && clock.millis() < authenticationBlockedUntilMs.get()) {
                return error(callId, "Perplexity web search is temporarily disabled after an authentication failure. Update its API key in Settings → Tool Connections before retrying.")
            }
            val response = if (config.provider == WebSearchProvider.BRAVE) {
                if (config.bearerToken.isBlank()) return error(callId, "Add a Brave Search API key in Settings → Tool Connections.")
                networkClient().get(config.endpointUrl) {
                    timeout { requestTimeoutMillis = 30_000 }
                    header("X-Subscription-Token", config.bearerToken.trim())
                    header("Accept", "application/json")
                    parameter("q", braveSearchQuery(request.query, request.includeDomains, request.excludeDomains))
                    parameter("count", request.maxResults)
                    parameter("result_filter", "web")
                    parameter("text_decorations", false)
                    request.recencyDays?.let { parameter("freshness", braveSearchFreshness(it, clock)) }
                }
            } else {
                networkClient().post(config.endpointUrl) {
                    when (config.provider) {
                        WebSearchProvider.EXA -> header("x-api-key", config.bearerToken.trim())
                        WebSearchProvider.FIRECRAWL, WebSearchProvider.PERPLEXITY -> bearerAuth(config.bearerToken.trim())
                        WebSearchProvider.AUTO, WebSearchProvider.BRAVE -> Unit
                    }
                    setBody(payload(request))
                }
            }
            if (response.status.value !in 200..299) {
                if (config.provider == WebSearchProvider.PERPLEXITY && response.status.value == 401) {
                    authenticationBlockedUntilMs.set(clock.millis() + 10 * 60 * 1000L)
                }
                return error(callId, providerFailureMessage(response.status.value))
            }
            val content = runCatching { normalized(config.provider, response.bodyAsText(), request) }.getOrElse { exception ->
                if (exception is CancellationException) throw exception
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

    private fun providerFailureMessage(status: Int): String {
        val provider = when (config.provider) {
            WebSearchProvider.FIRECRAWL -> "Firecrawl"
            WebSearchProvider.PERPLEXITY -> "Perplexity"
            WebSearchProvider.EXA -> "Exa"
            WebSearchProvider.BRAVE -> "Brave Search"
            WebSearchProvider.AUTO -> "Web search"
        }
        return when (status) {
            401 -> "$provider web search authentication failed (HTTP 401). Update this connection's $provider API key in Settings → Tool Connections. AI platform keys are configured separately."
            403 -> "$provider web search access was denied (HTTP 403). Check this search connection's API key permissions and provider account."
            429 -> "$provider web search rate limit reached (HTTP 429). Wait before retrying or check your search plan allowance."
            else -> "Web search failed: HTTP $status."
        }
    }

    private suspend fun executeAutoSearch(callId: String, request: WebSearchRequest): AgentToolResult = coroutineScope {
        val local = async {
            if (!config.allowLocalSearch || config.endpointUrl.isBlank()) return@async emptyList<JsonObject>()
            try {
                withTimeoutOrNull(2_000) { tryTermuxMcpSearch(request) }.orEmpty()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
        }
        val web = async {
            try {
                withTimeoutOrNull(15_000) { queryDuckDuckGo(request) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
        val localResults = local.await()
        val webResults = web.await()
        if (localResults.isEmpty() && webResults == null) {
            return@coroutineScope error(callId, "Web search failed: could not retrieve search results.")
        }
        val results = (localResults + webResults.orEmpty()).distinctBy {
            canonicalSearchUrl(it["url"]?.jsonPrimitive?.content.orEmpty())
        }
        AgentToolResult(callId, ToolResultContent.Json(compactSearchResults(results, request.maxResults * 2)), false)
    }

    private suspend fun tryTermuxMcpSearch(request: WebSearchRequest): List<JsonObject>? {
        val endpoint = config.endpointUrl.takeIf { it.isNotBlank() } ?: return null
        val response = networkClient().get(endpoint) {
            parameter("query", request.query)
            parameter("limit", request.maxResults)
        }
        if (response.status.value !in 200..299) return null
        val bodyText = response.bodyAsText()
        val root = NetworkClient.json.parseToJsonElement(bodyText).jsonObject
        val rawResults = root["results"]?.jsonArray ?: return null
        return rawResults.mapNotNull { element ->
            val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val title = obj.string("title") ?: return@mapNotNull null
            val url = obj.string("url") ?: return@mapNotNull null
            val snippet = obj.string("snippet") ?: obj.string("description") ?: ""
            buildJsonObject {
                put("title", title)
                put("url", url)
                put("snippet", snippet)
                obj.string("publishedDate")?.let { put("publishedDate", it) }
            }
        }.take(request.maxResults)
    }

    private suspend fun queryDuckDuckGo(request: WebSearchRequest): List<JsonObject> {
        if (clock.millis() < autoSearchBlockedUntilMs.get()) return emptyList()
        val encodedQuery = URLEncoder.encode(request.query, StandardCharsets.UTF_8.name())
        val endpoints = autoSearchEndpointTemplates.map { it.replace("{query}", encodedQuery) }

        for (url in endpoints) {
            try {
                val response = networkClient().get(url) {
                    header("User-Agent", USER_AGENT)
                    header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    header("Accept-Language", "en-US,en;q=0.9")
                }
                if (response.status.value in AUTO_SEARCH_BACKOFF_STATUSES) {
                    autoSearchBlockedUntilMs.updateAndGet { current ->
                        maxOf(current, clock.millis() + AUTO_SEARCH_BACKOFF_MS)
                    }
                    return emptyList()
                }
                if (response.status.value in 200..299) {
                    val html = response.bodyAsText()
                    if (html.isNotBlank()) {
                        val parsed = parseDuckDuckGoHtml(html, request.maxResults)
                        if (parsed.isNotEmpty()) {
                            var filtered = parsed
                            if (request.includeDomains.isNotEmpty()) {
                                filtered = filtered.filter { result ->
                                    val resultUrl = result["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                    matchesSearchDomains(resultUrl, request.includeDomains, emptyList())
                                }
                            }
                            if (request.excludeDomains.isNotEmpty()) {
                                filtered = filtered.filter { result ->
                                    val resultUrl = result["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                    matchesSearchDomains(resultUrl, emptyList(), request.excludeDomains)
                                }
                            }
                            if (filtered.isNotEmpty()) {
                                return filtered.take(request.maxResults)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Continue to next endpoint
            }
        }
        return emptyList()
    }

    private fun parseDuckDuckGoHtml(html: String, maxResults: Int): List<JsonObject> {
        val results = mutableListOf<JsonObject>()

        val resultBlockRegex = Regex(
            """(?:class="[^"]*(?:web-result|result\b|result__body)[^"]*"|data-testid="result")[^>]*>(.*?)(?=(?:class="[^"]*(?:web-result|result\b|result__body)[^"]*"|data-testid="result")|</body>|</html>|$)""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val blocks = resultBlockRegex.findAll(html).map { it.groupValues[1] }.toList()

        if (blocks.isEmpty()) {
            val liteResults = Regex("""<a\b([^>]*)>(.*?)</a>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                .findAll(html)
                .mapNotNull { match ->
                    val attributes = match.groupValues[1]
                    if (!attributes.contains("result-link", ignoreCase = true)) return@mapNotNull null
                    val rawUrl = Regex("""href\s*=\s*['\"]([^'\"]+)['\"]""", RegexOption.IGNORE_CASE)
                        .find(attributes)?.groupValues?.get(1).orEmpty()
                    val url = extractActualUrl(rawUrl)
                    val title = cleanHtml(match.groupValues[2])
                    if (url.isBlank() || title.isBlank()) return@mapNotNull null
                    buildJsonObject {
                        put("title", title)
                        put("url", url)
                        put("snippet", "")
                    }
                }
                .take(maxResults)
                .toList()
            if (liteResults.isNotEmpty()) return liteResults
        }

        val candidateBlocks = if (blocks.isNotEmpty()) blocks else html.split(Regex("""<div[^>]+class="[^"]*result[^"]*"[^>]*>""", RegexOption.IGNORE_CASE)).drop(1)

        val titleRegex = Regex("""<a[^>]+class="[^"]*result__a[^"]*"[^>]+href="([^"]+)"[^>]*>(.*?)</a>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val fallbackTitleRegex = Regex("""<a[^>]+href="([^"]+)"[^>]+data-testid="result-title-a"[^>]*>(.*?)</a>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val h2TitleRegex = Regex("""<h2[^>]*>\s*<a[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

        val snippetRegex = Regex("""<(?:a|div|span)[^>]+class="[^"]*(?:result__snippet|snippet)[^"]*"[^>]*>(.*?)</(?:a|div|span)>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val fallbackSnippetRegex = Regex("""<(?:div|span|p)[^>]+data-testid="result-snippet"[^>]*>(.*?)</(?:div|span|p)>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

        for (block in candidateBlocks) {
            val titleMatch = titleRegex.find(block) ?: fallbackTitleRegex.find(block) ?: h2TitleRegex.find(block)
            val rawUrl = titleMatch?.groupValues?.get(1).orEmpty()
            val rawTitle = titleMatch?.groupValues?.get(2).orEmpty()

            val url = extractActualUrl(rawUrl)
            val title = cleanHtml(rawTitle)

            val snippetMatch = snippetRegex.find(block) ?: fallbackSnippetRegex.find(block)
            val rawSnippet = snippetMatch?.groupValues?.get(1).orEmpty()
            val snippet = cleanHtml(rawSnippet)

            if (url.isNotBlank() && title.isNotBlank()) {
                results += buildJsonObject {
                    put("title", title)
                    put("url", url)
                    put("snippet", snippet)
                }
            }

            if (results.size >= maxResults) {
                break
            }
        }

        return results
    }

    private fun extractActualUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return ""
        val resolvedUrl = when {
            rawUrl.startsWith("//") -> "https:$rawUrl"
            rawUrl.startsWith("/") && !rawUrl.startsWith("/l/?") && !rawUrl.startsWith("/html/?") -> "https://duckduckgo.com$rawUrl"
            else -> rawUrl
        }
        val uri = runCatching { URI(resolvedUrl) }.getOrNull() ?: return resolvedUrl
        val queryParams = uri.rawQuery?.split("&").orEmpty()
        for (param in queryParams) {
            val parts = param.split("=", limit = 2)
            if (parts.size == 2 && parts[0] == "uddg") {
                return runCatching { URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name()) }.getOrDefault(resolvedUrl)
            }
        }
        return resolvedUrl
    }

    private fun cleanHtml(text: String): String {
        val withoutTags = text.replace(Regex("<[^>]+>"), " ")
        val decoded = withoutTags
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
        val normalized = Normalizer.normalize(decoded, Normalizer.Form.NFKC)
        return normalized.replace(Regex("\\s+"), " ").trim()
    }

    private fun parseRequest(arguments: JsonObject): WebSearchRequest? {
        if (validationErrors(arguments).isNotEmpty()) return null
        return WebSearchRequest(
            query = arguments["query"]!!.jsonPrimitive.content.trim(),
            maxResults = intArgument(arguments, "maxResults") ?: 10,
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
        if (config.provider == WebSearchProvider.BRAVE && errors.isEmpty()) {
            runCatching {
                braveSearchQuery(query, includeDomains, excludeDomains)
                recencyDays?.let { braveSearchFreshness(it, clock) }
            }.exceptionOrNull()?.let { errors += it.message ?: "invalid Brave search parameters" }
        }
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

        WebSearchProvider.BRAVE -> error("Brave Search uses GET parameters")

        WebSearchProvider.AUTO -> buildJsonObject {
            put("query", request.query)
            put("limit", request.maxResults)
        }
    }

    private fun normalized(provider: WebSearchProvider, body: String, request: WebSearchRequest): JsonObject {
        val root = NetworkClient.json.parseToJsonElement(body).jsonObject
        val rawResults = when (provider) {
            WebSearchProvider.FIRECRAWL -> root["data"]?.jsonObject?.get("web")?.jsonArray
            WebSearchProvider.PERPLEXITY -> root["results"]?.jsonArray
            WebSearchProvider.EXA -> root["results"]?.jsonArray
            WebSearchProvider.BRAVE -> {
                val web = root["web"]
                if (web == null || web is kotlinx.serialization.json.JsonNull) {
                    require(root.string("type") == "search") { "missing search response" }
                    JsonArray(emptyList())
                } else {
                    web.jsonObject["results"]?.jsonArray
                }
            }
            WebSearchProvider.AUTO -> root["results"]?.jsonArray
        } ?: throw IllegalArgumentException("missing results")
        val results = rawResults.take(request.maxResults).map { element ->
            val value = element.jsonObject
            val title = value.string("title")
            val url = value.string("url")
            val snippet = value.string("snippet") ?: value.string("description") ?: value.highlights() ?: value.string("text") ?: value.string("summary")
                ?: if (provider == WebSearchProvider.BRAVE) "" else null
            if (title == null || url == null || snippet == null) throw MissingRequiredResultFieldException()
            buildJsonObject {
                put("title", if (provider == WebSearchProvider.BRAVE) cleanHtml(title) else title)
                put("url", url)
                put("snippet", if (provider == WebSearchProvider.BRAVE) cleanHtml(snippet) else snippet)
                (value.string("publishedDate") ?: value.string("date"))?.let { put("publishedDate", it) }
            }
        }
        val filtered = if (provider == WebSearchProvider.BRAVE) {
            results.filter { matchesSearchDomains(it.string("url").orEmpty(), request.includeDomains, request.excludeDomains) }
        } else {
            results
        }
        return compactSearchResults(filtered, request.maxResults)
    }

    private fun compactSearchResults(results: List<JsonObject>, maxResults: Int): JsonObject {
        val compact = mutableListOf<JsonObject>()
        var bytes = 0
        for (result in results.take(maxResults)) {
            val entry = buildJsonObject {
                put("title", truncateUtf8(result.string("title").orEmpty(), 256))
                // Keep links intact so the model and UI can still open the source.
                put("url", result.string("url").orEmpty())
                put("snippet", truncateUtf8(result.string("snippet").orEmpty(), 768))
                result.string("publishedDate")?.let { put("publishedDate", it.take(64)) }
            }
            val size = entry.toString().toByteArray(Charsets.UTF_8).size
            if (bytes + size > 8_000) continue
            compact += entry
            bytes += size
        }
        return buildJsonObject { put("results", JsonArray(compact)) }
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

    private companion object {
        val perplexityAuthBlocks = ConcurrentHashMap<String, AtomicLong>()
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
        const val AUTO_SEARCH_BACKOFF_MS = 5 * 60 * 1000L
        val AUTO_SEARCH_BACKOFF_STATUSES = setOf(202, 403, 429)
        val DEFAULT_AUTO_SEARCH_ENDPOINTS = listOf(
            "https://lite.duckduckgo.com/lite/?q={query}",
            "https://html.duckduckgo.com/html/?q={query}"
        )
        val DEFAULT_AUTO_SEARCH_BLOCKED_UNTIL_MS = AtomicLong(0L)
    }
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
