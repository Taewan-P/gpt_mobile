package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.util.isAssistantErrorMessage
import javax.inject.Inject

class ContextBuilder @Inject constructor() {
    fun build(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2,
        policy: ProviderContextPolicy = ProviderContextPolicy.forClientType(platform.compatibleType)
    ): List<ConversationTurn> {
        if (userMessages.isEmpty()) return emptyList()

        val rawTurns = userMessages.mapIndexed { index, userMessage ->
            val assistantMessage = assistantMessages.getOrNull(index)
                ?.firstOrNull { it.content.isNotBlank() && it.platformType == platform.uid }

            RawConversationTurn(
                userMessage = userMessage,
                assistantMessage = assistantMessage,
                isCurrentTurn = index == userMessages.lastIndex
            )
        }

        val filteredTurns = rawTurns.filter { turn ->
            when {
                turn.isCurrentTurn -> true
                turn.assistantMessage == null -> false
                isAssistantErrorMessage(turn.assistantMessage.content) -> false
                else -> true
            }
        }

        if (filteredTurns.isEmpty()) return emptyList()

        val currentTurn = filteredTurns.lastOrNull { it.isCurrentTurn }
        val historyTurns = filteredTurns
            .filterNot { it.isCurrentTurn }
            .takeLast(policy.recentTurnWindow)

        val selectedTurns = buildList {
            addAll(historyTurns)
            currentTurn?.let { add(it) }
        }

        return applyAttachmentWindow(selectedTurns, policy)
    }

    private fun applyAttachmentWindow(
        turns: List<RawConversationTurn>,
        policy: ProviderContextPolicy
    ): List<ConversationTurn> {
        if (turns.isEmpty()) return emptyList()

        val lastIndex = turns.lastIndex
        return turns.mapIndexed { index, turn ->
            val shouldKeepAttachments = (lastIndex - index) <= policy.historicalImageTurnWindow
            val userMessage = if (shouldKeepAttachments) {
                turn.userMessage
            } else {
                turn.userMessage.copy(attachments = emptyList())
            }

            val assistantMessage = turn.assistantMessage?.let { message ->
                if (shouldKeepAttachments) {
                    message
                } else {
                    message.copy(attachments = emptyList())
                }
            }

            ConversationTurn(
                userMessage = userMessage,
                assistantMessage = assistantMessage,
                isCurrentTurn = turn.isCurrentTurn
            )
        }
    }
}

private data class RawConversationTurn(
    val userMessage: MessageV2,
    val assistantMessage: MessageV2?,
    val isCurrentTurn: Boolean
)
