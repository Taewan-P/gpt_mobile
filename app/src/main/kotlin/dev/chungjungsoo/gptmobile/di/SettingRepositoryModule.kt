package dev.chungjungsoo.gptmobile.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.database.dao.ChatPlatformModelV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.PlatformV2Dao
import dev.chungjungsoo.gptmobile.data.datastore.SettingDataSource
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingRepositoryModule {

    @Provides
    @Singleton
    fun provideSettingRepository(
        settingDataSource: SettingDataSource,
        platformV2Dao: PlatformV2Dao,
        chatPlatformModelV2Dao: ChatPlatformModelV2Dao
    ): SettingRepository = SettingRepositoryImpl(settingDataSource, platformV2Dao, chatPlatformModelV2Dao)
}
