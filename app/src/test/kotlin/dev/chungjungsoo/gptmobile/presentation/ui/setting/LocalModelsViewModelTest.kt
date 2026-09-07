package dev.chungjungsoo.gptmobile.presentation.ui.setting

import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceTokenStore
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStatus
import dev.chungjungsoo.gptmobile.data.repository.FakeLocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepository
import dev.chungjungsoo.gptmobile.presentation.ui.setup.FakeHuggingFaceAuthClient
import dev.chungjungsoo.gptmobile.presentation.ui.setup.FakeLocalDownloadGuards
import dev.chungjungsoo.gptmobile.presentation.ui.setup.MapSecretVault
import dev.chungjungsoo.gptmobile.presentation.ui.setup.RecordingProber
import dev.chungjungsoo.gptmobile.presentation.ui.setup.defaultWizardCatalog
import dev.chungjungsoo.gptmobile.presentation.ui.setup.localModelsViewModel
import dev.chungjungsoo.gptmobile.presentation.ui.setup.wizardGatedCoordinator
import dev.chungjungsoo.gptmobile.presentation.ui.setup.wizardStoredModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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
class LocalModelsViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `catalog failure is retryable and is not reported as empty success`() = runTest {
        val catalog = FailOnceCatalog(defaultWizardCatalog().getVisibleEntries())
        val localModels = FakeLocalModelRepository()
        val tokenStore = HuggingFaceTokenStore(MapSecretVault())
        val viewModel = LocalModelsViewModel(
            modelCatalogRepository = catalog,
            localModelRepository = localModels,
            gatedDownloadCoordinator = wizardGatedCoordinator(tokenStore = tokenStore),
            huggingFaceTokenStore = tokenStore,
            downloadGuards = FakeLocalDownloadGuards(),
            huggingFaceAuthClient = FakeHuggingFaceAuthClient(),
            deviceSocModel = ""
        )

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Catalog failed", viewModel.uiState.value.loadError)
        assertTrue(viewModel.uiState.value.items.isEmpty())

        viewModel.retryLoad()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.loadError)
        assertEquals(3, viewModel.uiState.value.items.size)
    }

    @Test
    fun `access token save and remove round-trip through the store`() = runTest {
        val vault = MapSecretVault()
        val tokenStore = HuggingFaceTokenStore(vault)
        val viewModel = localModelsViewModel(tokenStore = tokenStore)

        assertFalse(viewModel.uiState.value.hasHuggingFaceToken)

        viewModel.saveHuggingFaceAccessToken("hf_secret")

        assertTrue(viewModel.uiState.value.hasHuggingFaceToken)
        assertEquals("hf_secret", tokenStore.readAccessToken())
        assertEquals(
            "hf_secret",
            vault.values.getValue(HuggingFaceTokenStore.SECRET_REF).decodeToString()
        )

        viewModel.removeHuggingFaceAccessToken()

        assertFalse(viewModel.uiState.value.hasHuggingFaceToken)
        assertNull(tokenStore.readAccessToken())
        assertNull(vault.values[HuggingFaceTokenStore.SECRET_REF])
    }

    @Test
    fun `oauth not configured plus token entry retries and starts the download`() = runTest {
        val localModels = FakeLocalModelRepository()
        val prober = RecordingProber(statusCode = 401)
        val tokenStore = HuggingFaceTokenStore(MapSecretVault())
        val viewModel = localModelsViewModel(
            localModels = localModels,
            tokenStore = tokenStore,
            gatedCoordinator = wizardGatedCoordinator(
                oauthConfigured = false,
                tokenStore = tokenStore,
                prober = prober
            )
        )
        val gated = viewModel.uiState.value.items.single { it.entry.id == "gated-model" }.entry

        viewModel.onDownloadClick(gated)

        val blocked = viewModel.uiState.value.dialog
        assertTrue(blocked is LocalModelsDialog.OAuthNotConfigured)
        assertFalse((blocked as LocalModelsDialog.OAuthNotConfigured).isSessionExpired)
        assertTrue(localModels.startDownloadCalls.isEmpty())

        prober.statusCode = 200
        viewModel.saveHuggingFaceAccessToken("hf_ok")

        assertEquals(listOf("gated-model"), localModels.startDownloadCalls)
        assertEquals(LocalModelsDialog.Hidden, viewModel.uiState.value.dialog)
        assertEquals("hf_ok", tokenStore.readAccessToken())
    }

    @Test
    fun `session expired without oauth routes to token entry`() = runTest {
        val vault = MapSecretVault(
            mapOf(HuggingFaceTokenStore.SECRET_REF to "hf_expired".encodeToByteArray())
        )
        val tokenStore = HuggingFaceTokenStore(vault)
        val viewModel = localModelsViewModel(
            tokenStore = tokenStore,
            gatedCoordinator = wizardGatedCoordinator(
                statusCode = 401,
                oauthConfigured = false,
                tokenStore = tokenStore
            )
        )
        val gated = viewModel.uiState.value.items.single { it.entry.id == "gated-model" }.entry

        viewModel.onDownloadClick(gated)

        val dialog = viewModel.uiState.value.dialog
        assertTrue(dialog is LocalModelsDialog.OAuthNotConfigured)
        assertTrue((dialog as LocalModelsDialog.OAuthNotConfigured).isSessionExpired)
        assertNull(tokenStore.readAccessToken())
    }

    @Test
    fun `retry of a failed download skips ram but re-checks metered connection`() = runTest {
        val localModels = FakeLocalModelRepository(
            listOf(wizardStoredModel("pending-model", status = LocalModelStatus.FAILED))
        )
        val viewModel = localModelsViewModel(
            localModels = localModels,
            guards = FakeLocalDownloadGuards(metered = true, lowRamEntryIds = setOf("pending-model"))
        )
        val entry = viewModel.uiState.value.items.single { it.entry.id == "pending-model" }.entry

        viewModel.onDownloadClick(entry)

        assertTrue(localModels.startDownloadCalls.isEmpty())
        assertTrue(viewModel.uiState.value.dialog is LocalModelsDialog.MeteredConfirm)
    }

    @Test
    fun `retry of a failed download on unmetered starts immediately even when ram is low`() = runTest {
        val localModels = FakeLocalModelRepository(
            listOf(wizardStoredModel("pending-model", status = LocalModelStatus.FAILED))
        )
        val viewModel = localModelsViewModel(
            localModels = localModels,
            guards = FakeLocalDownloadGuards(metered = false, lowRamEntryIds = setOf("pending-model"))
        )
        val entry = viewModel.uiState.value.items.single { it.entry.id == "pending-model" }.entry

        viewModel.onDownloadClick(entry)

        assertEquals(listOf("pending-model"), localModels.startDownloadCalls)
        assertEquals(LocalModelsDialog.Hidden, viewModel.uiState.value.dialog)
    }

    @Test
    fun `fresh download still shows the ram warning`() = runTest {
        val localModels = FakeLocalModelRepository()
        val viewModel = localModelsViewModel(
            localModels = localModels,
            guards = FakeLocalDownloadGuards(lowRamEntryIds = setOf("pending-model"))
        )
        val entry = viewModel.uiState.value.items.single { it.entry.id == "pending-model" }.entry

        viewModel.onDownloadClick(entry)

        assertTrue(viewModel.uiState.value.dialog is LocalModelsDialog.RamWarning)
        assertTrue(localModels.startDownloadCalls.isEmpty())
    }
}

private class FailOnceCatalog(
    private val entries: List<CatalogEntry>
) : ModelCatalogRepository {
    private var shouldFail = true

    override suspend fun getVisibleEntries(): List<CatalogEntry> {
        if (shouldFail) {
            shouldFail = false
            error("Catalog failed")
        }
        return entries
    }
}
