package dev.chungjungsoo.gptmobile.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.database.dao.McpServerDao
import dev.chungjungsoo.gptmobile.data.mcp.McpManager
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.tool.BuiltInTool
import dev.chungjungsoo.gptmobile.data.tool.ToolExecutor
import dev.chungjungsoo.gptmobile.data.tool.ToolManager
import dev.chungjungsoo.gptmobile.data.tool.builtin.WebFetchTool
import dev.chungjungsoo.gptmobile.data.tool.builtin.WebSearchTool
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ToolModule {

    @Provides
    @Singleton
    fun provideMcpManager(
        mcpServerDao: McpServerDao,
        @ApplicationContext context: Context
    ): McpManager = McpManager(mcpServerDao, context)

    @Provides
    @Singleton
    fun provideBuiltInTools(networkClient: NetworkClient): Set<BuiltInTool> = setOf(
        WebSearchTool(networkClient),
        WebFetchTool(networkClient)
    )

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
