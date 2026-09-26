package dev.chungjungsoo.gptmobile.presentation.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.ApiCredentialRotator
import dev.chungjungsoo.gptmobile.presentation.common.AdvancedOptions
import dev.chungjungsoo.gptmobile.presentation.common.FreeProviderPicker
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

    // Handle back press
    BackHandler {
        if (wizardStep > 0) {
            setupViewModel.previousWizardStep()
        } else {
            setupViewModel.resetWizard()
            onBackAction()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SetupAppBar(
                backAction = {
                    if (wizardStep > 0) {
                        setupViewModel.previousWizardStep()
                    } else {
                        setupViewModel.resetWizard()
                        onBackAction()
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding()
        ) {
            // Progress indicator
            WizardProgressIndicator(
                currentStep = setupViewModel.wizardDisplayStep(),
                totalSteps = setupViewModel.wizardTotalSteps(),
                isLocalPlatform = selectedClientType == ClientType.LITERT_LM || selectedClientType == ClientType.FREE
            )

            // Step content
            AnimatedContent(
                targetState = wizardStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                    }
                },
                label = "wizard_step_animation",
                modifier = Modifier.weight(1f)
            ) { step ->
                when (step) {
                    WIZARD_STEP_BASICS -> {
                        // Collect states directly inside AnimatedContent for proper state updates
                        val currentPlatformName by setupViewModel.platformName.collectAsStateWithLifecycle()
                        val currentApiUrl by setupViewModel.apiUrl.collectAsStateWithLifecycle()
                        BasicsStep(
                            clientType = selectedClientType,
                            platformName = currentPlatformName,
                            onPlatformNameChange = setupViewModel::updatePlatformName,
                            apiUrl = currentApiUrl,
                            onApiUrlChange = setupViewModel::updateApiUrl,
                            isApiUrlVisible = selectedClientType != ClientType.LITERT_LM && selectedClientType != ClientType.FREE
                        )
                    }

                    WIZARD_STEP_API_KEY -> {
                        // Collect apiKey state directly inside AnimatedContent for proper state updates
                        val currentApiKey by setupViewModel.apiKey.collectAsStateWithLifecycle()
                        ApiKeyStep(
                            clientType = selectedClientType,
                            apiKey = currentApiKey,
                            onApiKeyChange = setupViewModel::updateApiKey
                        )
                    }

                    WIZARD_STEP_MODEL -> {
                        // Collect model state directly inside AnimatedContent for proper recomposition
                        val currentModel by setupViewModel.model.collectAsStateWithLifecycle()
                        if (selectedClientType == ClientType.FREE) {
                            val freeUrl by setupViewModel.apiUrl.collectAsStateWithLifecycle()
                            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                                FreeProviderPicker(apiUrl = freeUrl, onProviderSelected = setupViewModel::selectFreeProvider)
                            }
                        } else if (selectedClientType == ClientType.LITERT_LM) {
                            LocalModelStep(
                                items = catalogModels,
                                selectedCatalogEntryId = currentModel,
                                checkingAccessEntryId = downloadState.checkingAccessEntryId,
                                showPendingActivationHint = isWaitingForDownload,
                                onModelSelected = setupViewModel::selectLocalModel,
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

            // Navigation buttons
            WizardNavigationButtons(
                currentStep = wizardStep,
                canProceed = canProceed,
                onBack = {
                    if (wizardStep > 0) {
                        setupViewModel.previousWizardStep()
                    } else {
                        setupViewModel.resetWizard()
                        onBackAction()
                    }
                },
                onNext = {
                    if (wizardStep == WIZARD_STEP_MODEL) {
                        setupViewModel.savePlatform()
                        onComplete()
                    } else {
                        setupViewModel.nextWizardStep()
                    }
                },
                isLastStep = wizardStep == WIZARD_STEP_MODEL
            )
        }
    }
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
            .padding(horizontal = 20.dp, vertical = 16.dp)
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
            .padding(horizontal = 20.dp)
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
            readOnly = clientType == ClientType.FREE,
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

            AdvancedOptions(title = "Connection address", initiallyExpanded = apiUrl.isBlank()) {
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
}

@Composable
private fun ApiKeyStep(
    clientType: ClientType?,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val initialList = remember(apiKey) {
        val parsed = ApiCredentialRotator.parseKeys(apiKey)
        if (parsed.isEmpty()) listOf("") else parsed
    }
    val tokens = remember(apiKey) {
        mutableStateListOf<String>().apply {
            addAll(initialList)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
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

        Spacer(modifier = Modifier.height(16.dp))

        var advancedKeys by rememberSaveable { mutableStateOf(false) }
        // API Keys list
        tokens.forEachIndexed { index, tokenValue ->
            if (index == 0 || advancedKeys) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = tokenValue,
                        onValueChange = { newValue ->
                            tokens[index] = newValue
                            onApiKeyChange(ApiCredentialRotator.formatKeys(tokens.toList()))
                        },
                        label = {
                            Text(
                                if (tokens.size > 1) {
                                    stringResource(R.string.api_key_number, index + 1)
                                } else {
                                    stringResource(R.string.api_key)
                                }
                            )
                        },
                        placeholder = { Text(stringResource(R.string.api_key_hint)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                    if (tokens.size > 1) {
                        IconButton(
                            onClick = {
                                tokens.removeAt(index)
                                onApiKeyChange(ApiCredentialRotator.formatKeys(tokens.toList()))
                            },
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.remove_api_key),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        TextButton(onClick = { advancedKeys = !advancedKeys }) { Text(if (advancedKeys) "Hide advanced key options" else "Advanced key options") }
        if (advancedKeys) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        tokens.add("")
                        onApiKeyChange(ApiCredentialRotator.formatKeys(tokens.toList()))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.add_api_key),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(stringResource(R.string.add_api_key))
                }
            }

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
            .padding(horizontal = 20.dp)
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
            .padding(horizontal = 20.dp)
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
private fun WizardNavigationButtons(
    currentStep: Int,
    canProceed: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    isLastStep: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Back button
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
        ) {
            Text(
                text = if (currentStep == 0) {
                    stringResource(R.string.cancel)
                } else {
                    stringResource(R.string.back)
                }
            )
        }

        // Next/Finish button
        Button(
            onClick = onNext,
            modifier = Modifier
                .weight(1f)
                .height(40.dp),
            enabled = canProceed
        ) {
            Text(
                text = if (isLastStep) {
                    stringResource(R.string.finish)
                } else {
                    stringResource(R.string.next)
                }
            )
        }
    }
}

private fun getApiHelpUrl(clientType: ClientType): String? = when (clientType) {
    ClientType.OPENAI -> "https://platform.openai.com/account/api-keys"
    ClientType.ANTHROPIC -> "https://console.anthropic.com/settings/keys"
    ClientType.GOOGLE -> "https://aistudio.google.com/app/apikey"
    ClientType.GROQ -> "https://console.groq.com/keys"
    ClientType.OLLAMA -> "https://ollama.com/blog/openai-compatibility"
    ClientType.OPENROUTER -> "https://openrouter.ai/keys"
    ClientType.FREE, ClientType.CUSTOM -> null
    ClientType.LITERT_LM -> null
    ClientType.LLAMA -> "https://llama.meta.com/"
}
