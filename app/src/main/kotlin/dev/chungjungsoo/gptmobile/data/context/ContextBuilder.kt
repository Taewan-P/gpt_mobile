package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.database.entity.ACTIVE_REVISION_LATEST
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent
import dev.chungjungsoo.gptmobile.util.isAssistantErrorMessage
import dev.chungjungsoo.gptmobile.util.stripAssistantErrorNote
import javax.inject.Inject

class ContextBuilder @Inject constructor() {
    fun build(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): List<ConversationTurn> {
        if (userMessages.isEmpty()) return emptyList()

        val rawTurns = userMessages.mapIndexed { index, userMessage ->
            val assistantCandidates = assistantMessages.getOrNull(index).orEmpty()
                .filter { it.platformType == platform.uid }
            val assistantMessage = assistantCandidates.firstValidAssistantCandidate(platform.uid)

            RawConversationTurn(
                userMessage = userMessage,
                assistantMessage = assistantMessage,
                hasAssistantError = assistantMessage == null &&
                    assistantCandidates.any { candidate ->
                        isAssistantErrorMessage(sanitizeAssistantMessageForContext(candidate).content)
                    },
                isCurrentTurn = index == userMessages.lastIndex
            )
        }

        return rawTurns.mapNotNull { turn ->
            when {
                turn.isCurrentTurn -> turn.toConversationTurn()
                turn.hasAssistantError -> null
                else -> turn.toConversationTurn()
            }
        }
    }

    private fun List<MessageV2>.firstValidAssistantCandidate(platformUid: String): MessageV2? = firstNotNullOfOrNull { message ->
        if (message.platformType != platformUid) return@firstNotNullOfOrNull null

        val sanitizedMessage = sanitizeAssistantMessageForContext(message)
        when {
            sanitizedMessage.effectiveContent().isBlank() && sanitizedMessage.attachments.isEmpty() -> null
            isAssistantErrorMessage(sanitizedMessage.content) -> null
            else -> sanitizedMessage
        }
    }

    private fun sanitizeAssistantMessageForContext(message: MessageV2): MessageV2 {
        val sanitizedContent = stripAssistantErrorNote(message.effectiveContent()).trimEnd()
        return if (sanitizedContent == message.content && message.activeRevisionIndex == ACTIVE_REVISION_LATEST) {
            message
        } else {
            message.copy(
                content = sanitizedContent,
                activeRevisionIndex = ACTIVE_REVISION_LATEST
            )
        }
    }
}

private data class RawConversationTurn(
    val userMessage: MessageV2,
    val assistantMessage: MessageV2?,
    val hasAssistantError: Boolean,
    val isCurrentTurn: Boolean
)

private fun RawConversationTurn.toConversationTurn() = ConversationTurn(
    userMessage = userMessage,
    assistantMessage = assistantMessage,
    isCurrentTurn = isCurrentTurn
)
