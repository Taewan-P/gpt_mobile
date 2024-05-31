package dev.chungjungsoo.gptmobile.presentation.ui.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.dto.APIModel
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.presentation.common.ModelConstants.anthropicModels
import dev.chungjungsoo.gptmobile.presentation.common.ModelConstants.googleModels
import dev.chungjungsoo.gptmobile.presentation.common.ModelConstants.openaiModels
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
    }
    val defaultModel = remember {
        derivedStateOf {
            setupViewModel.setDefaultModel(
                platformType,
                when (platformType) {
                    ApiType.OPENAI -> 0
                    ApiType.ANTHROPIC -> 0
                    ApiType.GOOGLE -> 1
                }
            )
        }
    }
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
        ) {
            SelectModelText(title = title, description = description)
            ModelRadioGroup(
                availableModels = availableModels,
                model = model,
                onChangeEvent = { model -> setupViewModel.updateModel(platformType, model) }
            )
            Spacer(modifier = Modifier.weight(1f))
            PrimaryLongButton(
                enabled = availableModels.any { it.aliasValue == model },
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
    model: String,
    onChangeEvent: (String) -> Unit
) {
    Column(modifier = modifier) {
        availableModels.forEach { m ->
            RadioItem(
                value = m.aliasValue,
                selected = model == m.aliasValue,
                title = m.name,
                description = m.description,
                onSelected = onChangeEvent
            )
        }
    }
}
