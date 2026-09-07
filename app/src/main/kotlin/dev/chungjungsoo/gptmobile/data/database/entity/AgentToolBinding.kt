package dev.chungjungsoo.gptmobile.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "agent_tool_bindings",
    foreignKeys = [
        ForeignKey(
            entity = ToolConnection::class,
            parentColumns = ["connection_uid"],
            childColumns = ["connection_uid"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["profile_uid"]),
        Index(value = ["connection_uid"]),
        Index(value = ["profile_uid", "connection_uid", "tool_name"], unique = true)
    ]
)
data class AgentToolBinding(
    @PrimaryKey
    @ColumnInfo(name = "binding_uid")
    val bindingUid: String,

    @ColumnInfo(name = "profile_uid")
    val profileUid: String,

    @ColumnInfo(name = "connection_uid")
    val connectionUid: String?,

    @ColumnInfo(name = "tool_name")
    val toolName: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis() / 1000
)

object BuiltInAgentTool {
    const val CURRENT_DATE = "current_date"
    const val READ_URL = "read_url"
}
