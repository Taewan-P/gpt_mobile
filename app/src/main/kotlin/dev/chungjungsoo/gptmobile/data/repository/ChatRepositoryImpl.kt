package dev.chungjungsoo.gptmobile.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.context.ContextBuilder
import dev.chungjungsoo.gptmobile.data.context.ConversationTurn
import dev.chungjungsoo.gptmobile.data.context.ProviderContextPolicy
import dev.chungjungsoo.gptmobile.data.database.dao.ChatPlatformModelV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomDao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageDao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageV2Dao
import dev.chungjungsoo.gptmobile.data.database.entity.ChatPlatformModelV2
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.Message
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.ImageContent as AnthropicImageContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.ImageSource
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
import dev.chungjungsoo.gptmobile.data.dto.google.request.SafetySetting
import dev.chungjungsoo.gptmobile.data.dto.groq.request.GroqChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.common.ImageContent as OpenAIImageContent
import dev.chungjungsoo.gptmobile.data.dto.openai.common.ImageUrl
import dev.chungjungsoo.gptmobile.data.dto.openai.common.MessageContent as OpenAIMessageContent
import dev.chungjungsoo.gptmobile.data.dto.openai.common.Role as OpenAIRole
import dev.chungjungsoo.gptmobile.data.dto.openai.common.TextContent as OpenAITextContent
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatMessage
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ReasoningConfig
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseContentPart
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseInputContent
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseInputMessage
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponsesRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.response.OutputTextDeltaEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ReasoningSummaryTextDeltaEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseErrorEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseFailedEvent
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.GeminiSafetySettings
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPI
import dev.chungjungsoo.gptmobile.data.network.GoogleAPI
import dev.chungjungsoo.gptmobile.data.network.GroqAPI
import dev.chungjungsoo.gptmobile.data.network.OpenAIAPI
import dev.chungjungsoo.gptmobile.util.AttachmentPayloadCache
import dev.chungjungsoo.gptmobile.util.FileUtils
import dev.chungjungsoo.gptmobile.util.stripAssistantErrorNote
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext

class ChatRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val chatRoomDao: ChatRoomDao,
    private val messageDao: MessageDao,
    private val chatRoomV2Dao: ChatRoomV2Dao,
    private val messageV2Dao: MessageV2Dao,
    private val chatPlatformModelV2Dao: ChatPlatformModelV2Dao,
    private val settingRepository: SettingRepository,
    private val openAIAPI: OpenAIAPI,
    private val groqAPI: GroqAPI,
    private val anthropicAPI: AnthropicAPI,
    private val googleAPI: GoogleAPI,
    private val attachmentUploadCoordinator: AttachmentUploadCoordinator,
    private val contextBuilder: ContextBuilder
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
        ClientType.OPENAI -> {
            // Use Responses API for OpenAI (supports reasoning/thinking)
            completeChatWithOpenAIResponses(userMessages, assistantMessages, platform)
        }

        ClientType.GROQ -> {
            completeChatWithGroq(userMessages, assistantMessages, platform)
        }

        ClientType.OLLAMA, ClientType.OPENROUTER, ClientType.CUSTOM -> {
            // Use Chat Completions API for OpenAI-compatible services
            completeChatWithOpenAIChatCompletions(userMessages, assistantMessages, platform)
        }

        ClientType.ANTHROPIC -> {
            completeChatWithAnthropic(userMessages, assistantMessages, platform)
        }

        ClientType.GOOGLE -> {
            completeChatWithGoogle(userMessages, assistantMessages, platform)
        }
    }

    private suspend fun completeChatWithOpenAIResponses(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): Flow<ApiState> = try {
        openAIAPI.setToken(platform.token)
        openAIAPI.setAPIUrl(platform.apiUrl)

        streamPreparedApiState(
            prepare = {
                val contextTurns = buildContextTurns(userMessages, assistantMessages, platform)
                val inputMessages = buildResponsesInputMessages(contextTurns, platform.uid)

                ResponsesRequest(
                    model = platform.model,
                    input = inputMessages,
                    stream = true,
                    instructions = platform.systemPrompt?.takeIf { it.isNotBlank() },
                    temperature = if (platform.reasoning) null else platform.temperature,
                    topP = if (platform.reasoning) null else platform.topP,
                    reasoning = if (platform.reasoning) {
                        ReasoningConfig(
                            effort = "medium",
                            summary = "auto"
                        )
                    } else {
                        null
                    }
                )
            },
            stream = { request ->
                flow {
                    openAIAPI.streamResponses(request, platform.timeout).collect { event ->
                        when (event) {
                            is ReasoningSummaryTextDeltaEvent -> emit(ApiState.Thinking(event.delta))

                            is OutputTextDeltaEvent -> emit(ApiState.Success(event.delta))

                            is ResponseFailedEvent -> {
                                val errorMessage = event.response.error?.message ?: "Response failed"
                                emit(ApiState.Error(errorMessage))
                            }

                            is ResponseErrorEvent -> emit(ApiState.Error(event.message))

                            else -> {}
                        }
                    }
                }
            }
        ).catch { e ->
            emit(ApiState.Error(e.message ?: "Unknown error"))
        }.onCompletion {
            emit(ApiState.Done)
        }
    } catch (e: Exception) {
        flowOf(ApiState.Error(e.message ?: "Failed to complete chat"))
    }

    private suspend fun completeChatWithGroq(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): Flow<ApiState> = try {
        streamPreparedApiState(
            prepare = {
                val contextTurns = buildContextTurns(userMessages, assistantMessages, platform)
                validateInlineBudgetIfNeeded(contextTurns, platform)
                val messages = buildOpenAIChatMessages(contextTurns, platform.systemPrompt)

                createGroqChatCompletionRequest(messages, platform)
            },
            stream = { request ->
                flow {
                    val parser = GroqReasoningParser()
                    groqAPI.streamChatCompletion(
                        request = request,
                        timeoutSeconds = platform.timeout,
                        token = platform.token,
                        apiUrl = platform.apiUrl
                    ).collect { chunk ->
                        when {
                            chunk.error != null -> emit(ApiState.Error(chunk.error.message))

                            else -> {
                                val choice = chunk.choices?.firstOrNull()
                                parser.append(
                                    reasoningChunk = choice?.delta?.reasoning ?: choice?.message?.reasoning,
                                    contentChunk = choice?.delta?.content ?: choice?.message?.content
                                ).forEach { emit(it) }
                            }
                        }
                    }

                    parser.flush().forEach { emit(it) }
                }
            }
        ).catch { e ->
            emit(ApiState.Error(e.message ?: "Unknown error"))
        }.onCompletion {
            emit(ApiState.Done)
        }
    } catch (e: Exception) {
        flowOf(ApiState.Error(e.message ?: "Failed to complete chat"))
    }

    private suspend fun completeChatWithOpenAIChatCompletions(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): Flow<ApiState> = try {
        openAIAPI.setToken(platform.token)
        openAIAPI.setAPIUrl(platform.apiUrl)

        streamPreparedApiState(
            prepare = {
                val contextTurns = buildContextTurns(userMessages, assistantMessages, platform)
                validateInlineBudgetIfNeeded(contextTurns, platform)
                val messages = buildOpenAIChatMessages(contextTurns, platform.systemPrompt)

                ChatCompletionRequest(
                    model = platform.model,
                    messages = messages,
                    stream = platform.stream,
                    temperature = platform.temperature,
                    topP = platform.topP
                )
            },
            stream = { request ->
                flow {
                    openAIAPI.streamChatCompletion(request, platform.timeout).collect { chunk ->
                        when {
                            chunk.error != null -> emit(ApiState.Error(chunk.error.message))

                            chunk.choices?.firstOrNull()?.delta?.content != null -> {
                                emit(ApiState.Success(chunk.choices.first().delta.content!!))
                            }
                        }
                    }
                }
            }
        ).catch { e ->
            emit(ApiState.Error(e.message ?: "Unknown error"))
        }.onCompletion {
            emit(ApiState.Done)
        }
    } catch (e: Exception) {
        flowOf(ApiState.Error(e.message ?: "Failed to complete chat"))
    }

    private suspend fun buildContextTurns(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): List<ConversationTurn> {
        val policy = ProviderContextPolicy.forClientType(platform.compatibleType)
        val contextTurns = contextBuilder.build(userMessages, assistantMessages, platform, policy)
        if (!policy.preferProviderFileRefs || contextTurns.isEmpty()) {
            return contextTurns
        }

        return ensureProviderReferencesForTurns(contextTurns, platform)
    }

    private suspend fun ensureProviderReferencesForTurns(
        turns: List<ConversationTurn>,
        platform: PlatformV2
    ): List<ConversationTurn> {
        val preparedUserMessages = prepareMessagesForPlatform(turns.map { it.userMessage }, platform)
        return turns.mapIndexed { index, turn ->
            turn.copy(userMessage = preparedUserMessages[index])
        }
    }

    private suspend fun validateInlineBudgetIfNeeded(
        contextTurns: List<ConversationTurn>,
        platform: PlatformV2
    ) {
        val maxInlineBytes = ProviderContextPolicy.forClientType(platform.compatibleType).maxInlineAttachmentBytes ?: return
        attachmentUploadCoordinator.validateInlineAttachmentBudget(contextTurns, maxInlineBytes)
    }

    private suspend fun buildOpenAIChatMessages(
        contextTurns: List<ConversationTurn>,
        systemPrompt: String?
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()

        systemPrompt?.takeIf { it.isNotBlank() }?.let { prompt ->
            messages.add(
                ChatMessage(
                    role = OpenAIRole.SYSTEM,
                    content = listOf(OpenAITextContent(text = prompt))
                )
            )
        }

        contextTurns.forEach { turn ->
            if (hasRenderableMessageContent(turn.userMessage, isUser = true)) {
                messages.add(transformMessageV2ToChatMessage(turn.userMessage, isUser = true))
            }
            turn.assistantMessage?.takeIf { hasRenderableMessageContent(it, isUser = false) }?.let { assistantMessage ->
                messages.add(transformMessageV2ToChatMessage(assistantMessage, isUser = false))
            }
        }

        return messages
    }

    private suspend fun buildResponsesInputMessages(
        contextTurns: List<ConversationTurn>,
        platformUid: String
    ): List<ResponseInputMessage> {
        val inputMessages = mutableListOf<ResponseInputMessage>()

        contextTurns.forEach { turn ->
            if (hasRenderableMessageContent(turn.userMessage, isUser = true)) {
                inputMessages.add(
                    transformMessageV2ToResponsesInput(
                        turn.userMessage,
                        isUser = true,
                        platformUid = platformUid
                    )
                )
            }
            turn.assistantMessage?.takeIf { hasRenderableMessageContent(it, isUser = false) }?.let { assistantMessage ->
                inputMessages.add(
                    transformMessageV2ToResponsesInput(
                        assistantMessage,
                        isUser = false,
                        platformUid = platformUid
                    )
                )
            }
        }

        return inputMessages
    }

    private suspend fun buildAnthropicInputMessages(
        contextTurns: List<ConversationTurn>,
        platformUid: String
    ): List<InputMessage> {
        val messages = mutableListOf<InputMessage>()

        contextTurns.forEach { turn ->
            if (hasRenderableMessageContent(turn.userMessage, isUser = true)) {
                messages.add(transformMessageV2ToAnthropic(turn.userMessage, MessageRole.USER, platformUid))
            }
            turn.assistantMessage?.takeIf { hasRenderableMessageContent(it, isUser = false) }?.let { assistantMessage ->
                messages.add(transformMessageV2ToAnthropic(assistantMessage, MessageRole.ASSISTANT, platformUid))
            }
        }

        return messages
    }

    private suspend fun buildGoogleContents(
        contextTurns: List<ConversationTurn>,
        platformUid: String
    ): List<Content> {
        val contents = mutableListOf<Content>()

        contextTurns.forEach { turn ->
            if (hasRenderableMessageContent(turn.userMessage, isUser = true)) {
                contents.add(transformMessageV2ToGoogle(turn.userMessage, GoogleRole.USER, platformUid))
            }
            turn.assistantMessage?.takeIf { hasRenderableMessageContent(it, isUser = false) }?.let { assistantMessage ->
                contents.add(transformMessageV2ToGoogle(assistantMessage, GoogleRole.MODEL, platformUid))
            }
        }

        return contents
    }

    private fun hasRenderableMessageContent(message: MessageV2, isUser: Boolean): Boolean {
        val messageContent = if (isUser) message.content else message.sendableAssistantContent()
        return messageContent.isNotBlank() || message.attachments.isNotEmpty()
    }

    private suspend fun transformMessageV2ToChatMessage(message: MessageV2, isUser: Boolean): ChatMessage {
        val content = mutableListOf<OpenAIMessageContent>()
        val messageContent = if (isUser) message.content else message.sendableAssistantContent()

        // Add text content
        if (messageContent.isNotBlank()) {
            content.add(OpenAITextContent(text = messageContent))
        }

        // Add file content (images)
        message.attachments.forEach { attachment ->
            val filePath = attachment.preparedFilePath.ifBlank { attachment.localFilePath }
            val mimeType = attachment.mimeType.ifBlank { FileUtils.getMimeType(context, filePath) }
            val encodedImage = getEncodedAttachment(filePath, mimeType)
            if (encodedImage != null) {
                content.add(
                    OpenAIImageContent(
                        imageUrl = ImageUrl(url = "data:${encodedImage.mimeType};base64,${encodedImage.base64Data}")
                    )
                )
            }
        }

        return ChatMessage(
            role = if (isUser) OpenAIRole.USER else OpenAIRole.ASSISTANT,
            content = content
        )
    }

    private suspend fun transformMessageV2ToResponsesInput(message: MessageV2, isUser: Boolean, platformUid: String): ResponseInputMessage {
        val role = if (isUser) "user" else "assistant"
        val messageContent = if (isUser) message.content else message.sendableAssistantContent()

        // Check if there are any image files
        val imageAttachments = message.attachments.filter { attachment ->
            val filePath = attachment.preparedFilePath.ifBlank { attachment.localFilePath }
            val mimeType = attachment.mimeType.ifBlank { FileUtils.getMimeType(context, filePath) }
            FileUtils.isImage(mimeType)
        }

        // If no images, use simple text content
        if (imageAttachments.isEmpty()) {
            return ResponseInputMessage(
                role = role,
                content = ResponseInputContent.text(messageContent)
            )
        }

        // Build content parts for text + images
        val parts = mutableListOf<ResponseContentPart>()

        // Add text content if not blank
        if (messageContent.isNotBlank()) {
            parts.add(ResponseContentPart.text(messageContent))
        }

        // Add image content
        imageAttachments.forEach { attachment ->
            val providerRef = attachment.providerRefFor(platformUid)
            if (providerRef?.remoteType == dev.chungjungsoo.gptmobile.data.model.AttachmentRemoteType.OPENAI_FILE) {
                parts.add(ResponseContentPart.imageFile(providerRef.remoteId))
            } else {
                val filePath = attachment.preparedFilePath.ifBlank { attachment.localFilePath }
                val mimeType = attachment.mimeType.ifBlank { FileUtils.getMimeType(context, filePath) }
                val encodedImage = getEncodedAttachment(filePath, mimeType)
                if (encodedImage != null) {
                    parts.add(
                        ResponseContentPart.image(
                            "data:${encodedImage.mimeType};base64,${encodedImage.base64Data}"
                        )
                    )
                }
            }
        }

        validateResponseInputPartsOrThrow(messageContent, parts.size, message.id)

        return ResponseInputMessage(
            role = role,
            content = ResponseInputContent.parts(parts)
        )
    }

    private suspend fun completeChatWithAnthropic(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): Flow<ApiState> = try {
        anthropicAPI.setToken(platform.token)
        anthropicAPI.setAPIUrl(platform.apiUrl)

        streamPreparedApiState(
            prepare = {
                val contextTurns = buildContextTurns(userMessages, assistantMessages, platform)
                val messages = buildAnthropicInputMessages(contextTurns, platform.uid)

                MessageRequest(
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
            },
            stream = { request ->
                flow {
                    anthropicAPI.streamChatMessage(request, platform.timeout).collect { chunk ->
                        when (chunk) {
                            is dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentDeltaResponseChunk -> {
                                when (chunk.delta.type) {
                                    dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentBlockType.THINKING_DELTA -> {
                                        chunk.delta.thinking?.let { emit(ApiState.Thinking(it)) }
                                    }

                                    dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentBlockType.DELTA -> {
                                        chunk.delta.text?.let { emit(ApiState.Success(it)) }
                                    }

                                    else -> {}
                                }
                            }

                            is dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ErrorResponseChunk -> {
                                emit(ApiState.Error(chunk.error.message))
                            }

                            else -> {}
                        }
                    }
                }
            }
        ).catch { e ->
            emit(ApiState.Error(e.message ?: "Unknown error"))
        }.onCompletion {
            emit(ApiState.Done)
        }
    } catch (e: Exception) {
        flowOf(ApiState.Error(e.message ?: "Failed to complete chat"))
    }

    private suspend fun transformMessageV2ToAnthropic(message: MessageV2, role: MessageRole, platformUid: String): InputMessage {
        val content = mutableListOf<AnthropicMessageContent>()
        val messageContent = if (role == MessageRole.USER) message.content else message.sendableAssistantContent()

        // Add text content
        if (messageContent.isNotBlank()) {
            content.add(AnthropicTextContent(text = messageContent))
        }

        // Add file content (images)
        message.attachments.forEach { attachment ->
            val providerRef = attachment.providerRefFor(platformUid)
            if (providerRef?.remoteType == dev.chungjungsoo.gptmobile.data.model.AttachmentRemoteType.ANTHROPIC_FILE) {
                content.add(AnthropicImageContent(source = ImageSource.file(providerRef.remoteId)))
            } else {
                val filePath = attachment.preparedFilePath.ifBlank { attachment.localFilePath }
                val mimeType = attachment.mimeType.ifBlank { FileUtils.getMimeType(context, filePath) }
                val encodedImage = getEncodedAttachment(filePath, mimeType)
                if (encodedImage != null) {
                    val mediaType = when {
                        encodedImage.mimeType.contains("jpeg") || encodedImage.mimeType.contains("jpg") -> MediaType.JPEG
                        encodedImage.mimeType.contains("png") -> MediaType.PNG
                        encodedImage.mimeType.contains("gif") -> MediaType.GIF
                        encodedImage.mimeType.contains("webp") -> MediaType.WEBP
                        else -> MediaType.JPEG
                    }

                    content.add(
                        AnthropicImageContent(
                            source = ImageSource.base64(mediaType, encodedImage.base64Data)
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
        googleAPI.setToken(platform.token)
        googleAPI.setAPIUrl(platform.apiUrl)

        streamPreparedApiState(
            prepare = {
                val contextTurns = buildContextTurns(userMessages, assistantMessages, platform)
                val contents = buildGoogleContents(contextTurns, platform.uid)

                GenerateContentRequest(
                    contents = contents,
                    generationConfig = GenerationConfig(
                        temperature = platform.temperature,
                        topP = platform.topP,
                        thinkingConfig = if (platform.reasoning) {
                            dev.chungjungsoo.gptmobile.data.dto.google.request.ThinkingConfig(
                                includeThoughts = true
                            )
                        } else {
                            null
                        }
                    ),
                    systemInstruction = platform.systemPrompt?.takeIf { it.isNotBlank() }?.let {
                        Content(
                            parts = listOf(Part.text(it))
                        )
                    },
                    safetySettings = platform.toGoogleSafetySettings()
                )
            },
            stream = { request ->
                flow {
                    googleAPI.streamGenerateContent(request, platform.model, platform.timeout).collect { response ->
                        when {
                            response.error != null -> emit(ApiState.Error(response.error.message))

                            response.promptFeedback?.blockReason != null -> {
                                emit(ApiState.Error("Gemini safety settings blocked the prompt: ${response.promptFeedback.blockReason}"))
                            }

                            response.candidates?.firstOrNull()?.finishReason == GOOGLE_SAFETY_FINISH_REASON -> {
                                emit(ApiState.Error("Gemini safety settings blocked the response."))
                            }

                            response.candidates?.firstOrNull()?.content?.parts != null -> {
                                val parts = response.candidates.first().content?.parts.orEmpty()
                                parts.forEach { part ->
                                    part.text?.let { text ->
                                        if (part.thought == true) {
                                            emit(ApiState.Thinking(text))
                                        } else {
                                            emit(ApiState.Success(text))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ).catch { e ->
            emit(ApiState.Error(e.message ?: "Unknown error"))
        }.onCompletion {
            emit(ApiState.Done)
        }
    } catch (e: Exception) {
        flowOf(ApiState.Error(e.message ?: "Failed to complete chat"))
    }

    private companion object {
        private const val GOOGLE_SAFETY_FINISH_REASON = "SAFETY"
    }

    private fun PlatformV2.toGoogleSafetySettings(): List<SafetySetting> = listOf(
        SafetySetting(
            category = GeminiSafetySettings.HARM_CATEGORY_HARASSMENT,
            threshold = GeminiSafetySettings.normalizeThreshold(harassmentSafetyThreshold)
        ),
        SafetySetting(
            category = GeminiSafetySettings.HARM_CATEGORY_HATE_SPEECH,
            threshold = GeminiSafetySettings.normalizeThreshold(hateSpeechSafetyThreshold)
        ),
        SafetySetting(
            category = GeminiSafetySettings.HARM_CATEGORY_SEXUALLY_EXPLICIT,
            threshold = GeminiSafetySettings.normalizeThreshold(sexuallyExplicitSafetyThreshold)
        ),
        SafetySetting(
            category = GeminiSafetySettings.HARM_CATEGORY_DANGEROUS_CONTENT,
            threshold = GeminiSafetySettings.normalizeThreshold(dangerousContentSafetyThreshold)
        )
    )

    private suspend fun transformMessageV2ToGoogle(message: MessageV2, role: GoogleRole, platformUid: String): Content {
        val parts = mutableListOf<Part>()
        val messageContent = if (role == GoogleRole.USER) message.content else message.sendableAssistantContent()

        // Add text content
        if (messageContent.isNotBlank()) {
            parts.add(Part.text(messageContent))
        }

        // Add file content (images)
        message.attachments.forEach { attachment ->
            val providerRef = attachment.providerRefFor(platformUid)
            if (providerRef?.remoteType == dev.chungjungsoo.gptmobile.data.model.AttachmentRemoteType.GOOGLE_FILE) {
                parts.add(Part.fileData(providerRef.mimeType, providerRef.remoteId))
            } else {
                val filePath = attachment.preparedFilePath.ifBlank { attachment.localFilePath }
                val mimeType = attachment.mimeType.ifBlank { FileUtils.getMimeType(context, filePath) }
                val encodedImage = getEncodedAttachment(filePath, mimeType)
                if (encodedImage != null) {
                    parts.add(Part.inlineData(encodedImage.mimeType, encodedImage.base64Data))
                }
            }
        }

        return Content(role = role, parts = parts)
    }

    private suspend fun getEncodedAttachment(filePath: String, mimeType: String): FileUtils.EncodedImage? {
        if (!FileUtils.isSupportedUploadMimeType(mimeType)) return null
        AttachmentPayloadCache.get(filePath)?.let { return it }

        return withContext(Dispatchers.IO) {
            FileUtils.encodeFileForUpload(context, filePath, mimeType)?.also { encodedImage ->
                AttachmentPayloadCache.put(filePath, encodedImage)
            }
        }
    }

    private suspend fun prepareMessagesForPlatform(
        messages: List<MessageV2>,
        platform: PlatformV2
    ): List<MessageV2> {
        val updatedMessages = messages.map { attachmentUploadCoordinator.ensureMessageAttachmentsForPlatform(it, platform) }
        val changedMessages = updatedMessages
            .zip(messages)
            .mapNotNull { (updated, original) -> updated.takeIf { it != original } }

        if (changedMessages.isNotEmpty()) {
            messageV2Dao.editMessages(*changedMessages.toTypedArray())
        }

        return updatedMessages
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

    override suspend fun fetchChatPlatformModels(chatId: Int): Map<String, String> = chatPlatformModelV2Dao.getByChatId(chatId).associate {
        it.platformUid to it.model
    }

    override suspend fun saveChatPlatformModels(chatId: Int, models: Map<String, String>) {
        val rows = models
            .filterKeys { it.isNotBlank() }
            .map { (platformUid, model) ->
                ChatPlatformModelV2(
                    chatId = chatId,
                    platformUid = platformUid,
                    model = model.trim()
                )
            }

        if (rows.isNotEmpty()) {
            chatPlatformModelV2Dao.upsertAll(*rows.toTypedArray())
        }
    }

    override suspend fun migrateToChatRoomV2MessageV2() {
        val leftOverChatRoomV2s = chatRoomV2Dao.getChatRooms()
        leftOverChatRoomV2s.forEach { chatPlatformModelV2Dao.deleteByChatId(it.id) }
        chatRoomV2Dao.deleteChatRooms(*leftOverChatRoomV2s.toTypedArray())

        val chatList = fetchChatList()
        val platforms = settingRepository.fetchPlatformV2s()
        val apiTypeMap = mutableMapOf<ApiType, String>()
        val modelByPlatformUid = mutableMapOf<String, String>()

        platforms.forEach { platform ->
            modelByPlatformUid[platform.uid] = platform.model
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
                    attachments = listOf(),
                    revisions = listOf(),
                    linkedMessageId = m.linkedMessageId,
                    platformType = m.platformType?.let { apiTypeMap[it] },
                    createdAt = m.createdAt
                )
            }

            val enabledPlatformUids = chatRoom.enabledPlatform.mapNotNull { apiTypeMap[it] }.filter { it.isNotBlank() }
            chatRoomV2Dao.addChatRoom(
                ChatRoomV2(
                    id = chatRoom.id,
                    title = chatRoom.title,
                    enabledPlatform = enabledPlatformUids,
                    createdAt = chatRoom.createdAt,
                    updatedAt = chatRoom.createdAt
                )
            )

            val modelRows = enabledPlatformUids.map { platformUid ->
                ChatPlatformModelV2(
                    chatId = chatRoom.id,
                    platformUid = platformUid,
                    model = modelByPlatformUid[platformUid] ?: ""
                )
            }

            if (modelRows.isNotEmpty()) {
                chatPlatformModelV2Dao.upsertAll(*modelRows.toTypedArray())
            }

            messageV2Dao.addMessages(*messages.toTypedArray())
        }
    }

    override fun generateDefaultChatTitle(messages: List<MessageV2>): String? = messages.sortedBy { it.createdAt }.firstOrNull { it.platformType == null }?.content?.replace('\n', ' ')?.take(50)

    override suspend fun updateChatTitle(chatRoom: ChatRoomV2, title: String) {
        chatRoomV2Dao.editChatRoom(chatRoom.copy(title = title.replace('\n', ' ').take(50)))
    }

    override suspend fun saveChat(chatRoom: ChatRoomV2, messages: List<MessageV2>, chatPlatformModels: Map<String, String>): ChatRoomV2 {
        if (chatRoom.id == 0) {
            // New Chat
            val chatId = chatRoomV2Dao.addChatRoom(chatRoom)
            val updatedMessages = messages.map { it.copy(chatId = chatId.toInt()) }
            messageV2Dao.addMessages(*updatedMessages.toTypedArray())
            saveChatPlatformModels(
                chatId = chatId.toInt(),
                models = chatPlatformModels.filterKeys { it in chatRoom.enabledPlatform }
            )

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
        saveChatPlatformModels(
            chatId = chatRoom.id,
            models = chatPlatformModels.filterKeys { it in chatRoom.enabledPlatform }
        )

        return chatRoom
    }

    override suspend fun duplicateChatV2(chatRoom: ChatRoomV2): ChatRoomV2 {
        val duplicatedTitle = "${chatRoom.title} (copy)".take(50)
        val duplicatedChatId = chatRoomV2Dao.addChatRoom(
            ChatRoomV2(
                title = duplicatedTitle,
                enabledPlatform = chatRoom.enabledPlatform
            )
        ).toInt()

        val messages = fetchMessagesV2(chatRoom.id).map { message ->
            message.copy(
                id = 0,
                chatId = duplicatedChatId,
                linkedMessageId = 0
            )
        }
        if (messages.isNotEmpty()) {
            messageV2Dao.addMessages(*messages.toTypedArray())
        }

        val chatPlatformModels = fetchChatPlatformModels(chatRoom.id)
        saveChatPlatformModels(duplicatedChatId, chatPlatformModels)

        return chatRoom.copy(
            id = duplicatedChatId,
            title = duplicatedTitle,
            createdAt = System.currentTimeMillis() / 1000,
            updatedAt = System.currentTimeMillis() / 1000
        )
    }

    override suspend fun deleteChats(chatRooms: List<ChatRoom>) {
        chatRoomDao.deleteChatRooms(*chatRooms.toTypedArray())
    }

    override suspend fun deleteChatsV2(chatRooms: List<ChatRoomV2>) {
        chatRoomV2Dao.deleteChatRooms(*chatRooms.toTypedArray())
    }
}

internal fun createGroqChatCompletionRequest(
    messages: List<ChatMessage>,
    platform: PlatformV2
): GroqChatCompletionRequest {
    val isGptOssModel = isGroqGptOssModel(platform.model)

    return GroqChatCompletionRequest(
        model = platform.model,
        messages = messages,
        stream = platform.stream,
        temperature = platform.temperature,
        topP = platform.topP,
        reasoningEffort = if (platform.reasoning && isGptOssModel) "medium" else null,
        reasoningFormat = when {
            platform.reasoning && !isGptOssModel -> "parsed"
            !platform.reasoning && !isGptOssModel -> "hidden"
            else -> null
        },
        includeReasoning = when {
            platform.reasoning && isGptOssModel -> true
            !platform.reasoning && isGptOssModel -> false
            else -> null
        }
    )
}

internal fun isGroqGptOssModel(model: String): Boolean = model.contains("gpt-oss", ignoreCase = true)

internal fun MessageV2.sendableAssistantContent(): String {
    val strippedContent = stripAssistantErrorNote(effectiveContent()).trim()
    return if (strippedContent.startsWith("Error: ")) "" else strippedContent
}

internal fun MessageV2.hasSendableAssistantPayload(): Boolean = sendableAssistantContent().isNotBlank() || attachments.isNotEmpty()

internal fun validateResponseInputPartsOrThrow(messageContent: String, partCount: Int, messageId: Int) {
    if (messageContent.isBlank() && partCount == 0) {
        throw IllegalStateException("No encodable message content for messageId=$messageId")
    }
}

internal fun <T> streamPreparedApiState(
    prepare: suspend () -> T,
    stream: suspend (T) -> Flow<ApiState>
): Flow<ApiState> = flow {
    emit(ApiState.Loading)
    val preparedRequest = withContext(Dispatchers.Default) { prepare() }
    emitAll(stream(preparedRequest))
}
