package dev.chungjungsoo.gptmobile.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "files")
    val files: List<String> = listOf(),

    @ColumnInfo(name = "linked_message_id")
    val linkedMessageId: Int = 0,

    @ColumnInfo(name = "platform_type")
    val platformType: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis() / 1000
)
