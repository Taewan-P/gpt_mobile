package dev.chungjungsoo.gptmobile.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.BuildConfig
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepository
import dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ModelCatalogModule {

    @Provides
    @Singleton
    fun provideModelCatalogRepository(
        @ApplicationContext context: Context,
        networkClient: NetworkClient
    ): ModelCatalogRepository = ModelCatalogRepositoryImpl(
        context = context,
        networkClient = networkClient,
        appVersionName = BuildConfig.VERSION_NAME
    )
}
