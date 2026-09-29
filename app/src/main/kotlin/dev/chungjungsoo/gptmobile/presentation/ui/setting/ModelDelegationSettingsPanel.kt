package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

@Composable
fun ModelDelegationSettingsPanel(viewModel: LocalToolsViewModel = hiltViewModel()) {
    val config by viewModel.delegation.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var showAdvanced by remember { mutableStateOf(false) }
    val eligible = profiles.filter { it.enabled && !it.excludesMemory() && (!config.localPlatformsOnly || it.isPrivateDestination() || config.allowRemoteWorkers) }
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
                        Text(if (config.enabled) "Ready to share bounded work" else "Turn on to give tasks to a helper", style = MaterialTheme.typography.bodyMedium)
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(Icons.Default.Memory, "${config.maxLocalModelCalls} local calls")
                    StatusPill(Icons.Default.Bolt, "${config.handoffTokens} brief tokens")
                    if (config.allowRemoteWorkers) StatusPill(Icons.Default.Cloud, "Remote workers on")
                }
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeading("Delegation style", "Choose the balance that fits this device and task.")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(20 to "Efficient", 50 to "Balanced", 80 to "Thorough").forEach { (value, label) ->
                        FilterChip(
                            selected = config.strategy in (value - 15)..(value + 15),
                            onClick = { viewModel.update { it.withStrategy(value) } },
                            label = { Text(label) },
                            leadingIcon = if (value == 20) ({ Icon(Icons.Default.Bolt, null) }) else null,
                            modifier = Modifier.weight(1f),
                            enabled = !busy,
                            colors = FilterChipDefaults.filterChipColors()
                        )
                    }
                }
                DelegationSlider(
                    "Efficiency: ${strategyLabel(config.strategy)}",
                    config.strategy,
                    0..100,
                    10,
                    !busy,
                    "Extremely efficient uses fewer searches, smaller briefs and fewer local calls. Higher accuracy spends more tokens and gathers broader evidence."
                ) { value -> viewModel.update { it.withStrategy(value) } }
                DelegationSlider(
                    "Work split: ${ownershipLabel(config.processingOwnership)}",
                    config.processingOwnership,
                    0..100,
                    10,
                    !busy,
                    "Controls who performs the work. Shared keeps remote reasoning and local research active together when possible."
                ) { value -> viewModel.update { it.withProcessingOwnership(value) } }
                Text("Local-first saves remote tokens. Shared balances both models. Remote-first lets the main model lead.", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                SectionHeading("Worker access", "Control where delegated information is allowed to go.")
                LocalToolToggle("Only private destinations", config.localPlatformsOnly, !busy) { value -> viewModel.update { it.copy(localPlatformsOnly = value) } }
                Text("Local research and result processing require an on-device model or private server. Remote workers can handle analysis and verification when explicitly enabled.", style = MaterialTheme.typography.bodySmall)
                LocalToolToggle("Allow remote AI profiles as workers", config.allowRemoteWorkers, !busy) { value -> viewModel.update { it.copy(allowRemoteWorkers = value) } }
                if (config.allowRemoteWorkers) {
                    Text("Remote-to-remote delegation is opt-in, budgeted by this router, and limited to a shallow worker chain to prevent loops.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DelegationSlider("Maximum worker delegation depth", config.maxDelegationDepth, 1..2, 1, !busy, "Depth 1 keeps one router-to-worker hop. Depth 2 permits a controlled worker handoff.") { value -> viewModel.update { it.copy(maxDelegationDepth = value) } }
                }
                Text("Choose helper", style = MaterialTheme.typography.titleMedium)
                if (eligible.none { it.uid == config.targetProfileUid }) Text("Select an enabled helper profile. Download a model and create its profile from the Library tab, or add your local server in AI profiles.", color = MaterialTheme.colorScheme.primary)
                eligible.forEach { profile ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (config.targetProfileUid == profile.uid) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = config.targetProfileUid == profile.uid,
                                enabled = !busy,
                                onClick = { viewModel.update { it.copy(targetProfileUid = profile.uid) } },
                                modifier = Modifier.semantics { contentDescription = "Delegate to ${profile.name}" }
                            )
                            Column {
                                Text(profile.name)
                                Text(profile.model, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeading("Research assistant", "Let the helper gather evidence before the main model answers.")
                LocalToolToggle("Search and read pages locally", config.researchEnabled, !busy) { value -> viewModel.update { it.copy(researchEnabled = value) } }
                LocalToolToggle("Prepare research before the remote model", config.automaticResearch, !busy) { value -> viewModel.update { it.copy(automaticResearch = value) } }
                LocalToolToggle("Process large tool results locally", config.compactToolResults, !busy) { value -> viewModel.update { it.copy(compactToolResults = value) } }
                Text("Uses the main profile's enabled tools and chat permissions. Search engines still receive search queries. Page text stays with the local helper; only the brief goes to the remote model.", style = MaterialTheme.typography.bodySmall)
                DelegationSlider("Search queries", config.maxSearchQueries, 1..20, 1, !busy, "More queries broaden coverage and use more search requests.") { value -> viewModel.update { it.copy(maxSearchQueries = value) } }
                DelegationSlider("Results per search engine", config.searchResultsPerEngine, 1..28, 1, !busy) { value -> viewModel.update { it.copy(searchResultsPerEngine = value) } }
                DelegationSlider("Pages to read", config.maxPages, 0..32, 1, !busy, "Zero uses search snippets only.") { value -> viewModel.update { it.copy(maxPages = value) } }
                DelegationSlider("Crawl depth", config.crawlDepth, 0..8, 1, !busy, "Zero reads selected pages. Higher values follow links on the same host within the page limit.") { value -> viewModel.update { it.copy(crawlDepth = value) } }
                DelegationSlider("Parallel page requests", config.pageFetchConcurrency, 1..16, 1, !busy) { value -> viewModel.update { it.copy(pageFetchConcurrency = value) } }
                DelegationSlider("Page characters to process", config.maxPageCharacters, 1000..96000, 1000, !busy, "Long pages contribute relevant passages and are marked as excerpts.") { value -> viewModel.update { it.copy(maxPageCharacters = value) } }
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { SectionHeading("Advanced controls", "Fine-tune budgets, timeouts, and research breadth.") }
                    IconButton(onClick = { showAdvanced = !showAdvanced }) { Icon(if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "Toggle advanced controls") }
                }
                if (!showAdvanced) {
                    Text("Using the recommended bounded defaults. Expand for detailed controls.", style = MaterialTheme.typography.bodySmall)
                }
                if (showAdvanced) {
                    Text("Handoff and local workload", style = MaterialTheme.typography.titleMedium)
                    DelegationSlider("Remote brief token budget", config.handoffTokens, 128..8192, 128, !busy, "A byte-based estimate. Lower values reduce remote context; sources and limitations remain attached.") { value -> viewModel.update { it.copy(handoffTokens = value) } }
                    DelegationSlider("Local input characters per step", config.maxInputCharacters, 1000..16000, 500, !busy) { value -> viewModel.update { it.copy(maxInputCharacters = value) } }
                    DelegationSlider("Max input tokens per delegate", config.maxInputTokensPerDelegate, 1000..12000, 500, !busy, "Hard preflight cap including the worker prompt and retained tool schemas. Oversized tasks are chunked before inference.") { value -> viewModel.update { it.copy(maxInputTokensPerDelegate = value) } }
                    DelegationSlider("Chunk size", config.chunkSizeTokens, 1000..12000, 500, !busy, "Large delegated payloads are split near this token size instead of truncating one giant request.") { value -> viewModel.update { it.copy(chunkSizeTokens = value) } }
                    DelegationSlider("Retry chunk size", config.retryChunkSizeTokens, 500..6000, 250, !busy, "A failed chunk is retried only as smaller pieces; the original oversized payload is never replayed.") { value -> viewModel.update { it.copy(retryChunkSizeTokens = value) } }
                    DelegationSlider("Local output tokens per step", config.maxOutputTokens, 64..2048, 64, !busy) { value -> viewModel.update { it.copy(maxOutputTokens = value) } }
                    DelegationSlider("Local model calls per turn", config.maxLocalModelCalls, 1..24, 1, !busy, "Shared by planning, page summaries and tool-result processing.") { value -> viewModel.update { it.copy(maxLocalModelCalls = value) } }
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
                    Text("Safety policy: local retries stop after one failed attempt, live-tool failures are reported explicitly, one tool slot is reserved for final synthesis, and aggressive local research pauses below 20% battery.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun strategyLabel(value: Int): String = when {
    value < 25 -> "Extremely efficient"
    value < 45 -> "Efficient"
    value < 65 -> "Balanced"
    value < 85 -> "Accurate"
    else -> "Maximum accuracy"
}

private fun ownershipLabel(value: Int): String = when {
    value < 25 -> "Local-first"
    value < 45 -> "Local-balanced"
    value < 65 -> "Shared"
    value < 85 -> "Remote-balanced"
    else -> "Remote-first"
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
