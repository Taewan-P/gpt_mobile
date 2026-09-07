package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.localruntime.LocalAccelerators
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.presentation.common.EmptyErrorState
import dev.chungjungsoo.gptmobile.presentation.common.RadioItem
import dev.chungjungsoo.gptmobile.presentation.common.SettingItem
import dev.chungjungsoo.gptmobile.presentation.common.SettingsSection
import dev.chungjungsoo.gptmobile.presentation.theme.defaultSpatialSpec
import dev.chungjungsoo.gptmobile.presentation.theme.fastEffectsSpec
import dev.chungjungsoo.gptmobile.util.formatPlatformTimeout
import dev.chungjungsoo.gptmobile.util.pinnedExitUntilCollapsedScrollBehavior
import dev.chungjungsoo.gptmobile.util.requiresLocalNetworkAccess

internal fun apiKeySummary(
    token: String?,
    set: String,
    notSet: String
): String = if (token.isNullOrEmpty()) notSet else set

internal fun advancedGenerationSummary(values: List<Pair<String, String?>>): String = values.mapNotNull { (label, value) ->
    value?.let { "$label $it" }
}.joinToString(" · ")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformSettingScreen(
    modifier: Modifier = Modifier,
    settingViewModel: PlatformSettingViewModel = hiltViewModel(),
    onNavigationClick: () -> Unit,
    onNavigateToLocalModels: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = pinnedExitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward }
    )
    val platform by settingViewModel.platformState.collectAsStateWithLifecycle()
    val platformLoadState by settingViewModel.platformLoadState.collectAsStateWithLifecycle()
    val dialogState by settingViewModel.dialogState.collectAsStateWithLifecycle()
    val isDeleted by settingViewModel.isDeleted.collectAsStateWithLifecycle()
    val toolBindingState by settingViewModel.toolBindingState.collectAsStateWithLifecycle()
    val downloadedLocalModels by settingViewModel.downloadedLocalModels.collectAsStateWithLifecycle()
    val acceleratorOptions by settingViewModel.acceleratorOptions.collectAsStateWithLifecycle()
    val userMessage by settingViewModel.userMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var openMcpToolsAfterPermission by remember { mutableStateOf(false) }
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

    Scaffold(
        modifier = modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PlatformTopAppBar(
                title = platform?.name ?: stringResource(R.string.platform_settings),
                onNavigationClick = onNavigationClick,
                onDeleteClick = settingViewModel::openDeleteDialog,
                showDeleteAction = platformLoadState == PlatformLoadState.Loaded,
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        when (val loadState = platformLoadState) {
            PlatformLoadState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.loading_platform),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }

            PlatformLoadState.NotFound -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyErrorState(
                        title = stringResource(R.string.platform_not_found),
                        description = stringResource(R.string.platform_not_found_description),
                        primaryActionLabel = stringResource(R.string.retry),
                        onPrimaryAction = settingViewModel::retryLoadPlatform,
                        secondaryActionLabel = stringResource(R.string.go_back),
                        onSecondaryAction = onNavigationClick
                    )
                }
            }

            is PlatformLoadState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyErrorState(
                        title = stringResource(R.string.platform_load_error),
                        description = loadState.message,
                        primaryActionLabel = stringResource(R.string.retry),
                        onPrimaryAction = settingViewModel::retryLoadPlatform,
                        secondaryActionLabel = stringResource(R.string.go_back),
                        onSecondaryAction = onNavigationClick,
                        isError = true
                    )
                }
            }

            PlatformLoadState.Loaded -> platform?.let { platformData ->
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .verticalScroll(scrollState)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    val isLocalPlatform = platformData.compatibleType == ClientType.LITERT_LM
                    val isReasoningDisabled = platformData.compatibleType == ClientType.OPENAI && platformData.reasoning
                    val notSetText = stringResource(R.string.not_set)
                    var isAdvancedExpanded by rememberSaveable(platformData.uid) { mutableStateOf(false) }

                    SettingsSection(title = stringResource(R.string.connection)) {
                        PreferenceSwitchWithContainer(
                            title = stringResource(if (isLocalPlatform) R.string.enable_platform else R.string.enable_api),
                            isChecked = platformData.enabled
                        ) { settingViewModel.toggleEnabled() }
                        SettingItem(
                            modifier = Modifier.heightIn(min = 64.dp),
                            title = stringResource(R.string.platform_name),
                            description = platformData.name,
                            enabled = platformData.enabled,
                            onItemClick = settingViewModel::openPlatformNameDialog,
                            showTrailingIcon = true,
                            showLeadingIcon = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Label,
                                    contentDescription = null
                                )
                            }
                        )
                        if (!isLocalPlatform) {
                            SettingItem(
                                modifier = Modifier.heightIn(min = 64.dp),
                                title = stringResource(R.string.api_url),
                                description = platformData.apiUrl,
                                enabled = platformData.enabled,
                                onItemClick = settingViewModel::openApiUrlDialog,
                                showTrailingIcon = true,
                                showLeadingIcon = true,
                                leadingIcon = {
                                    Icon(
                                        ImageVector.vectorResource(id = R.drawable.ic_link),
                                        contentDescription = null
                                    )
                                }
                            )
                            SettingItem(
                                modifier = Modifier.heightIn(min = 64.dp),
                                title = stringResource(R.string.api_key),
                                description = apiKeySummary(
                                    token = platformData.token,
                                    set = stringResource(R.string.api_key_set),
                                    notSet = stringResource(R.string.token_not_set)
                                ),
                                enabled = platformData.enabled,
                                onItemClick = settingViewModel::openApiTokenDialog,
                                showTrailingIcon = true,
                                showLeadingIcon = true,
                                leadingIcon = {
                                    Icon(
                                        ImageVector.vectorResource(id = R.drawable.ic_key),
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }

                    SettingsSection(title = stringResource(R.string.model_and_generation)) {
                        val modelDescription = downloadedLocalModels
                            .firstOrNull { it.catalogEntryId == platformData.model }
                            ?.displayName
                            ?: platformData.model
                        SettingItem(
                            modifier = Modifier.heightIn(min = 64.dp),
                            title = stringResource(R.string.api_model),
                            description = modelDescription,
                            enabled = platformData.enabled,
                            onItemClick = settingViewModel::openApiModelDialog,
                            showTrailingIcon = true,
                            showLeadingIcon = true,
                            leadingIcon = {
                                Icon(
                                    ImageVector.vectorResource(id = R.drawable.ic_model),
                                    contentDescription = null
                                )
                            }
                        )
                        if (!isLocalPlatform) {
                            ExtendedThinkingSwitch(
                                modifier = Modifier.heightIn(min = 64.dp),
                                enabled = platformData.enabled,
                                isChecked = platformData.reasoning,
                                onCheckedChange = { settingViewModel.toggleReasoning() }
                            )
                        }
                        SettingItem(
                            modifier = Modifier.heightIn(min = 64.dp),
                            title = stringResource(R.string.advanced_generation),
                            description = if (isAdvancedExpanded) {
                                stringResource(R.string.hide)
                            } else {
                                advancedGenerationSummary(
                                    listOf(
                                        stringResource(R.string.temperature) to (platformData.temperature?.toString() ?: notSetText),
                                        stringResource(R.string.top_p) to (platformData.topP?.toString() ?: notSetText),
                                        stringResource(R.string.top_k) to if (isLocalPlatform) platformData.topK?.toString() ?: notSetText else null,
                                        stringResource(R.string.max_tokens) to if (isLocalPlatform) platformData.maxTokens?.toString() ?: notSetText else null
                                    )
                                )
                            },
                            enabled = platformData.enabled,
                            onItemClick = { isAdvancedExpanded = !isAdvancedExpanded },
                            showTrailingIcon = true,
                            showLeadingIcon = false
                        )
                        AnimatedVisibility(
                            visible = isAdvancedExpanded,
                            enter = expandVertically(animationSpec = defaultSpatialSpec()) +
                                fadeIn(animationSpec = fastEffectsSpec()),
                            exit = shrinkVertically(animationSpec = defaultSpatialSpec()) +
                                fadeOut(animationSpec = fastEffectsSpec())
                        ) {
                            Column {
                                SettingItem(
                                    modifier = Modifier.heightIn(min = 64.dp),
                                    title = stringResource(R.string.temperature),
                                    description = platformData.temperature?.toString() ?: notSetText,
                                    enabled = platformData.enabled && !isReasoningDisabled,
                                    onItemClick = settingViewModel::openTemperatureDialog,
                                    showTrailingIcon = true,
                                    showLeadingIcon = true,
                                    leadingIcon = {
                                        Icon(
                                            ImageVector.vectorResource(id = R.drawable.ic_temperature),
                                            contentDescription = null
                                        )
                                    }
                                )
                                SettingItem(
                                    modifier = Modifier.heightIn(min = 64.dp),
                                    title = stringResource(R.string.top_p),
                                    description = platformData.topP?.toString() ?: notSetText,
                                    enabled = platformData.enabled && !isReasoningDisabled,
                                    onItemClick = settingViewModel::openTopPDialog,
                                    showTrailingIcon = true,
                                    showLeadingIcon = true,
                                    leadingIcon = {
                                        Icon(
                                            ImageVector.vectorResource(id = R.drawable.ic_chart),
                                            contentDescription = null
                                        )
                                    }
                                )
                                if (isLocalPlatform) {
                                    SettingItem(
                                        modifier = Modifier.heightIn(min = 64.dp),
                                        title = stringResource(R.string.top_k),
                                        description = platformData.topK?.toString() ?: notSetText,
                                        enabled = platformData.enabled,
                                        onItemClick = settingViewModel::openTopKDialog,
                                        showTrailingIcon = true,
                                        showLeadingIcon = true,
                                        leadingIcon = {
                                            Icon(
                                                ImageVector.vectorResource(id = R.drawable.ic_chart),
                                                contentDescription = null
                                            )
                                        }
                                    )
                                    SettingItem(
                                        modifier = Modifier.heightIn(min = 64.dp),
                                        title = stringResource(R.string.max_tokens),
                                        description = platformData.maxTokens?.toString() ?: notSetText,
                                        enabled = platformData.enabled,
                                        onItemClick = settingViewModel::openMaxTokensDialog,
                                        showTrailingIcon = true,
                                        showLeadingIcon = true,
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Numbers,
                                                contentDescription = null
                                            )
                                        }
                                    )
                                }
                                if (platformData.compatibleType == ClientType.GOOGLE) {
                                    SettingItem(
                                        modifier = Modifier.heightIn(min = 64.dp),
                                        title = stringResource(R.string.gemini_safety_settings),
                                        description = stringResource(R.string.gemini_safety_settings_description),
                                        enabled = platformData.enabled,
                                        onItemClick = settingViewModel::openGeminiSafetyDialog,
                                        showTrailingIcon = true,
                                        showLeadingIcon = true,
                                        leadingIcon = {
                                            Icon(
                                                ImageVector.vectorResource(id = R.drawable.ic_info),
                                                contentDescription = null
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    SettingsSection(title = stringResource(R.string.instructions)) {
                        SettingItem(
                            modifier = Modifier.heightIn(min = 64.dp),
                            title = stringResource(R.string.system_prompt),
                            description = platformData.systemPrompt,
                            enabled = platformData.enabled,
                            onItemClick = settingViewModel::openSystemPromptDialog,
                            showTrailingIcon = true,
                            showLeadingIcon = true,
                            leadingIcon = {
                                Icon(
                                    ImageVector.vectorResource(id = R.drawable.ic_instructions),
                                    contentDescription = null
                                )
                            }
                        )
                    }

                    SettingsSection(title = stringResource(R.string.runtime)) {
                        if (isLocalPlatform) {
                            SettingItem(
                                modifier = Modifier.heightIn(min = 64.dp),
                                title = stringResource(R.string.accelerator),
                                description = acceleratorLabel(platformData.accelerator),
                                enabled = platformData.enabled && acceleratorOptions.isNotEmpty(),
                                onItemClick = settingViewModel::openAcceleratorDialog,
                                showTrailingIcon = true,
                                showLeadingIcon = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Speed,
                                        contentDescription = null
                                    )
                                }
                            )
                        } else {
                            SettingItem(
                                modifier = Modifier.heightIn(min = 64.dp),
                                title = stringResource(R.string.timeout),
                                description = formatPlatformTimeout(platformData.timeout, stringResource(R.string.off)),
                                enabled = platformData.enabled,
                                onItemClick = settingViewModel::openTimeoutDialog,
                                showTrailingIcon = true,
                                showLeadingIcon = true,
                                leadingIcon = {
                                    Icon(
                                        ImageVector.vectorResource(id = R.drawable.ic_info),
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }

                    SettingsSection(title = stringResource(R.string.tools)) {
                        SettingItem(
                            modifier = Modifier.heightIn(min = 64.dp),
                            title = stringResource(R.string.search_backend),
                            description = toolBindingState.searchConnections.firstOrNull {
                                it.connectionUid == toolBindingState.selectedSearchConnectionUid
                            }?.name ?: stringResource(R.string.none),
                            enabled = platformData.enabled,
                            onItemClick = settingViewModel::openSearchBackendDialog,
                            showTrailingIcon = true,
                            showLeadingIcon = false
                        )
                        PreferenceListSwitch(
                            modifier = Modifier.heightIn(min = 64.dp),
                            title = stringResource(R.string.read_url),
                            icon = ImageVector.vectorResource(id = R.drawable.ic_link),
                            enabled = platformData.enabled,
                            isChecked = toolBindingState.readUrlEnabled,
                            onCheckedChange = settingViewModel::toggleReadUrl
                        )
                        SettingItem(
                            modifier = Modifier.heightIn(min = 64.dp),
                            title = stringResource(R.string.mcp_tools),
                            description = stringResource(R.string.mcp_tools_assigned, toolBindingState.selectedMcpTools.size),
                            enabled = platformData.enabled,
                            onItemClick = {
                                val needsPermission = toolBindingState.mcpConnections.any { connection ->
                                    connection.endpointUrl?.let(::requiresLocalNetworkAccess) == true
                                }
                                if (needsPermission &&
                                    Build.VERSION.SDK_INT >= 37 &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_LOCAL_NETWORK
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    openMcpToolsAfterPermission = true
                                    localNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                                } else {
                                    settingViewModel.openMcpToolsDialog()
                                }
                            },
                            showTrailingIcon = true,
                            showLeadingIcon = false
                        )
                    }

                    PlatformNameDialog(dialogState, platformData.name, settingViewModel)
                    if (!isLocalPlatform) {
                        APIUrlDialog(dialogState, platformData.apiUrl, settingViewModel)
                        APIKeyDialog(dialogState, settingViewModel)
                        ModelDialog(dialogState, platformData.model, settingViewModel)
                        TimeoutDialog(dialogState, platformData.timeout, settingViewModel)
                    } else {
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
                    TemperatureDialog(dialogState, platformData.temperature, settingViewModel)
                    TopPDialog(dialogState, platformData.topP, settingViewModel)
                    SystemPromptDialog(dialogState, platformData.systemPrompt ?: "", settingViewModel)
                    GeminiSafetySettingsDialog(dialogState, platformData, settingViewModel)
                    DeletePlatformDialog(dialogState, settingViewModel)
                    SearchBackendDialog(toolBindingState, settingViewModel)
                    McpToolsDialog(toolBindingState, settingViewModel)
                    if (!toolBindingState.isMcpToolsDialogOpen) {
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
            } ?: EmptyErrorState(
                title = stringResource(R.string.platform_not_found),
                description = stringResource(R.string.platform_not_found_description),
                modifier = Modifier.padding(innerPadding),
                primaryActionLabel = stringResource(R.string.retry),
                onPrimaryAction = settingViewModel::retryLoadPlatform,
                secondaryActionLabel = stringResource(R.string.go_back),
                onSecondaryAction = onNavigationClick
            )
        }
    }
}

@Composable
private fun McpToolsDialog(
    toolBindingState: PlatformSettingViewModel.ToolBindingState,
    settingViewModel: PlatformSettingViewModel
) {
    if (!toolBindingState.isMcpToolsDialogOpen) return
    AlertDialog(
        title = { Text(stringResource(R.string.mcp_tools)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val discoveringMcpToolsDescription = stringResource(R.string.discovering_mcp_tools)
                when {
                    toolBindingState.mcpConnections.isEmpty() -> Text(stringResource(R.string.no_mcp_connections))

                    toolBindingState.isMcpToolsLoading -> CircularProgressIndicator(
                        modifier = Modifier
                            .padding(16.dp)
                            .semantics { contentDescription = discoveringMcpToolsDescription }
                    )

                    toolBindingState.mcpToolOptions.isEmpty() -> Text(stringResource(R.string.no_mcp_tools))

                    else -> toolBindingState.mcpToolOptions.forEach { option ->
                        val selected = toolBindingState.pendingMcpTools.any {
                            it.connectionUid == option.connectionUid && it.toolName == option.toolName
                        }
                        val optionDescription = stringResource(
                            R.string.mcp_tool_option_content_description,
                            option.connectionName,
                            option.toolName
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = selected,
                                    onValueChange = { settingViewModel.toggleMcpTool(option.connectionUid, option.toolName) }
                                )
                                .semantics { contentDescription = optionDescription }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = selected, onCheckedChange = null)
                            Column(Modifier.padding(start = 8.dp)) {
                                Text("${option.connectionName} · ${option.toolName}")
                                Text(
                                    option.description ?: option.modelToolName,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                toolBindingState.errorMessage?.let { message ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics {
                                    error(message)
                                    liveRegion = LiveRegionMode.Assertive
                                }
                        )
                        TextButton(onClick = settingViewModel::openMcpToolsDialog) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
        },
        onDismissRequest = settingViewModel::closeMcpToolsDialog,
        confirmButton = {
            TextButton(
                enabled = !toolBindingState.isMcpToolsLoading,
                onClick = settingViewModel::saveMcpTools
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = settingViewModel::closeMcpToolsDialog) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun SearchBackendDialog(
    toolBindingState: PlatformSettingViewModel.ToolBindingState,
    settingViewModel: PlatformSettingViewModel
) {
    if (toolBindingState.isSearchBackendDialogOpen) {
        AlertDialog(
            title = { Text(stringResource(R.string.search_backend)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    val noneSearchBackendDescription = stringResource(
                        R.string.search_backend_option_content_description,
                        stringResource(R.string.none)
                    )
                    RadioItem(
                        modifier = Modifier.semantics { contentDescription = noneSearchBackendDescription },
                        title = stringResource(R.string.none),
                        description = null,
                        value = "",
                        selected = toolBindingState.selectedSearchConnectionUid == null
                    ) {
                        settingViewModel.selectSearchBackend(null)
                    }
                    toolBindingState.searchConnections.forEach { connection ->
                        val searchBackendDescription = stringResource(
                            R.string.search_backend_option_content_description,
                            connection.name
                        )
                        RadioItem(
                            modifier = Modifier.semantics { contentDescription = searchBackendDescription },
                            title = connection.name,
                            description = connection.alias,
                            value = connection.connectionUid,
                            selected = toolBindingState.selectedSearchConnectionUid == connection.connectionUid
                        ) {
                            settingViewModel.selectSearchBackend(connection.connectionUid)
                        }
                    }
                }
            },
            onDismissRequest = settingViewModel::closeSearchBackendDialog,
            confirmButton = {
                TextButton(onClick = settingViewModel::closeSearchBackendDialog) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformTopAppBar(
    title: String,
    onNavigationClick: () -> Unit,
    onDeleteClick: () -> Unit,
    showDeleteAction: Boolean,
    scrollBehavior: TopAppBarScrollBehavior
) {
    var showMenu by remember { mutableStateOf(false) }

    LargeTopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        ),
        title = {
            Text(
                modifier = Modifier.padding(4.dp),
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(
                modifier = Modifier.padding(4.dp),
                onClick = onNavigationClick
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
            }
        },
        actions = {
            if (showDeleteAction) {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.more_options)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_platform)) },
                        onClick = {
                            showMenu = false
                            onDeleteClick()
                        }
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun acceleratorLabel(accelerator: String?): String = when (accelerator?.lowercase()) {
    LocalAccelerators.GPU -> stringResource(R.string.accelerator_gpu)
    LocalAccelerators.CPU -> stringResource(R.string.accelerator_cpu)
    LocalAccelerators.NPU -> stringResource(R.string.accelerator_npu)
    else -> stringResource(R.string.not_set)
}

@Composable
fun ExtendedThinkingSwitch(
    modifier: Modifier,
    enabled: Boolean,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    PreferenceListSwitch(
        modifier = modifier,
        title = stringResource(R.string.extended_thinking),
        description = stringResource(R.string.extended_thinking_description),
        icon = ImageVector.vectorResource(id = R.drawable.ic_model),
        enabled = enabled,
        isChecked = isChecked,
        onCheckedChange = onCheckedChange
    )
}

@Composable
private fun PreferenceListSwitch(
    modifier: Modifier,
    title: String,
    description: String? = null,
    icon: ImageVector,
    enabled: Boolean,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = ListItemDefaults.colors()

    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { }
            .toggleable(
                value = isChecked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(horizontal = 8.dp),
        headlineContent = {
            Text(
                text = title,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = description?.let {
            {
                Text(
                    text = description,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null
            )
        },
        trailingContent = {
            Switch(
                checked = isChecked,
                onCheckedChange = null,
                enabled = enabled
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = if (enabled) colors.headlineColor else colors.disabledHeadlineColor,
            supportingColor = if (enabled) colors.supportingTextColor else colors.disabledHeadlineColor,
            leadingIconColor = if (enabled) colors.leadingIconColor else colors.disabledLeadingIconColor,
            trailingIconColor = if (enabled) colors.trailingIconColor else colors.disabledTrailingIconColor
        )
    )
}

@Composable
fun PreferenceSwitchWithContainer(
    title: String,
    icon: ImageVector? = null,
    isChecked: Boolean,
    onClick: () -> Unit
) {
    val thumbContent: (@Composable () -> Unit)? = remember(isChecked) {
        if (isChecked) {
            {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize)
                )
            }
        } else {
            null
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                MaterialTheme.colorScheme.primaryContainer
            )
            .toggleable(
                value = isChecked,
                onValueChange = { onClick() },
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Switch
            )
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 8.dp, end = 16.dp)
                    .size(24.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (icon == null) 12.dp else 0.dp, end = 12.dp)
        ) {
            Text(
                text = title,
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Switch(
            checked = isChecked,
            interactionSource = interactionSource,
            onCheckedChange = null,
            modifier = Modifier.padding(start = 12.dp, end = 6.dp),
            thumbContent = thumbContent
        )
    }
}
