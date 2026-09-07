package dev.chungjungsoo.gptmobile.presentation.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting
import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceTokenStore
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStatus
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.repository.FakeLocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.SecretMigrationError
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LocalModelItemStatus
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LocalModelsDialog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelV2Test {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `wizard model step lists the full catalog with downloaded and undownloaded status`() = runTest {
        val viewModel = setupViewModel(
            localModels = FakeLocalModelRepository(listOf(wizardStoredModel("ready-model")))
        )

        val items = viewModel.catalogLocalModels.value
        assertEquals(listOf("ready-model", "pending-model", "gated-model"), items.map { it.entry.id })
        assertEquals(listOf("Ready", "Pending", "Gated"), items.map { it.entry.displayName })
        assertEquals(2_000_000L, items[0].entry.sizeInBytes)
        assertEquals(8, items[1].entry.minRamGb)
        assertEquals(LocalModelItemStatus.READY, items[0].status)
        assertEquals(LocalModelItemStatus.NOT_DOWNLOADED, items[1].status)
        assertEquals(LocalModelItemStatus.NOT_DOWNLOADED, items[2].status)
    }

    @Test
    fun `selecting an undownloaded model on a metered connection starts download once after confirm`() = runTest {
        val localModels = FakeLocalModelRepository()
        val viewModel = setupViewModel(
            localModels = localModels,
            guards = FakeLocalDownloadGuards(metered = true)
        )
        viewModel.selectClientType(ClientType.LITERT_LM)

        viewModel.selectLocalModel("pending-model")

        assertEquals("pending-model", viewModel.model.value)
        val dialog = viewModel.localModelDownloadState.value.dialog
        assertTrue(dialog is LocalModelsDialog.MeteredConfirm)
        assertEquals("pending-model", (dialog as LocalModelsDialog.MeteredConfirm).entry.id)
        assertTrue(localModels.startDownloadCalls.isEmpty())

        viewModel.confirmMeteredDownload()
        assertEquals(listOf("pending-model"), localModels.startDownloadCalls)
        assertEquals(LocalModelsDialog.Hidden, viewModel.localModelDownloadState.value.dialog)
        assertEquals(LocalModelItemStatus.DOWNLOADING, statusOf(viewModel, "pending-model"))

        viewModel.selectLocalModel("pending-model")
        assertEquals(listOf("pending-model"), localModels.startDownloadCalls)
    }

    @Test
    fun `selecting a READY model enables finish without any other field change`() = runTest {
        val viewModel = setupViewModel(
            localModels = FakeLocalModelRepository(listOf(wizardStoredModel("ready-model")))
        )
        viewModel.selectClientType(ClientType.LITERT_LM)
        viewModel.nextWizardStep()

        assertFalse(viewModel.canProceed.value)

        viewModel.selectLocalModel("ready-model")

        assertTrue(viewModel.canProceed.value)
        assertFalse(viewModel.isWaitingForDownload.value)
    }

    @Test
    fun `download becoming READY clears waiting without any other field change`() = runTest {
        val localModels = FakeLocalModelRepository()
        val viewModel = setupViewModel(localModels = localModels)
        viewModel.selectClientType(ClientType.LITERT_LM)
        viewModel.nextWizardStep()

        viewModel.selectLocalModel("pending-model")

        assertTrue(viewModel.canProceed.value)
        assertTrue(viewModel.isWaitingForDownload.value)

        localModels.setModels(listOf(wizardStoredModel("pending-model", LocalModelStatus.READY)))

        assertTrue(viewModel.canProceed.value)
        assertFalse(viewModel.isWaitingForDownload.value)
    }

    @Test
    fun `finish is enabled once a local model is selected even while downloading`() = runTest {
        val localModels = FakeLocalModelRepository()
        val viewModel = setupViewModel(localModels = localModels)
        viewModel.selectClientType(ClientType.LITERT_LM)
        viewModel.updatePlatformName("Local")
        viewModel.nextWizardStep()

        viewModel.selectLocalModel("pending-model")

        assertTrue(viewModel.canProceedFromStep(SetupViewModelV2.WIZARD_STEP_MODEL))
        assertTrue(viewModel.isWaitingForModelDownload())

        localModels.setModels(listOf(wizardStoredModel("pending-model", LocalModelStatus.READY)))

        assertTrue(viewModel.canProceedFromStep(SetupViewModelV2.WIZARD_STEP_MODEL))
        assertFalse(viewModel.isWaitingForModelDownload())
    }

    @Test
    fun `abandoning the wizard does not cancel the download or persist a platform`() = runTest {
        val settings = RecordingSettingRepository()
        val localModels = FakeLocalModelRepository()
        val store = ViewModelStore()
        val viewModel = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return setupViewModel(settings = settings, localModels = localModels) as T
                }
            }
        )[SetupViewModelV2::class.java]
        viewModel.selectClientType(ClientType.LITERT_LM)
        viewModel.updatePlatformName("Local")
        viewModel.selectLocalModel("pending-model")

        assertEquals(listOf("pending-model"), localModels.startDownloadCalls)
        assertTrue(settings.addedPlatforms.isEmpty())

        viewModel.resetWizard()
        store.clear()

        assertTrue(localModels.cancelDownloadCalls.isEmpty())
        assertTrue(settings.addedPlatforms.isEmpty())
        assertEquals(listOf("pending-model"), localModels.startDownloadCalls)
    }

    @Test
    fun `gated catalog entries route through coordinator sign-in and license states`() = runTest {
        val signInViewModel = setupViewModel(gatedCoordinator = wizardGatedCoordinator(statusCode = 401))
        signInViewModel.selectClientType(ClientType.LITERT_LM)
        signInViewModel.selectLocalModel("gated-model")

        val signIn = signInViewModel.localModelDownloadState.value.dialog
        assertTrue(signIn is LocalModelsDialog.SignIn)
        assertFalse((signIn as LocalModelsDialog.SignIn).isSessionExpired)

        val licenseTokenStore = huggingFaceTokenStoreWithToken("hf_ok")
        val licenseViewModel = setupViewModel(
            tokenStore = licenseTokenStore,
            gatedCoordinator = wizardGatedCoordinator(
                statusCode = 403,
                tokenStore = licenseTokenStore
            )
        )
        licenseViewModel.selectClientType(ClientType.LITERT_LM)
        licenseViewModel.selectLocalModel("gated-model")

        val license = licenseViewModel.localModelDownloadState.value.dialog
        assertTrue(license is LocalModelsDialog.License)
    }

    @Test
    fun `savePlatform persists a disabled pending platform while the model is downloading`() = runTest {
        val settings = RecordingSettingRepository()
        val localModels = FakeLocalModelRepository()
        val viewModel = setupViewModel(settings = settings, localModels = localModels)
        viewModel.selectClientType(ClientType.LITERT_LM)
        viewModel.updatePlatformName("On-device")
        viewModel.selectLocalModel("pending-model")
        viewModel.savePlatform()

        val saved = settings.addedPlatforms.single()
        assertEquals("On-device", saved.name)
        assertEquals(ClientType.LITERT_LM, saved.compatibleType)
        assertEquals("pending-model", saved.model)
        assertFalse(saved.enabled)
        assertEquals(SaveStatus.Success, viewModel.saveStatus.value)
        assertEquals(listOf("pending-model"), localModels.startDownloadCalls)
        assertTrue(localModels.cancelDownloadCalls.isEmpty())
    }

    @Test
    fun `savePlatform persists an enabled platform when the selected model is ready`() = runTest {
        val settings = RecordingSettingRepository()
        val localModels = FakeLocalModelRepository(listOf(wizardStoredModel("ready-model")))
        val viewModel = setupViewModel(settings = settings, localModels = localModels)
        viewModel.selectClientType(ClientType.LITERT_LM)
        viewModel.updatePlatformName("On-device")
        viewModel.selectLocalModel("ready-model")
        viewModel.savePlatform()

        val saved = settings.addedPlatforms.single()
        assertEquals("ready-model", saved.model)
        assertTrue(saved.enabled)
        assertTrue(localModels.startDownloadCalls.isEmpty())
    }

    @Test
    fun `savePlatform invokes callback only after persistence succeeds`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>()
        val settings = GatedSettingRepository(gate = gate)
        val viewModel = gatedSaveViewModel(settings)
        var didComplete = false
        viewModel.selectClientType(ClientType.LITERT_LM)
        viewModel.updatePlatformName("On-device")
        viewModel.selectLocalModel("ready-model")

        viewModel.savePlatform { didComplete = true }
        viewModel.savePlatform { didComplete = true }
        runCurrent()

        assertEquals(SaveStatus.Saving, viewModel.saveStatus.value)
        assertFalse(didComplete)
        assertTrue(settings.addedPlatforms.isEmpty())

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(SaveStatus.Success, viewModel.saveStatus.value)
        assertTrue(didComplete)
        assertEquals(1, settings.addCallCount)
        assertEquals("On-device", settings.addedPlatforms.single().name)
    }

    @Test
    fun `savePlatform does not invoke callback when persistence fails`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settings = GatedSettingRepository(failure = IllegalStateException("Save failed"))
        val viewModel = gatedSaveViewModel(settings)
        var didComplete = false
        viewModel.selectClientType(ClientType.LITERT_LM)
        viewModel.updatePlatformName("On-device")
        viewModel.selectLocalModel("ready-model")

        viewModel.savePlatform { didComplete = true }
        advanceUntilIdle()

        assertFalse(didComplete)
        val status = viewModel.saveStatus.value
        assertTrue(status is SaveStatus.Error)
        assertEquals("Save failed", (status as SaveStatus.Error).message)
        assertEquals("On-device", viewModel.platformName.value)
        assertEquals("ready-model", viewModel.model.value)
    }

    private fun statusOf(viewModel: SetupViewModelV2, catalogEntryId: String) = viewModel.catalogLocalModels.value
        .first { it.entry.id == catalogEntryId }
        .status
}

private fun huggingFaceTokenStoreWithToken(token: String) = dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceTokenStore(
    MapSecretVault(mapOf(dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceTokenStore.SECRET_REF to token.encodeToByteArray()))
)

private fun gatedSaveViewModel(settings: GatedSettingRepository) = SetupViewModelV2(
    settingRepository = settings,
    localModelRepository = FakeLocalModelRepository(listOf(wizardStoredModel("ready-model"))),
    modelCatalogRepository = defaultWizardCatalog(),
    gatedDownloadCoordinator = wizardGatedCoordinator(),
    huggingFaceTokenStore = HuggingFaceTokenStore(MapSecretVault()),
    downloadGuards = FakeLocalDownloadGuards(),
    huggingFaceAuthClient = FakeHuggingFaceAuthClient(),
    deviceSocModel = ""
)

private class GatedSettingRepository(
    private val gate: CompletableDeferred<Unit>? = null,
    private val failure: Throwable? = null
) : SettingRepository {
    val addedPlatforms = mutableListOf<PlatformV2>()
    var addCallCount = 0

    override suspend fun fetchPlatforms() = emptyList<Platform>()
    override suspend fun fetchPlatformV2s() = emptyList<PlatformV2>()
    override suspend fun fetchThemes() = ThemeSetting()
    override suspend fun migrateToPlatformV2() = Unit
    override suspend fun migrateSecrets() = emptyList<SecretMigrationError>()
    override suspend fun updatePlatforms(platforms: List<Platform>) = Unit
    override suspend fun updateThemes(themeSetting: ThemeSetting) = Unit

    override suspend fun addPlatformV2(platform: PlatformV2) {
        addCallCount++
        gate?.await()
        failure?.let { throw it }
        addedPlatforms += platform
    }

    override suspend fun updatePlatformV2(platform: PlatformV2) = Unit
    override suspend fun deletePlatformV2(platform: PlatformV2) = Unit
    override suspend fun getPlatformV2ById(id: Int): PlatformV2? = null
}
