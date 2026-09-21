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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEvent
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEventError
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEventStatus
import dev.chungjungsoo.gptmobile.presentation.theme.defaultSpatialSpec
import dev.chungjungsoo.gptmobile.presentation.theme.fastEffectsSpec
import java.time.Instant
import java.util.Locale

private const val TOOL_TRACE_TEXT_LIMIT = 1024

@Composable
fun ToolTraceBlock(
    events: List<ToolEvent>,
    modifier: Modifier = Modifier,
    contentIdentity: Any = events,
    onViewFull: (String) -> Unit = {}
) {
    if (events.isEmpty()) return

    val labels = toolTraceLabels()
    var isExpanded by rememberSaveable(contentIdentity) { mutableStateOf(false) }
    var query by rememberSaveable(contentIdentity) { mutableStateOf("") }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = fastEffectsSpec(),
        label = "tool trace rotation"
    )
    val summary = toolTraceStatusSummary(events, labels)
    val searchToolTrace = stringResource(R.string.search_tool_trace)
    val noMatchingToolCalls = stringResource(R.string.no_matching_tool_calls)
    val traceBlockDescription = stringResource(R.string.tool_trace_block_content_description, summary)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .semantics { contentDescription = traceBlockDescription }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .semantics {
                    role = Role.Button
                    contentDescription = if (isExpanded) labels.collapseToolTrace else labels.expandToolTrace
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = summary,
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
            key(contentIdentity) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                ) {
                    if (events.size > 1) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text(searchToolTrace) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = searchToolTrace }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    val filteredEvents = filterToolEvents(events, query)
                    if (filteredEvents.isEmpty()) {
                        Text(
                            text = noMatchingToolCalls,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        filteredEvents.forEach { event -> ToolTraceEventCard(event, labels, onViewFull) }
                    }
                }
            }
        }
    }
}

@Composable
private fun toolTraceLabels(): ToolTraceLabels = ToolTraceLabels(
    expandToolTrace = stringResource(R.string.tool_trace_expand_content_description),
    collapseToolTrace = stringResource(R.string.tool_trace_collapse_content_description),
    expand = stringResource(R.string.tool_trace_expand),
    collapse = stringResource(R.string.tool_trace_collapse),
    call = stringResource(R.string.tool_trace_call_singular),
    calls = stringResource(R.string.tool_trace_call_plural),
    running = stringResource(R.string.tool_trace_status_running),
    failed = stringResource(R.string.tool_trace_status_failed),
    completedWithErrors = stringResource(R.string.tool_trace_status_completed_with_errors),
    canceled = stringResource(R.string.tool_trace_status_canceled),
    completed = stringResource(R.string.tool_trace_status_completed),
    status = stringResource(R.string.tool_trace_status),
    callId = stringResource(R.string.tool_trace_call_id),
    connection = stringResource(R.string.tool_trace_connection),
    tool = stringResource(R.string.tool_trace_tool),
    modelTool = stringResource(R.string.tool_trace_model_tool),
    timing = stringResource(R.string.tool_trace_timing),
    error = stringResource(R.string.tool_trace_error),
    arguments = stringResource(R.string.tool_trace_arguments),
    result = stringResource(R.string.tool_trace_result),
    exportHeader = ToolTraceLabels.Default.exportHeader,
    startedAt = stringResource(R.string.tool_trace_timing_started_at)
)

@Composable
private fun ToolTraceEventCard(
    event: ToolEvent,
    labels: ToolTraceLabels,
    onViewFull: (String) -> Unit
) {
    val callDescription = stringResource(
        R.string.tool_trace_call_content_description,
        event.callId,
        event.status.lowercase(Locale.ROOT)
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .semantics { contentDescription = callDescription }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "${event.sequence + 1}. ${event.toolName}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (event.modelToolName != event.toolName) {
                ToolTraceLine(labels.modelTool, event.modelToolName)
            }
            ToolTraceLine(labels.status, event.status)
            ToolTraceLine(labels.callId, event.callId)
            connectionLabel(event)?.let { ToolTraceLine(labels.connection, it) }
            toolTimingLabel(event, labels)?.let { ToolTraceLine(labels.timing, it) }
            event.error?.takeIf { it.isNotBlank() }?.let {
                ToolTraceBlockText(
                    label = labels.error,
                    value = toolEventErrorText(it),
                    onViewFull = onViewFull,
                    fullValue = it
                )
            }
            ToolTraceBlockText(labels.arguments, event.arguments, onViewFull)
            event.result?.takeIf { it.isNotBlank() }?.let {
                ToolTraceBlockText(labels.result, it, onViewFull)
            }
        }
    }
}

@Composable
private fun ToolTraceLine(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ToolTraceBlockText(
    label: String,
    value: String,
    onViewFull: (String) -> Unit,
    fullValue: String = value
) {
    var hasVisualOverflow by remember(value) { mutableStateOf(false) }
    val viewFullDescription = stringResource(R.string.view_full_value, label)
    Text(
        text = "$label:",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
    Text(
        text = boundedText(value),
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 6,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { hasVisualOverflow = it.hasVisualOverflow }
    )
    if (toolTextRequiresViewFull(fullValue) || hasVisualOverflow) {
        TextButton(
            modifier = Modifier.semantics { contentDescription = viewFullDescription },
            onClick = { onViewFull(fullValue) }
        ) {
            Text(stringResource(R.string.view_full))
        }
    }
}

internal fun toolTextRequiresViewFull(value: String): Boolean {
    val normalized = normalizeToolText(value)
    return normalized.length > TOOL_TRACE_TEXT_LIMIT || normalized.count { it == '\n' } >= 6
}

internal fun filterToolEvents(events: List<ToolEvent>, query: String): List<ToolEvent> {
    val ordered = events.sortedBy { it.sequence }
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    if (normalizedQuery.isEmpty()) return ordered

    return ordered.filter { event ->
        listOfNotNull(
            event.connectionUidSnapshot,
            event.connectionNameSnapshot,
            event.toolName,
            event.modelToolName,
            event.status,
            event.callId,
            event.arguments,
            event.result,
            event.error,
            timingLabel(event, ToolTraceLabels.Default)
        ).any { normalizedQuery in it.lowercase(Locale.ROOT) }
    }
}

internal fun toolTraceStatusSummary(events: List<ToolEvent>, labels: ToolTraceLabels = ToolTraceLabels.Default): String {
    val count = events.size
    val noun = if (count == 1) labels.call else labels.calls
    if (events.isEmpty()) return "0 $noun"

    val hasActive = events.any { it.status == ToolEventStatus.RUNNING || it.status == ToolEventStatus.PENDING }
    val failed = events.count { it.status == ToolEventStatus.FAILED || it.isError }
    val completed = events.count { it.status == ToolEventStatus.COMPLETED && !it.isError }
    val status = when {
        hasActive -> labels.running
        failed == events.size -> labels.failed
        failed > 0 && completed > 0 -> labels.completedWithErrors
        failed > 0 -> labels.failed
        events.any { it.status == ToolEventStatus.CANCELED } -> labels.canceled
        else -> labels.completed
    }
    return "$count $noun - $status"
}

internal fun formatToolDuration(event: ToolEvent): String? {
    val seconds = toolDurationSeconds(event) ?: return null
    return "$seconds s"
}

internal fun toolDurationSeconds(event: ToolEvent): Long? {
    val startedAt = event.startedAt ?: return null
    val completedAt = event.completedAt ?: return null
    return (completedAt - startedAt).coerceAtLeast(0)
}

@Composable
private fun toolTimingLabel(event: ToolEvent, labels: ToolTraceLabels): String? {
    val startedAt = event.startedAt
    val completedAt = event.completedAt
    return when {
        startedAt != null && completedAt != null -> {
            val seconds = toolDurationSeconds(event) ?: return null
            "${Instant.ofEpochSecond(startedAt)} - ${Instant.ofEpochSecond(completedAt)} (${pluralStringResource(R.plurals.duration_seconds, seconds.toInt(), seconds)})"
        }

        startedAt != null -> "${labels.startedAt} ${Instant.ofEpochSecond(startedAt)}"

        else -> null
    }
}

internal fun formatToolTraceMarkdown(
    events: List<ToolEvent>,
    labels: ToolTraceLabels = ToolTraceLabels.Default
): String {
    if (events.isEmpty()) return ""

    return buildString {
        appendLine("## ${labels.exportHeader(events.size)}")
        filterToolEvents(events, "").forEach { event ->
            appendLine()
            appendLine("### ${event.sequence + 1}. ${event.toolName}")
            appendLine("- ${labels.status}: ${event.status}")
            appendLine("- ${labels.callId}: ${event.callId}")
            connectionLabel(event)?.let { appendLine("- ${labels.connection}: $it") }
            appendLine("- ${labels.tool}: ${event.toolName}")
            if (event.modelToolName != event.toolName) appendLine("- ${labels.modelTool}: ${event.modelToolName}")
            timingLabel(event, labels)?.let { appendLine("- ${labels.timing}: $it") }
            event.error?.takeIf { it.isNotBlank() }?.let { appendLine("- ${labels.error}: ${boundedText(it)}") }
            appendIndentedBlock(labels.arguments, event.arguments)
            event.result?.takeIf { it.isNotBlank() }?.let { appendIndentedBlock(labels.result, it) }
        }
    }.trimEnd()
}

private fun StringBuilder.appendIndentedBlock(label: String, value: String) {
    appendLine("- $label:")
    boundedText(value).lineSequence().forEach { line ->
        appendLine("    $line")
    }
}

private fun timingLabel(event: ToolEvent, labels: ToolTraceLabels): String? {
    val startedAt = event.startedAt
    val completedAt = event.completedAt
    return when {
        startedAt != null && completedAt != null -> "${Instant.ofEpochSecond(startedAt)} - ${Instant.ofEpochSecond(completedAt)} (${formatToolDuration(event)})"
        startedAt != null -> "${labels.startedAt} ${Instant.ofEpochSecond(startedAt)}"
        else -> null
    }
}

@Composable
private fun toolEventErrorText(error: String): String = when (error) {
    ToolEventError.INTERRUPTED_APP_STOPPED -> stringResource(R.string.tool_event_error_interrupted_app_stopped)
    else -> error
}

private fun connectionLabel(event: ToolEvent): String? {
    val name = event.connectionNameSnapshot?.takeIf { it.isNotBlank() }
    val uid = event.connectionUidSnapshot?.takeIf { it.isNotBlank() }
    return when {
        name != null && uid != null -> "$name ($uid)"
        name != null -> name
        uid != null -> uid
        else -> null
    }
}

private fun boundedText(value: String): String {
    val normalized = normalizeToolText(value)
    if (normalized.length <= TOOL_TRACE_TEXT_LIMIT) return normalized
    return normalized.take(TOOL_TRACE_TEXT_LIMIT) + "..."
}

private fun normalizeToolText(value: String): String = value.replace("\r\n", "\n").replace('\r', '\n')

data class ToolTraceLabels(
    val expandToolTrace: String,
    val collapseToolTrace: String,
    val expand: String,
    val collapse: String,
    val call: String,
    val calls: String,
    val running: String,
    val failed: String,
    val completedWithErrors: String,
    val canceled: String,
    val completed: String,
    val status: String,
    val callId: String,
    val connection: String,
    val tool: String,
    val modelTool: String,
    val timing: String,
    val error: String,
    val arguments: String,
    val result: String,
    val exportHeader: (Int) -> String,
    val startedAt: String
) {
    companion object {
        val Default = ToolTraceLabels(
            expandToolTrace = "Expand tool trace",
            collapseToolTrace = "Collapse tool trace",
            expand = "Expand",
            collapse = "Collapse",
            call = "tool call",
            calls = "tool calls",
            running = "running",
            failed = "failed",
            completedWithErrors = "completed with errors",
            canceled = "canceled",
            completed = "completed",
            status = "Status",
            callId = "Call ID",
            connection = "Connection",
            tool = "Tool",
            modelTool = "Model tool",
            timing = "Timing",
            error = "Error",
            arguments = "Arguments",
            result = "Result",
            exportHeader = { count -> "Tool calls ($count)" },
            startedAt = "started at"
        )
    }
}
