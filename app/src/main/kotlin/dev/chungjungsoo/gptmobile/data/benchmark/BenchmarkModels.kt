package dev.chungjungsoo.gptmobile.data.benchmark

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import java.security.MessageDigest
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.serialization.Serializable

@Serializable
enum class BenchmarkMode(val label: String) { QUICK("Quick"), FULL("Full"), DELEGATION("Delegation") }

@Serializable
enum class BenchmarkOutcome { PASSED, FAILED, ERROR, TIMED_OUT, CANCELED, UNSUPPORTED }

@Serializable
data class DelegationBenchmarkMetrics(
    val workerUid: String,
    val workerName: String,
    val workerProvider: String,
    val workerCalls: Int,
    val workerInputTokens: Long,
    val workerOutputTokens: Long,
    val primaryInputTokens: Long,
    val primaryOutputTokens: Long,
    val searches: Int,
    val pagesRead: Int,
    val rawEvidenceBytes: Int,
    val handoffCharacters: Int,
    val fixtureCalls: Int,
    val successfulFixtureCalls: Int,
    val primaryEstimated: Boolean = false,
    val workerModel: String = "",
    val workerConfigKey: String = "",
    val workerEstimated: Boolean = false,
    val workerDurationMs: Long = 0,
    val workerFirstTextMs: Long? = null,
    val workerDecodeTokensPerSecond: Double? = null,
    val outputCapViolations: Int = 0
)

@Serializable
data class BenchmarkSample(
    val testId: String,
    val label: String,
    val category: String,
    val outcome: BenchmarkOutcome,
    val durationMs: Long = 0,
    val firstTextMs: Long? = null,
    val outputTokens: Int = 0,
    val estimatedTokens: Boolean = true,
    val outputCharacters: Int = 0,
    val chunks: Int = 0,
    val longestGapMs: Long? = null,
    val toolCalls: Int = 0,
    val successfulToolCalls: Int = 0,
    val preview: String = "",
    val error: String? = null,
    val lastTextMs: Long? = null,
    val nativeMetrics: dev.chungjungsoo.gptmobile.data.localruntime.NativeInferenceMetrics? = null,
    val inputTokens: Int = 0,
    val delegation: DelegationBenchmarkMetrics? = null,
    val reconnectAttempts: Int = 0
) {
    val completed: Boolean get() = outcome == BenchmarkOutcome.PASSED || outcome == BenchmarkOutcome.FAILED

    // A one-chunk response has no observed decoding interval; avoid inventing a speed.
    val decodeTokensPerSecond: Double? get() = firstTextMs?.let { first ->
        val end = lastTextMs ?: durationMs
        (outputTokens * 1000.0 / (end - first)).takeIf {
            completed && outputTokens > 0 && chunks > 1 && end > first && it.isFinite()
        }
    }
}

@Serializable
data class BenchmarkRun(
    val id: String,
    val profileUid: String,
    val profileName: String,
    val provider: String,
    val model: String,
    val configKey: String,
    val local: Boolean,
    val mode: BenchmarkMode,
    val startedAt: Long,
    val samples: List<BenchmarkSample> = emptyList(),
    val canceled: Boolean = false,
    val finished: Boolean = true,
    val suiteVersion: Int = 1,
    val device: String = "",
    val backend: String? = null,
    val accelerator: String? = null,
    val peakClientPssKb: Long? = null,
    val thermalBefore: Int? = null,
    val thermalAfter: Int? = null,
    val batteryBefore: Int? = null,
    val batteryAfter: Int? = null,
    val engineWasLoaded: Boolean = false,
    val stoppedReason: String? = null,
    val delegationSettings: dev.chungjungsoo.gptmobile.data.model.ModelDelegationSettings? = null
)

data class BenchmarkDimension(val label: String, val score: Double?, val weight: Int, val detail: String)

data class BenchmarkRating(
    val score: Int?,
    val dimensions: List<BenchmarkDimension>,
    val sampleCount: Int,
    val completed: Int,
    val medianFirstTextMs: Long?,
    val p95FirstTextMs: Long?,
    val medianDurationMs: Long?,
    val medianSpeed: Double?,
    val estimatedSpeed: Boolean,
    val toolSuccessPercent: Double?,
    val measuredWeight: Int
) {
    val grade: String get() = when {
        score == null -> "Not rated"
        score >= 90 -> "Excellent"
        score >= 75 -> "Strong"
        score >= 60 -> "Good"
        score >= 40 -> "Fair"
        else -> "Needs attention"
    }
}

fun benchmarkConfigKey(profile: PlatformV2, localEnvironment: String = ""): String {
    val values = listOf(
        profile.compatibleType.name, profile.model, profile.apiUrl, profile.accelerator.orEmpty(),
        profile.temperature.toString(), profile.maxTokens.toString(), profile.timeout.toString(), profile.stream.toString(),
        profile.topP.toString(), profile.topK.toString(), profile.openRouterRouting.orEmpty(), profile.ollamaOptions.orEmpty(),
        profile.providerConnectionUid.orEmpty(), if (profile.compatibleType == ClientType.LITERT_LM) localEnvironment else ""
    )
    return MessageDigest.getInstance("SHA-256").digest(values.joinToString("\u0000").toByteArray())
        .joinToString("") { "%02x".format(it) }
}

fun comparableRuns(history: List<BenchmarkRun>, profile: PlatformV2, mode: BenchmarkMode, localEnvironment: String = ""): List<BenchmarkRun> {
    val key = benchmarkConfigKey(profile, localEnvironment)
    return history.filter { it.profileUid == profile.uid && it.configKey == key && it.mode == mode && it.suiteVersion == 1 && it.finished && !it.canceled && it.stoppedReason == null }
        .sortedByDescending { it.startedAt }.take(5)
}

/** Versioned app score, not a general intelligence or hardware benchmark. Missing data is never a zero. */
fun benchmarkRating(runs: List<BenchmarkRun>, local: Boolean = runs.firstOrNull()?.local == true): BenchmarkRating {
    val samples = runs.filter { it.finished && !it.canceled && it.stoppedReason == null }.flatMap { it.samples }
        .filter { it.outcome !in setOf(BenchmarkOutcome.CANCELED, BenchmarkOutcome.UNSUPPORTED) }
    val completed = samples.filter { it.completed }
    val speedSamples = completed.filter { it.category == "speed" && it.outcome == BenchmarkOutcome.PASSED }
    val speeds = speedSamples.mapNotNull { it.decodeTokensPerSecond }.sorted()
    val first = completed.mapNotNull { it.firstTextMs }.sorted()
    val speedTarget = if (local) 40.0 else 80.0
    val latencyTargetMs = if (local) 1500.0 else 750.0
    val speed = percentile(speeds, .5)
    val latency = percentile(first, .5)
    val success = samples.takeIf { it.isNotEmpty() }?.let { 100.0 * completed.size / it.size }
    val consistency = speeds.takeIf { it.size >= 3 && it.average() > 0 }?.let { data ->
        val average = data.average()
        val deviation = sqrt(data.map { (it - average) * (it - average) }.average())
        (100.0 * (1.0 - deviation / average)).coerceIn(0.0, 100.0)
    }
    fun passRate(category: String): Double? = samples.filter { it.category == category }.takeIf { it.isNotEmpty() }
        ?.let { items -> 100.0 * items.count { it.outcome == BenchmarkOutcome.PASSED } / items.size }
    val toolSamples = samples.filter { it.category == "tools" }
    val toolSuccess = toolSamples.takeIf { it.isNotEmpty() }?.let {
        // A model that never invokes the required fixture has failed the tool task.
        100.0 * it.count { sample -> sample.outcome == BenchmarkOutcome.PASSED } / it.size
    }
    val dimensions = listOf(
        BenchmarkDimension("Generation speed", speed?.let { (100 * it / speedTarget).coerceIn(0.0, 100.0) }, 20, "100 at $speedTarget tok/s; observed decode interval"),
        BenchmarkDimension("First response", latency?.let { (100 * latencyTargetMs / it.coerceAtLeast(1)).coerceIn(0.0, 100.0) }, 15, "100 at ${latencyTargetMs.toInt()} ms or faster"),
        BenchmarkDimension("Completion reliability", success, 15, "Completed requests ÷ attempted requests; timeouts count as failures"),
        BenchmarkDimension("Consistency", consistency, 10, "Variation across at least three speed trials"),
        BenchmarkDimension("Task accuracy", passRate("task"), 15, "Exact instruction, arithmetic and multi-turn fixture checks"),
        BenchmarkDimension("Structured output", passRate("json"), 10, "Strict JSON and expected field values"),
        BenchmarkDimension("Tool success", toolSuccess, 15, "Correct fixture call, arguments and final answer")
    )
    val measured = dimensions.filter { it.score != null }
    val weight = measured.sumOf { it.weight }
    val score = if (samples.size >= 3 && weight >= 40) (measured.sumOf { it.score!! * it.weight } / weight).roundToInt() else null
    return BenchmarkRating(
        score, dimensions, samples.size, completed.size, latency, percentile(first, .95),
        percentile(completed.map { it.durationMs }.sorted(), .5), speed,
        speedSamples.any { it.estimatedTokens && it.decodeTokensPerSecond != null }, toolSuccess, weight
    )
}

private fun <T> percentile(sorted: List<T>, fraction: Double): T? = sorted.takeIf { it.isNotEmpty() }
    ?.get((ceil(sorted.size * fraction).toInt() - 1).coerceAtLeast(0))
