package dev.chungjungsoo.gptmobile.data.database.entity

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "chats_v2")
data class ChatRoomV2(
    /*
    Now, enabled platforms are stored as list of strings.
    The strings are UUID V4 strings from PlatformV2.uid
     */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "chat_id")
    val id: Int = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "enabled_platform")
    val enabledPlatform: List<String>,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis() / 1000,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis() / 1000
) : Parcelable

class EnabledPlatformConverter {
    @TypeConverter
    fun fromString(value: String): List<String> = value.split(',')

    @TypeConverter
    fun fromList(value: List<String>): String = value.joinToString(",")
}
