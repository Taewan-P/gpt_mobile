package dev.chungjungsoo.gptmobile.data.repository

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomDao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageDao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageV2Dao
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.Message
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.ClientType
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import javax.inject.Inject
import kotlin.math.roundToInt
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
    private val settingRepository: SettingRepository
) : ChatRepository {

    override suspend fun completeChat(userMessages: List<MessageV2>, assistantMessages: List<List<MessageV2>>, platform: PlatformV2): Flow<ApiState> {
        val prompt = prompt(
            id = "${userMessages.hashCode()}${assistantMessages.hashCode()}_${platform.uid}",
            params = LLMParams(temperature = ((platform.temperature?.toDouble() ?: (1.0 * 10))).roundToInt() / 10.0)
        ) {
            platform.systemPrompt?.let { system(it) }
            userMessages.forEachIndexed { idx, msg ->
                user(msg.content) // TODO: Handle Attachments

                // The last assistant message is ignored
                assistantMessages.getOrNull(idx)
                    ?.takeIf { idx < userMessages.lastIndex }
                    ?.firstOrNull { it.platformType == platform.uid }
                    ?.let { assistant(it.content) }
            }
        }

        val model = LLModel(
            id = platform.model,
            provider = when (platform.compatibleType) {
                ClientType.OPENAI -> LLMProvider.OpenAI
                ClientType.ANTHROPIC -> LLMProvider.Anthropic
                ClientType.GOOGLE -> LLMProvider.Google
                ClientType.OPENROUTER -> LLMProvider.OpenRouter
                ClientType.OLLAMA -> LLMProvider.Ollama
                ClientType.CUSTOM -> LLMProvider.OpenAI
            },
            capabilities = listOf(
                LLMCapability.Temperature,
                LLMCapability.Vision.Image,
                LLMCapability.Schema.JSON.Full,
                LLMCapability.Document,
                LLMCapability.Completion
            )
        )

        val client = when (platform.compatibleType) {
            ClientType.OPENAI -> OpenAILLMClient(
                apiKey = platform.token ?: "",
                baseClient = HttpClient(CIO),
                settings = OpenAIClientSettings(baseUrl = platform.apiUrl)
            )

            ClientType.ANTHROPIC -> AnthropicLLMClient(
                apiKey = platform.token ?: "",
                baseClient = HttpClient(CIO),
                settings = AnthropicClientSettings(
                    baseUrl = platform.apiUrl,
                    modelVersionsMap = mapOf(Pair(model, platform.model))
                )
            )

            ClientType.GOOGLE -> GoogleLLMClient(
                apiKey = platform.token ?: "",
                baseClient = HttpClient(CIO),
                settings = GoogleClientSettings(baseUrl = platform.apiUrl)
            )

            ClientType.OPENROUTER -> OpenRouterLLMClient(
                apiKey = platform.token ?: "",
                baseClient = HttpClient(CIO),
                settings = OpenRouterClientSettings(baseUrl = platform.apiUrl)
            )

            ClientType.OLLAMA -> OllamaClient(
                baseUrl = platform.apiUrl,
                baseClient = HttpClient(CIO)
            )

            ClientType.CUSTOM -> OpenAILLMClient(
                apiKey = platform.token ?: "",
                baseClient = HttpClient(CIO),
                settings = OpenAIClientSettings(baseUrl = platform.apiUrl)
            )
        }

        return client.executeStreaming(prompt, model = model)
            .map<String, ApiState> { chunk -> ApiState.Success(chunk) }
            .catch { throwable -> emit(ApiState.Error(throwable.message ?: "Unknown Error")) }
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
                    revisions = listOf(),
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

    override fun generateDefaultChatTitle(messages: List<MessageV2>): String? = messages.sortedBy { it.createdAt }.firstOrNull { it.platformType == null }?.content?.replace('\n', ' ')?.take(50)

    override suspend fun updateChatTitle(chatRoom: ChatRoomV2, title: String) {
        chatRoomV2Dao.editChatRoom(chatRoom.copy(title = title.replace('\n', ' ').take(50)))
    }

    override suspend fun saveChat(chatRoom: ChatRoomV2, messages: List<MessageV2>): ChatRoomV2 {
        if (chatRoom.id == 0) {
            // New Chat
            val chatId = chatRoomV2Dao.addChatRoom(chatRoom)
            val updatedMessages = messages.map { it.copy(chatId = chatId.toInt()) }
            messageV2Dao.addMessages(*updatedMessages.toTypedArray())

            val savedChatRoom = chatRoom.copy(id = chatId.toInt())
            updateChatTitle(savedChatRoom, updatedMessages[0].content)

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
}
