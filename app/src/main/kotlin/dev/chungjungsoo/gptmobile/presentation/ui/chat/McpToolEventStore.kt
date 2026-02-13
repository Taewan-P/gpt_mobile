package dev.chungjungsoo.gptmobile.presentation.ui.chat

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpToolEventStore @Inject constructor() {
    private val eventsByChatId =
        ConcurrentHashMap<Int, Map<String, List<ChatViewModel.McpToolEvent>>>()

    fun get(chatId: Int): Map<String, List<ChatViewModel.McpToolEvent>> =
        eventsByChatId[chatId].orEmpty()

    fun put(chatId: Int, events: Map<String, List<ChatViewModel.McpToolEvent>>) {
        eventsByChatId[chatId] = events
    }
}

