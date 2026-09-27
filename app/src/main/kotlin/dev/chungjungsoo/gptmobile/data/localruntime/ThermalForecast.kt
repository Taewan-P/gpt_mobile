package dev.chungjungsoo.gptmobile.data.localruntime

internal data class ThermalForecast(val headroom: Float? = null, val moderateThreshold: Float? = null) {
    fun sanitized() = copy(
        headroom = headroom?.takeIf { it.isFinite() && it >= 0f },
        moderateThreshold = moderateThreshold?.takeIf { it.isFinite() && it > 0f && it <= 1f }
    )
}

/** Shared by both runtime backends: avoid repeated binder calls and Android's sampling limit. */
internal class ThermalForecastSampler {
    private var lastReadMs: Long? = null
    private var cached = ThermalForecast()

    @Synchronized
    fun read(nowMs: Long, query: () -> ThermalForecast): ThermalForecast {
        if (lastReadMs?.let { nowMs >= it && nowMs - it < 10_000L } == true) return cached
        lastReadMs = nowMs
        cached = try {
            query().sanitized()
        } catch (_: Exception) {
            ThermalForecast()
        }
        return cached
    }
}

/** Tighten immediately; require 30 seconds of a stable lower-pressure policy before relaxing. */
internal class AdaptivePolicyStabilizer(private val recoveryMs: Long = 30_000L) {
    private var active: AdaptiveThrottlingPolicy? = null
    private var recovering: AdaptiveThrottlingPolicy? = null
    private var recoveryStartedMs = 0L

    @Synchronized
    fun update(candidate: AdaptiveThrottlingPolicy, nowMs: Long): AdaptiveThrottlingPolicy {
        val previous = active ?: return candidate.also { active = it }
        val tightened = AdaptiveThrottlingPolicy(
            maxOf(previous.streamPublishIntervalMillis, candidate.streamPublishIntervalMillis),
            minOf(previous.topKReductionRatio, candidate.topKReductionRatio),
            listOfNotNull(previous.maxTokensClamp, candidate.maxTokensClamp).minOrNull(),
            previous.isCooperativeYieldAggressive || candidate.isCooperativeYieldAggressive
        )
        if (tightened != previous || candidate == previous) {
            active = tightened
            recovering = null
        } else if (recovering != candidate || nowMs < recoveryStartedMs) {
            recovering = candidate
            recoveryStartedMs = nowMs
        } else if (nowMs - recoveryStartedMs >= recoveryMs) {
            active = candidate
            recovering = null
        }
        return checkNotNull(active)
    }
}
