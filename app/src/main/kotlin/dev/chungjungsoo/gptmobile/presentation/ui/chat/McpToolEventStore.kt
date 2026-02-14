package dev.chungjungsoo.gptmobile.presentation.ui.chat

import dev.chungjungsoo.gptmobile.data.database.dao.McpToolEventDao
import dev.chungjungsoo.gptmobile.data.database.entity.McpToolEventEntity
import dev.chungjungsoo.gptmobile.data.database.entity.ToolCallStatus
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpToolEventStore @Inject constructor(
    private val mcpToolEventDao: McpToolEventDao
) {
    private val cache = ConcurrentHashMap<Int, Map<String, List<ChatViewModel.McpToolEvent>>>()

    suspend fun get(chatId: Int): Map<String, List<ChatViewModel.McpToolEvent>> {
        cache[chatId]?.let { return it }
        val entities = mcpToolEventDao.getEventsByChatId(chatId)
        val events = entities.groupBy { "${it.messageIndex}:${it.platformIndex}" }
            .mapValues { (_, list) ->
                list.map { entity ->
                    ChatViewModel.McpToolEvent(
                        callId = entity.callId,
                        toolName = entity.toolName,
                        request = entity.request,
                        output = entity.output,
                        status = entity.status.toViewModelStatus(),
                        isError = entity.isError
                    )
                }
            }
        cache[chatId] = events
        return events
    }

    suspend fun put(chatId: Int, events: Map<String, List<ChatViewModel.McpToolEvent>>) {
        cache[chatId] = events
        // Skip DB persistence for unsaved chats (id = 0 or -1)
        if (chatId <= 0) return
        mcpToolEventDao.deleteAllByChatId(chatId)
        val entities = events.flatMap { (key, eventList) ->
            val (messageIndex, platformIndex) = key.split(":").map { it.toInt() }
            eventList.map { event ->
                McpToolEventEntity(
                    chatId = chatId,
                    messageIndex = messageIndex,
                    platformIndex = platformIndex,
                    callId = event.callId,
                    toolName = event.toolName,
                    request = event.request,
                    output = event.output,
                    status = event.status.toEntityStatus(),
                    isError = event.isError
                )
            }
        }
        if (entities.isNotEmpty()) {
            mcpToolEventDao.insertEvents(entities)
        }
    }

    suspend fun clear(chatId: Int, messageIndex: Int, platformIndex: Int) {
        mcpToolEventDao.deleteEvents(chatId, messageIndex, platformIndex)
        cache[chatId]?.let { current ->
            val key = "$messageIndex:$platformIndex"
            cache[chatId] = current - key
        }
    }

    private fun ToolCallStatus.toViewModelStatus(): ChatViewModel.ToolCallStatus = when (this) {
        ToolCallStatus.REQUESTED -> ChatViewModel.ToolCallStatus.REQUESTED
        ToolCallStatus.EXECUTING -> ChatViewModel.ToolCallStatus.EXECUTING
        ToolCallStatus.COMPLETED -> ChatViewModel.ToolCallStatus.COMPLETED
        ToolCallStatus.FAILED -> ChatViewModel.ToolCallStatus.FAILED
    }

    private fun ChatViewModel.ToolCallStatus.toEntityStatus(): ToolCallStatus = when (this) {
        ChatViewModel.ToolCallStatus.REQUESTED -> ToolCallStatus.REQUESTED
        ChatViewModel.ToolCallStatus.EXECUTING -> ToolCallStatus.EXECUTING
        ChatViewModel.ToolCallStatus.COMPLETED -> ToolCallStatus.COMPLETED
        ChatViewModel.ToolCallStatus.FAILED -> ToolCallStatus.FAILED
    }
}

