package dev.chungjungsoo.gptmobile.data.localruntime

data class ConversationFingerprint(
    val messages: List<LocalHistoryMessage>
) {
    fun isPrefixOf(other: ConversationFingerprint): Boolean {
        if (messages.size > other.messages.size) return false
        return messages.indices.all { index -> messages[index] == other.messages[index] }
    }

    fun extend(additional: List<LocalHistoryMessage>): ConversationFingerprint = ConversationFingerprint(messages + additional)
}

fun conversationFingerprint(messages: List<LocalHistoryMessage>): ConversationFingerprint = ConversationFingerprint(messages)

fun incomingHistoryExtendsConsumed(
    consumed: ConversationFingerprint,
    incomingPrior: ConversationFingerprint
): Boolean = consumed.isPrefixOf(incomingPrior)
