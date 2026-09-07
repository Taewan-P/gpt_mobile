package dev.chungjungsoo.gptmobile.data.agent.tool

import android.text.Html
import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.charset
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.URI
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Dns

class ReadUrlTool(
    private val dns: Dns = Dns.SYSTEM,
    private val allowAddress: (InetAddress) -> Boolean = { false },
    private val htmlToText: (String) -> String = ::androidHtmlToText
) : AgentTool {

    override val definition: AgentToolDefinition = AgentToolDefinition(
        name = "read_url",
        description = "Read a public HTTP or HTTPS URL and return bounded plain text.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject { put("url", buildJsonObject { put("type", "string") }) })
            put("required", JsonArray(listOf(JsonPrimitive("url"))))
            put("additionalProperties", false)
        }
    )

    override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult {
        val start = parseUrl(arguments) ?: return error(callId, "Read URL failed: url must be a valid HTTP(S) URL without userinfo or fragment.")
        return try {
            read(callId, start)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: ReadUrlException) {
            error(callId, "Read URL failed: ${exception.message}.")
        } catch (ignored: Exception) {
            error(callId, "Read URL failed: request failed.")
        }
    }

    private suspend fun read(callId: String, start: URI): AgentToolResult {
        var current = start
        var redirects = 0
        val seen = mutableSetOf(current.toASCIIString())
        while (true) {
            val request = request(current)
            try {
                val response = request.response
                val status = response.status.value
                if (status in REDIRECT_STATUSES) {
                    if (redirects >= MAX_REDIRECTS) throw ReadUrlException("too many redirects")
                    val location = response.headers[HttpHeaders.Location]?.trim().orEmpty()
                    if (location.isBlank()) throw ReadUrlException("missing Location header")
                    val next = parseResolvedRedirect(current, location)
                    if (!seen.add(next.toASCIIString())) throw ReadUrlException("redirect loop")
                    current = next
                    redirects += 1
                    continue
                }
                if (!response.status.isSuccess()) throw ReadUrlException("HTTP $status")
                val contentType = response.headers[HttpHeaders.ContentType].orEmpty()
                if (!isTextContent(contentType)) throw ReadUrlException("binary content rejected")
                val bytes = readBounded(response)
                val rawText = bytes.toString(contentType.charsetOrUtf8())
                val text = if (isHtmlContent(contentType)) htmlToText(rawText) else rawText
                return AgentToolResult(
                    callId = callId,
                    content = ToolResultContent.Text(truncateUtf8(normalizeWhitespace(text), MAX_OUTPUT_BYTES)),
                    isError = false
                )
            } finally {
                request.client.close()
            }
        }
    }

    private suspend fun request(uri: URI): ReadUrlRequest {
        val pinnedAddresses = resolveSafe(uri.host)
        val pinnedDns = Dns { hostname ->
            if (!hostname.equals(uri.host, ignoreCase = true)) throw UnknownHostException(hostname)
            pinnedAddresses
        }
        // ponytail: per-call client isolates DNS pins; pool per agent run only if profiling shows setup cost matters.
        val client = HttpClient(OkHttp) {
            followRedirects = false
            engine {
                dns = pinnedDns
                clientCacheSize = 0
                config {
                    followRedirects(false)
                    followSslRedirects(false)
                    proxy(Proxy.NO_PROXY)
                }
            }
        }
        return try {
            ReadUrlRequest(client, client.get(uri.toASCIIString()))
        } catch (exception: Exception) {
            client.close()
            throw exception
        }
    }

    private fun resolveSafe(host: String): List<InetAddress> {
        val addresses = try {
            dns.lookup(host)
        } catch (exception: UnknownHostException) {
            throw ReadUrlException("DNS lookup failed")
        }
        if (addresses.isEmpty()) throw ReadUrlException("DNS lookup failed")
        if (addresses.any { !allowAddress(it) && SpecialUseAddress.isSpecialUse(it) }) {
            throw ReadUrlException("unsafe DNS address rejected")
        }
        return addresses
    }

    private suspend fun readBounded(response: HttpResponse): ByteArray {
        val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (contentLength != null && contentLength > MAX_BODY_BYTES) throw ReadUrlException("response too large")
        val channel = response.bodyAsChannel()
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = channel.readAvailable(buffer, 0, buffer.size)
            if (read == -1) break
            if (read == 0) {
                yield()
                continue
            }
            output.write(buffer, 0, read)
            if (output.size() > MAX_BODY_BYTES) throw ReadUrlException("response too large")
        }
        return output.toByteArray()
    }

    private fun parseUrl(arguments: JsonObject): URI? {
        if (arguments.keys != setOf("url")) return null
        val value = arguments["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (value.isBlank()) return null
        return runCatching { URI(value) }.getOrNull()?.takeIf { it.isAllowedUrl() }
    }

    private fun String.charsetOrUtf8(): Charset = runCatching { ContentType.parse(this).charset() }
        .getOrNull()
        ?: StandardCharsets.UTF_8

    private fun parseResolvedRedirect(base: URI, location: String): URI {
        val next = try {
            base.resolve(URI(location))
        } catch (ignored: Exception) {
            throw ReadUrlException("malformed redirect URL")
        }
        if (!next.isAllowedUrl()) throw ReadUrlException("malformed redirect URL")
        return next
    }

    private fun URI.isAllowedUrl(): Boolean {
        val scheme = scheme?.lowercase(Locale.US) ?: return false
        if (scheme != "http" && scheme != "https") return false
        if (host.isNullOrBlank()) return false
        if (rawUserInfo != null || rawFragment != null) return false
        return try {
            toURL()
            true
        } catch (ignored: Exception) {
            false
        }
    }

    private fun error(callId: String, message: String): AgentToolResult = AgentToolResult(
        callId = callId,
        content = ToolResultContent.Text(message.take(ERROR_BYTES)),
        isError = true
    )

    private companion object {
        const val MAX_BODY_BYTES = 1024 * 1024
        const val MAX_OUTPUT_BYTES = 64 * 1024
        const val ERROR_BYTES = 240
        const val MAX_REDIRECTS = 5
        val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
    }
}

internal object SpecialUseAddress {
    fun isSpecialUse(address: InetAddress): Boolean {
        if (
            address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return true
        }
        return when (address) {
            is Inet4Address -> isSpecialUseIpv4(address.address)
            is Inet6Address -> isSpecialUseIpv6(address.address)
            else -> false
        }
    }

    private fun isSpecialUseIpv4(bytes: ByteArray): Boolean {
        val a = bytes[0].toInt() and 0xff
        val b = bytes[1].toInt() and 0xff
        val c = bytes[2].toInt() and 0xff
        return a == 0 ||
            a == 10 ||
            a == 127 ||
            (a == 169 && b == 254) ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 100 && b in 64..127) ||
            (a == 192 && b == 0 && c == 0) ||
            (a == 192 && b == 0 && c == 2) ||
            (a == 198 && (b == 18 || b == 19)) ||
            (a == 198 && b == 51 && c == 100) ||
            (a == 203 && b == 0 && c == 113) ||
            a >= 224
    }

    private fun isSpecialUseIpv6(bytes: ByteArray): Boolean {
        val first = bytes[0].toInt() and 0xff
        val second = bytes[1].toInt() and 0xff
        return first == 0xfc ||
            first == 0xfd ||
            (first == 0x20 && second == 0x01 && bytes[2].toInt() == 0x0d && (bytes[3].toInt() and 0xff) == 0xb8) ||
            isTeredo(bytes) ||
            isLocalNat64(bytes) ||
            nat64Ipv4(bytes)?.let(::isSpecialUseIpv4) == true ||
            sixToFourIpv4(bytes)?.let(::isSpecialUseIpv4) == true ||
            ipv4Mapped(bytes)?.let(::isSpecialUseIpv4) == true ||
            ipv4Compatible(bytes)?.let(::isSpecialUseIpv4) == true
    }

    private fun isTeredo(bytes: ByteArray): Boolean = bytes.matchesPrefix(0x20, 0x01, 0x00, 0x00)

    private fun isLocalNat64(bytes: ByteArray): Boolean = bytes.matchesPrefix(0x00, 0x64, 0xff, 0x9b, 0x00, 0x01)

    private fun nat64Ipv4(bytes: ByteArray): ByteArray? {
        if (!bytes.matchesPrefix(0x00, 0x64, 0xff, 0x9b) || bytes.sliceArray(4..11).any { it.toInt() != 0 }) return null
        return bytes.copyOfRange(12, 16)
    }

    private fun sixToFourIpv4(bytes: ByteArray): ByteArray? {
        if (!bytes.matchesPrefix(0x20, 0x02)) return null
        return bytes.copyOfRange(2, 6)
    }

    private fun ipv4Mapped(bytes: ByteArray): ByteArray? {
        if (bytes.size != 16) return null
        if (bytes.take(10).any { it.toInt() != 0 }) return null
        if ((bytes[10].toInt() and 0xff) != 0xff || (bytes[11].toInt() and 0xff) != 0xff) return null
        return bytes.copyOfRange(12, 16)
    }

    private fun ipv4Compatible(bytes: ByteArray): ByteArray? {
        if (bytes.size != 16 || bytes.take(12).any { it.toInt() != 0 }) return null
        return bytes.copyOfRange(12, 16)
    }

    private fun ByteArray.matchesPrefix(vararg prefix: Int): Boolean = prefix.indices.all { index ->
        (this[index].toInt() and 0xff) == prefix[index]
    }
}

private class ReadUrlException(message: String) : Exception(message)

private data class ReadUrlRequest(
    val client: HttpClient,
    val response: HttpResponse
)

private fun androidHtmlToText(html: String): String = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()

private fun isTextContent(contentType: String): Boolean {
    val type = contentType.substringBefore(";").trim().lowercase(Locale.US)
    return type.startsWith("text/") ||
        type == "application/json" ||
        type == "application/xml" ||
        type == "application/xhtml+xml" ||
        type.endsWith("+json") ||
        type.endsWith("+xml")
}

private fun isHtmlContent(contentType: String): Boolean {
    val type = contentType.substringBefore(";").trim().lowercase(Locale.US)
    return type == "text/html" || type == "application/xhtml+xml"
}

private fun normalizeWhitespace(value: String): String = value.replace(Regex("\\s+"), " ").trim()

private fun truncateUtf8(value: String, maxBytes: Int): String {
    val result = StringBuilder()
    var index = 0
    var bytes = 0
    while (index < value.length) {
        val codePoint = value.codePointAt(index)
        val chunk = String(Character.toChars(codePoint))
        val chunkBytes = chunk.toByteArray(StandardCharsets.UTF_8).size
        if (bytes + chunkBytes > maxBytes) break
        result.append(chunk)
        bytes += chunkBytes
        index += Character.charCount(codePoint)
    }
    return result.toString()
}
