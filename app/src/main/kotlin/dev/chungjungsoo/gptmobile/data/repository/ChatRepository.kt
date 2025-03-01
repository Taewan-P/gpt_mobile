package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.Message
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import kotlinx.coroutines.flow.Flow

interface ChatRepository {

    suspend fun completeOpenAIChat(question: Message, history: List<Message>): Flow<ApiState>
    suspend fun completeAnthropicChat(question: Message, history: List<Message>): Flow<ApiState>
    suspend fun completeGroqChat(question: Message, history: List<Message>): Flow<ApiState>
    suspend fun completeOllamaChat(question: Message, history: List<Message>): Flow<ApiState>
    suspend fun fetchChatList(): List<ChatRoom>
    suspend fun fetchChatListV2(): List<ChatRoomV2>
    suspend fun fetchMessages(chatId: Int): List<Message>
    suspend fun fetchMessagesV2(chatId: Int): List<MessageV2>
    suspend fun migrateToChatRoomV2MessageV2()
    fun generateDefaultChatTitle(messages: List<MessageV2>): String?
    suspend fun updateChatTitle(chatRoom: ChatRoomV2, title: String)
    suspend fun saveChat(chatRoom: ChatRoomV2, messages: List<MessageV2>): ChatRoomV2
    suspend fun deleteChats(chatRooms: List<ChatRoom>)
    suspend fun deleteChatsV2(chatRooms: List<ChatRoomV2>)
}
