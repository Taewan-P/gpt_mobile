package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.commonmark.MarkdownParseOptions
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.ui.material3.RichText
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme

@Composable
fun UserChatBubble(
    modifier: Modifier = Modifier,
    text: String
) {
    val markdownParseOptions = remember { MarkdownParseOptions(autolink = false) }
    val parser = remember(markdownParseOptions) { CommonmarkAstNodeParser(markdownParseOptions) }
    val astNode = remember(text) { parser.parse(text.trimIndent()) }
    val cardColor = CardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        disabledContentColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
        disabledContainerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.38f)
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        colors = cardColor
    ) {
        RichText(modifier = Modifier.padding(16.dp)) {
            BasicMarkdown(astNode = astNode)
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
    val markdownParseOptions = remember { MarkdownParseOptions(autolink = false) }
    val parser = remember(markdownParseOptions) { CommonmarkAstNodeParser(markdownParseOptions) }
    val astNode = remember(text) { parser.parse(text.trimIndent() + if (isLoading) "▊" else "") }
    val cardColor = CardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        disabledContentColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.38f),
        disabledContainerColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.38f)
    )

    Column(modifier = modifier) {
        Column(horizontalAlignment = Alignment.End) {
            Card(
                shape = RoundedCornerShape(32.dp),
                colors = cardColor
            ) {
                RichText(modifier = Modifier.padding(24.dp)) {
                    BasicMarkdown(astNode = astNode)
                }
            }

            if (!isLoading) {
                Row {
                    if (!isError) {
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
                    Spacer(modifier = Modifier.width(8.dp))
                    if (canRetry) {
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
                }
            }
        }
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
        UserChatBubble(text = sampleText)
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
