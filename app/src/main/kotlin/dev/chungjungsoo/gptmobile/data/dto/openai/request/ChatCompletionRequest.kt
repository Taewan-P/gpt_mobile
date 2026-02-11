package dev.chungjungsoo.gptmobile.data.dto.openai.request

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ChatCompletionRequest(
    @SerialName("model")
    val model: String,

    @SerialName("messages")
    val messages: List<ChatMessage>,

    @SerialName("stream")
    val stream: Boolean = true,

    @SerialName("temperature")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val temperature: Float? = null,

    @SerialName("top_p")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val topP: Float? = null,

    @SerialName("max_tokens")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val maxTokens: Int? = null,

    @SerialName("max_completion_tokens")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val maxCompletionTokens: Int? = null,

    @SerialName("reasoning_effort")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val reasoningEffort: String? = null,

    @SerialName("presence_penalty")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val presencePenalty: Float? = null,

    @SerialName("frequency_penalty")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val frequencyPenalty: Float? = null,

    @SerialName("stop")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val stop: List<String>? = null,

    @SerialName("tools")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val tools: List<OpenAITool>? = null,

    @SerialName("tool_choice")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val toolChoice: String? = null
)
