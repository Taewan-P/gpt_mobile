package dev.chungjungsoo.gptmobile.presentation.ui.setup

import android.content.Intent
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.database.entity.LocalModel
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ProviderConnection
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting
import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceTokenStore
import dev.chungjungsoo.gptmobile.data.localmodel.GatedDownloadCoordinator
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelDownloadProber
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStatus
import dev.chungjungsoo.gptmobile.data.model.LocalRuntimeBackend
import dev.chungjungsoo.gptmobile.data.repository.FakeLocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.FakeModelCatalogRepository
import dev.chungjungsoo.gptmobile.data.repository.SecretMigrationError
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.HuggingFaceAuthClient
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.HuggingFaceSignInResult
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.LocalDownloadGuards
import dev.chungjungsoo.gptmobile.presentation.ui.setting.AddPlatformViewModel
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LocalModelsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

internal fun wizardCatalogEntry(
    id: String,
    displayName: String = id,
    sizeInBytes: Long = 2_000_000L,
    minRamGb: Int = 4,
    isGated: Boolean = false,
    downloadUrl: String = "https://huggingface.co/example/$id/resolve/main/model.litertlm?download=true"
) = CatalogEntry(
    id = id,
    displayName = displayName,
    downloadUrl = downloadUrl,
    sizeInBytes = sizeInBytes,
    minRamGb = minRamGb,
    isGated = isGated
)

internal fun wizardStoredModel(
    catalogEntryId: String,
    status: String = LocalModelStatus.READY
) = LocalModel(
    catalogEntryId = catalogEntryId,
    commitHash = "hash",
    fileName = "$catalogEntryId.litertlm",
    relativeDirectory = "models/$catalogEntryId/hash",
    totalBytes = 2_000_000L,
    status = status
)

internal fun defaultWizardCatalog() = FakeModelCatalogRepository(
    listOf(
        wizardCatalogEntry("ready-model", displayName = "Ready"),
        wizardCatalogEntry("pending-model", displayName = "Pending", sizeInBytes = 5_000_000L, minRamGb = 8),
        wizardCatalogEntry(
            "gated-model",
            displayName = "Gated",
            isGated = true,
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/model.litertlm?download=true"
        )
    )
)

internal class FakeLocalDownloadGuards(
    var metered: Boolean = false,
    var lowRamEntryIds: Set<String> = emptySet()
) : LocalDownloadGuards {
    override fun isMeteredConnection(): Boolean = metered

    override fun belowRamRequirement(entry: CatalogEntry): Boolean = entry.id in lowRamEntryIds
}

internal class FakeHuggingFaceAuthClient : HuggingFaceAuthClient {
    override fun authorizationIntent(): Intent? = null

    override suspend fun completeSignIn(data: Intent?): HuggingFaceSignInResult = HuggingFaceSignInResult.Cancelled

    override fun dispose() = Unit
}

internal class RecordingProber(
    var statusCode: Int = 200
) : LocalModelDownloadProber {
    override fun probe(downloadUrl: String, accessToken: String?): Int = statusCode
}

internal class MapSecretVault(
    initial: Map<String, ByteArray> = emptyMap()
) : SecretVault {
    val values = initial.mapValues { it.value.copyOf() }.toMutableMap()

    override suspend fun put(secretRef: String, secret: ByteArray) {
        values[secretRef] = secret.copyOf()
    }

    override suspend fun read(secretRef: String): ByteArray? = values[secretRef]?.copyOf()

    override suspend fun delete(secretRef: String) {
        values.remove(secretRef)?.fill(0)
    }
}

internal class RecordingSettingRepository : SettingRepository {
    val addedPlatforms = mutableListOf<PlatformV2>()
    var localRuntimeBackend: LocalRuntimeBackend = LocalRuntimeBackend.QUALCOMM_QNN
    var debugMode: Boolean = false

    override suspend fun fetchProviderConnections(): List<ProviderConnection> = emptyList()
    override fun observeProviderConnections(): Flow<List<ProviderConnection>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override suspend fun getProviderConnection(uid: String): ProviderConnection? = null
    override suspend fun addProviderConnection(connection: ProviderConnection, credential: String?): ProviderConnection = error("Provider writes are not used by this fixture")
    override suspend fun updateProviderConnection(connection: ProviderConnection, credential: String?): ProviderConnection = error("Provider writes are not used by this fixture")
    override suspend fun deleteProviderConnection(connection: ProviderConnection): Boolean = error("Provider writes are not used by this fixture")

    override suspend fun fetchPlatforms(): List<Platform> = emptyList()

    override suspend fun fetchPlatformV2s(): List<PlatformV2> = emptyList()

    override fun observePlatformV2s(): Flow<List<PlatformV2>> = flowOf(emptyList())

    override fun observePlatformV2ByUid(uid: String): Flow<PlatformV2?> = flowOf(null)

    override suspend fun fetchThemes(): ThemeSetting = ThemeSetting()

    override suspend fun getLocalRuntimeBackend(): LocalRuntimeBackend = localRuntimeBackend

    override suspend fun updateLocalRuntimeBackend(backend: LocalRuntimeBackend) {
        localRuntimeBackend = backend
    }

    override suspend fun getDebugMode(): Boolean = debugMode

    override suspend fun updateDebugMode(enabled: Boolean) {
        debugMode = enabled
    }

    override fun observeDebugMode(): Flow<Boolean> = flowOf(debugMode)

    override suspend fun migrateToPlatformV2() = Unit

    override suspend fun migrateSecrets(): List<SecretMigrationError> = emptyList()

    override suspend fun updatePlatforms(platforms: List<Platform>) = Unit

    override suspend fun updateThemes(themeSetting: ThemeSetting) = Unit

    override suspend fun getFavoriteGroups(): List<String> = emptyList()

    override suspend fun saveFavoriteGroups(groups: List<String>) = Unit

    override fun observeFavoriteGroups(): Flow<List<String>> = flowOf(emptyList())

    override suspend fun getFavoriteMessageGroups(): Map<Int, String> = emptyMap()

    override suspend fun saveFavoriteMessageGroups(messageGroups: Map<Int, String>) = Unit

    override fun observeFavoriteMessageGroups(): Flow<Map<Int, String>> = flowOf(emptyMap())

    override suspend fun addPlatformV2(platform: PlatformV2) {
        addedPlatforms += platform
    }

    override suspend fun updatePlatformV2(platform: PlatformV2) = Unit

    override suspend fun deletePlatformV2(platform: PlatformV2) = Unit

    override suspend fun getPlatformV2ById(id: Int): PlatformV2? = null

    override suspend fun exportConfigurationJson(): String = "{}"

    override suspend fun importConfigurationJson(json: String): Result<Int> = Result.success(0)
}

internal fun wizardGatedCoordinator(
    statusCode: Int = 200,
    oauthConfigured: Boolean = true,
    tokenStore: HuggingFaceTokenStore = HuggingFaceTokenStore(MapSecretVault()),
    prober: LocalModelDownloadProber = RecordingProber(statusCode)
) = GatedDownloadCoordinator(
    tokenStore = tokenStore,
    prober = prober,
    isOAuthConfigured = { oauthConfigured },
    ioDispatcher = Dispatchers.Unconfined
)

internal fun setupViewModel(
    settings: RecordingSettingRepository = RecordingSettingRepository(),
    localModels: FakeLocalModelRepository = FakeLocalModelRepository(),
    catalog: FakeModelCatalogRepository = defaultWizardCatalog(),
    gatedCoordinator: GatedDownloadCoordinator = wizardGatedCoordinator(),
    tokenStore: HuggingFaceTokenStore = HuggingFaceTokenStore(MapSecretVault()),
    guards: LocalDownloadGuards = FakeLocalDownloadGuards(),
    authClient: HuggingFaceAuthClient = FakeHuggingFaceAuthClient(),
    deviceSocModel: String = "",
    deviceRamGb: Long = 8L
) = SetupViewModelV2(
    settingRepository = settings,
    localModelRepository = localModels,
    modelCatalogRepository = catalog,
    gatedDownloadCoordinator = gatedCoordinator,
    huggingFaceTokenStore = tokenStore,
    downloadGuards = guards,
    huggingFaceAuthClient = authClient,
    deviceSocModel = deviceSocModel,
    deviceRamGb = deviceRamGb
)

internal fun addPlatformViewModel(
    localModels: FakeLocalModelRepository = FakeLocalModelRepository(),
    catalog: FakeModelCatalogRepository = defaultWizardCatalog(),
    gatedCoordinator: GatedDownloadCoordinator = wizardGatedCoordinator(),
    tokenStore: HuggingFaceTokenStore = HuggingFaceTokenStore(MapSecretVault()),
    guards: LocalDownloadGuards = FakeLocalDownloadGuards(),
    authClient: HuggingFaceAuthClient = FakeHuggingFaceAuthClient(),
    deviceSocModel: String = "",
    deviceRamGb: Long = 8L
) = AddPlatformViewModel(
    localModelRepository = localModels,
    modelCatalogRepository = catalog,
    gatedDownloadCoordinator = gatedCoordinator,
    huggingFaceTokenStore = tokenStore,
    downloadGuards = guards,
    huggingFaceAuthClient = authClient,
    deviceSocModel = deviceSocModel,
    deviceRamGb = deviceRamGb
)

internal fun localModelsViewModel(
    localModels: FakeLocalModelRepository = FakeLocalModelRepository(),
    catalog: FakeModelCatalogRepository = defaultWizardCatalog(),
    gatedCoordinator: GatedDownloadCoordinator = wizardGatedCoordinator(),
    tokenStore: HuggingFaceTokenStore = HuggingFaceTokenStore(MapSecretVault()),
    guards: LocalDownloadGuards = FakeLocalDownloadGuards(),
    authClient: HuggingFaceAuthClient = FakeHuggingFaceAuthClient(),
    deviceSocModel: String = ""
) = LocalModelsViewModel(
    modelCatalogRepository = catalog,
    localModelRepository = localModels,
    benchmarkStore = io.mockk.mockk<dev.chungjungsoo.gptmobile.data.benchmark.BenchmarkStore> {
        io.mockk.coEvery { load() } returns Unit
        io.mockk.every { history } returns MutableStateFlow(emptyList())
    },
    gatedDownloadCoordinator = gatedCoordinator,
    huggingFaceTokenStore = tokenStore,
    downloadGuards = guards,
    huggingFaceAuthClient = authClient,
    deviceSocModel = deviceSocModel
)
