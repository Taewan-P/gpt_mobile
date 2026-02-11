package dev.chungjungsoo.gptmobile.data.dto.tool

import kotlinx.serialization.Serializable

/**
 * Result of a tool execution to be sent back to the LLM
 */
@Serializable
data class ToolResult(
    val callId: String,
    val name: String,
    val output: String,
    val isError: Boolean = false
)
