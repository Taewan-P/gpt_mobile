package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.database.entity.McpServerConfig
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting

interface SettingRepository {
    suspend fun fetchPlatforms(): List<Platform>
    suspend fun fetchPlatformV2s(): List<PlatformV2>
    suspend fun fetchThemes(): ThemeSetting
    suspend fun migrateToPlatformV2()
    suspend fun updatePlatforms(platforms: List<Platform>)
    suspend fun updateThemes(themeSetting: ThemeSetting)

    // PlatformV2 CRUD operations
    suspend fun addPlatformV2(platform: PlatformV2)
    suspend fun updatePlatformV2(platform: PlatformV2)
    suspend fun deletePlatformV2(platform: PlatformV2)
    suspend fun getPlatformV2ById(id: Int): PlatformV2?

    // MCP server CRUD operations
    suspend fun fetchMcpServers(): List<McpServerConfig>
    suspend fun fetchEnabledMcpServers(): List<McpServerConfig>
    suspend fun getMcpServerById(id: Int): McpServerConfig?
    suspend fun addMcpServer(server: McpServerConfig): Long
    suspend fun updateMcpServer(server: McpServerConfig)
    suspend fun deleteMcpServer(server: McpServerConfig)
}
