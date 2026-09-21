package dev.chungjungsoo.gptmobile.data.agent

import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveAgentStreamPublisherTest {
    @Test
    fun liveSnapshotIsVisibleBeforeSlowRoomWriteCompletes() = runTest {
        val persistStarted = CompletableDeferred<Unit>()
        val persistGate = CompletableDeferred<Unit>()
        val persisted = mutableListOf<String>()
        val publisher = LiveAgentStreamPublisher(
            persistScope = backgroundScope,
            nanoTimeProvider = { testScheduler.currentTime * 1_000_000L },
            persist = { message ->
                persistStarted.complete(Unit)
                persistGate.await()
                persisted += message.content
            }
        )

        publisher.publish("run-1", assistantMessage("Hello"))

        assertEquals("Hello", publisher.snapshots.value.getValue("run-1").content)
        persistStarted.await()
        assertTrue(persisted.isEmpty())

        persistGate.complete(Unit)
        publisher.flush("run-1")
        advanceUntilIdle()
        assertEquals(listOf("Hello"), persisted)
    }

    @Test
    fun controlledBurstPublishesLiveOftenAndCheckpointsAboutEvery250msThenFlushes() = runTest {
        val persistedAt = mutableListOf<Pair<Long, String>>()
        val liveContents = mutableListOf<String>()
        val publisher = LiveAgentStreamPublisher(
            persistScope = backgroundScope,
            nanoTimeProvider = { testScheduler.currentTime * 1_000_000L },
            persist = { message ->
                persistedAt += testScheduler.currentTime to message.content
            }
        )

        repeat(12) { index ->
            publisher.publish("run-1", assistantMessage("x".repeat(index + 1)))
            liveContents += publisher.snapshots.value.getValue("run-1").content
            advanceTimeBy(20)
        }

        assertEquals(12, liveContents.distinct().size)
        assertEquals("xxxxxxxxxxxx", publisher.snapshots.value.getValue("run-1").content)

        advanceTimeBy(250)
        advanceUntilIdle()
        val checkpointTimes = persistedAt.map { it.first }
        assertTrue(checkpointTimes.size >= 2)
        checkpointTimes.zipWithNext().forEach { (first, second) ->
            assertTrue("checkpoint cadence $first -> $second", second - first >= 250L)
        }
        assertEquals("xxxxxxxxxxxx", persistedAt.last().second)

        publisher.flush("run-1")
        advanceUntilIdle()
        assertEquals("xxxxxxxxxxxx", persistedAt.last().second)
    }

    @Test
    fun persistThrowKeepsLiveSnapshotAndDoesNotFailTheScope() = runTest {
        val publisher = LiveAgentStreamPublisher(
            persistScope = backgroundScope,
            nanoTimeProvider = { testScheduler.currentTime * 1_000_000L },
            persist = { error("disk full") }
        )

        publisher.publish("run-1", assistantMessage("Hello"))
        advanceUntilIdle()

        assertEquals("Hello", publisher.snapshots.value.getValue("run-1").content)

        val flushed = publisher.flush("run-1")
        assertEquals("disk full", flushed?.message)
        assertEquals("Hello", publisher.snapshots.value.getValue("run-1").content)
    }

    @Test
    fun flushThenClearDoesNotReschedulePersistAfterAThrow() = runTest {
        var persistCalls = 0
        val publisher = LiveAgentStreamPublisher(
            persistScope = backgroundScope,
            nanoTimeProvider = { testScheduler.currentTime * 1_000_000L },
            persist = {
                persistCalls += 1
                error("disk full")
            }
        )

        publisher.publish("run-1", assistantMessage("Hello"))
        val flushed = publisher.flush("run-1")
        publisher.clear("run-1")
        advanceUntilIdle()
        advanceTimeBy(250)
        advanceUntilIdle()

        assertEquals("disk full", flushed?.message)
        assertTrue(publisher.snapshots.value.isEmpty())
        assertTrue(persistCalls >= 1)
        val callsAfterClear = persistCalls
        advanceTimeBy(250)
        advanceUntilIdle()
        assertEquals(callsAfterClear, persistCalls)
    }
}

private fun assistantMessage(content: String) = MessageV2(
    id = 7,
    content = content,
    platformType = "profile",
    currentRunId = "run-1"
)
