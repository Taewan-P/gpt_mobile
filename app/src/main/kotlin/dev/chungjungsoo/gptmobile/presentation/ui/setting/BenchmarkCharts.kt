package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.hypot

@Composable
internal fun ProfilePerformanceDetails(row: ProfilePerformance) {
    val metrics = row.metrics
    val color = benchmarkTint(row.metrics.provider == "LITERT_LM")
    val trend = row.samples.filter { it.status == "COMPLETED" }.take(30).reversed()
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(row.name, style = MaterialTheme.typography.headlineSmall, color = color)
            Text("${metrics.provider} · ${metrics.model}", style = MaterialTheme.typography.bodyMedium)
        }
        item {
            StatisticsCard("Performance") {
                MetricLine("Median / p95 latency", "${formatLatency(metrics.medianLatencyMs)} / ${formatLatency(metrics.p95LatencyMs)}")
                MetricLine("First text token · median / p95", "${formatLatency(metrics.medianFirstTokenMs)} / ${formatLatency(metrics.p95FirstTokenMs)}")
                MetricLine("Output / request second", metrics.outputTokensPerSecond?.let { "%.1f tok/s".format(it) } ?: "Not reported")
                MetricLine("Success", row.successPercent?.let { "%.1f%%".format(it) } ?: "—")
                MetricLine("Reported input / output", "${number(row.samples.filterNot { it.estimated }.sumOf { it.inputTokens.toLong() })} / ${number(row.samples.filterNot { it.estimated }.sumOf { it.outputTokens.toLong() })}")
                MetricLine("Estimated input / output", "≈ ${number(row.samples.filter { it.estimated }.sumOf { it.inputTokens.toLong() })} / ${number(row.samples.filter { it.estimated }.sumOf { it.outputTokens.toLong() })}")
                MetricLine("Request types", row.samples.groupingBy { it.kind }.eachCount().entries.joinToString { "${it.key}: ${it.value}" })
            }
        }
        item {
            StatisticsCard("Latency · latest 30 completions") {
                TrendChart(trend.map { it.durationMs / 1000.0 }, trend.map { shortTime(it.startedAt) }, color, "seconds")
            }
        }
        item { StatisticsCard("Outcomes") { OutcomeChart(metrics.completed, row.failed, row.canceled, row.unknown) } }
        item { Text("Recent requests · latest 50", style = MaterialTheme.typography.titleMedium) }
        items(row.samples.take(50), key = { it.id }) { sample ->
            var expanded by rememberSaveable(sample.id) { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${shortTime(sample.startedAt)} · ${sample.kind}", color = color, style = MaterialTheme.typography.labelLarge)
                    Text("${sample.status.lowercase()} · ${formatLatency(sample.durationMs)} · ${if (sample.estimated) "≈ " else ""}${number(sample.outputTokens.toLong())} output", color = if (sample.status in setOf("FAILED", "INTERRUPTED")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                    if (expanded) {
                        MetricLine("Input tokens", "${if (sample.estimated) "≈ " else ""}${sample.inputTokens}")
                        MetricLine("First text token", formatLatency(sample.firstTokenMs))
                        MetricLine("Throughput", sample.reportedThroughput()?.let { "%.1f tok/s".format(it) } ?: "Not measured")
                        Text("Request ${sample.id}\nRun ${sample.parentRunId}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PerformanceScatter(rows: List<ProfilePerformance>, onSelect: (String) -> Unit) {
    // At most 300 recent measured requests keeps drawing bounded without inventing samples.
    val points = remember(rows) { rows.flatMap { row -> row.samples.map { row.key to it } }.sortedByDescending { it.second.startedAt }.mapNotNull { (key, sample) -> sample.reportedThroughput()?.let { Triple(key, sample.durationMs / 1000f, it.toFloat()) } }.take(300) }
    val colors = rows.associate { it.key to benchmarkTint(it.metrics.provider == "LITERT_LM") }
    val grid = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    if (points.isEmpty()) {
        Text("A scatter plot appears when a model reports output tokens and completes a timed request.", style = MaterialTheme.typography.bodySmall)
        return
    }
    val maxX = (points.maxOfOrNull { it.second } ?: 1f).coerceAtLeast(1f) * 1.05f
    val maxY = (points.maxOfOrNull { it.third } ?: 1f).coerceAtLeast(1f) * 1.05f
    Text("Output tokens / second · 0 to %.1f".format(maxY), style = MaterialTheme.typography.labelSmall)
    Canvas(
        Modifier.fillMaxWidth().height(190.dp).semantics { contentDescription = "Request latency in seconds versus reported output tokens per second. ${points.size} requests. Use the profile cards below for accessible details." }.pointerInput(points) {
            detectTapGestures { tap ->
                val inset = 10.dp.toPx()
                val hit = points.minByOrNull { point -> hypot(inset + point.second / maxX * (size.width - 2 * inset) - tap.x, size.height - inset - point.third / maxY * (size.height - 2 * inset) - tap.y) }
                if (hit != null) {
                    val distance = hypot(inset + hit.second / maxX * (size.width - 2 * inset) - tap.x, size.height - inset - hit.third / maxY * (size.height - 2 * inset) - tap.y)
                    if (distance <= 28.dp.toPx()) onSelect(hit.first)
                }
            }
        }
    ) {
        val inset = 10.dp.toPx()
        repeat(5) { index ->
            val y = inset + index / 4f * (size.height - 2 * inset)
            drawLine(grid, Offset(inset, y), Offset(size.width - inset, y))
            val x = inset + index / 4f * (size.width - 2 * inset)
            drawLine(grid, Offset(x, inset), Offset(x, size.height - inset))
        }
        points.forEach { point -> drawCircle(colors.getValue(point.first).copy(alpha = 0.8f), 4.dp.toPx(), Offset(inset + point.second / maxX * (size.width - 2 * inset), size.height - inset - point.third / maxY * (size.height - 2 * inset))) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("0 s", style = MaterialTheme.typography.labelSmall)
        Text("Request latency · %.1f s".format(maxX), style = MaterialTheme.typography.labelSmall)
    }
    Text("${points.size} plotted requests · profile colors match the cards below", style = MaterialTheme.typography.labelSmall)
}

private fun number(value: Long) = NumberFormat.getIntegerInstance().format(value)
private fun shortTime(value: Long) = DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))
