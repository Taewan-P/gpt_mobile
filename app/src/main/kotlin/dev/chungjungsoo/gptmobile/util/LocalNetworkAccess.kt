package dev.chungjungsoo.gptmobile.util

import java.net.URI
import kotlinx.coroutines.CancellationException

internal fun requiresLocalNetworkAccess(url: String): Boolean {
    val host = runCatching { URI(normalizeEndpointForHostCheck(url)).host }
        .getOrNull()
        ?.trim('[', ']')
        ?.trimEnd('.')
        ?.lowercase()
        ?: return false
    if (host == "localhost" || host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home")) return true
    if ('.' !in host && ':' !in host) return true

    parseIpv4(host)?.let { octets ->
        val first = octets[0]
        val second = octets[1]
        return first == 0 ||
            first == 10 ||
            first == 127 ||
            (first == 169 && second == 254) ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168) ||
            (first == 100 && second in 64..127)
    }
    if (':' !in host) return false

    return host == "::1" ||
        host.startsWith("fe8") ||
        host.startsWith("fe9") ||
        host.startsWith("fea") ||
        host.startsWith("feb") ||
        host.startsWith("fc") ||
        host.startsWith("fd")
}

private fun normalizeEndpointForHostCheck(url: String): String {
    val trimmed = url.trim()
    if ("://" in trimmed) return trimmed
    return "http://$trimmed"
}

private fun parseIpv4(host: String): List<Int>? {
    val octets = host.split('.')
    if (octets.size != 4) return null
    return octets.map { it.toIntOrNull()?.takeIf { value -> value in 0..255 } ?: return null }
}

internal suspend fun determineLocalNetworkAccessRequirement(
    providerNeedsAccess: Boolean,
    toolNeedsAccess: suspend () -> Boolean,
    onLookupFailure: (Exception) -> Unit
): Boolean {
    if (providerNeedsAccess) return true
    return try {
        toolNeedsAccess()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        onLookupFailure(error)
        true
    }
}
