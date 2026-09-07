package dev.chungjungsoo.gptmobile.data.agent.provider

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.context.ConversationTurn
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
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
import dev.chungjungsoo.gptmobile.data.dto.openai.common.ImageContent as OpenAIImageContent
import dev.chungjungsoo.gptmobile.data.dto.openai.common.ImageUrl
import dev.chungjungsoo.gptmobile.data.dto.openai.common.MessageContent as OpenAIMessageContent
import dev.chungjungsoo.gptmobile.data.dto.openai.common.Role as OpenAIRole
import dev.chungjungsoo.gptmobile.data.dto.openai.common.TextContent as OpenAITextContent
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatMessage
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseContentPart
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseInputContent
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseInputMessage
import dev.chungjungsoo.gptmobile.data.model.AttachmentRemoteType
import dev.chungjungsoo.gptmobile.data.repository.sendableAssistantContent
import dev.chungjungsoo.gptmobile.data.repository.validateResponseInputPartsOrThrow
import dev.chungjungsoo.gptmobile.util.AttachmentPayloadCache
import dev.chungjungsoo.gptmobile.util.FileUtils
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProviderAttachmentEncoder @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    suspend fun openAIChatMessages(
        turns: List<ConversationTurn>,
        systemPrompt: String?
    ): List<ChatMessage> = buildList {
        systemPrompt?.takeIf { it.isNotBlank() }?.let { prompt ->
            add(ChatMessage(OpenAIRole.SYSTEM, listOf(OpenAITextContent(prompt))))
        }
        turns.forEach { turn ->
            if (turn.userMessage.hasRenderableContent(isUser = true)) {
                add(openAIChatMessage(turn.userMessage, isUser = true))
            }
            turn.assistantMessage?.takeIf { it.hasRenderableContent(isUser = false) }?.let { message ->
                add(openAIChatMessage(message, isUser = false))
            }
        }
    }

    suspend fun responsesInput(
        turns: List<ConversationTurn>,
        platformUid: String
    ): List<ResponseInputMessage> = buildList {
        turns.forEach { turn ->
            if (turn.userMessage.hasRenderableContent(isUser = true)) {
                add(responseInput(turn.userMessage, isUser = true, platformUid))
            }
            turn.assistantMessage?.takeIf { it.hasRenderableContent(isUser = false) }?.let { message ->
                add(responseInput(message, isUser = false, platformUid))
            }
        }
    }

    suspend fun anthropicMessages(
        turns: List<ConversationTurn>,
        platformUid: String
    ): List<InputMessage> = buildList {
        turns.forEach { turn ->
            if (turn.userMessage.hasRenderableContent(isUser = true)) {
                add(anthropicMessage(turn.userMessage, MessageRole.USER, platformUid))
            }
            turn.assistantMessage?.takeIf { it.hasRenderableContent(isUser = false) }?.let { message ->
                add(anthropicMessage(message, MessageRole.ASSISTANT, platformUid))
            }
        }
    }

    suspend fun googleContents(
        turns: List<ConversationTurn>,
        platformUid: String
    ): List<Content> = buildList {
        turns.forEach { turn ->
            if (turn.userMessage.hasRenderableContent(isUser = true)) {
                add(googleContent(turn.userMessage, GoogleRole.USER, platformUid))
            }
            turn.assistantMessage?.takeIf { it.hasRenderableContent(isUser = false) }?.let { message ->
                add(googleContent(message, GoogleRole.MODEL, platformUid))
            }
        }
    }

    private suspend fun openAIChatMessage(message: MessageV2, isUser: Boolean): ChatMessage {
        val content = mutableListOf<OpenAIMessageContent>()
        val text = message.modelVisibleText(isUser)
        if (text.isNotBlank()) content += OpenAITextContent(text)
        message.attachments.forEach { attachment ->
            val filePath = attachment.preparedFilePath.ifBlank { attachment.localFilePath }
            val mimeType = attachment.mimeType.ifBlank { FileUtils.getMimeType(context, filePath) }
            encodedAttachment(filePath, mimeType)?.let { encoded ->
                content += OpenAIImageContent(ImageUrl("data:${encoded.mimeType};base64,${encoded.base64Data}"))
            }
        }
        return ChatMessage(
            role = if (isUser) OpenAIRole.USER else OpenAIRole.ASSISTANT,
            content = content
        )
    }

    private suspend fun responseInput(
        message: MessageV2,
        isUser: Boolean,
        platformUid: String
    ): ResponseInputMessage {
        val text = message.modelVisibleText(isUser)
        val images = message.attachments.filter { attachment ->
            val filePath = attachment.preparedFilePath.ifBlank { attachment.localFilePath }
            FileUtils.isImage(attachment.mimeType.ifBlank { FileUtils.getMimeType(context, filePath) })
        }
        if (images.isEmpty()) {
            return ResponseInputMessage(
                role = if (isUser) "user" else "assistant",
                content = ResponseInputContent.text(text)
            )
        }

        val parts = buildList {
            if (text.isNotBlank()) add(ResponseContentPart.text(text))
            images.forEach { attachment ->
                val providerRef = attachment.providerRefFor(platformUid)
                if (providerRef?.remoteType == AttachmentRemoteType.OPENAI_FILE) {
                    add(ResponseContentPart.imageFile(providerRef.remoteId))
                } else {
                    val filePath = attachment.preparedFilePath.ifBlank { attachment.localFilePath }
                    val mimeType = attachment.mimeType.ifBlank { FileUtils.getMimeType(context, filePath) }
                    encodedAttachment(filePath, mimeType)?.let { encoded ->
                        add(ResponseContentPart.image("data:${encoded.mimeType};base64,${encoded.base64Data}"))
                    }
                }
            }
        }
        validateResponseInputPartsOrThrow(text, parts.size, message.id)
        return ResponseInputMessage(
            role = if (isUser) "user" else "assistant",
            content = ResponseInputContent.parts(parts)
        )
    }

    private suspend fun anthropicMessage(
        message: MessageV2,
        role: MessageRole,
        platformUid: String
    ): InputMessage {
        val content = mutableListOf<AnthropicMessageContent>()
        val text = message.modelVisibleText(role == MessageRole.USER)
        if (text.isNotBlank()) content += AnthropicTextContent(text)
        message.attachments.forEach { attachment ->
            val providerRef = attachment.providerRefFor(platformUid)
            if (providerRef?.remoteType == AttachmentRemoteType.ANTHROPIC_FILE) {
                content += AnthropicImageContent(ImageSource.file(providerRef.remoteId))
            } else {
                val filePath = attachment.preparedFilePath.ifBlank { attachment.localFilePath }
                val mimeType = attachment.mimeType.ifBlank { FileUtils.getMimeType(context, filePath) }
                encodedAttachment(filePath, mimeType)?.let { encoded ->
                    content += AnthropicImageContent(
                        ImageSource.base64(encoded.mimeType.toAnthropicMediaType(), encoded.base64Data)
                    )
                }
            }
        }
        return InputMessage(role, content)
    }

    private suspend fun googleContent(
        message: MessageV2,
        role: GoogleRole,
        platformUid: String
    ): Content {
        val parts = mutableListOf<Part>()
        val text = message.modelVisibleText(role == GoogleRole.USER)
        if (text.isNotBlank()) parts += Part.text(text)
        message.attachments.forEach { attachment ->
            val providerRef = attachment.providerRefFor(platformUid)
            if (providerRef?.remoteType == AttachmentRemoteType.GOOGLE_FILE) {
                parts += Part.fileData(providerRef.mimeType, providerRef.remoteId)
            } else {
                val filePath = attachment.preparedFilePath.ifBlank { attachment.localFilePath }
                val mimeType = attachment.mimeType.ifBlank { FileUtils.getMimeType(context, filePath) }
                encodedAttachment(filePath, mimeType)?.let { encoded ->
                    parts += Part.inlineData(encoded.mimeType, encoded.base64Data)
                }
            }
        }
        return Content(role, parts)
    }

    private suspend fun encodedAttachment(filePath: String, mimeType: String): FileUtils.EncodedImage? {
        if (!FileUtils.isSupportedUploadMimeType(mimeType)) return null
        AttachmentPayloadCache.get(filePath)?.let { return it }
        return withContext(Dispatchers.IO) {
            FileUtils.encodeFileForUpload(context, filePath, mimeType)?.also { encoded ->
                AttachmentPayloadCache.put(filePath, encoded)
            }
        }
    }

    private fun MessageV2.hasRenderableContent(isUser: Boolean): Boolean = modelVisibleText(isUser).isNotBlank() || attachments.isNotEmpty()

    private fun MessageV2.modelVisibleText(isUser: Boolean): String = if (isUser) content else sendableAssistantContent()

    private fun String.toAnthropicMediaType(): MediaType = when {
        contains("png") -> MediaType.PNG
        contains("gif") -> MediaType.GIF
        contains("webp") -> MediaType.WEBP
        else -> MediaType.JPEG
    }
}
