package dev.chungjungsoo.gptmobile.presentation.ui.setting

import dev.chungjungsoo.gptmobile.data.agent.tool.MCP_OAUTH_SCHEME
import dev.chungjungsoo.gptmobile.data.agent.tool.McpClientManager
import dev.chungjungsoo.gptmobile.data.agent.tool.McpOAuthClient
import dev.chungjungsoo.gptmobile.data.agent.tool.McpOAuthClientTest
import dev.chungjungsoo.gptmobile.data.agent.tool.McpOAuthCoordinator
import dev.chungjungsoo.gptmobile.data.agent.tool.McpOAuthCredential
import dev.chungjungsoo.gptmobile.data.database.dao.ToolConnectionDao
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionAuthType
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionType
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.repository.ToolConnectionRepository
import io.ktor.client.engine.cio.CIO
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.decodeFromString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ToolConnectionsViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial refresh clears loading and failed refresh can retry`() = runTest {
        val delegate = FakeToolConnectionDao()
        val dao = ControllableToolConnectionDao(delegate, listFailure = IllegalStateException("Refresh failed"))
        val vault = FakeSecretVault()
        val networkClient = NetworkClient(CIO)
        val manager = McpClientManager(networkClient())
        val repository = ToolConnectionRepository(dao, vault)
        val coordinator = McpOAuthCoordinator(McpOAuthClient(networkClient()), repository, vault, manager)
        val viewModel = ToolConnectionsViewModel(dao, vault, coordinator, manager)

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Refresh failed", viewModel.uiState.value.errorMessage)

        delegate.upsertConnection(testMcpConnection("mcp-1"))
        dao.listFailure = null
        viewModel.refresh()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(listOf("mcp-1"), viewModel.uiState.value.connections.map { it.connectionUid })
        manager.closeAll()
        networkClient().close()
    }

    @Test
    fun `save busy state clears and callback waits for persistence`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val dao = ControllableToolConnectionDao(FakeToolConnectionDao(), upsertGate = gate)
        val vault = FakeSecretVault()
        val networkClient = NetworkClient(CIO)
        val manager = McpClientManager(networkClient())
        val repository = ToolConnectionRepository(dao, vault)
        val coordinator = McpOAuthCoordinator(McpOAuthClient(networkClient()), repository, vault, manager)
        val viewModel = ToolConnectionsViewModel(dao, vault, coordinator, manager)
        var completed = false

        viewModel.saveConnection(
            existing = null,
            provider = ToolConnectionsViewModel.providers.first { it.type == ToolConnectionType.FIRECRAWL },
            name = "Search",
            alias = "search",
            endpointUrl = "",
            authType = ToolConnectionAuthType.BEARER,
            credential = "key",
            oauthClientId = "",
            allowCleartext = false,
            clearCredential = false,
            onSuccess = { completed = true }
        )

        assertTrue(viewModel.uiState.value.isSaving)
        assertFalse(completed)

        gate.complete(Unit)

        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(completed)
        manager.closeAll()
        networkClient().close()
    }

    @Test
    fun `save stays busy through close and rejects a second new save`() = runTest {
        val dao = FakeToolConnectionDao()
        val vault = FakeSecretVault()
        val networkClient = NetworkClient(CIO)
        val manager = McpClientManager(networkClient())
        val repository = ToolConnectionRepository(dao, vault)
        val coordinator = McpOAuthCoordinator(McpOAuthClient(networkClient()), repository, vault, manager)
        val viewModel = ToolConnectionsViewModel(dao, vault, coordinator, manager)
        val provider = ToolConnectionsViewModel.providers.first { it.type == ToolConnectionType.FIRECRAWL }
        var firstCompleted = false
        var secondCompleted = false

        viewModel.saveConnection(
            existing = null,
            provider = provider,
            name = "Search",
            alias = "search",
            endpointUrl = "",
            authType = ToolConnectionAuthType.BEARER,
            credential = "key",
            oauthClientId = "",
            allowCleartext = false,
            clearCredential = false,
            onSuccess = {
                firstCompleted = true
                viewModel.saveConnection(
                    existing = null,
                    provider = provider,
                    name = "Search Two",
                    alias = "search_two",
                    endpointUrl = "",
                    authType = ToolConnectionAuthType.BEARER,
                    credential = "key-two",
                    oauthClientId = "",
                    allowCleartext = false,
                    clearCredential = false,
                    onSuccess = { secondCompleted = true }
                )
            }
        )

        assertTrue(firstCompleted)
        assertFalse(secondCompleted)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(1, dao.listConnections().size)
        manager.closeAll()
        networkClient().close()
    }

    @Test
    fun `save failure clears busy state and does not invoke callback`() = runTest {
        val dao = ControllableToolConnectionDao(
            FakeToolConnectionDao(),
            upsertFailure = IllegalStateException("Save failed")
        )
        val vault = FakeSecretVault()
        val networkClient = NetworkClient(CIO)
        val manager = McpClientManager(networkClient())
        val repository = ToolConnectionRepository(dao, vault)
        val coordinator = McpOAuthCoordinator(McpOAuthClient(networkClient()), repository, vault, manager)
        val viewModel = ToolConnectionsViewModel(dao, vault, coordinator, manager)
        var completed = false

        viewModel.saveConnection(
            existing = null,
            provider = ToolConnectionsViewModel.providers.first { it.type == ToolConnectionType.FIRECRAWL },
            name = "Search",
            alias = "search",
            endpointUrl = "",
            authType = ToolConnectionAuthType.BEARER,
            credential = "key",
            oauthClientId = "",
            allowCleartext = false,
            clearCredential = false,
            onSuccess = { completed = true }
        )

        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(completed)
        assertEquals("Save failed", viewModel.uiState.value.errorMessage)
        manager.closeAll()
        networkClient().close()
    }

    @Test
    fun `provider metadata records the credential transport`() {
        val providers = ToolConnectionsViewModel.providers.associateBy { it.type }

        assertEquals(ToolConnectionAuthType.BEARER, providers.getValue(ToolConnectionType.FIRECRAWL).authType)
        assertEquals(ToolConnectionAuthType.BEARER, providers.getValue(ToolConnectionType.PERPLEXITY).authType)
        assertEquals(ToolConnectionAuthType.API_KEY, providers.getValue(ToolConnectionType.EXA).authType)
        assertEquals(ToolConnectionAuthType.NONE, providers.getValue(ToolConnectionType.MCP).authType)
    }

    @Test
    fun `new connection setup starts with exactly the two high level paths`() {
        val flow = ToolConnectionSetupFlow()

        assertEquals(ToolConnectionSetupStep.CONNECTION_TYPE, flow.step)
        assertEquals(
            listOf(ToolConnectionSetupPath.WEB_SEARCH, ToolConnectionSetupPath.MCP_SERVER),
            ToolConnectionSetupPath.entries
        )
    }

    @Test
    fun `web search setup progresses through provider then relevant details`() {
        val provider = ToolConnectionsViewModel.providers.first { it.type == ToolConnectionType.EXA }

        val selected = ToolConnectionSetupFlow().selectPath(ToolConnectionSetupPath.WEB_SEARCH)
        val providerStep = selected.next()
        val detailsStep = providerStep.selectWebProvider(provider).next()

        assertEquals(ToolConnectionSetupStep.CONNECTION_TYPE, selected.step)
        assertEquals(ToolConnectionSetupPath.WEB_SEARCH, selected.path)
        assertEquals(ToolConnectionSetupStep.WEB_SEARCH_PROVIDER, providerStep.step)
        assertFalse(providerStep.canContinue)
        assertEquals(ToolConnectionSetupStep.DETAILS, detailsStep.step)
        assertEquals(provider, detailsStep.provider)
        assertTrue(detailsStep.isSaveStep)
        assertEquals(ToolConnectionSetupStep.WEB_SEARCH_PROVIDER, detailsStep.back().step)
        assertEquals(ToolConnectionSetupStep.CONNECTION_TYPE, providerStep.back().step)
    }

    @Test
    fun `MCP setup separates connection details from authentication`() {
        val selected = ToolConnectionSetupFlow().selectPath(ToolConnectionSetupPath.MCP_SERVER)
        val detailsStep = selected.next()
        val authenticationStep = detailsStep.next()

        assertEquals(ToolConnectionSetupStep.CONNECTION_TYPE, selected.step)
        assertEquals(ToolConnectionSetupPath.MCP_SERVER, selected.path)
        assertEquals(ToolConnectionSetupStep.DETAILS, detailsStep.step)
        assertEquals(ToolConnectionType.MCP, detailsStep.provider?.type)
        assertEquals(ToolConnectionSetupStep.AUTHENTICATION, authenticationStep.step)
        assertTrue(authenticationStep.isSaveStep)
        assertEquals(ToolConnectionSetupStep.DETAILS, authenticationStep.back().step)
        assertEquals(ToolConnectionSetupStep.CONNECTION_TYPE, detailsStep.back().step)
    }

    @Test
    fun `editing keeps a stored connection type instead of falling back to another provider`() {
        val stored = ToolConnection(
            connectionUid = "search-edit",
            name = "Search",
            alias = "search",
            type = ToolConnectionType.EXA,
            endpointUrl = "https://api.exa.ai/search",
            authType = ToolConnectionAuthType.API_KEY,
            secretRef = null,
            oauthClientId = null
        )

        val known = ToolConnectionSetupFlow.editing(stored)

        assertEquals(ToolConnectionType.EXA, known?.provider?.type)
        assertEquals(ToolConnectionSetupPath.WEB_SEARCH, known?.path)
        assertNull(ToolConnectionSetupFlow.editing(stored.copy(type = "UNSUPPORTED_PROVIDER")))
    }

    @Test
    fun `normalizeAlias keeps aliases lowercase model safe and validates boundaries`() {
        assertEquals("fire_crawl_1", ToolConnectionsViewModel.normalizeAlias(" Fire-Crawl 1 "))

        assertTrue(ToolConnectionsViewModel.isValidAlias("exa_search"))
        assertTrue(ToolConnectionsViewModel.isValidAlias("a1234567890123456789012345678901"))
        assertFalse(ToolConnectionsViewModel.isValidAlias(""))
        assertFalse(ToolConnectionsViewModel.isValidAlias("1exa"))
        assertFalse(ToolConnectionsViewModel.isValidAlias("exa-search"))
        assertFalse(ToolConnectionsViewModel.isValidAlias("a12345678901234567890123456789012"))
    }

    @Test
    fun `normalizeAlias is independent of the device locale`() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))

            assertEquals("internal", ToolConnectionsViewModel.normalizeAlias("INTERNAL"))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `credentialInput preserves blank edit credentials unless explicit clear is selected`() {
        assertNull(ToolConnectionsViewModel.credentialInput("   ", clearCredential = false))
        assertEquals("new-key", ToolConnectionsViewModel.credentialInput(" new-key ", clearCredential = false)?.decodeToString())
        assertNull(ToolConnectionsViewModel.credentialInput("new-key", clearCredential = true))
    }

    @Test
    fun `credential editor distinguishes keep replace clear and missing states`() {
        assertEquals(
            CredentialEditState.KEEP,
            credentialEditState(
                hasExistingCredential = true,
                canPreserveCredential = true,
                credential = "",
                clearCredential = false
            )
        )
        assertEquals(
            CredentialEditState.REPLACE,
            credentialEditState(
                hasExistingCredential = true,
                canPreserveCredential = true,
                credential = "replacement",
                clearCredential = false
            )
        )
        assertEquals(
            CredentialEditState.CLEAR,
            credentialEditState(
                hasExistingCredential = true,
                canPreserveCredential = true,
                credential = "",
                clearCredential = true
            )
        )
        assertEquals(
            CredentialEditState.MISSING,
            credentialEditState(
                hasExistingCredential = true,
                canPreserveCredential = false,
                credential = "",
                clearCredential = false
            )
        )
        assertEquals(
            CredentialEditState.MISSING,
            credentialEditState(
                hasExistingCredential = false,
                canPreserveCredential = false,
                credential = "",
                clearCredential = true
            )
        )
    }

    @Test
    fun `provider change with blank credential clears old credential`() {
        assertTrue(
            ToolConnectionsViewModel.shouldClearCredential(
                existingType = ToolConnectionType.FIRECRAWL,
                providerType = ToolConnectionType.EXA,
                credential = " ",
                clearCredential = false
            )
        )
        assertFalse(
            ToolConnectionsViewModel.shouldClearCredential(
                existingType = ToolConnectionType.FIRECRAWL,
                providerType = ToolConnectionType.EXA,
                credential = "new-key",
                clearCredential = false
            )
        )
        assertFalse(
            ToolConnectionsViewModel.shouldClearCredential(
                existingType = ToolConnectionType.FIRECRAWL,
                providerType = ToolConnectionType.FIRECRAWL,
                credential = " ",
                clearCredential = false
            )
        )
    }

    @Test
    fun `MCP endpoint schemes are case insensitive`() {
        assertTrue(ToolConnectionsViewModel.isValidMcpEndpoint("HTTPS://example.com/mcp", allowCleartext = false))
        assertTrue(ToolConnectionsViewModel.isValidMcpEndpoint("HTTP://example.com/mcp", allowCleartext = true))
        assertFalse(ToolConnectionsViewModel.isValidMcpEndpoint("HTTP://example.com/mcp", allowCleartext = false))
    }

    @Test
    fun `save completion runs after persistence succeeds`() = runTest {
        val dao = FakeToolConnectionDao()
        val vault = FakeSecretVault()
        val networkClient = NetworkClient(CIO)
        val manager = McpClientManager(networkClient())
        val repository = ToolConnectionRepository(dao, vault)
        val coordinator = McpOAuthCoordinator(McpOAuthClient(networkClient()), repository, vault, manager)
        val viewModel = ToolConnectionsViewModel(dao, vault, coordinator, manager)
        var completed = false

        viewModel.saveConnection(
            existing = null,
            provider = ToolConnectionsViewModel.providers.first { it.type == ToolConnectionType.FIRECRAWL },
            name = "Search",
            alias = "search",
            endpointUrl = "",
            authType = ToolConnectionAuthType.BEARER,
            credential = "key",
            oauthClientId = "",
            allowCleartext = false,
            clearCredential = false,
            onSuccess = { completed = true }
        )

        assertTrue(completed)
        assertEquals(1, dao.listConnections().size)
        manager.closeAll()
        networkClient().close()
    }

    @Test
    fun `OAuth busy and browser failure stay with initiating row`() = runBlocking {
        McpOAuthClientTest.OAuthFixtureServer().use { server ->
            val dao = FakeToolConnectionDao(
                connections = mutableMapOf(
                    "mcp-1" to testMcpConnection("mcp-1", server.mcpUrl),
                    "mcp-2" to testMcpConnection("mcp-2", server.mcpUrl)
                )
            )
            val vault = FakeSecretVault()
            val networkClient = NetworkClient(CIO)
            val manager = McpClientManager(networkClient())
            val repository = ToolConnectionRepository(dao, vault)
            val coordinator = McpOAuthCoordinator(McpOAuthClient(networkClient()), repository, vault, manager)
            val viewModel = ToolConnectionsViewModel(dao, vault, coordinator, manager)
            val launch = async(start = CoroutineStart.UNDISPATCHED) { viewModel.oauthLaunches.first() }
            val busy = async(start = CoroutineStart.UNDISPATCHED) {
                viewModel.uiState.first { it.busyConnectionUid != null }
            }

            viewModel.startOAuth("mcp-1")

            assertEquals("mcp-1", busy.await().busyConnectionUid)
            assertFalse(busy.await().busyConnectionUid == "mcp-2")
            launch.await()
            viewModel.failOAuthLaunch()

            assertNull(viewModel.uiState.value.busyConnectionUid)
            assertEquals("mcp-1", viewModel.uiState.value.rowErrorConnectionUid)
            assertTrue(viewModel.uiState.value.rowErrorMessage!!.contains("browser"))
            viewModel.clearRowError("mcp-2")
            assertEquals("mcp-1", viewModel.uiState.value.rowErrorConnectionUid)
            viewModel.clearRowError("mcp-1")
            assertNull(viewModel.uiState.value.rowErrorConnectionUid)
            manager.closeAll()
            networkClient().close()
        }
    }

    @Test
    fun `OAuth callback cancellation stays with initiating row`() = runBlocking {
        McpOAuthClientTest.OAuthFixtureServer().use { server ->
            val dao = FakeToolConnectionDao(
                connections = mutableMapOf("mcp-1" to testMcpConnection("mcp-1", server.mcpUrl))
            )
            val vault = FakeSecretVault()
            val networkClient = NetworkClient(CIO)
            val manager = McpClientManager(networkClient())
            val repository = ToolConnectionRepository(dao, vault)
            val coordinator = McpOAuthCoordinator(McpOAuthClient(networkClient()), repository, vault, manager)
            val viewModel = ToolConnectionsViewModel(dao, vault, coordinator, manager)
            val launch = async(start = CoroutineStart.UNDISPATCHED) { viewModel.oauthLaunches.first() }

            viewModel.startOAuth("mcp-1")
            launch.await()
            viewModel.completeOAuthCallback(null)

            assertEquals("mcp-1", viewModel.uiState.value.rowErrorConnectionUid)
            assertTrue(viewModel.uiState.value.rowErrorMessage!!.contains("canceled"))
            manager.closeAll()
            networkClient().close()
        }
    }

    @Test
    fun `blank credential is cleared when saved metadata changes`() = runTest {
        val dao = FakeToolConnectionDao()
        val vault = FakeSecretVault()
        val networkClient = NetworkClient(CIO)
        val manager = McpClientManager(networkClient())
        val repository = ToolConnectionRepository(dao, vault)
        val coordinator = McpOAuthCoordinator(McpOAuthClient(networkClient()), repository, vault, manager)
        val existing = ToolConnection(
            connectionUid = "search-1",
            name = "Search",
            alias = "search",
            type = ToolConnectionType.FIRECRAWL,
            endpointUrl = "https://api.firecrawl.dev/v2/search",
            authType = ToolConnectionAuthType.BEARER,
            secretRef = "connection_search-1",
            oauthClientId = null
        )
        dao.upsertConnection(existing)
        vault.put("connection_search-1", "old-key".encodeToByteArray())
        val viewModel = ToolConnectionsViewModel(dao, vault, coordinator, manager)

        viewModel.saveConnection(
            existing = existing,
            provider = ToolConnectionsViewModel.providers.first { it.type == ToolConnectionType.PERPLEXITY },
            name = "Search",
            alias = "search",
            endpointUrl = "",
            authType = ToolConnectionAuthType.BEARER,
            credential = " ",
            oauthClientId = "",
            allowCleartext = false,
            clearCredential = false
        )

        assertNull(dao.getConnection("search-1")!!.secretRef)
        assertNull(vault.read("connection_search-1"))
        manager.closeAll()
        networkClient().close()
    }

    @Test
    fun `explicit credential clear removes saved secret without replacement`() = runTest {
        val dao = FakeToolConnectionDao()
        val vault = FakeSecretVault()
        val networkClient = NetworkClient(CIO)
        val manager = McpClientManager(networkClient())
        val repository = ToolConnectionRepository(dao, vault)
        val coordinator = McpOAuthCoordinator(McpOAuthClient(networkClient()), repository, vault, manager)
        val existing = ToolConnection(
            connectionUid = "search-clear",
            name = "Search",
            alias = "search",
            type = ToolConnectionType.FIRECRAWL,
            endpointUrl = "https://api.firecrawl.dev/v2/search",
            authType = ToolConnectionAuthType.BEARER,
            secretRef = "connection_search-clear",
            oauthClientId = null
        )
        dao.upsertConnection(existing)
        vault.put("connection_search-clear", "saved-secret".encodeToByteArray())
        val viewModel = ToolConnectionsViewModel(dao, vault, coordinator, manager)

        viewModel.saveConnection(
            existing = existing,
            provider = ToolConnectionsViewModel.providers.first { it.type == ToolConnectionType.FIRECRAWL },
            name = "Search",
            alias = "search",
            endpointUrl = "",
            authType = ToolConnectionAuthType.BEARER,
            credential = "",
            oauthClientId = "",
            allowCleartext = false,
            clearCredential = true
        )

        assertNull(dao.getConnection("search-clear")!!.secretRef)
        assertNull(vault.read("connection_search-clear"))
        manager.closeAll()
        networkClient().close()
    }

    @Test
    fun `explicit MCP bearer clear removes saved token`() = runTest {
        val dao = FakeToolConnectionDao()
        val vault = FakeSecretVault()
        val networkClient = NetworkClient(CIO)
        val manager = McpClientManager(networkClient())
        val repository = ToolConnectionRepository(dao, vault)
        val coordinator = McpOAuthCoordinator(McpOAuthClient(networkClient()), repository, vault, manager)
        val existing = ToolConnection(
            connectionUid = "mcp-clear",
            name = "MCP",
            alias = "mcp",
            type = ToolConnectionType.MCP,
            endpointUrl = "https://example.com/mcp",
            authType = ToolConnectionAuthType.BEARER,
            secretRef = "connection_mcp-clear",
            oauthClientId = null
        )
        dao.upsertConnection(existing)
        vault.put("connection_mcp-clear", "saved-token".encodeToByteArray())
        val viewModel = ToolConnectionsViewModel(dao, vault, coordinator, manager)

        viewModel.saveConnection(
            existing = existing,
            provider = ToolConnectionsViewModel.providers.first { it.type == ToolConnectionType.MCP },
            name = "MCP",
            alias = "mcp",
            endpointUrl = "https://example.com/mcp",
            authType = ToolConnectionAuthType.BEARER,
            credential = "",
            oauthClientId = "",
            allowCleartext = false,
            clearCredential = true
        )

        assertNull(dao.getConnection("mcp-clear")!!.secretRef)
        assertNull(vault.read("connection_mcp-clear"))
        manager.closeAll()
        networkClient().close()
    }

    @Test
    fun `OAuth launch and callback persist credential and refresh connection state`() = runBlocking {
        McpOAuthClientTest.OAuthFixtureServer().use { server ->
            val dao = FakeToolConnectionDao()
            val vault = FakeSecretVault()
            val repository = ToolConnectionRepository(dao, vault)
            val networkClient = NetworkClient(CIO)
            val manager = McpClientManager(networkClient())
            val coordinator = McpOAuthCoordinator(McpOAuthClient(networkClient()), repository, vault, manager)
            dao.upsertConnection(
                ToolConnection(
                    connectionUid = "connection-1",
                    name = "OAuth MCP",
                    alias = "oauth_mcp",
                    type = ToolConnectionType.MCP,
                    endpointUrl = server.mcpUrl,
                    authType = ToolConnectionAuthType.OAUTH,
                    secretRef = null,
                    oauthClientId = null,
                    allowCleartext = true
                )
            )
            val viewModel = ToolConnectionsViewModel(dao, vault, coordinator, manager)
            val launch = async(start = CoroutineStart.UNDISPATCHED) { viewModel.oauthLaunches.first() }

            viewModel.startOAuth("connection-1")
            val authorizationUri = launch.await()
            val state = URI(authorizationUri).rawQuery.formValues().getValue("state")
            val completion = async(start = CoroutineStart.UNDISPATCHED) {
                viewModel.uiState.first { uiState ->
                    uiState.busyConnectionUid == null &&
                        uiState.connections.any { it.connectionUid == "connection-1" && it.secretRef != null }
                }
            }
            viewModel.completeOAuthCallback(
                "$MCP_OAUTH_SCHEME://oauth/mcp/connection-1?code=auth-code&state=$state"
            )
            viewModel.cancelPendingOAuth()
            completion.await()

            val saved = dao.getConnection("connection-1")!!
            val credentialBytes = vault.read(checkNotNull(saved.secretRef))!!
            val credential = try {
                NetworkClient.json.decodeFromString<McpOAuthCredential>(credentialBytes.decodeToString())
            } finally {
                credentialBytes.fill(0)
            }
            assertEquals("fixture-client", saved.oauthClientId)
            assertEquals("access-1", credential.accessToken)
            assertNull(viewModel.uiState.value.errorMessage)
            assertNull(viewModel.uiState.value.rowErrorConnectionUid)
            assertNull(viewModel.uiState.value.rowErrorMessage)
            assertTrue(viewModel.uiState.value.connections.any { it.connectionUid == "connection-1" && it.secretRef != null })
            manager.closeAll()
            networkClient().close()
        }
    }

    @Test
    fun `OAuth launch is single flight`() = runBlocking {
        McpOAuthClientTest.OAuthFixtureServer().use { server ->
            val dao = FakeToolConnectionDao()
            val vault = FakeSecretVault()
            val repository = ToolConnectionRepository(dao, vault)
            val networkClient = NetworkClient(CIO)
            val manager = McpClientManager(networkClient())
            val coordinator = McpOAuthCoordinator(McpOAuthClient(networkClient()), repository, vault, manager)
            dao.upsertConnection(
                ToolConnection(
                    connectionUid = "connection-1",
                    name = "OAuth MCP",
                    alias = "oauth_mcp",
                    type = ToolConnectionType.MCP,
                    endpointUrl = server.mcpUrl,
                    authType = ToolConnectionAuthType.OAUTH,
                    secretRef = null,
                    oauthClientId = null,
                    allowCleartext = true
                )
            )
            val viewModel = ToolConnectionsViewModel(dao, vault, coordinator, manager)
            val launch = async(start = CoroutineStart.UNDISPATCHED) { viewModel.oauthLaunches.first() }

            viewModel.startOAuth("connection-1")
            viewModel.startOAuth("connection-1")
            launch.await()
            viewModel.uiState.first { it.busyConnectionUid == null }

            assertEquals(1, server.protectedResourceRequests.get())
            manager.closeAll()
            networkClient().close()
        }
    }

    @Test
    fun `OAuth cancel reset permits a second launch`() = runBlocking {
        McpOAuthClientTest.OAuthFixtureServer().use { server ->
            val dao = FakeToolConnectionDao()
            val vault = FakeSecretVault()
            val repository = ToolConnectionRepository(dao, vault)
            val networkClient = NetworkClient(CIO)
            val manager = McpClientManager(networkClient())
            val coordinator = McpOAuthCoordinator(McpOAuthClient(networkClient()), repository, vault, manager)
            dao.upsertConnection(
                ToolConnection(
                    connectionUid = "connection-1",
                    name = "OAuth MCP",
                    alias = "oauth_mcp",
                    type = ToolConnectionType.MCP,
                    endpointUrl = server.mcpUrl,
                    authType = ToolConnectionAuthType.OAUTH,
                    secretRef = null,
                    oauthClientId = null,
                    allowCleartext = true
                )
            )
            val viewModel = ToolConnectionsViewModel(dao, vault, coordinator, manager)
            val firstLaunch = async(start = CoroutineStart.UNDISPATCHED) { viewModel.oauthLaunches.first() }

            viewModel.startOAuth("connection-1")
            firstLaunch.await()
            viewModel.uiState.first { it.busyConnectionUid == null }
            viewModel.cancelPendingOAuth()

            assertEquals("connection-1", viewModel.uiState.value.rowErrorConnectionUid)
            assertTrue(viewModel.uiState.value.rowErrorMessage!!.contains("canceled"))

            viewModel.clearRowError("connection-1")
            val secondLaunch = async(start = CoroutineStart.UNDISPATCHED) { viewModel.oauthLaunches.first() }
            viewModel.startOAuth("connection-1")
            secondLaunch.await()
            viewModel.uiState.first { it.busyConnectionUid == null }

            assertEquals(2, server.protectedResourceRequests.get())
            manager.closeAll()
            networkClient().close()
        }
    }
}

private class ControllableToolConnectionDao(
    private val delegate: FakeToolConnectionDao,
    var listFailure: Throwable? = null,
    private val upsertGate: CompletableDeferred<Unit>? = null,
    private val upsertFailure: Throwable? = null
) : ToolConnectionDao by delegate {
    override suspend fun listConnections(): List<ToolConnection> {
        listFailure?.let { throw it }
        return delegate.listConnections()
    }

    override suspend fun upsertConnection(connection: ToolConnection) {
        upsertGate?.await()
        upsertFailure?.let { throw it }
        delegate.upsertConnection(connection)
    }
}

private fun testMcpConnection(
    connectionUid: String,
    endpointUrl: String = "https://example.com/mcp"
) = ToolConnection(
    connectionUid = connectionUid,
    name = connectionUid,
    alias = connectionUid.replace('-', '_'),
    type = ToolConnectionType.MCP,
    endpointUrl = endpointUrl,
    authType = ToolConnectionAuthType.OAUTH,
    secretRef = null,
    oauthClientId = null,
    allowCleartext = endpointUrl.startsWith("http://")
)

private fun String.formValues(): Map<String, String> = split('&')
    .filter(String::isNotBlank)
    .associate { item ->
        val parts = item.split('=', limit = 2)
        URLDecoder.decode(parts[0], StandardCharsets.UTF_8) to
            URLDecoder.decode(parts.getOrElse(1) { "" }, StandardCharsets.UTF_8)
    }
