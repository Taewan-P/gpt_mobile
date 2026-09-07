package dev.chungjungsoo.gptmobile.presentation.common

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting
import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import dev.chungjungsoo.gptmobile.data.repository.SecretMigrationError
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun updateThemeModePublishesOnlyAfterPersistence() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = RecordingSettingRepository(ThemeSetting(), gate)
        val viewModel = ThemeViewModel(repository)
        advanceUntilIdle()

        viewModel.updateThemeMode(ThemeMode.DARK)
        runCurrent()
        assertEquals(ThemeMode.SYSTEM, viewModel.themeSetting.value.themeMode)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(ThemeMode.DARK, viewModel.themeSetting.value.themeMode)
        assertEquals(ThemeMode.DARK, repository.updated.single().themeMode)
    }

    @Test
    fun fetchThemesFailureFallsBackToSystemWithoutClaimingPersistedMode() = runTest {
        val repository = RecordingSettingRepository(
            initial = ThemeSetting(),
            fetchError = IllegalStateException("theme store unavailable")
        )
        val viewModel = ThemeViewModel(repository)
        advanceUntilIdle()

        assertEquals(ThemeLoadState.FALLBACK_SYSTEM, viewModel.loadState.value)
        assertEquals(ThemeMode.SYSTEM, viewModel.themeSetting.value.themeMode)
    }

    @Test
    fun fetchThemesSuccessPublishesReadyWithPersistedSetting() = runTest {
        val persisted = ThemeSetting(dynamicTheme = DynamicTheme.ON, themeMode = ThemeMode.LIGHT)
        val viewModel = ThemeViewModel(RecordingSettingRepository(persisted))
        advanceUntilIdle()

        assertEquals(ThemeLoadState.READY, viewModel.loadState.value)
        assertEquals(persisted, viewModel.themeSetting.value)
    }

    @Test
    fun updateDynamicThemePublishesOnlyAfterPersistence() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = RecordingSettingRepository(ThemeSetting(), gate)
        val viewModel = ThemeViewModel(repository)
        advanceUntilIdle()

        viewModel.updateDynamicTheme(DynamicTheme.ON)
        runCurrent()
        assertEquals(DynamicTheme.OFF, viewModel.themeSetting.value.dynamicTheme)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(DynamicTheme.ON, viewModel.themeSetting.value.dynamicTheme)
        assertEquals(DynamicTheme.ON, repository.updated.single().dynamicTheme)
    }

    @Test
    fun concurrentThemeUpdatesSerializeReadPersistPublish() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = RecordingSettingRepository(ThemeSetting(), gate)
        val viewModel = ThemeViewModel(repository)
        advanceUntilIdle()

        viewModel.updateDynamicTheme(DynamicTheme.ON)
        viewModel.updateThemeMode(ThemeMode.DARK)
        runCurrent()
        assertEquals(ThemeSetting(), viewModel.themeSetting.value)

        gate.complete(Unit)
        advanceUntilIdle()

        val expected = ThemeSetting(dynamicTheme = DynamicTheme.ON, themeMode = ThemeMode.DARK)
        assertEquals(expected, viewModel.themeSetting.value)
        assertEquals(expected, repository.updated.last())
    }
}

private class RecordingSettingRepository(
    private val initial: ThemeSetting,
    private val updateGate: CompletableDeferred<Unit>? = null,
    private val fetchError: Throwable? = null
) : SettingRepository {
    val updated = mutableListOf<ThemeSetting>()

    override suspend fun fetchPlatforms(): List<Platform> = unused()
    override suspend fun fetchPlatformV2s(): List<PlatformV2> = unused()
    override suspend fun fetchThemes(): ThemeSetting {
        fetchError?.let { throw it }
        return initial
    }
    override suspend fun migrateToPlatformV2() = unused()
    override suspend fun migrateSecrets(): List<SecretMigrationError> = unused()
    override suspend fun updatePlatforms(platforms: List<Platform>) = unused()
    override suspend fun updateThemes(themeSetting: ThemeSetting) {
        updateGate?.await()
        updated += themeSetting
    }
    override suspend fun addPlatformV2(platform: PlatformV2) = unused()
    override suspend fun updatePlatformV2(platform: PlatformV2) = unused()
    override suspend fun deletePlatformV2(platform: PlatformV2) = unused()
    override suspend fun getPlatformV2ById(id: Int): PlatformV2? = unused()

    private fun unused(): Nothing = error("unused")
}
