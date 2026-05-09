package dev.chungjungsoo.gptmobile.data.repository.provider

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.context.ConversationTurn
import dev.chungjungsoo.gptmobile.data.context.ContextBuilder
import dev.chungjungsoo.gptmobile.data.context.ProviderContextPolicy
import dev.chungjungsoo.gptmobile.data.database.dao.MessageV2Dao
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.ImageContent as AnthropicImageContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.ImageSource
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.MediaType
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.MessageContent as AnthropicMessageContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.MessageRole
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.TextContent as AnthropicTextContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.InputMessage
import dev.chungjungsoo.gptmobile.data.dto.google.common.Content
import dev.chungjungsoo.gptmobile.data.dto.google.common.Part
import dev.chungjungsoo.gptmobile.data.dto.google.common.Role as GoogleRole
import dev.chungjungsoo.gptmobile.data.dto.google.request.GenerationConfig
import dev.chungjungsoo.gptmobile.data.dto.openai.common.ImageContent as OpenAIImageContent
import dev.chungjungsoo.gptmobile.data.dto.openai.common.ImageUrl
import dev.chungjungsoo.gptmobile.data.dto.openai.common.MessageContent as OpenAIMessageContent
import dev.chungjungsoo.gptmobile.data.dto.openai.common.Role as OpenAIRole
import dev.chungjungsoo.gptmobile.data.dto.openai.common.TextContent as OpenAITextContent
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatMessage
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseContentPart
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseInputItem
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseInputMessage
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseInputContent
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseTool
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponsesRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ReasoningConfig
import dev.chungjungsoo.gptmobile.data.dto.google.request.GenerateContentRequest
import dev.chungjungsoo.gptmobile.data.model.AttachmentRemoteType
import dev.chungjungsoo.gptmobile.util.AttachmentPayloadCache
import dev.chungjungsoo.gptmobile.util.FileUtils
import dev.chungjungsoo.gptmobile.util.isAssistantErrorMessage
import dev.chungjungsoo.gptmobile.util.stripAssistantErrorNote
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.chungjungsoo.gptmobile.data.repository.sendableAssistantContent
import dev.chungjungsoo.gptmobile.data.repository.validateResponseInputPartsOrThrow
import dev.chungjungsoo.gptmobile.data.tool.ToolRegistry

class ProviderRequestBuilder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val contextBuilder: ContextBuilder,
    private val messageV2Dao: MessageV2Dao,
    private val attachmentUploadCoordinator: dev.chungjungsoo.gptmobile.data.repository.AttachmentUploadCoordinator
) {
    suspend fun buildContextTurns(
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

    suspend fun validateInlineBudgetIfNeeded(
        contextTurns: List<ConversationTurn>,
        platform: PlatformV2
    ) {
        val maxInlineBytes = ProviderContextPolicy.forClientType(platform.compatibleType).maxInlineAttachmentBytes ?: return
        attachmentUploadCoordinator.validateInlineAttachmentBudget(contextTurns, maxInlineBytes)
    }

    suspend fun buildOpenAIChatMessages(
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

    suspend fun buildOpenAICompatibleMessages(
        contextTurns: List<ConversationTurn>,
        systemPrompt: String?
    ): List<ChatMessage> = buildOpenAIChatMessages(contextTurns, systemPrompt)

    suspend fun buildResponsesInputItems(
        contextTurns: List<ConversationTurn>,
        platformUid: String
    ): List<ResponseInputItem> {
        val inputItems = mutableListOf<ResponseInputItem>()

        contextTurns.forEach { turn ->
            if (hasRenderableMessageContent(turn.userMessage, isUser = true)) {
                inputItems.add(
                    transformMessageV2ToResponsesInput(
                        turn.userMessage,
                        isUser = true,
                        platformUid = platformUid
                    )
                )
            }
            turn.assistantMessage?.takeIf { hasRenderableMessageContent(it, isUser = false) }?.let { assistantMessage ->
                inputItems.add(
                    transformMessageV2ToResponsesInput(
                        assistantMessage,
                        isUser = false,
                        platformUid = platformUid
                    )
                )
            }
        }

        return inputItems
    }

    suspend fun buildAnthropicInputMessages(
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

    suspend fun buildGoogleContents(
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

    fun buildResponsesRequest(
        model: String,
        input: List<ResponseInputItem>,
        systemPrompt: String?,
        temperature: Float?,
        topP: Float?,
        reasoning: Boolean,
        tools: List<ResponseTool>
    ): ResponsesRequest = ResponsesRequest(
        model = model,
        input = input,
        stream = true,
        instructions = systemPrompt?.takeIf { it.isNotBlank() },
        temperature = if (reasoning) null else temperature,
        topP = if (reasoning) null else topP,
        reasoning = if (reasoning) {
            ReasoningConfig(
                effort = "medium",
                summary = "auto"
            )
        } else {
            null
        },
        parallelToolCalls = false,
        tools = tools.takeIf { it.isNotEmpty() }
    )

    private fun hasRenderableMessageContent(message: MessageV2, isUser: Boolean): Boolean {
        val messageContent = if (isUser) message.content else message.sendableAssistantContent()
        return messageContent.isNotBlank() || message.attachments.isNotEmpty()
    }

    private suspend fun transformMessageV2ToChatMessage(message: MessageV2, isUser: Boolean): ChatMessage {
        val content = mutableListOf<OpenAIMessageContent>()
        val messageContent = if (isUser) message.content else message.sendableAssistantContent()

        if (messageContent.isNotBlank()) {
            content.add(OpenAITextContent(text = messageContent))
        }

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

    private suspend fun transformMessageV2ToResponsesInput(
        message: MessageV2,
        isUser: Boolean,
        platformUid: String
    ): ResponseInputItem {
        val role = if (isUser) "user" else "assistant"
        val messageContent = if (isUser) message.content else message.sendableAssistantContent()

        val imageAttachments = message.attachments.filter { attachment ->
            val filePath = attachment.preparedFilePath.ifBlank { attachment.localFilePath }
            val mimeType = attachment.mimeType.ifBlank { FileUtils.getMimeType(context, filePath) }
            FileUtils.isImage(mimeType)
        }

        if (imageAttachments.isEmpty()) {
            return ResponseInputItem.Message(
                ResponseInputMessage(
                    role = role,
                    content = ResponseInputContent.text(messageContent)
                )
            )
        }

        val parts = mutableListOf<ResponseContentPart>()

        if (messageContent.isNotBlank()) {
            parts.add(ResponseContentPart.text(messageContent))
        }

        imageAttachments.forEach { attachment ->
            val providerRef = attachment.providerRefFor(platformUid)
            if (providerRef?.remoteType == AttachmentRemoteType.OPENAI_FILE) {
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

        return ResponseInputItem.Message(
            ResponseInputMessage(
                role = role,
                content = ResponseInputContent.parts(parts)
            )
        )
    }

    private suspend fun transformMessageV2ToAnthropic(message: MessageV2, role: MessageRole, platformUid: String): InputMessage {
        val content = mutableListOf<AnthropicMessageContent>()
        val messageContent = if (role == MessageRole.USER) message.content else message.sendableAssistantContent()

        if (messageContent.isNotBlank()) {
            content.add(AnthropicTextContent(text = messageContent))
        }

        message.attachments.forEach { attachment ->
            val providerRef = attachment.providerRefFor(platformUid)
            if (providerRef?.remoteType == AttachmentRemoteType.ANTHROPIC_FILE) {
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

    private suspend fun transformMessageV2ToGoogle(message: MessageV2, role: GoogleRole, platformUid: String): Content {
        val parts = mutableListOf<Part>()
        val messageContent = if (role == GoogleRole.USER) message.content else message.sendableAssistantContent()

        if (messageContent.isNotBlank()) {
            parts.add(Part.text(messageContent))
        }

        message.attachments.forEach { attachment ->
            val providerRef = attachment.providerRefFor(platformUid)
            if (providerRef?.remoteType == AttachmentRemoteType.GOOGLE_FILE) {
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

    private suspend fun ensureProviderReferencesForTurns(
        turns: List<ConversationTurn>,
        platform: PlatformV2
    ): List<ConversationTurn> {
        val preparedUserMessages = prepareMessagesForPlatform(turns.map { it.userMessage }, platform)
        return turns.mapIndexed { index, turn ->
            turn.copy(userMessage = preparedUserMessages[index])
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
}
