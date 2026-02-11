package dev.chungjungsoo.gptmobile.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.chungjungsoo.gptmobile.data.database.entity.McpServerConfig

@Dao
interface McpServerDao {
    @Query("SELECT * FROM mcp_servers ORDER BY id ASC")
    suspend fun getServers(): List<McpServerConfig>

    @Query("SELECT * FROM mcp_servers WHERE enabled = 1 ORDER BY id ASC")
    suspend fun getEnabledServers(): List<McpServerConfig>

    @Query("SELECT * FROM mcp_servers WHERE id = :id")
    suspend fun getServer(id: Int): McpServerConfig?

    @Insert
    suspend fun addServer(server: McpServerConfig): Long

    @Update
    suspend fun editServer(server: McpServerConfig)

    @Delete
    suspend fun deleteServer(server: McpServerConfig)
}
