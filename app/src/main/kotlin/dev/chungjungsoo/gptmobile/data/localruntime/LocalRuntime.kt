package dev.chungjungsoo.gptmobile.data.localruntime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class LocalEngineSpec(
    val modelPath: String,
    val accelerator: String,
    val maxTokens: Int,
    val isVisionEnabled: Boolean = false
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
    val toolExecutor: LocalToolExecutor? = null
)

sealed interface LocalRuntimeEvent {
    data class TextDelta(val text: String) : LocalRuntimeEvent
    data class ThinkingDelta(val text: String) : LocalRuntimeEvent
    data object Done : LocalRuntimeEvent
    data class Error(val message: String, val cause: Throwable? = null) : LocalRuntimeEvent
}

interface LocalRuntime {
    suspend fun loadEngine(spec: LocalEngineSpec)
    suspend fun createConversation(config: LocalConversationConfig)
    fun sendMessage(text: String, images: List<ByteArray> = emptyList()): Flow<LocalRuntimeEvent>
    fun cancelActive()
    suspend fun closeConversation()
    suspend fun unloadEngine()

    fun isEngineLoaded(spec: LocalEngineSpec): Boolean = false

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
