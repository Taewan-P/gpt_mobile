package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ModelDelegationSettings
import dev.chungjungsoo.gptmobile.data.model.excludesMemory
import dev.chungjungsoo.gptmobile.data.model.isPrivateDestination
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ModelDelegationSettingsPanel(viewModel: LocalToolsViewModel = hiltViewModel()) {
    val config by viewModel.delegation.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    ModelDelegationSettingsContent(config, profiles, busy, error, viewModel::update, viewModel::resetDelegationDefaults)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ModelDelegationSettingsContent(
    config: ModelDelegationSettings,
    profiles: List<PlatformV2>,
    busy: Boolean,
    error: String?,
    onChange: ((ModelDelegationSettings) -> ModelDelegationSettings) -> Unit,
    onReset: () -> Unit
) {
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    val eligible = profiles.filter { it.enabled && !it.excludesMemory() }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.padding(12.dp))
                }
                Text("A little help. A better answer.", style = MaterialTheme.typography.headlineSmall)
                Text("Your main AI writes the answer. A delegate helps with research, reading and longer tasks.", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Enable delegation", style = MaterialTheme.typography.titleMedium)
                        Text("Default for conversations", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = config.enabled,
                        onCheckedChange = { value -> onChange { it.copy(enabled = value) } },
                        enabled = !busy,
                        modifier = Modifier.semantics { contentDescription = "Enable delegation" }
                    )
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DelegateModelDropdown(
                    profiles = eligible,
                    selectedProfileUid = config.targetProfileUid,
                    enabled = !busy,
                    onSelected = { profile ->
                        onChange { it.copy(targetProfileUid = profile?.uid.orEmpty(), allowRemoteWorkers = it.allowRemoteWorkers || profile?.isPrivateDestination() == false) }
                    }
                )
                HorizontalDivider()
                SectionHeading("Work style", "Choose how much help you want. You can change this anytime.")
                val styles = listOf(20 to "Efficient", 50 to "Balanced", 80 to "Thorough")
                val selectedStyle = styles.minBy { abs(it.first - config.strategy) }.first
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    styles.forEach { (value, label) ->
                        FilterChip(
                            selected = selectedStyle == value,
                            onClick = { onChange { it.withStrategy(value) } },
                            label = { Text(label) },
                            enabled = !busy
                        )
                    }
                }
                Text(
                    when (selectedStyle) {
                        20 -> "Fewer searches and shorter summaries for everyday tasks."
                        80 -> "Broader research for questions that need more detail."
                        else -> "A comfortable mix of speed and research."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Pick a different helper in any conversation under Models.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showAdvanced = !showAdvanced }.padding(20.dp).testTag("delegation_advanced"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) { SectionHeading("Advanced", "Research, work sharing and limits") }
                Icon(if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (showAdvanced) "Collapse Advanced" else "Expand Advanced")
            }
            if (showAdvanced) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DelegationSlider(
                        "Efficiency: ${strategyLabel(config.strategy)}",
                        config.strategy,
                        0..100,
                        10,
                        !busy,
                        "Extremely efficient uses fewer searches, smaller briefs and fewer local calls. Higher accuracy spends more tokens and gathers broader evidence."
                    ) { value -> onChange { it.withStrategy(value) } }
                    DelegationSlider(
                        "Work split: ${ownershipLabel(config.processingOwnership)}",
                        config.processingOwnership,
                        0..100,
                        10,
                        !busy,
                        "Controls who performs the work. Shared keeps remote reasoning and local research active together when possible."
                    ) { value -> onChange { it.withProcessingOwnership(value) } }
                    Text("Local-first saves remote tokens. Shared balances both models. Remote-first lets the main model lead.", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    SectionHeading("Worker access", "Control where delegated information is allowed to go.")
                    LocalToolToggle("Prefer local or private models", config.localPlatformsOnly, !busy) { value -> onChange { it.copy(localPlatformsOnly = value) } }
                    Text("Online helpers can receive tasks when remote models are allowed or selected above.", style = MaterialTheme.typography.bodySmall)
                    LocalToolToggle("Allow remote AI profiles as workers", config.allowRemoteWorkers, !busy) { value -> onChange { it.copy(allowRemoteWorkers = value) } }
                    if (config.allowRemoteWorkers) {
                        Text("Remote-to-remote delegation is opt-in, budgeted by this router, and limited to a shallow worker chain to prevent loops.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DelegationSlider("Maximum worker delegation depth", config.maxDelegationDepth, 1..2, 1, !busy, "Depth 1 keeps one router-to-worker hop. Depth 2 permits a controlled worker handoff.") { value -> onChange { it.copy(maxDelegationDepth = value) } }
                    }
                    SectionHeading("Research assistant", "Let the helper gather evidence before the main model answers.")
                    LocalToolToggle("Search and read pages locally", config.researchEnabled, !busy) { value -> onChange { it.copy(researchEnabled = value) } }
                    LocalToolToggle("Prepare research before the remote model", config.automaticResearch, !busy) { value -> onChange { it.copy(automaticResearch = value) } }
                    LocalToolToggle("Process large tool results locally", config.compactToolResults, !busy) { value -> onChange { it.copy(compactToolResults = value) } }
                    Text("Uses the main profile's enabled tools and chat permissions. Search engines still receive search queries. Your chosen helper processes the pages and sends a summary to the main model.", style = MaterialTheme.typography.bodySmall)
                    DelegationSlider("Search queries", config.maxSearchQueries, 1..20, 1, !busy, "More queries broaden coverage and use more search requests.") { value -> onChange { it.copy(maxSearchQueries = value) } }
                    DelegationSlider("Results per search engine", config.searchResultsPerEngine, 1..28, 1, !busy) { value -> onChange { it.copy(searchResultsPerEngine = value) } }
                    DelegationSlider("Pages to read", config.maxPages, 0..32, 1, !busy, "Zero uses search snippets only.") { value -> onChange { it.copy(maxPages = value) } }
                    DelegationSlider("Crawl depth", config.crawlDepth, 0..8, 1, !busy, "Zero reads selected pages. Higher values follow links on the same host within the page limit.") { value -> onChange { it.copy(crawlDepth = value) } }
                    DelegationSlider("Parallel page requests", config.pageFetchConcurrency, 1..16, 1, !busy) { value -> onChange { it.copy(pageFetchConcurrency = value) } }
                    DelegationSlider("Page characters to process", config.maxPageCharacters, 1000..96000, 1000, !busy, "Long pages contribute relevant passages and are marked as excerpts.") { value -> onChange { it.copy(maxPageCharacters = value) } }
                    Text("Handoff and local workload", style = MaterialTheme.typography.titleMedium)
                    DelegationSlider("Remote brief token budget", config.handoffTokens, 128..8192, 128, !busy, "A byte-based estimate. Lower values reduce remote context; sources and limitations remain attached.") { value -> onChange { it.copy(handoffTokens = value) } }
                    DelegationSlider("Local input characters per step", config.maxInputCharacters, 1000..16000, 500, !busy) { value -> onChange { it.copy(maxInputCharacters = value) } }
                    DelegationSlider("Max input tokens per delegate", config.maxInputTokensPerDelegate, 1000..12000, 500, !busy, "Hard preflight cap including the worker prompt and retained tool schemas. Oversized tasks are chunked before inference.") { value -> onChange { it.copy(maxInputTokensPerDelegate = value) } }
                    DelegationSlider("Chunk size", config.chunkSizeTokens, 1000..12000, 500, !busy, "Large delegated payloads are split near this token size instead of truncating one giant request.") { value -> onChange { it.copy(chunkSizeTokens = value) } }
                    DelegationSlider("Retry chunk size", config.retryChunkSizeTokens, 500..6000, 250, !busy, "A failed chunk is retried only as smaller pieces; the original oversized payload is never replayed.") { value -> onChange { it.copy(retryChunkSizeTokens = value) } }
                    DelegationSlider("Local output tokens per step", config.maxOutputTokens, 64..2048, 64, !busy) { value -> onChange { it.copy(maxOutputTokens = value) } }
                    DelegationSlider("Local model calls per turn", config.maxLocalModelCalls, 1..24, 1, !busy, "Shared by planning, page summaries and tool-result processing.") { value -> onChange { it.copy(maxLocalModelCalls = value) } }
                    DelegationSlider("Maximum concurrent delegates", config.maxConcurrentDelegates, 1..4, 1, !busy, "One is safest for on-device inference. Increase only when the selected backend can run independent workers safely.") { value -> onChange { it.copy(maxConcurrentDelegates = value) } }
                    DelegationSlider("Research timeout in seconds", config.timeoutSeconds, 5..300, 5, !busy, "Legacy ceiling. Per-worker adaptive deadlines are also limited by the maximum delegate runtime below.") { value -> onChange { it.copy(timeoutSeconds = value) } }
                    DelegationSlider("Time-to-first-token timeout", config.timeToFirstTokenTimeoutSeconds, 5..90, 5, !busy, "Cancels a worker that never produces output or tool activity.") { value -> onChange { it.copy(timeToFirstTokenTimeoutSeconds = value) } }
                    DelegationSlider("Idle-token timeout", config.idleTokenTimeoutSeconds, 5..90, 5, !busy, "Cancels a worker that started but stops making output/tool progress.") { value -> onChange { it.copy(idleTokenTimeoutSeconds = value) } }
                    DelegationSlider("Maximum delegate runtime", config.maxDelegateRuntimeSeconds, 30..120, 5, !busy, "Absolute hard ceiling; adaptive small and medium jobs finish earlier.") { value -> onChange { it.copy(maxDelegateRuntimeSeconds = value) } }
                    DelegationSlider("Delegations per turn", config.maxCallsPerTurn, 1..16, 1, !busy) { value -> onChange { it.copy(maxCallsPerTurn = value) } }
                    DelegationSlider("Maximum wasted local tokens per turn", config.maxWastedLocalTokensPerTurn, 1000..64000, 1000, !busy, "Stops new workers after failed or canceled work consumes this estimated token budget.") { value -> onChange { it.copy(maxWastedLocalTokensPerTurn = value) } }
                    DelegationSlider("Stop when evidence sufficient", config.evidenceSufficiencyPercent, 50..100, 5, !busy, "Higher values gather more evidence before stopping; lower values reduce marginal delegate work.") { value -> onChange { it.copy(evidenceSufficiencyPercent = value) } }
                    DelegationSlider("Process tool results above characters", config.compactionThresholdCharacters, 256..48000, 256, !busy, "Small results pass through to avoid unnecessary local inference.") { value -> onChange { it.copy(compactionThresholdCharacters = value) } }
                    Text("These controls apply to delegation. The main model's output limit is unchanged. When the local budget runs out, the brief identifies omitted evidence.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onReset, enabled = !busy) {
                        Icon(Icons.Default.RestartAlt, null)
                        Text("Reset recommended defaults", Modifier.padding(start = 8.dp))
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
