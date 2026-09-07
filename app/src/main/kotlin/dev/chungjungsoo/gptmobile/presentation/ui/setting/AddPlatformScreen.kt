package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.presentation.common.DestinationCard
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.LocalModelDownloadDialogHost
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.rememberLocalModelDownloader
import dev.chungjungsoo.gptmobile.presentation.ui.setup.LocalModelCatalogPicker
import dev.chungjungsoo.gptmobile.util.pinnedExitUntilCollapsedScrollBehavior

private enum class AddPlatformStep { API_TYPE, DETAILS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlatformScreen(
    modifier: Modifier = Modifier,
    viewModel: AddPlatformViewModel = hiltViewModel(),
    settingViewModel: SettingViewModelV2,
    onNavigationClick: () -> Unit,
    onSave: () -> Unit,
    onNavigateToLocalModels: () -> Unit = {}
) {
    var step by rememberSaveable { mutableStateOf(AddPlatformStep.API_TYPE) }
    var selectedClientType by rememberSaveable { mutableStateOf<ClientType?>(null) }
    var platformName by rememberSaveable { mutableStateOf("") }
    var apiUrl by rememberSaveable { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var isReasoningEnabled by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scrollBehavior = pinnedExitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward }
    )
    val catalogModels by viewModel.catalogLocalModels.collectAsStateWithLifecycle()
    val downloadState by viewModel.localModelDownloadState.collectAsStateWithLifecycle()
    val selectedLocalModelId by viewModel.selectedCatalogEntryId.collectAsStateWithLifecycle()
    val canSave by viewModel.canSave.collectAsStateWithLifecycle()
    val isWaitingForDownload by viewModel.isWaitingForDownload.collectAsStateWithLifecycle()
    val saveState by settingViewModel.addPlatformSaveState.collectAsStateWithLifecycle()
    val requestDownload = rememberLocalModelDownloader { entry ->
        viewModel.selectLocalModel(entry.id)
    }
    val isLocalPlatform = selectedClientType == ClientType.LITERT_LM
    val title = stringResource(if (step == AddPlatformStep.API_TYPE) R.string.choose_platform_type else R.string.platform_details)
    val isSaveEnabled = !saveState.isSaving &&
        platformName.isNotBlank() &&
        if (isLocalPlatform) {
            canSave
        } else {
            model.isNotBlank() && apiUrl.isNotBlank()
        }
    val navigateBack = { if (step == AddPlatformStep.DETAILS) step = AddPlatformStep.API_TYPE else onNavigationClick() }
    BackHandler(enabled = step == AddPlatformStep.DETAILS) {
        if (!saveState.isSaving) {
            step = AddPlatformStep.API_TYPE
        }
    }

    fun savePlatform() {
        val clientType = selectedClientType ?: return
        val selectedModel = if (clientType == ClientType.LITERT_LM) {
            selectedLocalModelId.trim()
        } else {
            model.trim()
        }
        if (clientType == ClientType.LITERT_LM && !viewModel.canSaveLocalModel()) return
        val defaults = if (clientType == ClientType.LITERT_LM) {
            viewModel.defaultsFor(selectedModel)
        } else {
            null
        }
        settingViewModel.addPlatform(
            PlatformV2(
                name = platformName.trim(),
                compatibleType = clientType,
                enabled = if (clientType == ClientType.LITERT_LM) {
                    viewModel.shouldEnableLocalPlatform()
                } else {
                    true
                },
                apiUrl = if (clientType == ClientType.LITERT_LM) "" else apiUrl.trim(),
                token = apiKey.trim().takeIf { it.isNotEmpty() && clientType != ClientType.LITERT_LM },
                model = selectedModel,
                temperature = defaults?.temperature ?: 1.0f,
                topP = defaults?.topP ?: 1.0f,
                topK = defaults?.topK,
                maxTokens = defaults?.maxTokens,
                accelerator = defaults?.accelerator,
                systemPrompt = ModelConstants.DEFAULT_PROMPT,
                stream = true,
                reasoning = isReasoningEnabled && clientType != ClientType.LITERT_LM,
                timeout = 30
            ),
            onSuccess = {
                apiKey = ""
                onSave()
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            AddPlatformTopBar(
                title = title,
                scrollBehavior = scrollBehavior,
                onNavigationClick = navigateBack,
                navigationEnabled = !saveState.isSaving
            )
        },
        bottomBar = {
            if (step == AddPlatformStep.DETAILS) {
                AddPlatformSaveBar(
                    isSaveEnabled = isSaveEnabled,
                    isSaving = saveState.isSaving,
                    errorMessage = saveState.errorMessage,
                    onSave = ::savePlatform
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                if (step == AddPlatformStep.API_TYPE) {
                    Text(
                        text = stringResource(R.string.choose_platform_type_step_description),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                    ClientType.entries.forEach { clientType ->
                        DestinationCard(
                            title = getClientTypeName(clientType),
                            description = getClientTypeDescription(clientType),
                            selected = selectedClientType == clientType,
                            onClick = {
                                selectedClientType = clientType
                                platformName = ModelConstants.defaultPlatformName(clientType)
                                apiUrl = ModelConstants.defaultApiUrl(clientType)
                                model = ModelConstants.defaultModel(clientType)
                                apiKey = ""
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
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                    OutlinedTextField(
                        value = platformName,
                        onValueChange = { platformName = it },
                        label = { Text(stringResource(R.string.platform_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = platformName.isBlank(),
                        supportingText = {
                            Text(
                                if (platformName.isBlank()) {
                                    stringResource(R.string.field_required)
                                } else {
                                    stringResource(R.string.platform_name_supporting)
                                }
                            )
                        }
                    )
                    if (clientType != ClientType.LITERT_LM) {
                        OutlinedTextField(
                            value = apiUrl,
                            onValueChange = { apiUrl = it },
                            label = { Text(stringResource(R.string.api_url)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            singleLine = true,
                            isError = apiUrl.isBlank(),
                            supportingText = {
                                if (apiUrl.isBlank()) Text(stringResource(R.string.field_required))
                            }
                        )
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text(stringResource(R.string.api_key)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            supportingText = { Text(stringResource(R.string.api_key_supporting)) }
                        )
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            label = { Text(stringResource(R.string.model)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            singleLine = true,
                            isError = model.isBlank(),
                            supportingText = {
                                Text(
                                    if (model.isBlank()) {
                                        stringResource(R.string.field_required)
                                    } else {
                                        stringResource(R.string.model_supporting)
                                    }
                                )
                            }
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.extended_thinking),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(R.string.extended_thinking_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isReasoningEnabled,
                                onCheckedChange = { isReasoningEnabled = it }
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                        LocalModelCatalogPicker(
                            items = catalogModels,
                            selectedCatalogEntryId = selectedLocalModelId,
                            checkingAccessEntryId = downloadState.checkingAccessEntryId,
                            showPendingActivationHint = isWaitingForDownload,
                            onModelSelected = { catalogEntryId ->
                                val entry = catalogModels.firstOrNull { it.entry.id == catalogEntryId }?.entry
                                if (entry != null) {
                                    requestDownload(entry)
                                } else {
                                    viewModel.selectLocalModel(catalogEntryId)
                                }
                            },
                            onNavigateToLocalModels = onNavigateToLocalModels
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    LocalModelDownloadDialogHost(
        dialog = downloadState.dialog,
        onConfirmRamWarning = viewModel::confirmRamWarning,
        onConfirmMeteredDownload = viewModel::confirmMeteredDownload,
        onDismissDialog = viewModel::dismissDownloadDialog,
        onStartSignIn = viewModel::startHuggingFaceSignIn,
        onAuthActivityResult = viewModel::onAuthActivityResult,
        onLicenseTabClosed = viewModel::onLicenseTabClosed,
        onRetryAfterLicense = viewModel::retryAfterLicense,
        onEnterAccessToken = viewModel::openAccessTokenDialog,
        onSaveAccessToken = viewModel::saveHuggingFaceAccessToken
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPlatformTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigationClick: () -> Unit,
    navigationEnabled: Boolean
) {
    LargeTopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = MaterialTheme.colorScheme.onBackground),
        title = { Text(modifier = Modifier.padding(4.dp), text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(
                modifier = Modifier.padding(4.dp),
                enabled = navigationEnabled,
                onClick = onNavigationClick
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun AddPlatformSaveBar(
    isSaveEnabled: Boolean,
    isSaving: Boolean,
    errorMessage: String?,
    onSave: () -> Unit
) {
    Surface(shadowElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .semantics {
                            error(message)
                            liveRegion = LiveRegionMode.Assertive
                        }
                        .padding(bottom = 8.dp)
                )
            }
            Button(
                onClick = onSave,
                enabled = isSaveEnabled,
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                }
                Text(stringResource(if (isSaving) R.string.saving else R.string.save))
            }
        }
    }
}

@Composable
private fun getClientTypeName(clientType: ClientType): String = when (clientType) {
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
    ClientType.CUSTOM -> stringResource(R.string.client_type_custom_desc)
    ClientType.LITERT_LM -> stringResource(R.string.client_type_litert_lm_desc)
}
