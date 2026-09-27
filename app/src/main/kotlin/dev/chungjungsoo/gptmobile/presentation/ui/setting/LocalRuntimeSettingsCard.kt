package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.data.model.LocalRuntimeBackend
import dev.chungjungsoo.gptmobile.data.model.SpeculativeDecodingMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LocalRuntimeSettingsCard(viewModel: LocalRuntimeSettingsViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val backend by viewModel.backend.collectAsStateWithLifecycle()
    val active by viewModel.active.collectAsStateWithLifecycle()
    val hardware by viewModel.hardware.collectAsStateWithLifecycle()
    val qnnAvailable by viewModel.qnnAvailable.collectAsStateWithLifecycle()
    val npuStatus by viewModel.npuStatus.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    var threads by remember(settings.localCpuThreads) { mutableStateOf(settings.localCpuThreads.toFloat()) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Engine & performance", style = MaterialTheme.typography.titleLarge)
            Text("${viewModel.soc} · ${viewModel.cores} CPU cores · ${viewModel.ramGb} GB RAM", style = MaterialTheme.typography.bodyMedium)
            Text(active.engineSpec?.let { "Running: ${active.backend?.displayName} / ${it.accelerator.uppercase()} · ${it.maxTokens} context" } ?: "Engine idle", color = MaterialTheme.colorScheme.primary)
            active.fallbackReason?.let { Text("Fallback: $it", style = MaterialTheme.typography.bodySmall) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LocalRuntimeBackend.entries.filter { it != LocalRuntimeBackend.QUALCOMM_QNN || qnnAvailable }.forEach { option -> FilterChip(selected = backend == option, onClick = { viewModel.selectBackend(option) }, label = { Text(option.displayName) }, enabled = !busy) }
            }
            if (qnnAvailable) Text(npuStatus, style = MaterialTheme.typography.bodySmall)
            Text("CPU/GPU use compatible LiteRT-LM packages. NPU requires the exact SoC model build. Choose the accelerator and context in each AI profile.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide tuning" else "Performance tuning") }
            if (expanded) {
                Text("CPU threads: ${if (threads.toInt() == 0) "Automatic" else threads.toInt().toString()}")
                Slider(value = threads.coerceIn(0f, viewModel.cores.toFloat()), onValueChange = { threads = it }, onValueChangeFinished = { viewModel.updateTuning(threads = threads.toInt()) }, valueRange = 0f..viewModel.cores.toFloat(), steps = (viewModel.cores - 1).coerceAtLeast(0), enabled = !busy, modifier = Modifier.semantics { contentDescription = "CPU threads; zero uses runtime defaults" })
                Text("Automatic uses the runtime default. More threads can increase heat without improving token speed.", style = MaterialTheme.typography.bodySmall)
                Text("Speculative decoding", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpeculativeDecodingMode.entries.forEach { option ->
                        FilterChip(
                            selected = settings.localSpeculativeDecoding == option,
                            onClick = { viewModel.updateTuning(speculative = option) },
                            label = { Text(option.label) },
                            enabled = !busy
                        )
                    }
                }
                Text("Model default follows the package's supported settings. On requires a compatible draft or MTP model package and may fail to load otherwise. Compare speed and answer quality in Benchmarks.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Native performance counters", Modifier.weight(1f))
                    Switch(settings.localNativeMetrics, { viewModel.updateTuning(nativeMetrics = it) }, enabled = !busy, modifier = Modifier.semantics { contentDescription = "Native performance counters" })
                }
                Text("Measure prefill and decode separately on this device. Counters cover the last inference segment and may add measurement overhead.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cache compiled models", Modifier.weight(1f))
                    Switch(settings.localModelCache, { viewModel.updateTuning(cache = it) }, enabled = !busy, modifier = Modifier.semantics { contentDescription = "Cache compiled models" })
                }
                if (qnnAvailable) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Automatic QNN fallback", Modifier.weight(1f))
                        Switch(settings.qnnAutomaticFallback, { viewModel.updateTuning(fallback = it) }, enabled = !busy, modifier = Modifier.semantics { contentDescription = "Automatic QNN fallback" })
                    }
                }
                Text("Release model after idle", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 5, 10, 30, 0).forEach { minutes -> FilterChip(settings.localIdleMinutes == minutes, onClick = { viewModel.updateTuning(idle = minutes) }, label = { Text(if (minutes == 0) "Keep warm" else "$minutes min") }, enabled = !busy) }
                }
                Text("Thermal: ${hardware.thermalState.name.lowercase()} · Battery: ${hardware.batteryPct}%${if (hardware.isPowerSaveMode) " · Power saver" else ""}", style = MaterialTheme.typography.bodySmall)
                hardware.thermalHeadroom?.let { headroom ->
                    Text("10-second thermal forecast: ${(headroom * 100).toInt()}% of the severe-throttling threshold", style = MaterialTheme.typography.bodySmall)
                }
                Text("New requests use smaller budgets under thermal or memory pressure. Limits relax after 30 seconds of stable lower pressure.", style = MaterialTheme.typography.bodySmall)
                FlowRow {
                    TextButton(onClick = viewModel::refresh) { Text("Refresh hardware") }
                    TextButton(onClick = { viewModel.releaseIdleMemory() }, enabled = !busy) { Text("Release idle memory") }
                    TextButton(onClick = { viewModel.updateTuning(threads = 0, cache = true, idle = 10, fallback = true, speculative = SpeculativeDecodingMode.AUTO, nativeMetrics = false) }, enabled = !busy) { Text("Recommended defaults") }
                }
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
