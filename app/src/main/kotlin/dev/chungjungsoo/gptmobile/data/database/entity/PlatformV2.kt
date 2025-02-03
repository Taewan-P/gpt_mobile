package dev.chungjungsoo.gptmobile.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "platform_v2")
data class PlatformV2(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo("platform_id")
    val id: Int = 0,

    @ColumnInfo("uid")
    val uid: String = UUID.randomUUID().toString(),

    @ColumnInfo("name")
    val name: String,

    @ColumnInfo(name = "enabled")
    val enabled: Boolean = false,

    @ColumnInfo(name = "api_url")
    val apiUrl: String,

    @ColumnInfo(name = "token")
    val token: String? = null,

    @ColumnInfo(name = "model")
    val model: String,

    @ColumnInfo(name = "temperature")
    val temperature: Float? = null,

    @ColumnInfo(name = "top_p")
    val topP: Float? = null,

    @ColumnInfo(name = "system_prompt")
    val systemPrompt: String? = null,

    @ColumnInfo(name = "stream")
    val stream: Boolean = true,

    @ColumnInfo(name = "timeout")
    val timeout: Int = 30
)
