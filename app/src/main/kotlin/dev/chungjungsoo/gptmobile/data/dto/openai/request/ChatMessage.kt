package dev.chungjungsoo.gptmobile.data.dto.openai.request

import dev.chungjungsoo.gptmobile.data.dto.openai.common.MessageContent
import dev.chungjungsoo.gptmobile.data.dto.openai.common.Role
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    @SerialName("role")
    val role: Role,

    @SerialName("content")
    val content: List<MessageContent>? = null,

    @SerialName("tool_calls")
    val toolCalls: List<ChatToolCall>? = null,

    @SerialName("tool_call_id")
    val toolCallId: String? = null
)

@Serializable
data class ChatToolCall(
    @SerialName("id")
    val id: String,

    @SerialName("function")
    val function: ChatFunction,

    @SerialName("type")
    val type: String = "function"
)

@Serializable
data class ChatFunction(
    @SerialName("name")
    val name: String,

    @SerialName("arguments")
    val arguments: String
)
