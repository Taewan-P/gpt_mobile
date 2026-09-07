package dev.chungjungsoo.gptmobile.data.agent

import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItem
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItemType
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRunCoordinatorTest {
    @Test
    fun `lazy run cleanup fires when job is canceled before start`() = runTest {
        val events = mutableListOf<String>()
        val job = launch(start = CoroutineStart.LAZY) { events += "execute" }

        job.invokeOnCompletionCleanup { events += "cleanup" }
        job.cancel()

        assertEquals(listOf("cleanup"), events)
    }

    @Test
    fun `cancellation joins the run before applying the terminal fallback`() = runTest {
        val events = mutableListOf<String>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { events += "persist partial message" }
            }
        }

        cancelAndJoinAgentRun(job) { events += "terminal fallback" }

        assertEquals(listOf("persist partial message", "terminal fallback"), events)
    }

    @Test
    fun `terminal message is persisted only after the running transition wins`() = runTest {
        val rejectedEvents = mutableListOf<String>()
        val rejected = commitTerminalAgentRun(
            finishRun = {
                rejectedEvents += "finish"
                false
            },
            persistMessage = { rejectedEvents += "message" }
        )

        val acceptedEvents = mutableListOf<String>()
        val accepted = commitTerminalAgentRun(
            finishRun = {
                acceptedEvents += "finish"
                true
            },
            persistMessage = { acceptedEvents += "message" }
        )

        assertFalse(rejected)
        assertEquals(listOf("finish"), rejectedEvents)
        assertTrue(accepted)
        assertEquals(listOf("finish", "message"), acceptedEvents)
    }

    @Test
    fun `coordinator terminal failure appends error after the authoritative timeline`() {
        val partial = MessageV2(
            content = "Partial",
            timeline = listOf(
                AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "Partial"),
                AssistantTimelineItem(AssistantTimelineItemType.TOOL, toolSequence = 0)
            ),
            platformType = "profile"
        )

        val terminal = terminalAgentMessage(partial, "Provider failed.", completedAt = 42L)

        assertEquals("Partial\n\n[Response stopped: Provider failed.]", terminal.content)
        assertEquals(
            AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "\n\n[Response stopped: Provider failed.]"),
            terminal.timeline.last()
        )
        assertEquals(42L, terminal.createdAt)
    }

    @Test
    fun `coordinator terminal failure merges error into a trailing text item`() {
        val partial = MessageV2(
            content = "Partial",
            timeline = listOf(AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "Partial")),
            platformType = "profile"
        )

        val terminal = terminalAgentMessage(partial, "Provider failed.", completedAt = 42L)

        assertEquals("Partial\n\n[Response stopped: Provider failed.]", terminal.content)
        assertEquals(1, terminal.timeline.size)
        assertEquals(
            AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "Partial\n\n[Response stopped: Provider failed.]"),
            terminal.timeline.single()
        )
        assertEquals(42L, terminal.createdAt)
    }

    @Test
    fun `coordinator terminal failure keeps the whole error text for whitespace only partials`() {
        val whitespace = " ".repeat(64)
        val partial = MessageV2(
            content = whitespace,
            timeline = listOf(AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = whitespace)),
            platformType = "profile"
        )

        val terminal = terminalAgentMessage(partial, "Provider failed.", completedAt = 42L)

        assertEquals("Error: Provider failed.", terminal.content)
        assertTrue(terminal.timeline.single().content.endsWith("Error: Provider failed."))
    }

    @Test
    fun `coordinator failure before streaming creates an authoritative error timeline`() {
        val terminal = terminalAgentMessage(
            MessageV2(content = "", platformType = "profile"),
            "Service start failed.",
            completedAt = 42L
        )

        assertEquals("Error: Service start failed.", terminal.content)
        assertEquals(
            listOf(AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "Error: Service start failed.")),
            terminal.timeline
        )
    }
}
