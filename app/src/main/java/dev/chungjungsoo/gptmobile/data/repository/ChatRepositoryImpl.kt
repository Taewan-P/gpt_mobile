package dev.chungjungsoo.gptmobile.data.repository

import com.aallam.openai.api.chat.ChatChunk
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomDao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageDao
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.database.entity.Message
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.network.ApiState
import dev.chungjungsoo.gptmobile.presentation.common.ModelConstants
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class ChatRepositoryImpl @Inject constructor(
    private val chatRoomDao: ChatRoomDao,
    private val messageDao: MessageDao,
    private val settingRepository: SettingRepository
) : ChatRepository {

    private lateinit var openAI: OpenAI

    override suspend fun completeOpenAIChat(messages: List<Message>): Flow<ApiState<ChatChunk>> {
        val platform = checkNotNull(settingRepository.fetchPlatforms().firstOrNull { it.name == ApiType.OPENAI })

        if (!::openAI.isInitialized) {
            openAI = OpenAI(platform.token ?: "")
        }

        val generatedMessages = messageToOpenAIMessage(messages)
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId(platform.model ?: ""),
            messages = generatedMessages
        )

        return openAI.chatCompletions(chatCompletionRequest)
            .map { chunk -> ApiState.Success(chunk.choices[0]) as ApiState<ChatChunk> }
            .catch { throwable ->
                emit(ApiState.Error(throwable.message ?: "Unknown error"))
            }
            .onStart { emit(ApiState.Loading) }
    }

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

    private fun messageToOpenAIMessage(messages: List<Message>): List<ChatMessage> {
        val result = mutableListOf(
            ChatMessage(role = ChatRole.System, content = ModelConstants.OPENAI_PROMPT)
        )

        messages.forEach { message ->
            if (message.platformType == null) {
                // User
                result.add(
                    ChatMessage(
                        role = ChatRole.User,
                        content = message.content
                    )
                )
            } else if (message.platformType == ApiType.OPENAI) {
                // Assistant
                result.add(
                    ChatMessage(
                        role = ChatRole.Assistant,
                        content = message.content
                    )
                )
            }
        }

        return result
    }
}
