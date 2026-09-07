package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.database.dao.AgentToolBindingWithConnection
import dev.chungjungsoo.gptmobile.data.database.dao.ToolConnectionDao
import dev.chungjungsoo.gptmobile.data.database.entity.AgentToolBinding
import dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionAuthType
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionType
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ToolConnectionRepositoryTest {
    @Test
    fun `connection CRUD stores only verified vault reference and lists deterministically`() = runBlocking {
        val dao = FakeToolConnectionDao()
        val vault = ConnectionFakeSecretVault()
        val repository = ToolConnectionRepository(dao, vault)
        val credential = "alpha-secret".encodeToByteArray()

        repository.upsertConnection(testConnection("beta", name = "Beta"))
        repository.upsertConnection(testConnection("alpha", name = "Alpha"), credential = credential)

        assertEquals(listOf("alpha", "beta"), repository.listConnections().map { it.connectionUid })
        assertEquals("connection_alpha", repository.getConnection("alpha")?.secretRef)
        assertEquals("alpha-secret", vault.values.getValue("connection_alpha").decodeToString())
        assertTrue(dao.connections.getValue("alpha").secretRef == "connection_alpha")
        assertTrue(credential.all { it == 0.toByte() })
        assertTrue(vault.lastPutBytes?.all { it == 0.toByte() } == true)
        assertTrue(vault.lastReadBytes?.all { it == 0.toByte() } == true)

        repository.deleteConnection("alpha")

        assertNull(repository.getConnection("alpha"))
        assertNull(vault.values["connection_alpha"])
    }

    @Test
    fun `metadata update without credential preserves existing credential reference`() = runBlocking {
        val dao = FakeToolConnectionDao()
        val vault = ConnectionFakeSecretVault()
        val repository = ToolConnectionRepository(dao, vault)
        repository.upsertConnection(testConnection("alpha"), credential = "old-secret".encodeToByteArray())

        repository.upsertConnection(testConnection("alpha", name = "Renamed", secretRef = null))

        assertEquals("Renamed", dao.connections.getValue("alpha").name)
        assertEquals("connection_alpha", dao.connections.getValue("alpha").secretRef)
        assertEquals("old-secret", vault.values.getValue("connection_alpha").decodeToString())
    }

    @Test
    fun `explicit credential clear updates database before deleting the vault record`() = runBlocking {
        val events = mutableListOf<String>()
        val dao = FakeToolConnectionDao(events = events)
        val vault = ConnectionFakeSecretVault(events = events)
        val repository = ToolConnectionRepository(dao, vault)
        repository.upsertConnection(testConnection("alpha"), credential = "old-secret".encodeToByteArray())
        events.clear()

        repository.upsertConnection(testConnection("alpha"), clearCredential = true)

        assertNull(dao.connections.getValue("alpha").secretRef)
        assertNull(vault.values["connection_alpha"])
        assertEquals(listOf("dao.upsert:alpha:null", "vault.delete:connection_alpha"), events)
    }

    @Test
    fun `failed credential replacement restores previous vault bytes and leaves input wiped`() = runBlocking {
        val dao = FakeToolConnectionDao()
        val vault = ConnectionFakeSecretVault()
        val repository = ToolConnectionRepository(dao, vault)
        repository.upsertConnection(testConnection("alpha"), credential = "old-secret".encodeToByteArray())
        val replacement = "new-secret".encodeToByteArray()
        dao.failUpserts = true

        try {
            repository.upsertConnection(testConnection("alpha", name = "Renamed"), credential = replacement)
            fail("Expected the database write to fail")
        } catch (_: IllegalStateException) {
            // Expected.
        }

        assertEquals("Alpha", dao.connections.getValue("alpha").name)
        assertEquals("connection_alpha", dao.connections.getValue("alpha").secretRef)
        assertEquals("old-secret", vault.values.getValue("connection_alpha").decodeToString())
        assertTrue(replacement.all { it == 0.toByte() })
    }

    @Test
    fun `failed vault verification restores old secret before database write`() = runBlocking {
        val dao = FakeToolConnectionDao()
        val vault = ConnectionFakeSecretVault()
        val repository = ToolConnectionRepository(dao, vault)
        repository.upsertConnection(testConnection("alpha", secretRef = "legacy_alpha"), credential = "old-secret".encodeToByteArray())
        dao.connections["alpha"] = dao.connections.getValue("alpha").copy(secretRef = "legacy_alpha")
        vault.values["legacy_alpha"] = "old-secret".encodeToByteArray()
        val replacement = "new-secret".encodeToByteArray()
        vault.failReadsFor += "connection_alpha"

        try {
            repository.upsertConnection(testConnection("alpha", name = "Renamed"), credential = replacement)
            fail("Expected credential verification to fail")
        } catch (_: IllegalStateException) {
            // Expected.
        }

        assertEquals("legacy_alpha", dao.connections.getValue("alpha").secretRef)
        assertEquals("old-secret", vault.values.getValue("legacy_alpha").decodeToString())
        assertNull(vault.values["connection_alpha"])
        assertTrue(replacement.all { it == 0.toByte() })
    }

    @Test
    fun `new connection verification failure deletes orphan secret and wipes input`() = runBlocking {
        val dao = FakeToolConnectionDao()
        val vault = ConnectionFakeSecretVault()
        val repository = ToolConnectionRepository(dao, vault)
        val credential = "new-secret".encodeToByteArray()
        vault.failReadsFor += "connection_alpha"

        try {
            repository.upsertConnection(testConnection("alpha"), credential = credential)
            fail("Expected credential verification to fail")
        } catch (_: IllegalStateException) {
            // Expected.
        }

        assertNull(dao.connections["alpha"])
        assertNull(vault.values["connection_alpha"])
        assertTrue(credential.all { it == 0.toByte() })
    }

    @Test
    fun `successful stable ref migration deletes old secret after database write`() = runBlocking {
        val events = mutableListOf<String>()
        val dao = FakeToolConnectionDao(events = events)
        val vault = ConnectionFakeSecretVault(events = events)
        val repository = ToolConnectionRepository(dao, vault)
        dao.connections["alpha"] = testConnection("alpha", secretRef = "legacy_alpha")
        vault.values["legacy_alpha"] = "old-secret".encodeToByteArray()
        events.clear()

        repository.upsertConnection(testConnection("alpha"), credential = "new-secret".encodeToByteArray())

        assertEquals("connection_alpha", dao.connections.getValue("alpha").secretRef)
        assertEquals("new-secret", vault.values.getValue("connection_alpha").decodeToString())
        assertNull(vault.values["legacy_alpha"])
        assertEquals(
            listOf("vault.read:legacy_alpha", "vault.put:connection_alpha", "vault.read:connection_alpha", "dao.upsert:alpha:connection_alpha", "vault.delete:legacy_alpha"),
            events
        )
    }

    @Test
    fun `deleting a connection removes database row before vault cleanup`() = runBlocking {
        val events = mutableListOf<String>()
        val dao = FakeToolConnectionDao(events = events)
        val vault = ConnectionFakeSecretVault(events = events)
        val repository = ToolConnectionRepository(dao, vault)
        repository.upsertConnection(testConnection("alpha"), credential = "old-secret".encodeToByteArray())
        events.clear()

        repository.deleteConnection("alpha")

        assertNull(dao.connections["alpha"])
        assertNull(vault.values["connection_alpha"])
        assertEquals(
            listOf("dao.delete:alpha", "vault.delete:connection_alpha", "vault.delete:mcp_oauth_pending_alpha"),
            events
        )
    }

    @Test
    fun `web search replacement keeps exactly one binding for the profile`() = runBlocking {
        val dao = FakeToolConnectionDao()
        val repository = ToolConnectionRepository(dao, ConnectionFakeSecretVault())
        repository.upsertConnection(searchConnection("search-a"))
        repository.upsertConnection(searchConnection("search-b"))

        repository.replaceWebSearchBinding("profile-1", "search-a")
        repository.replaceWebSearchBinding("profile-1", "search-b")

        val bindings = repository.listBindingsByProfile("profile-1")
        assertEquals(1, bindings.size)
        assertEquals("web_search", bindings.single().toolName)
        assertEquals("search-b", bindings.single().connectionUid)
    }

    @Test
    fun `read url toggle uses builtin null connection and preserves web search`() = runBlocking {
        val dao = FakeToolConnectionDao()
        val repository = ToolConnectionRepository(dao, ConnectionFakeSecretVault())
        repository.upsertConnection(searchConnection("search"))
        repository.replaceWebSearchBinding("profile-1", "search")

        repository.setReadUrlBinding("profile-1", enabled = true)
        assertEquals(
            listOf("read_url:null", "web_search:search"),
            repository.listBindingsByProfile("profile-1").map { "${it.toolName}:${it.connectionUid}" }
        )

        repository.setReadUrlBinding("profile-1", enabled = false)
        assertEquals(listOf("web_search:search"), repository.listBindingsByProfile("profile-1").map { "${it.toolName}:${it.connectionUid}" })
    }

    @Test
    fun `MCP replacement preserves built in bindings and does not auto bind unselected tools`() = runBlocking {
        val dao = FakeToolConnectionDao()
        val repository = ToolConnectionRepository(dao, ConnectionFakeSecretVault())
        repository.upsertConnection(testConnection("mcp"))
        repository.upsertConnection(searchConnection("search"))
        repository.replaceWebSearchBinding("profile-1", "search")
        repository.setReadUrlBinding("profile-1", enabled = true)
        repository.replaceMcpToolBindings("profile-1", listOf(ToolBindingSelection("mcp", "old_tool")))

        repository.replaceMcpToolBindings("profile-1", listOf(ToolBindingSelection("mcp", "selected_tool")))

        assertEquals(
            listOf("read_url:null", "selected_tool:mcp", "web_search:search"),
            repository.listBindingsByProfile("profile-1").map { "${it.toolName}:${it.connectionUid}" }
        )
    }

    @Test
    fun `colliding MCP tool names survive web search and read url replacement`() = runBlocking {
        val dao = FakeToolConnectionDao()
        val repository = ToolConnectionRepository(dao, ConnectionFakeSecretVault())
        repository.upsertConnection(searchConnection("search"))
        repository.upsertConnection(testConnection("mcp"))
        repository.replaceMcpToolBindings(
            "profile-1",
            listOf(
                ToolBindingSelection("mcp", "web_search"),
                ToolBindingSelection("mcp", BuiltInAgentTool.READ_URL)
            )
        )

        repository.replaceWebSearchBinding("profile-1", "search")
        repository.setReadUrlBinding("profile-1", enabled = true)
        repository.setReadUrlBinding("profile-1", enabled = false)

        assertEquals(
            listOf("read_url:mcp", "web_search:mcp", "web_search:search"),
            repository.listBindingsByProfile("profile-1").map { "${it.toolName}:${it.connectionUid}" }
        )
    }

    @Test
    fun `MCP replacement preserves colliding search and read url bindings`() = runBlocking {
        val dao = FakeToolConnectionDao()
        val repository = ToolConnectionRepository(dao, ConnectionFakeSecretVault())
        repository.upsertConnection(searchConnection("search"))
        repository.upsertConnection(testConnection("mcp"))
        repository.replaceWebSearchBinding("profile-1", "search")
        repository.setReadUrlBinding("profile-1", enabled = true)

        repository.replaceMcpToolBindings(
            "profile-1",
            listOf(
                ToolBindingSelection("mcp", "web_search"),
                ToolBindingSelection("mcp", BuiltInAgentTool.READ_URL)
            )
        )

        assertEquals(
            listOf("read_url:null", "read_url:mcp", "web_search:mcp", "web_search:search"),
            repository.listBindingsByProfile("profile-1").map { "${it.toolName}:${it.connectionUid}" }
        )
    }

    @Test
    fun `web search binding rejects non search connection`() = runBlocking {
        val dao = FakeToolConnectionDao()
        val repository = ToolConnectionRepository(dao, ConnectionFakeSecretVault())
        repository.upsertConnection(testConnection("mcp"))

        try {
            repository.replaceWebSearchBinding("profile-1", "mcp")
            fail("Expected wrong connection type to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        assertEquals(emptyList<AgentToolBinding>(), repository.listBindingsByProfile("profile-1"))
    }

    @Test
    fun `MCP bindings require existing MCP connection and nonblank tool names`() = runBlocking {
        val dao = FakeToolConnectionDao()
        val repository = ToolConnectionRepository(dao, ConnectionFakeSecretVault())
        repository.upsertConnection(searchConnection("search"))

        try {
            repository.replaceMcpToolBindings("profile-1", listOf(ToolBindingSelection("search", "tool")))
            fail("Expected wrong connection type to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        try {
            repository.replaceMcpToolBindings("profile-1", listOf(ToolBindingSelection("missing", "tool")))
            fail("Expected missing connection to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        try {
            repository.replaceMcpToolBindings("profile-1", listOf(ToolBindingSelection("search", " ")))
            fail("Expected blank tool name to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        assertEquals(emptyList<AgentToolBinding>(), repository.listBindingsByProfile("profile-1"))
    }

    @Test
    fun `remove web search only removes search connection binding`() = runBlocking {
        val dao = FakeToolConnectionDao()
        val repository = ToolConnectionRepository(dao, ConnectionFakeSecretVault())
        repository.upsertConnection(searchConnection("search"))
        repository.upsertConnection(testConnection("mcp"))
        repository.replaceWebSearchBinding("profile-1", "search")
        repository.replaceMcpToolBindings("profile-1", listOf(ToolBindingSelection("mcp", "web_search")))

        repository.removeWebSearchBinding("profile-1")

        assertEquals(listOf("web_search:mcp"), repository.listBindingsByProfile("profile-1").map { "${it.toolName}:${it.connectionUid}" })
    }

    @Test
    fun `profile bindings can be listed with connection metadata`() = runBlocking {
        val dao = FakeToolConnectionDao()
        val repository = ToolConnectionRepository(dao, ConnectionFakeSecretVault())
        repository.upsertConnection(testConnection("mcp", name = "MCP Server"))
        repository.replaceMcpToolBindings("profile-1", listOf(ToolBindingSelection("mcp", "selected_tool")))
        repository.setReadUrlBinding("profile-1", enabled = true)

        val bindings = repository.listBindingsWithConnections("profile-1")

        assertEquals(listOf(null, "MCP Server"), bindings.map { it.connection?.name })
    }

    private fun testConnection(
        uid: String,
        name: String = "Alpha",
        secretRef: String? = null
    ) = ToolConnection(
        connectionUid = uid,
        name = name,
        alias = uid,
        type = ToolConnectionType.MCP,
        endpointUrl = "https://$uid.example/mcp",
        authType = ToolConnectionAuthType.BEARER,
        secretRef = secretRef,
        oauthClientId = null
    )

    private fun searchConnection(
        uid: String,
        name: String = "Alpha"
    ) = testConnection(uid, name).copy(type = ToolConnectionType.FIRECRAWL)
}

private class ConnectionFakeSecretVault(
    private val events: MutableList<String> = mutableListOf()
) : SecretVault {
    val values = mutableMapOf<String, ByteArray>()
    val failReadsFor = mutableSetOf<String>()
    var lastPutBytes: ByteArray? = null
    var lastReadBytes: ByteArray? = null

    override suspend fun put(secretRef: String, secret: ByteArray) {
        lastPutBytes = secret
        values[secretRef] = secret.copyOf()
        events += "vault.put:$secretRef"
    }

    override suspend fun read(secretRef: String): ByteArray? {
        if (secretRef in failReadsFor) error("Vault read failed.")
        return values[secretRef]?.copyOf()?.also {
            lastReadBytes = it
            events += "vault.read:$secretRef"
        }
    }

    override suspend fun delete(secretRef: String) {
        values.remove(secretRef)
        events += "vault.delete:$secretRef"
    }
}

private class FakeToolConnectionDao(
    val connections: MutableMap<String, ToolConnection> = mutableMapOf(),
    private val events: MutableList<String> = mutableListOf()
) : ToolConnectionDao {
    val bindings = mutableMapOf<String, AgentToolBinding>()
    var failUpserts = false

    override suspend fun listConnections(): List<ToolConnection> = connections.values.sortedWith(
        compareBy<ToolConnection> { it.name }
            .thenBy { it.alias }
            .thenBy { it.connectionUid }
    )

    override suspend fun getConnection(connectionUid: String): ToolConnection? = connections[connectionUid]

    override suspend fun getConnectionsByUids(connectionUids: List<String>): List<ToolConnection> = connectionUids.mapNotNull(connections::get)

    override suspend fun upsertConnection(connection: ToolConnection) {
        check(!failUpserts) { "Database write failed." }
        connections[connection.connectionUid] = connection
        events += "dao.upsert:${connection.connectionUid}:${connection.secretRef}"
    }

    override suspend fun deleteConnectionByUid(connectionUid: String) {
        connections.remove(connectionUid)
        bindings.values.removeAll { it.connectionUid == connectionUid }
        events += "dao.delete:$connectionUid"
    }

    override suspend fun listBindingsByProfile(profileUid: String): List<AgentToolBinding> = bindings.values
        .filter { it.profileUid == profileUid }
        .sortedWith(compareBy<AgentToolBinding> { it.toolName }.thenBy { it.connectionUid ?: "" }.thenBy { it.bindingUid })

    override suspend fun insertBinding(binding: AgentToolBinding) {
        bindings[binding.bindingUid] = binding
    }

    override suspend fun deleteConnectionToolBindingsForTypes(
        profileUid: String,
        toolName: String,
        connectionTypes: List<String>
    ) {
        bindings.values.removeAll { binding ->
            binding.profileUid == profileUid &&
                binding.toolName == toolName &&
                binding.connectionUid?.let { connections[it]?.type in connectionTypes } == true
        }
    }

    override suspend fun deleteBuiltInToolBinding(profileUid: String, toolName: String) {
        bindings.values.removeAll { it.profileUid == profileUid && it.toolName == toolName && it.connectionUid == null }
    }

    override suspend fun deleteConnectionBindingsForType(profileUid: String, connectionType: String) {
        bindings.values.removeAll { binding ->
            binding.profileUid == profileUid &&
                binding.connectionUid?.let { connections[it]?.type == connectionType } == true
        }
    }

    override suspend fun listBindingsWithConnections(profileUid: String): List<AgentToolBindingWithConnection> = listBindingsByProfile(profileUid).map { binding ->
        AgentToolBindingWithConnection(binding, binding.connectionUid?.let(connections::get))
    }
}
