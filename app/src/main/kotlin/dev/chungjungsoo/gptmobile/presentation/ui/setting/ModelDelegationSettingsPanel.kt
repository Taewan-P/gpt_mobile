package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
    val eligible = profiles.filter { it.enabled && !it.excludesMemory() && (!config.localPlatformsOnly || it.isPrivateDestination()) }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LocalToolToggle("Model delegation", config.enabled, !busy) { value -> viewModel.update { it.copy(enabled = value) } }
                Text("Let a local model plan searches, read pages and process tool results. Your remote model receives a compact brief with sources and any missing evidence.", style = MaterialTheme.typography.bodyMedium)
                DelegationSlider(
                    "Token efficiency strategy: ${strategyLabel(config.strategy)}",
                    config.strategy,
                    0..100,
                    10,
                    !busy,
                    "Extremely efficient uses fewer searches, smaller briefs and fewer local calls. Higher accuracy spends more tokens and gathers broader evidence."
                ) { value -> viewModel.update { it.withStrategy(value) } }
                LocalToolToggle("Only private destinations", config.localPlatformsOnly, !busy) { value -> viewModel.update { it.copy(localPlatformsOnly = value) } }
                Text("Local research and result processing require an on-device model or private server. Other destinations support text delegation only.", style = MaterialTheme.typography.bodySmall)
                Text("Helper profile", style = MaterialTheme.typography.titleSmall)
                if (eligible.none { it.uid == config.targetProfileUid }) Text("Select an enabled helper profile. Download a model and create its profile from the Library tab, or add your local server in AI profiles.", color = MaterialTheme.colorScheme.primary)
                eligible.forEach { profile ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Local research", style = MaterialTheme.typography.titleMedium)
                LocalToolToggle("Search and read pages locally", config.researchEnabled, !busy) { value -> viewModel.update { it.copy(researchEnabled = value) } }
                LocalToolToggle("Prepare research before the remote model", config.automaticResearch, !busy) { value -> viewModel.update { it.copy(automaticResearch = value) } }
                LocalToolToggle("Process large tool results locally", config.compactToolResults, !busy) { value -> viewModel.update { it.copy(compactToolResults = value) } }
                Text("Uses the main profile's enabled tools and chat permissions. Search engines still receive search queries. Page text stays with the local helper; only the brief goes to the remote model.", style = MaterialTheme.typography.bodySmall)
                DelegationSlider("Search queries", config.maxSearchQueries, 1..6, 1, !busy, "More queries broaden coverage and use more search requests.") { value -> viewModel.update { it.copy(maxSearchQueries = value) } }
                DelegationSlider("Results per search engine", config.searchResultsPerEngine, 1..10, 1, !busy) { value -> viewModel.update { it.copy(searchResultsPerEngine = value) } }
                DelegationSlider("Pages to read", config.maxPages, 0..12, 1, !busy, "Zero uses search snippets only.") { value -> viewModel.update { it.copy(maxPages = value) } }
                DelegationSlider("Crawl depth", config.crawlDepth, 0..2, 1, !busy, "Zero reads selected pages. Higher values follow links on the same host within the page limit.") { value -> viewModel.update { it.copy(crawlDepth = value) } }
                DelegationSlider("Parallel page requests", config.pageFetchConcurrency, 1..4, 1, !busy) { value -> viewModel.update { it.copy(pageFetchConcurrency = value) } }
                DelegationSlider("Page characters to process", config.maxPageCharacters, 1000..48000, 1000, !busy, "Long pages contribute relevant passages and are marked as excerpts.") { value -> viewModel.update { it.copy(maxPageCharacters = value) } }
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Handoff and local workload", style = MaterialTheme.typography.titleMedium)
                DelegationSlider("Remote brief token budget", config.handoffTokens, 128..4096, 128, !busy, "A byte-based estimate. Lower values reduce remote context; sources and limitations remain attached.") { value -> viewModel.update { it.copy(handoffTokens = value) } }
                DelegationSlider("Local input characters per step", config.maxInputCharacters, 1000..64000, 1000, !busy) { value -> viewModel.update { it.copy(maxInputCharacters = value) } }
                DelegationSlider("Local output tokens per step", config.maxOutputTokens, 64..4096, 64, !busy) { value -> viewModel.update { it.copy(maxOutputTokens = value) } }
                DelegationSlider("Local model calls per turn", config.maxLocalModelCalls, 1..24, 1, !busy, "Shared by planning, page summaries and tool-result processing.") { value -> viewModel.update { it.copy(maxLocalModelCalls = value) } }
                DelegationSlider("Research timeout in seconds", config.timeoutSeconds, 5..300, 5, !busy) { value -> viewModel.update { it.copy(timeoutSeconds = value) } }
                DelegationSlider("Delegations per turn", config.maxCallsPerTurn, 1..8, 1, !busy) { value -> viewModel.update { it.copy(maxCallsPerTurn = value) } }
                DelegationSlider("Process tool results above characters", config.compactionThresholdCharacters, 500..24000, 500, !busy, "Small results pass through to avoid unnecessary local inference.") { value -> viewModel.update { it.copy(compactionThresholdCharacters = value) } }
                Text("These controls apply to delegation. The main model's output limit is unchanged. When the local budget runs out, the brief identifies omitted evidence.", style = MaterialTheme.typography.bodySmall)
            }
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
