package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStatus
import dev.chungjungsoo.gptmobile.data.repository.FakeLocalModelRepository
import dev.chungjungsoo.gptmobile.presentation.ui.setup.FakeLocalDownloadGuards
import dev.chungjungsoo.gptmobile.presentation.ui.setup.addPlatformViewModel
import dev.chungjungsoo.gptmobile.presentation.ui.setup.wizardCatalogEntry
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddPlatformViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `add platform lists the full catalog with downloaded and undownloaded status`() = runTest {
        val viewModel = addPlatformViewModel(
            localModels = FakeLocalModelRepository(listOf(wizardStoredModel("ready-model")))
        )

        val items = viewModel.catalogLocalModels.value
        assertEquals(listOf("ready-model", "pending-model", "gated-model"), items.map { it.entry.id })
        assertEquals(8, items.first { it.entry.id == "pending-model" }.entry.minRamGb)
        assertEquals(5_000_000L, items.first { it.entry.id == "pending-model" }.entry.sizeInBytes)
        assertEquals(LocalModelItemStatus.READY, items.first { it.entry.id == "ready-model" }.status)
        assertEquals(LocalModelItemStatus.NOT_DOWNLOADED, items.first { it.entry.id == "pending-model" }.status)
    }

    @Test
    fun `selecting an undownloaded model on a metered connection starts download once after confirm`() = runTest {
        val localModels = FakeLocalModelRepository()
        val viewModel = addPlatformViewModel(
            localModels = localModels,
            guards = FakeLocalDownloadGuards(metered = true)
        )

        viewModel.selectLocalModel("pending-model")

        assertEquals("pending-model", viewModel.selectedCatalogEntryId.value)
        val dialog = viewModel.localModelDownloadState.value.dialog
        assertTrue(dialog is LocalModelsDialog.MeteredConfirm)
        assertTrue(localModels.startDownloadCalls.isEmpty())

        viewModel.confirmMeteredDownload()
        assertEquals(listOf("pending-model"), localModels.startDownloadCalls)

        viewModel.selectLocalModel("pending-model")
        assertEquals(listOf("pending-model"), localModels.startDownloadCalls)
    }

    @Test
    fun `selecting a READY model enables finish without any other field change`() = runTest {
        val viewModel = addPlatformViewModel(
            localModels = FakeLocalModelRepository(listOf(wizardStoredModel("ready-model")))
        )

        assertFalse(viewModel.canSave.value)

        viewModel.selectLocalModel("ready-model")

        assertTrue(viewModel.canSave.value)
        assertFalse(viewModel.isWaitingForDownload.value)
    }

    @Test
    fun `download becoming READY clears waiting without any other field change`() = runTest {
        val localModels = FakeLocalModelRepository()
        val viewModel = addPlatformViewModel(localModels = localModels)

        viewModel.selectLocalModel("pending-model")

        assertTrue(viewModel.canSave.value)
        assertTrue(viewModel.isWaitingForDownload.value)

        localModels.setModels(listOf(wizardStoredModel("pending-model", LocalModelStatus.READY)))

        assertTrue(viewModel.canSave.value)
        assertFalse(viewModel.isWaitingForDownload.value)
    }

    @Test
    fun `save is enabled once a local model is selected even while downloading`() = runTest {
        val localModels = FakeLocalModelRepository()
        val viewModel = addPlatformViewModel(localModels = localModels)

        viewModel.selectLocalModel("pending-model")

        assertTrue(viewModel.canSaveLocalModel())
        assertTrue(viewModel.isWaitingForModelDownload())
        assertFalse(viewModel.shouldEnableLocalPlatform())

        localModels.setModels(listOf(wizardStoredModel("pending-model", LocalModelStatus.READY)))

        assertTrue(viewModel.canSaveLocalModel())
        assertFalse(viewModel.isWaitingForModelDownload())
        assertTrue(viewModel.shouldEnableLocalPlatform())
    }

    @Test
    fun `clearing the add-platform ViewModel does not cancel an in-flight download`() = runTest {
        val localModels = FakeLocalModelRepository()
        val store = ViewModelStore()
        val viewModel = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return addPlatformViewModel(localModels = localModels) as T
                }
            }
        )[AddPlatformViewModel::class.java]

        viewModel.selectLocalModel("pending-model")
        assertEquals(listOf("pending-model"), localModels.startDownloadCalls)

        store.clear()

        assertTrue(localModels.cancelDownloadCalls.isEmpty())
        assertEquals(listOf("pending-model"), localModels.startDownloadCalls)
    }

    @Test
    fun `gated catalog entries route through coordinator sign-in state`() = runTest {
        val viewModel = addPlatformViewModel(gatedCoordinator = wizardGatedCoordinator(statusCode = 401))

        viewModel.selectLocalModel("gated-model")

        val dialog = viewModel.localModelDownloadState.value.dialog
        assertTrue(dialog is LocalModelsDialog.SignIn)
        assertEquals("gated-model", (dialog as LocalModelsDialog.SignIn).entry.id)
    }

    @Test
    fun `downloaded catalog entries are selectable without starting a download`() = runTest {
        val localModels = FakeLocalModelRepository(listOf(wizardStoredModel("ready-model")))
        val viewModel = addPlatformViewModel(localModels = localModels)

        viewModel.selectLocalModel("ready-model")

        assertEquals("ready-model", viewModel.selectedCatalogEntryId.value)
        assertTrue(viewModel.canSaveLocalModel())
        assertTrue(viewModel.shouldEnableLocalPlatform())
        assertTrue(localModels.startDownloadCalls.isEmpty())
        assertEquals(LocalModelsDialog.Hidden, viewModel.localModelDownloadState.value.dialog)
    }

    @Test
    fun `defaultsFor still reads sampling defaults from the selected catalog entry`() = runTest {
        val viewModel = addPlatformViewModel(
            catalog = dev.chungjungsoo.gptmobile.data.repository.FakeModelCatalogRepository(
                listOf(
                    wizardCatalogEntry("pending-model").copy(
                        defaultConfig = dev.chungjungsoo.gptmobile.data.catalog.CatalogDefaultConfig(
                            topK = 20,
                            topP = 0.8f,
                            temperature = 0.7f,
                            maxTokens = 4096
                        ),
                        supportedAccelerators = listOf("cpu")
                    )
                )
            )
        )

        val defaults = viewModel.defaultsFor("pending-model")
        assertEquals(0.7f, defaults?.temperature)
        assertEquals(0.8f, defaults?.topP)
        assertEquals(20, defaults?.topK)
        assertEquals(4096, defaults?.maxTokens)
    }
}
