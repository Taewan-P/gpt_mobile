package dev.chungjungsoo.gptmobile.presentation.ui.chat

import dev.chungjungsoo.gptmobile.data.database.entity.AgentRunStatus
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItem
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItemType
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatRunNoticeTest {
    @Test
    fun `transient notices stay only while the run is active`() {
        val afterLoading = applyChatRunNotice(emptyMap(), "run-1", "Loading local model…", persistent = false)
        val afterIgnored = applyChatRunNotice(afterLoading, "run-1", "The local platform ignored attachments", persistent = true)

        assertEquals(
            listOf("Loading local model…", "The local platform ignored attachments"),
            visibleChatRunNotices(
                stored = afterIgnored.getValue("run-1"),
                timelineNotices = emptyList(),
                isRunActive = true
            )
        )
        assertEquals(
            listOf("The local platform ignored attachments"),
            visibleChatRunNotices(
                stored = afterIgnored.getValue("run-1"),
                timelineNotices = emptyList(),
                isRunActive = false
            )
        )
    }

    @Test
    fun `completed runs drop transient notices and keep timeline informational chips`() {
        val stored = applyChatRunNotice(
            applyChatRunNotice(emptyMap(), "run-1", "Waiting for the local engine", persistent = false),
            "run-1",
            "GPU unavailable on this device — running on CPU",
            persistent = true
        )
        val pruned = pruneTransientChatRunNotices(
            stored,
            runStatuses = mapOf("run-1" to AgentRunStatus.COMPLETED),
            activeRunIds = emptySet()
        )

        assertEquals(
            listOf(
                "The local platform ignored attachments",
                "GPU unavailable on this device — running on CPU"
            ),
            visibleChatRunNotices(
                stored = pruned.getValue("run-1"),
                timelineNotices = listOf("The local platform ignored attachments"),
                isRunActive = false
            )
        )
    }

    @Test
    fun `timeline notice messages are extracted in order`() {
        assertEquals(
            listOf("ignored", "cpu"),
            timelineNoticeMessages(
                listOf(
                    AssistantTimelineItem(AssistantTimelineItemType.NOTICE, content = "ignored"),
                    AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "hello"),
                    AssistantTimelineItem(AssistantTimelineItemType.NOTICE, content = "cpu")
                )
            )
        )
    }
}
