package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.model.excludesMemory
import dev.chungjungsoo.gptmobile.data.model.isPrivateDestination

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
    val config by viewModel.delegation.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var showDoctor by remember { mutableStateOf(false) }
    if (showDoctor) ConnectionDoctorDialog(onDismiss = { showDoctor = false })
    var showMemory by remember { mutableStateOf(initialSection == "memory") }
    var advancedDelegation by remember { mutableStateOf(false) }
    var showDelegation by remember { mutableStateOf(initialSection == "delegation") }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!settingsOnly) TextButton(onClick = { showDoctor = true }) { Text(stringResource(R.string.local_tools_settings_panel_label_1)) }
        if (settingsOnly) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.local_tools_settings_panel_label_2), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.local_tools_settings_panel_label_3), style = MaterialTheme.typography.bodySmall)
                    LocalToolToggle("No app context limit", budget.contextTokens == Int.MAX_VALUE, true) { enabled -> viewModel.updateBudget { it.copy(contextTokens = if (enabled) Int.MAX_VALUE else 32768) } }
                    if (budget.contextTokens != Int.MAX_VALUE) DelegationNumber("Context window tokens", budget.contextTokens, 2048..1048576, true) { value -> viewModel.updateBudget { it.copy(contextTokens = value) } }
                    DelegationNumber("Output reserve tokens", budget.outputTokens, 128..32768, true) { value -> viewModel.updateBudget { it.copy(outputTokens = value) } }
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
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LocalToolToggle("Model delegation", config.enabled, !busy) { value ->
                        viewModel.update { it.copy(enabled = value) }
                        if (value) showDelegation = true
                    }
                    Text(stringResource(R.string.local_tools_settings_panel_label_5), style = MaterialTheme.typography.bodySmall)
                    Text("Use a main model that supports tools, then select a helper profile.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = { showDelegation = !showDelegation }) { Text(if (showDelegation) "Hide configuration" else "Configure delegation") }
                    if (showDelegation) {
                        LocalToolToggle("Only private destinations", config.localPlatformsOnly, !busy) { value -> viewModel.update { it.copy(localPlatformsOnly = value) } }
                        Text(stringResource(R.string.local_tools_settings_panel_label_6), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.local_tools_settings_panel_label_7), style = MaterialTheme.typography.titleSmall)
                        val eligible = profiles.filter { it.enabled && !it.excludesMemory() && (!config.localPlatformsOnly || it.isPrivateDestination()) }
                        if (eligible.isEmpty()) Text(stringResource(R.string.local_tools_settings_panel_label_8))
                        if (eligible.none { it.uid == config.targetProfileUid }) Text(stringResource(R.string.local_tools_settings_panel_label_9), color = MaterialTheme.colorScheme.error)
                        eligible.forEach { profile ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = config.targetProfileUid == profile.uid, enabled = !busy, onClick = { viewModel.update { it.copy(targetProfileUid = profile.uid) } }, modifier = Modifier.semantics { contentDescription = "Delegate to ${profile.name}" })
                                Column {
                                    Text(profile.name)
                                    Text("${profile.compatibleType} · ${profile.model}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        TextButton(onClick = { advancedDelegation = !advancedDelegation }) { Text(if (advancedDelegation) "Hide advanced limits" else "Advanced limits") }
                        if (advancedDelegation) {
                            DelegationNumber("Maximum input characters", config.maxInputCharacters, 500..16000, !busy) { value -> viewModel.update { it.copy(maxInputCharacters = value) } }
                            DelegationNumber("Maximum output tokens", config.maxOutputTokens, 64..2048, !busy) { value -> viewModel.update { it.copy(maxOutputTokens = value) } }
                            DelegationNumber("Timeout in seconds", config.timeoutSeconds, 5..40, !busy) { value -> viewModel.update { it.copy(timeoutSeconds = value) } }
                            DelegationNumber("Calls per conversation turn", config.maxCallsPerTurn, 1..3, !busy) { value -> viewModel.update { it.copy(maxCallsPerTurn = value) } }
                        }
                        Text(stringResource(R.string.local_tools_settings_panel_label_10), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.local_tools_settings_panel_label_11), style = MaterialTheme.typography.bodySmall)
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
fun LocalToolConfigurationDialog(section: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.heightIn(max = 650.dp).verticalScroll(rememberScrollState())) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.local_tools_settings_panel_label_12)) }
                LocalToolsSettingsPanel(initialSection = section)
            }
        }
    }
}

@Composable
private fun LocalToolToggle(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
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
