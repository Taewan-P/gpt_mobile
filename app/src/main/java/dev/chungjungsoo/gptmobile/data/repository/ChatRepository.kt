package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.database.entity.Message

interface ChatRepository {

    suspend fun fetchChatList(): List<ChatRoom>
    suspend fun fetchMessages(chatId: Int): List<Message>
    suspend fun updateChatTitle(chatRoom: ChatRoom, title: String)
    suspend fun saveChat(chatRoom: ChatRoom, messages: List<Message>)
    suspend fun deleteChat(chatRoom: ChatRoom)
}
