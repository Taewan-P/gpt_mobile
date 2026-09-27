package dev.chungjungsoo.gptmobile.data.localruntime

import dev.chungjungsoo.gptmobile.data.model.LocalRuntimeBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

data class LocalEngineSpec(
    val modelPath: String,
    val accelerator: String,
    val maxTokens: Int,
    val isVisionEnabled: Boolean = false,
    val litertDispatchLibDir: String? = null,
    val visionAccelerator: String = LocalAccelerators.GPU,
    val cpuThreads: Int? = null,
    val cacheEnabled: Boolean = true
)

data class LocalSamplerConfig(
    val topK: Int,
    val topP: Float,
    val temperature: Float
)

enum class LocalHistoryRole {
    USER,
    MODEL
}

data class LocalHistoryMessage(
    val role: LocalHistoryRole,
    val text: String,
    val imageIds: List<String> = emptyList(),
    val images: List<ByteArray> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LocalHistoryMessage) return false
        return role == other.role && text == other.text && imageIds == other.imageIds
    }

    override fun hashCode(): Int {
        var result = role.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + imageIds.hashCode()
        return result
    }
}

data class LocalToolDescriptor(
    val name: String,
    val description: String,
    val inputSchemaJson: String
)

fun interface LocalToolExecutor {
    suspend fun execute(toolName: String, argumentsJson: String): String
}

data class LocalConversationConfig(
    val sampler: LocalSamplerConfig,
    val systemPrompt: String?,
    val initialMessages: List<LocalHistoryMessage>,
    val tools: List<LocalToolDescriptor> = emptyList(),
    val isConstrainedDecodingEnabled: Boolean = false,
    val toolExecutor: LocalToolExecutor? = null,
    val maxOutputTokens: Int? = null
)

/** Execution phase of local on-device inference. */
enum class LocalInferencePhase {
    PREFILL,
    GENERATING
}

/** Performance and generation telemetry emitted during local model execution. */
data class LocalInferenceMetrics(
    val timeToFirstTokenMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val totalChunks: Int = 0,
    val totalCharacters: Int = 0,
    val estimatedTokens: Int = 0,
    val tokensPerSecond: Double = 0.0
)

sealed interface LocalRuntimeEvent {
    data class PhaseChanged(val phase: LocalInferencePhase) : LocalRuntimeEvent
    data class TextDelta(val text: String) : LocalRuntimeEvent
    data class ThinkingDelta(val text: String) : LocalRuntimeEvent
    data class Metrics(val metrics: LocalInferenceMetrics) : LocalRuntimeEvent
    data object Done : LocalRuntimeEvent
    data class Error(val message: String, val cause: Throwable? = null) : LocalRuntimeEvent
}

/** The engine that actually initialized, independent of the saved preference. */
data class LocalRuntimeState(
    val backend: LocalRuntimeBackend? = null,
    val engineSpec: LocalEngineSpec? = null,
    val fallbackReason: String? = null
)

private val EMPTY_RUNTIME_STATE: StateFlow<LocalRuntimeState> = MutableStateFlow(LocalRuntimeState())

class LocalRuntimeFallbackDisabledException(cause: Throwable) : IllegalStateException("QNN could not start and automatic fallback is disabled. Select LiteRT-LM or enable fallback in Local models → Performance tuning.", cause)

class LocalNpuPackageException(cause: Throwable) : IllegalStateException(
    "This NPU package could not start. Its compiled model may exceed the NPU memory available on this phone. " +
        "Download the GPU / CPU edition from the model marketplace, or choose another NPU model.",
    cause
)

interface LocalRuntime {
    val handlesEngineFallback: Boolean get() = false
    val state: StateFlow<LocalRuntimeState> get() = EMPTY_RUNTIME_STATE

    fun loadedEngineSpec(): LocalEngineSpec? = null

    val deviceRamGb: Long get() = 8L

    fun getHardwareState(): DeviceHardwareState = DeviceHardwareState()

    fun getAdaptiveThrottlingPolicy(): AdaptiveThrottlingPolicy =
        DeviceHardwareGovernor.computeThrottlingPolicy(getHardwareState(), deviceRamGb >= 10L)

    suspend fun loadEngine(spec: LocalEngineSpec)
    suspend fun createConversation(config: LocalConversationConfig)
    fun sendMessage(text: String, images: List<ByteArray> = emptyList()): Flow<LocalRuntimeEvent>
    fun cancelActive()
    suspend fun closeConversation()
    suspend fun unloadEngine()

    /**
     * Unloads the engine if it has been idle without active requests for at least [idleThresholdMs].
     * Returns true if unloaded, false otherwise.
     */
    suspend fun unloadIfIdle(idleThresholdMs: Long): Boolean = false

    suspend fun isEngineLoaded(spec: LocalEngineSpec): Boolean = false

    fun hasOpenConversation(): Boolean = false

    suspend fun <T> runExclusive(block: suspend LocalRuntime.() -> T): T = block(this)

    fun <T> runExclusiveFlow(block: suspend LocalRuntime.() -> Flow<T>): Flow<T> = runExclusiveFlow(onContended = {}, block = block)

    fun <T> runExclusiveFlow(
        onContended: suspend () -> Unit,
        block: suspend LocalRuntime.() -> Flow<T>
    ): Flow<T> = flow {
        block(this@LocalRuntime).collect { emit(it) }
    }
}
