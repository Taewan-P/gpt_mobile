package dev.chungjungsoo.gptmobile.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
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
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.ImageContent as AnthropicImageContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.ImageSource
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.ImageSourceType
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.MediaType
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.MessageContent as AnthropicMessageContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.MessageRole
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.TextContent as AnthropicTextContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.InputMessage
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.MessageRequest
import dev.chungjungsoo.gptmobile.data.dto.google.common.Content
import dev.chungjungsoo.gptmobile.data.dto.google.common.Part
import dev.chungjungsoo.gptmobile.data.dto.google.common.Role as GoogleRole
import dev.chungjungsoo.gptmobile.data.dto.google.request.GenerateContentRequest
import dev.chungjungsoo.gptmobile.data.dto.google.request.GenerationConfig
import dev.chungjungsoo.gptmobile.data.dto.openai.common.ImageContent as OpenAIImageContent
import dev.chungjungsoo.gptmobile.data.dto.openai.common.ImageUrl
import dev.chungjungsoo.gptmobile.data.dto.openai.common.MessageContent as OpenAIMessageContent
import dev.chungjungsoo.gptmobile.data.dto.openai.common.Role as OpenAIRole
import dev.chungjungsoo.gptmobile.data.dto.openai.common.TextContent as OpenAITextContent
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatMessage
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPI
import dev.chungjungsoo.gptmobile.data.network.GoogleAPI
import dev.chungjungsoo.gptmobile.data.network.OpenAIAPI
import dev.chungjungsoo.gptmobile.util.FileUtils
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion

class ChatRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatRoomDao: ChatRoomDao,
    private val messageDao: MessageDao,
    private val chatRoomV2Dao: ChatRoomV2Dao,
    private val messageV2Dao: MessageV2Dao,
    private val settingRepository: SettingRepository,
    private val openAIAPI: OpenAIAPI,
    private val anthropicAPI: AnthropicAPI,
    private val googleAPI: GoogleAPI
) : ChatRepository {

    private fun isImageFile(extension: String): Boolean = extension in setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff", "svg")

    private fun isDocumentFile(extension: String): Boolean = extension in setOf("pdf", "txt", "doc", "docx", "xls", "xlsx")

    private fun getMimeType(extension: String): String = when (extension) {
        // Images
        "jpg", "jpeg" -> "image/jpeg"

        "png" -> "image/png"

        "gif" -> "image/gif"

        "bmp" -> "image/bmp"

        "webp" -> "image/webp"

        "tiff" -> "image/tiff"

        "svg" -> "image/svg+xml"

        // Documents
        "pdf" -> "application/pdf"

        "txt" -> "text/plain"

        "doc" -> "application/msword"

        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

        "xls" -> "application/vnd.ms-excel"

        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

        else -> "application/octet-stream"
    }

    override suspend fun completeChat(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): Flow<ApiState> = when (platform.compatibleType) {
        ClientType.OPENAI, ClientType.GROQ, ClientType.OLLAMA, ClientType.OPENROUTER, ClientType.CUSTOM -> {
            completeChatWithOpenAI(userMessages, assistantMessages, platform)
        }

        ClientType.ANTHROPIC -> {
            completeChatWithAnthropic(userMessages, assistantMessages, platform)
        }

        ClientType.GOOGLE -> {
            completeChatWithGoogle(userMessages, assistantMessages, platform)
        }
    }

    private suspend fun completeChatWithOpenAI(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): Flow<ApiState> = try {
        // Configure API
        openAIAPI.setToken(platform.token)
        openAIAPI.setAPIUrl(platform.apiUrl)

        // Build message list
        val messages = mutableListOf<ChatMessage>()

        // Add system message if present
        platform.systemPrompt?.takeIf { it.isNotBlank() }?.let { systemPrompt ->
            messages.add(
                ChatMessage(
                    role = OpenAIRole.SYSTEM,
                    content = listOf(OpenAITextContent(text = systemPrompt))
                )
            )
        }

        // Add conversation history (interleaved user and assistant messages)
        userMessages.forEachIndexed { index, userMsg ->
            // Add user message
            messages.add(transformMessageV2ToOpenAI(userMsg, isUser = true))

            // Add assistant responses if available (skip empty responses)
            if (index < assistantMessages.size) {
                assistantMessages[index]
                    .filter { it.content.isNotBlank() }
                    .forEach { assistantMsg ->
                        messages.add(transformMessageV2ToOpenAI(assistantMsg, isUser = false))
                    }
            }
        }

        // Create request
        val request = ChatCompletionRequest(
            model = platform.model,
            messages = messages,
            stream = platform.stream,
            temperature = platform.temperature,
            topP = platform.topP
        )

        // Stream response
        flow {
            emit(ApiState.Loading)
            openAIAPI.streamChatCompletion(request).collect { chunk ->
                when {
                    chunk.error != null -> emit(ApiState.Error(chunk.error.message))

                    chunk.choices?.firstOrNull()?.delta?.content != null -> {
                        emit(ApiState.Success(chunk.choices.first().delta.content!!))
                    }

                    chunk.choices?.firstOrNull()?.finishReason != null -> emit(ApiState.Done)
                }
            }
        }.catch { e ->
            emit(ApiState.Error(e.message ?: "Unknown error"))
        }.onCompletion {
            emit(ApiState.Done)
        }
    } catch (e: Exception) {
        flowOf(ApiState.Error(e.message ?: "Failed to complete chat"))
    }

    private fun transformMessageV2ToOpenAI(message: MessageV2, isUser: Boolean): ChatMessage {
        val content = mutableListOf<OpenAIMessageContent>()

        // Add text content
        if (message.content.isNotBlank()) {
            content.add(OpenAITextContent(text = message.content))
        }

        // Add file content (images)
        message.files.forEach { fileUri ->
            val mimeType = FileUtils.getMimeType(context, fileUri)
            if (FileUtils.isImage(mimeType)) {
                val base64 = FileUtils.readAndEncodeFile(context, fileUri)
                if (base64 != null) {
                    content.add(
                        OpenAIImageContent(
                            imageUrl = ImageUrl(url = "data:$mimeType;base64,$base64")
                        )
                    )
                }
            }
        }

        return ChatMessage(
            role = if (isUser) OpenAIRole.USER else OpenAIRole.ASSISTANT,
            content = content
        )
    }

    private suspend fun completeChatWithAnthropic(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): Flow<ApiState> = try {
        // Configure API
        anthropicAPI.setToken(platform.token)
        anthropicAPI.setAPIUrl(platform.apiUrl)

        // Build message list (Anthropic alternates user/assistant)
        val messages = mutableListOf<InputMessage>()

        userMessages.forEachIndexed { index, userMsg ->
            // Add user message
            messages.add(transformMessageV2ToAnthropic(userMsg, MessageRole.USER))

            // Add assistant responses if available (skip empty responses)
            if (index < assistantMessages.size) {
                assistantMessages[index]
                    .filter { it.content.isNotBlank() }
                    .forEach { assistantMsg ->
                        messages.add(transformMessageV2ToAnthropic(assistantMsg, MessageRole.ASSISTANT))
                    }
            }
        }

        // Create request
        // Note: When thinking is enabled, temperature defaults to 1 and top_p/top_k are not allowed
        val request = MessageRequest(
            model = platform.model,
            messages = messages,
            maxTokens = if (platform.reasoning) 16000 else 4096,
            stream = platform.stream,
            systemPrompt = platform.systemPrompt,
            temperature = if (platform.reasoning) null else platform.temperature,
            topP = if (platform.reasoning) null else platform.topP,
            thinking = if (platform.reasoning) {
                dev.chungjungsoo.gptmobile.data.dto.anthropic.request.ThinkingConfig(
                    type = "enabled",
                    budgetTokens = 10000
                )
            } else {
                null
            }
        )

        // Stream response
        flow {
            emit(ApiState.Loading)
            anthropicAPI.streamChatMessage(request).collect { chunk ->
                when (chunk) {
                    is dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentDeltaResponseChunk -> {
                        when (chunk.delta.type) {
                            dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentBlockType.THINKING_DELTA -> {
                                chunk.delta.thinking?.let { emit(ApiState.Thinking(it)) }
                            }

                            dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentBlockType.DELTA -> {
                                chunk.delta.text?.let { emit(ApiState.Success(it)) }
                            }

                            // Ignore signature blocks and other types
                            else -> { }
                        }
                    }

                    is dev.chungjungsoo.gptmobile.data.dto.anthropic.response.MessageStopResponseChunk -> {
                        emit(ApiState.Done)
                    }

                    is dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ErrorResponseChunk -> {
                        emit(ApiState.Error(chunk.error.message))
                    }

                    else -> { /* Ignore other chunk types */ }
                }
            }
        }.catch { e ->
            emit(ApiState.Error(e.message ?: "Unknown error"))
        }.onCompletion {
            emit(ApiState.Done)
        }
    } catch (e: Exception) {
        flowOf(ApiState.Error(e.message ?: "Failed to complete chat"))
    }

    private fun transformMessageV2ToAnthropic(message: MessageV2, role: MessageRole): InputMessage {
        val content = mutableListOf<AnthropicMessageContent>()

        // Add text content
        if (message.content.isNotBlank()) {
            content.add(AnthropicTextContent(text = message.content))
        }

        // Add file content (images)
        message.files.forEach { fileUri ->
            val mimeType = FileUtils.getMimeType(context, fileUri)
            if (FileUtils.isImage(mimeType)) {
                val base64 = FileUtils.readAndEncodeFile(context, fileUri)
                if (base64 != null) {
                    val mediaType = when {
                        mimeType.contains("jpeg") || mimeType.contains("jpg") -> MediaType.JPEG
                        mimeType.contains("png") -> MediaType.PNG
                        mimeType.contains("gif") -> MediaType.GIF
                        mimeType.contains("webp") -> MediaType.WEBP
                        else -> MediaType.JPEG // Default
                    }

                    content.add(
                        AnthropicImageContent(
                            source = ImageSource(
                                type = ImageSourceType.BASE64,
                                mediaType = mediaType,
                                data = base64
                            )
                        )
                    )
                }
            }
        }

        return InputMessage(role = role, content = content)
    }

    private suspend fun completeChatWithGoogle(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): Flow<ApiState> = try {
        // Configure API
        googleAPI.setToken(platform.token)
        googleAPI.setAPIUrl(platform.apiUrl)

        // Build contents list
        val contents = mutableListOf<Content>()

        userMessages.forEachIndexed { index, userMsg ->
            // Add user message
            contents.add(transformMessageV2ToGoogle(userMsg, GoogleRole.USER))

            // Add assistant responses if available (skip empty responses)
            if (index < assistantMessages.size) {
                assistantMessages[index]
                    .filter { it.content.isNotBlank() }
                    .forEach { assistantMsg ->
                        contents.add(transformMessageV2ToGoogle(assistantMsg, GoogleRole.MODEL))
                    }
            }
        }

        // Create request
        val request = GenerateContentRequest(
            contents = contents,
            generationConfig = GenerationConfig(
                temperature = platform.temperature,
                topP = platform.topP
            ),
            systemInstruction = platform.systemPrompt?.takeIf { it.isNotBlank() }?.let {
                Content(
                    parts = listOf(Part.text(it))
                )
            }
        )

        // Stream response
        flow {
            emit(ApiState.Loading)
            googleAPI.streamGenerateContent(request, platform.model).collect { response ->
                when {
                    response.error != null -> emit(ApiState.Error(response.error.message))

                    response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text != null -> {
                        val text = response.candidates!!.first().content.parts.first().text!!
                        emit(ApiState.Success(text))
                    }

                    response.candidates?.firstOrNull()?.finishReason != null -> emit(ApiState.Done)
                }
            }
        }.catch { e ->
            emit(ApiState.Error(e.message ?: "Unknown error"))
        }.onCompletion {
            emit(ApiState.Done)
        }
    } catch (e: Exception) {
        flowOf(ApiState.Error(e.message ?: "Failed to complete chat"))
    }

    private fun transformMessageV2ToGoogle(message: MessageV2, role: GoogleRole): Content {
        val parts = mutableListOf<Part>()

        // Add text content
        if (message.content.isNotBlank()) {
            parts.add(Part.text(message.content))
        }

        // Add file content (images)
        message.files.forEach { fileUri ->
            val mimeType = FileUtils.getMimeType(context, fileUri)
            if (FileUtils.isImage(mimeType)) {
                val base64 = FileUtils.readAndEncodeFile(context, fileUri)
                if (base64 != null) {
                    parts.add(Part.inlineData(mimeType, base64))
                }
            }
        }

        return Content(role = role, parts = parts)
    }

    override suspend fun fetchChatList(): List<ChatRoom> = chatRoomDao.getChatRooms()

    override suspend fun fetchChatListV2(): List<ChatRoomV2> = chatRoomV2Dao.getChatRooms()

    override suspend fun searchChatsV2(query: String): List<ChatRoomV2> {
        if (query.isBlank()) {
            return chatRoomV2Dao.getChatRooms()
        }

        // Search by title
        val titleMatches = chatRoomV2Dao.searchChatRoomsByTitle(query)

        // Search by message content and get chat IDs
        val messageMatchChatIds = messageV2Dao.searchMessagesByContent(query)

        // Get all chat rooms and filter by message match IDs
        val allChatRooms = chatRoomV2Dao.getChatRooms()
        val messageMatches = allChatRooms.filter { it.id in messageMatchChatIds }

        // Combine results and remove duplicates, maintaining order by updatedAt
        return (titleMatches + messageMatches)
            .distinctBy { it.id }
            .sortedByDescending { it.updatedAt }
    }

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
