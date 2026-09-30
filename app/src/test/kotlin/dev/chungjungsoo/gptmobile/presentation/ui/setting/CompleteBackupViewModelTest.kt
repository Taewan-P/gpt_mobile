package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.net.Uri
import dev.chungjungsoo.gptmobile.data.backup.BackupRestoreResult
import dev.chungjungsoo.gptmobile.data.backup.BackupStatus
import dev.chungjungsoo.gptmobile.data.backup.CompleteBackupManager
import dev.chungjungsoo.gptmobile.data.model.LocalRuntimeBackend
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class CompleteBackupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val manager = mockk<CompleteBackupManager>(relaxed = true)
    private lateinit var viewModel: SettingViewModelV2

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        val settings = mockk<SettingRepository>(relaxed = true)
        every { settings.observePlatformV2s() } returns flowOf(emptyList())
        every { settings.observeDebugMode() } returns flowOf(false)
        every { settings.observeProviderConnections() } returns flowOf(emptyList())
        every { settings.observeLocalRuntimeBackend() } returns flowOf(LocalRuntimeBackend.DEFAULT)
        every { settings.observeFeatureSettings() } returns flowOf(dev.chungjungsoo.gptmobile.data.model.AppFeatureSettings())
        coEvery { settings.getLocalRuntimeBackend() } returns LocalRuntimeBackend.DEFAULT
        every { manager.getBackupStatus() } returns BackupStatus()
        viewModel = SettingViewModelV2(settings, manager, dev.chungjungsoo.gptmobile.data.localruntime.FakeLocalRuntime())
    }

    @After
    fun cleanup() {
        Dispatchers.resetMain()
    }

    @Test
    fun pickerCancellationUnlocksActionsAndNeverRunsBackup() = runTest(dispatcher) {
        assertTrue(viewModel.prepareBackupPicker(restoring = false))
        assertFalse(viewModel.prepareBackupPicker(restoring = true))
        viewModel.backupDestinationSelected(null)
        assertTrue(viewModel.backupUi.value.canBackup)
        coVerify(exactly = 0) { manager.backup(any()) }
    }

    @Test
    fun backupAndPasswordlessRestoreUseSameManager() = runTest(dispatcher) {
        val uri = mockk<Uri>()
        coEvery { manager.backup(uri) } returns BackupRestoreResult(true, "Saved")
        coEvery { manager.requiresPassword(uri) } returns false
        coEvery { manager.restore(uri, null) } returns BackupRestoreResult(true, "Restored")

        assertTrue(viewModel.prepareBackupPicker(restoring = false))
        viewModel.backupDestinationSelected(uri)
        advanceUntilIdle()
        coVerify(exactly = 1) { manager.backup(uri) }
        assertEquals("Saved", viewModel.backupUi.value.message)

        viewModel.selectAllBackupSections()
        assertTrue(viewModel.prepareBackupPicker(restoring = true))
        viewModel.restoreSourceSelected(uri)
        advanceUntilIdle()
        assertEquals(uri, viewModel.backupUi.value.restoreUri)
        assertFalse(viewModel.backupUi.value.requiresLegacyPassword)

        viewModel.confirmRestore()
        advanceUntilIdle()
        coVerify(exactly = 1) { manager.restore(uri, null) }
        assertFalse(viewModel.backupUi.value.isBusy)
        assertEquals("Restored", viewModel.backupUi.value.message)
    }

    @Test
    fun legacyEncryptedRestorePromptsOnlyAfterFileInspection() = runTest(dispatcher) {
        viewModel.selectAllBackupSections()
        val uri = mockk<Uri>()
        coEvery { manager.requiresPassword(uri) } returns true
        coEvery { manager.restore(uri, "legacy-pass") } returns BackupRestoreResult(true, "Legacy restored")

        assertTrue(viewModel.prepareBackupPicker(restoring = true))
        viewModel.restoreSourceSelected(uri)
        advanceUntilIdle()

        assertTrue(viewModel.backupUi.value.requiresLegacyPassword)
        viewModel.confirmRestore()
        assertTrue(viewModel.backupUi.value.isError)

        viewModel.updateLegacyBackupPassword("legacy-pass")
        viewModel.confirmRestore()
        advanceUntilIdle()

        coVerify(exactly = 1) { manager.restore(uri, "legacy-pass") }
        assertEquals("Legacy restored", viewModel.backupUi.value.message)
    }
}
