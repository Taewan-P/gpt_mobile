package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.ui.material3.RichText
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme

@Composable
fun ThinkingBlock(
    modifier: Modifier = Modifier,
    thoughts: String,
    isLoading: Boolean = false
) {
    if (thoughts.isBlank()) return

    var isExpanded by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "rotation"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "💭",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isExpanded) {
                    stringResource(R.string.hide_thinking)
                } else {
                    stringResource(R.string.view_thinking)
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (isLoading) {
                Text(
                    text = stringResource(R.string.thinking_in_progress),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (isExpanded) {
                    stringResource(R.string.collapse)
                } else {
                    stringResource(R.string.expand)
                },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotationAngle)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            val parser = remember { CommonmarkAstNodeParser() }
            val displayText = if (isLoading) thoughts.trimIndent() + "●" else thoughts.trimIndent()
            val astNode = remember(displayText) { parser.parse(displayText) }

            RichText(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                BasicMarkdown(astNode = astNode)
            }
        }

        if (!isExpanded && thoughts.isNotBlank()) {
            Text(
                text = thoughts.take(100).replace("\n", " ") + if (thoughts.length > 100) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            )
        }
    }
}

@Preview
@Composable
private fun ThinkingBlockPreview() {
    val sampleThoughts = """
        Let me think about this step by step:
        
        1. First, I need to understand the problem
        2. Then, I'll analyze the requirements
        3. Finally, I'll provide a solution
        
        This is a longer thinking process that shows how the AI reasons through the problem.
    """.trimIndent()

    GPTMobileTheme {
        ThinkingBlock(
            thoughts = sampleThoughts,
            isLoading = false
        )
    }
}

@Preview
@Composable
private fun ThinkingBlockLoadingPreview() {
    GPTMobileTheme {
        ThinkingBlock(
            thoughts = "Analyzing the problem...",
            isLoading = true
        )
    }
}
