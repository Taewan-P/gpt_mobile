package dev.chungjungsoo.gptmobile.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import dev.chungjungsoo.gptmobile.data.dto.ApiType

@Entity(tableName = "chats")
data class ChatRoom(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "chat_id")
    val id: Int = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "enabled_platform")
    val enabledPlatform: List<ApiType>,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis() / 1000
)

class APITypeConverter {
    @TypeConverter
    fun fromString(value: String): List<ApiType> {
        val splitted = value.split(',')

        return splitted.map { s -> ApiType.valueOf(s) }
    }

    @TypeConverter
    fun fromList(value: List<ApiType>): String {
        return value.joinToString(",") { v -> v.name }
    }
}
