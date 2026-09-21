package dev.chungjungsoo.gptmobile.presentation.ui.chat

import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItem
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItemType
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEvent
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEventStatus
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolTraceBlockTest {
    @Test
    fun unresolvedToolReferencesIncludeMissingAndNullSequences() {
        val event = event("one", sequence = 1)

        assertFalse(
            hasUnresolvedToolReferences(
                timeline = listOf(AssistantTimelineItem(AssistantTimelineItemType.TOOL, toolSequence = 1)),
                events = listOf(event)
            )
        )
        assertTrue(
            hasUnresolvedToolReferences(
                timeline = listOf(AssistantTimelineItem(AssistantTimelineItemType.TOOL, toolSequence = 7)),
                events = listOf(event)
            )
        )
        assertTrue(
            hasUnresolvedToolReferences(
                timeline = listOf(AssistantTimelineItem(AssistantTimelineItemType.TOOL)),
                events = listOf(event)
            )
        )
    }

    @Test
    fun toolTextRequiresViewFullForCharacterOrLineTruncation() {
        assertFalse(toolTextRequiresViewFull("short\nvalue"))
        assertTrue(toolTextRequiresViewFull("a".repeat(1025)))
        assertTrue(toolTextRequiresViewFull((1..7).joinToString("\n")))
    }

    @Test
    fun filterToolEvents_ordersBySequenceAndSearchesPersistedDisplayFields() {
        val late = event(
            eventId = "late",
            sequence = 2,
            callId = "call-result",
            connectionNameSnapshot = "Files",
            toolName = "read_file",
            arguments = """{"path":"README.md"}""",
            result = "contract text"
        )
        val early = event(
            eventId = "early",
            sequence = 1,
            callId = "call-args",
            connectionUidSnapshot = "conn-web",
            connectionNameSnapshot = "Web",
            toolName = "search",
            modelToolName = "web__search",
            arguments = """{"query":"weather"}""",
            result = "sunny"
        )
        val error = event(
            eventId = "error",
            sequence = 3,
            callId = "call-error",
            connectionNameSnapshot = "Calendar",
            toolName = "list_events",
            error = "permission denied"
        )
        val running = event(
            eventId = "running",
            sequence = 4,
            status = ToolEventStatus.RUNNING,
            startedAt = 100L
        )

        assertEquals(listOf("early", "late", "error", "running"), filterToolEvents(listOf(late, error, running, early), "").map { it.eventId })
        assertEquals(listOf("early"), filterToolEvents(listOf(late, early, error), "conn-web").map { it.eventId })
        assertEquals(listOf("early"), filterToolEvents(listOf(late, early, error), "web__search").map { it.eventId })
        assertEquals(listOf("early"), filterToolEvents(listOf(late, early, error), "weather").map { it.eventId })
        assertEquals(listOf("late"), filterToolEvents(listOf(late, early, error), "contract").map { it.eventId })
        assertEquals(listOf("error"), filterToolEvents(listOf(late, early, error), "permission").map { it.eventId })
        assertEquals(listOf("running"), filterToolEvents(listOf(running, early), "running").map { it.eventId })
        assertEquals(listOf("running"), filterToolEvents(listOf(running, early), "started at").map { it.eventId })
    }

    @Test
    fun filterToolEvents_matchesAsciiIdentifiersIndependentlyOfDeviceLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))

            assertEquals(listOf("file"), filterToolEvents(listOf(event("file", toolName = "FILE_INDEX")), "file_index").map { it.eventId })
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun toolTraceStatusSummary_prioritizesActiveAndDistinguishesPartialFailure() {
        assertEquals("0 tool calls", toolTraceStatusSummary(emptyList()))
        assertEquals("2 tool calls - running", toolTraceStatusSummary(listOf(event("one", status = ToolEventStatus.COMPLETED), event("two", status = ToolEventStatus.RUNNING))))
        assertEquals("2 tool calls - running", toolTraceStatusSummary(listOf(event("one", status = ToolEventStatus.FAILED), event("two", status = ToolEventStatus.PENDING))))
        assertEquals("2 tool calls - failed", toolTraceStatusSummary(listOf(event("one", status = ToolEventStatus.FAILED), event("two", status = ToolEventStatus.COMPLETED, isError = true))))
        assertEquals("2 tool calls - completed with errors", toolTraceStatusSummary(listOf(event("one", status = ToolEventStatus.COMPLETED), event("two", status = ToolEventStatus.FAILED))))
        assertEquals("2 tool calls - canceled", toolTraceStatusSummary(listOf(event("one", status = ToolEventStatus.COMPLETED), event("two", status = ToolEventStatus.CANCELED))))
        assertEquals("2 tool calls - completed", toolTraceStatusSummary(listOf(event("one", status = ToolEventStatus.COMPLETED), event("two", status = ToolEventStatus.COMPLETED))))
    }

    @Test
    fun formatToolTraceMarkdown_containsBoundedSnapshotsAndOmitsAbsentFields() {
        val markdown = formatToolTraceMarkdown(
            listOf(
                event(
                    eventId = "event-1",
                    sequence = 1,
                    callId = "call-1",
                    connectionUidSnapshot = "conn-1",
                    connectionNameSnapshot = "Docs",
                    toolName = "read",
                    modelToolName = "mcp__docs__read",
                    arguments = "a".repeat(1500),
                    result = "short result",
                    status = ToolEventStatus.COMPLETED,
                    startedAt = 100L,
                    completedAt = 145L
                ),
                event(
                    eventId = "event-2",
                    sequence = 2,
                    callId = "call-2",
                    connectionNameSnapshot = null,
                    toolName = "search",
                    arguments = "{}",
                    result = null,
                    error = null
                )
            )
        )

        assertTrue(markdown.contains("## Tool calls (2)"))
        assertTrue(markdown.contains("Connection: Docs (conn-1)"))
        assertTrue(markdown.contains("Tool: read"))
        assertTrue(markdown.contains("Model tool: mcp__docs__read"))
        assertTrue(markdown.contains("Call ID: call-1"))
        assertTrue(markdown.contains("Timing: 1970-01-01T00:01:40Z - 1970-01-01T00:02:25Z (45 s)"))
        assertTrue(markdown.contains("    ${"a".repeat(1024)}..."))
        assertTrue(markdown.contains("    short result"))
        assertFalse(markdown.contains("Connection: null"))
        assertFalse(markdown.contains("Result: null"))
        assertFalse(markdown.contains("```"))
    }

    @Test
    fun formatToolTraceMarkdown_emptyEventsProduceEmptyText() {
        assertEquals("", formatToolTraceMarkdown(emptyList()))
    }

    @Test
    fun formatToolDuration_requiresStartedAndCompletedTimestamps() {
        assertEquals("45 s", formatToolDuration(event("done", startedAt = 100L, completedAt = 145L)))
        assertEquals(null, formatToolDuration(event("missing-start", startedAt = null, completedAt = 145L)))
        assertEquals(null, formatToolDuration(event("missing-complete", startedAt = 100L, completedAt = null)))
    }

    private fun event(
        eventId: String,
        sequence: Int = 0,
        callId: String = "call-$eventId",
        connectionUidSnapshot: String? = null,
        connectionNameSnapshot: String? = "Web",
        toolName: String = "search",
        modelToolName: String = toolName,
        arguments: String = "{}",
        result: String? = null,
        status: String = ToolEventStatus.COMPLETED,
        startedAt: Long? = null,
        completedAt: Long? = null,
        error: String? = null,
        isError: Boolean = false
    ) = ToolEvent(
        eventId = eventId,
        runId = "run-1",
        sequence = sequence,
        callId = callId,
        connectionUidSnapshot = connectionUidSnapshot,
        connectionNameSnapshot = connectionNameSnapshot,
        toolName = toolName,
        modelToolName = modelToolName,
        arguments = arguments,
        result = result,
        resultType = null,
        status = status,
        isError = isError,
        startedAt = startedAt,
        completedAt = completedAt,
        error = error
    )
}
