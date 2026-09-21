package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.lifecycle.SavedStateHandle
import dev.chungjungsoo.gptmobile.data.agent.tool.AgentToolResolver
import dev.chungjungsoo.gptmobile.data.agent.tool.McpClientManager
import dev.chungjungsoo.gptmobile.data.agent.tool.McpOAuthClient
import dev.chungjungsoo.gptmobile.data.agent.tool.McpOAuthCoordinator
import dev.chungjungsoo.gptmobile.data.catalog.CatalogDefaultConfig
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.catalog.SocVariant
import dev.chungjungsoo.gptmobile.data.database.dao.AgentToolBindingWithConnection
import dev.chungjungsoo.gptmobile.data.database.dao.ToolConnectionDao
import dev.chungjungsoo.gptmobile.data.database.entity.AgentToolBinding
import dev.chungjungsoo.gptmobile.data.database.entity.LocalModel
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionAuthType
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionType
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStatus
import dev.chungjungsoo.gptmobile.data.localruntime.AcceleratorUnavailableReason
import dev.chungjungsoo.gptmobile.data.localruntime.LocalAccelerators
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.repository.FakeLocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepository
import dev.chungjungsoo.gptmobile.data.repository.SecretMigrationError
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.repository.ToolConnectionRepository
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlatformSettingViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `platform load transitions from Loading to Loaded`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = testViewModel(
            dao = FakeToolConnectionDao(),
            settingRepository = FakeSettingRepository()
        )

        assertEquals(PlatformLoadState.Loading, viewModel.platformLoadState.value)
        runCurrent()

        assertEquals(PlatformLoadState.Loaded, viewModel.platformLoadState.value)
        assertEquals("profile-1", viewModel.platformState.value?.uid)
    }

    @Test
    fun `missing platform transitions from Loading to NotFound`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = testViewModel(
            dao = FakeToolConnectionDao(),
            settingRepository = FakeSettingRepository(fetchPlatformsResult = emptyList())
        )

        assertEquals(PlatformLoadState.Loading, viewModel.platformLoadState.value)
        runCurrent()

        assertEquals(PlatformLoadState.NotFound, viewModel.platformLoadState.value)
        assertNull(viewModel.platformState.value)
    }

    @Test
    fun `repository failure transitions from Loading to Error`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = testViewModel(
            dao = FakeToolConnectionDao(),
            settingRepository = FakeSettingRepository(fetchFailure = IllegalStateException("Load failed"))
        )

        assertEquals(PlatformLoadState.Loading, viewModel.platformLoadState.value)
        runCurrent()

        val state = viewModel.platformLoadState.value
        assertTrue(state is PlatformLoadState.Error)
        assertEquals("Load failed", (state as PlatformLoadState.Error).message)
    }

    @Test
    fun `retry returns from Error to Loaded`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeSettingRepository(fetchFailure = IllegalStateException("Load failed"))
        val viewModel = testViewModel(
            dao = FakeToolConnectionDao(),
            settingRepository = repository
        )
        runCurrent()
        assertTrue(viewModel.platformLoadState.value is PlatformLoadState.Error)

        repository.fetchFailure = null
        viewModel.retryLoadPlatform()

        assertEquals(PlatformLoadState.Loading, viewModel.platformLoadState.value)
        runCurrent()
        assertEquals(PlatformLoadState.Loaded, viewModel.platformLoadState.value)
    }

    @Test
    fun `selectSearchBackend none removes web search binding and closes dialog`() = runTest {
        val dao = FakeToolConnectionDao(
            connections = mutableMapOf("search-1" to testConnection("search-1")),
            bindings = mutableListOf(testBinding("profile-1", "search-1"))
        )
        val viewModel = testViewModel(dao)

        viewModel.loadToolBindings()
        viewModel.openSearchBackendDialog()
        viewModel.selectSearchBackend(null)

        assertNull(viewModel.toolBindingState.value.selectedSearchConnectionUid)
        assertFalse(viewModel.toolBindingState.value.isSearchBackendDialogOpen)
        assertNull(viewModel.toolBindingState.value.errorMessage)
        assertEquals(emptyList<AgentToolBinding>(), dao.listBindingsByProfile("profile-1"))
    }

    @Test
    fun `loadToolBindings refreshes selected web search binding from repository`() = runTest {
        val dao = FakeToolConnectionDao(
            connections = mutableMapOf(
                "search-1" to testConnection("search-1"),
                "search-2" to testConnection("search-2")
            ),
            bindings = mutableListOf(testBinding("profile-1", "search-1"))
        )
        val viewModel = testViewModel(dao)

        viewModel.loadToolBindings()
        dao.bindings.clear()
        dao.bindings += testBinding("profile-1", "search-2")
        viewModel.loadToolBindings()

        assertEquals("search-2", viewModel.toolBindingState.value.selectedSearchConnectionUid)
    }

    @Test
    fun `saving MCP tool selection binds only selected server tool`() = runTest {
        val dao = FakeToolConnectionDao(
            connections = mutableMapOf("mcp-1" to testConnection("mcp-1", ToolConnectionType.MCP))
        )
        val viewModel = testViewModel(dao)

        viewModel.loadToolBindings()
        viewModel.toggleMcpTool("mcp-1", "echo")
        viewModel.saveMcpTools()

        assertEquals(
            listOf("mcp-1:echo"),
            dao.listBindingsByProfile("profile-1").map { "${it.connectionUid}:${it.toolName}" }
        )
    }

    @Test
    fun `MCP tools named like builtins do not enable builtin bindings`() = runTest {
        val dao = FakeToolConnectionDao(
            connections = mutableMapOf("mcp-1" to testConnection("mcp-1", ToolConnectionType.MCP)),
            bindings = mutableListOf(
                AgentToolBinding("mcp-web", "profile-1", "mcp-1", "web_search"),
                AgentToolBinding("mcp-read", "profile-1", "mcp-1", "read_url")
            )
        )
        val viewModel = testViewModel(dao)

        viewModel.loadToolBindings()

        assertNull(viewModel.toolBindingState.value.selectedSearchConnectionUid)
        assertFalse(viewModel.toolBindingState.value.readUrlEnabled)
        assertEquals(setOf("web_search", "read_url"), viewModel.toolBindingState.value.selectedMcpTools.map { it.toolName }.toSet())
    }

    @Test
    fun `closing MCP tools dialog cancels discovery loading state`() = runTest {
        val dao = FakeToolConnectionDao(
            connections = mutableMapOf("mcp-1" to testConnection("mcp-1", ToolConnectionType.MCP))
        )
        val viewModel = testViewModel(dao)

        viewModel.loadToolBindings()
        viewModel.openMcpToolsDialog()
        viewModel.closeMcpToolsDialog()

        assertFalse(viewModel.toolBindingState.value.isMcpToolsDialogOpen)
        assertFalse(viewModel.toolBindingState.value.isMcpToolsLoading)
    }

    @Test
    fun `closing MCP tools dialog clears discovery error`() = runTest {
        val dao = FakeToolConnectionDao(
            connections = mutableMapOf(
                "mcp-1" to testConnection("mcp-1", ToolConnectionType.MCP).copy(endpointUrl = null)
            )
        )
        val viewModel = testViewModel(dao)

        viewModel.loadToolBindings()
        viewModel.openMcpToolsDialog()

        assertTrue(viewModel.toolBindingState.value.errorMessage != null)

        viewModel.closeMcpToolsDialog()

        assertFalse(viewModel.toolBindingState.value.isMcpToolsDialogOpen)
        assertFalse(viewModel.toolBindingState.value.isMcpToolsLoading)
        assertNull(viewModel.toolBindingState.value.errorMessage)
    }

    @Test
    fun `updating top-k persists to the local profile`() = runTest {
        val settings = FakeSettingRepository(localPlatform())
        val viewModel = localSettingsViewModel(settings)

        viewModel.updateTopK(80)

        assertEquals(80, viewModel.platformState.value?.topK)
        assertEquals(80, settings.updatedPlatforms.single().topK)
        assertFalse(viewModel.dialogState.value.isTopKDialogOpen)
    }

    @Test
    fun `updating max tokens persists to the local profile`() = runTest {
        val settings = FakeSettingRepository(localPlatform())
        val viewModel = localSettingsViewModel(settings)

        viewModel.updateMaxTokens(4096)

        assertEquals(4096, viewModel.platformState.value?.maxTokens)
        assertEquals(4096, settings.updatedPlatforms.single().maxTokens)
        assertFalse(viewModel.dialogState.value.isMaxTokensDialogOpen)
    }

    @Test
    fun `updating accelerator persists to the local profile`() = runTest {
        val settings = FakeSettingRepository(localPlatform())
        val viewModel = localSettingsViewModel(settings)

        viewModel.updateAccelerator(LocalAccelerators.CPU)

        assertEquals(LocalAccelerators.CPU, viewModel.platformState.value?.accelerator)
        assertEquals(LocalAccelerators.CPU, settings.updatedPlatforms.single().accelerator)
        assertFalse(viewModel.dialogState.value.isAcceleratorDialogOpen)
    }

    @Test
    fun `accelerator options always list CPU GPU and NPU with enabled flags`() = runTest {
        val viewModel = localSettingsViewModel(
            settings = FakeSettingRepository(localPlatform(model = "gpu-only")),
            catalog = FakeModelCatalogRepository(
                listOf(
                    catalogEntry("gpu-only", supportedAccelerators = listOf("gpu", "npu")),
                    catalogEntry("cpu-gpu", supportedAccelerators = listOf("cpu", "gpu", "npu"))
                )
            )
        )

        val options = viewModel.acceleratorOptions.value
        assertEquals(
            listOf(LocalAccelerators.CPU, LocalAccelerators.GPU, LocalAccelerators.NPU),
            options.map { it.accelerator }
        )
        assertFalse(options.single { it.accelerator == LocalAccelerators.CPU }.enabled)
        assertTrue(options.single { it.accelerator == LocalAccelerators.GPU }.enabled)
        assertFalse(options.single { it.accelerator == LocalAccelerators.NPU }.enabled)
        assertEquals(
            AcceleratorUnavailableReason.MODEL_HAS_NO_BUILD,
            options.single { it.accelerator == LocalAccelerators.CPU }.unavailableReason
        )
        assertEquals(
            AcceleratorUnavailableReason.MODEL_HAS_NO_BUILD,
            options.single { it.accelerator == LocalAccelerators.NPU }.unavailableReason
        )
    }

    @Test
    fun `NPU is listed disabled with a model reason when there is no SOC variant`() = runTest {
        val viewModel = localSettingsViewModel(
            settings = FakeSettingRepository(localPlatform(model = "cpu-gpu")),
            catalog = FakeModelCatalogRepository(
                listOf(catalogEntry("cpu-gpu", supportedAccelerators = listOf("npu", "cpu", "gpu")))
            )
        )

        val npu = viewModel.acceleratorOptions.value.single { it.accelerator == LocalAccelerators.NPU }
        assertFalse(npu.enabled)
        assertEquals(AcceleratorUnavailableReason.MODEL_HAS_NO_BUILD, npu.unavailableReason)
    }

    @Test
    fun `NPU is listed disabled with a device reason when this SOC is missing`() = runTest {
        val viewModel = localSettingsViewModel(
            settings = FakeSettingRepository(localPlatform(model = "cpu-gpu")),
            catalog = FakeModelCatalogRepository(
                listOf(
                    catalogEntry(
                        id = "cpu-gpu",
                        supportedAccelerators = listOf("npu", "cpu", "gpu"),
                        socToModelFiles = mapOf("SM8650" to SocVariant(modelFile = "npu.litertlm"))
                    )
                )
            ),
            deviceSocModel = "Tensor G4"
        )

        val npu = viewModel.acceleratorOptions.value.single { it.accelerator == LocalAccelerators.NPU }
        assertFalse(npu.enabled)
        assertEquals(AcceleratorUnavailableReason.DEVICE_NOT_SUPPORTED, npu.unavailableReason)
    }

    @Test
    fun `accelerator options include enabled NPU when the model and device SOC qualify`() = runTest {
        val viewModel = localSettingsViewModel(
            settings = FakeSettingRepository(localPlatform(model = "cpu-gpu")),
            catalog = FakeModelCatalogRepository(
                listOf(
                    catalogEntry(
                        id = "cpu-gpu",
                        supportedAccelerators = listOf("npu", "cpu", "gpu"),
                        socToModelFiles = mapOf("SM8650" to SocVariant(modelFile = "npu.litertlm"))
                    )
                )
            ),
            deviceSocModel = "SM8650"
        )

        val options = viewModel.acceleratorOptions.value
        assertEquals(
            listOf(LocalAccelerators.CPU, LocalAccelerators.GPU, LocalAccelerators.NPU),
            options.map { it.accelerator }
        )
        assertTrue(options.all { it.enabled })
    }

    @Test
    fun `updating accelerator persists NPU when the device qualifies`() = runTest {
        val settings = FakeSettingRepository(localPlatform(model = "cpu-gpu"))
        val viewModel = localSettingsViewModel(
            settings = settings,
            catalog = FakeModelCatalogRepository(
                listOf(
                    catalogEntry(
                        id = "cpu-gpu",
                        supportedAccelerators = listOf("cpu", "gpu", "npu"),
                        socToModelFiles = mapOf("SM8650" to SocVariant(modelFile = "npu.litertlm"))
                    )
                )
            ),
            deviceSocModel = "SM8650"
        )

        viewModel.updateAccelerator(LocalAccelerators.NPU)

        assertEquals(LocalAccelerators.NPU, viewModel.platformState.value?.accelerator)
        assertEquals(LocalAccelerators.NPU, settings.updatedPlatforms.single().accelerator)
        assertFalse(viewModel.dialogState.value.isAcceleratorDialogOpen)
    }

    @Test
    fun `NPU max-tokens updates are capped to the matching SOC variant context`() = runTest {
        val settings = FakeSettingRepository(
            localPlatform(model = "cpu-gpu", accelerator = LocalAccelerators.NPU, maxTokens = 512)
        )
        val viewModel = localSettingsViewModel(
            settings = settings,
            catalog = FakeModelCatalogRepository(
                listOf(
                    catalogEntry(
                        id = "cpu-gpu",
                        supportedAccelerators = listOf("cpu", "gpu", "npu"),
                        socToModelFiles = mapOf(
                            "SM8750" to SocVariant(modelFile = "npu.litertlm", contextSize = 1280)
                        )
                    )
                )
            ),
            deviceSocModel = "SM8750"
        )

        viewModel.updateMaxTokens(4096)

        assertEquals(1280, viewModel.platformState.value?.maxTokens)
        assertEquals(1280, settings.updatedPlatforms.single().maxTokens)
    }

    @Test
    fun `updating accelerator ignores NPU when the device does not qualify`() = runTest {
        val settings = FakeSettingRepository(localPlatform(model = "cpu-gpu", accelerator = LocalAccelerators.GPU))
        val viewModel = localSettingsViewModel(
            settings = settings,
            catalog = FakeModelCatalogRepository(
                listOf(catalogEntry("cpu-gpu", supportedAccelerators = listOf("cpu", "gpu", "npu")))
            )
        )

        viewModel.updateAccelerator(LocalAccelerators.NPU)

        assertEquals(LocalAccelerators.GPU, viewModel.platformState.value?.accelerator)
        assertTrue(settings.updatedPlatforms.isEmpty())
    }

    @Test
    fun `changing the local model reseeds sampling defaults from the new catalog entry`() = runTest {
        val settings = FakeSettingRepository(
            localPlatform(
                model = "gemma3-1b-it",
                temperature = 0.2f,
                topP = 0.4f,
                topK = 8,
                maxTokens = 256,
                accelerator = LocalAccelerators.CPU
            )
        )
        val viewModel = localSettingsViewModel(
            settings = settings,
            catalog = FakeModelCatalogRepository(
                listOf(
                    catalogEntry(
                        id = "gemma3-1b-it",
                        supportedAccelerators = listOf("cpu", "gpu"),
                        defaults = CatalogDefaultConfig(topK = 64, topP = 0.95f, temperature = 1.0f, maxTokens = 1024)
                    ),
                    catalogEntry(
                        id = "gemma-3n-e2b-it",
                        supportedAccelerators = listOf("cpu"),
                        defaults = CatalogDefaultConfig(topK = 20, topP = 0.8f, temperature = 0.7f, maxTokens = 4096)
                    )
                )
            )
        )

        viewModel.updateApiModel("gemma-3n-e2b-it")

        val updated = settings.updatedPlatforms.single()
        assertEquals("gemma-3n-e2b-it", updated.model)
        assertEquals(0.7f, updated.temperature)
        assertEquals(0.8f, updated.topP)
        assertEquals(20, updated.topK)
        assertEquals(4096, updated.maxTokens)
        assertEquals(LocalAccelerators.CPU, updated.accelerator)
    }

    @Test
    fun `changing the local model preserves sampling fields when catalog defaults are missing`() = runTest {
        val settings = FakeSettingRepository(
            localPlatform(
                model = "gemma3-1b-it",
                temperature = 0.2f,
                topP = 0.4f,
                topK = 8,
                maxTokens = 256,
                accelerator = LocalAccelerators.CPU
            )
        )
        val viewModel = localSettingsViewModel(
            settings = settings,
            catalog = FakeModelCatalogRepository(
                listOf(catalogEntry("gemma3-1b-it", supportedAccelerators = listOf("cpu", "gpu")))
            )
        )

        viewModel.updateApiModel("unknown-model")

        val updated = settings.updatedPlatforms.single()
        assertEquals("unknown-model", updated.model)
        assertEquals(0.2f, updated.temperature)
        assertEquals(0.4f, updated.topP)
        assertEquals(8, updated.topK)
        assertEquals(256, updated.maxTokens)
        assertEquals(LocalAccelerators.CPU, updated.accelerator)
    }

    @Test
    fun `enabling a local platform without a ready model stays disabled and explains why`() = runTest {
        val settings = FakeSettingRepository(localPlatform().copy(enabled = false, model = "pending-model"))
        val viewModel = localSettingsViewModel(
            settings = settings,
            localModels = FakeLocalModelRepository()
        )

        viewModel.toggleEnabled()

        assertFalse(viewModel.platformState.value!!.enabled)
        assertTrue(settings.updatedPlatforms.isEmpty())
        assertEquals(
            dev.chungjungsoo.gptmobile.R.string.local_platform_enable_model_not_ready,
            viewModel.userMessage.value
        )
    }

    @Test
    fun `enabling a local platform with a ready model succeeds`() = runTest {
        val settings = FakeSettingRepository(localPlatform().copy(enabled = false, model = "ready-model"))
        val viewModel = localSettingsViewModel(
            settings = settings,
            localModels = FakeLocalModelRepository(
                listOf(
                    LocalModel(
                        catalogEntryId = "ready-model",
                        commitHash = "hash",
                        fileName = "ready-model.litertlm",
                        relativeDirectory = "models/ready-model/hash",
                        totalBytes = 10L,
                        status = LocalModelStatus.READY
                    )
                )
            )
        )

        viewModel.toggleEnabled()

        assertTrue(viewModel.platformState.value!!.enabled)
        assertTrue(settings.updatedPlatforms.single().enabled)
        assertNull(viewModel.userMessage.value)
    }

    private fun localSettingsViewModel(
        settings: FakeSettingRepository,
        catalog: FakeModelCatalogRepository = FakeModelCatalogRepository(
            listOf(catalogEntry("gemma3-1b-it", supportedAccelerators = listOf("cpu", "gpu")))
        ),
        localModels: LocalModelRepository = FakeLocalModelRepository(),
        deviceSocModel: String = ""
    ): PlatformSettingViewModel = testViewModel(
        dao = FakeToolConnectionDao(),
        settingRepository = settings,
        catalogRepository = catalog,
        localModelRepository = localModels,
        platformUid = "local-1",
        deviceSocModel = deviceSocModel
    )

    private fun testViewModel(
        dao: FakeToolConnectionDao,
        settingRepository: SettingRepository = FakeSettingRepository(),
        catalogRepository: ModelCatalogRepository = FakeModelCatalogRepository(),
        localModelRepository: LocalModelRepository = FakeLocalModelRepository(),
        platformUid: String = "profile-1",
        deviceSocModel: String = ""
    ): PlatformSettingViewModel {
        val vault = FakeSecretVault()
        val repository = ToolConnectionRepository(dao, vault)
        val networkClient = NetworkClient(CIO)
        val manager = McpClientManager(networkClient())
        val resolver = AgentToolResolver(
            repository,
            vault,
            networkClient,
            manager,
            McpOAuthCoordinator(McpOAuthClient(networkClient()), repository, vault, manager)
        )
        return PlatformSettingViewModel(
            settingRepository = settingRepository,
            toolConnectionDao = dao,
            secretVault = vault,
            agentToolResolver = resolver,
            modelCatalogRepository = catalogRepository,
            localModelRepository = localModelRepository,
            deviceSocModel = deviceSocModel,
            savedStateHandle = SavedStateHandle(mapOf("platformUid" to platformUid))
        )
    }

    private fun testConnection(
        connectionUid: String,
        type: String = ToolConnectionType.FIRECRAWL
    ): ToolConnection = ToolConnection(
        connectionUid = connectionUid,
        name = connectionUid,
        alias = connectionUid.replace("-", "_"),
        type = type,
        endpointUrl = "https://example.com",
        authType = ToolConnectionAuthType.BEARER,
        secretRef = null,
        oauthClientId = null
    )

    private fun testBinding(profileUid: String, connectionUid: String): AgentToolBinding = AgentToolBinding(
        bindingUid = "$profileUid:$connectionUid:web_search",
        profileUid = profileUid,
        connectionUid = connectionUid,
        toolName = "web_search"
    )
}

internal class FakeToolConnectionDao(
    val connections: MutableMap<String, ToolConnection> = mutableMapOf(),
    val bindings: MutableList<AgentToolBinding> = mutableListOf()
) : ToolConnectionDao {
    override suspend fun listConnections(): List<ToolConnection> = connections.values.toList()

    override suspend fun getConnection(connectionUid: String): ToolConnection? = connections[connectionUid]

    override suspend fun getConnectionsByUids(connectionUids: List<String>): List<ToolConnection> = connectionUids.mapNotNull(connections::get)

    override suspend fun upsertConnection(connection: ToolConnection) {
        connections[connection.connectionUid] = connection
    }

    override suspend fun deleteConnectionByUid(connectionUid: String) {
        connections.remove(connectionUid)
        bindings.removeAll { it.connectionUid == connectionUid }
    }

    override suspend fun listBindingsByProfile(profileUid: String): List<AgentToolBinding> = bindings.filter { it.profileUid == profileUid }

    override suspend fun insertBinding(binding: AgentToolBinding) {
        bindings.removeAll { it.bindingUid == binding.bindingUid }
        bindings += binding
    }

    override suspend fun deleteConnectionToolBindingsForTypes(
        profileUid: String,
        toolName: String,
        connectionTypes: List<String>
    ) {
        bindings.removeAll { binding ->
            binding.profileUid == profileUid &&
                binding.toolName == toolName &&
                binding.connectionUid?.let { connections[it]?.type in connectionTypes } == true
        }
    }

    override suspend fun deleteBuiltInToolBinding(profileUid: String, toolName: String) {
        bindings.removeAll { it.profileUid == profileUid && it.toolName == toolName && it.connectionUid == null }
    }

    override suspend fun deleteConnectionBindingsForType(profileUid: String, connectionType: String) {
        bindings.removeAll { binding ->
            binding.profileUid == profileUid &&
                binding.connectionUid?.let { connections[it]?.type == connectionType } == true
        }
    }

    override suspend fun listBindingsWithConnections(profileUid: String): List<AgentToolBindingWithConnection> = listBindingsByProfile(profileUid).map { binding ->
        AgentToolBindingWithConnection(binding, binding.connectionUid?.let(connections::get))
    }
}

internal class FakeSecretVault : SecretVault {
    val values = mutableMapOf<String, ByteArray>()

    override suspend fun put(secretRef: String, secret: ByteArray) {
        values[secretRef] = secret.copyOf()
    }

    override suspend fun read(secretRef: String): ByteArray? = values[secretRef]?.copyOf()

    override suspend fun delete(secretRef: String) {
        values.remove(secretRef)?.fill(0)
    }
}

private class FakeSettingRepository(
    initialPlatform: PlatformV2 = PlatformV2(
        uid = "profile-1",
        name = "OpenAI",
        compatibleType = ClientType.OPENAI,
        enabled = true,
        apiUrl = "https://example.com",
        model = "gpt"
    ),
    private val fetchPlatformsResult: List<PlatformV2> = listOf(initialPlatform),
    var fetchFailure: Throwable? = null
) : SettingRepository {
    private var platform = initialPlatform
    val updatedPlatforms = mutableListOf<PlatformV2>()

    override suspend fun fetchPlatforms(): List<Platform> = emptyList()

    override suspend fun fetchPlatformV2s(): List<PlatformV2> {
        fetchFailure?.let { throw it }
        return fetchPlatformsResult.map { if (it.uid == platform.uid) platform else it }
    }

    override suspend fun fetchThemes(): ThemeSetting = ThemeSetting()
    override suspend fun migrateToPlatformV2() = Unit
    override suspend fun migrateSecrets(): List<SecretMigrationError> = emptyList()
    override suspend fun updatePlatforms(platforms: List<Platform>) = Unit
    override suspend fun updateThemes(themeSetting: ThemeSetting) = Unit
    override suspend fun addPlatformV2(platform: PlatformV2) = Unit
    override suspend fun updatePlatformV2(platform: PlatformV2) {
        this.platform = platform
        updatedPlatforms += platform
    }
    override suspend fun deletePlatformV2(platform: PlatformV2) = Unit
    override suspend fun getPlatformV2ById(id: Int): PlatformV2? = null
}

private class FakeModelCatalogRepository(
    private val entries: List<CatalogEntry> = emptyList()
) : ModelCatalogRepository {
    override suspend fun getVisibleEntries(): List<CatalogEntry> = entries
}

private fun localPlatform(
    model: String = "gemma3-1b-it",
    temperature: Float? = 1.0f,
    topP: Float? = 0.95f,
    topK: Int? = 64,
    maxTokens: Int? = 1024,
    accelerator: String? = LocalAccelerators.GPU
) = PlatformV2(
    uid = "local-1",
    name = "Local",
    compatibleType = ClientType.LITERT_LM,
    enabled = true,
    apiUrl = "",
    model = model,
    temperature = temperature,
    topP = topP,
    topK = topK,
    maxTokens = maxTokens,
    accelerator = accelerator
)

private fun catalogEntry(
    id: String,
    supportedAccelerators: List<String>,
    defaults: CatalogDefaultConfig = CatalogDefaultConfig(),
    socToModelFiles: Map<String, SocVariant> = emptyMap()
) = CatalogEntry(
    id = id,
    displayName = id,
    supportedAccelerators = supportedAccelerators,
    defaultConfig = defaults,
    socToModelFiles = socToModelFiles
)
