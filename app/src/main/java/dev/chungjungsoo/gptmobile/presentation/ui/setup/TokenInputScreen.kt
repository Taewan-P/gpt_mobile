package dev.chungjungsoo.gptmobile.presentation.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.dto.ApiType
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.presentation.common.PrimaryLongButton

@Preview
@Composable
fun TokenInputScreen(
    modifier: Modifier = Modifier,
    platformState: List<Platform> = listOf(),
    onChangeEvent: (Platform, String) -> Unit = { _, _ -> },
    onClearEvent: (Platform) -> Unit = { },
    onNextButtonClicked: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    Column(
        modifier = modifier
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
            onChangeEvent = { platform, s -> onChangeEvent(platform, s) },
            onClearEvent = { platform -> onClearEvent(platform) }
        )
        Spacer(modifier = Modifier.weight(1f))
        PrimaryLongButton(
            enabled = platformState.filter { it.selected }.all { platform -> platform.token != null },
            onClick = onNextButtonClicked,
            text = stringResource(R.string.next)
        )
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
    val labels = mapOf(
        ApiType.OPENAI to stringResource(R.string.openai_api_key),
        ApiType.ANTHROPIC to stringResource(R.string.anthropic_api_key),
        ApiType.GOOGLE to stringResource(R.string.google_api_key)
    )
    val helpLinks = mapOf(
        ApiType.OPENAI to stringResource(R.string.openai_api_help),
        ApiType.ANTHROPIC to stringResource(R.string.anthropic_api_help),
        ApiType.GOOGLE to stringResource(R.string.google_api_help)
    )

    Column(modifier = modifier) {
        platforms.filter { it.selected }.forEachIndexed { i, platform ->
            val isLast = platforms.filter { it.selected }.size - 1 == i
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

@Preview
@Composable
fun TokenInputField(
    modifier: Modifier = Modifier,
    value: String = "",
    onValueChange: (String) -> Unit = { },
    keyboardOptions: KeyboardOptions = KeyboardOptions(),
    onClearClick: () -> Unit = { },
    label: String = "",
    helpLink: String = ""
) {
    OutlinedTextField(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 16.dp, start = 20.dp, end = 20.dp),
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(label)
        },
        keyboardOptions = keyboardOptions,
        supportingText = {
            HelpText(helpLink)
        },
        trailingIcon = {
            if (value.isNotBlank()) {
                IconButton(onClick = onClearClick) {
                    Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.clear_token))
                }
            }
        }
    )
}

@Preview
@Composable
fun HelpText(helpLink: String = "") {
    val uriHandler = LocalUriHandler.current
    val annotatedString = buildAnnotatedString {
        val str = stringResource(R.string.need_help)
        append(str)
        addStyle(
            style = SpanStyle(
                color = MaterialTheme.colorScheme.tertiary,
                textDecoration = TextDecoration.Underline
            ),
            0,
            str.length
        )
        addStringAnnotation(
            "URL",
            annotation = helpLink,
            start = 0,
            end = str.length
        )
    }

    ClickableText(
        text = annotatedString,
        onClick = {
            annotatedString
                .getStringAnnotations("URL", it, it)
                .firstOrNull()?.let { stringAnnotation ->
                    uriHandler.openUri(stringAnnotation.item)
                }
        }
    )
}
