package dev.chungjungsoo.gptmobile.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.BuildConfig
import dev.chungjungsoo.gptmobile.data.database.dao.LocalModelDao
import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceOAuthConfig
import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceTokenStore
import dev.chungjungsoo.gptmobile.data.localmodel.GatedDownloadCoordinator
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelDownloadProber
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelDownloadProberImpl
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepositoryImpl
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.HuggingFaceAuthClient
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.HuggingFaceAuthClientImpl
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.LocalDownloadGuards
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.LocalDownloadGuardsImpl
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object LocalModelModule {

    @Provides
    @Singleton
    fun provideLocalModelDownloadProber(
        impl: LocalModelDownloadProberImpl
    ): LocalModelDownloadProber = impl

    @Provides
    @Singleton
    fun provideGatedDownloadCoordinator(
        tokenStore: HuggingFaceTokenStore,
        prober: LocalModelDownloadProber
    ): GatedDownloadCoordinator = GatedDownloadCoordinator(
        tokenStore = tokenStore,
        prober = prober,
        isOAuthConfigured = {
            HuggingFaceOAuthConfig.isConfigured(
                BuildConfig.HF_OAUTH_CLIENT_ID,
                BuildConfig.HF_OAUTH_REDIRECT_URI
            )
        },
        ioDispatcher = Dispatchers.IO
    )

    @Provides
    @Singleton
    fun provideLocalDownloadGuards(
        impl: LocalDownloadGuardsImpl
    ): LocalDownloadGuards = impl

    @Provides
    fun provideHuggingFaceAuthClient(
        impl: HuggingFaceAuthClientImpl
    ): HuggingFaceAuthClient = impl

    @Provides
    @Singleton
    fun provideLocalModelRepository(
        @ApplicationContext context: Context,
        localModelDao: LocalModelDao,
        @DeviceSocModel deviceSocModel: String
    ): LocalModelRepository = LocalModelRepositoryImpl(
        context = context,
        localModelDao = localModelDao,
        deviceSocModel = deviceSocModel,
        ioDispatcher = Dispatchers.IO
    )
}
