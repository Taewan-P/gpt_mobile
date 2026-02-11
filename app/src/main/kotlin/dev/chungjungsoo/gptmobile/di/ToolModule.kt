package dev.chungjungsoo.gptmobile.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.database.dao.McpServerDao
import dev.chungjungsoo.gptmobile.data.mcp.McpManager
import dev.chungjungsoo.gptmobile.data.tool.BuiltInTool
import dev.chungjungsoo.gptmobile.data.tool.ToolExecutor
import dev.chungjungsoo.gptmobile.data.tool.ToolManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ToolModule {

    @Provides
    @Singleton
    fun provideMcpManager(mcpServerDao: McpServerDao): McpManager = McpManager(mcpServerDao)

    @Provides
    @Singleton
    fun provideBuiltInTools(): Set<BuiltInTool> = emptySet()

    @Provides
    @Singleton
    fun provideToolManager(
        mcpManager: McpManager,
        builtInTools: Set<@JvmSuppressWildcards BuiltInTool>
    ): ToolManager = ToolManager(mcpManager, builtInTools)

    @Provides
    @Singleton
    fun provideToolExecutor(toolManager: ToolManager): ToolExecutor = toolManager
}
