package dev.chungjungsoo.gptmobile.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tool_events",
    foreignKeys = [
        ForeignKey(
            entity = AgentRun::class,
            parentColumns = ["run_id"],
            childColumns = ["run_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["run_id"]),
        Index(value = ["run_id", "sequence"], unique = true)
    ]
)
data class ToolEvent(
    @PrimaryKey
    @ColumnInfo(name = "event_id")
    val eventId: String,

    @ColumnInfo(name = "run_id")
    val runId: String,

    @ColumnInfo(name = "sequence")
    val sequence: Int,

    @ColumnInfo(name = "call_id")
    val callId: String,

    @ColumnInfo(name = "connection_uid_snapshot")
    val connectionUidSnapshot: String?,

    @ColumnInfo(name = "connection_name_snapshot")
    val connectionNameSnapshot: String?,

    @ColumnInfo(name = "tool_name")
    val toolName: String,

    @ColumnInfo(name = "model_tool_name")
    val modelToolName: String,

    @ColumnInfo(name = "arguments")
    val arguments: String,

    @ColumnInfo(name = "result")
    val result: String?,

    @ColumnInfo(name = "result_type")
    val resultType: String?,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "is_error")
    val isError: Boolean = false,

    @ColumnInfo(name = "started_at")
    val startedAt: Long? = null,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,

    @ColumnInfo(name = "error")
    val error: String? = null
)

object ToolEventStatus {
    const val PENDING = "PENDING"
    const val RUNNING = "RUNNING"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
    const val CANCELED = "CANCELED"
}

object ToolEventResultType {
    const val TEXT = "TEXT"
    const val JSON = "JSON"
    const val RESOURCE_LINKS = "RESOURCE_LINKS"
    const val UNSUPPORTED = "UNSUPPORTED"
}

object ToolEventError {
    const val INTERRUPTED_APP_STOPPED = "INTERRUPTED_APP_STOPPED"
}
