package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItem
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItemType
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEvent
import dev.chungjungsoo.gptmobile.presentation.ui.thinking.ThinkingParser
import kotlinx.coroutines.delay

/** One disclosure controls the entire process; response text remains readable while collapsed. */
@Composable
internal fun AssistantChronologicalContent(
    timeline: List<AssistantTimelineItem>,
    toolEvents: List<ToolEvent>,
    fallbackText: String,
    fallbackThoughts: String,
    contentIdentity: Any,
    isLoading: Boolean,
    debugMode: Boolean,
    showReasoning: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    isError: Boolean = false
) {
    val events = toolEvents.associateBy { it.sequence }
    val legacy = timeline.isEmpty() || timeline.any { it.type == AssistantTimelineItemType.LEGACY_ORDER }
    val items = if (legacy) {
        buildList {
            if (fallbackThoughts.isNotBlank()) add(AssistantTimelineItem(AssistantTimelineItemType.THINKING, fallbackThoughts))
            addAll(toolEvents.sortedBy { it.sequence }.map { AssistantTimelineItem(AssistantTimelineItemType.TOOL, toolSequence = it.sequence) })
            if (fallbackText.isNotBlank()) add(AssistantTimelineItem(AssistantTimelineItemType.TEXT, fallbackText))
            addAll(timeline.filter { it.type == AssistantTimelineItemType.NOTICE })
        }
    } else {
        timeline
    }
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        run {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (isLoading) {
                        GenerationStatusText(toolEvents, items)
                        LinearProgressIndicator(
                            Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        )
                    } else {
                        androidx.compose.foundation.layout.Spacer(Modifier.fillMaxWidth())
                    }
                }
                IconButton(onClick = { onExpandedChange(!expanded) }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "Collapse activity" else "Expand activity", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        items.forEachIndexed { index, item ->
            key(contentIdentity, index, item.type, item.toolSequence) {
                when (item.type) {
                    AssistantTimelineItemType.THINKING -> if (expanded && showReasoning) {
                        Text(item.content, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AssistantTimelineItemType.TEXT -> {
                        val parsed = androidx.compose.runtime.remember(item.content) { ThinkingParser.extractThinking(item.content) }
                        if (expanded && showReasoning && !parsed.thinking.isNullOrBlank()) {
                            Text(parsed.thinking.orEmpty(), Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                        }
                        if (parsed.response.isNotBlank()) {
                            if (isError) {
                                Text(parsed.response, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
                            } else {
                                ChatMarkdown(content = parsed.response, contentIdentity = "$contentIdentity:$index", streaming = isLoading, modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                    AssistantTimelineItemType.TOOL -> if (expanded) {
                        events[item.toolSequence]?.let { event ->
                            InlineExecutionTrace(listOf(event), listOf(item), "$contentIdentity:$index", debugMode, remoteDelegation = items.take(index + 1).lastOrNull { it.delegationInvocationId != null }?.delegationRemote == true || items.drop(index + 1).firstOrNull { it.delegationInvocationId != null }?.delegationRemote == true)
                        }
                    }
                    AssistantTimelineItemType.NOTICE -> if (item.delegationInvocationId != null) {
                        if (debugMode) {
                            Text("${item.delegationProfile.orEmpty()} · Delegation", style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color(0xFF4CAF50))
                            Text(item.content, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color(0xFF4CAF50))
                        }
                    } else if (expanded && !item.statusSummary && !isContextDiagnostic(item.content)) {
                        if (item.recalledFacts.isNotEmpty()) {
                            InlineExecutionTrace(emptyList(), listOf(item), "$contentIdentity:$index", debugMode)
                        } else if (item.content.isNotBlank()) {
                            Text(item.content, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    AssistantTimelineItemType.LEGACY_ORDER -> Unit
                }
            }
        }
    }
}

internal fun isContextDiagnostic(value: String): Boolean = value.startsWith("Context estimate:", true) || value.startsWith("Context:", true) || value.startsWith("[telemetry]", true)

@Composable
private fun GenerationStatusText(toolEvents: List<ToolEvent>, timeline: List<AssistantTimelineItem>) {
    var dots by remember { mutableIntStateOf(1) }
    var phase by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var ticks = 0
        while (true) {
            delay(500)
            dots = dots % 3 + 1
            ticks++
            if (ticks % 10 == 0) phase++
        }
    }

    val runningTool = toolEvents.lastOrNull {
        it.status == dev.chungjungsoo.gptmobile.data.database.entity.ToolEventStatus.RUNNING ||
            it.status == dev.chungjungsoo.gptmobile.data.database.entity.ToolEventStatus.PENDING
    }
    // Refresh from actual activity every five seconds, rather than inventing work phases.
    val activity = timeline.lastOrNull { it.type == AssistantTimelineItemType.TEXT || it.type == AssistantTimelineItemType.THINKING }
    val checkpoint = timeline.lastOrNull { it.statusSummary }?.content
        ?: timeline.lastOrNull { it.progressCheckpoint && it.modelAuthored }?.content
    val base = remember(phase, runningTool?.toolName, activity?.type, checkpoint) {
        runningTool?.let { smartToolVerb(it.toolName) } ?: checkpoint ?: when (activity?.type) {
            AssistantTimelineItemType.TEXT -> "Writing"
            AssistantTimelineItemType.THINKING -> "Thinking"
            else -> checkpoint?.trim()?.takeIf { it.isNotEmpty() }?.split(Regex("\\s+"))?.take(12)?.joinToString(" ") ?: "Preparing response"
        }
    }
    val suffix = ".".repeat(dots)

    val transition = rememberInfiniteTransition(label = "generationStatusGradient")
    val sweep by transition.animateFloat(
        initialValue = -250f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800),
            repeatMode = RepeatMode.Restart
        ),
        label = "generationStatusSweep"
    )
    val primary = MaterialTheme.colorScheme.primary
    val brush = Brush.linearGradient(
        colors = listOf(primary.copy(alpha = 0.35f), primary, primary.copy(alpha = 0.35f)),
        start = Offset(sweep, 0f),
        end = Offset(sweep + 300f, 0f)
    )
    Text(
        text = base.removeSuffix("...").removeSuffix(".") + suffix,
        style = MaterialTheme.typography.labelSmall.copy(brush = brush),
        modifier = Modifier.padding(bottom = 8.dp)
    )
}
