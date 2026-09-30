package dev.chungjungsoo.gptmobile.data.localruntime

import android.os.SystemClock
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Thread-safe lifecycle holder for the native LiteRT model instance.
 *
 * Supports warm engine retention across conversational sessions when using the same
 * model specification, preventing reinitialization overhead (2-5s latency savings)
 * especially beneficial for high-RAM flagship devices.
 *
 * Provides idle timeout auto-unload to safely release native model memory after periods
 * of inactivity, protecting background system memory.
 */
class LocalEngineHolder(
    private val delegate: LocalRuntime,
    private val timeProvider: () -> Long = { SystemClock.elapsedRealtime() }
) : LocalRuntime {
    override val handlesEngineFallback get() = delegate.handlesEngineFallback
    override val state get() = delegate.state
    override fun loadedEngineSpec(): LocalEngineSpec? = delegate.loadedEngineSpec()

    private val mutex = Mutex()
    private var loadedSpec: LocalEngineSpec? = null

    @Volatile
    private var lastAccessedElapsedRealtimeMs: Long = 0L

    override val deviceRamGb: Long
        get() = delegate.deviceRamGb

    override fun getHardwareState(): DeviceHardwareState = delegate.getHardwareState()

    override fun getAdaptiveThrottlingPolicy(): AdaptiveThrottlingPolicy = delegate.getAdaptiveThrottlingPolicy()

    /**
     * Timestamp in elapsed realtime milliseconds when the engine was last used or loaded.
     */
    val lastAccessedTimestamp: Long
        get() = lastAccessedElapsedRealtimeMs

    override suspend fun loadEngine(spec: LocalEngineSpec) = withGenerationLock {
        markAccessed()
        if (loadedSpec == spec && delegate.isEngineLoaded(spec)) {
            return@withGenerationLock
        }
        if (loadedSpec != null) {
            delegate.closeConversation()
            delegate.unloadEngine()
            loadedSpec = null
        }
        try {
            delegate.loadEngine(spec)
            loadedSpec = spec
        } catch (error: Throwable) {
            loadedSpec = null
            withContext(NonCancellable) { delegate.unloadEngine() }
            throw error
        }
    }

    override suspend fun createConversation(config: LocalConversationConfig) = withGenerationLock {
        markAccessed()
        delegate.createConversation(config)
    }

    override fun sendMessage(text: String, images: List<ByteArray>): Flow<LocalRuntimeEvent> = channelFlow {
        withGenerationLock {
            markAccessed()
            delegate.sendMessage(text, images).collect {
                markAccessed()
                send(it)
            }
        }
    }

    override fun cancelActive() {
        delegate.cancelActive()
    }

    override suspend fun closeConversation() = withGenerationLock {
        delegate.closeConversation()
    }

    override suspend fun unloadEngine() {
        delegate.cancelActive()
        withGenerationLock {
            delegate.closeConversation()
            delegate.unloadEngine()
            loadedSpec = null
            lastAccessedElapsedRealtimeMs = 0L
        }
    }

    /**
     * Unloads the engine if it has been idle for at least [idleThresholdMs].
     *
     * Returns true if the engine was unloaded due to inactivity, or false if it is still
     * active, already unloaded, or has not exceeded the idle threshold.
     */
    override suspend fun unloadIfIdle(idleThresholdMs: Long): Boolean {
        // Never cancel a running generation to enforce an idle timeout. Check the
        // timestamp and unload under the same lock as generation/model switching.
        if (!mutex.tryLock()) return false
        return try {
            val idleMs = timeProvider() - lastAccessedElapsedRealtimeMs
            if (loadedSpec == null || idleMs < idleThresholdMs) {
                false
            } else {
                delegate.unloadEngine()
                loadedSpec = null
                lastAccessedElapsedRealtimeMs = 0L
                true
            }
        } finally {
            mutex.unlock()
        }
    }

    override suspend fun isEngineLoaded(spec: LocalEngineSpec): Boolean = loadedSpec == spec && delegate.isEngineLoaded(spec)

    override fun hasOpenConversation(): Boolean = delegate.hasOpenConversation()

    override suspend fun <T> tryRunExclusive(block: suspend LocalRuntime.() -> T): T? {
        if (coroutineContext[GenerationLock] != null || !mutex.tryLock()) return null
        return try {
            withContext(GenerationLock()) { block(this@LocalEngineHolder) }
        } finally {
            mutex.unlock()
        }
    }

    override suspend fun <T> runExclusive(block: suspend LocalRuntime.() -> T): T = withGenerationLock {
        markAccessed()
        block(this)
    }

    override fun <T> runExclusiveFlow(
        onContended: suspend () -> Unit,
        block: suspend LocalRuntime.() -> Flow<T>
    ): Flow<T> = channelFlow {
        if (coroutineContext[GenerationLock] != null) {
            markAccessed()
            block(this@LocalEngineHolder).collect { send(it) }
            return@channelFlow
        }
        var locked = mutex.tryLock()
        if (!locked) {
            onContended()
            mutex.lock()
            locked = true
        }
        try {
            withContext(GenerationLock()) {
                markAccessed()
                block(this@LocalEngineHolder).collect { send(it) }
            }
        } finally {
            if (locked) mutex.unlock()
        }
    }

    private fun markAccessed() {
        lastAccessedElapsedRealtimeMs = timeProvider()
    }

    private suspend fun <T> withGenerationLock(block: suspend () -> T): T {
        if (coroutineContext[GenerationLock] != null) {
            return block()
        }
        return mutex.withLock {
            withContext(GenerationLock()) { block() }
        }
    }

    companion object {
        /** Default idle duration (10 minutes) after which an inactive engine is eligible for unloading. */
        const val DEFAULT_IDLE_UNLOAD_TIMEOUT_MS = 10 * 60 * 1000L
    }
}

private class GenerationLock : AbstractCoroutineContextElement(GenerationLock) {
    companion object Key : CoroutineContext.Key<GenerationLock>
}
