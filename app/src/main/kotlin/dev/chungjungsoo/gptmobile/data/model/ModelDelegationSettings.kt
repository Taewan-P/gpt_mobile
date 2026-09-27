package dev.chungjungsoo.gptmobile.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ModelDelegationSettings(
    val enabled: Boolean = false,
    val targetProfileUid: String = "",
    val localPlatformsOnly: Boolean = true,
    val maxInputCharacters: Int = 8000,
    val maxOutputTokens: Int = 512,
    val timeoutSeconds: Int = 30,
    val maxCallsPerTurn: Int = 1,
    val searchBreadth: Int = 4,
    val maxPageReads: Int = 6,
    val crawlDepth: Int = 1,
    val parallelism: Int = 2,
    val localWorkloadPercent: Int = 70,
    val remoteBriefCharacters: Int = 6000
) {
    fun normalized() = copy(
        maxInputCharacters = maxInputCharacters.coerceIn(500, 16000),
        maxOutputTokens = maxOutputTokens.coerceIn(64, 2048),
        timeoutSeconds = timeoutSeconds.coerceIn(5, 120),
        maxCallsPerTurn = maxCallsPerTurn.coerceIn(1, 8),
        searchBreadth = searchBreadth.coerceIn(1, 12),
        maxPageReads = maxPageReads.coerceIn(1, 20),
        crawlDepth = crawlDepth.coerceIn(0, 4),
        parallelism = parallelism.coerceIn(1, 8),
        localWorkloadPercent = localWorkloadPercent.coerceIn(0, 100),
        remoteBriefCharacters = remoteBriefCharacters.coerceIn(1000, 16000)
    )
}

fun ClientType.isLocalPlatform(): Boolean = this in setOf(ClientType.LITERT_LM, ClientType.LLAMA, ClientType.OLLAMA)

/** Privacy classification uses the actual destination, including custom API profiles. */
fun dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2.isPrivateDestination(): Boolean {
    if (compatibleType == ClientType.LITERT_LM) return true
    return endpointLocality(apiUrl) != EndpointLocality.EXTERNAL
}
enum class EndpointLocality { ON_DEVICE, PRIVATE_NETWORK, EXTERNAL }
fun endpointLocality(url: String): EndpointLocality {
    val host = runCatching { java.net.URI(url).host?.lowercase()?.removeSurrounding("[", "]") }.getOrNull() ?: return EndpointLocality.EXTERNAL
    val segments = host.split('.')
    val octets = segments.mapNotNull(String::toIntOrNull)
    val validIpv4 = segments.size == 4 && octets.size == 4 && octets.all { it in 0..255 }
    if (host == "localhost" || host == "::1" || (validIpv4 && octets[0] == 127)) return EndpointLocality.ON_DEVICE
    val privateIpv4 = validIpv4 &&
        (
            octets[0] == 10 ||
                (octets[0] == 192 && octets[1] == 168) ||
                (octets[0] == 172 && octets[1] in 16..31) ||
                (octets[0] == 100 && octets[1] in 64..127) ||
                (octets[0] == 169 && octets[1] == 254)
            )
    val privateIpv6 = host.contains(':') && (host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe8") || host.startsWith("fe9") || host.startsWith("fea") || host.startsWith("feb"))
    return if (privateIpv4 || privateIpv6) EndpointLocality.PRIVATE_NETWORK else EndpointLocality.EXTERNAL
}
