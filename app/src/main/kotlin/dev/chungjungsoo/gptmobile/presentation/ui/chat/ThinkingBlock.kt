package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import dev.chungjungsoo.gptmobile.presentation.theme.defaultSpatialSpec
import dev.chungjungsoo.gptmobile.presentation.theme.fastEffectsSpec

@Composable
fun ThinkingBlock(
    modifier: Modifier = Modifier,
    thoughts: String,
    contentIdentity: Any = thoughts,
    isLoading: Boolean = false
) {
    if (thoughts.isBlank()) return

    var isExpanded by rememberSaveable(contentIdentity) { mutableStateOf(false) }
    val expandLabel = stringResource(R.string.expand)
    val collapseLabel = stringResource(R.string.collapse)
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = fastEffectsSpec(),
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
                .heightIn(min = 48.dp)
                .clickable { isExpanded = !isExpanded }
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    stateDescription = if (isExpanded) collapseLabel else expandLabel
                }
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
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotationAngle)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = defaultSpatialSpec()) +
                fadeIn(animationSpec = fastEffectsSpec()),
            exit = shrinkVertically(animationSpec = defaultSpatialSpec()) +
                fadeOut(animationSpec = fastEffectsSpec())
        ) {
            val displayText = if (isLoading) thoughts + "●" else thoughts

            ChatMarkdown(
                content = displayText,
                contentIdentity = contentIdentity,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            )
        }

        if (!isExpanded && thoughts.isNotBlank()) {
            val preview = thoughts.take(100).replace("\n", " ") +
                if (thoughts.length > 100) "..." else ""
            Text(
                text = if (isLoading) preview + "●" else preview,
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
