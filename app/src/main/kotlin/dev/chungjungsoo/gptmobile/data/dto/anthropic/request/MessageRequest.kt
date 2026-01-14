package dev.chungjungsoo.gptmobile.data.dto.anthropic.request

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
When certain value is used in the future, use @EncodeDefault or remove default values
 */

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class MessageRequest(
    @SerialName("model")
    val model: String,

    @SerialName("messages")
    val messages: List<InputMessage>,

    @SerialName("max_tokens")
    val maxTokens: Int,

    @SerialName("metadata")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val metadata: RequestMetadata? = null,

    @SerialName("stop_sequences")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val stopSequences: List<String>? = null,

    @SerialName("stream")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val stream: Boolean = false,

    @SerialName("system")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val systemPrompt: String? = null,

    @SerialName("temperature")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val temperature: Float? = null,

    @SerialName("top_k")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val topK: Int? = null,

    @SerialName("top_p")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val topP: Float? = null,

    @SerialName("thinking")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val thinking: ThinkingConfig? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ThinkingConfig(
    @SerialName("type")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val type: String = "enabled",

    @SerialName("budget_tokens")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val budgetTokens: Int = 10000
)
