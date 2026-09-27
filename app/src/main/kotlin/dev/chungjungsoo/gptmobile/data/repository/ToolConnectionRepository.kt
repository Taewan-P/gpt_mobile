package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.database.dao.AgentToolBindingWithConnection
import dev.chungjungsoo.gptmobile.data.database.dao.ToolConnectionDao
import dev.chungjungsoo.gptmobile.data.database.entity.AgentToolBinding
import dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionType
import dev.chungjungsoo.gptmobile.data.security.EndpointSecrets
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject

data class ToolBindingSelection(
    val connectionUid: String,
    val toolName: String
)

class ToolConnectionRepository internal constructor(
    private val toolConnectionDao: ToolConnectionDao,
    private val secretVault: SecretVault,
    private val bindingUidGenerator: (String, String?, String) -> String
) {
    @Inject constructor(
        toolConnectionDao: ToolConnectionDao,
        secretVault: SecretVault
    ) : this(toolConnectionDao, secretVault, ::stableBindingUid)

    suspend fun listConnections(): List<ToolConnection> = toolConnectionDao.listConnections().map { connection ->
        val protected = EndpointSecrets.protect(connection, secretVault)
        if (protected != connection) toolConnectionDao.upsertConnection(protected)
        protected
    }

    suspend fun getAllConnections(): List<ToolConnection> = listConnections()

    suspend fun getConnection(connectionUid: String): ToolConnection? = listConnections().firstOrNull { it.connectionUid == connectionUid }

    suspend fun upsertConnection(
        connection: ToolConnection,
        credential: ByteArray? = null,
        clearCredential: Boolean = false
    ) {
        val protected = EndpointSecrets.protect(connection, secretVault)
        require(!(credential != null && clearCredential)) { "Credential replacement and clear are mutually exclusive." }
        val previous = toolConnectionDao.getConnection(connection.connectionUid)
        val previousSecretRef = previous?.secretRef
        when {
            clearCredential -> {
                toolConnectionDao.upsertConnection(protected.copy(secretRef = null))
                previousSecretRef?.let { secretVault.delete(it) }
            }

            credential == null -> {
                toolConnectionDao.upsertConnection(protected.copy(secretRef = previousSecretRef))
            }

            else -> replaceCredential(protected, credential, previousSecretRef)
        }
    }

    suspend fun deleteConnection(connectionUid: String) {
        val secretRef = toolConnectionDao.getConnection(connectionUid)?.secretRef
        toolConnectionDao.deleteConnectionByUid(connectionUid)
        secretRef?.let { secretVault.delete(it) }
        secretVault.delete(mcpOAuthPendingSecretRef(connectionUid))
        secretVault.delete(EndpointSecrets.reference(connectionUid))
    }

    suspend fun listBindingsByProfile(profileUid: String): List<AgentToolBinding> = toolConnectionDao.listBindingsByProfile(profileUid)

    suspend fun listBindingsWithConnections(profileUid: String): List<AgentToolBindingWithConnection> {
        listConnections()
        return toolConnectionDao.listBindingsWithConnections(profileUid)
    }

    suspend fun replaceWebSearchBinding(profileUid: String, connectionUid: String) {
        requireSearchConnection(connectionUid)
        toolConnectionDao.replaceWebSearchBinding(newBinding(profileUid, connectionUid, WEB_SEARCH_TOOL))
    }

    suspend fun replaceWebSearchBindings(profileUid: String, connectionUids: Set<String>) {
        connectionUids.forEach { requireSearchConnection(it) }
        toolConnectionDao.replaceWebSearchBindings(profileUid, connectionUids.sorted().map { newBinding(profileUid, it, WEB_SEARCH_TOOL) })
    }

    suspend fun removeWebSearchBinding(profileUid: String) {
        toolConnectionDao.removeWebSearchBinding(profileUid)
    }

    suspend fun setReadUrlBinding(profileUid: String, enabled: Boolean) {
        setBuiltInToolBinding(profileUid, BuiltInAgentTool.READ_URL, enabled)
    }

    suspend fun setBuiltInToolBinding(profileUid: String, toolName: String, enabled: Boolean) {
        require(
            toolName in setOf(
                BuiltInAgentTool.READ_URL,
                BuiltInAgentTool.READ_FILE_SLICE,
                BuiltInAgentTool.DEVICE_LOCATION,
                BuiltInAgentTool.CALCULATE_EXPRESSION,
                BuiltInAgentTool.GITHUB,
                BuiltInAgentTool.CURRENT_DATE
            )
        ) { "Unknown built-in tool: $toolName" }
        toolConnectionDao.deleteBuiltInToolBinding(profileUid, toolName)
        if (enabled) {
            toolConnectionDao.insertBinding(newBinding(profileUid, null, toolName))
        }
    }

    suspend fun replaceMcpToolBindings(profileUid: String, selections: List<ToolBindingSelection>) {
        requireMcpSelections(selections)
        toolConnectionDao.replaceMcpBindings(
            profileUid = profileUid,
            bindings = selections.map { newBinding(profileUid, it.connectionUid, it.toolName) }
        )
    }

    private suspend fun replaceCredential(
        connection: ToolConnection,
        credential: ByteArray,
        previousSecretRef: String?
    ) {
        val secretRef = connectionSecretRef(connection.connectionUid)
        var previousBytes: ByteArray? = null
        try {
            previousBytes = previousSecretRef?.let { secretVault.read(it) }
            try {
                storeVerified(secretRef, credential)
                toolConnectionDao.upsertConnection(connection.copy(secretRef = secretRef))
                if (previousSecretRef != null && previousSecretRef != secretRef) {
                    secretVault.delete(previousSecretRef)
                }
            } catch (error: Exception) {
                restoreCredential(secretRef, previousSecretRef, previousBytes)
                throw error
            }
        } finally {
            credential.fill(0)
            previousBytes?.fill(0)
        }
    }

    private suspend fun restoreCredential(
        secretRef: String,
        previousSecretRef: String?,
        previousBytes: ByteArray?
    ) {
        if (previousSecretRef != null && previousBytes != null) {
            secretVault.put(previousSecretRef, previousBytes)
            if (previousSecretRef != secretRef) {
                secretVault.delete(secretRef)
            }
        } else {
            secretVault.delete(secretRef)
        }
    }

    private suspend fun storeVerified(secretRef: String, secret: ByteArray) {
        secretVault.put(secretRef, secret)
        val verified = secretVault.read(secretRef)
        try {
            check(verified != null && verified.contentEquals(secret)) { "Credential verification failed." }
        } finally {
            verified?.fill(0)
        }
    }

    private fun newBinding(
        profileUid: String,
        connectionUid: String?,
        toolName: String
    ) = AgentToolBinding(
        bindingUid = bindingUidGenerator(profileUid, connectionUid, toolName),
        profileUid = profileUid,
        connectionUid = connectionUid,
        toolName = toolName
    )

    private suspend fun requireSearchConnection(connectionUid: String) {
        val connection = toolConnectionDao.getConnection(connectionUid)
        require(connection?.type in WEB_SEARCH_TYPES) { "Web search binding requires an existing search connection." }
    }

    private suspend fun requireMcpSelections(selections: List<ToolBindingSelection>) {
        require(selections.all { it.toolName.isNotBlank() }) { "MCP tool names must be nonblank." }
        val connectionUids = selections.map { it.connectionUid }.distinct()
        if (connectionUids.isEmpty()) return
        val connections = toolConnectionDao.getConnectionsByUids(connectionUids).associateBy { it.connectionUid }
        require(connectionUids.all { connections[it]?.type == ToolConnectionType.MCP }) { "MCP bindings require existing MCP connections." }
    }

    private fun connectionSecretRef(connectionUid: String): String = "connection_$connectionUid"

    private companion object {
        const val WEB_SEARCH_TOOL = "web_search"
        val WEB_SEARCH_TYPES = ToolConnectionType.WEB_SEARCH_TYPES
    }
}

internal fun mcpOAuthPendingSecretRef(connectionUid: String): String = "mcp_oauth_pending_$connectionUid"

private fun stableBindingUid(
    profileUid: String,
    connectionUid: String?,
    toolName: String
): String {
    val key = "$profileUid|${connectionUid ?: "builtin"}|$toolName"
    return UUID.nameUUIDFromBytes(key.toByteArray(StandardCharsets.UTF_8)).toString()
}
