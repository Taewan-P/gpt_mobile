package dev.chungjungsoo.gptmobile.data.localruntime

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.yield

class FakeLocalRuntime : LocalRuntime {
    val loadEngineCalls = mutableListOf<LocalEngineSpec>()
    val createConversationCalls = mutableListOf<LocalConversationConfig>()
    val sendMessageCalls = mutableListOf<String>()
    val sendMessageImages = mutableListOf<List<ByteArray>>()
    var cancelActiveCalls = 0
    var closeConversationCalls = 0
    var unloadEngineCalls = 0

    var scriptedEvents: List<List<LocalRuntimeEvent>> = emptyList()
    var scriptedToolInvocations: List<List<ScriptedToolInvocation>> = emptyList()
    var failLoadEngineIf: (LocalEngineSpec) -> Throwable? = { null }
    var emitDelayMillis: Long = 0L
    var pauseAfterFirst: CompletableDeferred<Unit>? = null
    val generationMutex = Mutex()
    val toolExecutorCalls = mutableListOf<Pair<String, String>>()
    val toolExecutorResults = mutableListOf<String>()

    private var scriptIndex = 0
    private var conversationOpen = false
    private var activeToolExecutor: LocalToolExecutor? = null
    private var loadedSpec: LocalEngineSpec? = null

    @Volatile
    private var generationCancelled = false

    override suspend fun loadEngine(spec: LocalEngineSpec) {
        loadEngineCalls += spec
        failLoadEngineIf(spec)?.let { throw it }
        loadedSpec = spec
    }

    override fun isEngineLoaded(spec: LocalEngineSpec): Boolean = loadedSpec == spec

    override suspend fun createConversation(config: LocalConversationConfig) {
        createConversationCalls += config
        activeToolExecutor = config.toolExecutor
        conversationOpen = true
    }

    override fun sendMessage(text: String, images: List<ByteArray>): Flow<LocalRuntimeEvent> = flow {
        generationCancelled = false
        sendMessageCalls += text
        sendMessageImages += images
        if (emitDelayMillis > 0L) {
            delay(emitDelayMillis)
        }
        val index = scriptIndex++
        val events = scriptedEvents.getOrElse(index) {
            listOf(LocalRuntimeEvent.Done)
        }
        val invocations = scriptedToolInvocations.getOrElse(index) { emptyList() }
        events.forEachIndexed { eventIndex, event ->
            if (generationCancelled) {
                throw CancellationException("Local generation cancelled")
            }
            emit(event)
            yield()
            if (eventIndex == 0) {
                pauseAfterFirst?.await()
            }
            if (generationCancelled) {
                throw CancellationException("Local generation cancelled")
            }
            invocations.filter { it.afterEventIndex == eventIndex }.forEach { invocation ->
                invokeTool(invocation)
            }
        }
    }

    private suspend fun invokeTool(invocation: ScriptedToolInvocation) {
        val executor = activeToolExecutor ?: return
        toolExecutorCalls += invocation.name to invocation.argumentsJson
        toolExecutorResults += executor.execute(invocation.name, invocation.argumentsJson)
    }

    override fun cancelActive() {
        cancelActiveCalls += 1
        generationCancelled = true
        pauseAfterFirst?.complete(Unit)
    }

    override suspend fun closeConversation() {
        closeConversationCalls += 1
        conversationOpen = false
    }

    override suspend fun unloadEngine() {
        unloadEngineCalls += 1
        conversationOpen = false
        loadedSpec = null
    }

    override fun hasOpenConversation(): Boolean = conversationOpen

    override fun <T> runExclusiveFlow(
        onContended: suspend () -> Unit,
        block: suspend LocalRuntime.() -> Flow<T>
    ): Flow<T> = channelFlow {
        var locked = generationMutex.tryLock()
        if (!locked) {
            onContended()
            generationMutex.lock()
            locked = true
        }
        try {
            block(this@FakeLocalRuntime).collect { send(it) }
        } finally {
            if (locked) generationMutex.unlock()
        }
    }
}

data class ScriptedToolInvocation(
    val name: String,
    val argumentsJson: String,
    val afterEventIndex: Int = 0
)
