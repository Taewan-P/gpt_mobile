package dev.chungjungsoo.gptmobile.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.chungjungsoo.gptmobile.data.database.entity.AgentToolBinding
import dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionType

data class AgentToolBindingWithConnection(
    val binding: AgentToolBinding,
    val connection: ToolConnection?
)

@Dao
interface ToolConnectionDao {
    @Query("SELECT * FROM tool_connections ORDER BY name, alias, connection_uid")
    suspend fun listConnections(): List<ToolConnection>

    @Query("SELECT * FROM tool_connections WHERE connection_uid = :connectionUid")
    suspend fun getConnection(connectionUid: String): ToolConnection?

    @Query("SELECT * FROM tool_connections WHERE connection_uid IN (:connectionUids)")
    suspend fun getConnectionsByUids(connectionUids: List<String>): List<ToolConnection>

    @Upsert
    suspend fun upsertConnection(connection: ToolConnection)

    @Query("DELETE FROM tool_connections WHERE connection_uid = :connectionUid")
    suspend fun deleteConnectionByUid(connectionUid: String)

    @Query("SELECT * FROM agent_tool_bindings WHERE profile_uid = :profileUid ORDER BY tool_name, connection_uid, binding_uid")
    suspend fun listBindingsByProfile(profileUid: String): List<AgentToolBinding>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBinding(binding: AgentToolBinding)

    @Query(
        """
        DELETE FROM agent_tool_bindings
        WHERE profile_uid = :profileUid
            AND tool_name = :toolName
            AND connection_uid IN (
                SELECT connection_uid FROM tool_connections
                WHERE type IN (:connectionTypes)
            )
        """
    )
    suspend fun deleteConnectionToolBindingsForTypes(
        profileUid: String,
        toolName: String,
        connectionTypes: List<String>
    )

    @Query(
        """
        DELETE FROM agent_tool_bindings
        WHERE profile_uid = :profileUid
            AND tool_name = :toolName
            AND connection_uid IS NULL
        """
    )
    suspend fun deleteBuiltInToolBinding(profileUid: String, toolName: String)

    @Query(
        """
        DELETE FROM agent_tool_bindings
        WHERE profile_uid = :profileUid
            AND connection_uid IN (
                SELECT connection_uid FROM tool_connections
                WHERE type = :connectionType
            )
        """
    )
    suspend fun deleteConnectionBindingsForType(profileUid: String, connectionType: String)

    @Transaction
    suspend fun replaceWebSearchBinding(binding: AgentToolBinding) {
        require(binding.toolName == WEB_SEARCH_TOOL)
        deleteConnectionToolBindingsForTypes(binding.profileUid, WEB_SEARCH_TOOL, WEB_SEARCH_TYPES)
        insertBinding(binding)
    }

    @Transaction
    suspend fun removeWebSearchBinding(profileUid: String) {
        deleteConnectionToolBindingsForTypes(profileUid, WEB_SEARCH_TOOL, WEB_SEARCH_TYPES)
    }

    @Transaction
    suspend fun replaceReadUrlBinding(binding: AgentToolBinding) {
        require(binding.toolName == BuiltInAgentTool.READ_URL)
        deleteBuiltInToolBinding(binding.profileUid, BuiltInAgentTool.READ_URL)
        insertBinding(binding)
    }

    @Transaction
    suspend fun removeReadUrlBinding(profileUid: String) {
        deleteBuiltInToolBinding(profileUid, BuiltInAgentTool.READ_URL)
    }

    @Transaction
    suspend fun replaceMcpBindings(
        profileUid: String,
        bindings: List<AgentToolBinding>
    ) {
        deleteConnectionBindingsForType(profileUid, ToolConnectionType.MCP)
        bindings.forEach { insertBinding(it) }
    }

    @Transaction
    suspend fun listBindingsWithConnections(profileUid: String): List<AgentToolBindingWithConnection> {
        val bindings = listBindingsByProfile(profileUid)
        val connections = bindings
            .mapNotNull { it.connectionUid }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.let { getConnectionsByUids(it) }
            ?.associateBy { it.connectionUid }
            .orEmpty()
        return bindings.map { binding ->
            AgentToolBindingWithConnection(binding, binding.connectionUid?.let(connections::get))
        }
    }

    companion object {
        private const val WEB_SEARCH_TOOL = "web_search"
        private val WEB_SEARCH_TYPES = listOf(ToolConnectionType.FIRECRAWL, ToolConnectionType.PERPLEXITY, ToolConnectionType.EXA)
    }
}
