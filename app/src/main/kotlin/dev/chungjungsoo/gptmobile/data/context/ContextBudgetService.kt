package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import kotlinx.serialization.Serializable

@Serializable
data class TokenBudgetSettings(val contextTokens: Int = Int.MAX_VALUE, val outputTokens: Int = 32768, val totalRunTokens: Int = Int.MAX_VALUE, val profileContextCeilings: Map<String, Int> = emptyMap()) {
    fun normalized() = copy(contextTokens = if (contextTokens == 0 || contextTokens == Int.MAX_VALUE) Int.MAX_VALUE else contextTokens.coerceIn(256, 1048576), outputTokens = outputTokens.coerceIn(128, 32768), totalRunTokens = if (totalRunTokens == 0 || totalRunTokens == Int.MAX_VALUE) Int.MAX_VALUE else totalRunTokens.coerceIn(4096, 2097152))
}

data class ContextPlan(val turns: List<ConversationTurn>, val system: String, val tools: List<AgentToolDefinition>, val toolResultBytes: Int, val outputTokens: Int, val notice: String, val promptTokens: Int = 0)

/** Conservative byte-based estimates. Provider-reported counts remain the accounting authority. */
object ContextBudgetService {
    fun estimate(text: String): Int = (text.toByteArray(Charsets.UTF_8).size + 2) / 3
    fun plan(turns: List<ConversationTurn>, system: String, tools: List<AgentToolDefinition>, settings: TokenBudgetSettings): ContextPlan {
        val config = settings.normalized()
        if (config.contextTokens == Int.MAX_VALUE) {
            return ContextPlan(
                turns,
                system,
                tools,
                256 * 1024,
                config.outputTokens,
                "Context: no app-imposed limit. The provider or local model's actual context capacity still applies."
            )
        }
        val output = config.outputTokens.coerceAtMost(config.contextTokens / 4)
        val resultReserve = if (tools.isEmpty()) 0 else (config.contextTokens / 8).coerceAtMost(8192)
        val promptLimit = config.contextTokens - output - resultReserve - minOf(256, config.contextTokens / 16)
        fun cost(turn: ConversationTurn): Int = estimate(turn.userMessage.content) + estimate(turn.assistantMessage?.content.orEmpty()) +
            (turn.userMessage.attachments.size + (turn.assistantMessage?.attachments?.size ?: 0)) * 2048 + 16
        val current = turns.filter { it.isCurrentTurn }
        var used = estimate(system) + current.sumOf(::cost)
        require(used <= promptLimit) { "The current message and system context exceed the estimated context budget. Shorten the message or increase the context limit in Tool connections." }
        val selectedTools = tools.filter { tool ->
            val size = estimate(tool.name + tool.description + tool.inputSchema.toString()) + 16
            (used + size <= promptLimit).also { if (it) used += size }
        }
        val history = mutableListOf<ConversationTurn>()
        for (turn in turns.filterNot { it.isCurrentTurn }.asReversed()) {
            val size = cost(turn)
            if (used + size > promptLimit) break
            used += size
            history += turn
        }
        val omitted = turns.size - current.size - history.size
        return ContextPlan(
            history.asReversed() + current,
            system,
            selectedTools,
            resultReserve * 3,
            output,
            "Context estimate: $used prompt tokens, $output output reserved, $resultReserve tool-result tokens reserved. $omitted earlier turns and ${tools.size - selectedTools.size} tools omitted. Attachment costs are estimates; server limits may differ.",
            promptTokens = used
        )
    }
}
