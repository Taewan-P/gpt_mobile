package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRun
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItem
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItemType
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEvent
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEventStatus
import dev.chungjungsoo.gptmobile.data.database.entity.hasUnavailableAssistantOrder
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import dev.chungjungsoo.gptmobile.presentation.theme.defaultSpatialSpec
import dev.chungjungsoo.gptmobile.presentation.theme.fastEffectsSpec
import java.io.File

@Composable
fun UserChatBubble(
    modifier: Modifier = Modifier,
    text: String,
    files: List<String> = emptyList(),
    contentIdentity: Any = text,
    canEdit: Boolean = false,
    onCopyClick: () -> Unit = {},
    onEditClick: () -> Unit = {}
) {
    val cardColor = CardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        disabledContentColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
        disabledContainerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.38f)
    )

    var isActionsSheetOpen by rememberSaveable(contentIdentity) { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.End) {
        Card(
            modifier = modifier
                .pointerInput(contentIdentity) {
                    detectTapGestures(onLongPress = { isActionsSheetOpen = true })
                },
            shape = RoundedCornerShape(32.dp),
            colors = cardColor
        ) {
            ChatMarkdown(
                content = text,
                modifier = Modifier.padding(16.dp)
            )
        }
        MessageFileThumbnailRow(
            files = files,
            modifier = Modifier.padding(top = 8.dp)
        )
    }

    if (isActionsSheetOpen) {
        MessageActionsSheet(
            canEdit = canEdit,
            onCopy = onCopyClick,
            onEdit = onEditClick,
            onDismissRequest = { isActionsSheetOpen = false }
        )
    }
}

@Composable
fun OpponentChatBubble(
    modifier: Modifier = Modifier,
    canRetry: Boolean,
    isLoading: Boolean,
    isError: Boolean = false,
    text: String,
    thoughts: String = "",
    timeline: List<AssistantTimelineItem> = emptyList(),
    attachments: List<String> = emptyList(),
    agentRun: AgentRun? = null,
    runNotices: List<ChatRunNotice> = emptyList(),
    toolEvents: List<ToolEvent> = emptyList(),
    contentIdentity: Any = text,
    canEdit: Boolean = false,
    revisionIndexLabel: String? = null,
    canShowPreviousRevision: Boolean = false,
    canShowNextRevision: Boolean = false,
    onCopyClick: () -> Unit = {},
    onSelectClick: () -> Unit = {},
    onViewFull: (String) -> Unit = {},
    onRetryClick: () -> Unit = {},
    onEditClick: () -> Unit = {},
    onShowPreviousRevision: () -> Unit = {},
    onShowNextRevision: () -> Unit = {}
) {
    val cardColor = CardColors(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        disabledContentColor = MaterialTheme.colorScheme.background.copy(alpha = 0.38f),
        disabledContainerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f)
    )

    val noticeMessages = visibleChatRunNotices(
        stored = runNotices,
        timelineNotices = timelineNoticeMessages(timeline),
        isRunActive = isLoading
    )
    val contentTimeline = timeline.filter { it.type != AssistantTimelineItemType.NOTICE }
    val activeToolEvents = toolEvents.filter {
        it.status == ToolEventStatus.PENDING || it.status == ToolEventStatus.RUNNING
    }
    val hasUnresolvedToolDetails = hasUnresolvedToolReferences(contentTimeline, toolEvents)
    val hasDetails = thoughts.isNotBlank() ||
        toolEvents.isNotEmpty() ||
        hasUnresolvedToolDetails ||
        contentTimeline.any {
            it.type == AssistantTimelineItemType.THINKING ||
                it.type == AssistantTimelineItemType.LEGACY_ORDER
        }
    val hasUnavailableOrder = hasUnavailableAssistantOrder(
        timeline = contentTimeline,
        content = text,
        thoughts = thoughts,
        hasToolEvents = toolEvents.isNotEmpty()
    )
    var isDetailsExpanded by rememberSaveable(contentIdentity) { mutableStateOf(false) }
    val showAnswerStreamingIndicator = isLoading && activeToolEvents.isEmpty()
    val showProcessStreamingIndicator = showAnswerStreamingIndicator && text.isBlank()

    Column(modifier = modifier) {
        RunNoticeChips(
            notices = noticeMessages,
            modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp)
        )
        AgentRunStatusBlock(
            run = agentRun,
            modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp)
        )

        if (hasDetails) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DetailsButton(
                    isExpanded = isDetailsExpanded,
                    onClick = { isDetailsExpanded = !isDetailsExpanded }
                )
            }
        }

        AnimatedContent(
            targetState = isDetailsExpanded && hasDetails,
            transitionSpec = {
                fadeIn(animationSpec = fastEffectsSpec()) togetherWith
                    fadeOut(animationSpec = fastEffectsSpec()) using
                    SizeTransform(sizeAnimationSpec = { _, _ -> defaultSpatialSpec() })
            },
            label = "assistant details"
        ) { showDetails ->
            if (showDetails) {
                Column {
                    if (contentTimeline.isNotEmpty() && !hasUnavailableOrder) {
                        AssistantProcessContent(
                            timeline = contentTimeline,
                            toolEvents = toolEvents,
                            showStreamingIndicator = showProcessStreamingIndicator,
                            contentIdentity = contentIdentity,
                            onViewFull = onViewFull
                        )
                    } else {
                        LegacyAssistantProcessContent(
                            thoughts = thoughts,
                            toolEvents = toolEvents,
                            contentIdentity = contentIdentity,
                            showOrderNotice = hasUnavailableOrder,
                            showStreamingIndicator = showProcessStreamingIndicator,
                            onViewFull = onViewFull
                        )
                    }
                    QuietAssistantContent(
                        cardColor = cardColor,
                        text = text,
                        attachments = attachments,
                        showStreamingIndicator = showAnswerStreamingIndicator && text.isNotBlank(),
                        contentIdentity = contentIdentity,
                        isStreaming = isLoading
                    )
                }
            } else {
                QuietAssistantContent(
                    cardColor = cardColor,
                    text = text,
                    attachments = attachments,
                    showStreamingIndicator = showAnswerStreamingIndicator,
                    contentIdentity = contentIdentity,
                    isStreaming = isLoading
                )
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
                    if (canEdit) {
                        Spacer(modifier = Modifier.width(8.dp))
                        EditTextIcon(onEditClick)
                    }
                }
                if (canRetry) {
                    Spacer(modifier = Modifier.width(8.dp))
                    RetryIcon(onRetryClick)
                }
            }
            if (canRetry) {
                Text(
                    text = stringResource(R.string.retry_tools_warning),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                )
            }

            revisionIndexLabel?.let { label ->
                Row(
                    modifier = Modifier.padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        enabled = canShowPreviousRevision,
                        onClick = onShowPreviousRevision
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.previous_revision)
                        )
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        enabled = canShowNextRevision,
                        onClick = onShowNextRevision
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.next_revision)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuietAssistantContent(
    cardColor: CardColors,
    text: String,
    attachments: List<String>,
    showStreamingIndicator: Boolean,
    contentIdentity: Any,
    isStreaming: Boolean = false
) {
    Card(
        shape = RoundedCornerShape(0.dp),
        colors = cardColor
    ) {
        Column {
            ChatMarkdown(
                content = text,
                contentIdentity = contentIdentity,
                isStreaming = isStreaming,
                modifier = Modifier.padding(16.dp)
            )
            if (showStreamingIndicator) {
                Text(
                    text = "●",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            MessageFileThumbnailRow(
                files = attachments,
                usePrimaryColors = false,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun DetailsButton(
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = fastEffectsSpec(),
        label = "details rotation"
    )
    val expandStateDescription = stringResource(R.string.expand)
    val collapseStateDescription = stringResource(R.string.collapse)
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                stateDescription = if (isExpanded) {
                    collapseStateDescription
                } else {
                    expandStateDescription
                }
            }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.details),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(rotationAngle)
        )
    }
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

@Composable
private fun EditTextIcon(onEditClick: () -> Unit) {
    IconButton(onClick = onEditClick) {
        Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = stringResource(R.string.edit)
        )
    }
}

@Composable
private fun AssistantProcessContent(
    timeline: List<AssistantTimelineItem>,
    toolEvents: List<ToolEvent>,
    showStreamingIndicator: Boolean,
    contentIdentity: Any,
    onViewFull: (String) -> Unit
) {
    val toolEventsBySequence = toolEvents.associateBy(ToolEvent::sequence)
    val lastProcessIndex = timeline.indexOfLast {
        it.type == AssistantTimelineItemType.THINKING ||
            it.type == AssistantTimelineItemType.TOOL
    }
    timeline.forEachIndexed { index, item ->
        key(contentIdentity, item.type, item.toolSequence, index) {
            when (item.type) {
                AssistantTimelineItemType.THINKING -> ThinkingBlock(
                    modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp),
                    thoughts = item.content,
                    contentIdentity = "$contentIdentity:thinking:$index",
                    isLoading = showStreamingIndicator && index == lastProcessIndex
                )

                AssistantTimelineItemType.TEXT,
                AssistantTimelineItemType.NOTICE,
                AssistantTimelineItemType.LEGACY_ORDER -> Unit

                AssistantTimelineItemType.TOOL -> {
                    val event = item.toolSequence?.let(toolEventsBySequence::get)
                    if (event == null) {
                        Text(
                            text = stringResource(R.string.details_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    } else {
                        ToolTraceBlock(
                            events = listOf(event),
                            modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp),
                            contentIdentity = "$contentIdentity:tool:${event.sequence}",
                            onViewFull = onViewFull
                        )
                    }
                }
            }
        }
    }
    if (
        showStreamingIndicator &&
        lastProcessIndex >= 0 &&
        timeline[lastProcessIndex].type == AssistantTimelineItemType.TOOL
    ) {
        Text(
            text = "●",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

internal fun hasUnresolvedToolReferences(
    timeline: List<AssistantTimelineItem>,
    events: List<ToolEvent>
): Boolean {
    val sequences = events.mapTo(mutableSetOf(), ToolEvent::sequence)
    return timeline.any { item ->
        item.type == AssistantTimelineItemType.TOOL &&
            (item.toolSequence == null || item.toolSequence !in sequences)
    }
}

@Composable
private fun LegacyAssistantProcessContent(
    thoughts: String,
    toolEvents: List<ToolEvent>,
    contentIdentity: Any,
    showOrderNotice: Boolean,
    showStreamingIndicator: Boolean,
    onViewFull: (String) -> Unit
) {
    val isThinking = showStreamingIndicator && thoughts.isNotBlank()
    if (showOrderNotice) {
        Text(
            text = stringResource(R.string.legacy_assistant_order_unavailable),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, start = 16.dp, end = 16.dp)
        )
    }
    if (thoughts.isNotBlank()) {
        ThinkingBlock(
            modifier = Modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp),
            thoughts = thoughts,
            contentIdentity = contentIdentity,
            isLoading = isThinking
        )
    }
    ToolTraceBlock(
        events = toolEvents,
        modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp),
        contentIdentity = contentIdentity,
        onViewFull = onViewFull
    )
    if (showStreamingIndicator && thoughts.isBlank()) {
        Text(
            text = "●",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
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
    name: String,
    selected: Boolean,
    onPlatformClick: () -> Unit
) {
    val buttonContent: @Composable RowScope.() -> Unit = {
        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
    }

    TextButton(
        modifier = Modifier.widthIn(max = 160.dp),
        onClick = onPlatformClick,
        colors = if (selected) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.textButtonColors(),
        content = buttonContent
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
        UserChatBubble(text = sampleText, files = emptyList())
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
            revisionIndexLabel = "Revision 1/1",
            onCopyClick = {},
            onRetryClick = {}
        )
    }
}

@Composable
internal fun MessageFileThumbnailRow(
    files: List<String>,
    modifier: Modifier = Modifier,
    usePrimaryColors: Boolean = true
) {
    // Filter out empty strings and check if we have valid files
    val validFiles = files.filter { it.isNotEmpty() && it.isNotBlank() }

    if (validFiles.isEmpty()) {
        return
    }

    Row(
        modifier = modifier
            .wrapContentHeight()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        validFiles.forEach { filePath ->
            MessageFileThumbnail(
                filePath = filePath,
                usePrimaryColors = usePrimaryColors
            )
        }
    }
}

@Composable
private fun MessageFileThumbnail(
    filePath: String,
    usePrimaryColors: Boolean
) {
    val file = File(filePath)
    val isImage = isImageFile(file.extension)
    val containerColor = if (usePrimaryColors) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (usePrimaryColors) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier.width(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(containerColor)
        ) {
            if (isImage) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_image),
                    contentDescription = file.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    tint = contentColor
                )
            } else {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_file),
                    contentDescription = file.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    tint = contentColor
                )
            }
        }

        Text(
            text = file.name,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .padding(top = 4.dp)
                .width(56.dp)
        )
    }
}

private fun isImageFile(extension: String?): Boolean {
    val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    return extension?.lowercase() in imageExtensions
}
