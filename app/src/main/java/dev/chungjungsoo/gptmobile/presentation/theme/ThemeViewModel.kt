package dev.chungjungsoo.gptmobile.presentation.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.datastore.SettingDataSource
import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ThemeViewModel @Inject constructor(private val settingDataSource: SettingDataSource) : ViewModel() {

    data class ThemeSetting(
        val dynamicTheme: DynamicTheme = DynamicTheme.OFF,
        val themeMode: ThemeMode = ThemeMode.SYSTEM
    )

    private val _themeSetting = MutableStateFlow(ThemeSetting())
    val themeSetting = _themeSetting.asStateFlow()

    init {
        fetchThemes()
    }

    private fun fetchThemes() {
        viewModelScope.launch {
            _themeSetting.update { setting ->
                setting.copy(
                    dynamicTheme = settingDataSource.getDynamicTheme() ?: DynamicTheme.OFF,
                    themeMode = settingDataSource.getThemeMode() ?: ThemeMode.SYSTEM
                )
            }
        }
    }

    fun updateDynamicTheme(theme: DynamicTheme) {
        _themeSetting.update { setting ->
            setting.copy(dynamicTheme = theme)
        }
        viewModelScope.launch {
            settingDataSource.updateDynamicTheme(theme)
        }
    }

    fun updateThemeMode(theme: ThemeMode) {
        _themeSetting.update { setting ->
            setting.copy(themeMode = theme)
        }
        viewModelScope.launch {
            settingDataSource.updateThemeMode(theme)
        }
    }
}
