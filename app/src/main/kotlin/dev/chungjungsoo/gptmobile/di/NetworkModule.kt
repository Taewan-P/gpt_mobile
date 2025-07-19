package dev.chungjungsoo.gptmobile.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPI
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPIImpl
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
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
    fun provideAnthropicAPI(): AnthropicAPI = AnthropicAPIImpl(provideNetworkClient())
}
