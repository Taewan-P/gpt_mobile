package dev.chungjungsoo.gptmobile.data.database.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantTimelineTest {
    private val timeline = listOf(
        AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "Before"),
        AssistantTimelineItem(AssistantTimelineItemType.TOOL, toolSequence = 0),
        AssistantTimelineItem(AssistantTimelineItemType.THINKING, content = "Checking"),
        AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "After")
    )

    @Test
    fun `timeline converter preserves cross type ordering`() {
        val converter = AssistantTimelineListConverter()

        val restored = converter.fromString(converter.fromList(timeline))

        assertEquals(timeline, restored)
    }

    @Test
    fun `assistant revisions retain the timeline for restoration`() {
        val message = MessageV2(
            content = "BeforeAfter",
            thoughts = "Checking",
            timeline = timeline,
            platformType = "profile",
            currentRunId = "run-current"
        )

        val revision = requireNotNull(message.snapshotLatestAssistantRevision(timestamp = 42L))
        val historical = message.copy(
            timeline = emptyList(),
            revisions = listOf(revision),
            activeRevisionIndex = 0
        )

        assertEquals(timeline, revision.timeline)
        assertEquals(timeline, historical.effectiveTimeline())
    }

    @Test
    fun `revision selection changes content thoughts timeline and run together`() {
        val historicalTimeline = listOf(
            AssistantTimelineItem(AssistantTimelineItemType.THINKING, content = "Old thinking"),
            AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "Old answer")
        )
        val message = MessageV2(
            content = "Latest answer",
            thoughts = "Latest thinking",
            timeline = timeline,
            platformType = "profile",
            currentRunId = "run-latest",
            revisions = listOf(
                AssistantRevision(
                    content = "Old answer",
                    thoughts = "Old thinking",
                    createdAt = 1L,
                    runId = "run-old",
                    timeline = historicalTimeline
                )
            ),
            activeRevisionIndex = 0
        )

        assertEquals("Old answer", message.effectiveContent())
        assertEquals("Old thinking", message.effectiveThoughts())
        assertEquals(historicalTimeline, message.effectiveTimeline())
        assertEquals("run-old", message.effectiveRunId())

        val latest = message.resetActiveRevision()
        assertEquals("Latest answer", latest.effectiveContent())
        assertEquals("Latest thinking", latest.effectiveThoughts())
        assertEquals(timeline, latest.effectiveTimeline())
        assertEquals("run-latest", latest.effectiveRunId())
    }

    @Test
    fun `editing assistant aggregates preserves tool markers and declares unknown chronology`() {
        val edited = rebuildAssistantTimelineForEdit(
            currentTimeline = timeline,
            updatedContent = "Edited answer",
            updatedThoughts = "Edited reasoning"
        )

        assertEquals(AssistantTimelineItemType.LEGACY_ORDER, edited.first().type)
        assertEquals(
            listOf(0),
            edited.filter { it.type == AssistantTimelineItemType.TOOL }.mapNotNull(AssistantTimelineItem::toolSequence)
        )
        assertEquals(edited, AssistantTimelineListConverter().fromString(AssistantTimelineListConverter().fromList(edited)))
    }

    @Test
    fun `editing assistant without tools rebuilds an authoritative timeline`() {
        assertEquals(
            listOf(
                AssistantTimelineItem(AssistantTimelineItemType.THINKING, content = "Edited reasoning"),
                AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "Edited answer")
            ),
            rebuildAssistantTimelineForEdit(
                currentTimeline = listOf(AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "Old answer")),
                updatedContent = "Edited answer",
                updatedThoughts = "Edited reasoning"
            )
        )
    }

    @Test
    fun `legacy render policy detects marker and tool traces without inventing order`() {
        assertTrue(
            hasUnavailableAssistantOrder(
                timeline = listOf(AssistantTimelineItem(AssistantTimelineItemType.LEGACY_ORDER)),
                content = "Answer",
                thoughts = "Reasoning",
                hasToolEvents = true
            )
        )
        assertTrue(
            hasUnavailableAssistantOrder(
                timeline = emptyList(),
                content = "Answer",
                thoughts = "",
                hasToolEvents = true
            )
        )
        assertFalse(
            hasUnavailableAssistantOrder(
                timeline = listOf(AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "Answer")),
                content = "Answer",
                thoughts = "",
                hasToolEvents = false
            )
        )
    }
}
