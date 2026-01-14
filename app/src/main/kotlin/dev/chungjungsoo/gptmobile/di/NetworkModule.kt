package dev.chungjungsoo.gptmobile.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPI
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPIImpl
import dev.chungjungsoo.gptmobile.data.network.GoogleAPI
import dev.chungjungsoo.gptmobile.data.network.GoogleAPIImpl
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.network.OpenAIAPI
import dev.chungjungsoo.gptmobile.data.network.OpenAIAPIImpl
import io.ktor.client.engine.cio.CIO
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideNetworkClient(): NetworkClient = NetworkClient(CIO)

    @Provides
    @Singleton
    fun provideOpenAIAPI(networkClient: NetworkClient): OpenAIAPI = OpenAIAPIImpl(networkClient)

    @Provides
    @Singleton
    fun provideAnthropicAPI(networkClient: NetworkClient): AnthropicAPI = AnthropicAPIImpl(networkClient)

    @Provides
    @Singleton
    fun provideGoogleAPI(networkClient: NetworkClient): GoogleAPI = GoogleAPIImpl(networkClient)
}
