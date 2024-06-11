package dev.chungjungsoo.gptmobile.data.repository

import com.aallam.openai.api.chat.ChatChunk
import com.google.ai.client.generativeai.type.GenerateContentResponse
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.database.entity.Message
import dev.chungjungsoo.gptmobile.data.network.ApiState
import kotlinx.coroutines.flow.Flow

interface ChatRepository {

    suspend fun completeOpenAIChat(question: Message, history: List<Message>): Flow<ApiState<ChatChunk>>
    suspend fun completeGoogleChat(question: Message, history: List<Message>): Flow<ApiState<GenerateContentResponse>>
    suspend fun fetchChatList(): List<ChatRoom>
    suspend fun fetchMessages(chatId: Int): List<Message>
    suspend fun updateChatTitle(chatRoom: ChatRoom, title: String)
    suspend fun saveChat(chatRoom: ChatRoom, messages: List<Message>): ChatRoom
    suspend fun deleteChat(chatRoom: ChatRoom)
}
