package dev.chungjungsoo.gptmobile.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.tool.BingSearchBackend
import dev.chungjungsoo.gptmobile.data.tool.DefaultToolExecutor
import dev.chungjungsoo.gptmobile.data.tool.DefaultToolRegistry
import dev.chungjungsoo.gptmobile.data.tool.DefaultWebPageFetcher
import dev.chungjungsoo.gptmobile.data.tool.SearchBackend
import dev.chungjungsoo.gptmobile.data.tool.ToolExecutor
import dev.chungjungsoo.gptmobile.data.tool.ToolRegistry
import dev.chungjungsoo.gptmobile.data.tool.WebPageFetcher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ToolModule {
    @Provides
    @Singleton
    fun provideToolRegistry(defaultToolRegistry: DefaultToolRegistry): ToolRegistry = defaultToolRegistry

    @Provides
    @Singleton
    fun provideSearchBackend(bingSearchBackend: BingSearchBackend): SearchBackend = bingSearchBackend

    @Provides
    @Singleton
    fun provideWebPageFetcher(defaultWebPageFetcher: DefaultWebPageFetcher): WebPageFetcher = defaultWebPageFetcher

    @Provides
    @Singleton
    fun provideToolExecutor(defaultToolExecutor: DefaultToolExecutor): ToolExecutor = defaultToolExecutor
}
