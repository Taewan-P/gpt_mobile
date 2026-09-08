package dev.chungjungsoo.gptmobile.data.agent

import dev.chungjungsoo.gptmobile.data.context.CompactionStatus
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRunStatus
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItem
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItemType
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.util.ApiStateFlowOutcome
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
    fun `manual compaction timeout returns a visible failed result`() = runTest {
        val result = collectCompactionResult(10L, "Compaction timed out") {
            awaitCancellation()
        }

        assertEquals(CompactionStatus.FAILED, result.status)
        assertEquals("Compaction timed out", result.message)
    }

    @Test
    fun `persisted terminal snapshot clears even when another terminal transition won`() {
        assertTrue(shouldClearLiveSnapshot(persistError = null, terminalTransitionCommitted = false))
        assertTrue(shouldClearLiveSnapshot(IllegalStateException("disk full"), terminalTransitionCommitted = true))
        assertFalse(shouldClearLiveSnapshot(IllegalStateException("disk full"), terminalTransitionCommitted = false))
    }

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

    @Test
    fun saveFailureDoesNotRewriteSuccessfulGenerationContent() {
        val completed = MessageV2(
            content = "Final answer",
            timeline = listOf(AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "Final answer")),
            platformType = "profile"
        )
        val persistError = IllegalStateException("disk full")

        val terminal = terminalAgentMessage(completed, error = null, completedAt = 42L)
        assertEquals("Final answer", terminal.content)
        assertEquals("disk full", saveFailureNotice(persistError))
        assertFalse(terminal.content.contains("disk full"))
        assertFalse(terminal.timeline.single().content.contains("stopped"))
    }

    @Test
    fun `outer generation timeout preserves partial text as a failed terminal`() = runTest {
        val outcome = collectGenerationOutcome(timeoutMillis = 10L) {
            awaitCancellation()
            ApiStateFlowOutcome.Completed
        }
        val terminal = outcome.toTerminalUpdate()
        val message = terminalAgentMessage(
            MessageV2(content = "Partial", platformType = "profile"),
            terminal.error,
            completedAt = 42L
        )

        assertEquals(AgentRunStatus.FAILED, terminal.status)
        assertEquals("Agent run timed out after 10 ms.", terminal.error)
        assertTrue(message.content.startsWith("Partial"))
        assertTrue(message.content.contains("timed out"))
    }

    @Test
    fun `outer generation bound does not fire when collect completes`() = runTest {
        val outcome = collectGenerationOutcome(timeoutMillis = 10_000L) {
            ApiStateFlowOutcome.Completed
        }
        assertEquals(ApiStateFlowOutcome.Completed, outcome)
        assertEquals(AgentRunStatus.COMPLETED, outcome.toTerminalUpdate().status)
    }

    @Test
    fun `user cancellation is not reported as a generation timeout`() = runTest {
        var outcome: ApiStateFlowOutcome? = null
        val job = launch {
            outcome = collectGenerationOutcome(timeoutMillis = 10_000L) {
                awaitCancellation()
                ApiStateFlowOutcome.Completed
            }
        }
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertEquals(null, outcome)
    }

    @Test
    fun `timeout still uses persistence-failure notice instead of rewriting content`() {
        val timedOut = terminalAgentMessage(
            MessageV2(
                content = "Partial",
                timeline = listOf(AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "Partial")),
                platformType = "profile"
            ),
            error = null,
            completedAt = 42L
        )
        assertEquals("Partial", timedOut.content)
        assertEquals("disk full", saveFailureNotice(IllegalStateException("disk full")))
        assertFalse(timedOut.content.contains("disk full"))
    }
}
