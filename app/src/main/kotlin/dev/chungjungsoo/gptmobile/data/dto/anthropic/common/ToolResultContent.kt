package dev.chungjungsoo.gptmobile.data.dto.anthropic.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("tool_result")
data class ToolResultContent(
    @SerialName("tool_use_id")
    val toolUseId: String,

    @SerialName("content")
    val content: String,

    @SerialName("is_error")
    val isError: Boolean = false
) : MessageContent()

