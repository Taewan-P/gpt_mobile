package dev.chungjungsoo.gptmobile.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.database.ChatDatabase
import dev.chungjungsoo.gptmobile.data.database.ChatDatabaseV2
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomDao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.McpServerDao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageDao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.PlatformV2Dao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DB_NAME = "chat"
    private const val DB_NAME_V2 = "chat_v2"

    @Provides
    fun providePlatformV2Dao(chatDatabaseV2: ChatDatabaseV2): PlatformV2Dao = chatDatabaseV2.platformDao()

    @Provides
    fun provideChatRoomDao(chatDatabase: ChatDatabase): ChatRoomDao = chatDatabase.chatRoomDao()

    @Provides
    fun provideChatRoomV2Dao(chatDatabaseV2: ChatDatabaseV2): ChatRoomV2Dao = chatDatabaseV2.chatRoomDao()

    @Provides
    fun provideMessageDao(chatDatabase: ChatDatabase): MessageDao = chatDatabase.messageDao()

    @Provides
    fun provideMessageV2Dao(chatDatabaseV2: ChatDatabaseV2): MessageV2Dao = chatDatabaseV2.messageDao()

    @Provides
    fun provideMcpServerDao(chatDatabaseV2: ChatDatabaseV2): McpServerDao = chatDatabaseV2.mcpServerDao()

    @Provides
    @Singleton
    fun provideChatDatabase(@ApplicationContext appContext: Context): ChatDatabase = Room.databaseBuilder(
        appContext,
        ChatDatabase::class.java,
        DB_NAME
    ).build()

    @Provides
    @Singleton
    fun provideChatDatabaseV2(@ApplicationContext appContext: Context): ChatDatabaseV2 = Room.databaseBuilder(
        appContext,
        ChatDatabaseV2::class.java,
        DB_NAME_V2
    )
        .addMigrations(MIGRATION_1_2_V2)
        .build()

    private val MIGRATION_1_2_V2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS mcp_servers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    url TEXT,
                    command TEXT,
                    args TEXT NOT NULL,
                    env TEXT NOT NULL,
                    headers TEXT NOT NULL,
                    enabled INTEGER NOT NULL,
                    allowed_tools TEXT
                )
                """.trimIndent()
            )
        }
    }
}
