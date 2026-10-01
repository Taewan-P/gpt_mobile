package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItem
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEvent
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEventStatus
import dev.chungjungsoo.gptmobile.presentation.theme.defaultSpatialSpec
import dev.chungjungsoo.gptmobile.presentation.theme.fastEffectsSpec
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InlineExecutionTrace(events: List<ToolEvent>, timeline: List<AssistantTimelineItem>, contentIdentity: Any, debugMode: Boolean = false, remoteDelegation: Boolean = false) {
    val recalled = timeline.flatMap { it.recalledFacts }.distinctBy { it.id }
    if (events.isEmpty() && recalled.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (recalled.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                recalled.forEach { fact ->
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text("🧠 Recalled: ${fact.label}", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        events.sortedBy { it.sequence }.forEach { event ->
            val toolItem = timeline.firstOrNull { it.toolSequence == event.sequence }
            val metrics = toolItem?.toolMetrics
            val delegatedTool = debugMode && toolItem?.delegatedTool == true
            var expanded by rememberSaveable(contentIdentity.toString(), event.eventId) { mutableStateOf(false) }
            val status = if (event.isError) {
                "Failed"
            } else {
                when (event.status) {
                    ToolEventStatus.RUNNING, ToolEventStatus.PENDING -> "Running"
                    ToolEventStatus.FAILED -> "Failed"
                    ToolEventStatus.CANCELED -> "Canceled"
                    else -> "Completed"
                }
            }
            val running = event.status == ToolEventStatus.RUNNING || event.status == ToolEventStatus.PENDING
            val failed = event.isError || event.status == ToolEventStatus.FAILED
            val isDelegation =
                event.toolName.contains("delegate_to_model", true) ||
                    event.modelToolName.contains("delegate_to_model", true)
            val isRemoteDelegation = isDelegation && (remoteDelegation || event.result.orEmpty().startsWith("<!-- delegation:remote -->"))
            var dots by androidx.compose.runtime.remember(event.eventId) { mutableStateOf(1) }
            LaunchedEffect(running) {
                while (running) {
                    delay(400)
                    dots = dots % 3 + 1
                }
            }
            val summary = if (debugMode) {
                buildString {
                    append("${event.toolName} · $status")
                    metrics?.durationMs?.let { append(" · ${it}ms") }
                    metrics?.resultBytes?.let { append(" · $it bytes") }
                    if (metrics?.shared == true) append(" · Shared")
                }
            } else {
                friendlyToolActivity(event.toolName)
            }
            Surface(
                onClick = { expanded = !expanded },
                shape = RoundedCornerShape(18.dp),
                color = when {
                    delegatedTool -> androidx.compose.ui.graphics.Color(0xFF4CAF50).copy(alpha = 0.18f)
                    failed -> MaterialTheme.colorScheme.errorContainer
                    isDelegation -> androidx.compose.ui.graphics.Color(0xFFFFD54F).copy(alpha = 0.10f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier.fillMaxWidth().animateContentSize(defaultSpatialSpec())
                    .semantics { contentDescription = "$summary. ${if (expanded) "Collapse" else "Expand"} tool details" }
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            if (isRemoteDelegation) Icons.Outlined.Router else toolActivityIcon(event.toolName),
                            null,
                            tint = if (isDelegation) androidx.compose.ui.graphics.Color(0xFFFFC107) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(summary, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (running) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                (1..3).forEach { index -> Text("•", color = MaterialTheme.colorScheme.primary.copy(alpha = if (index <= dots) 1f else 0.15f)) }
                            }
                        } else {
                            Icon(
                                if (failed) {
                                    Icons.Default.Close
                                } else if (event.status == ToolEventStatus.CANCELED) {
                                    Icons.Default.Remove
                                } else {
                                    Icons.Default.Check
                                },
                                contentDescription = status,
                                tint = if (failed) {
                                    MaterialTheme.colorScheme.error
                                } else if (event.status == ToolEventStatus.CANCELED) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Icon(if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                    AnimatedVisibility(
                        visible = expanded,
                        enter = expandVertically(defaultSpatialSpec()) + fadeIn(fastEffectsSpec()),
                        exit = shrinkVertically(defaultSpatialSpec()) + fadeOut(fastEffectsSpec())
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                            Text(event.connectionNameSnapshot ?: "Integrated tool", style = MaterialTheme.typography.labelLarge)
                            if (debugMode) Text("Call ${event.callId} · Run ${event.runId} · #${event.sequence}", style = MaterialTheme.typography.bodySmall)
                            metrics?.let {
                                if (debugMode) Text("Arguments: ${it.argumentsCharacters} characters · ${it.argumentsBytes} UTF-8 bytes", style = MaterialTheme.typography.bodySmall)
                                it.estimatedResultTokens?.let { tokens ->
                                    Text("Result: approximately $tokens tokens (character estimate, not billed usage).", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    when {
                                        it.timingSource == "gateway" -> "Gateway-reported duration. Response payload size was not supplied."
                                        it.shared -> "Reused a result from this conversation turn. Time measures delivery or waiting for the shared result."
                                        else -> "Time measures this tool execution. Payload sizes are measured before output clipping."
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            val mediaLinks = Regex("gptmobile://media/[a-f0-9-]{36}\\.(?:png|jpg|webp|mp3|wav|ogg)").findAll(event.result.orEmpty()).map { it.value }.distinct().take(8).toList()
                            mediaLinks.forEach { link -> ChatMarkdown("[Open media result]($link)") }
                            if (debugMode && isDelegation && !event.result.isNullOrBlank()) {
                                Text(
                                    event.result
                                        .orEmpty()
                                        .removePrefix("<!-- delegation:remote -->")
                                        .removePrefix("<!-- delegation:local -->")
                                        .trimStart(),
                                    color = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            } else {
                                ToolTraceBlock(events = listOf(event))
                            }
                        }
                    }
                }
            }
        }
    }
}
