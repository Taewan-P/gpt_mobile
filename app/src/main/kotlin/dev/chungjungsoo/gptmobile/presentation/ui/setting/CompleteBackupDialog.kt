package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.backup.BackupStatus
import dev.chungjungsoo.gptmobile.data.backup.CompleteBackupSection
import java.text.DateFormat
import java.util.Date

private enum class BackupAction { BACKUP, RESTORE }

@Composable
fun CompleteBackupDialog(
    state: SettingViewModelV2.BackupUiState,
    backupStatus: BackupStatus,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onSectionChange: (CompleteBackupSection, Boolean) -> Unit = { _, _ -> },
    onPasswordProtectionChange: (Boolean) -> Unit = {},
    onPasswordChange: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    var pendingAction by rememberSaveable { mutableStateOf<BackupAction?>(null) }

    Dialog(onDismissRequest = { if (!state.isBusy) onDismiss() }) {
        Surface(
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(stringResource(R.string.backup_and_restore), style = MaterialTheme.typography.headlineSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        if (state.passwordProtectionEnabled) "Your password is saved securely in the app and included inside the encrypted backup. Use it to restore after reinstall or on another device." else "Encryption is off. The backup includes readable app data and your saved password. Enable encryption to protect it.",
                        modifier = Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    backupStatus.lastBackupEpochMs?.let {
                        stringResource(R.string.complete_backup_last, DateFormat.getDateTimeInstance().format(Date(it)))
                    } ?: stringResource(R.string.complete_backup_none),
                    style = MaterialTheme.typography.bodySmall
                )

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { pendingAction = BackupAction.BACKUP },
                            enabled = state.canBackup,
                            modifier = Modifier.fillMaxWidth().testTag("backup_all")
                        ) {
                            Icon(Icons.Outlined.Backup, null)
                            Text("Backup", Modifier.padding(start = 8.dp))
                        }
                        OutlinedButton(
                            onClick = onRestore,
                            enabled = !state.isBusy,
                            modifier = Modifier.fillMaxWidth().testTag("restore_all")
                        ) {
                            Icon(Icons.Outlined.Restore, null)
                            Text("Restore", Modifier.padding(start = 8.dp))
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Encrypt backup", Modifier.weight(1f).padding(start = 10.dp))
                    Switch(
                        checked = state.passwordProtectionEnabled,
                        onCheckedChange = onPasswordProtectionChange,
                        enabled = !state.isBusy,
                        modifier = Modifier.testTag("backup_encrypt")
                    )
                }
                if (state.passwordProtectionEnabled) {
                    OutlinedTextField(
                        value = state.backupPassword,
                        onValueChange = onPasswordChange,
                        label = { Text("Password") },
                        supportingText = { Text("Saved automatically. Use at least 8 characters.") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth().testTag("backup_password")
                    )
                }

                if (state.isWorking) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.complete_backup_working), style = MaterialTheme.typography.bodySmall)
                }
                state.message?.let {
                    Text(
                        it,
                        color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                TextButton(onClick = onDismiss, enabled = !state.isBusy, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }

    pendingAction?.let { action ->
        BackupSelectionDialog(
            action = action,
            state = state,
            onSectionChange = onSectionChange,
            onCancel = { pendingAction = null },
            onContinue = {
                pendingAction = null
                if (action == BackupAction.BACKUP) onBackup() else onRestore()
            }
        )
    }
}

@Composable
private fun BackupSelectionDialog(
    action: BackupAction,
    state: SettingViewModelV2.BackupUiState,
    onSectionChange: (CompleteBackupSection, Boolean) -> Unit,
    onCancel: () -> Unit,
    onContinue: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(
                if (action == BackupAction.BACKUP) Icons.Outlined.Backup else Icons.Outlined.Restore,
                null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(if (action == BackupAction.BACKUP) "Backup contents" else "Restore contents") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                BackupSelectionContent(state, onSectionChange)
            }
        },
        confirmButton = {
            Button(onClick = onContinue, enabled = state.selection.sections.isNotEmpty()) {
                Text(if (action == BackupAction.BACKUP) "Backup" else "Restore")
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun BackupOptionRow(
    state: SettingViewModelV2.BackupUiState,
    section: CompleteBackupSection,
    title: String,
    subtitle: String,
    onSectionChange: (CompleteBackupSection, Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = section in state.selection.sections,
            enabled = !state.isBusy,
            onCheckedChange = { onSectionChange(section, it) }
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun BackupSelectionContent(
    state: SettingViewModelV2.BackupUiState,
    onSectionChange: (CompleteBackupSection, Boolean) -> Unit
) {
    val allSelected = state.selection.sections.containsAll(CompleteBackupSection.entries)
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = allSelected,
                onCheckedChange = { checked ->
                    CompleteBackupSection.entries.forEach { onSectionChange(it, checked) }
                }
            )
            Text("Select all", fontWeight = FontWeight.SemiBold)
        }
        BackupOptionRow(state, CompleteBackupSection.SETTINGS, "Settings, themes & preferences", "Includes appearance and custom theme configuration.", onSectionChange)
        BackupOptionRow(state, CompleteBackupSection.CONVERSATIONS, "Conversations & favorites", "Messages, titles, drafts and chat choices.", onSectionChange)
        BackupOptionRow(state, CompleteBackupSection.PLATFORMS, "AI platforms & profiles", "Remote, local and free model profiles.", onSectionChange)
        BackupOptionRow(state, CompleteBackupSection.TOOLS, "Tools & MCP connections", "Bindings, providers and tool configuration.", onSectionChange)
        BackupOptionRow(state, CompleteBackupSection.CREDENTIALS, "Credentials & APIs", "Provider secrets, Brave Search and Hugging Face access token.", onSectionChange)
        BackupOptionRow(state, CompleteBackupSection.MEMORY, "Memory", "Local memory vault and memory metadata.", onSectionChange)
        BackupOptionRow(state, CompleteBackupSection.LOCAL_MODELS, "Local models", "Installed model records and model files.", onSectionChange)
        BackupOptionRow(state, CompleteBackupSection.ATTACHMENTS, "Attachments", "Files and images attached to conversations.", onSectionChange)
        BackupOptionRow(state, CompleteBackupSection.AGENT_HISTORY, "Agent & tool history", "Agent runs, tool events and diagnostics history.", onSectionChange)
        Text(
            "These choices are saved and reused the next time you open Backup & Restore.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
