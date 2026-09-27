/*
 * Copyright (C) 2024-2026 Melo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AllInbox
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.localruntime.LocalAccelerators
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.FreeAiProvider
import dev.chungjungsoo.gptmobile.data.model.SamplingCreativity
import dev.chungjungsoo.gptmobile.data.model.excludesMemory
import dev.chungjungsoo.gptmobile.data.model.parseProfileLabels
import dev.chungjungsoo.gptmobile.presentation.common.BeveledProfileLabel
import dev.chungjungsoo.gptmobile.presentation.common.FreeProviderPicker
import dev.chungjungsoo.gptmobile.presentation.common.ProfileLabelEditorDialog
import dev.chungjungsoo.gptmobile.presentation.common.SettingItem
import dev.chungjungsoo.gptmobile.util.PERMISSION_ACCESS_LOCAL_NETWORK
import dev.chungjungsoo.gptmobile.util.formatPlatformTimeout
import dev.chungjungsoo.gptmobile.util.pinnedExitUntilCollapsedScrollBehavior
import dev.chungjungsoo.gptmobile.util.requiresLocalNetworkAccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformSettingScreen(
    modifier: Modifier = Modifier,
    settingViewModel: PlatformSettingViewModel = hiltViewModel(),
    onNavigationClick: () -> Unit = {},
    onNavigateToBenchmarks: () -> Unit = {},
    onNavigateToUsage: () -> Unit = {},
    onNavigateToLocalModels: () -> Unit = {},
    onNavigateToMcpTools: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = pinnedExitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward }
    )
    val platform by settingViewModel.platformState.collectAsStateWithLifecycle()
    val debugMode by settingViewModel.debugMode.collectAsStateWithLifecycle()
    val providerConnection by settingViewModel.providerConnectionState.collectAsStateWithLifecycle()
    val reusableLabels by settingViewModel.reusableLabels.collectAsStateWithLifecycle()
    val dialogState by settingViewModel.dialogState.collectAsStateWithLifecycle()
    val isDeleted by settingViewModel.isDeleted.collectAsStateWithLifecycle()
    val toolBindingState by settingViewModel.toolBindingState.collectAsStateWithLifecycle()
    val downloadedLocalModels by settingViewModel.downloadedLocalModels.collectAsStateWithLifecycle()
    val acceleratorOptions by settingViewModel.acceleratorOptions.collectAsStateWithLifecycle()
    val userMessage by settingViewModel.userMessage.collectAsStateWithLifecycle()
    val openRouterCreditsState by settingViewModel.openRouterCreditsState.collectAsStateWithLifecycle()
    val ollamaServerState by settingViewModel.ollamaServerState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var openMcpToolsAfterPermission by remember { mutableStateOf(false) }
    var showBatchUrlDialog by remember { mutableStateOf(false) }
    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && openMcpToolsAfterPermission) {
            settingViewModel.openMcpToolsDialog()
        } else if (!granted) {
            Toast.makeText(context, R.string.local_network_permission_required, Toast.LENGTH_SHORT).show()
        }
        openMcpToolsAfterPermission = false
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            settingViewModel.toggleDeviceLocation(true)
        } else {
            Toast.makeText(context, "Location permission is required for the device location tool.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(isDeleted) {
        if (isDeleted) {
            onNavigationClick()
        }
    }

    LaunchedEffect(userMessage) {
        userMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            settingViewModel.consumeUserMessage()
        }
    }

    platform?.let { platformData ->
        val profileLabels = remember(platformData.labels) { parseProfileLabels(platformData.labels) }
        Scaffold(
            modifier = modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                PlatformTopAppBar(
                    title = platformData.name,
                    onNavigationClick = onNavigationClick,
                    onDeleteClick = settingViewModel::openDeleteDialog,
                    scrollBehavior = scrollBehavior
                )
            }
        ) { innerPadding ->
            Column(
                Modifier
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
            ) {
                val isLocalPlatform = platformData.compatibleType == ClientType.LITERT_LM
                val isFreePlatform = platformData.compatibleType == ClientType.FREE
                val supportsTools = !isFreePlatform || FreeAiProvider.fromApiUrl(platformData.apiUrl)?.supportsTools == true
                val isOllamaPlatform = platformData.compatibleType == ClientType.OLLAMA
                PreferenceSwitchWithContainer(
                    title = stringResource(if (isLocalPlatform) R.string.enable_platform else R.string.enable),
                    isChecked = platformData.enabled
                ) { settingViewModel.toggleEnabled() }
                if (isFreePlatform) {
                    FreeProviderPicker(
                        apiUrl = platformData.apiUrl,
                        onProviderSelected = settingViewModel::selectFreeProvider,
                        modifier = Modifier.padding(16.dp),
                        enabled = platformData.enabled
                    )
                }
                if (debugMode) {
                    ProfileSectionTitle(title = "Debug & performance")
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = onNavigateToUsage, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.BarChart, null)
                            Text("Usage", Modifier.padding(start = 8.dp))
                        }
                        FilledTonalButton(onClick = onNavigateToBenchmarks, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Speed, null)
                            Text("Benchmarks", Modifier.padding(start = 8.dp))
                        }
                    }
                }
                ProfileSectionTitle(
                    title = stringResource(
                        if (isLocalPlatform) R.string.profile_settings else R.string.connection_settings
                    )
                )
                if (!isLocalPlatform && providerConnection != null) {
                    SettingItem(
                        modifier = Modifier.height(64.dp),
                        title = stringResource(R.string.provider_connection),
                        description = providerConnection?.name ?: stringResource(R.string.not_set),
                        enabled = false,
                        onItemClick = {},
                        showTrailingIcon = false,
                        showLeadingIcon = false
                    )
                }
                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.ai_profile_name),
                    description = platformData.name,
                    enabled = platformData.enabled,
                    onItemClick = { if (platformData.compatibleType != ClientType.FREE) settingViewModel.openPlatformNameDialog() },
                    showTrailingIcon = false,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Label,
                            contentDescription = stringResource(R.string.platform_name)
                        )
                    }
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = platformData.enabled,
                            onClick = settingViewModel::openLabelsDialog
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Labels",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (profileLabels.isEmpty()) "Add" else "Manage",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Reusable colored labels organize profiles and filter the model picker.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    if (profileLabels.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(top = 8.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            profileLabels.forEach { label ->
                                BeveledProfileLabel(label = label)
                            }
                        }
                    }
                }
                // Endpoint and credentials belong to the parent provider connection.
                // Standalone/legacy profiles keep their own connection fields for compatibility.
                if (!isLocalPlatform && !isFreePlatform && providerConnection == null) {
                    SettingItem(
                        modifier = Modifier.height(64.dp),
                        title = stringResource(R.string.api_url),
                        description = platformData.apiUrl,
                        enabled = platformData.enabled,
                        onItemClick = settingViewModel::openApiUrlDialog,
                        showTrailingIcon = false,
                        showLeadingIcon = true,
                        leadingIcon = {
                            Icon(
                                ImageVector.vectorResource(id = R.drawable.ic_link),
                                contentDescription = stringResource(R.string.api_url)
                            )
                        }
                    )
                    SettingItem(
                        modifier = Modifier.height(64.dp),
                        title = stringResource(R.string.api_key),
                        description = if (platformData.token.isNullOrEmpty()) {
                            stringResource(R.string.not_set)
                        } else {
                            "••••••••"
                        },
                        enabled = platformData.enabled,
                        onItemClick = settingViewModel::openApiTokenDialog,
                        showTrailingIcon = false,
                        showLeadingIcon = true,
                        leadingIcon = {
                            Icon(
                                ImageVector.vectorResource(id = R.drawable.ic_key),
                                contentDescription = stringResource(R.string.api_key)
                            )
                        }
                    )
                }

                if (isOllamaPlatform) {
                    OllamaServerCard(
                        serverUrl = platformData.apiUrl.orEmpty(),
                        uiState = ollamaServerState,
                        currentModel = platformData.model,
                        onTestConnection = settingViewModel::checkOllamaServer,
                        onSelectModel = settingViewModel::updateApiModel
                    )
                }

                if (platformData.compatibleType == ClientType.OPENROUTER && !platformData.token.isNullOrBlank()) {
                    FancyOpenRouterCreditsCard(
                        uiState = openRouterCreditsState,
                        onRefresh = { settingViewModel.refreshOpenRouterCredits(forceRefresh = true) }
                    )
                }

                ProfileSectionTitle(title = stringResource(R.string.model_behavior))
                val modelDescription = downloadedLocalModels
                    .firstOrNull { it.catalogEntryId == platformData.model }
                    ?.displayName ?: platformData.model
                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.api_model),
                    description = modelDescription,
                    enabled = platformData.enabled && !isFreePlatform,
                    onItemClick = settingViewModel::openApiModelDialog,
                    showTrailingIcon = false,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            ImageVector.vectorResource(id = R.drawable.ic_model),
                            contentDescription = stringResource(R.string.api_model)
                        )
                    }
                )
                ProfileSectionTitle(title = stringResource(R.string.advanced_settings))
                val isReasoningDisabled = platformData.compatibleType == ClientType.OPENAI && platformData.reasoning
                val usesNpuSampling = isLocalPlatform && !LocalAccelerators.shouldApplySampler(platformData.accelerator.orEmpty())
                if (usesNpuSampling) {
                    Text(
                        "NPU uses the model’s built-in sampling defaults.",
                        Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val notSetText = stringResource(R.string.not_set)
                var creativityDraft by remember(
                    platformData.uid,
                    platformData.temperature,
                    platformData.topP
                ) {
                    mutableFloatStateOf(
                        SamplingCreativity.fromSampling(
                            platformData.temperature,
                            platformData.topP
                        )
                    )
                }
                CreativitySlider(
                    value = creativityDraft,
                    onValueChange = { creativityDraft = it },
                    onValueChangeFinished = {
                        settingViewModel.updateCreativity(creativityDraft)
                    },
                    enabled = platformData.enabled && !isReasoningDisabled && !usesNpuSampling
                )
                if (isLocalPlatform) {
                    SettingItem(
                        modifier = Modifier.height(64.dp),
                        title = stringResource(R.string.top_k),
                        description = platformData.topK?.toString() ?: notSetText,
                        enabled = platformData.enabled && !usesNpuSampling,
                        onItemClick = settingViewModel::openTopKDialog,
                        showTrailingIcon = false,
                        showLeadingIcon = true,
                        leadingIcon = {
                            Icon(
                                ImageVector.vectorResource(id = R.drawable.ic_chart),
                                contentDescription = stringResource(R.string.top_k)
                            )
                        }
                    )
                    val maxTokensDescription = platformData.maxTokens?.toString()
                        ?: stringResource(R.string.output_tokens_unlimited)
                    SettingItem(
                        modifier = Modifier.height(64.dp),
                        title = stringResource(R.string.max_tokens),
                        description = maxTokensDescription,
                        enabled = platformData.enabled,
                        onItemClick = settingViewModel::openMaxTokensDialog,
                        showTrailingIcon = false,
                        showLeadingIcon = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Numbers,
                                contentDescription = stringResource(R.string.max_tokens)
                            )
                        }
                    )
                    SettingItem(
                        modifier = Modifier.height(64.dp),
                        title = stringResource(R.string.accelerator),
                        description = acceleratorLabel(platformData.accelerator),
                        enabled = platformData.enabled && acceleratorOptions.isNotEmpty(),
                        onItemClick = settingViewModel::openAcceleratorDialog,
                        showTrailingIcon = false,
                        showLeadingIcon = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Speed,
                                contentDescription = stringResource(R.string.accelerator)
                            )
                        }
                    )
                }
                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.system_prompt),
                    description = platformData.systemPrompt,
                    enabled = platformData.enabled,
                    onItemClick = settingViewModel::openSystemPromptDialog,
                    showTrailingIcon = false,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            ImageVector.vectorResource(id = R.drawable.ic_instructions),
                            contentDescription = stringResource(R.string.system_prompt)
                        )
                    }
                )
                if (!isLocalPlatform && !isFreePlatform) {
                    SettingItem(
                        modifier = Modifier.height(64.dp),
                        title = stringResource(R.string.timeout),
                        description = formatPlatformTimeout(platformData.timeout, stringResource(R.string.not_set)),
                        enabled = platformData.enabled,
                        onItemClick = settingViewModel::openTimeoutDialog,
                        showTrailingIcon = false,
                        showLeadingIcon = true,
                        leadingIcon = {
                            Icon(
                                ImageVector.vectorResource(id = R.drawable.ic_info),
                                contentDescription = stringResource(R.string.timeout)
                            )
                        }
                    )
                }
                if (platformData.compatibleType == ClientType.GOOGLE) {
                    SettingItem(
                        modifier = Modifier.height(64.dp),
                        title = stringResource(R.string.gemini_safety_settings),
                        description = stringResource(R.string.gemini_safety_settings),
                        enabled = platformData.enabled,
                        onItemClick = settingViewModel::openGeminiSafetyDialog,
                        showTrailingIcon = false,
                        showLeadingIcon = true,
                        leadingIcon = {
                            Icon(
                                ImageVector.vectorResource(id = R.drawable.ic_info),
                                contentDescription = stringResource(R.string.gemini_safety_settings)
                            )
                        }
                    )
                }
                if (platformData.compatibleType == ClientType.OPENROUTER) {
                    SettingItem(
                        modifier = Modifier.height(64.dp),
                        title = stringResource(R.string.openrouter_advanced_settings),
                        description = if (platformData.openRouterRouting.isNullOrBlank()) {
                            stringResource(R.string.default_label)
                        } else {
                            stringResource(R.string.custom)
                        },
                        enabled = platformData.enabled,
                        onItemClick = settingViewModel::openOpenRouterSettingsDialog,
                        showTrailingIcon = true,
                        showLeadingIcon = true,
                        leadingIcon = {
                            Icon(
                                ImageVector.vectorResource(id = R.drawable.ic_instructions),
                                contentDescription = stringResource(R.string.openrouter_advanced_settings)
                            )
                        }
                    )
                }
                if (platformData.compatibleType == ClientType.OLLAMA) {
                    SettingItem(
                        modifier = Modifier.height(64.dp),
                        title = stringResource(R.string.ollama_advanced_options),
                        description = if (platformData.ollamaOptions.isNullOrBlank()) {
                            stringResource(R.string.default_label)
                        } else {
                            stringResource(R.string.custom)
                        },
                        enabled = platformData.enabled,
                        onItemClick = settingViewModel::openOllamaAdvancedDialog,
                        showTrailingIcon = true,
                        showLeadingIcon = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = stringResource(R.string.ollama_advanced_options)
                            )
                        }
                    )
                }
                if (platformData.compatibleType == ClientType.LLAMA) {
                    SettingItem(
                        modifier = Modifier.height(64.dp),
                        title = stringResource(R.string.llama_advanced_settings),
                        description = stringResource(R.string.llama_advanced_settings_description),
                        enabled = platformData.enabled,
                        onItemClick = settingViewModel::openLlamaAdvancedDialog,
                        showTrailingIcon = true,
                        showLeadingIcon = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = stringResource(R.string.llama_advanced_settings)
                            )
                        }
                    )
                }
                if (!isLocalPlatform && !isFreePlatform) {
                    ExtendedThinkingSwitch(
                        modifier = Modifier.height(64.dp),
                        enabled = platformData.enabled,
                        isChecked = platformData.reasoning,
                        onCheckedChange = { settingViewModel.toggleReasoning() }
                    )

                    // Provider-specific OpenRouter batching lives exclusively in
                    // OpenRouter Provider Settings. Keep these legacy per-profile controls
                    // only for providers whose batch configuration is still profile-scoped.
                    if (platformData.compatibleType == ClientType.OPENAI ||
                        platformData.compatibleType == ClientType.ANTHROPIC
                    ) {
                        PreferenceListSwitch(
                            modifier = Modifier.height(64.dp),
                            title = stringResource(R.string.batch_mode),
                            description = stringResource(R.string.batch_mode_description),
                            icon = Icons.Default.AllInbox,
                            enabled = platformData.enabled,
                            isChecked = platformData.batchMode,
                            onCheckedChange = {
                                settingViewModel.updatePlatform(platformData.copy(batchMode = it))
                            }
                        )
                        if (platformData.batchMode) {
                            SettingItem(
                                modifier = Modifier.height(64.dp),
                                title = stringResource(R.string.batch_api_url),
                                description = platformData.batchApiUrl ?: stringResource(R.string.not_set),
                                enabled = platformData.enabled,
                                onItemClick = { showBatchUrlDialog = true },
                                showTrailingIcon = false,
                                showLeadingIcon = true,
                                leadingIcon = {
                                    Icon(
                                        ImageVector.vectorResource(id = R.drawable.ic_link),
                                        contentDescription = stringResource(R.string.batch_api_url)
                                    )
                                }
                            )
                        }
                    }
                }

                ProfileSectionTitle(title = stringResource(R.string.tools_section))

                // Global Master Tool Disablement
                PreferenceListSwitch(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.disable_all_tools),
                    description = stringResource(R.string.disable_all_tools_description),
                    icon = Icons.Default.Build,
                    enabled = supportsTools && platformData.enabled,
                    isChecked = platformData.disableAllTools,
                    onCheckedChange = { settingViewModel.toggleDisableAllTools() }
                )

                // Granular Remote vs Local Tool Disablement
                PreferenceListSwitch(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.disable_remote_tools),
                    description = stringResource(R.string.disable_remote_tools_description),
                    icon = Icons.Default.Language,
                    enabled = supportsTools && platformData.enabled && !platformData.disableAllTools,
                    isChecked = platformData.disableRemoteTools,
                    onCheckedChange = { settingViewModel.toggleDisableRemoteTools() }
                )

                PreferenceListSwitch(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.disable_local_tools),
                    description = stringResource(R.string.disable_local_tools_description),
                    icon = Icons.Default.Calculate,
                    enabled = supportsTools && platformData.enabled && !platformData.disableAllTools,
                    isChecked = platformData.disableLocalTools,
                    onCheckedChange = { settingViewModel.toggleDisableLocalTools() }
                )

                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.web_search),
                    description = "Built-in search + ${toolBindingState.selectedSearchConnectionUids.size} connected engines",
                    enabled = supportsTools && platformData.enabled && !platformData.disableAllTools && !platformData.disableRemoteTools,
                    onItemClick = settingViewModel::openSearchBackendDialog,
                    showTrailingIcon = true,
                    showLeadingIcon = false
                )
                PreferenceListSwitch(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.tool_trace_tool),
                    icon = ImageVector.vectorResource(id = R.drawable.ic_link),
                    enabled = supportsTools && !platformData.disableAllTools && !platformData.disableRemoteTools,
                    isChecked = toolBindingState.readUrlEnabled,
                    onCheckedChange = settingViewModel::toggleReadUrl
                )
                PreferenceListSwitch(
                    modifier = Modifier.height(72.dp),
                    title = "Device location",
                    description = "Allow this AI profile to request the phone's current GPS location when needed.",
                    icon = Icons.Default.LocationOn,
                    enabled = !isFreePlatform && supportsTools && platformData.enabled && !platformData.disableAllTools && !platformData.disableLocalTools,
                    isChecked = !isFreePlatform && toolBindingState.deviceLocationEnabled,
                    onCheckedChange = { enabled ->
                        if (!enabled) {
                            settingViewModel.toggleDeviceLocation(false)
                        } else {
                            val fineGranted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            val coarseGranted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            if (fineGranted || coarseGranted) {
                                settingViewModel.toggleDeviceLocation(true)
                            } else {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        }
                    }
                )
                SettingItem(
                    modifier = Modifier.height(64.dp),
                    title = stringResource(R.string.mcp_tools),
                    description = if (platformData.excludesMemory()) stringResource(R.string.free_ai_memory_off) else "${toolBindingState.selectedMcpTools.size} assigned",
                    enabled = !platformData.excludesMemory() && supportsTools && platformData.enabled && !platformData.disableAllTools && !platformData.disableRemoteTools,
                    onItemClick = {
                        val needsPermission = toolBindingState.mcpConnections.any { connection ->
                            connection.endpointUrl?.let(::requiresLocalNetworkAccess) == true
                        }
                        if (needsPermission &&
                            Build.VERSION.SDK_INT >= 37 &&
                            ContextCompat.checkSelfPermission(context, PERMISSION_ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
                        ) {
                            openMcpToolsAfterPermission = true
                            localNetworkPermissionLauncher.launch(PERMISSION_ACCESS_LOCAL_NETWORK)
                        } else {
                            onNavigateToMcpTools()
                        }
                    },
                    showTrailingIcon = true,
                    showLeadingIcon = false
                )

                // Advanced Settings: Maximum Tool Calls
                if (supportsTools) PlatformMaxToolCallsSettingHost(settingViewModel)

                PlatformNameDialog(dialogState, platformData.name, settingViewModel)
                if (dialogState.isLabelsDialogOpen) {
                    ProfileLabelEditorDialog(
                        currentLabels = profileLabels,
                        reusableLabels = reusableLabels,
                        onDismiss = settingViewModel::closeLabelsDialog,
                        onSave = settingViewModel::saveProfileLabels
                    )
                }
                if (!isLocalPlatform && !isFreePlatform) {
                    APIUrlDialog(dialogState, platformData.apiUrl, settingViewModel)
                    APIKeyDialog(dialogState, platformData.token, settingViewModel)
                    if (platformData.compatibleType == ClientType.OPENROUTER && dialogState.isApiModelDialogOpen) {
                        OpenRouterModelPickerDialog(
                            currentModel = platformData.model,
                            onDismiss = settingViewModel::closeApiModelDialog,
                            onModelSelected = settingViewModel::updateApiModel
                        )
                    } else if (platformData.compatibleType == ClientType.LLAMA && dialogState.isApiModelDialogOpen) {
                        LlamaModelPickerDialog(
                            baseUrl = platformData.apiUrl,
                            currentModel = platformData.model,
                            onDismiss = settingViewModel::closeApiModelDialog,
                            onModelSelected = settingViewModel::updateApiModel
                        )
                    } else {
                        ModelDialog(dialogState, platformData.model, settingViewModel)
                    }
                    TimeoutDialog(dialogState, platformData.timeout, settingViewModel)
                } else if (isLocalPlatform) {
                    LocalModelDialog(
                        dialogState = dialogState,
                        selectedCatalogEntryId = platformData.model,
                        models = downloadedLocalModels,
                        onNavigateToLocalModels = onNavigateToLocalModels,
                        settingViewModel = settingViewModel
                    )
                    TopKDialog(dialogState, platformData.topK, settingViewModel)
                    MaxTokensDialog(dialogState, platformData.maxTokens, settingViewModel)
                    AcceleratorDialog(dialogState, platformData.accelerator, acceleratorOptions, settingViewModel)
                }
                SystemPromptDialog(dialogState, platformData.systemPrompt ?: "", settingViewModel)
                GeminiSafetySettingsDialog(dialogState, platformData, settingViewModel)
                OpenRouterAdvancedSettingsDialog(dialogState, platformData.openRouterRouting, settingViewModel)
                OllamaAdvancedSettingsDialog(dialogState, platformData.ollamaOptions, settingViewModel)
                LlamaAdvancedSettingsDialog(dialogState, settingViewModel)
                DeletePlatformDialog(dialogState, settingViewModel)
                SearchBackendDialog(toolBindingState, settingViewModel)
                LegacyMcpToolsDialog(toolBindingState, settingViewModel)

                if (showBatchUrlDialog) {
                    var batchUrlInput by remember { mutableStateOf(platformData.batchApiUrl.orEmpty()) }
                    AlertDialog(
                        onDismissRequest = { showBatchUrlDialog = false },
                        title = { Text(stringResource(R.string.batch_api_url)) },
                        text = {
                            OutlinedTextField(
                                value = batchUrlInput,
                                onValueChange = { batchUrlInput = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val url = batchUrlInput.trim().takeIf { it.isNotBlank() }
                                settingViewModel.updatePlatform(platformData.copy(batchApiUrl = url))
                                showBatchUrlDialog = false
                            }) {
                                Text(stringResource(R.string.confirm))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showBatchUrlDialog = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    )
                }

                toolBindingState.errorMessage?.let { message ->
                    AlertDialog(
                        title = { Text(stringResource(R.string.error)) },
                        text = { Text(message) },
                        onDismissRequest = settingViewModel::clearToolError,
                        confirmButton = {
                            TextButton(onClick = settingViewModel::clearToolError) {
                                Text(stringResource(R.string.close))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp)
    )
}

@Composable
private fun SearchBackendDialog(
    toolBindingState: PlatformSettingViewModel.ToolBindingState,
    settingViewModel: PlatformSettingViewModel
) {
    if (toolBindingState.isSearchBackendDialogOpen) {
        var selected by remember(toolBindingState.selectedSearchConnectionUids) { mutableStateOf(toolBindingState.selectedSearchConnectionUids) }
        AlertDialog(
            icon = { Icon(dev.chungjungsoo.gptmobile.presentation.ui.chat.toolActivityIcon("web_search"), null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Search engines") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("One search, more perspectives. Each query searches all enabled engines and combines unique sources.", style = MaterialTheme.typography.bodyMedium)
                    Text("Built-in web search is included. Selected MCP web-search tools participate automatically; connection permissions still apply.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    toolBindingState.searchConnections.forEach { connection ->
                        PreferenceListSwitch(
                            title = connection.name,
                            icon = dev.chungjungsoo.gptmobile.presentation.ui.chat.toolActivityIcon("web_search"),
                            description = connection.type.lowercase().replaceFirstChar { it.uppercase() },
                            isChecked = connection.connectionUid in selected,
                            onCheckedChange = { enabled -> selected = if (enabled) selected + connection.connectionUid else selected - connection.connectionUid }
                        )
                    }
                    if (toolBindingState.searchConnections.isEmpty()) Text("Add Exa, Firecrawl, or Perplexity in Tool connections.")
                    Row {
                        TextButton(onClick = { selected = toolBindingState.searchConnections.map { it.connectionUid }.toSet() }) { Text("Select all") }
                        TextButton(onClick = { selected = emptySet() }) { Text("Built-in only") }
                    }
                    toolBindingState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            onDismissRequest = settingViewModel::closeSearchBackendDialog,
            confirmButton = { TextButton(onClick = { settingViewModel.selectSearchBackends(selected) }) { Text("Save engines") } },
            dismissButton = { TextButton(onClick = settingViewModel::closeSearchBackendDialog) { Text("Cancel") } }
        )
    }
}

@Composable
private fun LegacyMcpToolsDialog(
    toolBindingState: PlatformSettingViewModel.ToolBindingState,
    settingViewModel: PlatformSettingViewModel
) {
    if (!toolBindingState.isMcpToolsDialogOpen) return
    AlertDialog(
        title = { Text(stringResource(R.string.mcp_server)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when {
                    toolBindingState.mcpConnections.isEmpty() -> Text(stringResource(R.string.no_tool_connections))

                    toolBindingState.isMcpToolsLoading -> CircularProgressIndicator(
                        modifier = Modifier
                            .padding(16.dp)
                            .semantics { contentDescription = "Discovering MCP tools" }
                    )

                    toolBindingState.mcpToolOptions.isEmpty() -> Text(stringResource(R.string.no_tool_connections))

                    else -> toolBindingState.mcpToolOptions.forEach { option ->
                        val selected = toolBindingState.pendingMcpTools.any {
                            it.connectionUid == option.connectionUid && it.toolName == option.toolName
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = selected,
                                    onValueChange = { settingViewModel.toggleMcpTool(option.connectionUid, option.toolName) }
                                )
                                .semantics { contentDescription = "${option.connectionName} ${option.toolName}" }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = selected, onCheckedChange = null)
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(
                                    text = option.toolName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "${option.connectionName} • ${option.modelToolName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                option.description?.takeIf(String::isNotBlank)?.let { description ->
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        onDismissRequest = settingViewModel::closeMcpToolsDialog,
        confirmButton = {
            TextButton(
                onClick = settingViewModel::saveMcpTools,
                enabled = !toolBindingState.isMcpToolsLoading
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = settingViewModel::closeMcpToolsDialog) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformTopAppBar(
    title: String,
    onNavigationClick: () -> Unit,
    onDeleteClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior
) {
    var expanded by remember { mutableStateOf(false) }

    LargeTopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigationClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.arrow_icon)
                )
            }
        },
        actions = {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.options)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = {
                        expanded = false
                        onDeleteClick()
                    }
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
fun PreferenceSwitchWithContainer(
    title: String,
    isChecked: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .toggleable(
                value = isChecked,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Switch,
                onValueChange = { onClick() }
            ),
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        },
        trailingContent = {
            Switch(
                checked = isChecked,
                onCheckedChange = null,
                thumbContent = {
                    if (isChecked) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                }
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}

@Composable
fun PreferenceListSwitch(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    icon: ImageVector,
    enabled: Boolean = true,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = isChecked,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            ),
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        },
        supportingContent = description?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
        },
        trailingContent = {
            Switch(
                checked = isChecked,
                onCheckedChange = null,
                enabled = enabled,
                thumbContent = {
                    if (isChecked) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                }
            )
        }
    )
}

@Composable
fun ExtendedThinkingSwitch(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    PreferenceListSwitch(
        modifier = modifier,
        title = stringResource(R.string.extended_thinking),
        description = stringResource(R.string.extended_thinking_description),
        icon = ImageVector.vectorResource(id = R.drawable.ic_extended_thinking),
        enabled = enabled,
        isChecked = isChecked,
        onCheckedChange = onCheckedChange
    )
}

@Composable
private fun acceleratorLabel(accelerator: String?): String = when (LocalAccelerators.normalize(accelerator)) {
    LocalAccelerators.GPU -> stringResource(R.string.accelerator_gpu)
    LocalAccelerators.NPU -> stringResource(R.string.accelerator_npu)
    else -> stringResource(R.string.accelerator_cpu)
}
