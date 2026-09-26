package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UsageStatisticsScreen(onBack: () -> Unit, viewModel: UsageStatisticsViewModel = hiltViewModel()) {
    val stats by viewModel.statistics.collectAsStateWithLifecycle()
    var order by rememberSaveable { mutableStateOf(PerformanceOrder.LATENCY) }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val rankings = remember(stats.profilePerformance, order) { rankPerformance(stats.profilePerformance, order) }
    val selected = stats.profilePerformance.firstOrNull { it.key == selectedKey }
    val samples = remember(stats.profilePerformance) { stats.profilePerformance.flatMap { it.samples } }
    val measured = samples.filterNot { it.estimated }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Usage & performance") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary) }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7 to "7 days", 30 to "30 days", 0 to "Stored history").forEach { (days, label) ->
                        FilterChip(stats.days == days, {
                            selectedKey = null
                            viewModel.selectRange(days)
                        }, label = { Text(label) })
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("MODEL REQUESTS", style = MaterialTheme.typography.labelLarge)
                        Text(number(samples.size.toLong()), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        Text("${number(measured.sumOf { it.outputTokens.toLong() })} reported output tokens", style = MaterialTheme.typography.titleMedium)
                        Text("${number(measured.sumOf { it.inputTokens.toLong() })} reported input · ${stats.profilePerformance.size} profile / model combinations", style = MaterialTheme.typography.bodySmall)
                        if (samples.any { it.estimated }) Text("${samples.count { it.estimated }} requests have estimates; excluded from reported totals and speed measurements.", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item {
                StatisticsCard("Conversation output over time") {
                    Text(if (stats.days == 0) "Last 30 days · reported tokens" else "Reported tokens each day", style = MaterialTheme.typography.labelMedium)
                    TrendChart(stats.dailyTokens.map { it.second.toDouble() }, stats.dailyTokens.map { it.first.toString() }, MaterialTheme.colorScheme.primary, "tokens")
                }
            }
            item {
                StatisticsCard("Latency × throughput") {
                    Text("Each dot is one completed request. Tap a dot or a profile below for details.", style = MaterialTheme.typography.bodySmall)
                    PerformanceScatter(stats.profilePerformance) { selectedKey = it }
                }
            }
            item {
                StatisticsCard("Request outcomes") {
                    OutcomeChart(samples.count { it.status == "COMPLETED" }, samples.count { it.status in setOf("FAILED", "INTERRUPTED") }, samples.count { it.status == "CANCELED" }, samples.count { it.status == "STOPPED" })
                }
            }
            item {
                Text("Compare profiles", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PerformanceOrder.entries.forEach { item -> FilterChip(order == item, { order = item }, label = { Text(item.label) }) }
                }
                Text(
                    when (order) {
                        PerformanceOrder.LATENCY -> "Fastest median request first · lower is better"
                        PerformanceOrder.THROUGHPUT -> "Reported output tokens per second · higher is better"
                        PerformanceOrder.SUCCESS -> "Completed ÷ (completed + failed + interrupted)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (rankings.isEmpty()) item { Text("Send a message to begin measuring model performance. Your existing conversation usage is retained below.") }
            items(rankings, key = { it.key }) { row ->
                val color = modelChartColor(row.key)
                Card(onClick = { selectedKey = row.key }, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(row.name, color = color, fontWeight = FontWeight.SemiBold)
                                Text(row.metrics.model, style = MaterialTheme.typography.bodyMedium)
                                Text(row.metrics.provider, style = MaterialTheme.typography.labelSmall)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Profile performance", tint = color)
                        }
                        Text(
                            when (order) {
                                PerformanceOrder.LATENCY -> formatLatency(row.metrics.medianLatencyMs)
                                PerformanceOrder.THROUGHPUT -> row.metrics.outputTokensPerSecond?.let { "%.1f tok/s".format(it) } ?: "Not reported"
                                PerformanceOrder.SUCCESS -> row.successPercent?.let { "%.1f%%".format(it) } ?: "No outcomes"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = color
                        )
                        Text("${row.metrics.requests} requests · p95 ${formatLatency(row.metrics.p95LatencyMs)} · ${row.failed} failed / interrupted", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            item {
                StatisticsCard("Tools") {
                    if (stats.toolUsage.isEmpty()) Text("No tool calls in this period.")
                    val max = stats.toolUsage.maxOfOrNull { it.calls }?.coerceAtLeast(1) ?: 1
                    stats.toolUsage.take(12).forEach { tool ->
                        Text(tool.name, color = modelChartColor(tool.name), style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(progress = { tool.calls.toFloat() / max }, modifier = Modifier.fillMaxWidth(), color = modelChartColor(tool.name))
                        Text("${tool.calls} calls · ${tool.failures} failures", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item {
                StatisticsCard("Conversation history") {
                    MetricLine("Runs / conversations", "${stats.runs} / ${stats.conversations}")
                    MetricLine("Reported output", number(stats.generatedTokens))
                    MetricLine("Unreported output estimate", "≈ ${number(stats.estimatedTokens)}")
                    Text("Includes older conversations recorded before request performance tracking was available. Conversation totals and individual request totals are separate views; they are never added together.", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Text("Based on up to 10,000 stored requests, runs and tool calls. Request timing includes network and time to first text token. Throughput excludes estimated usage. Canceled requests and older requests with unknown outcomes are excluded from success rates. Estimates are not billing totals.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (selected != null) {
        ModalBottomSheet(onDismissRequest = { selectedKey = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            ProfilePerformanceDetails(selected)
        }
    }
}

@Composable
private fun ProfilePerformanceDetails(row: ProfilePerformance) {
    val metrics = row.metrics
    val color = modelChartColor(row.key)
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
private fun PerformanceScatter(rows: List<ProfilePerformance>, onSelect: (String) -> Unit) {
    // At most 300 recent measured requests keeps drawing bounded without inventing samples.
    val points = remember(rows) { rows.flatMap { row -> row.samples.map { row.key to it } }.sortedByDescending { it.second.startedAt }.mapNotNull { (key, sample) -> sample.reportedThroughput()?.let { Triple(key, sample.durationMs / 1000f, it.toFloat()) } }.take(300) }
    val colors = rows.associate { it.key to modelChartColor(it.key) }
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

@Composable
private fun TrendChart(values: List<Double>, labels: List<String>, color: Color, unit: String) {
    val maximum = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val grid = MaterialTheme.colorScheme.primary.copy(alpha = .15f)
    if (values.isEmpty()) {
        Text("No samples in this period.")
        return
    }
    Text("0 – ${if (maximum >= 100) number(maximum.toLong()) else "%.1f".format(maximum)} $unit", style = MaterialTheme.typography.labelSmall, color = color)
    Canvas(Modifier.fillMaxWidth().height(135.dp).semantics { contentDescription = values.mapIndexed { index, value -> "${labels.getOrNull(index).orEmpty()}: $value $unit" }.joinToString() }) {
        val inset = 6.dp.toPx()
        val height = size.height - 2 * inset
        repeat(4) { drawLine(grid, Offset(inset, inset + height * it / 3f), Offset(size.width - inset, inset + height * it / 3f)) }
        val points = values.mapIndexed { index, value -> Offset(inset + index.toFloat() / (values.size - 1).coerceAtLeast(1) * (size.width - 2 * inset), size.height - inset - (value / maximum * height).toFloat()) }
        val line = Path().apply { points.forEachIndexed { index, point -> if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) } }
        val area = Path().apply {
            addPath(line)
            lineTo(points.last().x, size.height - inset)
            lineTo(points.first().x, size.height - inset)
            close()
        }
        drawPath(area, color.copy(alpha = .12f))
        drawPath(line, color, style = Stroke(2.dp.toPx()))
        points.forEach { drawCircle(color, 2.5.dp.toPx(), it) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(labels.firstOrNull().orEmpty(), style = MaterialTheme.typography.labelSmall)
        Text(labels.lastOrNull().orEmpty(), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun OutcomeChart(completed: Int, failed: Int, canceled: Int, unknown: Int) {
    val values = listOf(completed, failed, canceled, unknown)
    val labels = listOf("Completed", "Failed / interrupted", "Canceled", "Unknown legacy outcome")
    val colors = listOf(Color(0xFF16A6A1), MaterialTheme.colorScheme.error, Color(0xFFE99538), MaterialTheme.colorScheme.secondary)
    val total = values.sum().coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.primary.copy(alpha = .12f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Canvas(Modifier.size(96.dp).semantics { contentDescription = labels.zip(values).joinToString { "${it.first}: ${it.second}" } }) {
            val inset = 10.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(14.dp.toPx()))
            var angle = -90f
            values.forEachIndexed { index, value ->
                val sweep = value * 360f / total
                if (value > 0) drawArc(colors[index], angle, sweep, false, Offset(inset, inset), arcSize, style = Stroke(14.dp.toPx()))
                angle += sweep
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            labels.forEachIndexed { index, label -> if (index < 3 || values[index] > 0) Text("${values[index]} $label", color = colors[index], style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun StatisticsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
internal fun MetricLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun modelChartColor(name: String): Color {
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, Color(0xFF16A6A1), Color(0xFFE99538), Color(0xFFAC7DEC), Color(0xFFE2739C))
    return colors[(name.hashCode() and Int.MAX_VALUE) % colors.size]
}

internal fun formatLatency(value: Long?): String = value?.let { "%.2f s".format(it / 1000.0) } ?: "—"
private fun number(value: Long) = NumberFormat.getIntegerInstance().format(value)
private fun shortTime(value: Long) = DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))
