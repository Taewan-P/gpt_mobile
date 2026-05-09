package dev.chungjungsoo.gptmobile.data.tool

import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup

interface SearchBackend {
    suspend fun search(query: String, topK: Int): SearchResultPayload
}

@Singleton
class BingSearchBackend @Inject constructor(
    private val networkClient: NetworkClient
) : SearchBackend {
    override suspend fun search(query: String, topK: Int): SearchResultPayload {
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val url = "https://www.bing.com/search?q=$encodedQuery"
        val response = networkClient().get(url) {
            header(HttpHeaders.UserAgent, DEFAULT_BROWSER_USER_AGENT)
            header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9,zh-CN;q=0.8")
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Search request failed with HTTP ${response.status.value}")
        }
        val html = response.bodyAsText()
        val document = Jsoup.parse(html, url)
        val items = document.select("li.b_algo").mapNotNull { element ->
            val titleElement = element.selectFirst("h2 > a") ?: return@mapNotNull null
            val title = titleElement.text().trim()
            val rawLink = titleElement.absUrl("href").ifBlank { titleElement.attr("href") }
            val link = normalizeSearchResultUrl(rawLink) ?: return@mapNotNull null
            val snippet = element.selectFirst(".b_caption p")?.text()?.trim()
                ?: element.selectFirst("p")?.text()?.trim().orEmpty()
            if (title.isBlank() || link.isBlank()) return@mapNotNull null
            SearchResultItem(title = title, url = link, snippet = snippet)
        }
            .distinctBy { it.url }
            .take(topK.coerceIn(1, 10))

        return SearchResultPayload(query = query, items = items)
    }
}

interface WebPageFetcher {
    suspend fun fetch(url: String): WebPagePayload
}

@Singleton
class DefaultWebPageFetcher @Inject constructor(
    private val networkClient: NetworkClient
) : WebPageFetcher {
    override suspend fun fetch(url: String): WebPagePayload {
        val normalizedUrl = normalizeAndValidateUrl(url)
        val response = networkClient().get(normalizedUrl) {
            header(HttpHeaders.UserAgent, DEFAULT_BROWSER_USER_AGENT)
            header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9,zh-CN;q=0.8")
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Failed to fetch page: HTTP ${response.status.value}")
        }

        val contentType = response.headers[HttpHeaders.ContentType].orEmpty().lowercase()
        val body = response.bodyAsText()
        if (looksBlockedPage(body)) {
            throw IllegalStateException("The page appears to be blocked or requires verification.")
        }

        if (!contentType.contains("html") && !contentType.contains("xhtml")) {
            val plainText = body.normalizeWhitespace().take(6000)
            if (plainText.isBlank()) {
                throw IllegalStateException("The page did not contain readable text.")
            }
            return WebPagePayload(
                url = normalizedUrl,
                title = normalizedUrl,
                excerpt = plainText.take(1000),
                content = plainText
            )
        }

        val document = Jsoup.parse(body, normalizedUrl)
        val title = (
            document.selectFirst("meta[property=og:title]")?.attr("content")
                ?: document.title()
        ).trim().ifBlank { normalizedUrl }
        val description = (
            document.selectFirst("meta[name=description]")?.attr("content")
                ?: document.selectFirst("meta[property=og:description]")?.attr("content")
        ).normalizeWhitespace()

        val content = extractReadableContent(document).ifBlank { description }.normalizeWhitespace()
        if (content.isBlank()) {
            throw IllegalStateException("Could not extract readable page content.")
        }

        val mergedContent = if (description.isNotBlank() && !content.startsWith(description)) {
            "$description\n\n$content"
        } else {
            content
        }
        val excerpt = mergedContent.take(1000)
        val clippedContent = mergedContent.take(7000)

        return WebPagePayload(
            url = normalizedUrl,
            title = title,
            excerpt = excerpt,
            content = clippedContent
        )
    }
}

class DefaultToolExecutor @Inject constructor(
    private val searchBackend: SearchBackend,
    private val webPageFetcher: WebPageFetcher
) : ToolExecutor {
    override suspend fun execute(toolName: String, arguments: JsonObject): ToolExecutionResult = when (toolName) {
        "search_web" -> {
            val query = arguments["query"]?.jsonPrimitive?.content.orEmpty()
            val topK = arguments["top_k"]?.jsonPrimitive?.intOrNull ?: 5
            val result = runCatching {
                searchBackend.search(query, topK)
            }.getOrElse { error ->
                SearchResultPayload(
                    query = query,
                    items = listOf(
                        SearchResultItem(
                            title = "Search failed",
                            url = "",
                            snippet = error.message ?: "Unknown search error"
                        )
                    )
                )
            }
            ToolExecutionResult(output = NetworkClient.json.encodeToString(result))
        }

        "open_url" -> {
            val url = arguments["url"]?.jsonPrimitive?.content.orEmpty()
            val result = runCatching {
                webPageFetcher.fetch(url)
            }.getOrElse { error ->
                WebPagePayload(
                    url = url,
                    title = "Open URL failed",
                    excerpt = error.message ?: "Unknown error",
                    content = error.message ?: "Unknown error"
                )
            }
            ToolExecutionResult(output = NetworkClient.json.encodeToString(result))
        }

        else -> throw IllegalArgumentException("Unsupported tool: $toolName")
    }
}

private fun normalizeAndValidateUrl(rawUrl: String): String {
    val uri = runCatching { URI(rawUrl.trim()) }.getOrNull()
        ?: throw IllegalArgumentException("Invalid URL")
    val scheme = uri.scheme?.lowercase().orEmpty()
    if (scheme != "http" && scheme != "https") {
        throw IllegalArgumentException("Only HTTP and HTTPS URLs are supported")
    }
    val host = uri.host?.lowercase().orEmpty()
    if (host.isBlank() || host == "localhost" || host.endsWith(".local")) {
        throw IllegalArgumentException("Local addresses are not supported")
    }

    val address = runCatching { InetAddress.getByName(host) }.getOrNull()
    if (address != null && (address.isAnyLocalAddress || address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress)) {
        throw IllegalArgumentException("Local network addresses are not supported")
    }

    return uri.toString()
}

private fun normalizeSearchResultUrl(rawUrl: String): String? {
    val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
    val host = uri.host?.lowercase().orEmpty()
    val path = uri.path.orEmpty()
    if (host.contains("bing.com") && path.startsWith("/ck/")) {
        return null
    }
    if (uri.scheme.isNullOrBlank()) return null
    if (uri.scheme.lowercase() !in setOf("http", "https")) return null
    return uri.toString()
}

private fun extractReadableContent(document: org.jsoup.nodes.Document): String {
    val working = document.clone()
    working.select(
        "script,style,noscript,svg,canvas,iframe,nav,header,footer,aside,form,button,menu,figure,figcaption,advertisement"
    ).remove()

    val candidateSelectors = listOf(
        "article",
        "main",
        "[role=main]",
        "#content",
        "#main",
        ".content",
        ".article",
        ".article-content",
        ".entry-content",
        ".post-content",
        ".main-content"
    )

    val candidates = candidateSelectors
        .flatMap { selector -> working.select(selector).toList() }
        .map { it.text().normalizeWhitespace() }
        .filter { it.length >= 120 }

    val best = candidates.maxByOrNull { it.length }
    if (!best.isNullOrBlank()) return best

    return working.body()?.text().normalizeWhitespace()
}

private fun looksBlockedPage(html: String): Boolean {
    val lowered = html.lowercase()
    return lowered.contains("captcha") ||
        lowered.contains("verify you are human") ||
        lowered.contains("access denied") ||
        lowered.contains("forbidden") ||
        lowered.contains("just a moment")
}

private fun String?.normalizeWhitespace(): String = this.orEmpty().replace(Regex("\\s+"), " ").trim()

private const val DEFAULT_BROWSER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
