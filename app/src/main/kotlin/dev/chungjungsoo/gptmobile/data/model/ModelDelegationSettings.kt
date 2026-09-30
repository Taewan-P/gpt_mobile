package dev.chungjungsoo.gptmobile.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ModelDelegationSettings(
    val enabled: Boolean = false,
    /** Research depth: 0 = focused, 50 = standard, 100 = broad coverage. */
    val strategy: Int = 70,
    /** 0 = local-first, 50 = shared concurrent-capable, 100 = remote-first. */
    val processingOwnership: Int = 20,
    val targetProfileUid: String = "",
    val fallbackToAnotherProfile: Boolean = true,
    val localPlatformsOnly: Boolean = true,
    /** Allow enabled remote profiles to receive delegated worker tasks. */
    val allowRemoteWorkers: Boolean = false,
    /** Maximum worker-to-worker delegation depth; 1 prevents delegation loops by default. */
    val maxDelegationDepth: Int = 1,
    val maxInputCharacters: Int = 4000,
    /** Hard token estimate cap for one delegated inference request. Oversized work is chunked before dispatch. */
    val maxInputTokensPerDelegate: Int = 6000,
    /** Preferred chunk size for oversized delegated work. */
    val chunkSizeTokens: Int = 5000,
    /** Smaller chunk size used when retrying an oversized/failed chunk. */
    val retryChunkSizeTokens: Int = 2500,
    val maxOutputTokens: Int = 512,
    /** Legacy upper timeout; adaptive worker deadlines are additionally bounded by maxDelegateRuntimeSeconds. */
    val timeoutSeconds: Int = 75,
    /** Cancel a worker that produces no model/tool progress within this window. */
    val timeToFirstTokenTimeoutSeconds: Int = 30,
    /** Cancel a worker when output/tool progress stalls for this long after starting. */
    val idleTokenTimeoutSeconds: Int = 20,
    /** Absolute per-delegate runtime ceiling, regardless of profile timeout. */
    val maxDelegateRuntimeSeconds: Int = 120,
    /** Maximum simultaneous delegated inference calls. Local devices default to one. */
    val maxConcurrentDelegates: Int = 1,
    /** Stop spending local compute after this many estimated tokens have been discarded in a turn. */
    val maxWastedLocalTokensPerTurn: Int = 8000,
    /** Evidence coverage percentage at which additional research can stop early. */
    val evidenceSufficiencyPercent: Int = 79,
    val maxCallsPerTurn: Int = 8,
    val researchEnabled: Boolean = true,
    val automaticResearch: Boolean = true,
    val compactToolResults: Boolean = true,
    val maxLocalModelCalls: Int = 16,
    val maxSearchQueries: Int = 9,
    val searchResultsPerEngine: Int = 10,
    val maxPages: Int = 11,
    val crawlDepth: Int = 1,
    val pageFetchConcurrency: Int = 4,
    val maxPageCharacters: Int = 37200,
    val handoffTokens: Int = 512,
    val compactionThresholdCharacters: Int = 500,
    /** Maximum local retry attempts after the initial delegation attempt. */
    val localRetryLimit: Int = 0,
    /** Pause aggressive local research at or below this battery percentage. */
    val lowBatteryThresholdPercent: Int = 15,
    /** Legacy saved preference; final answers now follow the primary profile output budget. */
    val remoteSynthesisOutputTokens: Int = 256,
    /** Maximum prior tool-exchange tokens replayed to the primary model on each round. */
    val primaryReplayTokens: Int = 4000,
    /** Maximum tokens retained from one already-consumed tool result on later rounds. */
    val primaryReplayResultTokens: Int = 512
) {
    /** Research depth changes breadth without disabling compaction or rewriting worker limits. */
    fun withStrategy(value: Int): ModelDelegationSettings {
        val level = value.coerceIn(0, 100)
        fun scale(min: Int, max: Int): Int = min + ((max - min) * level / 100)
        return copy(
            strategy = level,
            maxSearchQueries = scale(2, 12),
            searchResultsPerEngine = 10,
            maxPages = scale(4, 14),
            crawlDepth = if (level >= 85) 2 else 1,
            maxPageCharacters = scale(12000, 48000),
            evidenceSufficiencyPercent = scale(65, 85)
        )
    }

    /** Higher amounts favor the helper and give it more small, bounded calls. */
    fun withDelegationAmount(value: Int): ModelDelegationSettings {
        val amount = value.coerceIn(0, 100)
        return copy(
            processingOwnership = 100 - amount,
            maxLocalModelCalls = 4 + 16 * amount / 100,
            maxCallsPerTurn = 2 + 8 * amount / 100
        )
    }

    /** Apply the local-to-remote processing ownership slider without rewriting the user's
     * detailed controls. Runtime floors below ensure Local-first cannot be starved by an
     * efficiency preset that was designed only to reduce breadth/verbosity. */
    fun withProcessingOwnership(value: Int): ModelDelegationSettings = copy(
        processingOwnership = value.coerceIn(0, 100)
    )

    /** Effective local-worker call allowance for this turn.
     *
     * The ownership slider is authoritative for where work happens. Previously an
     * "extremely efficient" strategy could reduce maxLocalModelCalls to 4 even with
     * processingOwnership=0, causing the remote primary to take over most of the turn.
     */
    fun effectiveLocalModelCalls(): Int {
        val ownershipFloor = when {
            processingOwnership <= 10 -> 16
            processingOwnership <= 25 -> 12
            processingOwnership <= 40 -> 8
            else -> 1
        }
        return maxOf(maxLocalModelCalls, ownershipFloor).coerceIn(1, 48)
    }

    /** Effective research-workflow allowance for this turn.
     *
     * Keep the strategy slider from starving Local-first delegation. maxCallsPerTurn controls
     * research workflow requests, while maxLocalModelCalls controls individual worker calls.
     * A Local-first profile therefore needs more than one research request even when an
     * efficiency preset configured maxCallsPerTurn=1.
     */
    fun effectiveResearchCalls(): Int {
        val ownershipFloor = when {
            processingOwnership <= 10 -> 4
            processingOwnership <= 25 -> 3
            processingOwnership <= 40 -> 2
            else -> 1
        }
        return maxOf(maxCallsPerTurn, ownershipFloor).coerceIn(1, 16)
    }

    /** Local-first needs enough request room for the worker system prompt/tool schemas.
     * Keep the emergency ceiling in the coordinator, but do not let a low-efficiency
     * preset make a private worker unusable because provider overhead alone exceeds 3k. */
    fun effectiveLocalInputTokens(): Int {
        val ownershipFloor = when {
            processingOwnership <= 10 -> 8_000
            processingOwnership <= 25 -> 6_000
            processingOwnership <= 40 -> 4_500
            else -> 1_000
        }
        return maxOf(maxInputTokensPerDelegate, ownershipFloor).coerceIn(1_000, 12_000)
    }

    /** Failed local attempts should still be bounded, but Local-first gets enough room to
     * recover from one or two reasoning-only/empty responses without immediately handing
     * the rest of the workload back to the remote primary. */
    fun effectiveWastedLocalTokens(): Int {
        val ownershipFloor = when {
            processingOwnership <= 10 -> 24_000
            processingOwnership <= 25 -> 16_000
            processingOwnership <= 40 -> 10_000
            else -> 1_000
        }
        return maxOf(maxWastedLocalTokensPerTurn, ownershipFloor).coerceIn(1_000, 64_000)
    }

    fun normalized(): ModelDelegationSettings {
        val normalizedInputCap = maxInputTokensPerDelegate.coerceIn(1000, 12000)
        val normalizedChunk = chunkSizeTokens.coerceIn(1000, normalizedInputCap)
        val normalizedRetryChunk = retryChunkSizeTokens.coerceIn(500, normalizedChunk)
        return copy(
            strategy = strategy.coerceIn(0, 100),
            processingOwnership = processingOwnership.coerceIn(0, 100),
            maxDelegationDepth = maxDelegationDepth.coerceIn(1, 2),
            maxInputCharacters = maxInputCharacters.coerceIn(1000, 64000),
            maxInputTokensPerDelegate = normalizedInputCap,
            chunkSizeTokens = normalizedChunk,
            retryChunkSizeTokens = normalizedRetryChunk,
            maxOutputTokens = maxOutputTokens.coerceIn(64, 4096),
            timeoutSeconds = timeoutSeconds.coerceIn(5, 300),
            timeToFirstTokenTimeoutSeconds = timeToFirstTokenTimeoutSeconds.coerceIn(5, 90),
            idleTokenTimeoutSeconds = idleTokenTimeoutSeconds.coerceIn(5, 90),
            maxDelegateRuntimeSeconds = maxDelegateRuntimeSeconds.coerceIn(30, 120),
            maxConcurrentDelegates = maxConcurrentDelegates.coerceIn(1, 4),
            maxWastedLocalTokensPerTurn = maxWastedLocalTokensPerTurn.coerceIn(1000, 64000),
            evidenceSufficiencyPercent = evidenceSufficiencyPercent.coerceIn(50, 100),
            maxCallsPerTurn = maxCallsPerTurn.coerceIn(1, 16),
            maxLocalModelCalls = maxLocalModelCalls.coerceIn(1, 48),
            maxSearchQueries = maxSearchQueries.coerceIn(1, 20),
            searchResultsPerEngine = searchResultsPerEngine.coerceIn(1, 10),
            maxPages = maxPages.coerceIn(0, 32),
            crawlDepth = crawlDepth.coerceIn(0, 8),
            pageFetchConcurrency = pageFetchConcurrency.coerceIn(1, 16),
            maxPageCharacters = maxPageCharacters.coerceIn(1000, 96000),
            handoffTokens = handoffTokens.coerceIn(128, 8192),
            compactionThresholdCharacters = compactionThresholdCharacters.coerceIn(256, 48000),
            localRetryLimit = localRetryLimit.coerceIn(0, 1),
            lowBatteryThresholdPercent = lowBatteryThresholdPercent.coerceIn(0, 50),
            remoteSynthesisOutputTokens = remoteSynthesisOutputTokens.coerceIn(256, 4096),
            primaryReplayTokens = primaryReplayTokens.coerceIn(1024, 16000),
            primaryReplayResultTokens = primaryReplayResultTokens.coerceIn(128, 2048)
        )
    }
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
