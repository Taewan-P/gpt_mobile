package dev.chungjungsoo.gptmobile.presentation.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme

@Preview
@Composable
fun PrimaryLongButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    text: String = ""
) {
    GPTMobileTheme {
        Button(
            modifier = modifier
                .padding(20.dp)
                .fillMaxWidth()
                .height(56.dp),
            onClick = onClick,
            enabled = enabled
        ) {
            Text(text = text)
        }
    }
}
