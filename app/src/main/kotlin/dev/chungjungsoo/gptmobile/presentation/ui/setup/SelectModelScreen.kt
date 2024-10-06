package dev.chungjungsoo.gptmobile.presentation.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.ModelConstants.anthropicModels
import dev.chungjungsoo.gptmobile.data.ModelConstants.googleModels
import dev.chungjungsoo.gptmobile.data.ModelConstants.openaiModels
import dev.chungjungsoo.gptmobile.data.dto.APIModel
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.presentation.common.PrimaryLongButton
import dev.chungjungsoo.gptmobile.presentation.common.RadioItem
import dev.chungjungsoo.gptmobile.util.collectManagedState
import dev.chungjungsoo.gptmobile.util.generateAnthropicModelList
import dev.chungjungsoo.gptmobile.util.generateGoogleModelList
import dev.chungjungsoo.gptmobile.util.generateOpenAIModelList
import dev.chungjungsoo.gptmobile.util.getAPIModelSelectDescription
import dev.chungjungsoo.gptmobile.util.getAPIModelSelectTitle

@Composable
fun SelectModelScreen(
    modifier: Modifier = Modifier,
    setupViewModel: SetupViewModel = hiltViewModel(),
    currentRoute: String,
    platformType: ApiType,
    onNavigate: (route: String) -> Unit,
    onBackAction: () -> Unit
) {
    val title = getAPIModelSelectTitle(platformType)
    val description = getAPIModelSelectDescription(platformType)
    val availableModels = when (platformType) {
        ApiType.OPENAI -> generateOpenAIModelList(models = openaiModels)
        ApiType.ANTHROPIC -> generateAnthropicModelList(models = anthropicModels)
        ApiType.GOOGLE -> generateGoogleModelList(models = googleModels)
        ApiType.OLLAMA -> listOf()
    }
    val defaultModel = remember {
        derivedStateOf {
            setupViewModel.setDefaultModel(
                platformType,
                when (platformType) {
                    ApiType.OPENAI -> 0
                    ApiType.ANTHROPIC -> 0
                    ApiType.GOOGLE -> 1
                    ApiType.OLLAMA -> 0
                }
            )
        }
    }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val platformState by setupViewModel.platformState.collectManagedState()
    val model = platformState.firstOrNull { it.name == platformType }?.model ?: defaultModel.value

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { SetupAppBar(onBackAction) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
        ) {
            SelectModelText(title = title, description = description)
            ModelRadioGroup(
                availableModels = availableModels,
                initModel = model,
                onChangeEvent = { model -> setupViewModel.updateModel(platformType, model) }
            )
            Spacer(modifier = Modifier.weight(1f))
            PrimaryLongButton(
                enabled = availableModels.any { it.aliasValue == model } || model.isNotBlank(),
                onClick = {
                    val nextStep = setupViewModel.getNextSetupRoute(currentRoute)
                    onNavigate(nextStep)
                },
                text = stringResource(R.string.next)
            )
        }
    }
}

@Composable
fun SelectModelText(
    modifier: Modifier = Modifier,
    title: String,
    description: String
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Text(
            modifier = Modifier
                .padding(4.dp)
                .semantics { heading() },
            text = title,
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            modifier = Modifier.padding(4.dp),
            text = description,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun ModelRadioGroup(
    modifier: Modifier = Modifier,
    availableModels: List<APIModel>,
    initModel: String,
    onChangeEvent: (String) -> Unit
) {
    var model by remember { mutableStateOf(initModel) }
    var customSelected by remember { mutableStateOf(model !in availableModels.map { it.aliasValue }.toSet()) }
    var customModel by remember { mutableStateOf(if (customSelected) model else "") }

    Column(modifier = modifier) {
        availableModels.forEach { m ->
            RadioItem(
                value = m.aliasValue,
                selected = model == m.aliasValue && !customSelected,
                title = m.name,
                description = m.description,
                onSelected = {
                    model = it
                    customSelected = false
                    onChangeEvent(it)
                }
            )
        }
        RadioItem(
            value = customModel,
            selected = customSelected,
            title = stringResource(R.string.custom),
            description = stringResource(R.string.custom_description),
            onSelected = {
                customSelected = true
                customModel = it
                onChangeEvent(it)
            }
        )
        OutlinedTextField(
            modifier = Modifier
                .padding(start = 24.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            enabled = customSelected,
            value = customModel,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            onValueChange = { s ->
                customModel = s
                onChangeEvent(s)
            },
            label = {
                Text(stringResource(R.string.model_name))
            },
            placeholder = {
                Text(stringResource(R.string.model_custom_example))
            },
            supportingText = {
                Text(stringResource(R.string.custom_model_warning))
            }
        )
    }
}
