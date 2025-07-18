package dev.chungjungsoo.gptmobile.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomDao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageDao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageV2Dao
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.data.repository.ChatRepositoryImpl
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ChatRepositoryModule {

    @Provides
    @Singleton
    fun provideChatRepository(
        chatRoomDao: ChatRoomDao,
        messageDao: MessageDao,
        chatRoomV2Dao: ChatRoomV2Dao,
        messageV2Dao: MessageV2Dao,
        settingRepository: SettingRepository
    ): ChatRepository = ChatRepositoryImpl(
        chatRoomDao,
        messageDao,
        chatRoomV2Dao,
        messageV2Dao,
        settingRepository
    )
}
