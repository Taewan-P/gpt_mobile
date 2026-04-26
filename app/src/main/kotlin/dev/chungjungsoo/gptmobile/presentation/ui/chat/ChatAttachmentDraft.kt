package dev.chungjungsoo.gptmobile.presentation.ui.chat

import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import java.io.File

data class ChatAttachmentDraft(
    val sourceFilePath: String,
    val preparedFilePath: String? = null,
    val attachment: ChatAttachment? = null,
    val mimeType: String = "",
    val status: Status = Status.Preparing,
    val cleanupOnDiscard: Boolean = true,
    val notice: String? = null,
    val errorMessage: String? = null
) {
    val id: String = sourceFilePath
    val displayName: String = attachment?.resolvedDisplayName ?: File(preparedFilePath ?: sourceFilePath).name

    enum class Status {
        Preparing,
        Ready,
        Failed
    }

    companion object {
        fun fromAttachment(attachment: ChatAttachment): ChatAttachmentDraft = ChatAttachmentDraft(
            sourceFilePath = attachment.localFilePath,
            preparedFilePath = attachment.preparedFilePath,
            attachment = attachment,
            mimeType = attachment.mimeType,
            status = Status.Ready,
            cleanupOnDiscard = false
        )
    }
}
