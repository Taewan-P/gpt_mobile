package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import java.nio.charset.StandardCharsets

object TokenEstimator {
    fun estimateText(text: String?): Int {
        if (text.isNullOrEmpty()) return 0
        return (text.toByteArray(StandardCharsets.UTF_8).size + 3) / 4
    }

    fun estimateTurn(turn: ConversationTurn): Int {
        val user = estimateText(turn.userMessage.content) +
            turn.userMessage.attachments.sumOf(::estimateAttachment) + 8
        val assistant = turn.assistantMessage?.let { estimateText(it.content) + it.attachments.sumOf(::estimateAttachment) + 8 } ?: 0
        return user + assistant
    }

    private fun estimateAttachment(attachment: ChatAttachment): Int {
        // ponytail: image costs vary by model; use a conservative patch estimate until
        // provider usage calibrates the request. This is never presented as a token count.
        val width = attachment.width?.takeIf { it > 0 } ?: 1024
        val height = attachment.height?.takeIf { it > 0 } ?: 1024
        val patches = ((width.toLong() + 31) / 32) * ((height.toLong() + 31) / 32)
        return (patches * 3 + estimateText(attachment.displayName) + 16).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun estimateTurns(turns: List<ConversationTurn>): Int = turns.sumOf(::estimateTurn)

    fun estimateEvidence(evidence: List<ToolEvidence>): Int = evidence.sumOf {
        estimateText(it.toolName) + estimateText(it.result)
    }

    fun estimateToolDefinition(name: String, description: String, schemaJson: String): Int = estimateText(name) + estimateText(description) + estimateText(schemaJson) + TOOL_DEFINITION_ENVELOPE_TOKENS

    private const val TOOL_DEFINITION_ENVELOPE_TOKENS = 24
}
