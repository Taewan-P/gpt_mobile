package dev.chungjungsoo.gptmobile.presentation.ui.localmodel

import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceTokenStore
import dev.chungjungsoo.gptmobile.data.localmodel.GatedDownloadCoordinator
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelReplacementCoordinator
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStatus
import dev.chungjungsoo.gptmobile.data.localmodel.ResolvedModelDownload
import dev.chungjungsoo.gptmobile.data.localruntime.FakeLocalRuntime
import dev.chungjungsoo.gptmobile.data.repository.FakeLocalModelRepository
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LocalModelItemStatus
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LocalModelsDialog
import dev.chungjungsoo.gptmobile.presentation.ui.setup.FakeHuggingFaceAuthClient
import dev.chungjungsoo.gptmobile.presentation.ui.setup.FakeLocalDownloadGuards
import dev.chungjungsoo.gptmobile.presentation.ui.setup.MapSecretVault
import dev.chungjungsoo.gptmobile.presentation.ui.setup.RecordingProber
import dev.chungjungsoo.gptmobile.presentation.ui.setup.wizardCatalogEntry
import dev.chungjungsoo.gptmobile.presentation.ui.setup.wizardGatedCoordinator
import dev.chungjungsoo.gptmobile.presentation.ui.setup.wizardStoredModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalModelReplacementViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun request_doesNotDownloadUntilConfirm_andKeepsExactTargetThroughAuthAndCancel() = runTest {
        val localModels = FakeLocalModelRepository(listOf(wizardStoredModel("gated-model")))
        val coordinator = LocalModelReplacementCoordinator()
        val prober = RecordingProber(statusCode = 401)
        val tokenStore = HuggingFaceTokenStore(MapSecretVault())
        val viewModel = replacementViewModel(
            coordinator = coordinator,
            localModels = localModels,
            tokenStore = tokenStore,
            gatedCoordinator = wizardGatedCoordinator(
                oauthConfigured = false,
                tokenStore = tokenStore,
                prober = prober
            )
        )
        val entry = wizardCatalogEntry(
            id = "gated-model",
            isGated = true,
            downloadUrl = "https://huggingface.co/example/cpu/resolve/main/cpu.litertlm"
        )
        val target = ResolvedModelDownload(
            fileName = "npu.litertlm",
            downloadUrl = "https://huggingface.co/example/npu/resolve/abc/npu.litertlm",
            commitHash = "abc",
            sizeInBytes = 9_000_000L
        )

        coordinator.request(entry, target, "npu")

        assertTrue(localModels.startDownloadCalls.isEmpty())
        assertEquals("gated-model", viewModel.uiState.value.confirmation?.entry?.id)
        assertEquals(target.downloadUrl, viewModel.uiState.value.confirmation?.target?.downloadUrl)

        viewModel.dismissConfirmation()

        assertTrue(localModels.startDownloadCalls.isEmpty())
        assertTrue(coordinator.pending.value.isEmpty())
        assertEquals(LocalModelStatus.READY, localModels.getById("gated-model")?.status)

        coordinator.request(entry, target, "npu")
        viewModel.confirmReplacement()

        assertTrue(localModels.startDownloadCalls.isEmpty())
        assertTrue(viewModel.uiState.value.download.dialog is LocalModelsDialog.OAuthNotConfigured)
        assertNull(viewModel.uiState.value.confirmation)

        coordinator.request(
            wizardCatalogEntry("other-model"),
            target.copy(fileName = "other.litertlm", downloadUrl = "https://example/other"),
            "cpu"
        )
        assertEquals("gated-model", coordinator.pending.value.first().entry.id)
        assertEquals(2, coordinator.pending.value.size)
        assertTrue(viewModel.uiState.value.download.dialog is LocalModelsDialog.OAuthNotConfigured)

        prober.statusCode = 200
        viewModel.saveAccessToken("hf_ok")

        assertEquals(listOf("gated-model"), localModels.startDownloadCalls)
        assertEquals(listOf(target), localModels.startDownloadResolved)
        assertEquals("other-model", coordinator.pending.value.single().entry.id)
    }

    @Test
    fun confirmedReplacement_allowsReadyAndNormalDownloadStillRefusesReady() = runTest {
        val localModels = FakeLocalModelRepository(listOf(wizardStoredModel("pending-model")))
        val coordinator = LocalModelReplacementCoordinator()
        val viewModel = replacementViewModel(coordinator = coordinator, localModels = localModels)
        val entry = wizardCatalogEntry("pending-model")
        val target = ResolvedModelDownload(
            fileName = "pending-npu.litertlm",
            downloadUrl = "https://example/pending-npu.litertlm",
            commitHash = "npu",
            sizeInBytes = 3_000_000L
        )

        LocalModelDownloadActions(
            localModelRepository = localModels,
            gatedDownloadCoordinator = wizardGatedCoordinator(),
            huggingFaceTokenStore = HuggingFaceTokenStore(MapSecretVault()),
            downloadGuards = FakeLocalDownloadGuards(),
            huggingFaceAuthClient = FakeHuggingFaceAuthClient(),
            scope = CoroutineScope(Dispatchers.Unconfined)
        ).requestDownload(entry, LocalModelItemStatus.READY)

        assertTrue(localModels.startDownloadCalls.isEmpty())

        coordinator.request(entry, target, "gpu")
        viewModel.confirmReplacement()

        assertEquals(listOf("pending-model"), localModels.startDownloadCalls)
        assertEquals(listOf(target), localModels.startDownloadResolved)
        assertTrue(coordinator.pending.value.isEmpty())
    }
}

private fun replacementViewModel(
    coordinator: LocalModelReplacementCoordinator,
    localModels: FakeLocalModelRepository,
    tokenStore: HuggingFaceTokenStore = HuggingFaceTokenStore(MapSecretVault()),
    gatedCoordinator: GatedDownloadCoordinator = wizardGatedCoordinator(
        tokenStore = tokenStore
    ),
    guards: LocalDownloadGuards = FakeLocalDownloadGuards(),
    authClient: HuggingFaceAuthClient = FakeHuggingFaceAuthClient()
) = LocalModelReplacementViewModel(
    coordinator = coordinator,
    localModelRepository = localModels,
    gatedDownloadCoordinator = gatedCoordinator,
    huggingFaceTokenStore = tokenStore,
    downloadGuards = guards,
    huggingFaceAuthClient = authClient,
    deviceSocModel = "",
    localRuntime = FakeLocalRuntime()
)
