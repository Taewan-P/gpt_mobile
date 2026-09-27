package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.data.accounting.ModelInvocation
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRunStatus
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEventStatus
import dev.chungjungsoo.gptmobile.data.localruntime.DiagnosticsTelemetryProvider
import dev.chungjungsoo.gptmobile.data.localruntime.QnnEnvironment
import dev.chungjungsoo.gptmobile.data.model.AppFeatureSettings
import dev.chungjungsoo.gptmobile.data.model.DebugMetric
import dev.chungjungsoo.gptmobile.data.security.DiagnosticRedactor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugDiagnosticsScreen(
    settingViewModel: SettingViewModelV2,
    onNavigationClick: () -> Unit,
    onStatisticsClick: () -> Unit,
    onBenchmarksClick: () -> Unit,
    viewModel: DebugDiagnosticsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val analytics by viewModel.analytics.collectAsStateWithLifecycle()
    val settings by settingViewModel.featureSettings.collectAsStateWithLifecycle()
    val runtime by settingViewModel.localRuntimeState.collectAsStateWithLifecycle()
    val debugEnabled by settingViewModel.debugMode.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var frozen by remember { mutableStateOf<DebugAnalyticsState?>(null) }
    val state = frozen ?: analytics
    val live = frozen == null && selectedTab == 0
    val now = rememberLiveClock(live)
    val hardware = rememberLiveHardware(runtime.backend?.displayName ?: "Idle", runtime.engineSpec?.accelerator ?: "None", live)
    var showMetrics by rememberSaveable { mutableStateOf(false) }
    val activeRequests = state.invocations.filter { it.status == "RUNNING" }
    val activeTools = state.recentToolEvents.distinctBy { it.eventId }.filter { it.status == ToolEventStatus.RUNNING || it.status == ToolEventStatus.PENDING }
    Scaffold(modifier = modifier.fillMaxSize(), topBar = {
        TopAppBar(title = { Text("Debug and Statistics") }, navigationIcon = {
            IconButton(onClick = onNavigationClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary) }
        }, actions = {
            if (selectedTab == 0) TextButton(onClick = { frozen = if (frozen == null) analytics else null }) { Text(if (frozen == null) "Pause" else "Resume") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Live", "Runs", "Logs").forEachIndexed { index, title -> FilterChip(selectedTab == index, { selectedTab = index }, label = { Text(title) }) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onStatisticsClick, Modifier.weight(1f)) {
                        Icon(Icons.Default.BarChart, null)
                        Text("Usage", Modifier.padding(start = 8.dp))
                    }
                    FilledTonalButton(onBenchmarksClick, Modifier.weight(1f)) {
                        Icon(Icons.Default.Speed, null)
                        Text("Benchmarks", Modifier.padding(start = 8.dp))
                    }
                }
            }
            when (selectedTab) {
                0 -> {
                    item {
                        DebugPanel(if (frozen == null) "Live session" else "Paused snapshot") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Debug in conversations", fontWeight = FontWeight.SemiBold)
                                    Text("Open response activity to inspect its diagnostics.", style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(debugEnabled, settingViewModel::updateDebugMode)
                            }
                            MetricLine("Active model requests / tools", "${activeRequests.size} / ${activeTools.size}")
                            MetricLine("Recent runs / failed", "${state.recentRuns.size} / ${state.failedRuns}")
                            Text("Live values update once per second. Hardware samples every two seconds while this screen is visible.", style = MaterialTheme.typography.labelSmall)
                            TextButton(onClick = { showMetrics = !showMetrics }) { Text(if (showMetrics) "Hide display settings" else "Display settings") }
                            if (showMetrics) {
                                DebugMetric.entries.forEach { metric ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(metric.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        Switch(settings.shows(metric), { settingViewModel.updateDebugMetric(metric, it) })
                                    }
                                }
                            }
                        }
                    }
                    if (activeRequests.isEmpty()) item { Text("No model request is running. Start a conversation to see live timing and token observations.", style = MaterialTheme.typography.bodyMedium) }
                    items(activeRequests, key = { "live-${it.id}" }) { request ->
                        DebugPanel(state.profileNames[request.profileUid] ?: request.model) { RequestDiagnostic(request, now) }
                    }
                    items(activeTools, key = { "tool-${it.eventId}" }) { tool ->
                        DebugPanel(tool.modelToolName.ifBlank { tool.toolName }) {
                            MetricLine("Status", tool.status.lowercase())
                            MetricLine("Elapsed", tool.startedAt?.let { formatLatency((now - it * 1000).coerceAtLeast(0)) } ?: "Waiting")
                            MetricLine("Connection", tool.connectionNameSnapshot ?: "Built in")
                            Text("Run ${tool.runId} · call ${tool.callId}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    item { DebugPanel("This device") { hardware?.let { HardwareDiagnostic(it) } ?: Text("Reading device state…") } }
                }
                1 -> {
                    item { Text("Recent runs · up to 250", style = MaterialTheme.typography.titleMedium) }
                    items(state.recentRuns.distinctBy { it.runId }, key = { it.runId }) { run ->
                        var expanded by rememberSaveable(run.runId) { mutableStateOf(false) }
                        val requests = state.invocations.filter { it.parentRunId == run.runId }
                        val tools = state.recentToolEvents.filter { it.runId == run.runId }.distinctBy { it.eventId }
                        Card(onClick = { expanded = !expanded }, shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(state.profileNames[run.profileUid] ?: run.modelSnapshot, color = modelChartColor(run.profileUid), fontWeight = FontWeight.SemiBold)
                                Text("${run.modelSnapshot} · ${run.status.lowercase()}", style = MaterialTheme.typography.bodyMedium, color = if (run.status in setOf(AgentRunStatus.FAILED, AgentRunStatus.INTERRUPTED)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                                Text("${requests.size} recent requests · ${tools.size} retained tool calls", style = MaterialTheme.typography.labelSmall)
                                if (expanded) {
                                    Text("Run ${run.runId}\nProvider ${run.providerSnapshot}\nChat ${run.chatId}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                                    run.terminalError?.let { Text(DiagnosticRedactor.redact(it), color = MaterialTheme.colorScheme.error) }
                                    requests.forEach { RequestDiagnostic(it, now) }
                                    tools.forEach { tool ->
                                        MetricLine(tool.modelToolName.ifBlank { tool.toolName }, tool.status.lowercase())
                                        tool.error?.let { Text(DiagnosticRedactor.redact(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                                    }
                                    if (requests.isEmpty()) Text("No individual request record in the latest 100. Full retained performance is available in Usage.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    if (state.recentRuns.isEmpty()) item { Text("No recorded runs yet.") }
                }
                else -> item { AppLogPanel() }
            }
        }
    }
}

@Composable
internal fun RequestDiagnostic(request: ModelInvocation, now: Long, settings: AppFeatureSettings = AppFeatureSettings()) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("${request.kind} · ${request.status.lowercase()}", color = if (request.status in setOf("FAILED", "INTERRUPTED")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        if (settings.debugShowRuntime) MetricLine("Provider / model", "${request.provider} / ${request.model}")
        if (settings.debugShowNetwork || settings.debugShowRuntime) MetricLine("Request elapsed", formatLatency(if (request.status == "RUNNING") (now - request.startedAt).coerceAtLeast(0) else request.durationMs))
        if (settings.debugShowTotalTokens) MetricLine(if (request.estimated) "Input / output estimate" else "Reported input / output", "${request.inputTokens} / ${request.outputTokens}")
        if (settings.debugShowTimeToFirstToken) MetricLine("First text token", formatLatency(request.firstTokenMs))
        if (settings.debugShowTokenSpeed) {
            if (request.status == "RUNNING") {
                val speed = request.durationMs.takeIf { it > 0 }?.let { request.outputTokens * 1000.0 / it }
                MetricLine(if (request.estimated) "Live estimated output / second" else "Live reported output / second", speed?.let { "%.1f tok/s".format(it) } ?: "Waiting")
            } else {
                MetricLine("Reported output / second", request.reportedThroughput()?.let { "%.1f tok/s".format(it) } ?: "Not measured")
            }
        }
        Text("Request ${request.id}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
    }
}

@Composable
internal fun HardwareDiagnostic(snapshot: DiagnosticsTelemetryProvider.DiagnosticsSnapshot, showNetwork: Boolean = true) {
    MetricLine("Local runtime", "${snapshot.backendName} · ${snapshot.accelerator}")
    MetricLine("Processor", snapshot.socModel)
    MetricLine("Available / total device RAM", "${snapshot.availableRamMb} MB / ${snapshot.totalRamGb} GB")
    MetricLine("App PSS / Java heap", "${snapshot.processMemoryMb} / ${snapshot.javaHeapMb} MB")
    MetricLine("Thermal state", snapshot.thermalStatus)
    MetricLine("Battery", "${snapshot.batteryPct.takeIf { it >= 0 }?.let { "$it%" } ?: "Unknown"}${if (snapshot.isCharging) " · charging" else ""}")
    if (showNetwork) MetricLine("Network transport", snapshot.network)
    if (snapshot.qnnReady && QnnEnvironment.isQualcommPlatform()) MetricLine("QNN prerequisites", "Available · execution unverified")
}

@Composable
private fun DebugPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

internal fun AppFeatureSettings.shows(metric: DebugMetric): Boolean = when (metric) {
    DebugMetric.TOOL_CALLS -> debugShowToolCalls
    DebugMetric.TOTAL_TOKENS -> debugShowTotalTokens
    DebugMetric.TOKEN_SPEED -> debugShowTokenSpeed
    DebugMetric.TIME_TO_FIRST_TOKEN -> debugShowTimeToFirstToken
    DebugMetric.RUNTIME -> debugShowRuntime
    DebugMetric.HARDWARE -> debugShowHardware
    DebugMetric.NETWORK -> debugShowNetwork
}
