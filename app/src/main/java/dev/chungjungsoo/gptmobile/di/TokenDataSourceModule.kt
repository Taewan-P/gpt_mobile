package dev.chungjungsoo.gptmobile.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.datastore.TokenDataSource
import dev.chungjungsoo.gptmobile.data.datastore.TokenDataSourceImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TokenDataSourceModule {
    @Provides
    @Singleton
    fun provideTokenDataStore(dataStore: DataStore<Preferences>): TokenDataSource = TokenDataSourceImpl(dataStore)
}
