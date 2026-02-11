package dev.chungjungsoo.gptmobile.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.tool.DefaultToolExecutor
import dev.chungjungsoo.gptmobile.data.tool.ToolExecutor
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ToolExecutorModule {

    @Provides
    @Singleton
    fun provideToolExecutor(): ToolExecutor = DefaultToolExecutor()
}

