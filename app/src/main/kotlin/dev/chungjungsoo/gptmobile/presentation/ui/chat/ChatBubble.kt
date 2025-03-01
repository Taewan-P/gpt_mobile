package dev.chungjungsoo.gptmobile.presentation.ui.chat

import android.text.util.Linkify
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import dev.jeziellago.compose.markdowntext.MarkdownText

@Composable
fun UserChatBubble(
    modifier: Modifier = Modifier,
    text: String,
    isLoading: Boolean,
    onEditClick: () -> Unit,
    onCopyClick: () -> Unit
) {
    val cardColor = CardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        disabledContentColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
        disabledContainerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.38f)
    )

    Column(horizontalAlignment = Alignment.End) {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(32.dp),
            colors = cardColor
        ) {
            MarkdownText(
                modifier = Modifier.padding(16.dp),
                markdown = text,
                isTextSelectable = true,
                linkifyMask = Linkify.WEB_URLS
            )
        }
        Row {
            if (!isLoading) {
                EditTextChip(onEditClick)
                Spacer(modifier = Modifier.width(8.dp))
            }
            CopyTextChip(onCopyClick)
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
    onRetryClick: () -> Unit = {}
) {
    val cardColor = CardColors(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        disabledContentColor = MaterialTheme.colorScheme.background.copy(alpha = 0.38f),
        disabledContainerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f)
    )

    Column(modifier = modifier) {
        GPTMobileIcon()
        Column(horizontalAlignment = Alignment.End) {
            Card(
                shape = RoundedCornerShape(0.dp),
                colors = cardColor
            ) {
                MarkdownText(
                    modifier = Modifier.padding(24.dp),
                    markdown = text.trimIndent() + if (isLoading) "●" else "",
                    isTextSelectable = true,
                    linkifyMask = Linkify.WEB_URLS
                )
            }

            if (!isLoading) {
                Row {
                    if (!isError) {
                        CopyTextChip(onCopyClick)
                    }
                    if (canRetry) {
                        Spacer(modifier = Modifier.width(8.dp))
                        RetryChip(onRetryClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun GPTMobileIcon() {
    Box(
        modifier = Modifier
            .padding(start = 8.dp)
            .size(48.dp)
            .clip(RoundedCornerShape(48.dp))
            .background(color = Color(0xFF00A67D)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_gpt_mobile_no_padding),
            contentDescription = null,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
private fun EditTextChip(onEditClick: () -> Unit) {
    AssistChip(
        onClick = onEditClick,
        label = { Text(stringResource(R.string.edit)) },
        leadingIcon = {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.edit),
                modifier = Modifier.size(AssistChipDefaults.IconSize)
            )
        }
    )
}

@Composable
private fun CopyTextChip(onCopyClick: () -> Unit) {
    AssistChip(
        onClick = onCopyClick,
        label = { Text(stringResource(R.string.copy_text)) },
        leadingIcon = {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_copy),
                contentDescription = stringResource(R.string.copy_text),
                modifier = Modifier.size(AssistChipDefaults.IconSize)
            )
        }
    )
}

@Composable
private fun RetryChip(onRetryClick: () -> Unit) {
    AssistChip(
        onClick = onRetryClick,
        label = { Text(stringResource(R.string.retry)) },
        leadingIcon = {
            Icon(
                Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.retry),
                modifier = Modifier.size(AssistChipDefaults.IconSize)
            )
        }
    )
}

@Preview
@Composable
fun UserChatBubblePreview() {
    val sampleText = """
        How can I print hello world
        in Python?
    """.trimIndent()
    GPTMobileTheme {
        UserChatBubble(text = sampleText, isLoading = false, onCopyClick = {}, onEditClick = {})
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
