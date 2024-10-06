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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.presentation.common.HelpText
import dev.chungjungsoo.gptmobile.presentation.common.PrimaryLongButton
import dev.chungjungsoo.gptmobile.presentation.common.Route
import dev.chungjungsoo.gptmobile.util.collectManagedState
import dev.chungjungsoo.gptmobile.util.getPlatformHelpLinkResources
import dev.chungjungsoo.gptmobile.util.isValidUrl

@Composable
fun SetupAPIUrlScreen(
    modifier: Modifier = Modifier,
    currentRoute: String = Route.OLLAMA_API_ADDRESS,
    setupViewModel: SetupViewModel = hiltViewModel(),
    onNavigate: (route: String) -> Unit,
    onBackAction: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val platformState by setupViewModel.platformState.collectManagedState()
    val ollamaPlatform = platformState.first { it.name == ApiType.OLLAMA }

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
            APIAddressInputText()

            APIAddressInput(
                platform = ollamaPlatform,
                onChangeEvent = { s -> setupViewModel.updateAPIAddress(ollamaPlatform, s) },
                onClearEvent = { setupViewModel.updateAPIAddress(ollamaPlatform, "") }
            )
            Spacer(modifier = Modifier.weight(1f))
            PrimaryLongButton(
                enabled = ollamaPlatform.apiUrl.isValidUrl(),
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
fun APIAddressInputText(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Text(
            modifier = Modifier
                .padding(4.dp)
                .semantics { heading() },
            text = stringResource(R.string.enter_api_address),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            modifier = Modifier.padding(4.dp),
            text = stringResource(R.string.api_address_description),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun APIAddressInput(
    modifier: Modifier = Modifier,
    platform: Platform,
    onChangeEvent: (String) -> Unit = { _ -> },
    onClearEvent: () -> Unit = {}
) {
    val helpLinks = getPlatformHelpLinkResources()

    Column(modifier = modifier) {
        OutlinedTextField(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 16.dp, start = 20.dp, end = 20.dp),
            value = platform.apiUrl,
            onValueChange = onChangeEvent,
            label = {
                Text(stringResource(R.string.ollama_api_address))
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            supportingText = {
                HelpText(helpLinks[ApiType.OLLAMA]!!)
            },
            trailingIcon = {
                if (platform.apiUrl.isNotBlank()) {
                    IconButton(onClick = onClearEvent) {
                        Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.clear_token))
                    }
                }
            }
        )
    }
}
