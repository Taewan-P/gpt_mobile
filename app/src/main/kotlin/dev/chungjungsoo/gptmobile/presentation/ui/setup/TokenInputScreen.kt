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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.presentation.common.PrimaryLongButton
import dev.chungjungsoo.gptmobile.presentation.common.Route
import dev.chungjungsoo.gptmobile.presentation.common.TokenInputField
import dev.chungjungsoo.gptmobile.util.getPlatformAPILabelResources
import dev.chungjungsoo.gptmobile.util.getPlatformHelpLinkResources

@Composable
fun TokenInputScreen(
    modifier: Modifier = Modifier,
    currentRoute: String = Route.TOKEN_INPUT,
    setupViewModel: SetupViewModel = hiltViewModel(),
    onNavigate: (route: String) -> Unit,
    onBackAction: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val platformState by setupViewModel.platformState.collectAsStateWithLifecycle()

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
            TokenInputText()
            TokenInput(
                platforms = platformState,
                onChangeEvent = { platform, s -> setupViewModel.updateToken(platform, s) },
                onClearEvent = { platform -> setupViewModel.updateToken(platform, "") }
            )
            Spacer(modifier = Modifier.weight(1f))
            PrimaryLongButton(
                enabled = platformState.filter { it.selected && it.name != ApiType.OLLAMA }.all { platform -> platform.token != null },
                onClick = {
                    val nextStep = setupViewModel.getNextSetupRoute(currentRoute)
                    onNavigate(nextStep)
                },
                text = stringResource(R.string.next)
            )
        }
    }
}

@Preview
@Composable
fun TokenInputText(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Text(
            modifier = Modifier
                .padding(4.dp)
                .semantics { heading() },
            text = stringResource(R.string.enter_api_key),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            modifier = Modifier.padding(4.dp),
            text = stringResource(R.string.token_input_description),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            modifier = Modifier.padding(4.dp),
            text = stringResource(R.string.token_input_warning),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Preview
@Composable
fun TokenInput(
    modifier: Modifier = Modifier,
    platforms: List<Platform> = listOf(),
    onChangeEvent: (Platform, String) -> Unit = { _, _ -> },
    onClearEvent: (Platform) -> Unit = {}
) {
    val labels = getPlatformAPILabelResources()
    val helpLinks = getPlatformHelpLinkResources()

    Column(modifier = modifier) {
        // Ollama doesn't currently support api keys
        platforms.filter { it.selected && it.name != ApiType.OLLAMA }.forEachIndexed { i, platform ->
            val isLast = platforms.filter { it.selected && it.name != ApiType.OLLAMA }.size - 1 == i
            TokenInputField(
                value = platform.token ?: "",
                onValueChange = { onChangeEvent(platform, it) },
                onClearClick = { onClearEvent(platform) },
                label = labels[platform.name]!!,
                keyboardOptions = KeyboardOptions(imeAction = if (isLast) ImeAction.Done else ImeAction.Next),
                helpLink = helpLinks[platform.name]!!
            )
        }
    }
}
