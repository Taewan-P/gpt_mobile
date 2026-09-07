package dev.chungjungsoo.gptmobile.data.localruntime

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalEngineHolder(
    private val delegate: LocalRuntime
) : LocalRuntime {
    private val mutex = Mutex()
    private var loadedSpec: LocalEngineSpec? = null

    override suspend fun loadEngine(spec: LocalEngineSpec) = withGenerationLock {
        if (loadedSpec == spec) return@withGenerationLock
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
            throw error
        }
    }

    override suspend fun createConversation(config: LocalConversationConfig) = withGenerationLock {
        delegate.createConversation(config)
    }

    override fun sendMessage(text: String, images: List<ByteArray>): Flow<LocalRuntimeEvent> = channelFlow {
        withGenerationLock {
            delegate.sendMessage(text, images).collect { send(it) }
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
        }
    }

    override fun isEngineLoaded(spec: LocalEngineSpec): Boolean = loadedSpec == spec

    override fun hasOpenConversation(): Boolean = delegate.hasOpenConversation()

    override suspend fun <T> runExclusive(block: suspend LocalRuntime.() -> T): T = withGenerationLock {
        block(this)
    }

    override fun <T> runExclusiveFlow(
        onContended: suspend () -> Unit,
        block: suspend LocalRuntime.() -> Flow<T>
    ): Flow<T> = channelFlow {
        if (coroutineContext[GenerationLock] != null) {
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
                block(this@LocalEngineHolder).collect { send(it) }
            }
        } finally {
            if (locked) mutex.unlock()
        }
    }

    private suspend fun <T> withGenerationLock(block: suspend () -> T): T {
        if (coroutineContext[GenerationLock] != null) {
            return block()
        }
        return mutex.withLock {
            withContext(GenerationLock()) { block() }
        }
    }
}

private class GenerationLock : AbstractCoroutineContextElement(GenerationLock) {
    companion object Key : CoroutineContext.Key<GenerationLock>
}
