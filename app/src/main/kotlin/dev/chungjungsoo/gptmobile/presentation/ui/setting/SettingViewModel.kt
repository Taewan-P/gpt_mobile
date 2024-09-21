package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
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

    private val _dialogState = MutableStateFlow(DialogState())
    val dialogState: StateFlow<DialogState> = _dialogState.asStateFlow()

    init {
        fetchPlatformStatus()
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

    fun savePlatformSettings() {
        viewModelScope.launch {
            settingRepository.updatePlatforms(_platformState.value)
        }
    }

    fun updateURL(apiType: ApiType, url: String) {
        val index = _platformState.value.indexOfFirst { it.name == apiType }

        if (index >= 0) {
            _platformState.update {
                it.mapIndexed { i, p ->
                    if (index == i && url.isNotBlank()) {
                        p.copy(apiUrl = url)
                    } else {
                        p
                    }
                }
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
        }
    }

    fun updateModel(apiType: ApiType, model: String) {
        val index = _platformState.value.indexOfFirst { it.name == apiType }
        val models = when (apiType) {
            ApiType.OPENAI -> ModelConstants.openaiModels
            ApiType.ANTHROPIC -> ModelConstants.anthropicModels
            ApiType.GOOGLE -> ModelConstants.googleModels
            ApiType.OLLAMA -> ModelConstants.ollamaModels
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
        }
    }

    fun updateTemperature(apiType: ApiType, temperature: Float) {
        val index = _platformState.value.indexOfFirst { it.name == apiType }
        val modifiedTemperature = when (apiType) {
            ApiType.ANTHROPIC -> temperature.coerceIn(0F, 1F)
            else -> temperature.coerceIn(0F, 2F)
        }

        if (index >= 0) {
            _platformState.update {
                it.mapIndexed { i, p ->
                    if (index == i) {
                        p.copy(temperature = modifiedTemperature)
                    } else {
                        p
                    }
                }
            }
        }
    }

    fun updateTopP(apiType: ApiType, topP: Float) {
        val index = _platformState.value.indexOfFirst { it.name == apiType }
        val modifiedTopP = topP.coerceIn(0.1F, 1F)

        if (index >= 0) {
            _platformState.update {
                it.mapIndexed { i, p ->
                    if (index == i) {
                        p.copy(topP = modifiedTopP)
                    } else {
                        p
                    }
                }
            }
        }
    }

    fun updateSystemPrompt(apiType: ApiType, prompt: String) {
        val index = _platformState.value.indexOfFirst { it.name == apiType }

        if (index >= 0) {
            _platformState.update {
                it.mapIndexed { i, p ->
                    if (index == i && prompt.isNotBlank()) {
                        p.copy(systemPrompt = prompt)
                    } else {
                        p
                    }
                }
            }
        }
    }

    fun openThemeDialog() = _dialogState.update { it.copy(isThemeDialogOpen = true) }

    fun openApiUrlDialog() = _dialogState.update { it.copy(isApiUrlDialogOpen = true) }

    fun openApiTokenDialog() = _dialogState.update { it.copy(isApiTokenDialogOpen = true) }

    fun openApiModelDialog() = _dialogState.update { it.copy(isApiModelDialogOpen = true) }

    fun openTemperatureDialog() = _dialogState.update { it.copy(isTemperatureDialogOpen = true) }

    fun openTopPDialog() = _dialogState.update { it.copy(isTopPDialogOpen = true) }

    fun openSystemPromptDialog() = _dialogState.update { it.copy(isSystemPromptDialogOpen = true) }

    fun closeThemeDialog() = _dialogState.update { it.copy(isThemeDialogOpen = false) }

    fun closeApiUrlDialog() = _dialogState.update { it.copy(isApiUrlDialogOpen = false) }

    fun closeApiTokenDialog() = _dialogState.update { it.copy(isApiTokenDialogOpen = false) }

    fun closeApiModelDialog() = _dialogState.update { it.copy(isApiModelDialogOpen = false) }

    fun closeTemperatureDialog() = _dialogState.update { it.copy(isTemperatureDialogOpen = false) }

    fun closeTopPDialog() = _dialogState.update { it.copy(isTopPDialogOpen = false) }

    fun closeSystemPromptDialog() = _dialogState.update { it.copy(isSystemPromptDialogOpen = false) }

    private fun fetchPlatformStatus() {
        viewModelScope.launch {
            val platforms = settingRepository.fetchPlatforms()
            _platformState.update { platforms }
        }
    }

    data class DialogState(
        val isThemeDialogOpen: Boolean = false,
        val isApiUrlDialogOpen: Boolean = false,
        val isApiTokenDialogOpen: Boolean = false,
        val isApiModelDialogOpen: Boolean = false,
        val isTemperatureDialogOpen: Boolean = false,
        val isTopPDialogOpen: Boolean = false,
        val isSystemPromptDialogOpen: Boolean = false
    )
}
