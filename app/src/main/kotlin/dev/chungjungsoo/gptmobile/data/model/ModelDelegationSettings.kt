package dev.chungjungsoo.gptmobile.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ModelDelegationSettings(
    val enabled: Boolean = false,
    /** 0 = extremely token efficient, 50 = balanced, 100 = maximum accuracy. */
    val strategy: Int = 50,
    /** 0 = local-first, 50 = shared concurrent-capable, 100 = remote-first. */
    val processingOwnership: Int = 50,
    val targetProfileUid: String = "",
    val localPlatformsOnly: Boolean = true,
    /** Allow enabled remote profiles to receive delegated worker tasks. */
    val allowRemoteWorkers: Boolean = false,
    /** Maximum worker-to-worker delegation depth; 1 prevents delegation loops by default. */
    val maxDelegationDepth: Int = 1,
    val maxInputCharacters: Int = 3500,
    val maxOutputTokens: Int = 512,
    val timeoutSeconds: Int = 75,
    val maxCallsPerTurn: Int = 5,
    val researchEnabled: Boolean = true,
    val automaticResearch: Boolean = true,
    val compactToolResults: Boolean = true,
    val maxLocalModelCalls: Int = 10,
    val maxSearchQueries: Int = 6,
    val searchResultsPerEngine: Int = 10,
    val maxPages: Int = 10,
    val crawlDepth: Int = 2,
    val pageFetchConcurrency: Int = 4,
    val maxPageCharacters: Int = 36000,
    val handoffTokens: Int = 256,
    val compactionThresholdCharacters: Int = 500,
    /** Maximum local retry attempts after the initial delegation attempt. */
    val localRetryLimit: Int = 0,
    /** Pause aggressive local research at or below this battery percentage. */
    val lowBatteryThresholdPercent: Int = 15,
    /** Keep remote synthesis compact after local research has prepared evidence. */
    val remoteSynthesisOutputTokens: Int = 256
) {
    /** Apply the master slider to every delegation budget and breadth setting. */
    fun withStrategy(value: Int): ModelDelegationSettings {
        val level = value.coerceIn(0, 100)
        fun scale(min: Int, max: Int): Int = min + ((max - min) * level / 100)
        return copy(
            strategy = level,
            maxInputCharacters = scale(2000, 32000),
            maxOutputTokens = scale(256, 2048),
            timeoutSeconds = scale(30, 360),
            maxCallsPerTurn = scale(1, 16),
            maxLocalModelCalls = scale(4, 32),
            maxSearchQueries = scale(1, 12),
            searchResultsPerEngine = scale(2, 20),
            maxPages = scale(1, 8),
            crawlDepth = scale(0, 4),
            pageFetchConcurrency = scale(1, 6),
            maxPageCharacters = scale(6000, 60000),
            handoffTokens = scale(256, 4096),
            compactionThresholdCharacters = scale(256, 12000),
            compactToolResults = level < 20
        )
    }

    /** Apply the local-to-remote processing ownership slider without changing token budgets. */
    fun withProcessingOwnership(value: Int): ModelDelegationSettings = copy(
        processingOwnership = value.coerceIn(0, 100)
    )

    fun normalized() = copy(
        strategy = strategy.coerceIn(0, 100),
        processingOwnership = processingOwnership.coerceIn(0, 100),
        maxDelegationDepth = maxDelegationDepth.coerceIn(1, 2),
        maxInputCharacters = maxInputCharacters.coerceIn(1000, 64000),
        maxOutputTokens = maxOutputTokens.coerceIn(64, 4096),
        timeoutSeconds = timeoutSeconds.coerceIn(5, 300),
        maxCallsPerTurn = maxCallsPerTurn.coerceIn(1, 16),
        maxLocalModelCalls = maxLocalModelCalls.coerceIn(1, 48),
        maxSearchQueries = maxSearchQueries.coerceIn(1, 20),
        searchResultsPerEngine = searchResultsPerEngine.coerceIn(1, 28),
        maxPages = maxPages.coerceIn(0, 32),
        crawlDepth = crawlDepth.coerceIn(0, 8),
        pageFetchConcurrency = pageFetchConcurrency.coerceIn(1, 16),
        maxPageCharacters = maxPageCharacters.coerceIn(1000, 96000),
        handoffTokens = handoffTokens.coerceIn(128, 8192),
        compactionThresholdCharacters = compactionThresholdCharacters.coerceIn(256, 48000),
        localRetryLimit = localRetryLimit.coerceIn(0, 1),
        lowBatteryThresholdPercent = lowBatteryThresholdPercent.coerceIn(0, 50),
        remoteSynthesisOutputTokens = remoteSynthesisOutputTokens.coerceIn(256, 4096)
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
