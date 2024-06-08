package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.datastore.SettingDataSource
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import javax.inject.Inject

class SettingRepositoryImpl @Inject constructor(
    private val settingDataSource: SettingDataSource
) : SettingRepository {

    override suspend fun fetchPlatforms(): List<Platform> = ApiType.entries.map { apiType ->
        val status = settingDataSource.getStatus(apiType)
        val token = settingDataSource.getToken(apiType)
        val model = settingDataSource.getModel(apiType)

        Platform(apiType, enabled = status ?: false, token = token, model = model)
    }

    override suspend fun fetchThemes(): ThemeSetting = ThemeSetting(
        dynamicTheme = settingDataSource.getDynamicTheme() ?: DynamicTheme.OFF,
        themeMode = settingDataSource.getThemeMode() ?: ThemeMode.SYSTEM
    )

    override suspend fun updatePlatforms(platforms: List<Platform>) {
        platforms.forEach { platform ->
            settingDataSource.updateStatus(platform.name, platform.enabled)

            if (platform.token != null) {
                settingDataSource.updateToken(platform.name, platform.token)
            }

            if (platform.model != null) {
                settingDataSource.updateModel(platform.name, platform.model)
            }
        }
    }

    override suspend fun updateThemes(themeSetting: ThemeSetting) {
        settingDataSource.updateDynamicTheme(themeSetting.dynamicTheme)
        settingDataSource.updateThemeMode(themeSetting.themeMode)
    }
}
