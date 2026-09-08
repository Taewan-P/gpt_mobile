package dev.chungjungsoo.gptmobile.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "context_checkpoints",
    primaryKeys = ["chat_id", "platform_uid"],
    foreignKeys = [
        ForeignKey(
            entity = ChatRoomV2::class,
            parentColumns = ["chat_id"],
            childColumns = ["chat_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ContextCheckpointEntity(
    @ColumnInfo(name = "chat_id")
    val chatId: Int,

    @ColumnInfo(name = "platform_uid")
    val platformUid: String,

    @ColumnInfo(name = "source_prefix_fingerprint")
    val sourcePrefixFingerprint: String,

    @ColumnInfo(name = "endpoint_model_key")
    val endpointModelKey: String,

    @ColumnInfo(name = "covered_turn_count")
    val coveredTurnCount: Int,

    @ColumnInfo(name = "representation")
    val representation: String,

    @ColumnInfo(name = "serialized_working_context")
    val serializedWorkingContext: String,

    @ColumnInfo(name = "estimated_tokens")
    val estimatedTokens: Int? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "model_capacities",
    primaryKeys = ["platform_uid", "endpoint", "model"]
)
data class ModelCapacityEntity(
    @ColumnInfo(name = "platform_uid")
    val platformUid: String,

    @ColumnInfo(name = "endpoint")
    val endpoint: String,

    @ColumnInfo(name = "model")
    val model: String,

    @ColumnInfo(name = "detected_context_tokens")
    val detectedContextTokens: Int? = null,

    @ColumnInfo(name = "override_context_tokens")
    val overrideContextTokens: Int? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
