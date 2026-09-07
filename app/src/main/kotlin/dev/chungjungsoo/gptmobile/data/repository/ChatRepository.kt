package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.database.entity.AgentRun
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.Message
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentRetryRequest
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentRetryResult
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentTurnRequest
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentTurnResult
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEvent
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import kotlinx.coroutines.flow.Flow

interface ChatRepository {

    suspend fun completeChat(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2,
        runId: String
    ): Flow<ApiState>
    fun observeMessagesV2(chatId: Int): Flow<List<MessageV2>>
    fun observeAgentRuns(chatId: Int): Flow<List<AgentRun>>
    fun observeToolEvents(chatId: Int): Flow<List<ToolEvent>>
    suspend fun fetchChatList(): List<ChatRoom>
    suspend fun fetchChatListV2(): List<ChatRoomV2>
    suspend fun searchChatsV2(query: String): List<ChatRoomV2>
    suspend fun fetchMessages(chatId: Int): List<Message>
    suspend fun fetchMessagesV2(chatId: Int): List<MessageV2>
    suspend fun fetchChatPlatformModels(chatId: Int): Map<String, String>
    suspend fun saveChatPlatformModels(chatId: Int, models: Map<String, String>)
    suspend fun persistAgentTurn(request: PersistAgentTurnRequest): PersistAgentTurnResult
    suspend fun persistAgentRetry(request: PersistAgentRetryRequest): PersistAgentRetryResult
    suspend fun markAgentRunRunning(runId: String, startedAt: Long): Boolean
    suspend fun finishAgentRun(runId: String, status: String, completedAt: Long, terminalError: String?): Boolean
    suspend fun finishQueuedAgentRun(runId: String, status: String, completedAt: Long, terminalError: String?): Boolean
    suspend fun finishActiveAgentRun(runId: String, status: String, completedAt: Long, terminalError: String?): Boolean
    suspend fun updateAgentMessage(message: MessageV2)
    suspend fun interruptActiveAgentRuns(completedAt: Long): Int
    suspend fun migrateToChatRoomV2MessageV2()
    fun generateDefaultChatTitle(messages: List<MessageV2>): String?
    suspend fun updateChatTitle(chatRoom: ChatRoomV2, title: String)
    suspend fun saveChat(chatRoom: ChatRoomV2, messages: List<MessageV2>, chatPlatformModels: Map<String, String>): ChatRoomV2
    suspend fun duplicateChatV2(chatRoom: ChatRoomV2): ChatRoomV2
    suspend fun deleteChats(chatRooms: List<ChatRoom>)
    suspend fun deleteChatsV2(chatRooms: List<ChatRoomV2>)
}
