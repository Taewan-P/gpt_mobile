package dev.chungjungsoo.gptmobile.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.database.ChatDatabase
import dev.chungjungsoo.gptmobile.data.database.ChatDatabaseV2
import dev.chungjungsoo.gptmobile.data.database.ChatDatabaseV2Migrations
import dev.chungjungsoo.gptmobile.data.database.dao.AgentPersistenceDao
import dev.chungjungsoo.gptmobile.data.database.dao.AgentRunDao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatPlatformModelV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomDao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.LocalModelDao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageDao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.PlatformV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.ToolConnectionDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DB_NAME = "chat"
    private const val DB_NAME_V2 = "chat_v2"

    @Provides
    fun provideAgentRunDao(chatDatabaseV2: ChatDatabaseV2): AgentRunDao = chatDatabaseV2.agentRunDao()

    @Provides
    fun provideAgentPersistenceDao(chatDatabaseV2: ChatDatabaseV2): AgentPersistenceDao = chatDatabaseV2.agentPersistenceDao()

    @Provides
    fun provideToolConnectionDao(chatDatabaseV2: ChatDatabaseV2): ToolConnectionDao = chatDatabaseV2.toolConnectionDao()

    @Provides
    fun provideLocalModelDao(chatDatabaseV2: ChatDatabaseV2): LocalModelDao = chatDatabaseV2.localModelDao()

    @Provides
    fun provideChatPlatformModelV2Dao(chatDatabaseV2: ChatDatabaseV2): ChatPlatformModelV2Dao = chatDatabaseV2.chatPlatformModelDao()

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
    ).addMigrations(
        ChatDatabaseV2Migrations.MIGRATION_1_2,
        ChatDatabaseV2Migrations.MIGRATION_2_3,
        ChatDatabaseV2Migrations.MIGRATION_3_4,
        ChatDatabaseV2Migrations.MIGRATION_4_5,
        ChatDatabaseV2Migrations.MIGRATION_5_6,
        ChatDatabaseV2Migrations.MIGRATION_6_7,
        ChatDatabaseV2Migrations.MIGRATION_7_8,
        ChatDatabaseV2Migrations.MIGRATION_8_9,
        ChatDatabaseV2Migrations.MIGRATION_9_10
    ).addCallback(ChatDatabaseV2Migrations.AGENT_TOOL_BINDING_CALLBACK).build()
}
