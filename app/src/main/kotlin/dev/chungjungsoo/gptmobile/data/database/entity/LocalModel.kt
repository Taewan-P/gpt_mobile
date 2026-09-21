package dev.chungjungsoo.gptmobile.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelRecord
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStatus

@Entity(tableName = "local_models")
data class LocalModel(
    @PrimaryKey
    @ColumnInfo(name = "catalog_entry_id")
    val catalogEntryId: String,

    @ColumnInfo(name = "commit_hash")
    val commitHash: String,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "relative_directory")
    val relativeDirectory: String,

    @ColumnInfo(name = "total_bytes")
    val totalBytes: Long,

    @ColumnInfo(name = "status")
    val status: String = LocalModelStatus.DOWNLOADING,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis() / 1000,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis() / 1000
) {
    fun toRecord(): LocalModelRecord = LocalModelRecord(
        catalogEntryId = catalogEntryId,
        commitHash = commitHash,
        fileName = fileName,
        relativeDirectory = relativeDirectory,
        status = status
    )
}
