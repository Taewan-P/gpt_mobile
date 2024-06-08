package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomDao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageDao
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.database.entity.Message
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val chatRoomDao: ChatRoomDao,
    private val messageDao: MessageDao
) : ChatRepository {

    override suspend fun fetchChatList(): List<ChatRoom> = chatRoomDao.getChatRooms()

    override suspend fun fetchMessages(chatId: Int): List<Message> = messageDao.loadMessages(chatId)

    override suspend fun updateChatTitle(chatRoom: ChatRoom, title: String) {
        chatRoomDao.editChatRoom(chatRoom.copy(title = title.take(50)))
    }

    override suspend fun saveChat(chatRoom: ChatRoom, messages: List<Message>) {
        if (chatRoom.id == 0) {
            // New Chat
            val chatId = chatRoomDao.addChatRoom(chatRoom)
            val updatedMessages = messages.map { it.copy(chatId = chatId.toInt()) }
            messageDao.addMessages(*updatedMessages.toTypedArray())
            return
        }

        val savedMessages = fetchMessages(chatRoom.id)

        val shouldBeDeleted = savedMessages.filter { m ->
            messages.firstOrNull { it.id == m.id } == null
        }
        val shouldBeUpdated = messages.filter { m ->
            savedMessages.firstOrNull { it.id == m.id && it != m } != null
        }
        val shouldBeAdded = messages.filter { m ->
            savedMessages.firstOrNull { it.id == m.id } == null
        }

        messageDao.deleteMessages(*shouldBeDeleted.toTypedArray())
        messageDao.editMessages(*shouldBeUpdated.toTypedArray())
        messageDao.addMessages(*shouldBeAdded.toTypedArray())
    }

    override suspend fun deleteChat(chatRoom: ChatRoom) {
        chatRoomDao.deleteChatRooms(chatRoom)
    }
}
