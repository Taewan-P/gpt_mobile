package dev.chungjungsoo.gptmobile.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

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
