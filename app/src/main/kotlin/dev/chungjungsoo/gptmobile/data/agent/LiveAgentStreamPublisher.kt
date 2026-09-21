package dev.chungjungsoo.gptmobile.data.agent

import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItem
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val AGENT_STREAM_CHECKPOINT_INTERVAL_MILLIS = 250L

data class LiveAgentResponseSnapshot(
    val runId: String,
    val messageId: Int,
    val content: String,
    val thoughts: String,
    val timeline: List<AssistantTimelineItem>
)

private data class PersistableStream(
    val content: String,
    val thoughts: String,
    val timeline: List<AssistantTimelineItem>
)

internal class LiveAgentStreamPublisher(
    private val persistIntervalMillis: Long = AGENT_STREAM_CHECKPOINT_INTERVAL_MILLIS,
    private val persistScope: CoroutineScope,
    private val nanoTimeProvider: () -> Long = System::nanoTime,
    private val persist: suspend (MessageV2) -> Unit
) {
    private val _snapshots = MutableStateFlow<Map<String, LiveAgentResponseSnapshot>>(emptyMap())
    val snapshots = _snapshots.asStateFlow()

    private val latest = ConcurrentHashMap<String, MessageV2>()
    private val lastPersistNanos = ConcurrentHashMap<String, Long>()
    private val lastPersisted = ConcurrentHashMap<String, PersistableStream>()
    private val lastPersistError = ConcurrentHashMap<String, Throwable>()
    private val delayedJobs = ConcurrentHashMap<String, Job>()
    private val persistJobs = ConcurrentHashMap<String, Job>()
    private val persistMutexes = ConcurrentHashMap<String, Mutex>()
    private val flushing = ConcurrentHashMap.newKeySet<String>()

    fun publish(runId: String, message: MessageV2) {
        latest[runId] = message
        _snapshots.update { it + (runId to message.toLiveSnapshot(runId)) }
        if (runId !in flushing) {
            schedulePersist(runId)
        }
    }

    suspend fun flush(runId: String): Throwable? {
        flushing += runId
        delayedJobs.remove(runId)?.cancel()
        persistJobs[runId]?.join()
        val message = latest[runId] ?: return lastPersistError[runId]
        return mutex(runId).withLock { persistSafely(runId, message) }
    }

    suspend fun clear(runId: String) {
        flushing += runId
        delayedJobs.remove(runId)?.cancel()
        persistJobs.remove(runId)?.cancelAndJoin()
        latest.remove(runId)
        lastPersistNanos.remove(runId)
        lastPersisted.remove(runId)
        lastPersistError.remove(runId)
        persistMutexes.remove(runId)
        _snapshots.update { it - runId }
        flushing.remove(runId)
    }

    private fun schedulePersist(runId: String) {
        if (runId in flushing) return
        if (persistJobs[runId]?.isActive == true) return
        if (delayedJobs[runId]?.isActive == true) return

        val message = latest[runId] ?: return
        if (lastPersisted[runId] == persistable(message)) return

        val now = nanoTimeProvider()
        val last = lastPersistNanos[runId]
        val waitMillis = if (last == null) {
            0L
        } else {
            persistIntervalMillis - (now - last) / 1_000_000L
        }

        if (waitMillis > 0L) {
            delayedJobs[runId] = persistScope.launch {
                delay(waitMillis)
                delayedJobs.remove(runId)
                if (runId !in flushing) {
                    schedulePersist(runId)
                }
            }
            return
        }

        persistJobs[runId] = persistScope.launch {
            try {
                mutex(runId).withLock {
                    val toPersist = latest[runId] ?: return@withLock
                    persistSafely(runId, toPersist)
                }
            } finally {
                persistJobs.remove(runId)
            }
            if (runId in flushing) return@launch
            val newest = latest[runId] ?: return@launch
            if (lastPersisted[runId] != persistable(newest)) {
                schedulePersist(runId)
            }
        }
    }

    private suspend fun persistSafely(runId: String, message: MessageV2): Throwable? {
        if (lastPersisted[runId] == persistable(message)) return null
        return try {
            persist(message)
            lastPersistNanos[runId] = nanoTimeProvider()
            lastPersisted[runId] = persistable(message)
            lastPersistError.remove(runId)
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            lastPersistNanos[runId] = nanoTimeProvider()
            lastPersistError[runId] = error
            error
        }
    }

    private fun mutex(runId: String): Mutex = persistMutexes.getOrPut(runId) { Mutex() }
}

internal fun MessageV2.toLiveSnapshot(runId: String) = LiveAgentResponseSnapshot(
    runId = runId,
    messageId = id,
    content = content,
    thoughts = thoughts,
    timeline = timeline
)

private fun persistable(message: MessageV2) = PersistableStream(
    content = message.content,
    thoughts = message.thoughts,
    timeline = message.timeline
)
