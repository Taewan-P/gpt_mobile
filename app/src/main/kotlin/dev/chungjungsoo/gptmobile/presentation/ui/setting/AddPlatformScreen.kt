package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ProviderConnection
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.FreeAiProvider
import dev.chungjungsoo.gptmobile.data.model.ProfileLabel
import dev.chungjungsoo.gptmobile.data.model.SamplingCreativity
import dev.chungjungsoo.gptmobile.data.model.encodeProfileLabels
import dev.chungjungsoo.gptmobile.data.network.ApiCredentialRotator
import dev.chungjungsoo.gptmobile.data.ollama.OllamaOptions
import dev.chungjungsoo.gptmobile.presentation.common.BeveledProfileLabel
import dev.chungjungsoo.gptmobile.presentation.common.DestinationCard
import dev.chungjungsoo.gptmobile.presentation.common.FreeProviderPicker
import dev.chungjungsoo.gptmobile.presentation.common.ProfileLabelEditorDialog
import dev.chungjungsoo.gptmobile.presentation.ui.setup.LocalModelCatalogPicker
import dev.chungjungsoo.gptmobile.util.pinnedExitUntilCollapsedScrollBehavior
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private enum class AddPlatformStep { API_TYPE, DETAILS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlatformScreen(
    modifier: Modifier = Modifier,
    viewModel: AddPlatformViewModel = hiltViewModel(),
    onNavigationClick: () -> Unit,
    savedConnections: List<ProviderConnection> = emptyList(),
    reusableLabels: List<ProfileLabel> = emptyList(),
    onSave: (PlatformV2, ProviderConnection?, String?) -> Unit,
    onNavigateToLocalModels: () -> Unit = {}
) {
    var step by remember { mutableStateOf(AddPlatformStep.API_TYPE) }
    var selectedClientType by remember { mutableStateOf<ClientType?>(null) }
    var platformName by remember { mutableStateOf("") }
    var apiUrl by remember { mutableStateOf("") }
    val apiTokens = remember { mutableStateListOf("") }
    var model by remember { mutableStateOf("") }
    var isReasoningEnabled by remember { mutableStateOf(false) }
    var selectedConnectionUid by remember { mutableStateOf<String?>(null) }
    var createNewConnection by remember { mutableStateOf(true) }
    var connectionName by remember { mutableStateOf("") }
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var systemPrompt by remember { mutableStateOf(ModelConstants.DEFAULT_PROMPT) }
    var creativity by remember { mutableStateOf(SamplingCreativity.DEFAULT) }
    var profileLabels by remember { mutableStateOf<List<ProfileLabel>>(emptyList()) }
    var showLabelsDialog by remember { mutableStateOf(false) }
    var maxToolCallsText by remember { mutableStateOf("50") }
    var showSuggestedModels by remember { mutableStateOf(false) }
    var showOpenRouterPicker by remember { mutableStateOf(false) }
    var showLlamaPicker by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scrollBehavior = pinnedExitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward }
    )
    val catalogModels by viewModel.catalogLocalModels.collectAsStateWithLifecycle()
    val downloadState by viewModel.localModelDownloadState.collectAsStateWithLifecycle()
    val selectedLocalModelId by viewModel.selectedCatalogEntryId.collectAsStateWithLifecycle()
    val canSave by viewModel.canSave.collectAsStateWithLifecycle()
    val isWaitingForDownload by viewModel.isWaitingForDownload.collectAsStateWithLifecycle()

    val isLocalPlatform = selectedClientType == ClientType.LITERT_LM
    val title = stringResource(if (step == AddPlatformStep.API_TYPE) R.string.choose_platform_type else R.string.platform_details)
    val hasProviderConnection = selectedConnectionUid != null || (createNewConnection && apiUrl.isNotBlank())
    val isSaveEnabled = (selectedClientType != ClientType.FREE || FreeAiProvider.fromApiUrl(apiUrl)?.isAvailable == true) &&
        platformName.isNotBlank() &&
        if (isLocalPlatform) {
            canSave
        } else {
            model.isNotBlank() && hasProviderConnection
        }
    val navigateBack = { if (step == AddPlatformStep.DETAILS) step = AddPlatformStep.API_TYPE else onNavigationClick() }
    BackHandler(enabled = step == AddPlatformStep.DETAILS) { step = AddPlatformStep.API_TYPE }

    Scaffold(
        modifier = modifier,
        topBar = {
            AddPlatformTopBar(
                title = title,
                scrollBehavior = scrollBehavior,
                actionLabel = if (step == AddPlatformStep.DETAILS) stringResource(R.string.save) else null,
                isActionEnabled = isSaveEnabled,
                onNavigationClick = navigateBack,
                onActionClick = {
                    val clientType = selectedClientType ?: return@AddPlatformTopBar
                    val selectedModel = if (clientType == ClientType.LITERT_LM) {
                        selectedLocalModelId.trim()
                    } else {
                        model.trim()
                    }
                    if (clientType == ClientType.LITERT_LM && !viewModel.canSaveLocalModel()) return@AddPlatformTopBar
                    val defaults = if (clientType == ClientType.LITERT_LM) {
                        viewModel.defaultsFor(selectedModel)
                    } else {
                        null
                    }
                    val formattedApiKey = ApiCredentialRotator.formatKeys(apiTokens.toList())
                    val defaultOllamaOptions = if (clientType == ClientType.OLLAMA) {
                        Json.encodeToString(OllamaOptions.createDefault())
                    } else {
                        null
                    }
                    val sampling = SamplingCreativity.toSampling(creativity)
                    val newConnection = if (!isLocalPlatform && createNewConnection) {
                        ProviderConnection(
                            name = connectionName.trim().ifBlank {
                                "${ModelConstants.defaultPlatformName(clientType)} connection"
                            },
                            compatibleType = clientType,
                            apiUrl = apiUrl.trim()
                        )
                    } else {
                        null
                    }
                    val platform = PlatformV2(
                        name = platformName.trim(),
                        compatibleType = clientType,
                        enabled = if (clientType == ClientType.LITERT_LM) {
                            viewModel.shouldEnableLocalPlatform()
                        } else {
                            true
                        },
                        apiUrl = "",
                        token = null,
                        model = selectedModel,
                        temperature = sampling.temperature,
                        topP = sampling.topP,
                        topK = defaults?.topK,
                        maxTokens = defaults?.maxTokens,
                        accelerator = defaults?.accelerator,
                        systemPrompt = systemPrompt,
                        stream = true,
                        reasoning = isReasoningEnabled && clientType != ClientType.LITERT_LM,
                        timeout = 300,
                        maxToolCalls = maxToolCallsText.toIntOrNull()?.coerceAtLeast(1) ?: Int.MAX_VALUE,
                        ollamaOptions = defaultOllamaOptions,
                        labels = encodeProfileLabels(profileLabels),
                        providerConnectionUid = if (createNewConnection) null else selectedConnectionUid
                    ).let { profile ->
                        if (clientType == ClientType.FREE) FreeAiProvider.fromApiUrl(apiUrl)!!.applyTo(profile).copy(maxToolCalls = 50) else profile
                    }
                    apiTokens.clear()
                    apiTokens.add("")
                    onSave(
                        platform,
                        newConnection,
                        formattedApiKey.takeIf { newConnection != null && it.isNotEmpty() }
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .imePadding()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            if (step == AddPlatformStep.API_TYPE) {
                Text(
                    text = stringResource(R.string.choose_platform_type_step_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                (listOf(ClientType.FREE) + ClientType.entries.filter { it != ClientType.FREE }).forEach { clientType ->
                    DestinationCard(
                        title = getClientTypeName(clientType),
                        description = getClientTypeDescription(clientType),
                        onClick = {
                            selectedClientType = clientType
                            platformName = ModelConstants.defaultPlatformName(clientType)
                            model = ModelConstants.defaultModel(clientType)
                            val existingConnection = savedConnections.firstOrNull {
                                it.compatibleType == clientType
                            }
                            selectedConnectionUid = existingConnection?.uid
                            createNewConnection = existingConnection == null
                            connectionName = existingConnection?.name
                                ?: "${ModelConstants.defaultPlatformName(clientType)} connection"
                            apiUrl = existingConnection?.apiUrl ?: ModelConstants.defaultApiUrl(clientType)
                            if (clientType == ClientType.FREE) model = FreeAiProvider.fromApiUrl(apiUrl)?.model ?: FreeAiProvider.default.model
                            apiTokens.clear()
                            apiTokens.add("")
                            systemPrompt = ModelConstants.DEFAULT_PROMPT
                            creativity = SamplingCreativity.DEFAULT
                            profileLabels = emptyList()
                            maxToolCallsText = "50"
                            showAdvancedSettings = false
                            isReasoningEnabled = false
                            step = AddPlatformStep.DETAILS
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            } else {
                val clientType = selectedClientType ?: ClientType.OPENAI
                Text(
                    text = stringResource(R.string.platform_details_description, getClientTypeName(clientType)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = platformName,
                    readOnly = clientType == ClientType.FREE,
                    onValueChange = { platformName = it },
                    label = { Text(stringResource(R.string.platform_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.platform_name_supporting)) }
                )
                Text(
                    text = "Labels",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                )
                Text(
                    text = "Colored labels organize AI profiles and become filters when starting a conversation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (profileLabels.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        profileLabels.forEach { label ->
                            BeveledProfileLabel(label = label)
                        }
                    }
                }
                OutlinedButton(
                    onClick = { showLabelsDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(if (profileLabels.isEmpty()) "Add labels" else "Manage labels")
                }
                if (clientType == ClientType.FREE) {
                    FreeProviderPicker(
                        apiUrl = apiUrl,
                        onProviderSelected = { provider ->
                            platformName = "Free Models"
                            apiUrl = provider.apiUrl
                            model = provider.model
                            val saved = savedConnections.firstOrNull { it.compatibleType == ClientType.FREE && it.apiUrl == provider.apiUrl }
                            selectedConnectionUid = saved?.uid
                            createNewConnection = saved == null
                            connectionName = provider.displayName
                            apiTokens.clear()
                            apiTokens.add("")
                        },
                        modifier = Modifier.padding(top = 16.dp)
                    )
                } else if (clientType != ClientType.LITERT_LM) {
                    val providerConnections = savedConnections.filter { it.compatibleType == clientType }
                    Text(
                        text = stringResource(R.string.provider_connection),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                    )
                    Text(
                        text = stringResource(R.string.provider_connection_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    providerConnections.forEach { connection ->
                        DestinationCard(
                            title = connection.name,
                            description = buildString {
                                append(connection.apiUrl.ifBlank { stringResource(R.string.default_label) })
                                if (connection.hasCredential) {
                                    append(" • ")
                                    append(stringResource(R.string.credential_saved))
                                }
                            },
                            onClick = {
                                selectedConnectionUid = connection.uid
                                createNewConnection = false
                                connectionName = connection.name
                                apiUrl = connection.apiUrl
                                apiTokens.clear()
                                apiTokens.add("")
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    OutlinedButton(
                        onClick = {
                            createNewConnection = true
                            selectedConnectionUid = null
                            connectionName = "${ModelConstants.defaultPlatformName(clientType)} connection"
                            apiUrl = ModelConstants.defaultApiUrl(clientType)
                            apiTokens.clear()
                            apiTokens.add("")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text(stringResource(R.string.new_provider_connection))
                    }

                    if (createNewConnection) {
                        OutlinedTextField(
                            value = connectionName,
                            onValueChange = { connectionName = it },
                            label = { Text(stringResource(R.string.connection_name)) },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = apiUrl,
                            onValueChange = { apiUrl = it },
                            label = { Text(stringResource(R.string.api_url)) },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.multi_api_keys_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        apiTokens.forEachIndexed { index, tokenValue ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    modifier = Modifier.weight(1f),
                                    value = tokenValue,
                                    onValueChange = { apiTokens[index] = it },
                                    label = {
                                        Text(
                                            if (apiTokens.size > 1) {
                                                stringResource(R.string.api_key_number, index + 1)
                                            } else {
                                                stringResource(R.string.api_key)
                                            }
                                        )
                                    },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                                )
                                if (apiTokens.size > 1) {
                                    IconButton(onClick = { apiTokens.removeAt(index) }) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = stringResource(R.string.remove_api_key),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { apiTokens.add("") }) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                                Text(stringResource(R.string.add_api_key))
                            }
                        }
                    } else {
                        val selected = providerConnections.firstOrNull { it.uid == selectedConnectionUid }
                        selected?.let { connection ->
                            Text(
                                text = stringResource(
                                    R.string.using_saved_connection,
                                    connection.name,
                                    connection.apiUrl
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.ai_profile),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text(stringResource(R.string.model)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text(stringResource(R.string.model_supporting)) }
                    )

                    val suggestions = suggestedModels(clientType)
                    if (suggestions.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { showSuggestedModels = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Text(stringResource(R.string.choose_model))
                            }
                            DropdownMenu(
                                expanded = showSuggestedModels,
                                onDismissRequest = { showSuggestedModels = false }
                            ) {
                                suggestions.forEach { suggestion ->
                                    DropdownMenuItem(
                                        text = { Text(suggestion) },
                                        onClick = {
                                            model = suggestion
                                            showSuggestedModels = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (clientType == ClientType.OPENROUTER) {
                        OutlinedButton(
                            onClick = { showOpenRouterPicker = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(text = stringResource(R.string.openrouter_browse_models))
                        }
                    } else if (clientType == ClientType.LLAMA) {
                        OutlinedButton(
                            onClick = { showLlamaPicker = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(text = stringResource(R.string.llama_select_router_model))
                        }
                    }

                    OutlinedButton(
                        onClick = { showAdvancedSettings = !showAdvancedSettings },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    ) {
                        Text(
                            stringResource(
                                if (showAdvancedSettings) {
                                    R.string.hide_advanced_settings
                                } else {
                                    R.string.advanced_settings
                                }
                            )
                        )
                    }
                    AnimatedVisibility(visible = showAdvancedSettings) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            dev.chungjungsoo.gptmobile.presentation.common.SystemPromptEditor(
                                value = systemPrompt,
                                onValueChange = { systemPrompt = it }
                            )
                            CreativitySlider(
                                value = creativity,
                                onValueChange = { creativity = it },
                                enabled = !(clientType == ClientType.OPENAI && isReasoningEnabled),
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            OutlinedTextField(
                                value = maxToolCallsText,
                                onValueChange = { maxToolCallsText = it.filter(Char::isDigit) },
                                label = { Text(stringResource(R.string.maximum_tool_calls)) },
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                singleLine = true,
                                supportingText = { Text(stringResource(R.string.blank_means_unlimited)) }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.extended_thinking), style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        stringResource(R.string.extended_thinking_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(checked = isReasoningEnabled, onCheckedChange = { isReasoningEnabled = it })
                            }
                            Text(
                                text = stringResource(R.string.mcp_tools_after_save),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                    LocalModelCatalogPicker(
                        items = catalogModels,
                        selectedCatalogEntryId = selectedLocalModelId,
                        checkingAccessEntryId = downloadState.checkingAccessEntryId,
                        showPendingActivationHint = isWaitingForDownload,
                        onModelSelected = viewModel::selectLocalModel,
                        onNavigateToLocalModels = onNavigateToLocalModels
                    )
                    CreativitySlider(
                        value = creativity,
                        onValueChange = { creativity = it },
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showLabelsDialog) {
        ProfileLabelEditorDialog(
            currentLabels = profileLabels,
            reusableLabels = reusableLabels,
            onDismiss = { showLabelsDialog = false },
            onSave = { labels ->
                profileLabels = labels
                showLabelsDialog = false
            }
        )
    }

    if (showOpenRouterPicker) {
        OpenRouterModelPickerDialog(
            currentModel = model,
            onDismiss = { showOpenRouterPicker = false },
            onModelSelected = { selectedModel ->
                model = selectedModel
            }
        )
    }

    if (showLlamaPicker) {
        LlamaModelPickerDialog(
            baseUrl = apiUrl,
            currentModel = model,
            onDismiss = { showLlamaPicker = false },
            onModelSelected = { selectedModel ->
                model = selectedModel
            }
        )
    }
}

private fun suggestedModels(clientType: ClientType): List<String> = when (clientType) {
    ClientType.OPENAI -> ModelConstants.openaiModels.toList()
    ClientType.ANTHROPIC -> ModelConstants.anthropicModels.toList()
    ClientType.GOOGLE -> ModelConstants.googleModels.toList()
    ClientType.GROQ -> ModelConstants.groqModels.toList()
    ClientType.OLLAMA -> ModelConstants.ollamaModels.toList()
    ClientType.LLAMA -> ModelConstants.llamaModels.toList()
    ClientType.OPENROUTER, ClientType.CUSTOM, ClientType.LITERT_LM, ClientType.FREE -> emptyList()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPlatformTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    actionLabel: String?,
    isActionEnabled: Boolean,
    onNavigationClick: () -> Unit,
    onActionClick: () -> Unit
) {
    LargeTopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = MaterialTheme.colorScheme.onBackground),
        title = { Text(modifier = Modifier.padding(4.dp), text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(modifier = Modifier.padding(4.dp), onClick = onNavigationClick) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
            }
        },
        actions = {
            actionLabel?.let { label ->
                TextButton(modifier = Modifier.semantics { contentDescription = label }, enabled = isActionEnabled, onClick = onActionClick) { Text(label) }
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun getClientTypeName(clientType: ClientType): String = when (clientType) {
    ClientType.FREE -> stringResource(R.string.free_ai)
    ClientType.CUSTOM -> stringResource(R.string.custom)
    else -> ModelConstants.defaultPlatformName(clientType)
}

@Composable
private fun getClientTypeDescription(clientType: ClientType): String = when (clientType) {
    ClientType.OPENAI -> stringResource(R.string.client_type_openai_desc)
    ClientType.ANTHROPIC -> stringResource(R.string.client_type_anthropic_desc)
    ClientType.GOOGLE -> stringResource(R.string.client_type_google_desc)
    ClientType.GROQ -> stringResource(R.string.client_type_groq_desc)
    ClientType.OLLAMA -> stringResource(R.string.client_type_ollama_desc)
    ClientType.OPENROUTER -> stringResource(R.string.client_type_openrouter_desc)
    ClientType.FREE -> stringResource(R.string.free_ai_description)
    ClientType.CUSTOM -> stringResource(R.string.client_type_custom_desc)
    ClientType.LITERT_LM -> stringResource(R.string.client_type_litert_lm_desc)
    ClientType.LLAMA -> stringResource(R.string.client_type_llama_desc)
}
