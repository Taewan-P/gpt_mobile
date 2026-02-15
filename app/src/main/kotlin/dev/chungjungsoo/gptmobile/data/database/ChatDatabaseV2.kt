package dev.chungjungsoo.gptmobile.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.chungjungsoo.gptmobile.data.database.dao.ChatPlatformModelV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.PlatformV2Dao
import dev.chungjungsoo.gptmobile.data.database.entity.ChatPlatformModelV2
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.StringListConverter

@Database(
    entities = [ChatRoomV2::class, MessageV2::class, PlatformV2::class, ChatPlatformModelV2::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(StringListConverter::class)
abstract class ChatDatabaseV2 : RoomDatabase() {

    abstract fun platformDao(): PlatformV2Dao
    abstract fun chatRoomDao(): ChatRoomV2Dao
    abstract fun messageDao(): MessageV2Dao
    abstract fun chatPlatformModelDao(): ChatPlatformModelV2Dao
}
