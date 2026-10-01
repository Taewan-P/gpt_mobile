package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(
    settingViewModel: SettingViewModelV2,
    onNavigationClick: () -> Unit,
    onNavigateToAiPlatforms: () -> Unit,
    onNavigateToLocalModels: () -> Unit,
    onNavigateToOpenRouterSettings: () -> Unit = {},
    onNavigateToToolConnections: () -> Unit,
    onNavigateToAdvancedSettings: () -> Unit,
    onNavigateToDebugDiagnostics: () -> Unit,
    onNavigateToAboutPage: () -> Unit,
    onNavigateToFactVault: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val platforms by settingViewModel.platformState.collectAsState()
    val providerConnections by settingViewModel.providerConnections.collectAsState()
    val dialogState by settingViewModel.dialogState.collectAsState()
    val debugMode by settingViewModel.debugMode.collectAsState()
    val featureSettings by settingViewModel.featureSettings.collectAsState()
    val backupStatus by settingViewModel.backupStatus.collectAsState()
    val backupUi by settingViewModel.backupUi.collectAsState()
    val context = LocalContext.current
    var showDelegation by remember { mutableStateOf(false) }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = settingViewModel::backupDestinationSelected
    )
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = settingViewModel::restoreSourceSelected
    )

    val recoveryKeyBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
        settingViewModel::backupRecoveryKeySelected
    )
    val recoveryKeyRestoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
        settingViewModel::restoreRecoveryKeySelected
    )
    LaunchedEffect(backupUi.backupUri) {
        if (backupUi.backupUri != null) {
            try {
                recoveryKeyBackupLauncher.launch("gpt_mobile_${System.currentTimeMillis()}.gptkey")
            } catch (_: android.content.ActivityNotFoundException) {
                settingViewModel.cancelBackupPicker()
                Toast.makeText(context, R.string.backup_picker_unavailable, Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(settingViewModel) {
        settingViewModel.uiEvent.collect { event ->
            when (event) {
                is SettingViewModelV2.UiEvent.ShowToast ->
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigationClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.go_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                SettingsCategory(
                    title = "AI & models"
                ) {
                    SettingsDestination(
                        icon = Icons.Default.SmartToy,
                        title = "AI Platforms & Profiles",
                        onClick = onNavigateToAiPlatforms
                    )
                    SettingsDestination(
                        icon = Icons.Default.AccountTree,
                        title = "Memory",
                        onClick = onNavigateToFactVault
                    )
                    SettingsDestination(
                        icon = Icons.Default.Storage,
                        title = stringResource(R.string.local_models),
                        onClick = onNavigateToLocalModels
                    )
                    SettingsDestination(
                        icon = Icons.Default.Psychology,
                        title = "Model Delegation",
                        onClick = { showDelegation = true }
                    )
                }
            }

            item {
                SettingsCategory(
                    title = "Tools & connectivity"
                ) {
                    SettingsDestination(
                        icon = Icons.Default.Build,
                        title = stringResource(R.string.tool_connections),
                        onClick = onNavigateToToolConnections
                    )
                }
            }

            item {
                SettingsCategory(
                    title = "Experience"
                ) {
                    SettingsDestination(
                        icon = Icons.Default.Palette,
                        title = stringResource(R.string.theme_settings),
                        onClick = settingViewModel::openThemeDialog
                    )
                    SettingsDestination(
                        icon = Icons.Default.Tune,
                        title = "Advanced Settings",
                        onClick = onNavigateToAdvancedSettings
                    )
                }
            }

            item {
                SettingsCategory(
                    title = "Diagnostics & data"
                ) {
                    SettingsDestination(
                        icon = Icons.Default.BugReport,
                        title = "Debug and Statistics",
                        onClick = onNavigateToDebugDiagnostics
                    )

                    SettingsDestination(
                        icon = Icons.Default.Backup,
                        title = stringResource(R.string.backup_and_restore),
                        onClick = settingViewModel::openBackupRestoreDialog
                    )
                }
            }

            item {
                SettingsCategory(
                    title = "About"
                ) {
                    SettingsDestination(
                        icon = Icons.Default.Info,
                        title = stringResource(R.string.about),
                        onClick = onNavigateToAboutPage
                    )
                }
            }
            item { Spacer(Modifier.height(48.dp)) }
        }
    }

    if (showDelegation) {
        LocalToolConfigurationDialog(section = "delegation", onDismiss = { showDelegation = false })
    }

    if (dialogState.isThemeDialogOpen) {
        ThemeSettingDialog(settingViewModel)
    }

    if (dialogState.isBackupRestoreDialogOpen) {
        CompleteBackupDialog(
            state = backupUi,
            backupStatus = backupStatus,
            onDismiss = settingViewModel::closeBackupRestoreDialog,
            onBackup = {
                if (settingViewModel.prepareBackupPicker(restoring = false)) {
                    try {
                        backupLauncher.launch("gpt_mobile_${System.currentTimeMillis()}.gptbackup")
                    } catch (_: android.content.ActivityNotFoundException) {
                        settingViewModel.cancelBackupPicker()
                        Toast.makeText(context, R.string.backup_picker_unavailable, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onRestore = {
                if (settingViewModel.prepareBackupPicker(restoring = true)) {
                    try {
                        restoreLauncher.launch(arrayOf("*/*"))
                    } catch (_: android.content.ActivityNotFoundException) {
                        settingViewModel.cancelBackupPicker()
                        Toast.makeText(context, R.string.backup_picker_unavailable, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onSectionChange = settingViewModel::updateBackupSection,
            onPasswordProtectionChange = settingViewModel::updateBackupPasswordProtection,
            onPasswordChange = settingViewModel::updateBackupPassword
        )
    }

    if (backupUi.restoreUri != null) {
        AlertDialog(
            title = { Text(stringResource(R.string.complete_restore_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.complete_restore_confirmation))
                    BackupSelectionContent(backupUi, settingViewModel::updateBackupSection)
                    if (backupUi.requiresRecoveryKey) {
                        Text("Select the separate recovery key saved with this backup. No password is required.")
                        Button(onClick = {
                            try {
                                recoveryKeyRestoreLauncher.launch(arrayOf("*/*"))
                            } catch (_: android.content.ActivityNotFoundException) {
                                Toast.makeText(context, R.string.backup_picker_unavailable, Toast.LENGTH_LONG).show()
                            }
                        }) {
                            Text(if (backupUi.recoveryKeyUri == null) "Choose recovery key" else "Recovery key selected")
                        }
                    }
                    if (backupUi.requiresLegacyPassword) {
                        Text(
                            text = stringResource(R.string.complete_backup_legacy_password_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = backupUi.legacyPassword,
                            onValueChange = settingViewModel::updateLegacyBackupPassword,
                            label = { Text(stringResource(R.string.complete_backup_legacy_password)) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            onDismissRequest = settingViewModel::cancelBackupPicker,
            confirmButton = {
                Button(
                    enabled = backupUi.selection.sections.isNotEmpty() &&
                        (!backupUi.requiresLegacyPassword || backupUi.legacyPassword.isNotBlank()) &&
                        (!backupUi.requiresRecoveryKey || backupUi.recoveryKeyUri != null),
                    onClick = settingViewModel::confirmRestore
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = settingViewModel::cancelBackupPicker) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingsHeroCard(
    activeProfiles: Int,
    totalProfiles: Int,
    providerCount: Int,
    runtime: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("GPT Mobile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Configure the parts of the app you actually use. Provider connections, AI behavior and tools are kept separate.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsStat("$activeProfiles/$totalProfiles", "AI profiles", Modifier.weight(1f))
                SettingsStat(providerCount.toString(), "Providers", Modifier.weight(1f))
                SettingsStat(runtime, "Local runtime", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SettingsStat(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsCategory(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun SettingsDestination(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun ThemeSettingDialog(settingViewModel: SettingViewModelV2) {
    ThemeSettingsScreen(onDismiss = settingViewModel::closeThemeDialog)
}
