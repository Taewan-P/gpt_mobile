package dev.chungjungsoo.gptmobile.data.repository

import com.aallam.openai.api.chat.ChatCompletionChunk
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.content
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomDao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageDao
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.database.entity.Message
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.MessageRole
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.TextContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.InputMessage
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.MessageRequest
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentDeltaResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ErrorResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.MessageResponseChunk
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPI
import dev.chungjungsoo.gptmobile.presentation.common.ModelConstants
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

class ChatRepositoryImpl @Inject constructor(
    private val chatRoomDao: ChatRoomDao,
    private val messageDao: MessageDao,
    private val settingRepository: SettingRepository,
    private val anthropic: AnthropicAPI
) : ChatRepository {

    private lateinit var openAI: OpenAI
    private lateinit var google: GenerativeModel

    override suspend fun completeOpenAIChat(question: Message, history: List<Message>): Flow<ApiState> {
        val platform = checkNotNull(settingRepository.fetchPlatforms().firstOrNull { it.name == ApiType.OPENAI })
        openAI = OpenAI(platform.token ?: "")

        val generatedMessages = messageToOpenAIMessage(history + listOf(question))
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId(platform.model ?: ""),
            messages = generatedMessages
        )

        return openAI.chatCompletions(chatCompletionRequest)
            .map<ChatCompletionChunk, ApiState> { chunk -> ApiState.Success(chunk.choices[0].delta.content ?: "") }
            .catch { throwable -> emit(ApiState.Error(throwable.message ?: "Unknown error")) }
            .onStart { emit(ApiState.Loading) }
            .onCompletion { emit(ApiState.Done) }
    }

    override suspend fun completeAnthropicChat(question: Message, history: List<Message>): Flow<ApiState> {
        val platform = checkNotNull(settingRepository.fetchPlatforms().firstOrNull { it.name == ApiType.ANTHROPIC })
        anthropic.setToken(platform.token)

        val generatedMessages = messageToAnthropicMessage(history + listOf(question))
        val messageRequest = MessageRequest(
            model = platform.model ?: "",
            messages = generatedMessages,
            maxTokens = ModelConstants.ANTHROPIC_MAXIMUM_TOKEN
        )

        return anthropic.streamChatMessage(messageRequest)
            .map<MessageResponseChunk, ApiState> { chunk ->
                when (chunk) {
                    is ContentDeltaResponseChunk -> ApiState.Success(chunk.delta.text)
                    is ErrorResponseChunk -> throw Error(chunk.error.message)
                    else -> ApiState.Success("")
                }
            }
            .catch { throwable -> emit(ApiState.Error(throwable.message ?: "Unknown error")) }
            .onStart { emit(ApiState.Loading) }
            .onCompletion { emit(ApiState.Done) }
    }

    override suspend fun completeGoogleChat(question: Message, history: List<Message>): Flow<ApiState> {
        val platform = checkNotNull(settingRepository.fetchPlatforms().firstOrNull { it.name == ApiType.GOOGLE })
        google = GenerativeModel(
            modelName = platform.model ?: "",
            apiKey = platform.token ?: "",
            systemInstruction = content { text(ModelConstants.GOOGLE_PROMPT) }
        )

        val inputContent = messageToGoogleMessage(history)
        val chat = google.startChat(history = inputContent)

        return chat.sendMessageStream(question.content)
            .map<GenerateContentResponse, ApiState> { response -> ApiState.Success(response.text ?: "") }
            .catch { throwable -> emit(ApiState.Error(throwable.message ?: "Unknown error")) }
            .onStart { emit(ApiState.Loading) }
            .onCompletion { emit(ApiState.Done) }
    }

    override suspend fun fetchChatList(): List<ChatRoom> = chatRoomDao.getChatRooms()

    override suspend fun fetchMessages(chatId: Int): List<Message> = messageDao.loadMessages(chatId)

    override suspend fun updateChatTitle(chatRoom: ChatRoom, title: String) {
        chatRoomDao.editChatRoom(chatRoom.copy(title = title.take(50)))
    }

    override suspend fun saveChat(chatRoom: ChatRoom, messages: List<Message>): ChatRoom {
        if (chatRoom.id == 0) {
            // New Chat
            val chatId = chatRoomDao.addChatRoom(chatRoom)
            val updatedMessages = messages.map { it.copy(chatId = chatId.toInt()) }
            messageDao.addMessages(*updatedMessages.toTypedArray())

            val savedChatRoom = chatRoom.copy(id = chatId.toInt())
            updateChatTitle(savedChatRoom, updatedMessages[0].content)

            return savedChatRoom.copy(title = updatedMessages[0].content.take(50))
        }

        val savedMessages = fetchMessages(chatRoom.id)
        val updatedMessages = messages.map { it.copy(chatId = chatRoom.id) }

        val shouldBeDeleted = savedMessages.filter { m ->
            updatedMessages.firstOrNull { it.id == m.id } == null
        }
        val shouldBeUpdated = updatedMessages.filter { m ->
            savedMessages.firstOrNull { it.id == m.id && it != m } != null
        }
        val shouldBeAdded = updatedMessages.filter { m ->
            savedMessages.firstOrNull { it.id == m.id } == null
        }

        messageDao.deleteMessages(*shouldBeDeleted.toTypedArray())
        messageDao.editMessages(*shouldBeUpdated.toTypedArray())
        messageDao.addMessages(*shouldBeAdded.toTypedArray())

        return chatRoom
    }

    override suspend fun deleteChat(chatRoom: ChatRoom) {
        chatRoomDao.deleteChatRooms(chatRoom)
    }

    private fun messageToOpenAIMessage(messages: List<Message>): List<ChatMessage> {
        val result = mutableListOf(
            ChatMessage(role = ChatRole.System, content = ModelConstants.OPENAI_PROMPT)
        )

        messages.forEach { message ->
            when (message.platformType) {
                null -> {
                    result.add(
                        ChatMessage(
                            role = ChatRole.User,
                            content = message.content
                        )
                    )
                }

                ApiType.OPENAI -> {
                    result.add(
                        ChatMessage(
                            role = ChatRole.Assistant,
                            content = message.content
                        )
                    )
                }

                else -> {}
            }
        }

        return result
    }

    private fun messageToAnthropicMessage(messages: List<Message>): List<InputMessage> {
        val result = mutableListOf<InputMessage>()

        messages.forEach { message ->
            when (message.platformType) {
                null -> result.add(
                    InputMessage(role = MessageRole.USER, content = listOf(TextContent(text = message.content)))
                )

                ApiType.ANTHROPIC -> result.add(
                    InputMessage(role = MessageRole.ASSISTANT, content = listOf(TextContent(text = message.content)))
                )

                else -> {}
            }
        }

        return result
    }

    private fun messageToGoogleMessage(messages: List<Message>): List<Content> {
        val result = mutableListOf<Content>()

        messages.forEach { message ->
            when (message.platformType) {
                null -> result.add(content(role = "user") { text(message.content) })

                ApiType.GOOGLE -> result.add(content(role = "model") { text(message.content) })

                else -> {}
            }
        }

        return result
    }
}
