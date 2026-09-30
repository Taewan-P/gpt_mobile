package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.data.model.excludesMemory
import dev.chungjungsoo.gptmobile.data.model.isPrivateDestination
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelDelegationSettingsPanel(viewModel: LocalToolsViewModel = hiltViewModel()) {
    val config by viewModel.delegation.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var showAdvanced by remember { mutableStateOf(false) }
    var helperMenuOpen by remember { mutableStateOf(false) }
    val eligible = profiles.filter { it.enabled && !it.excludesMemory() && (it.isPrivateDestination() || config.allowRemoteWorkers) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                        Icon(Icons.Default.AutoAwesome, "", Modifier.padding(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Model delegation", style = MaterialTheme.typography.headlineSmall)
                        Text(if (config.enabled) "${100 - config.processingOwnership} delegation amount · ${config.strategy} research depth" else "Turn on to give tasks to a helper", style = MaterialTheme.typography.bodyMedium)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            if (config.enabled) "Enabled" else "Disabled",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        androidx.compose.material3.Switch(
                            checked = config.enabled,
                            onCheckedChange = { value -> viewModel.update { it.copy(enabled = value) } },
                            enabled = !busy,
                            modifier = Modifier.semantics { contentDescription = "Enable delegation" }
                        )
                    }
                }
                Text("A helper can research, read pages, and compress tool results so the main model receives a focused answer with evidence.", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(Icons.Default.Memory, "Up to ${config.effectiveLocalModelCalls()} helper calls")
                    StatusPill(Icons.Default.Bolt, "Compact evidence brief")
                    if (config.allowRemoteWorkers) StatusPill(Icons.Default.Cloud, "Remote workers on")
                }
            }
        }
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading("Helper model", "Choose an AI profile to handle delegated work.")
                LocalToolToggle("Allow cloud helpers", config.allowRemoteWorkers, !busy) { value -> viewModel.update { it.copy(allowRemoteWorkers = value) } }
                Text("Cloud helpers receive delegated content and use their provider's tokens. Leave off to use only this device or a private server.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val selected = eligible.firstOrNull { it.uid == config.targetProfileUid }
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { helperMenuOpen = true },
                        enabled = !busy && eligible.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                            Text(selected?.name ?: "Choose helper model")
                            selected?.let { Text(it.model, style = MaterialTheme.typography.bodySmall) }
                        }
                        Icon(Icons.Default.ExpandMore, null)
                    }
                    DropdownMenu(expanded = helperMenuOpen, onDismissRequest = { helperMenuOpen = false }) {
                        eligible.forEach { profile ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(profile.name)
                                        Text("${if (profile.isPrivateDestination()) "Private" else "Cloud"} · ${profile.model}", style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                onClick = {
                                    helperMenuOpen = false
                                    viewModel.update { it.copy(targetProfileUid = profile.uid) }
                                }
                            )
                        }
                    }
                }
                if (selected == null) {
                    Text("Select an enabled profile with a working model and connection in AI profiles. If it becomes unavailable, another eligible helper may be used.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading("Workload", "Adjust how much the helper does and how widely it researches.")
                DelegationSlider(
                    "Delegation amount",
                    100 - config.processingOwnership,
                    0..100,
                    5,
                    !busy,
                    "Less → More. Recommended: 80. Higher values favor the helper for research and tool results; the main model writes the final answer. This is a routing preference, not a guaranteed token percentage."
                ) { value -> viewModel.update { it.withDelegationAmount(value) } }
                DelegationSlider(
                    "Research depth",
                    config.strategy,
                    0..100,
                    5,
                    !busy,
                    "Focused → Broad. Recommended: 70. More coverage reads more sources while keeping each helper request bounded."
                ) { value -> viewModel.update { it.withStrategy(value) } }
                Text("Up to ${config.effectiveLocalModelCalls()} helper calls · ${config.maxSearchQueries} searches · ${config.maxPages} pages per research pass", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeading("Automatic assistance", "Recommended on for substantial delegation.")
                LocalToolToggle("Delegate web research", config.researchEnabled, !busy) { value -> viewModel.update { it.copy(researchEnabled = value) } }
                LocalToolToggle("Research before answering", config.automaticResearch, !busy && config.researchEnabled) { value -> viewModel.update { it.copy(automaticResearch = value) } }
                LocalToolToggle("Compress large tool results", config.compactToolResults, !busy) { value -> viewModel.update { it.copy(compactToolResults = value) } }
                Text("Uses the conversation's enabled tools and permissions. Research sends sources and a compact brief to the main model.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { viewModel.resetDelegationDefaults() }, enabled = !busy) {
                    Icon(Icons.Default.RestartAlt, null)
                    Text("Restore recommended defaults", Modifier.padding(start = 8.dp))
                }
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { SectionHeading("Advanced controls", "Fine-tune budgets, timeouts, and research breadth.") }
                    IconButton(onClick = { showAdvanced = !showAdvanced }) { Icon(if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "Toggle advanced controls") }
                }
                if (!showAdvanced) {
                    Text("${config.maxOutputTokens} output tokens per helper call · ${config.maxConcurrentDelegates} concurrent workers. Expand to customize limits.", style = MaterialTheme.typography.bodySmall)
                }
                if (showAdvanced) {
                    DelegationSlider("Maximum worker delegation depth", config.maxDelegationDepth, 1..2, 1, !busy) { value -> viewModel.update { it.copy(maxDelegationDepth = value) } }
                    Text("Research limits", style = MaterialTheme.typography.titleMedium)
                    DelegationSlider("Search queries", config.maxSearchQueries, 1..20, 1, !busy) { value -> viewModel.update { it.copy(maxSearchQueries = value) } }
                    DelegationSlider("Results per search engine", config.searchResultsPerEngine, 1..10, 1, !busy) { value -> viewModel.update { it.copy(searchResultsPerEngine = value) } }
                    DelegationSlider("Pages to read", config.maxPages, 0..32, 1, !busy) { value -> viewModel.update { it.copy(maxPages = value) } }
                    DelegationSlider("Crawl depth", config.crawlDepth, 0..8, 1, !busy) { value -> viewModel.update { it.copy(crawlDepth = value) } }
                    DelegationSlider("Parallel page requests", config.pageFetchConcurrency, 1..16, 1, !busy) { value -> viewModel.update { it.copy(pageFetchConcurrency = value) } }
                    DelegationSlider("Page characters to process", config.maxPageCharacters, 1000..96000, 1000, !busy) { value -> viewModel.update { it.copy(maxPageCharacters = value) } }
                    Text("Handoff and helper workload", style = MaterialTheme.typography.titleMedium)
                    DelegationSlider("Remote brief token budget", config.handoffTokens, 128..8192, 128, !busy, "A byte-based estimate. Lower values reduce remote context; sources and limitations remain attached.") { value -> viewModel.update { it.copy(handoffTokens = value) } }
                    DelegationSlider("Helper input characters per step", config.maxInputCharacters, 1000..64000, 500, !busy) { value -> viewModel.update { it.copy(maxInputCharacters = value) } }
                    DelegationSlider("Max input tokens per delegate", config.maxInputTokensPerDelegate, 1000..12000, 500, !busy, "Hard preflight cap including the worker prompt and retained tool schemas. Oversized tasks are chunked before inference.") { value -> viewModel.update { it.copy(maxInputTokensPerDelegate = value) } }
                    DelegationSlider("Chunk size", config.chunkSizeTokens, 1000..12000, 500, !busy, "Large delegated payloads are split near this token size instead of truncating one giant request.") { value -> viewModel.update { it.copy(chunkSizeTokens = value) } }
                    DelegationSlider("Retry chunk size", config.retryChunkSizeTokens, 500..6000, 250, !busy, "A failed chunk is retried only as smaller pieces; the original oversized payload is never replayed.") { value -> viewModel.update { it.copy(retryChunkSizeTokens = value) } }
                    DelegationSlider("Helper output tokens per step", config.maxOutputTokens, 64..4096, 64, !busy) { value -> viewModel.update { it.copy(maxOutputTokens = value) } }
                    DelegationSlider("Helper model calls per turn", config.maxLocalModelCalls, 1..48, 1, !busy, "Shared by planning, page summaries and tool-result processing.") { value -> viewModel.update { it.copy(maxLocalModelCalls = value) } }
                    DelegationSlider("Maximum concurrent delegates", config.maxConcurrentDelegates, 1..4, 1, !busy, "One is safest for on-device inference. Increase only when the selected backend can run independent workers safely.") { value -> viewModel.update { it.copy(maxConcurrentDelegates = value) } }
                    DelegationSlider("Research timeout in seconds", config.timeoutSeconds, 5..300, 5, !busy, "Legacy ceiling. Per-worker adaptive deadlines are also limited by the maximum delegate runtime below.") { value -> viewModel.update { it.copy(timeoutSeconds = value) } }
                    DelegationSlider("Time-to-first-token timeout", config.timeToFirstTokenTimeoutSeconds, 5..90, 5, !busy, "Cancels a worker that never produces output or tool activity.") { value -> viewModel.update { it.copy(timeToFirstTokenTimeoutSeconds = value) } }
                    DelegationSlider("Idle-token timeout", config.idleTokenTimeoutSeconds, 5..90, 5, !busy, "Cancels a worker that started but stops making output/tool progress.") { value -> viewModel.update { it.copy(idleTokenTimeoutSeconds = value) } }
                    DelegationSlider("Maximum delegate runtime", config.maxDelegateRuntimeSeconds, 30..120, 5, !busy, "Absolute hard ceiling; adaptive small and medium jobs finish earlier.") { value -> viewModel.update { it.copy(maxDelegateRuntimeSeconds = value) } }
                    DelegationSlider("Delegations per turn", config.maxCallsPerTurn, 1..16, 1, !busy) { value -> viewModel.update { it.copy(maxCallsPerTurn = value) } }
                    DelegationSlider("Maximum wasted local tokens per turn", config.maxWastedLocalTokensPerTurn, 1000..64000, 1000, !busy, "Stops new workers after failed or canceled work consumes this estimated token budget.") { value -> viewModel.update { it.copy(maxWastedLocalTokensPerTurn = value) } }
                    DelegationSlider("Stop when evidence sufficient", config.evidenceSufficiencyPercent, 50..100, 5, !busy, "Higher values gather more evidence before stopping; lower values reduce marginal delegate work.") { value -> viewModel.update { it.copy(evidenceSufficiencyPercent = value) } }
                    DelegationSlider("Process tool results above characters", config.compactionThresholdCharacters, 256..48000, 256, !busy, "Small results pass through to avoid unnecessary local inference.") { value -> viewModel.update { it.copy(compactionThresholdCharacters = value) } }
                    Text("These controls apply to delegation. The main model's output limit is unchanged. When the local budget runs out, the brief identifies omitted evidence.", style = MaterialTheme.typography.bodySmall)
                    DelegationSlider("Retry limit", config.localRetryLimit, 0..1, 1, !busy) { value -> viewModel.update { it.copy(localRetryLimit = value) } }
                    DelegationSlider("Pause threshold for low battery", config.lowBatteryThresholdPercent, 0..50, 1, !busy) { value -> viewModel.update { it.copy(lowBatteryThresholdPercent = value) } }
                    Text("Requests for missing or unauthorized models stop immediately. Final answers use the main profile's output limit.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        FilterChip(selected = false, onClick = { viewModel.resetDelegationDefaults() }, label = { Text("Reset recommended defaults") }, leadingIcon = { Icon(Icons.Default.RestartAlt, null) }, enabled = !busy)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, Modifier.height(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun DelegationSlider(label: String, value: Int, range: IntRange, step: Int, enabled: Boolean, hint: String? = null, save: (Int) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Text("$label: ${draft.roundToInt()}", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = draft,
            onValueChange = { draft = ((it / step).roundToInt() * step).coerceIn(range).toFloat() },
            onValueChangeFinished = { if (draft.roundToInt() != value) save(draft.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = label }
        )
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
