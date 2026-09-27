package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.data.benchmark.BenchmarkMode
import dev.chungjungsoo.gptmobile.data.benchmark.BenchmarkOutcome
import dev.chungjungsoo.gptmobile.data.benchmark.BenchmarkRating
import dev.chungjungsoo.gptmobile.data.benchmark.BenchmarkRun
import dev.chungjungsoo.gptmobile.data.benchmark.benchmarkConfigKey
import dev.chungjungsoo.gptmobile.data.benchmark.benchmarkRating
import dev.chungjungsoo.gptmobile.data.benchmark.benchmarkSuite
import dev.chungjungsoo.gptmobile.data.benchmark.comparableRuns
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileBenchmarkScreen(onBack: () -> Unit, onUsage: () -> Unit, viewModel: ProfileBenchmarkViewModel = hiltViewModel()) {
    DisposableEffect(viewModel) { onDispose { viewModel.cancel() } }
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val profile by viewModel.selected.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val localEnvironment by viewModel.localEnvironment.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val activeRequests by viewModel.activeRequests.collectAsStateWithLifecycle()
    val everyday by viewModel.everyday.collectAsStateWithLifecycle()
    val everydayTools by viewModel.everydayTools.collectAsStateWithLifecycle()
    val days by viewModel.days.collectAsStateWithLifecycle()
    val rangeLabel = if (days == 0) "stored history" else "$days days"
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var mode by rememberSaveable { mutableStateOf(BenchmarkMode.QUICK) }
    var typeFilter by rememberSaveable { mutableIntStateOf(0) }
    var performanceOrder by rememberSaveable { mutableStateOf(PerformanceOrder.LATENCY) }
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = profile
    val local = selected?.compatibleType == ClientType.LITERT_LM
    val tint = benchmarkTint(local)
    val profileHistory = remember(history, selected?.uid) { history.filter { it.profileUid == selected?.uid } }
    val matching = remember(profileHistory, selected, mode, localEnvironment) { selected?.let { comparableRuns(profileHistory, it, mode, localEnvironment) }.orEmpty() }
    val rating = remember(matching, local) { benchmarkRating(matching, local) }
    val detail = everyday.firstOrNull { it.key == detailKey }
    Scaffold(topBar = {
        TopAppBar(title = { Text("AI profile benchmarks") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        }, actions = {
            IconButton(onClick = onUsage) { Icon(Icons.Default.BarChart, "Usage") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("PERFORMANCE LAB", style = MaterialTheme.typography.labelLarge, color = tint)
                Text("Know what your AI can do", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Repeatable tests, transparent ratings and real conversation performance.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { BenchmarkProfilePicker(profiles, selected, progress == null, viewModel::select) }
            if (error != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(error.orEmpty(), color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(onClick = viewModel::dismissError) { Text("Dismiss") }
                        }
                    }
                }
            }
            if (selected == null) {
                item { BenchmarkPanel("No AI profiles yet") { Text("Create an AI profile in Settings, choose its model, then return here to measure it.") } }
            } else {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Overview", "Everyday", "Compare", "History").forEachIndexed { index, label ->
                            FilterChip(tab == index, { tab = index }, label = { Text(label) })
                        }
                    }
                }
                progress?.let { current ->
                    item {
                        BenchmarkPanel("Running · ${current.profileName}") {
                            LinearProgressIndicator(progress = { current.completed.toFloat() / current.total }, modifier = Modifier.fillMaxWidth(), color = tint)
                            Text("${current.completed} / ${current.total} tests · ${current.testName}")
                            TextButton(onClick = viewModel::cancel) {
                                Icon(Icons.Default.Stop, null)
                                Text("Stop benchmark")
                            }
                        }
                    }
                }
                if (tab == 1 || tab == 2) {
                    item {
                        Column {
                            Text("Everyday measurement window", style = MaterialTheme.typography.labelMedium)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(7 to "7 days", 30 to "30 days", 0 to "Stored history").forEach { (value, label) ->
                                    FilterChip(days == value, { viewModel.selectRange(value) }, label = { Text(label) })
                                }
                            }
                        }
                    }
                }
                when (tab) {
                    0 -> {
                        item {
                            BenchmarkPanel("Run a benchmark") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BenchmarkMode.entries.forEach { option -> FilterChip(mode == option, { mode = option }, enabled = progress == null, label = { Text(option.label) }) }
                                }
                                Text(if (mode == BenchmarkMode.QUICK) "5 tests · speed, instructions, JSON, arithmetic and tools" else "8 tests · adds repeated speed trials and conversation recall", style = MaterialTheme.typography.bodyMedium)
                                Text("Up to 512 output tokens per request and 90 seconds per test. Tools use a harmless in-memory fixture. ${if (local) "Keep this device cool and idle for comparable results." else "Requests use this profile’s provider and may incur its normal charges."}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Button(
                                    onClick = { viewModel.start(mode) },
                                    enabled = ready && progress == null && !activeRequests,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = tint, contentColor = if (tint.luminance() > .5f) Color.Black else Color.White)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null)
                                    Text("Run ${mode.label.lowercase()} benchmark", Modifier.padding(start = 8.dp))
                                }
                                if (activeRequests && progress == null) Text("Waiting for active model requests to finish.", style = MaterialTheme.typography.labelSmall)
                                Text("Leaving this page cancels an active run. Results save after each test.", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        item { BenchmarkScoreCard(rating, local, matching.size, mode) }
                        if (matching.size > 1) {
                            item {
                                BenchmarkPanel("Score over time") {
                                    val trend = matching.reversed().mapNotNull { run -> benchmarkRating(listOf(run)).score?.let { run.startedAt to it } }
                                    TrendChart(trend.map { it.second.toDouble() }, trend.map { benchmarkDate(it.first) }, tint, "app score")
                                }
                            }
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                BenchmarkTile("First response", formatLatency(rating.medianFirstTextMs), "median first text", Modifier.weight(1f), tint)
                                BenchmarkTile("Generation speed", rating.medianSpeed?.let { "${if (rating.estimatedSpeed) "≈ " else ""}%.1f".format(it) } ?: "—", "tokens / decode second", Modifier.weight(1f), tint)
                            }
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                BenchmarkTile("Completion", rating.sampleCount.takeIf { it > 0 }?.let { "${(100.0 * rating.completed / it).roundToInt()}%" } ?: "—", "${rating.completed} / ${rating.sampleCount} requests", Modifier.weight(1f), tint)
                                BenchmarkTile("Tool success", percent(rating.toolSuccessPercent), "validated tool tasks", Modifier.weight(1f), tint)
                            }
                        }
                        item {
                            BenchmarkPanel("What makes the score") {
                                rating.dimensions.forEach { dimension ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(dimension.label, style = MaterialTheme.typography.bodyMedium)
                                        Text(dimension.score?.let { "${it.roundToInt()} / 100" } ?: "Not measured", style = MaterialTheme.typography.labelMedium, color = tint)
                                    }
                                    LinearProgressIndicator(progress = { (dimension.score ?: 0.0).toFloat() / 100 }, modifier = Modifier.fillMaxWidth(), color = tint)
                                    Text("${dimension.weight}% weight · ${dimension.detail}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        item {
                            BenchmarkPanel("Measurement notes") {
                                MetricLine("p95 first response", formatLatency(rating.p95FirstTextMs))
                                MetricLine("Median request time", formatLatency(rating.medianDurationMs))
                                Text("Suite v1 · latest 5 completed ${mode.label.lowercase()} runs with this configuration. Fixed instructions, temperature 0 and reasoning off; profile settings are preserved. Network, model loading and queue time are included in first response. Token estimates use characters ÷ 4 and are marked ≈. One-chunk responses have no measured decode speed.", style = MaterialTheme.typography.bodySmall)
                                Text("This is an app performance score, not a general intelligence test. Missing measurements are excluded and remaining weights are normalized. Local and remote scores use different speed targets and are ranked separately.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    1 -> {
                        val rows = everyday.filter { it.profileUid == selected.uid && it.metrics.model == selected.model && it.metrics.provider == selected.compatibleType.name }
                        val toolMetrics = everydayTools.firstOrNull { it.profileUid == selected.uid && it.model == selected.model && it.provider == selected.compatibleType.name }
                        item {
                            BenchmarkPanel("Everyday performance · $rangeLabel") {
                                Text("Measured from actual conversations for this profile and model. Prompt lengths, tools and server load vary; these observations do not change the controlled benchmark score.", style = MaterialTheme.typography.bodySmall)
                                MetricLine("Tool success", percent(toolMetrics?.successPercent))
                                MetricLine("Tool completions / failures", "${toolMetrics?.completed ?: 0} / ${toolMetrics?.failed ?: 0}")
                                Text("Canceled and pending tools are excluded. Up to 10,000 stored requests, runs and tool events.", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (rows.isEmpty()) item { Text("Send a message with this profile to start collecting everyday performance.") }
                        items(rows, key = { it.key }) { row -> EverydayPerformanceCard(row, local) { detailKey = row.key } }
                        item {
                            BenchmarkPanel("Latency × throughput") {
                                Text("Reported tokens only. Tap a point for request history.", style = MaterialTheme.typography.bodySmall)
                                PerformanceScatter(rows) { detailKey = it }
                            }
                        }
                    }
                    2 -> {
                        item {
                            BenchmarkPanel("Compare AI profiles") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BenchmarkMode.entries.forEach { option -> FilterChip(mode == option, { mode = option }, enabled = progress == null, label = { Text(option.label) }) }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("All", "Local", "Remote").forEachIndexed { index, label -> FilterChip(typeFilter == index, { typeFilter = index }, label = { Text(label) }) }
                                }
                                Text("Same suite and test mode; latest 5 runs per current configuration. Compare coverage and sample counts alongside scores. Local and remote have separate target scales.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        listOf(true, false).filter { typeFilter == 0 || it == (typeFilter == 1) }.forEach { groupLocal ->
                            val ranking = profiles.filter { (it.compatibleType == ClientType.LITERT_LM) == groupLocal }.map { item ->
                                val runs = comparableRuns(history, item, mode, localEnvironment)
                                Triple(item, benchmarkRating(runs, groupLocal), runs.size)
                            }.sortedByDescending { it.second.score ?: -1 }
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BenchmarkTypeBadge(groupLocal)
                                    Text("${ranking.size} profiles", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            items(ranking, key = { "rank-${it.first.uid}" }) { (item, score, count) ->
                                Card(onClick = {
                                    viewModel.select(item)
                                    tab = 0
                                }, shape = RoundedCornerShape(20.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(item.name, fontWeight = FontWeight.SemiBold)
                                            Text(item.model, style = MaterialTheme.typography.bodySmall)
                                            Text("$count runs · ${score.sampleCount} tests · ${score.measuredWeight}% coverage", style = MaterialTheme.typography.labelSmall)
                                        }
                                        Text(score.score?.toString() ?: "—", style = MaterialTheme.typography.headlineMedium, color = benchmarkTint(groupLocal))
                                    }
                                }
                            }
                        }
                        item {
                            BenchmarkPanel("Everyday rankings · $rangeLabel") {
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PerformanceOrder.entries.forEach { option -> FilterChip(performanceOrder == option, { performanceOrder = option }, label = { Text(option.label) }) }
                                }
                                Text("Observed requests, including previous models and deleted profiles. These are not controlled comparisons.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        val observed = rankPerformance(everyday.filter { typeFilter == 0 || (it.metrics.provider == ClientType.LITERT_LM.name) == (typeFilter == 1) }, performanceOrder)
                        items(observed, key = { "observed-${it.key}" }) { row -> EverydayPerformanceCard(row, row.metrics.provider == ClientType.LITERT_LM.name) { detailKey = row.key } }
                    }
                    3 -> {
                        item { Text("Saved runs · ${profileHistory.size}", style = MaterialTheme.typography.titleLarge) }
                        item { Text("The latest 200 runs across all profiles are stored on this device. Canceled and interrupted runs are retained but excluded from ratings.", style = MaterialTheme.typography.bodySmall) }
                        if (profileHistory.isEmpty()) item { BenchmarkPanel("Your first result starts here") { Text("Run a quick benchmark from Overview. Every test will include its outcome, timing and response preview.") } }
                        items(profileHistory, key = { it.id }) { run ->
                            BenchmarkHistoryCard(run, run.configKey == benchmarkConfigKey(selected, localEnvironment), progress == null, { deleteId = run.id })
                        }
                        viewModel.legacyReport?.let { report ->
                            item {
                                BenchmarkPanel("Archived connection-doctor benchmark") {
                                    Text("Previous format · excluded from ratings", style = MaterialTheme.typography.labelSmall)
                                    Text(report, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (detail != null) ModalBottomSheet(onDismissRequest = { detailKey = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) { ProfilePerformanceDetails(detail) }
    if (deleteId != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete this benchmark?") },
            text = { Text("The run will be removed from history and its profile rating will be recalculated.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteId?.let(viewModel::delete)
                    deleteId = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun BenchmarkProfilePicker(profiles: List<PlatformV2>, selected: PlatformV2?, enabled: Boolean, onSelect: (PlatformV2) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Card(onClick = { expanded = true }, enabled = enabled && profiles.isNotEmpty(), shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val local = selected?.compatibleType == ClientType.LITERT_LM
                Icon(if (local) Icons.Default.Memory else Icons.Default.Cloud, null, tint = benchmarkTint(local), modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(selected?.name ?: "Select an AI profile", style = MaterialTheme.typography.titleMedium)
                    Text(selected?.model.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    if (selected != null) Text(if (local) "LOCAL · ${selected.accelerator?.uppercase() ?: "AUTO"}" else "REMOTE · ${selected.compatibleType.name}", style = MaterialTheme.typography.labelSmall, color = benchmarkTint(local))
                }
                Icon(Icons.Default.ExpandMore, "Choose AI profile")
            }
        }
        DropdownMenu(expanded, { expanded = false }) {
            profiles.forEach { item ->
                val local = item.compatibleType == ClientType.LITERT_LM
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(item.name)
                            Text(item.model, style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    leadingIcon = { Icon(if (local) Icons.Default.Memory else Icons.Default.Cloud, if (local) "Local" else "Remote", tint = benchmarkTint(local)) },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun BenchmarkScoreCard(rating: BenchmarkRating, local: Boolean, runs: Int, mode: BenchmarkMode) {
    val tint = benchmarkTint(local)
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = .1f))) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { (rating.score ?: 0) / 100f }, modifier = Modifier.fillMaxSize(), color = tint, strokeWidth = 7.dp, trackColor = tint.copy(alpha = .15f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(rating.score?.toString() ?: "—", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = tint)
                    Text("APP SCORE", style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BenchmarkTypeBadge(local)
                Text(rating.grade, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("${mode.label} · $runs runs · ${rating.measuredWeight}% coverage", style = MaterialTheme.typography.bodySmall)
                Text(
                    if (rating.score == null) {
                        "Run tests to build a rating"
                    } else if (runs < 3) {
                        "Early result · repeat for confidence"
                    } else {
                        "Based on ${rating.sampleCount} tests"
                    },
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun BenchmarkTile(title: String, value: String, subtitle: String, modifier: Modifier, tint: Color) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = tint)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EverydayPerformanceCard(row: ProfilePerformance, local: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BenchmarkTypeBadge(local)
            Text(row.name, style = MaterialTheme.typography.titleMedium)
            Text(row.metrics.model, style = MaterialTheme.typography.bodySmall)
            MetricLine("Median / p95 request", "${formatLatency(row.metrics.medianLatencyMs)} / ${formatLatency(row.metrics.p95LatencyMs)}")
            MetricLine("First text", formatLatency(row.metrics.medianFirstTokenMs))
            MetricLine("Reported output / request second", row.metrics.outputTokensPerSecond?.let { "%.1f tok/s".format(it) } ?: "Not reported")
            MetricLine("Completion success", percent(row.successPercent))
            Text("${row.metrics.requests} requests · tap for charts and history", style = MaterialTheme.typography.labelSmall, color = benchmarkTint(local))
        }
    }
}

@Composable
private fun BenchmarkHistoryCard(run: BenchmarkRun, current: Boolean, canDelete: Boolean, onDelete: () -> Unit) {
    var expanded by rememberSaveable(run.id) { mutableStateOf(false) }
    val rating = remember(run) { benchmarkRating(listOf(run)) }
    Card(onClick = { expanded = !expanded }, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BenchmarkTypeBadge(run.local)
                Text(
                    if (run.canceled) {
                        "Canceled"
                    } else if (run.stoppedReason != null) {
                        "Stopped"
                    } else if (!run.finished) {
                        "Interrupted"
                    } else {
                        rating.score?.let { "$it / 100" } ?: "Not rated"
                    },
                    color = benchmarkTint(run.local),
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text("${run.mode.label} · ${benchmarkDate(run.startedAt)}", style = MaterialTheme.typography.titleSmall)
            Text(run.model, style = MaterialTheme.typography.bodySmall)
            Text("${run.samples.count { it.outcome == BenchmarkOutcome.PASSED }} passed · ${run.samples.size} / ${benchmarkSuite(run.mode).size} tests · ${if (current) "current configuration" else "previous configuration"}", style = MaterialTheme.typography.labelSmall)
            run.stoppedReason?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Text(if (expanded) "Hide test details" else "Show test details", style = MaterialTheme.typography.labelLarge, color = benchmarkTint(run.local))
            if (expanded) {
                MetricLine("Device", run.device)
                if (run.local) {
                    MetricLine("Actual runtime", listOfNotNull(run.backend, run.accelerator).joinToString(" · ").ifBlank { "Not observed" })
                    MetricLine("Engine loaded before run", if (run.engineWasLoaded) "Yes; may be another model" else "No")
                }
                MetricLine("Peak sampled client memory", run.peakClientPssKb?.let { "${it / 1024} MiB PSS" } ?: "Not sampled")
                MetricLine("Thermal before / after", "${thermalLabel(run.thermalBefore)} / ${thermalLabel(run.thermalAfter)}")
                MetricLine("Battery before / after", "${run.batteryBefore?.let { "$it%" } ?: "—"} / ${run.batteryAfter?.let { "$it%" } ?: "—"}")
                Text("Memory is sampled after each test for this Android app, including remote runs; it is not server memory or a continuous peak. Thermal and battery readings are context, not efficiency scores.", style = MaterialTheme.typography.labelSmall)
                run.samples.forEach { sample ->
                    HorizontalDivider()
                    Text(sample.label, fontWeight = FontWeight.SemiBold)
                    Text(sample.outcome.name.lowercase().replace('_', ' '), color = if (sample.outcome in setOf(BenchmarkOutcome.ERROR, BenchmarkOutcome.FAILED, BenchmarkOutcome.TIMED_OUT)) MaterialTheme.colorScheme.error else benchmarkTint(run.local), style = MaterialTheme.typography.labelLarge)
                    MetricLine("Duration / first text", "${formatLatency(sample.durationMs)} / ${formatLatency(sample.firstTextMs)}")
                    MetricLine("Output tokens", "${if (sample.estimatedTokens) "≈ " else ""}${sample.outputTokens}")
                    MetricLine("Longest text pause", formatLatency(sample.longestGapMs))
                    if (sample.category == "tools") MetricLine("Successful fixture calls", "${sample.successfulToolCalls} / ${sample.toolCalls}")
                    sample.error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (sample.preview.isNotEmpty()) Text(sample.preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onDelete, enabled = canDelete) { Text("Delete run", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun BenchmarkPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun BenchmarkTypeBadge(local: Boolean) {
    val tint = benchmarkTint(local)
    Surface(color = tint.copy(alpha = .12f), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(if (local) Icons.Default.Memory else Icons.Default.Cloud, null, tint = tint, modifier = Modifier.size(16.dp))
            Text(if (local) "Local" else "Remote", color = tint, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun benchmarkTint(local: Boolean): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    return if (local) {
        if (dark) Color(0xFF72DACA) else Color(0xFF006B60)
    } else {
        if (dark) Color(0xFFD2BBFF) else Color(0xFF6942B7)
    }
}

private fun percent(value: Double?): String = value?.let { "%.0f%%".format(it) } ?: "—"
private fun benchmarkDate(time: Long): String = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))
private fun thermalLabel(status: Int?): String = when (status) {
    0 -> "Normal"
    1 -> "Light"
    2 -> "Moderate"
    3 -> "Severe"
    4 -> "Critical"
    5 -> "Emergency"
    6 -> "Shutdown"
    else -> "—"
}
