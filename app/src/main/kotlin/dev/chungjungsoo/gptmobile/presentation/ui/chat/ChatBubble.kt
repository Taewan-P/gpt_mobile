package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRun
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItem
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItemType
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEvent
import dev.chungjungsoo.gptmobile.data.database.entity.hasUnavailableAssistantOrder
import dev.chungjungsoo.gptmobile.data.localruntime.DiagnosticsTelemetryProvider
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import dev.chungjungsoo.gptmobile.presentation.theme.fastEffectsSpec
import dev.chungjungsoo.gptmobile.presentation.ui.thinking.ThinkingParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal fun formatMessageTimestamp(timestampMillis: Long?): String {
    if (timestampMillis == null || timestampMillis <= 0) return ""
    return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestampMillis))
}

internal fun shouldShowContinuePrompt(
    text: String,
    isLoading: Boolean,
    isLastMessage: Boolean = false
): Boolean = ChatResponseActionParser.shouldShowContinuePrompt(text, isLoading, isLastMessage)

@Composable
fun UserChatBubble(
    modifier: Modifier = Modifier,
    text: String,
    timestamp: Long? = null,
    files: List<String> = emptyList(),
    hasDetails: Boolean = false,
    areDetailsVisible: Boolean = false,
    onToggleDetails: () -> Unit = {},
    canEdit: Boolean = false,
    onCopyClick: () -> Unit = {},
    onSelectClick: () -> Unit = {},
    onEditClick: () -> Unit = {},
    onLongPress: () -> Unit
) {
    var actionsExpanded by rememberSaveable { mutableStateOf(false) }
    val cardColor = CardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        disabledContentColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
        disabledContainerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.38f)
    )
    val formattedTime = remember(timestamp) { formatMessageTimestamp(timestamp) }

    Column(horizontalAlignment = Alignment.End) {
        Card(
            modifier = modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) },
            shape = RoundedCornerShape(32.dp),
            colors = cardColor
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp)) {
                ChatMarkdown(content = text, modifier = Modifier.alpha(0.7f))
                if (formattedTime.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Light),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(visible = actionsExpanded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CopyTextIcon(onCopyClick)
                    SelectTextIcon(onSelectClick)
                    if (canEdit) EditTextIcon(onEditClick)
                }
            }
            MessageActionsToggle(actionsExpanded) { actionsExpanded = !actionsExpanded }
        }
        if (hasDetails) {
            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .padding(top = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DetailsButton(
                    isVisible = areDetailsVisible,
                    isEnabled = true,
                    onClick = onToggleDetails
                )
            }
        }
        MessageFileThumbnailRow(files = files, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun OpponentChatBubble(
    modifier: Modifier = Modifier,
    canRetry: Boolean,
    isLoading: Boolean,
    isError: Boolean = false,
    text: String,
    timestamp: Long? = null,
    thoughts: String = "",
    timeline: List<AssistantTimelineItem> = emptyList(),
    attachments: List<String> = emptyList(),
    agentRun: AgentRun? = null,
    runNotices: List<ChatRunNotice> = emptyList(),
    toolEvents: List<ToolEvent> = emptyList(),
    locationToolEvents: List<ToolEvent> = toolEvents,
    contentIdentity: Any = text,
    canEdit: Boolean = false,
    isFavorite: Boolean = false,
    debugMode: Boolean = false,
    invocations: List<dev.chungjungsoo.gptmobile.data.accounting.ModelInvocation> = emptyList(),
    debugSettings: dev.chungjungsoo.gptmobile.data.model.AppFeatureSettings = dev.chungjungsoo.gptmobile.data.model.AppFeatureSettings(),
    showReasoning: Boolean = true,
    revisionIndexLabel: String? = null,
    canShowPreviousRevision: Boolean = false,
    canShowNextRevision: Boolean = false,
    isUserTyping: Boolean = false,
    isLastMessage: Boolean = false,
    onCopyClick: () -> Unit = {},
    onSelectClick: () -> Unit = {},
    onRetryClick: () -> Unit = {},
    onEditClick: () -> Unit = {},
    onFavoriteClick: () -> Unit = {},
    onFavoriteLongPress: () -> Unit = {},
    onShowPreviousRevision: () -> Unit = {},
    onShowNextRevision: () -> Unit = {},
    onContinueClick: (() -> Unit)? = null,
    onActionClick: ((String) -> Unit)? = null
) {
    var activityExpanded by rememberSaveable(contentIdentity) { mutableStateOf(false) }
    val bubbleColor = animateColorAsState(
        targetValue = if (activityExpanded) MaterialTheme.colorScheme.primary.copy(alpha = if (isFavorite) 0.138f else 0.069f) else Color.Transparent,
        animationSpec = tween(durationMillis = 1000),
        label = "activityBackground"
    ).value
    val noticeMessages = remember(runNotices, timeline, isLoading) {
        visibleChatRunNotices(runNotices, timelineNoticeMessages(timeline), isLoading)
    }
    val visibleThoughts = remember(showReasoning, thoughts, text) {
        if (showReasoning) thoughts.ifBlank { ThinkingParser.extractThinking(text).thinking.orEmpty() } else ""
    }
    val processToolEvents = toolEvents
    val contentTimeline = remember(timeline, showReasoning) {
        timeline.filter { showReasoning || it.type != AssistantTimelineItemType.THINKING }
    }
    val (telemetryNotice, nonTelemetryNotices) = remember(noticeMessages) {
        extractTelemetryNotice(noticeMessages)
    }
    val formattedTime = remember(timestamp) { formatMessageTimestamp(timestamp) }
    val showContinueAction = remember(text, isLoading, isLastMessage) { shouldShowContinuePrompt(text, isLoading, isLastMessage) }
    val dynamicActions = remember(text, isLoading) { ChatResponseActionParser.extractDynamicActions(text, isLoading) }
    var continueDismissed by rememberSaveable(contentIdentity) { mutableStateOf(false) }
    var actionDismissed by rememberSaveable(contentIdentity) { mutableStateOf(false) }
    var actionsExpanded by rememberSaveable(contentIdentity) { mutableStateOf(false) }

    // Suggestion Button Hold-to-Highlight State
    val highlightProgress = remember { Animatable(0f) }
    var activeHighlightedSentence by remember { mutableStateOf<String?>(null) }
    var activeHighlightJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun startHighlight(buttonText: String, promptText: String) {
        val matchingSentence = SuggestionHighlightManager.findMatchingSentence(text, buttonText, promptText)
        if (matchingSentence != null) {
            activeHighlightedSentence = matchingSentence
            activeHighlightJob?.cancel()
            activeHighlightJob = coroutineScope.launch {
                val current = highlightProgress.value
                val remainingRatio = (1f - current).coerceAtLeast(0f)
                val duration = (SuggestionHighlightManager.ANIMATION_DURATION_MS * remainingRatio).toInt()
                highlightProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = duration, easing = LinearEasing)
                )
                while (true) {
                    highlightProgress.animateTo(
                        targetValue = 0.68f,
                        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
                    )
                    highlightProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
                    )
                }
            }
        }
    }

    fun reverseHighlight() {
        activeHighlightJob?.cancel()
        activeHighlightJob = coroutineScope.launch {
            val current = highlightProgress.value
            val duration = (SuggestionHighlightManager.ANIMATION_DURATION_MS * current).toInt()
            highlightProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = duration, easing = LinearEasing)
            )
            if (highlightProgress.value == 0f) {
                activeHighlightedSentence = null
            }
        }
    }

    var areDetailsVisible by rememberSaveable(contentIdentity) {
        mutableStateOf(isLoading)
    }

    val detailsTimeline = remember(contentTimeline, showReasoning, debugMode) {
        processTimelineForDisplay(contentTimeline, showReasoning, debugMode)
    }
    val hasDetails =
        remember(detailsTimeline, visibleThoughts, processToolEvents) {
            hasAssistantProcessDetails(
                timeline = detailsTimeline,
                fallbackThoughts = visibleThoughts,
                hasToolEvents = processToolEvents.isNotEmpty()
            )
        }

    val showAnswerStreamingIndicator = isLoading
    val showProcessStreamingIndicator = showAnswerStreamingIndicator && text.isBlank()

    val hasVisibleText = text.isNotBlank() ||
        (debugMode && showAnswerStreamingIndicator && (!hasDetails || areDetailsVisible))
    val hasVisibleProcess = hasDetails && areDetailsVisible
    val hasVisibleExtras = (debugMode && (nonTelemetryNotices.isNotEmpty() || agentRun != null)) ||
        attachments.isNotEmpty() ||
        locationToolEvents.isNotEmpty() ||
        (!isLoading && (canRetry || canEdit || isError))
    val shouldShowBubble = isLoading || contentTimeline.isNotEmpty() || toolEvents.isNotEmpty() || hasVisibleText || hasVisibleProcess || hasVisibleExtras

    Column(modifier = modifier) {
        AnimatedVisibility(
            visible = shouldShowBubble,
            enter = fadeIn(animationSpec = tween(1500)),
            exit = fadeOut(animationSpec = tween(650))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = bubbleColor,
                        shape = RoundedCornerShape(32.dp)
                    )
            ) {
                val hasUnavailableOrder = remember(contentTimeline, text, visibleThoughts, processToolEvents) {
                    hasUnavailableAssistantOrder(contentTimeline, text, visibleThoughts, processToolEvents.isNotEmpty())
                }

                AssistantChronologicalContent(
                    timeline = contentTimeline,
                    toolEvents = toolEvents,
                    fallbackText = text,
                    fallbackThoughts = visibleThoughts,
                    contentIdentity = contentIdentity,
                    isLoading = isLoading,
                    debugMode = debugMode,
                    showReasoning = showReasoning,
                    isError = isError,
                    expanded = activityExpanded,
                    onExpandedChange = { activityExpanded = it }
                )

                LocationToolMapPreview(
                    toolEvents = locationToolEvents,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                MessageFileThumbnailRow(
                    files = attachments,
                    usePrimaryColors = false,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    if (!isLoading) {
                        AnimatedVisibility(
                            visible = actionsExpanded,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(120))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isError) {
                                    CopyTextIcon(onCopyClick)
                                    Spacer(Modifier.width(4.dp))
                                    SelectTextIcon(onSelectClick)
                                    Spacer(Modifier.width(4.dp))
                                    FavoriteIcon(isFavorite, onFavoriteClick, onFavoriteLongPress)
                                    if (canEdit) {
                                        Spacer(Modifier.width(4.dp))
                                        EditTextIcon(onEditClick)
                                    }
                                }
                                if (canRetry) {
                                    Spacer(Modifier.width(4.dp))
                                    RetryIcon(onRetryClick)
                                }
                            }
                        }

                        MessageActionsToggle(actionsExpanded) { actionsExpanded = !actionsExpanded }
                    }
                }

                if (debugMode && activityExpanded) {
                    ChatDebugDiagnosticsCard(
                        agentRun = agentRun,
                        telemetryNotice = telemetryNotice,
                        notices = (nonTelemetryNotices + contentTimeline.filter { it.type == AssistantTimelineItemType.NOTICE && isContextDiagnostic(it.content) }.map { it.content }).distinct(),
                        invocations = invocations,
                        settings = debugSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                // Dynamic Action Buttons strip (if assistant proposed choices or options).
                // Manual typing takes priority and hides the generated suggestions.
                if (dynamicActions.isNotEmpty() && onActionClick != null && !actionDismissed && !isUserTyping && !isLoading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val allInteractionSource = remember { MutableInteractionSource() }
                        val allPressed by allInteractionSource.collectIsPressedAsState()
                        val allPulse = suggestionHoldPulseAlpha(allPressed)
                        LaunchedEffect(allInteractionSource, dynamicActions) {
                            allInteractionSource.interactions.collect { interaction ->
                                when (interaction) {
                                    is PressInteraction.Press -> startHighlight("all of the above", dynamicActions.joinToString(" ") { it.actionPrompt })
                                    is PressInteraction.Release, is PressInteraction.Cancel -> reverseHighlight()
                                }
                            }
                        }
                        AssistChip(
                            onClick = {
                                actionDismissed = true
                                onActionClick(
                                    buildString {
                                        append("Please do all of the following: ")
                                        append(dynamicActions.joinToString("; ") { it.actionPrompt })
                                    }
                                )
                            },
                            label = { Text("All of the above", maxLines = 1) },
                            leadingIcon = {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            interactionSource = allInteractionSource,
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (allPressed) Color(0xFFFFD54F).copy(alpha = allPulse) else MaterialTheme.colorScheme.primaryContainer,
                                labelColor = if (allPressed) Color(0xFF3E2723) else MaterialTheme.colorScheme.onPrimaryContainer,
                                leadingIconContentColor = if (allPressed) Color(0xFF3E2723) else MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            border = BorderStroke(
                                if (allPressed) 2.dp else 1.dp,
                                if (allPressed) Color(0xFFFFB300) else MaterialTheme.colorScheme.outlineVariant
                            )
                        )

                        dynamicActions.forEach { action ->
                            val icon = when (action.iconType) {
                                ActionIconType.SEARCH -> Icons.Default.Search
                                ActionIconType.SUMMARIZE -> Icons.Default.Description
                                ActionIconType.EXPLAIN -> Icons.Default.HelpOutline
                                ActionIconType.CONFIRM -> Icons.Default.Check
                                ActionIconType.CANCEL -> Icons.Default.Close
                                ActionIconType.OPTION -> Icons.Default.AutoAwesome
                                ActionIconType.DEFAULT -> Icons.AutoMirrored.Filled.ArrowForward
                            }

                            val chipInteractionSource = remember { MutableInteractionSource() }
                            val chipPressed by chipInteractionSource.collectIsPressedAsState()
                            val chipPulse = suggestionHoldPulseAlpha(chipPressed)

                            LaunchedEffect(chipInteractionSource, action) {
                                chipInteractionSource.interactions.collect { interaction ->
                                    when (interaction) {
                                        is PressInteraction.Press -> {
                                            startHighlight(action.label, action.actionPrompt)
                                        }
                                        is PressInteraction.Release, is PressInteraction.Cancel -> {
                                            reverseHighlight()
                                        }
                                    }
                                }
                            }

                            AssistChip(
                                onClick = {
                                    actionDismissed = true
                                    onActionClick(action.actionPrompt)
                                },
                                label = {
                                    Text(
                                        text = action.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                interactionSource = chipInteractionSource,
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (chipPressed) Color(0xFFFFD54F).copy(alpha = chipPulse) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                    labelColor = if (chipPressed) Color(0xFF3E2723) else MaterialTheme.colorScheme.onSecondaryContainer,
                                    leadingIconContentColor = if (chipPressed) Color(0xFF3E2723) else MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                border = BorderStroke(
                                    if (chipPressed) 2.dp else 1.dp,
                                    if (chipPressed) Color(0xFFFFB300) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            )
                        }
                    }
                }

                // Minimal transparent continuation chip & bottom-right aligned timestamp
                val isContinueVisible = showContinueAction && onContinueClick != null && !continueDismissed && !isUserTyping
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedVisibility(
                        visible = activityExpanded && formattedTime.isNotBlank(),
                        enter = fadeIn(tween(1000)),
                        exit = fadeOut(tween(1000))
                    ) {
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Light),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    AnimatedVisibility(
                        visible = isContinueVisible,
                        enter = fadeIn(tween(300)),
                        exit = fadeOut(tween(500))
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "continuePulse")
                        val pulseAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.45f,
                            targetValue = 0.95f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulseAlpha"
                        )

                        val continueInteractionSource = remember { MutableInteractionSource() }
                        val continuePressed by continueInteractionSource.collectIsPressedAsState()
                        val continueHoldPulse = suggestionHoldPulseAlpha(continuePressed)

                        LaunchedEffect(continueInteractionSource) {
                            continueInteractionSource.interactions.collect { interaction ->
                                when (interaction) {
                                    is PressInteraction.Press -> startHighlight("continue", "continue")
                                    is PressInteraction.Release, is PressInteraction.Cancel -> reverseHighlight()
                                }
                            }
                        }

                        SuggestionChip(
                            onClick = {
                                continueDismissed = true
                                onContinueClick?.invoke()
                            },
                            label = { Text("Continue") },
                            icon = {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Continue",
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            interactionSource = continueInteractionSource,
                            border = BorderStroke(
                                if (continuePressed) 2.dp else 1.5.dp,
                                if (continuePressed) Color(0xFFFFB300) else MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
                            ),
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (continuePressed) Color(0xFFFFD54F).copy(alpha = continueHoldPulse) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = pulseAlpha * 0.6f),
                                labelColor = if (continuePressed) Color(0xFF3E2723) else MaterialTheme.colorScheme.onPrimaryContainer,
                                iconContentColor = if (continuePressed) Color(0xFF3E2723) else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                if (!isLoading) {
                    revisionIndexLabel?.let { label ->
                        Row(
                            modifier = Modifier.padding(start = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(enabled = canShowPreviousRevision, onClick = onShowPreviousRevision) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    stringResource(R.string.previous_revision),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = if (canShowPreviousRevision) 1f else 0.38f)
                                )
                            }
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(enabled = canShowNextRevision, onClick = onShowNextRevision) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    stringResource(R.string.next_revision),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = if (canShowNextRevision) 1f else 0.38f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun suggestionHoldPulseAlpha(pressed: Boolean): Float {
    val transition = rememberInfiniteTransition(label = "suggestionHoldPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.58f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "suggestionHoldPulseAlpha"
    )
    return if (pressed) pulse else 1f
}

@Composable
fun OpponentResponseContainer(
    isFavorite: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    // The response itself owns its disclosure background, including favourites.
    Column(modifier = modifier, content = content)
}

@Composable
internal fun DetailsButton(
    isVisible: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    label: String = stringResource(R.string.details)
) {
    val rotation by animateFloatAsState(
        targetValue = if (isVisible) 180f else 0f,
        animationSpec = fastEffectsSpec(),
        label = "detailsArrowRotation"
    )

    val expandedDesc = stringResource(R.string.tool_trace_collapse)
    val collapsedDesc = stringResource(R.string.tool_trace_expand)
    val unavailableDesc = stringResource(R.string.details_unavailable)

    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(
                enabled = isEnabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics {
                role = Role.Button
                stateDescription = if (!isEnabled) {
                    unavailableDesc
                } else if (isVisible) {
                    expandedDesc
                } else {
                    collapsedDesc
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier
                .size(16.dp)
                .rotate(rotation),
            tint = if (isEnabled) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.19f)
            }
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isEnabled) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.19f)
            }
        )
    }
}

@Composable
fun GPTMobileIcon(loading: Boolean) {
    Box(
        modifier = Modifier
            .padding(start = 4.dp)
            .size(40.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = Color(0xFF00E5FF),
                strokeWidth = 2.5.dp,
                trackColor = Color.Transparent
            )
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0xFF00BCD4)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_gpt_mobile_no_padding),
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun PlatformButton(
    isLoading: Boolean,
    name: String,
    selected: Boolean,
    disabled: Boolean = false,
    onPlatformClick: () -> Unit,
    onPlatformLongPress: () -> Unit = {}
) {
    val currentClick by rememberUpdatedState(onPlatformClick)
    val currentLongPress by rememberUpdatedState(onPlatformLongPress)
    val haptic = LocalHapticFeedback.current
    val content: @Composable RowScope.() -> Unit = {
        Spacer(Modifier.width(12.dp))
        if (isLoading) {
            CircularProgressIndicator(Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        if (isLoading) Spacer(Modifier.width(4.dp))
    }
    Surface(
        modifier = Modifier
            .widthIn(max = 160.dp)
            .alpha(if (disabled) 0.5f else 1f)
            .clip(RoundedCornerShape(24.dp))
            .semantics {
                role = Role.Button
                stateDescription = if (disabled) "Paused" else "Active"
                onClick {
                    currentClick()
                    true
                }
                onLongClick(label = if (disabled) "Resume AI profile" else "Pause AI profile") {
                    currentLongPress()
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val released = withTimeoutOrNull(1000L) {
                        waitForUpOrCancellation()?.let {
                            it.consume()
                            true
                        } ?: false
                    }
                    when (released) {
                        true -> currentClick()
                        false -> Unit
                        null -> {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentLongPress()
                            waitForUpOrCancellation()?.consume()
                        }
                    }
                }
            },
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable private fun CopyTextIcon(onClick: () -> Unit) = IconButton(onClick = onClick) {
    Icon(ImageVector.vectorResource(R.drawable.ic_copy), stringResource(R.string.copy_text), tint = MaterialTheme.colorScheme.primary)
}

@Composable private fun SelectTextIcon(onClick: () -> Unit) = IconButton(onClick = onClick) {
    Icon(ImageVector.vectorResource(R.drawable.ic_select), stringResource(R.string.select_text), tint = MaterialTheme.colorScheme.primary)
}

@Composable
private fun FavoriteIcon(
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    onFavoriteLongPress: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onFavoriteClick() },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onFavoriteLongPress()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            stringResource(if (isFavorite) R.string.unfavorite else R.string.favorite),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = if (isFavorite) 1f else 0.7f)
        )
    }
}

@Composable private fun RetryIcon(onClick: () -> Unit) = IconButton(onClick = onClick) {
    Icon(Icons.Rounded.Refresh, stringResource(R.string.retry), tint = MaterialTheme.colorScheme.primary)
}

@Composable private fun EditTextIcon(onClick: () -> Unit) = IconButton(onClick = onClick) {
    Icon(Icons.Outlined.Edit, stringResource(R.string.edit), tint = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun TelemetryBadge(notice: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.semantics { contentDescription = notice },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text = notice,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** One live inspection panel per response; context details never repeat in its timeline. */
@Composable
internal fun ChatDebugDiagnosticsCard(
    agentRun: AgentRun?,
    telemetryNotice: String?,
    notices: List<String> = emptyList(),
    invocations: List<dev.chungjungsoo.gptmobile.data.accounting.ModelInvocation> = emptyList(),
    settings: dev.chungjungsoo.gptmobile.data.model.AppFeatureSettings = dev.chungjungsoo.gptmobile.data.model.AppFeatureSettings(),
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val running = agentRun?.status in setOf("RUNNING", "QUEUED")
    val now = dev.chungjungsoo.gptmobile.presentation.ui.setting.rememberLiveClock(running)
    val hardware = if (settings.debugShowHardware) dev.chungjungsoo.gptmobile.presentation.ui.setting.rememberLiveHardware("Device diagnostics", "See Local Model settings", running) else null
    val contextNotices = notices.filter(::isContextDiagnostic).distinct()
    val report = buildString {
        agentRun?.let { appendLine("Run ${it.runId} · ${it.status} · ${it.providerSnapshot} / ${it.modelSnapshot}") }
        invocations.forEach { appendLine("${it.id} · ${it.kind} · ${it.status} · ${it.durationMs} ms · input ${it.inputTokens} / output ${it.outputTokens}${if (it.estimated) " (estimate)" else ""}") }
        contextNotices.forEach { appendLine(it) }
        hardware?.let { appendLine(DiagnosticsTelemetryProvider.formatDiagnosticsText(it, telemetryNotice)) }
    }
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (running) "Live diagnostics" else "Response diagnostics", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { clipboard.setText(AnnotatedString(dev.chungjungsoo.gptmobile.data.security.DiagnosticRedactor.redact(report))) }) { Text("Copy") }
            }
            if (invocations.isEmpty()) {
                agentRun?.let { run ->
                    dev.chungjungsoo.gptmobile.presentation.ui.setting.MetricLine("Run status", run.status.lowercase())
                    if (settings.debugShowTotalTokens) dev.chungjungsoo.gptmobile.presentation.ui.setting.MetricLine("Reported input / output", "${run.inputTokens ?: "—"} / ${run.outputTokens ?: "—"}")
                }
            } else {
                invocations.distinctBy { it.id }.forEach { request ->
                    dev.chungjungsoo.gptmobile.presentation.ui.setting.RequestDiagnostic(request, now, settings)
                }
            }
            if (settings.debugShowTotalTokens) contextNotices.forEach { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
            if (settings.debugShowTokenSpeed) telemetryNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (settings.debugShowHardware) hardware?.let { dev.chungjungsoo.gptmobile.presentation.ui.setting.HardwareDiagnostic(it) }
            agentRun?.terminalError?.let { Text(dev.chungjungsoo.gptmobile.data.security.DiagnosticRedactor.redact(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

internal fun isTelemetryNotice(message: String): Boolean =
    message.startsWith("Local: ") && message.contains("tok/s")

internal fun extractTelemetryNotice(notices: List<String>): Pair<String?, List<String>> {
    val telemetry = notices.firstOrNull(::isTelemetryNotice)
    val remaining = notices.filterNot(::isTelemetryNotice)
    return telemetry to remaining
}

internal fun buildDiagnosticsHudText(
    agentRun: AgentRun?,
    telemetryNotice: String?,
    debugMode: Boolean
): String? {
    if (!debugMode) return null

    val parts = mutableListOf<String>()
    agentRun?.modelSnapshot?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    agentRunDurationSeconds(agentRun ?: return telemetryNotice)?.let { duration ->
        parts.add("${duration}s")
    }
    telemetryNotice?.takeIf { it.isNotBlank() }?.let { parts.add(it) }

    return if (parts.isNotEmpty()) parts.joinToString(" • ") else telemetryNotice
}

@Preview
@Composable
fun UserChatBubblePreview() {
    val sampleText = "How can I print hello world in Python?"
    GPTMobileTheme {
        UserChatBubble(text = sampleText, files = emptyList(), onLongPress = {})
    }
}

@Composable
internal fun MessageFileThumbnailRow(files: List<String>, modifier: Modifier = Modifier, usePrimaryColors: Boolean = true) {
    val validFiles = remember(files) { files.filter(String::isNotBlank) }
    if (validFiles.isEmpty()) return
    Row(
        modifier = modifier.wrapContentHeight().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) { validFiles.forEach { MessageFileThumbnail(it, usePrimaryColors) } }
}

@Composable
private fun MessageFileThumbnail(filePath: String, usePrimaryColors: Boolean) {
    val file = remember(filePath) { File(filePath) }
    val isImage = remember(file.extension) { isImageFile(file.extension) }
    val container = if (usePrimaryColors) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .7f) else MaterialTheme.colorScheme.surfaceVariant
    val content = if (usePrimaryColors) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(container)) {
            Icon(
                ImageVector.vectorResource(if (isImage) R.drawable.ic_image else R.drawable.ic_file),
                file.name,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                tint = content
            )
        }
        Text(
            file.name,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp).width(56.dp)
        )
    }
}

private fun isImageFile(extension: String?): Boolean =
    extension != null && extension.lowercase() in setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")

@Composable
private fun MessageActionsToggle(expanded: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Icon(Icons.Default.MoreHoriz, if (expanded) "Collapse message actions" else "Show message actions", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
    }
}
