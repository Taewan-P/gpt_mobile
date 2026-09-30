package dev.chungjungsoo.gptmobile.data.benchmark

import kotlin.math.ceil
import kotlin.math.roundToInt

internal data class WorkerBenchmarkTelemetry(
    val durationMs: Long = 0,
    val firstTextMs: Long? = null,
    val decodeTokensPerSecond: Double? = null,
    val estimated: Boolean = false,
    val outputCapViolations: Int = 0
)

data class DelegationBenchmarkRating(
    val score: Int?,
    val attempts: Int,
    val passed: Int,
    val toolTaskSuccessPercent: Double?,
    val successfulToolCalls: Int,
    val toolCalls: Int,
    val medianLatencyMs: Long?,
    val p95LatencyMs: Long?,
    val medianFirstTextMs: Long?,
    val medianDecodeSpeed: Double?,
    val workerInputTokens: Long,
    val workerOutputTokens: Long,
    val primaryInputTokens: Long,
    val primaryOutputTokens: Long,
    val outputCapViolations: Int,
    val estimated: Boolean,
    val dimensions: List<BenchmarkDimension>
)

data class DelegateRanking(val run: BenchmarkRun, val runs: Int, val rating: DelegationBenchmarkRating)

/** Separate from the primary-model score: failed/no-call tool tasks cannot earn speed points. */
fun delegationBenchmarkRating(runs: List<BenchmarkRun>): DelegationBenchmarkRating {
    val samples = runs.filter { it.mode == BenchmarkMode.DELEGATION && it.finished && !it.canceled }
        .flatMap { it.samples }.filter { it.outcome != BenchmarkOutcome.CANCELED }
    val passed = samples.count { it.outcome == BenchmarkOutcome.PASSED }
    val metrics = samples.mapNotNull { it.delegation }
    val successful = samples.filter { it.outcome == BenchmarkOutcome.PASSED }
    val tools = samples.filter { it.testId == "delegation-tools" }
    val toolSuccess = tools.takeIf { it.isNotEmpty() }?.let { 100.0 * it.count { sample -> sample.outcome == BenchmarkOutcome.PASSED && (sample.delegation?.successfulFixtureCalls ?: 0) > 0 } / it.size }
    val latency = median(successful.map { it.durationMs })
    val first = median(successful.mapNotNull { it.delegation?.workerFirstTextMs })
    val speed = median(successful.mapNotNull { it.delegation?.workerDecodeTokensPerSecond?.takeIf { value -> value > 0 && value.isFinite() } })
    fun accuracy(id: String) = samples.filter { it.testId == id }.takeIf { it.isNotEmpty() }
        ?.let { 100.0 * it.count { sample -> sample.outcome == BenchmarkOutcome.PASSED } / it.size }
    val dimensions = listOf(
        BenchmarkDimension("Task success", samples.takeIf { it.isNotEmpty() }?.let { 100.0 * passed / it.size }, 30, "Passed tasks / attempted tasks; errors and timeouts fail"),
        BenchmarkDimension("Successful tool usage", toolSuccess, 30, "Valid tool execution and exact final result; no invocation fails"),
        BenchmarkDimension("Evidence accuracy", accuracy("delegation-compact"), 10, "Preserves the random evidence code"),
        BenchmarkDimension("Research and handoff", accuracy("delegation-research"), 10, "Page read, preserved evidence, correct primary answer and source"),
        BenchmarkDimension("Latency", latency?.let { (5000.0 / it.coerceAtLeast(1) * 100).coerceAtMost(100.0) }, 10, "100 at 5 seconds; successful end-to-end cases only"),
        BenchmarkDimension("Generation speed", speed?.let { (100 * it / 40).coerceAtMost(100.0) }, 10, "100 at 40 estimated text tokens/s; observed decoding interval")
    )
    val measured = dimensions.filter { it.score != null }
    val weight = measured.sumOf { it.weight }
    val successRate = if (samples.isEmpty()) 0.0 else passed.toDouble() / samples.size
    // Reliability also limits the entire score, so a fast helper failing tasks cannot rank first.
    val score = if (samples.size >= 3 && samples.map { it.testId }.containsAll(delegationBenchmarkSuite().map { it.id })) {
        minOf(measured.sumOf { it.score!! * it.weight } / weight, 100 * successRate).roundToInt()
    } else {
        null
    }
    return DelegationBenchmarkRating(
        score, samples.size, passed, toolSuccess, metrics.sumOf { it.successfulFixtureCalls }, metrics.sumOf { it.fixtureCalls },
        latency, percentile(successful.map { it.durationMs }, .95), first, speed,
        metrics.sumOf { it.workerInputTokens }, metrics.sumOf { it.workerOutputTokens },
        metrics.sumOf { it.primaryInputTokens }, metrics.sumOf { it.primaryOutputTokens },
        metrics.sumOf { it.outputCapViolations }, metrics.any { it.workerEstimated || it.primaryEstimated }, dimensions
    )
}

/** Rankings compare helpers under the same primary, worker configuration, settings and suite. */
fun delegateRankings(
    history: List<BenchmarkRun>,
    primaryConfigKey: String,
    settings: dev.chungjungsoo.gptmobile.data.model.ModelDelegationSettings? = null
): List<DelegateRanking> = history
    .filter { it.mode == BenchmarkMode.DELEGATION && it.suiteVersion == 2 && it.configKey == primaryConfigKey && it.finished && !it.canceled }
    .filter { settings == null || it.delegationSettings?.copy(targetProfileUid = "", fallbackToAnotherProfile = false) == settings.normalized().copy(targetProfileUid = "", fallbackToAnotherProfile = false) }
    .groupBy { run ->
        val worker = run.samples.mapNotNull { it.delegation }.firstOrNull()
        listOf(worker?.workerUid, worker?.workerConfigKey, run.delegationSettings?.toString())
    }
    .values.mapNotNull { group ->
        val runs = group.sortedByDescending { it.startedAt }.take(5)
        if (runs.first().samples.none { it.delegation != null }) return@mapNotNull null
        DelegateRanking(runs.first(), runs.size, delegationBenchmarkRating(runs))
    }
    .sortedWith(compareByDescending<DelegateRanking> { it.rating.score ?: -1 }.thenBy { it.rating.medianLatencyMs ?: Long.MAX_VALUE })

private fun <T : Comparable<T>> median(values: List<T>): T? = percentile(values, .5)

private fun <T : Comparable<T>> percentile(values: List<T>, fraction: Double): T? = values.sorted().takeIf { it.isNotEmpty() }
    ?.get((ceil(values.size * fraction).toInt() - 1).coerceAtLeast(0))
