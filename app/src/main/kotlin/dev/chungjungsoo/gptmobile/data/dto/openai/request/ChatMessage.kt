package dev.chungjungsoo.gptmobile.data.dto.openai.request

import dev.chungjungsoo.gptmobile.data.dto.openai.common.MessageContent
import dev.chungjungsoo.gptmobile.data.dto.openai.common.Role
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ChatMessage(
    @SerialName("role")
    val role: Role,

    @SerialName("content")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val content: List<MessageContent>? = null,

    @SerialName("tool_call_id")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val toolCallId: String? = null,

    @SerialName("tool_calls")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val toolCalls: List<OpenAIToolCall>? = null
)
