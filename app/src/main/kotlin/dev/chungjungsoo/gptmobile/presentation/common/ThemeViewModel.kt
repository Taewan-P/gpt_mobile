package dev.chungjungsoo.gptmobile.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.dto.ThemeSetting
import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ThemeLoadState { LOADING, READY, FALLBACK_SYSTEM }

@HiltViewModel
class ThemeViewModel @Inject constructor(private val settingRepository: SettingRepository) : ViewModel() {

    private val _themeSetting = MutableStateFlow(ThemeSetting())
    val themeSetting = _themeSetting.asStateFlow()

    private val _loadState = MutableStateFlow(ThemeLoadState.LOADING)
    val loadState = _loadState.asStateFlow()

    private val themeMutex = Mutex()

    init {
        fetchThemes()
    }

    private fun fetchThemes() {
        viewModelScope.launch {
            try {
                _themeSetting.value = settingRepository.fetchThemes()
                _loadState.value = ThemeLoadState.READY
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _themeSetting.value = ThemeSetting(themeMode = ThemeMode.SYSTEM)
                _loadState.value = ThemeLoadState.FALLBACK_SYSTEM
            }
        }
    }

    fun updateDynamicTheme(theme: DynamicTheme) {
        viewModelScope.launch {
            persistTheme { it.copy(dynamicTheme = theme) }
        }
    }

    fun updateThemeMode(theme: ThemeMode) {
        viewModelScope.launch {
            persistTheme { it.copy(themeMode = theme) }
        }
    }

    private suspend fun persistTheme(transform: (ThemeSetting) -> ThemeSetting) {
        themeMutex.withLock {
            val updated = transform(_themeSetting.value)
            settingRepository.updateThemes(updated)
            _themeSetting.value = updated
        }
    }
}
