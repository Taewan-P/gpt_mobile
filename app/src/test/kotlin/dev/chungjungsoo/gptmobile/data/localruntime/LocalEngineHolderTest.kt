package dev.chungjungsoo.gptmobile.data.localruntime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalEngineHolderTest {
    @Test
    fun `optional status inference skips a busy engine instead of queuing`() = runTest {
        val holder = LocalEngineHolder(FakeLocalRuntime(), timeProvider = { 1000L })
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val generation = launch {
            holder.runExclusive {
                started.complete(Unit)
                release.await()
            }
        }
        started.await()
        var ran = false
        assertEquals(
            null,
            holder.tryRunExclusive {
                ran = true
                "status"
            }
        )
        assertFalse(ran)
        release.complete(Unit)
        generation.join()
        assertEquals("status", holder.tryRunExclusive { "status" })
    }

    @Test
    fun `reuses engine for the same spec and reloads for a different spec`() = runTest {
        val fake = FakeLocalRuntime()
        val holder = LocalEngineHolder(fake, timeProvider = { 1_000L })
        val first = LocalEngineSpec("/models/a.litertlm", LocalAccelerators.GPU, 1024)
        val second = LocalEngineSpec("/models/b.litertlm", LocalAccelerators.CPU, 2048)

        holder.loadEngine(first)
        holder.loadEngine(first)
        holder.loadEngine(second)

        assertEquals(listOf(first, second), fake.loadEngineCalls)
        assertEquals(1, fake.unloadEngineCalls)
    }

    @Test
    fun `mutex serializes two concurrent sends`() = runTest {
        val fake = FakeLocalRuntime().apply {
            emitDelayMillis = 50L
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("one"), LocalRuntimeEvent.Done),
                listOf(LocalRuntimeEvent.TextDelta("two"), LocalRuntimeEvent.Done)
            )
        }
        val holder = LocalEngineHolder(fake, timeProvider = { 1_000L })
        val order = mutableListOf<String>()

        val first = launch {
            holder.sendMessage("a").collect { event ->
                if (event is LocalRuntimeEvent.TextDelta) order += "a:${event.text}"
                if (event is LocalRuntimeEvent.Done) order += "a:done"
            }
        }
        val second = async {
            holder.sendMessage("b").toList()
        }

        first.join()
        val secondEvents = second.await()
        advanceUntilIdle()

        assertEquals(listOf("a:one", "a:done"), order)
        assertTrue(secondEvents.first() is LocalRuntimeEvent.TextDelta)
        assertEquals("two", (secondEvents.first() as LocalRuntimeEvent.TextDelta).text)
        assertEquals(listOf("a", "b"), fake.sendMessageCalls)
    }

    @Test
    fun `reloads engine when vision flag changes`() = runTest {
        val fake = FakeLocalRuntime()
        val holder = LocalEngineHolder(fake, timeProvider = { 1_000L })
        val textOnly = LocalEngineSpec("/models/a.litertlm", LocalAccelerators.GPU, 1024, isVisionEnabled = false)
        val vision = LocalEngineSpec("/models/a.litertlm", LocalAccelerators.GPU, 1024, isVisionEnabled = true)

        holder.loadEngine(textOnly)
        holder.loadEngine(vision)

        assertEquals(listOf(textOnly, vision), fake.loadEngineCalls)
        assertEquals(1, fake.unloadEngineCalls)
    }

    @Test
    fun `failed load clears the cached spec so a later cpu spec can reload`() = runTest {
        val gpu = LocalEngineSpec("/models/a.litertlm", LocalAccelerators.GPU, 1024)
        val cpu = LocalEngineSpec("/models/a.litertlm", LocalAccelerators.CPU, 1024)
        val fake = FakeLocalRuntime().apply {
            failLoadEngineIf = { spec ->
                if (spec.accelerator == LocalAccelerators.GPU) {
                    IllegalStateException("CreateSharedMemoryManager unimplemented")
                } else {
                    null
                }
            }
        }
        val holder = LocalEngineHolder(fake, timeProvider = { 1_000L })

        holder.loadEngine(cpu)
        runCatching { holder.loadEngine(gpu) }
        holder.loadEngine(cpu)

        assertEquals(listOf(cpu, gpu, cpu), fake.loadEngineCalls)
        assertEquals(2, fake.unloadEngineCalls)
        assertTrue(holder.isEngineLoaded(cpu))
        assertFalse(holder.isEngineLoaded(gpu))
    }

    @Test
    fun `reloads engine when accelerator changes from GPU to NPU`() = runTest {
        val fake = FakeLocalRuntime()
        val holder = LocalEngineHolder(fake, timeProvider = { 1_000L })
        val gpu = LocalEngineSpec("/models/a.litertlm", LocalAccelerators.GPU, 1024)
        val npu = LocalEngineSpec("/models/a.litertlm", LocalAccelerators.NPU, 1024)

        holder.loadEngine(gpu)
        holder.loadEngine(npu)

        assertEquals(listOf(gpu, npu), fake.loadEngineCalls)
        assertEquals(1, fake.unloadEngineCalls)
    }

    @Test
    fun `forwards image payloads to the delegate`() = runTest {
        val fake = FakeLocalRuntime()
        val holder = LocalEngineHolder(fake, timeProvider = { 1_000L })
        val image = byteArrayOf(1, 2, 3)

        holder.sendMessage("look", listOf(image)).toList()

        assertEquals(listOf("look"), fake.sendMessageCalls)
        assertEquals(1, fake.sendMessageImages.single().size)
        assertTrue(fake.sendMessageImages.single().single().contentEquals(image))
    }

    @Test
    fun `trim unload cancels the in-flight generation then unloads`() = runTest {
        val pause = CompletableDeferred<Unit>()
        val fake = FakeLocalRuntime().apply {
            pauseAfterFirst = pause
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("one"), LocalRuntimeEvent.Done)
            )
        }
        val holder = LocalEngineHolder(fake, timeProvider = { 1_000L })
        val spec = LocalEngineSpec("/models/a.litertlm", LocalAccelerators.GPU, 1024)
        holder.loadEngine(spec)

        val events = mutableListOf<LocalRuntimeEvent>()
        val send = launch {
            try {
                holder.sendMessage("a").collect { events += it }
            } catch (_: kotlinx.coroutines.CancellationException) {
            }
        }
        while (events.none { it is LocalRuntimeEvent.TextDelta }) {
            yield()
        }

        holder.unloadEngine()
        send.join()
        advanceUntilIdle()

        assertEquals(1, fake.cancelActiveCalls)
        assertEquals(1, fake.unloadEngineCalls)
        assertFalse(fake.isEngineLoaded(spec))
    }

    @Test
    fun `unloadIfIdle unloads engine when idle duration exceeds threshold`() = runTest {
        var simulatedTime = 1_000L
        val fake = FakeLocalRuntime()
        val holder = LocalEngineHolder(fake, timeProvider = { simulatedTime })
        val spec = LocalEngineSpec("/models/a.litertlm", LocalAccelerators.GPU, 1024)

        holder.loadEngine(spec)
        assertTrue(holder.isEngineLoaded(spec))
        assertEquals(1_000L, holder.lastAccessedTimestamp)

        // Advance time by 5 minutes: below 10-minute threshold
        simulatedTime += 5 * 60 * 1000L
        val unloadedEarly = holder.unloadIfIdle(10 * 60 * 1000L)
        assertFalse(unloadedEarly)
        assertTrue(holder.isEngineLoaded(spec))

        // Advance time past threshold (additional 6 minutes -> 11 minutes total)
        simulatedTime += 6 * 60 * 1000L
        val unloaded = holder.unloadIfIdle(10 * 60 * 1000L)
        assertTrue(unloaded)
        assertFalse(holder.isEngineLoaded(spec))
        assertEquals(1, fake.unloadEngineCalls)

        // Calling again returns false because it is already unloaded
        assertFalse(holder.unloadIfIdle(10 * 60 * 1000L))
    }

    @Test
    fun `sendMessage updates lastAccessedTimestamp and resets idle timer`() = runTest {
        var simulatedTime = 10_000L
        val fake = FakeLocalRuntime().apply {
            scriptedEvents = listOf(
                listOf(LocalRuntimeEvent.TextDelta("res"), LocalRuntimeEvent.Done)
            )
        }
        val holder = LocalEngineHolder(fake, timeProvider = { simulatedTime })
        val spec = LocalEngineSpec("/models/a.litertlm", LocalAccelerators.GPU, 1024)

        holder.loadEngine(spec)
        assertEquals(10_000L, holder.lastAccessedTimestamp)

        // Move clock forward and send message
        simulatedTime = 50_000L
        holder.sendMessage("hello").toList()
        assertEquals(50_000L, holder.lastAccessedTimestamp)

        // Check idle threshold with respect to new timestamp
        simulatedTime = 50_000L + 5 * 60 * 1000L
        assertFalse(holder.unloadIfIdle(10 * 60 * 1000L))
        assertTrue(holder.isEngineLoaded(spec))
    }

    @Test
    fun `idle expiry during a stalled response does not cancel or unload it`() = runTest {
        var clock = 1_000L
        val pause = CompletableDeferred<Unit>()
        val fake = FakeLocalRuntime().apply {
            pauseAfterFirst = pause
            scriptedEvents = listOf(listOf(LocalRuntimeEvent.TextDelta("first"), LocalRuntimeEvent.Done))
        }
        val holder = LocalEngineHolder(fake) { clock }
        holder.loadEngine(LocalEngineSpec("model.litertlm", "cpu", 1024))
        val job = launch { holder.sendMessage("hello").toList() }
        while (fake.sendMessageCalls.isEmpty()) yield()
        clock += 700_000
        assertFalse(holder.unloadIfIdle(600_000))
        assertEquals(0, fake.cancelActiveCalls)
        assertEquals(0, fake.unloadEngineCalls)
        pause.complete(Unit)
        job.join()
        assertFalse(holder.unloadIfIdle(600_000))
        clock += 600_000
        assertTrue(holder.unloadIfIdle(600_000))
    }
}
