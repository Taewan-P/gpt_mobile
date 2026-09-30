package dev.chungjungsoo.gptmobile.presentation.ui.chat

import dev.chungjungsoo.gptmobile.data.database.entity.AgentRunStatus
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItem
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
                    AssistantTimelineItem(AssistantTimelineItemType.NOTICE, content = "private helper output", delegationInvocationId = "child"),
                    AssistantTimelineItem(AssistantTimelineItemType.NOTICE, content = "Searching", statusSummary = true),
                    AssistantTimelineItem(AssistantTimelineItemType.NOTICE, content = "Preparing response", progressCheckpoint = true),
                    AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "hello"),
                    AssistantTimelineItem(AssistantTimelineItemType.NOTICE, content = "cpu")
                )
            )
        )
    }

    @Test
    fun `isTelemetryNotice identifies local inference telemetry strings`() {
        assertTrue(isTelemetryNotice("Local: 22.4 tok/s · TTFT 380ms · ~120 tokens"))
        assertTrue(isTelemetryNotice("Local: 15.1 tok/s · TTFT 410ms · ~85 tokens · ⚡ Throttled"))
        assertTrue(isTelemetryNotice("Local: 19.8 tok/s · TTFT 350ms · ~200 tokens · 🌡️ Warm"))
        assertFalse(isTelemetryNotice("Loading local model…"))
        assertFalse(isTelemetryNotice("GPU unavailable on this device — running on CPU"))
        assertFalse(isTelemetryNotice("Local: prompt processed"))
    }

    @Test
    fun `extractTelemetryNotice isolates telemetry badge notice from informational notices`() {
        val notices = listOf(
            "The local platform ignored attachments",
            "Local: 18.5 tok/s · TTFT 320ms · ~150 tokens · ⚡ Throttled",
            "GPU unavailable on this device — running on CPU"
        )
        val (telemetry, remaining) = extractTelemetryNotice(notices)

        assertEquals("Local: 18.5 tok/s · TTFT 320ms · ~150 tokens · ⚡ Throttled", telemetry)
        assertEquals(
            listOf(
                "The local platform ignored attachments",
                "GPU unavailable on this device — running on CPU"
            ),
            remaining
        )
    }

    @Test
    fun `extractTelemetryNotice returns null when no telemetry is present`() {
        val notices = listOf(
            "The local platform ignored attachments",
            "GPU unavailable on this device — running on CPU"
        )
        val (telemetry, remaining) = extractTelemetryNotice(notices)

        assertNull(telemetry)
        assertEquals(notices, remaining)
    }
}
