package dev.chungjungsoo.gptmobile.data.repository

import android.content.Context
import dev.chungjungsoo.gptmobile.data.context.ConversationTurn
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.AttachmentProviderRef
import dev.chungjungsoo.gptmobile.data.model.AttachmentRemoteType
import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPI
import dev.chungjungsoo.gptmobile.data.network.GoogleAPI
import dev.chungjungsoo.gptmobile.data.network.OpenAIAPI
import dev.chungjungsoo.gptmobile.util.FileUtils
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AttachmentUploadCoordinator @Inject constructor(
    private val openAIAPI: OpenAIAPI,
    private val anthropicAPI: AnthropicAPI,
    private val googleAPI: GoogleAPI
) {
    suspend fun prepareLocalAttachment(context: Context, filePath: String): ChatAttachment? = withContext(Dispatchers.IO) {
        val preparationResult = FileUtils.prepareAttachmentForUpload(context, filePath) ?: return@withContext null
        val preparedFilePath = preparationResult.preparedFilePath
        val dimensions = FileUtils.getImageDimensionsForDisplay(context, preparedFilePath)
        ChatAttachment(
            localFilePath = filePath,
            preparedFilePath = preparedFilePath,
            displayName = File(preparedFilePath).name,
            mimeType = preparationResult.mimeType,
            sizeBytes = FileUtils.getFileSize(context, preparedFilePath),
            width = dimensions?.first,
            height = dimensions?.second,
            wasResized = preparationResult.wasResized
        )
    }

    suspend fun ensureMessageAttachmentsForPlatform(message: MessageV2, platform: PlatformV2): MessageV2 {
        if (message.attachments.isEmpty()) return message
        val updatedAttachments = when (platform.compatibleType) {
            ClientType.OPENAI -> message.attachments.map { ensureOpenAIRef(it, platform.uid) }
            ClientType.ANTHROPIC -> message.attachments.map { ensureAnthropicRef(it, platform.uid) }
            ClientType.GOOGLE -> message.attachments.map { ensureGoogleRef(it, platform.uid) }
            else -> message.attachments
        }
        return if (updatedAttachments == message.attachments) message else message.copy(attachments = updatedAttachments)
    }

    suspend fun validateInlineAttachmentBudget(
        contextTurns: List<ConversationTurn>,
        maxInlineBytes: Long = MAX_SAFE_INLINE_BYTES
    ) {
        val totalPreparedBytes = contextTurns
            .flatMap { turn ->
                buildList {
                    addAll(turn.userMessage.attachments)
                    turn.assistantMessage?.let { addAll(it.attachments) }
                }
            }
            .sumOf { attachment ->
                val file = File(resolveUploadFilePath(attachment))
                when {
                    file.exists() -> file.length()
                    attachment.sizeBytes > 0L -> attachment.sizeBytes
                    else -> 0L
                }
            }

        if (totalPreparedBytes > maxInlineBytes) {
            throw IllegalStateException(
                "These images are too large to upload safely on this provider. Remove some images or use OpenAI, Anthropic, or Google."
            )
        }
    }

    private suspend fun ensureOpenAIRef(attachment: ChatAttachment, platformUid: String): ChatAttachment {
        val existingRef = attachment.providerRefFor(platformUid)
        if (existingRef?.remoteType == AttachmentRemoteType.OPENAI_FILE && openAIAPI.isFileAvailable(existingRef.remoteId)) {
            return attachment
        }

        val uploadFile = openAIAPI.uploadFile(
            filePath = resolveUploadFilePath(attachment),
            fileName = attachment.resolvedDisplayName,
            mimeType = resolveMimeType(attachment)
        )
        return attachment.upsertProviderRef(
            AttachmentProviderRef(
                platformUid = platformUid,
                remoteType = AttachmentRemoteType.OPENAI_FILE,
                remoteId = uploadFile.id,
                mimeType = uploadFile.mimeType,
                uploadedAt = System.currentTimeMillis() / 1000
            )
        )
    }

    private suspend fun ensureAnthropicRef(attachment: ChatAttachment, platformUid: String): ChatAttachment {
        val existingRef = attachment.providerRefFor(platformUid)
        if (existingRef?.remoteType == AttachmentRemoteType.ANTHROPIC_FILE && anthropicAPI.isFileAvailable(existingRef.remoteId)) {
            return attachment
        }

        val uploadFile = anthropicAPI.uploadFile(
            filePath = resolveUploadFilePath(attachment),
            fileName = attachment.resolvedDisplayName,
            mimeType = resolveMimeType(attachment)
        )
        return attachment.upsertProviderRef(
            AttachmentProviderRef(
                platformUid = platformUid,
                remoteType = AttachmentRemoteType.ANTHROPIC_FILE,
                remoteId = uploadFile.id,
                mimeType = uploadFile.mimeType,
                uploadedAt = System.currentTimeMillis() / 1000
            )
        )
    }

    private suspend fun ensureGoogleRef(attachment: ChatAttachment, platformUid: String): ChatAttachment {
        val existingRef = attachment.providerRefFor(platformUid)
        if (
            existingRef?.remoteType == AttachmentRemoteType.GOOGLE_FILE &&
            !existingRef.remoteName.isNullOrBlank() &&
            googleAPI.isFileAvailable(existingRef.remoteName)
        ) {
            return attachment
        }

        val uploadFile = googleAPI.uploadFile(
            filePath = resolveUploadFilePath(attachment),
            fileName = attachment.resolvedDisplayName,
            mimeType = resolveMimeType(attachment)
        )
        return attachment.upsertProviderRef(
            AttachmentProviderRef(
                platformUid = platformUid,
                remoteType = AttachmentRemoteType.GOOGLE_FILE,
                remoteId = uploadFile.uri ?: uploadFile.id,
                remoteName = uploadFile.name,
                mimeType = uploadFile.mimeType,
                uploadedAt = System.currentTimeMillis() / 1000
            )
        )
    }

    private fun resolveUploadFilePath(attachment: ChatAttachment): String = attachment.preparedFilePath.ifBlank { attachment.localFilePath }

    private fun resolveMimeType(attachment: ChatAttachment): String = attachment.mimeType.ifBlank {
        FileUtils.getMimeTypeFromPath(resolveUploadFilePath(attachment))
    }

    companion object {
        const val MAX_SAFE_INLINE_BYTES = 12L * 1024 * 1024
    }
}
