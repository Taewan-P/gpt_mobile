package dev.chungjungsoo.gptmobile.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(
    tableName = "mcp_tool_events",
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
data class McpToolEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo("id")
    val id: Int = 0,

    @ColumnInfo("chat_id")
    val chatId: Int,

    @ColumnInfo("message_index")
    val messageIndex: Int,

    @ColumnInfo("platform_index")
    val platformIndex: Int,

    @ColumnInfo("call_id")
    val callId: String,

    @ColumnInfo("tool_name")
    val toolName: String,

    @ColumnInfo("request")
    val request: String,

    @ColumnInfo("output")
    val output: String = "",

    @ColumnInfo("status")
    val status: ToolCallStatus = ToolCallStatus.REQUESTED,

    @ColumnInfo("is_error")
    val isError: Boolean = false
)

enum class ToolCallStatus {
    REQUESTED,
    EXECUTING,
    COMPLETED,
    FAILED
}

class ToolCallStatusConverter {
    @TypeConverter
    fun fromStatus(value: ToolCallStatus): String = value.name

    @TypeConverter
    fun toStatus(value: String): ToolCallStatus = ToolCallStatus.valueOf(value)
}
