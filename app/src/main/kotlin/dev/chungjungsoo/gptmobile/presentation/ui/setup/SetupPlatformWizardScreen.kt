package dev.chungjungsoo.gptmobile.presentation.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.presentation.common.PrimaryLongButton
import dev.chungjungsoo.gptmobile.presentation.theme.defaultSpatialSpec
import dev.chungjungsoo.gptmobile.presentation.theme.fastEffectsSpec
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.LocalModelDownloadDialogHost
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.rememberLocalModelDownloader
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LocalModelListItem
import dev.chungjungsoo.gptmobile.presentation.ui.setup.SetupViewModelV2.Companion.WIZARD_STEP_API_KEY
import dev.chungjungsoo.gptmobile.presentation.ui.setup.SetupViewModelV2.Companion.WIZARD_STEP_BASICS
import dev.chungjungsoo.gptmobile.presentation.ui.setup.SetupViewModelV2.Companion.WIZARD_STEP_MODEL

@Composable
fun SetupPlatformWizardScreen(
    modifier: Modifier = Modifier,
    setupViewModel: SetupViewModelV2 = hiltViewModel(),
    onComplete: () -> Unit,
    onBackAction: () -> Unit,
    onNavigateToLocalModels: () -> Unit = {}
) {
    val wizardStep by setupViewModel.wizardStep.collectAsStateWithLifecycle()
    val selectedClientType by setupViewModel.selectedClientType.collectAsStateWithLifecycle()
    val catalogModels by setupViewModel.catalogLocalModels.collectAsStateWithLifecycle()
    val downloadState by setupViewModel.localModelDownloadState.collectAsStateWithLifecycle()
    val canProceed by setupViewModel.canProceed.collectAsStateWithLifecycle()
    val isWaitingForDownload by setupViewModel.isWaitingForDownload.collectAsStateWithLifecycle()
    val saveStatus by setupViewModel.saveStatus.collectAsStateWithLifecycle()
    val requestDownload = rememberLocalModelDownloader { entry ->
        setupViewModel.selectLocalModel(entry.id)
    }

    val isSaving = saveStatus is SaveStatus.Saving
    val handleBack = {
        if (!isSaving) {
            if (wizardStep > 0) {
                setupViewModel.previousWizardStep()
            } else {
                setupViewModel.resetWizard()
                onBackAction()
            }
        }
    }

    BackHandler(onBack = handleBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SetupAppBar(
                backAction = handleBack,
                enabled = !isSaving
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .imePadding()
            ) {
                WizardProgressIndicator(
                    currentStep = setupViewModel.wizardDisplayStep(),
                    totalSteps = setupViewModel.wizardTotalSteps(),
                    isLocalPlatform = selectedClientType == ClientType.LITERT_LM
                )

                AnimatedContent(
                    targetState = wizardStep,
                    transitionSpec = {
                        val direction = if (targetState > initialState) {
                            AnimatedContentTransitionScope.SlideDirection.Start
                        } else {
                            AnimatedContentTransitionScope.SlideDirection.End
                        }
                        (
                            slideIntoContainer(
                                towards = direction,
                                animationSpec = defaultSpatialSpec()
                            ) + fadeIn(animationSpec = fastEffectsSpec())
                            ) togetherWith
                            (
                                slideOutOfContainer(
                                    towards = direction,
                                    animationSpec = defaultSpatialSpec()
                                ) + fadeOut(animationSpec = fastEffectsSpec())
                                )
                    },
                    label = "wizard_step_animation",
                    modifier = Modifier.weight(1f)
                ) { step ->
                    when (step) {
                        WIZARD_STEP_BASICS -> {
                            val currentPlatformName by setupViewModel.platformName.collectAsStateWithLifecycle()
                            val currentApiUrl by setupViewModel.apiUrl.collectAsStateWithLifecycle()
                            BasicsStep(
                                clientType = selectedClientType,
                                platformName = currentPlatformName,
                                onPlatformNameChange = setupViewModel::updatePlatformName,
                                apiUrl = currentApiUrl,
                                onApiUrlChange = setupViewModel::updateApiUrl,
                                isApiUrlVisible = selectedClientType != ClientType.LITERT_LM
                            )
                        }

                        WIZARD_STEP_API_KEY -> {
                            val currentApiKey by setupViewModel.apiKey.collectAsStateWithLifecycle()
                            ApiKeyStep(
                                clientType = selectedClientType,
                                apiKey = currentApiKey,
                                onApiKeyChange = setupViewModel::updateApiKey
                            )
                        }

                        WIZARD_STEP_MODEL -> {
                            val currentModel by setupViewModel.model.collectAsStateWithLifecycle()
                            if (selectedClientType == ClientType.LITERT_LM) {
                                LocalModelStep(
                                    items = catalogModels,
                                    selectedCatalogEntryId = currentModel,
                                    checkingAccessEntryId = downloadState.checkingAccessEntryId,
                                    showPendingActivationHint = isWaitingForDownload,
                                    onModelSelected = { catalogEntryId ->
                                        val entry = catalogModels.firstOrNull { it.entry.id == catalogEntryId }?.entry
                                        if (entry != null) {
                                            requestDownload(entry)
                                        } else {
                                            setupViewModel.selectLocalModel(catalogEntryId)
                                        }
                                    },
                                    onNavigateToLocalModels = onNavigateToLocalModels
                                )
                            } else {
                                ModelStep(
                                    model = currentModel,
                                    onModelChange = setupViewModel::updateModel
                                )
                            }
                        }
                    }
                }

                WizardNavigationButton(
                    canProceed = canProceed && saveStatus !is SaveStatus.Saving,
                    onNext = {
                        if (wizardStep == WIZARD_STEP_MODEL) {
                            setupViewModel.savePlatform(onComplete)
                        } else {
                            setupViewModel.nextWizardStep()
                        }
                    },
                    isLastStep = wizardStep == WIZARD_STEP_MODEL,
                    isSaving = isSaving,
                    errorMessage = (saveStatus as? SaveStatus.Error)?.message
                )
            }
        }
    }

    LocalModelDownloadDialogHost(
        dialog = downloadState.dialog,
        onConfirmRamWarning = setupViewModel::confirmRamWarning,
        onConfirmMeteredDownload = setupViewModel::confirmMeteredDownload,
        onDismissDialog = setupViewModel::dismissDownloadDialog,
        onStartSignIn = setupViewModel::startHuggingFaceSignIn,
        onAuthActivityResult = setupViewModel::onAuthActivityResult,
        onLicenseTabClosed = setupViewModel::onLicenseTabClosed,
        onRetryAfterLicense = setupViewModel::retryAfterLicense,
        onEnterAccessToken = setupViewModel::openAccessTokenDialog,
        onSaveAccessToken = setupViewModel::saveHuggingFaceAccessToken
    )
}

@Composable
private fun WizardProgressIndicator(
    currentStep: Int,
    totalSteps: Int,
    isLocalPlatform: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Step indicator text
        Text(
            text = stringResource(R.string.step_x_of_y, currentStep + 1, totalSteps),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Progress bar
        LinearProgressIndicator(
            progress = { (currentStep + 1).toFloat() / totalSteps },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Step labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StepLabel(
                text = stringResource(R.string.step_basics),
                isCompleted = currentStep > 0,
                isCurrent = currentStep == 0
            )
            if (!isLocalPlatform) {
                StepLabel(
                    text = stringResource(R.string.step_api_key),
                    isCompleted = currentStep > WIZARD_STEP_API_KEY,
                    isCurrent = currentStep == WIZARD_STEP_API_KEY
                )
            }
            StepLabel(
                text = stringResource(R.string.step_model),
                isCompleted = currentStep > totalSteps - 1,
                isCurrent = currentStep == totalSteps - 1
            )
        }
    }
}

@Composable
private fun StepLabel(
    text: String,
    isCompleted: Boolean,
    isCurrent: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (isCompleted) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = when {
                isCurrent -> MaterialTheme.colorScheme.primary
                isCompleted -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun BasicsStep(
    clientType: ClientType?,
    platformName: String,
    onPlatformNameChange: (String) -> Unit,
    apiUrl: String,
    onApiUrlChange: (String) -> Unit,
    isApiUrlVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Text(
            modifier = Modifier.semantics { heading() },
            text = stringResource(R.string.step_basics),
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(
                if (clientType == ClientType.LITERT_LM) {
                    R.string.local_platform_basics_description
                } else {
                    R.string.platform_basics_description
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Platform Name
        OutlinedTextField(
            value = platformName,
            onValueChange = onPlatformNameChange,
            label = { Text(stringResource(R.string.platform_name)) },
            placeholder = { Text(stringResource(R.string.platform_name_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = {
                Text(stringResource(R.string.platform_name_supporting))
            }
        )

        if (isApiUrlVisible) {
            Spacer(modifier = Modifier.height(20.dp))

            // API URL
            OutlinedTextField(
                value = apiUrl,
                onValueChange = onApiUrlChange,
                label = { Text(stringResource(R.string.api_url)) },
                placeholder = { Text(stringResource(R.string.api_url_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = clientType != ClientType.GOOGLE,
                supportingText = {
                    if (clientType == ClientType.GOOGLE) {
                        Text(stringResource(R.string.client_type_google_desc))
                    } else {
                        Text(stringResource(R.string.api_url_cautions))
                    }
                }
            )
        }
    }
}

@Composable
private fun ApiKeyStep(
    clientType: ClientType?,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Text(
            modifier = Modifier.semantics { heading() },
            text = stringResource(R.string.step_api_key),
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.api_key_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (clientType == ClientType.OLLAMA) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.api_key_optional_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // API Key
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text(stringResource(R.string.api_key)) },
            placeholder = { Text(stringResource(R.string.api_key_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            supportingText = {
                Text(stringResource(R.string.api_key_supporting))
            }
        )

        // Help link based on client type
        clientType?.let { type ->
            val helpUrl = getApiHelpUrl(type)
            if (helpUrl != null) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.need_help),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = helpUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun LocalModelStep(
    items: List<LocalModelListItem>,
    selectedCatalogEntryId: String,
    checkingAccessEntryId: String?,
    showPendingActivationHint: Boolean,
    onModelSelected: (String) -> Unit,
    onNavigateToLocalModels: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Text(
            modifier = Modifier.semantics { heading() },
            text = stringResource(R.string.step_model),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        LocalModelCatalogPicker(
            items = items,
            selectedCatalogEntryId = selectedCatalogEntryId,
            checkingAccessEntryId = checkingAccessEntryId,
            showPendingActivationHint = showPendingActivationHint,
            onModelSelected = onModelSelected,
            onNavigateToLocalModels = onNavigateToLocalModels
        )
    }
}

@Composable
private fun ModelStep(
    model: String,
    onModelChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Text(
            modifier = Modifier.semantics { heading() },
            text = stringResource(R.string.step_model),
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.model_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Model
        OutlinedTextField(
            value = model,
            onValueChange = onModelChange,
            label = { Text(stringResource(R.string.model)) },
            placeholder = { Text(stringResource(R.string.model_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = {
                Text(stringResource(R.string.model_supporting))
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Examples
        Text(
            text = stringResource(R.string.model_examples),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WizardNavigationButton(
    canProceed: Boolean,
    onNext: () -> Unit,
    isLastStep: Boolean,
    isSaving: Boolean = false,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        errorMessage?.let { message ->
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
                    .padding(bottom = 8.dp)
            )
        }
        PrimaryLongButton(
            enabled = canProceed,
            onClick = onNext,
            text = stringResource(
                when {
                    isLastStep && isSaving -> R.string.saving
                    isLastStep -> R.string.finish
                    else -> R.string.next
                }
            )
        )
    }
}

private fun getApiHelpUrl(clientType: ClientType): String? = when (clientType) {
    ClientType.OPENAI -> "https://platform.openai.com/account/api-keys"
    ClientType.ANTHROPIC -> "https://console.anthropic.com/settings/keys"
    ClientType.GOOGLE -> "https://aistudio.google.com/app/apikey"
    ClientType.GROQ -> "https://console.groq.com/keys"
    ClientType.OLLAMA -> "https://ollama.com/blog/openai-compatibility"
    ClientType.OPENROUTER -> "https://openrouter.ai/keys"
    ClientType.CUSTOM -> null
    ClientType.LITERT_LM -> null
}
