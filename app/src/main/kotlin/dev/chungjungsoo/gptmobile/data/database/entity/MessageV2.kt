package dev.chungjungsoo.gptmobile.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import kotlinx.serialization.Serializable

@Entity(
    tableName = "messages_v2",
    foreignKeys = [
        ForeignKey(
            entity = ChatRoomV2::class,
            parentColumns = ["chat_id"],
            childColumns = ["chat_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["chat_id"])]
)
data class MessageV2(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo("message_id")
    val id: Int = 0,

    @ColumnInfo(name = "chat_id")
    val chatId: Int = 0,

    @ColumnInfo(name = "thoughts")
    val thoughts: String = "",

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "attachments")
    val attachments: List<ChatAttachment> = listOf(),

    @ColumnInfo(name = "revisions")
    val revisions: List<AssistantRevision> = listOf(),

    @ColumnInfo(name = "active_revision_index")
    val activeRevisionIndex: Int = ACTIVE_REVISION_LATEST,

    @ColumnInfo(name = "linked_message_id")
    val linkedMessageId: Int = 0,

    @ColumnInfo(name = "platform_type")
    val platformType: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis() / 1000
)

@Serializable
data class AssistantRevision(
    val content: String,
    val thoughts: String = "",
    val createdAt: Long
)

const val ACTIVE_REVISION_LATEST = -1

fun MessageV2.hasHistoricalRevisionSelected(): Boolean = activeRevisionIndex in revisions.indices

fun MessageV2.effectiveContent(): String = revisions
    .getOrNull(activeRevisionIndex)
    ?.content
    ?: content

fun MessageV2.effectiveThoughts(): String = revisions
    .getOrNull(activeRevisionIndex)
    ?.thoughts
    ?: thoughts

fun MessageV2.isEffectivelyBlank(): Boolean = effectiveContent().isBlank() && attachments.isEmpty()

fun MessageV2.resetActiveRevision(): MessageV2 = copy(activeRevisionIndex = ACTIVE_REVISION_LATEST)

fun MessageV2.selectRevision(index: Int): MessageV2 = copy(
    activeRevisionIndex = if (index in revisions.indices) index else ACTIVE_REVISION_LATEST
)

fun MessageV2.snapshotLatestAssistantRevision(timestamp: Long = System.currentTimeMillis() / 1000): AssistantRevision? {
    if (platformType == null) return null
    if (content.isBlank() && thoughts.isBlank()) return null

    return AssistantRevision(
        content = content,
        thoughts = thoughts,
        createdAt = timestamp
    )
}
