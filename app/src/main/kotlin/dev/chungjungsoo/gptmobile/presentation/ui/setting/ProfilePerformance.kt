package dev.chungjungsoo.gptmobile.presentation.ui.setting

import dev.chungjungsoo.gptmobile.data.accounting.ModelInvocation

data class ProfilePerformance(
    val key: String,
    val profileUid: String?,
    val name: String,
    val metrics: ModelPerformance,
    val samples: List<ModelInvocation>
) {
    val failed: Int get() = samples.count { it.status == "FAILED" || it.status == "INTERRUPTED" }
    val canceled: Int get() = samples.count { it.status == "CANCELED" }
    val unknown: Int get() = samples.count { it.status == "STOPPED" }
    val successPercent: Double? get() = (metrics.completed + failed).takeIf { it > 0 }?.let { 100.0 * metrics.completed / it }
}

enum class PerformanceOrder(val label: String) { LATENCY("Speed"), THROUGHPUT("Tokens/s"), SUCCESS("Success") }

internal fun profilePerformance(invocations: List<ModelInvocation>, names: Map<String, String>): List<ProfilePerformance> = invocations
    .distinctBy { it.id }
    .filter { it.status != "RUNNING" }
    .groupBy { Triple(it.profileUid, it.provider, it.model) }
    .map { (key, samples) ->
        ProfilePerformance(
            "${key.first.orEmpty()}|${key.second}|${key.third}",
            key.first,
            key.first?.let { names[it] ?: "Deleted profile (${it.take(8)})" } ?: "Unassigned historical requests",
            modelPerformance(samples).single(),
            samples.sortedByDescending { it.startedAt }
        )
    }

internal fun rankPerformance(rows: List<ProfilePerformance>, order: PerformanceOrder): List<ProfilePerformance> = when (order) {
    PerformanceOrder.LATENCY -> rows.sortedWith(compareBy<ProfilePerformance> { it.metrics.medianLatencyMs ?: Long.MAX_VALUE }.thenByDescending { it.metrics.completed }.thenBy { it.key })
    PerformanceOrder.THROUGHPUT -> rows.sortedWith(compareByDescending<ProfilePerformance> { it.metrics.outputTokensPerSecond ?: -1.0 }.thenByDescending { it.metrics.completed }.thenBy { it.key })
    PerformanceOrder.SUCCESS -> rows.sortedWith(compareByDescending<ProfilePerformance> { it.successPercent ?: -1.0 }.thenByDescending { it.metrics.completed }.thenBy { it.key })
}

internal fun ModelInvocation.reportedThroughput(): Double? = if (!estimated && status == "COMPLETED" && durationMs > 0 && outputTokens > 0) outputTokens * 1000.0 / durationMs else null
