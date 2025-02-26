package dev.chungjungsoo.gptmobile.data.repository

import com.aallam.openai.api.chat.ChatCompletionChunk
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIHost
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomDao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageDao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageV2Dao
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.Message
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
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
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

class ChatRepositoryImpl @Inject constructor(
    private val chatRoomDao: ChatRoomDao,
    private val messageDao: MessageDao,
    private val chatRoomV2Dao: ChatRoomV2Dao,
    private val messageV2Dao: MessageV2Dao,
    private val settingRepository: SettingRepository,
    private val anthropic: AnthropicAPI
) : ChatRepository {

    private lateinit var openAI: OpenAI
    private lateinit var google: GenerativeModel
    private lateinit var ollama: OpenAI
    private lateinit var groq: OpenAI

    override suspend fun completeOpenAIChat(question: Message, history: List<Message>): Flow<ApiState> {
        val platform = checkNotNull(settingRepository.fetchPlatforms().firstOrNull { it.name == ApiType.OPENAI })
        openAI = OpenAI(platform.token ?: "", host = OpenAIHost(baseUrl = platform.apiUrl))

        val generatedMessages = messageToOpenAICompatibleMessage(ApiType.OPENAI, history + listOf(question))
        val prompt = platform.systemPrompt ?: ModelConstants.OPENAI_PROMPT
        val generatedMessageWithPrompt = listOf(
            ChatMessage(role = ChatRole.System, content = prompt)
        ) + generatedMessages
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId(platform.model ?: ""),
            messages = if (prompt.isEmpty()) generatedMessages else generatedMessageWithPrompt, // disable system prompt only if user set it to empty explicitly
            temperature = platform.temperature?.toDouble(),
            topP = platform.topP?.toDouble()
        )

        return openAI.chatCompletions(chatCompletionRequest)
            .map<ChatCompletionChunk, ApiState> { chunk -> ApiState.Success(chunk.choices.getOrNull(0)?.delta?.content ?: "") }
            .catch { throwable -> emit(ApiState.Error(throwable.message ?: "Unknown error")) }
            .onStart { emit(ApiState.Loading) }
            .onCompletion { emit(ApiState.Done) }
    }

    override suspend fun completeAnthropicChat(question: Message, history: List<Message>): Flow<ApiState> {
        val platform = checkNotNull(settingRepository.fetchPlatforms().firstOrNull { it.name == ApiType.ANTHROPIC })
        anthropic.setToken(platform.token)
        anthropic.setAPIUrl(platform.apiUrl)

        val generatedMessages = messageToAnthropicMessage(history + listOf(question))
        val prompt = platform.systemPrompt ?: ModelConstants.DEFAULT_PROMPT
        val messageRequest = MessageRequest(
            model = platform.model ?: "",
            messages = generatedMessages,
            maxTokens = ModelConstants.ANTHROPIC_MAXIMUM_TOKEN,
            systemPrompt = if (prompt.isEmpty()) null else prompt,
            stream = true,
            temperature = platform.temperature,
            topP = platform.topP
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
        val config = generationConfig {
            temperature = platform.temperature
            topP = platform.topP
        }
        val prompt = platform.systemPrompt ?: ModelConstants.DEFAULT_PROMPT
        google = GenerativeModel(
            modelName = platform.model ?: "",
            apiKey = platform.token ?: "",
            systemInstruction = if (prompt.isEmpty()) null else content { text(prompt) },
            generationConfig = config,
            safetySettings = listOf(
                SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.ONLY_HIGH),
                SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE)
            )
        )

        val inputContent = messageToGoogleMessage(history)
        val chat = google.startChat(history = inputContent)

        return chat.sendMessageStream(question.content)
            .map<GenerateContentResponse, ApiState> { response -> ApiState.Success(response.text ?: "") }
            .catch { throwable -> emit(ApiState.Error(throwable.message ?: "Unknown error")) }
            .onStart { emit(ApiState.Loading) }
            .onCompletion { emit(ApiState.Done) }
    }

    override suspend fun completeGroqChat(question: Message, history: List<Message>): Flow<ApiState> {
        val platform = checkNotNull(settingRepository.fetchPlatforms().firstOrNull { it.name == ApiType.GROQ })
        groq = OpenAI(platform.token ?: "", host = OpenAIHost(baseUrl = platform.apiUrl))

        val generatedMessages = messageToOpenAICompatibleMessage(ApiType.GROQ, history + listOf(question))
        val prompt = platform.systemPrompt ?: ModelConstants.DEFAULT_PROMPT
        val generatedMessageWithPrompt = listOf(
            ChatMessage(role = ChatRole.System, content = prompt)
        ) + generatedMessages
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId(platform.model ?: ""),
            messages = if (prompt.isEmpty()) generatedMessages else generatedMessageWithPrompt,
            temperature = platform.temperature?.toDouble(),
            topP = platform.topP?.toDouble()
        )

        return groq.chatCompletions(chatCompletionRequest)
            .map<ChatCompletionChunk, ApiState> { chunk -> ApiState.Success(chunk.choices.getOrNull(0)?.delta?.content ?: "") }
            .catch { throwable -> emit(ApiState.Error(throwable.message ?: "Unknown error")) }
            .onStart { emit(ApiState.Loading) }
            .onCompletion { emit(ApiState.Done) }
    }

    override suspend fun completeOllamaChat(question: Message, history: List<Message>): Flow<ApiState> {
        val platform = checkNotNull(settingRepository.fetchPlatforms().firstOrNull { it.name == ApiType.OLLAMA })
        ollama = OpenAI(platform.token ?: "", host = OpenAIHost(baseUrl = "${platform.apiUrl}v1/"))

        val generatedMessages = messageToOpenAICompatibleMessage(ApiType.OLLAMA, history + listOf(question))
        val prompt = platform.systemPrompt ?: ModelConstants.DEFAULT_PROMPT
        val generatedMessageWithPrompt = listOf(
            ChatMessage(role = ChatRole.System, content = prompt)
        ) + generatedMessages
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId(platform.model ?: ""),
            messages = if (prompt.isEmpty()) generatedMessages else generatedMessageWithPrompt,
            temperature = platform.temperature?.toDouble(),
            topP = platform.topP?.toDouble()
        )

        return ollama.chatCompletions(chatCompletionRequest)
            .map<ChatCompletionChunk, ApiState> { chunk -> ApiState.Success(chunk.choices.getOrNull(0)?.delta?.content ?: "") }
            .catch { throwable -> emit(ApiState.Error(throwable.message ?: "Unknown error")) }
            .onStart { emit(ApiState.Loading) }
            .onCompletion { emit(ApiState.Done) }
    }

    override suspend fun fetchChatList(): List<ChatRoom> = chatRoomDao.getChatRooms()

    override suspend fun fetchChatListV2(): List<ChatRoomV2> = chatRoomV2Dao.getChatRooms()

    override suspend fun fetchMessages(chatId: Int): List<Message> = messageDao.loadMessages(chatId)

    override suspend fun fetchMessagesV2(chatId: Int): List<MessageV2> = messageV2Dao.loadMessages(chatId)

    override suspend fun migrateToChatRoomV2MessageV2() {
        val leftOverChatRoomV2s = chatRoomV2Dao.getChatRooms()
        chatRoomV2Dao.deleteChatRooms(*leftOverChatRoomV2s.toTypedArray())

        val chatList = fetchChatList()
        val platforms = settingRepository.fetchPlatformV2s()
        val apiTypeMap = mutableMapOf<ApiType, String>()

        platforms.forEach { platform ->
            when (platform.name) {
                "OpenAI" -> apiTypeMap[ApiType.OPENAI] = platform.uid
                "Anthropic" -> apiTypeMap[ApiType.ANTHROPIC] = platform.uid
                "Google" -> apiTypeMap[ApiType.GOOGLE] = platform.uid
                "Groq" -> apiTypeMap[ApiType.GROQ] = platform.uid
                "Ollama" -> apiTypeMap[ApiType.OLLAMA] = platform.uid
            }
        }

        chatList.forEach { chatRoom ->
            val messages = messageDao.loadMessages(chatRoom.id).map { m ->
                MessageV2(
                    id = m.id,
                    chatId = m.chatId,
                    content = m.content,
                    files = listOf(),
                    linkedMessageId = m.linkedMessageId,
                    platformType = m.platformType?.let { apiTypeMap[it] },
                    createdAt = m.createdAt
                )
            }

            chatRoomV2Dao.addChatRoom(
                ChatRoomV2(
                    id = chatRoom.id,
                    title = chatRoom.title,
                    enabledPlatform = chatRoom.enabledPlatform.map { apiTypeMap[it] ?: "" },
                    createdAt = chatRoom.createdAt,
                    updatedAt = chatRoom.createdAt
                )
            )

            messageV2Dao.addMessages(*messages.toTypedArray())
        }
    }

    override fun generateDefaultChatTitle(messages: List<Message>): String? = messages.sortedBy { it.createdAt }.firstOrNull { it.platformType == null }?.content?.replace('\n', ' ')?.take(50)

    override suspend fun updateChatTitle(chatRoom: ChatRoom, title: String) {
        chatRoomDao.editChatRoom(chatRoom.copy(title = title.replace('\n', ' ').take(50)))
    }

    override suspend fun updateChatTitleV2(chatRoom: ChatRoomV2, title: String) {
        chatRoomV2Dao.editChatRoom(chatRoom.copy(title = title.replace('\n', ' ').take(50)))
    }

    override suspend fun saveChat(chatRoom: ChatRoom, messages: List<Message>): ChatRoom {
        if (chatRoom.id == 0) {
            // New Chat
            val chatId = chatRoomDao.addChatRoom(chatRoom)
            val updatedMessages = messages.map { it.copy(chatId = chatId.toInt()) }
            messageDao.addMessages(*updatedMessages.toTypedArray())

            val savedChatRoom = chatRoom.copy(id = chatId.toInt())
            updateChatTitle(savedChatRoom, updatedMessages[0].content)

            return savedChatRoom.copy(title = updatedMessages[0].content.replace('\n', ' ').take(50))
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

        chatRoomDao.editChatRoom(chatRoom)
        messageDao.deleteMessages(*shouldBeDeleted.toTypedArray())
        messageDao.editMessages(*shouldBeUpdated.toTypedArray())
        messageDao.addMessages(*shouldBeAdded.toTypedArray())

        return chatRoom
    }

    override suspend fun saveChatV2(chatRoom: ChatRoomV2, messages: List<MessageV2>): ChatRoomV2 {
        if (chatRoom.id == 0) {
            // New Chat
            val chatId = chatRoomV2Dao.addChatRoom(chatRoom)
            val updatedMessages = messages.map { it.copy(chatId = chatId.toInt()) }
            messageV2Dao.addMessages(*updatedMessages.toTypedArray())

            val savedChatRoom = chatRoom.copy(id = chatId.toInt())
            updateChatTitleV2(savedChatRoom, updatedMessages[0].content)

            return savedChatRoom.copy(title = updatedMessages[0].content.replace('\n', ' ').take(50))
        }

        val savedMessages = fetchMessagesV2(chatRoom.id)
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

        chatRoomV2Dao.editChatRoom(chatRoom)
        messageV2Dao.deleteMessages(*shouldBeDeleted.toTypedArray())
        messageV2Dao.editMessages(*shouldBeUpdated.toTypedArray())
        messageV2Dao.addMessages(*shouldBeAdded.toTypedArray())

        return chatRoom
    }

    override suspend fun deleteChats(chatRooms: List<ChatRoom>) {
        chatRoomDao.deleteChatRooms(*chatRooms.toTypedArray())
    }

    override suspend fun deleteChatsV2(chatRooms: List<ChatRoomV2>) {
        chatRoomV2Dao.deleteChatRooms(*chatRooms.toTypedArray())
    }

    private fun messageToOpenAICompatibleMessage(apiType: ApiType, messages: List<Message>): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()

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

                apiType -> {
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
