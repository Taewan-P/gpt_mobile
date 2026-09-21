package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2

fun groupPersistedConversation(messages: List<MessageV2>): Pair<List<MessageV2>, List<List<MessageV2>>> {
    val ordered = messages.sortedBy { it.id }
    val users = ordered.filter { it.platformType == null }
    val userPositions = users.withIndex().filter { it.value.id > 0 }.associate { it.value.id to it.index }
    val assistants = List(users.size) { mutableListOf<MessageV2>() }
    var precedingUser = -1
    ordered.forEach { message ->
        if (message.platformType == null) {
            precedingUser++
        } else {
            // Older rows may lack a usable link; preserve their insertion order.
            val userIndex = userPositions[message.linkedMessageId] ?: precedingUser
            assistants.getOrNull(userIndex)?.add(message)
        }
    }
    return users to assistants
}
