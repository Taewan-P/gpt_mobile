package dev.chungjungsoo.gptmobile.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomDao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageDao
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPI
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
        settingRepository: SettingRepository,
        anthropicAPI: AnthropicAPI
    ): ChatRepository = ChatRepositoryImpl(chatRoomDao, messageDao, settingRepository, anthropicAPI)
}
