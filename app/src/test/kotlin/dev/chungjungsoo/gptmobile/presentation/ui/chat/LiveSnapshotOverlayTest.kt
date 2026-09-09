package dev.chungjungsoo.gptmobile.presentation.ui.chat

import dev.chungjungsoo.gptmobile.data.agent.LiveAgentResponseSnapshot
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantRevision
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItem
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItemType
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSnapshotOverlayTest {
    @Test
    fun overlayKeepsLongerLiveTextWhenRoomLags() {
        val persisted = ChatViewModel.GroupedMessages(
            userMessages = listOf(MessageV2(content = "Q", platformType = null)),
            assistantMessages = listOf(
                listOf(
                    MessageV2(
                        id = 5,
                        content = "Hel",
                        thoughts = "th",
                        currentRunId = "run-1",
                        platformType = "profile",
                        timeline = listOf(
                            AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "Hel")
                        )
                    )
                )
            )
        )
        val live = mapOf(
            "run-1" to LiveAgentResponseSnapshot(
                runId = "run-1",
                messageId = 5,
                content = "Hello world",
                thoughts = "thinking",
                timeline = listOf(
                    AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "Hello world")
                )
            )
        )

        val overlaid = overlayLiveSnapshots(persisted, live).assistantMessages[0][0]
        assertEquals("Hello world", overlaid.content)
        assertEquals("thinking", overlaid.thoughts)
        assertEquals("Hello world", overlaid.timeline.single().content)
    }

    @Test
    fun overlayDoesNotShrinkWhenLiveIsShorterThanRoom() {
        val persisted = ChatViewModel.GroupedMessages(
            assistantMessages = listOf(
                listOf(
                    MessageV2(
                        id = 5,
                        content = "Hello world",
                        currentRunId = "run-1",
                        platformType = "profile"
                    )
                )
            )
        )
        val live = mapOf(
            "run-1" to LiveAgentResponseSnapshot(
                runId = "run-1",
                messageId = 5,
                content = "Hello",
                thoughts = "",
                timeline = emptyList()
            )
        )

        assertEquals(
            "Hello world",
            overlayLiveSnapshots(persisted, live).assistantMessages[0][0].content
        )
    }

    @Test
    fun highWaterKeepsLiveAfterClearUntilRoomCatchesUp() {
        val live = LiveAgentResponseSnapshot(
            runId = "run-1",
            messageId = 5,
            content = "Hello world",
            thoughts = "",
            timeline = emptyList()
        )
        val previous = mapOf("run-1" to live)
        val lagged = ChatViewModel.GroupedMessages(
            assistantMessages = listOf(
                listOf(
                    MessageV2(
                        id = 5,
                        content = "Hello",
                        currentRunId = "run-1",
                        platformType = "profile"
                    )
                )
            )
        )
        val kept = nextLiveHighWater(previous, emptyMap(), lagged)
        assertTrue(kept.containsKey("run-1"))
        assertEquals("Hello world", overlayLiveSnapshots(lagged, kept).assistantMessages[0][0].content)

        val caughtUp = ChatViewModel.GroupedMessages(
            assistantMessages = listOf(
                listOf(
                    MessageV2(
                        id = 5,
                        content = "Hello world",
                        currentRunId = "run-1",
                        platformType = "profile"
                    )
                )
            )
        )
        assertTrue(nextLiveHighWater(kept, emptyMap(), caughtUp).isEmpty())
    }

    @Test
    fun overlaySkipsHistoricalRevisionSelection() {
        val persisted = ChatViewModel.GroupedMessages(
            assistantMessages = listOf(
                listOf(
                    MessageV2(
                        id = 5,
                        content = "Latest",
                        currentRunId = "run-1",
                        platformType = "profile",
                        activeRevisionIndex = 0,
                        revisions = listOf(AssistantRevision(content = "Older", createdAt = 1L))
                    )
                )
            )
        )
        val live = mapOf(
            "run-1" to LiveAgentResponseSnapshot(
                runId = "run-1",
                messageId = 5,
                content = "Streaming",
                thoughts = "",
                timeline = emptyList()
            )
        )

        val overlaid = overlayLiveSnapshots(persisted, live).assistantMessages[0][0]
        assertEquals("Latest", overlaid.content)
        assertEquals(0, overlaid.activeRevisionIndex)
    }
}
