package dev.chungjungsoo.gptmobile.data.tool

import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
        val html = response.bodyAsText().take(MAX_SEARCH_RESPONSE_CHARS)
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
        val requestedUrl = normalizeAndValidateUrl(url)
        val response = networkClient().get(requestedUrl) {
            header(HttpHeaders.UserAgent, DEFAULT_BROWSER_USER_AGENT)
            header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9,zh-CN;q=0.8")
            header(HttpHeaders.Range, "bytes=0-${MAX_WEB_PAGE_BYTES - 1}")
        }
        val normalizedUrl = normalizeAndValidateUrl(response.request.url.toString())
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Failed to fetch page: HTTP ${response.status.value}")
        }

        val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (contentLength != null && contentLength > MAX_WEB_PAGE_BYTES) {
            throw IllegalStateException("The page is too large to fetch safely.")
        }

        val contentType = response.headers[HttpHeaders.ContentType].orEmpty().lowercase()
        val body = response.bodyAsText().take(MAX_WEB_PAGE_CHARS)
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
            val result = runCatching {
                val query = arguments["query"].primitiveContentOrEmpty()
                val topK = arguments["top_k"].primitiveIntOrNull() ?: 5
                searchBackend.search(query, topK)
            }.getOrElse { error ->
                SearchResultPayload(
                    query = arguments["query"].primitiveContentOrNull().orEmpty(),
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
            val result = runCatching {
                val url = arguments["url"].primitiveContentOrEmpty()
                webPageFetcher.fetch(url)
            }.getOrElse { error ->
                val url = arguments["url"].primitiveContentOrNull().orEmpty()
                WebPagePayload(
                    url = url,
                    title = "Open URL failed",
                    excerpt = error.message ?: "Unknown error",
                    content = error.message ?: "Unknown error"
                )
            }
            ToolExecutionResult(output = NetworkClient.json.encodeToString(result))
        }

        else -> ToolExecutionResult(
            output = NetworkClient.json.encodeToString(
                WebPagePayload(
                    url = "",
                    title = "Unsupported tool",
                    excerpt = "Unsupported tool: $toolName",
                    content = "Unsupported tool: $toolName"
                )
            )
        )
    }
}

private fun JsonElement?.primitiveContentOrEmpty(): String =
    primitiveContentOrNull().orEmpty()

private fun JsonElement?.primitiveContentOrNull(): String? =
    runCatching { (this as? JsonPrimitive)?.content }.getOrNull()

private fun JsonElement?.primitiveIntOrNull(): Int? =
    (this as? JsonPrimitive)?.intOrNull

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

    val addresses = runCatching { InetAddress.getAllByName(host).toList() }.getOrDefault(emptyList())
    if (addresses.isEmpty() || addresses.any { it.isBlockedAddress() }) {
        throw IllegalArgumentException("Local network addresses are not supported")
    }

    return uri.toString()
}

private fun normalizeSearchResultUrl(rawUrl: String): String? {
    val candidate = decodeBingRedirectTarget(rawUrl) ?: rawUrl
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    if (uri.scheme.isNullOrBlank()) return null
    if (uri.scheme.lowercase() !in setOf("http", "https")) return null
    return runCatching { normalizeAndValidateUrl(uri.toString()) }.getOrNull()
}

private fun decodeBingRedirectTarget(rawUrl: String): String? {
    val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
    val host = uri.host?.lowercase().orEmpty()
    val path = uri.path.orEmpty()
    if (!host.contains("bing.com") || !path.startsWith("/ck/")) {
        return null
    }

    val decodedTarget = uri.queryParameter("u") ?: return null
    if (decodedTarget.startsWith("http://") || decodedTarget.startsWith("https://")) {
        return decodedTarget
    }

    return decodeBase64UrlTarget(decodedTarget.removePrefix("a1"))
}

private fun URI.queryParameter(name: String): String? = rawQuery
    ?.split("&")
    ?.firstNotNullOfOrNull { part ->
        val key = URLDecoder.decode(part.substringBefore("="), StandardCharsets.UTF_8.name())
        if (key == name) {
            URLDecoder.decode(part.substringAfter("=", ""), StandardCharsets.UTF_8.name())
        } else {
            null
        }
    }

private fun decodeBase64UrlTarget(value: String): String? {
    if (value.isBlank()) return null
    val padded = value + "=".repeat((4 - value.length % 4) % 4)
    return runCatching {
        String(Base64.getUrlDecoder().decode(padded), StandardCharsets.UTF_8)
    }.getOrNull()
        ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
}

private fun extractReadableContent(document: org.jsoup.nodes.Document): String {
    val working = document.clone()
    working.select(
        "script,style,noscript,svg,canvas,iframe,nav,header,footer,aside,form,button,menu,figure,figcaption,advertisement,.advertisement,.ads,.cookie,.cookies,.modal,.popup"
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
        .map { element ->
            element.select("p,li")
                .map { it.text().normalizeWhitespace() }
                .filter { it.length >= MIN_READABLE_PARAGRAPH_CHARS }
                .joinToString(separator = "\n")
                .ifBlank { element.text().normalizeWhitespace() }
        }
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

private fun InetAddress.isBlockedAddress(): Boolean {
    if (
        isAnyLocalAddress ||
        isLoopbackAddress ||
        isSiteLocalAddress ||
        isLinkLocalAddress ||
        isMulticastAddress
    ) {
        return true
    }

    return when (this) {
        is Inet4Address -> isBlockedIpv4Address()
        is Inet6Address -> isBlockedIpv6Address()
        else -> true
    }
}

private fun Inet4Address.isBlockedIpv4Address(): Boolean {
    val bytes = address.map { it.toInt() and 0xff }
    val first = bytes[0]
    val second = bytes[1]
    val third = bytes[2]
    return first == 0 ||
        first == 10 ||
        first == 127 ||
        first >= 224 ||
        first == 169 && second == 254 ||
        first == 172 && second in 16..31 ||
        first == 192 && second == 168 ||
        first == 100 && second in 64..127 ||
        first == 192 && second == 0 && third == 2 ||
        first == 198 && second in setOf(18, 19) ||
        first == 198 && second == 51 && third == 100 ||
        first == 203 && second == 0 && third == 113
}

private fun Inet6Address.isBlockedIpv6Address(): Boolean {
    val bytes = address.map { it.toInt() and 0xff }
    return bytes.all { it == 0 } ||
        bytes.dropLast(1).all { it == 0 } && bytes.last() == 1 ||
        (bytes[0] and 0xfe) == 0xfc ||
        bytes[0] == 0xfe && (bytes[1] and 0xc0) == 0x80
}

private fun String?.normalizeWhitespace(): String = this.orEmpty().replace(Regex("\\s+"), " ").trim()

private const val MAX_SEARCH_RESPONSE_CHARS = 500_000
private const val MAX_WEB_PAGE_BYTES = 1_000_000
private const val MAX_WEB_PAGE_CHARS = 120_000
private const val MIN_READABLE_PARAGRAPH_CHARS = 40
private const val DEFAULT_BROWSER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
