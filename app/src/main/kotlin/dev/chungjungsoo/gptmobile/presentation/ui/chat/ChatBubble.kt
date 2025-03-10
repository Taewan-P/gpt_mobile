package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.ui.material3.RichText
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme

@Composable
fun UserChatBubble(
    modifier: Modifier = Modifier,
    text: String,
    onLongPress: () -> Unit
) {
    val cardColor = CardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        disabledContentColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
        disabledContainerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.38f)
    )
    val parser = remember { CommonmarkAstNodeParser() }
    val astNode = remember(text) { parser.parse(text.trimIndent()) }

    Column(horizontalAlignment = Alignment.End) {
        Card(
            modifier = modifier
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onLongPress.invoke() })
                },
            shape = RoundedCornerShape(32.dp),
            colors = cardColor
        ) {
            RichText(modifier = Modifier.padding(16.dp)) {
                BasicMarkdown(astNode = astNode)
            }
        }
    }
}

@Composable
fun OpponentChatBubble(
    modifier: Modifier = Modifier,
    canRetry: Boolean,
    isLoading: Boolean,
    isError: Boolean = false,
    text: String,
    onCopyClick: () -> Unit = {},
    onSelectClick: () -> Unit = {},
    onRetryClick: () -> Unit = {}
) {
    val cardColor = CardColors(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        disabledContentColor = MaterialTheme.colorScheme.background.copy(alpha = 0.38f),
        disabledContainerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f)
    )
    val parser = remember { CommonmarkAstNodeParser() }
    val astNode = remember(text) { parser.parse(text.trimIndent() + if (isLoading) "●" else "") }

    Column(modifier = modifier) {
        Column {
            Card(
                shape = RoundedCornerShape(0.dp),
                colors = cardColor
            ) {
                RichText(modifier = Modifier.padding(16.dp)) {
                    BasicMarkdown(astNode = astNode)
                }
            }

            if (!isLoading) {
                Row(
                    modifier = Modifier.padding(start = 16.dp)
                ) {
                    if (!isError) {
                        CopyTextIcon(onCopyClick)
                        Spacer(modifier = Modifier.width(8.dp))
                        SelectTextIcon(onSelectClick)
                    }
                    if (canRetry) {
                        Spacer(modifier = Modifier.width(8.dp))
                        RetryIcon(onRetryClick)
                    }
                }
            }
        }
    }
}

@Composable
fun GPTMobileIcon(loading: Boolean) {
    Box(
        modifier = Modifier
            .padding(start = 8.dp)
            .size(40.dp)
            .clip(RoundedCornerShape(40.dp))
            .background(color = Color(0xFF00A67D)),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp)
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_gpt_mobile_no_padding),
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun PlatformButton(
    isLoading: Boolean,
    name: String,
    selected: Boolean,
    onPlatformClick: () -> Unit
) {
    val buttonContent: @Composable RowScope.() -> Unit = {
        Spacer(modifier = Modifier.width(12.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }

        Text(
            text = name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        if (isLoading) Spacer(modifier = Modifier.width(4.dp))
    }

    TextButton(
        modifier = Modifier.widthIn(max = 160.dp),
        onClick = onPlatformClick,
        colors = if (selected) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.textButtonColors(),
        content = buttonContent
    )
}

@Composable
private fun CopyTextIcon(onCopyClick: () -> Unit) {
    IconButton(onClick = onCopyClick) {
        Icon(
            imageVector = ImageVector.vectorResource(id = R.drawable.ic_copy),
            contentDescription = stringResource(R.string.copy_text)
        )
    }
}

@Composable
private fun SelectTextIcon(onSelectClick: () -> Unit) {
    IconButton(onClick = onSelectClick) {
        Icon(
            imageVector = ImageVector.vectorResource(id = R.drawable.ic_select),
            contentDescription = stringResource(R.string.select_text)
        )
    }
}

@Composable
private fun RetryIcon(onRetryClick: () -> Unit) {
    IconButton(onClick = onRetryClick) {
        Icon(
            Icons.Rounded.Refresh,
            contentDescription = stringResource(R.string.retry)
        )
    }
}

@Preview
@Composable
fun UserChatBubblePreview() {
    val sampleText = """
        How can I print hello world
        in Python?
    """.trimIndent()
    GPTMobileTheme {
        UserChatBubble(text = sampleText, onLongPress = {})
    }
}

@Preview
@Composable
fun OpponentChatBubblePreview() {
    val sampleText = """
        # Demo
    
        Emphasis, aka italics, with *asterisks* or _underscores_. Strong emphasis, aka bold, with **asterisks** or __underscores__. Combined emphasis with **asterisks and _underscores_**. [Links with two blocks, text in square-brackets, destination is in parentheses.](https://www.example.com). Inline `code` has `back-ticks around` it.
    
        1. First ordered list item
        2. Another item
            * Unordered sub-list.
        3. And another item.
            You can have properly indented paragraphs within list items. Notice the blank line above, and the leading spaces (at least one, but we'll use three here to also align the raw Markdown).
    
        * Unordered list can use asterisks
        - Or minuses
        + Or pluses
    """.trimIndent()
    GPTMobileTheme {
        OpponentChatBubble(
            text = sampleText,
            canRetry = true,
            isLoading = false,
            onCopyClick = {},
            onRetryClick = {}
        )
    }
}
