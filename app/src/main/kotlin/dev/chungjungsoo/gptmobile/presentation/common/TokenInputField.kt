package dev.chungjungsoo.gptmobile.presentation.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R

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
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
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
    val annotatedString = buildAnnotatedString {
        val str = stringResource(R.string.need_help)
        withLink(LinkAnnotation.Url(url = helpLink)) {
            append(str)
        }
    }
    Text(annotatedString)
}
