package dev.chungjungsoo.gptmobile.data.dto.anthropic.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
@SerialName("tool_use")
data class ToolUseContent(
    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String,

    @SerialName("input")
    val input: JsonObject
) : MessageContent()

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
