package dev.chungjungsoo.gptmobile.presentation.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.datastore.SettingDataSource
import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ThemeViewModel @Inject constructor(private val settingDataSource: SettingDataSource) : ViewModel() {

    private val _dynamicTheme = MutableStateFlow(DynamicTheme.OFF)
    val dynamicTheme: StateFlow<DynamicTheme> = _dynamicTheme.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    init {
        fetchThemes()
    }

    private fun fetchThemes() {
        viewModelScope.launch {
            _dynamicTheme.update {
                settingDataSource.getDynamicTheme() ?: DynamicTheme.OFF
            }

            _themeMode.update {
                settingDataSource.getThemeMode() ?: ThemeMode.SYSTEM
            }
        }
    }

    fun updateDynamicTheme(theme: DynamicTheme) {
        _dynamicTheme.update { theme }
        viewModelScope.launch {
            settingDataSource.updateDynamicTheme(theme)
        }
    }

    fun updateThemeMode(themeMode: ThemeMode) {
        _themeMode.update { themeMode }
        viewModelScope.launch {
            settingDataSource.updateThemeMode(themeMode)
        }
    }
}
