package dev.chungjungsoo.gptmobile.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "agent_runs",
    foreignKeys = [
        ForeignKey(
            entity = ChatRoomV2::class,
            parentColumns = ["chat_id"],
            childColumns = ["chat_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MessageV2::class,
            parentColumns = ["message_id"],
            childColumns = ["user_message_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MessageV2::class,
            parentColumns = ["message_id"],
            childColumns = ["assistant_message_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["chat_id"]),
        Index(value = ["user_message_id"]),
        Index(value = ["assistant_message_id"]),
        Index(value = ["status"])
    ]
)
data class AgentRun(
    @PrimaryKey
    @ColumnInfo(name = "run_id")
    val runId: String,

    @ColumnInfo(name = "chat_id")
    val chatId: Int,

    @ColumnInfo(name = "user_message_id")
    val userMessageId: Int,

    @ColumnInfo(name = "assistant_message_id")
    val assistantMessageId: Int,

    @ColumnInfo(name = "profile_uid")
    val profileUid: String,

    @ColumnInfo(name = "provider_snapshot")
    val providerSnapshot: String,

    @ColumnInfo(name = "model_snapshot")
    val modelSnapshot: String,

    @ColumnInfo(name = "status")
    val status: String = AgentRunStatus.QUEUED,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis() / 1000,

    @ColumnInfo(name = "started_at")
    val startedAt: Long? = null,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,

    @ColumnInfo(name = "terminal_error")
    val terminalError: String? = null
)

data class AgentRunDraft(
    val runId: String,
    val profileUid: String,
    val providerSnapshot: String,
    val modelSnapshot: String,
    val createdAt: Long = System.currentTimeMillis() / 1000
)

data class PersistAgentTurnRequest(
    val chatRoom: ChatRoomV2,
    val userMessage: MessageV2,
    val runs: List<AgentRunDraft>,
    val chatPlatformModels: Map<String, String>
)

data class PersistAgentTurnResult(
    val chatRoom: ChatRoomV2,
    val userMessage: MessageV2,
    val assistantMessages: List<MessageV2>,
    val runs: List<AgentRun>
)

data class PersistAgentRetryRequest(
    val userMessage: MessageV2,
    val assistantMessage: MessageV2,
    val run: AgentRunDraft
)

data class PersistAgentRetryResult(
    val assistantMessage: MessageV2,
    val run: AgentRun
)

object AgentRunStatus {
    const val QUEUED = "QUEUED"
    const val RUNNING = "RUNNING"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
    const val CANCELED = "CANCELED"
    const val INTERRUPTED = "INTERRUPTED"
}

object AgentRunTerminalError {
    const val SERVICE_STOPPED = "SERVICE_STOPPED"
}
