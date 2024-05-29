package dev.chungjungsoo.gptmobile.util

import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ThemePreference {

    data class ThemeSetting(
        val dynamicTheme: DynamicTheme = DynamicTheme.OFF,
        val themeMode: ThemeMode = ThemeMode.SYSTEM
    )

//    @Inject
//    lateinit var settingDataSource: SettingDataSource

    private val _themeSetting = MutableStateFlow(ThemeSetting())
    val themeSetting = _themeSetting.asStateFlow()

    fun updateDynamicTheme(theme: DynamicTheme) {
        _themeSetting.update { setting ->
            setting.copy(dynamicTheme = theme)
        }
//        runBlocking { settingDataSource.updateDynamicTheme(theme) }
    }

    fun updateThemeMode(theme: ThemeMode) {
        _themeSetting.update { setting ->
            setting.copy(themeMode = theme)
        }
//        runBlocking { settingDataSource.updateThemeMode(theme) }
    }
}
