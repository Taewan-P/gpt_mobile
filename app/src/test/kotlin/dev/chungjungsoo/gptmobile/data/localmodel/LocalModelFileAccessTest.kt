package dev.chungjungsoo.gptmobile.data.localmodel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LocalModelFileAccessTest {
    @Test
    fun cancelledWriter_cleanupAndReplacementWaitUntilFileIsReleased() = runTest {
        val events = mutableListOf<String>()
        val release = CompletableDeferred<Unit>()
        val writer = launch {
            LocalModelFileAccess.withLock {
                events += "writing"
                withContext(NonCancellable) { release.await() }
                events += "closed"
            }
        }
        runCurrent()
        LocalModelFileAccess.whenIdle { events += "unsafe cleanup" }
        writer.cancel()
        val cleanup = launch { LocalModelFileAccess.withLock { events += "cleanup" } }
        val replacement = launch { LocalModelFileAccess.withLock { events += "replacement" } }
        runCurrent()
        assertEquals(listOf("writing"), events)

        release.complete(Unit)
        writer.join()
        cleanup.join()
        replacement.join()
        assertEquals(listOf("writing", "closed", "cleanup", "replacement"), events)
    }
}
