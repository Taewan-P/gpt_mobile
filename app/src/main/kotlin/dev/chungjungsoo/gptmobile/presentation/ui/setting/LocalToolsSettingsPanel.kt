package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R

@Composable
fun LocalToolsSettingsPanel(
    initialSection: String? = null,
    settingsOnly: Boolean = false,
    viewModel: LocalToolsViewModel = hiltViewModel(),
    memory: FactVaultViewModel = hiltViewModel()
) {
    val vault by memory.vault.collectAsStateWithLifecycle()
    val memoryBusy by memory.busy.collectAsStateWithLifecycle()
    val memoryError by memory.error.collectAsStateWithLifecycle()
    val budget by viewModel.tokenBudget.collectAsStateWithLifecycle()
    var showDoctor by remember { mutableStateOf(false) }
    if (showDoctor) ConnectionDoctorDialog(onDismiss = { showDoctor = false })
    var showMemory by remember { mutableStateOf(initialSection == "memory") }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!settingsOnly) TextButton(onClick = { showDoctor = true }) { Text(stringResource(R.string.local_tools_settings_panel_label_1)) }
        if (settingsOnly) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.local_tools_settings_panel_label_2), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.local_tools_settings_panel_label_3), style = MaterialTheme.typography.bodySmall)
                    LocalToolToggle("No app context limit", budget.contextTokens == Int.MAX_VALUE, true) { enabled -> viewModel.updateBudget { it.copy(contextTokens = if (enabled) Int.MAX_VALUE else 32768) } }
                    if (budget.contextTokens != Int.MAX_VALUE) DelegationNumber("Context window tokens", budget.contextTokens, 2048..1048576, true) { value -> viewModel.updateBudget { it.copy(contextTokens = value) } }
                    LocalToolToggle("No app output-token limit", budget.outputTokens == 0, true) { enabled -> viewModel.updateBudget { it.copy(outputTokens = if (enabled) 0 else 32768) } }
                    if (budget.outputTokens > 0) DelegationNumber("Output token limit (recommended: 32768)", budget.outputTokens, 1..Int.MAX_VALUE, true) { value -> viewModel.updateBudget { it.copy(outputTokens = value) } }
                    LocalToolToggle("No app total-token limit", budget.totalRunTokens == Int.MAX_VALUE, true) { enabled -> viewModel.updateBudget { it.copy(totalRunTokens = if (enabled) Int.MAX_VALUE else 65536) } }
                    if (budget.totalRunTokens != Int.MAX_VALUE) DelegationNumber("Total run tokens including delegates", budget.totalRunTokens, 4096..2097152, true) { value -> viewModel.updateBudget { it.copy(totalRunTokens = value) } }
                }
            }
        }
        if (!settingsOnly) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LocalToolToggle("Local memory capture and recall", vault.enabled, !memoryBusy, memory::setEnabled)
                    Text(stringResource(R.string.local_tools_settings_panel_label_4), style = MaterialTheme.typography.bodySmall)
                    Text(if (vault.settings.allowCloudRecall) "Recall can be included in cloud AI requests. Change this in Configure memory." else "Recall is restricted to local AI platforms.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { showMemory = true }) { Text("Configure memory · ${vault.facts.size} facts") }
                    memoryError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    if (showMemory) {
        Dialog(onDismissRequest = { showMemory = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            FactVaultScreen(viewModel = memory, onBack = { showMemory = false })
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LocalToolConfigurationDialog(section: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                androidx.compose.material3.TopAppBar(
                    title = { Text(if (section == "delegation") "Model Delegation" else stringResource(R.string.local_tools_settings_panel_label_12)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.local_tools_settings_panel_label_12))
                        }
                    }
                )
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    if (section == "delegation") ModelDelegationSettingsPanel() else LocalToolsSettingsPanel(initialSection = section)
                }
            }
        }
    }
}

@Composable
internal fun LocalToolToggle(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Switch(checked, onChange, enabled = enabled, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun DelegationNumber(label: String, value: Int, range: IntRange, enabled: Boolean, save: (Int) -> Unit) {
    var draft by remember(value) { mutableStateOf(value.toString()) }
    val parsed = draft.toIntOrNull()
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = draft, onValueChange = { draft = it.take(7) }, label = { Text(label) }, supportingText = { Text("${range.first}–${range.last}") }, isError = parsed == null || parsed !in range, enabled = enabled, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        TextButton(enabled = enabled && parsed != null && parsed in range && parsed != value, onClick = { parsed?.let(save) }) { Text(stringResource(R.string.local_tools_settings_panel_label_13)) }
    }
}
