package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.presentation.common.ModelConstants
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val settingRepository: SettingRepository
) : ViewModel() {

    private val _platformState = MutableStateFlow(listOf<Platform>())
    val platformState: StateFlow<List<Platform>> = _platformState.asStateFlow()

    private val _isThemeDialogOpen = MutableStateFlow(false)
    val isThemeDialogOpen: StateFlow<Boolean> = _isThemeDialogOpen.asStateFlow()

    init {
        fetchPlatformStatus()
    }

    fun fetchPlatformStatus() {
        viewModelScope.launch {
            val platforms = settingRepository.fetchPlatforms()
            _platformState.update { platforms }
        }
    }

    fun toggleAPI(apiType: ApiType) {
        val index = _platformState.value.indexOfFirst { it.name == apiType }

        if (index >= 0) {
            _platformState.update {
                it.mapIndexed { i, p ->
                    if (index == i) {
                        p.copy(enabled = p.enabled.not())
                    } else {
                        p
                    }
                }
            }
            viewModelScope.launch {
                settingRepository.updatePlatforms(_platformState.value)
            }
        }
    }

    fun updateToken(apiType: ApiType, token: String) {
        val index = _platformState.value.indexOfFirst { it.name == apiType }

        if (index >= 0) {
            _platformState.update {
                it.mapIndexed { i, p ->
                    if (index == i && token.isNotBlank()) {
                        p.copy(token = token)
                    } else {
                        p
                    }
                }
            }
            viewModelScope.launch {
                settingRepository.updatePlatforms(_platformState.value)
            }
        }
    }

    fun updateModel(apiType: ApiType, model: String) {
        val index = _platformState.value.indexOfFirst { it.name == apiType }
        val models = when (apiType) {
            ApiType.OPENAI -> ModelConstants.openaiModels
            ApiType.ANTHROPIC -> ModelConstants.anthropicModels
            ApiType.GOOGLE -> ModelConstants.googleModels
        }

        if (index >= 0) {
            _platformState.update {
                it.mapIndexed { i, p ->
                    if (index == i && model in models) {
                        p.copy(model = model)
                    } else {
                        p
                    }
                }
            }
            viewModelScope.launch {
                settingRepository.updatePlatforms(_platformState.value)
            }
        }
    }

    fun openThemeDialog() {
        _isThemeDialogOpen.update { true }
    }

    fun closeThemeDialog() {
        _isThemeDialogOpen.update { false }
    }
}
