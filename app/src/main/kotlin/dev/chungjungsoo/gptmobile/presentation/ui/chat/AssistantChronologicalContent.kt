package dev.chungjungsoo.gptmobile.presentation.ui.chat

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItem
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItemType
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEvent
import dev.chungjungsoo.gptmobile.presentation.ui.thinking.ThinkingParser

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
    isError: Boolean = false
) {
    var expanded by rememberSaveable(contentIdentity.toString()) { mutableStateOf(false) }
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
    val hasProcess = toolEvents.isNotEmpty() ||
        items.any { it.type != AssistantTimelineItemType.TEXT } ||
        (showReasoning && !ThinkingParser.extractThinking(fallbackText).thinking.isNullOrBlank())
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        if (isLoading || hasProcess) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (isLoading) {
                        Text("Preparing the response and checking the available context.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    } else {
                        Text("Activity · ${toolEvents.size} tools", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "Hide tools and reasoning" else "Show tools and reasoning", tint = MaterialTheme.colorScheme.primary)
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
                                ChatMarkdown(content = parsed.response, contentIdentity = "$contentIdentity:$index", modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                    AssistantTimelineItemType.TOOL -> if (expanded) {
                        events[item.toolSequence]?.let { event ->
                            InlineExecutionTrace(listOf(event), listOf(item), "$contentIdentity:$index", debugMode)
                        }
                    }
                    AssistantTimelineItemType.NOTICE -> if (expanded && (debugMode || !isContextDiagnostic(item.content))) {
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
