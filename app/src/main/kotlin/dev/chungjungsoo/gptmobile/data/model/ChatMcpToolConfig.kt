package dev.chungjungsoo.gptmobile.data.model

import kotlinx.serialization.Serializable

/**
 * Model representing a tool available for chat execution (built-in or MCP).
 */
@Serializable
data class AvailableChatTool(
    val id: String,
    val name: String,
    val description: String,
    val source: String = "MCP", // "Built-in" or "MCP"
    val isEnabled: Boolean = true
)

/**
 * Per-chat configuration for enabling or disabling specific tools,
 * and user-overridable execution limits (e.g., max tools exposed and max tool calls).
 */
@Serializable
data class ChatMcpToolConfig(
    val enabledToolIds: Set<String> = emptySet(),
    val disabledToolIds: Set<String> = emptySet(),
    val allowAllByDefault: Boolean = true,
    val allToolsDisabled: Boolean = false,
    val maxTools: Int? = null,
    val maxToolCalls: Int? = null,
    val delegation: ConversationDelegationSettings? = null
) {
    fun effectiveDelegation(defaults: ModelDelegationSettings): ModelDelegationSettings =
        delegation?.applyTo(defaults) ?: defaults.normalized()

    fun isToolEnabled(toolId: String): Boolean {
        if (allToolsDisabled) return false
        return if (allowAllByDefault) {
            !disabledToolIds.contains(toolId)
        } else {
            enabledToolIds.contains(toolId)
        }
    }

    /** A connection, model alias or original tool name may identify the same tool. */
    fun isToolEnabled(candidateIds: Collection<String>): Boolean =
        !allToolsDisabled &&
            candidateIds.none { it in disabledToolIds } &&
            (allowAllByDefault || candidateIds.any { it in enabledToolIds })

    fun withToolDisabled(toolId: String): ChatMcpToolConfig = copy(
        disabledToolIds = disabledToolIds + toolId,
        enabledToolIds = enabledToolIds - toolId
    )

    fun withToolEnabled(toolId: String): ChatMcpToolConfig = copy(
        enabledToolIds = enabledToolIds + toolId,
        disabledToolIds = disabledToolIds - toolId,
        allToolsDisabled = false
    )

    fun toggleTool(toolId: String): ChatMcpToolConfig = if (isToolEnabled(toolId)) {
        withToolDisabled(toolId)
    } else {
        withToolEnabled(toolId)
    }

    fun withMaxTools(limit: Int?): ChatMcpToolConfig = copy(maxTools = limit?.coerceAtLeast(0))

    fun withMaxToolCalls(limit: Int?): ChatMcpToolConfig = copy(maxToolCalls = limit?.coerceAtLeast(0))

    /**
     * Applies the maxTools limit to a candidate list of tools.
     * If maxTools is configured (non-null), the list is truncated to at most maxTools items.
     */
    fun <T> limitTools(tools: List<T>): List<T> {
        val limit = maxTools ?: return tools
        return tools.take(limit)
    }
}
