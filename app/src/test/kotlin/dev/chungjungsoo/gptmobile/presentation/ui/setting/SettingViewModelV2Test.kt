package dev.chungjungsoo.gptmobile.presentation.ui.setting

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.repository.SecretMigrationError
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingViewModelV2Test {
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun addPlatformNavigatesOnlyAfterPersistence() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>()
        val repository = AddPlatformRepository(gate = gate)
        val viewModel = SettingViewModelV2(repository)
        var didNavigate = false

        viewModel.addPlatform(testPlatform()) { didNavigate = true }
        runCurrent()

        assertTrue(viewModel.addPlatformSaveState.value.isSaving)
        assertFalse(didNavigate)
        assertNull(viewModel.addPlatformSaveState.value.errorMessage)

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.addPlatformSaveState.value.isSaving)
        assertTrue(didNavigate)
        assertEquals(testPlatform(), repository.addedPlatform)
    }

    @Test
    fun addPlatformFailureKeepsScreenAndExposesError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = AddPlatformRepository(failure = IllegalStateException("Save failed"))
        val viewModel = SettingViewModelV2(repository)
        var didNavigate = false

        viewModel.addPlatform(testPlatform()) { didNavigate = true }
        advanceUntilIdle()

        assertFalse(viewModel.addPlatformSaveState.value.isSaving)
        assertFalse(didNavigate)
        assertEquals("Save failed", viewModel.addPlatformSaveState.value.errorMessage)
    }

    @Test
    fun addPlatformSuccessCallbackObservesRefreshedPlatformState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = AddPlatformRepository()
        val viewModel = SettingViewModelV2(repository)
        var observedDuringCallback: List<PlatformV2> = emptyList()

        viewModel.addPlatform(testPlatform()) {
            observedDuringCallback = viewModel.platformState.value
        }
        advanceUntilIdle()

        val refreshed = listOf(testPlatform().copy(id = 42))
        assertEquals(refreshed, observedDuringCallback)
        assertEquals(refreshed, viewModel.platformState.value)
    }

    @Test
    fun addPlatformRefreshFailureStillInvokesSuccess() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = AddPlatformRepository(refreshFailure = IllegalStateException("Refresh failed"))
        val viewModel = SettingViewModelV2(repository)
        var didNavigate = false

        viewModel.addPlatform(testPlatform()) { didNavigate = true }
        advanceUntilIdle()

        assertFalse(viewModel.addPlatformSaveState.value.isSaving)
        assertTrue(didNavigate)
        assertNull(viewModel.addPlatformSaveState.value.errorMessage)
    }
}

private class AddPlatformRepository(
    private val gate: CompletableDeferred<Unit>? = null,
    private val failure: Throwable? = null,
    private val refreshFailure: Throwable? = null
) : SettingRepository {
    var addedPlatform: PlatformV2? = null
    private val platforms = mutableListOf<PlatformV2>()

    override suspend fun fetchPlatforms(): List<Platform> = emptyList()
    override suspend fun fetchPlatformV2s(): List<PlatformV2> {
        if (addedPlatform != null) {
            refreshFailure?.let { throw it }
        }
        return platforms.toList()
    }

    override suspend fun fetchThemes(): ThemeSetting = ThemeSetting()
    override suspend fun migrateToPlatformV2() = Unit
    override suspend fun migrateSecrets(): List<SecretMigrationError> = emptyList()
    override suspend fun updatePlatforms(platforms: List<Platform>) = Unit
    override suspend fun updateThemes(themeSetting: ThemeSetting) = Unit

    override suspend fun addPlatformV2(platform: PlatformV2) {
        gate?.await()
        failure?.let { throw it }
        addedPlatform = platform
        platforms += platform.copy(id = 42)
    }

    override suspend fun updatePlatformV2(platform: PlatformV2) = Unit
    override suspend fun deletePlatformV2(platform: PlatformV2) = Unit
    override suspend fun getPlatformV2ById(id: Int): PlatformV2? = null
}

private fun testPlatform() = PlatformV2(
    uid = "platform-1",
    name = "OpenAI",
    compatibleType = ClientType.OPENAI,
    enabled = true,
    apiUrl = "https://api.openai.com/v1",
    model = "gpt"
)
