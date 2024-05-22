package dev.chungjungsoo.gptmobile.presentation.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.datastore.TokenDataSource
import dev.chungjungsoo.gptmobile.data.dto.ApiType
import dev.chungjungsoo.gptmobile.data.dto.Platform
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SetupViewModel @Inject constructor(private val tokenDataSource: TokenDataSource) : ViewModel() {

    val openaiModels = listOf("gpt-4o", "gpt-4-turbo", "gpt-4", "gpt-3.5-turbo")
    val anthropicModels = listOf("claude-3-opus-20240229", "claude-3-sonnet-20240229", "claude-3-haiku-20240307")
    val googleModels = listOf("gemini-1.5-pro-latest", "gemini-1.5-flash-latest", "gemini-1.0-pro")

    private val _platformState = MutableStateFlow(
        listOf(
            Platform(ApiType.OPENAI),
            Platform(ApiType.ANTHROPIC),
            Platform(ApiType.GOOGLE)
        )
    )
    val platformState: StateFlow<List<Platform>> = _platformState.asStateFlow()

    init {
        viewModelScope.launch {
            fetchCheckedState()
        }
    }

    private suspend fun fetchCheckedState() {
        _platformState.value = _platformState.value.map { p ->
            val status = tokenDataSource.getStatus(p.name)
            p.copy(enabled = status ?: false)
        }
    }

    fun updateCheckedState(platform: Platform) {
        val index = _platformState.value.indexOf(platform)

        if (index >= 0) {
            _platformState.value = _platformState.value.mapIndexed { i, p ->
                if (index == i) {
                    p.copy(enabled = p.enabled.not())
                } else {
                    p
                }
            }
        }
    }

    fun updateToken(platform: Platform, token: String) {
        val index = _platformState.value.indexOf(platform)

        if (index >= 0) {
            _platformState.value = _platformState.value.mapIndexed { i, p ->
                if (index == i) {
                    p.copy(token = token.ifBlank { null })
                } else {
                    p
                }
            }
        }
    }

    fun updateModel(apiType: ApiType, model: String) {
        val index = _platformState.value.indexOfFirst { it.name == apiType }
        val models = when (apiType) {
            ApiType.OPENAI -> openaiModels
            ApiType.ANTHROPIC -> anthropicModels
            ApiType.GOOGLE -> googleModels
        }

        if (index >= 0) {
            _platformState.value = _platformState.value.mapIndexed { i, p ->
                if (index == i) {
                    p.copy(model = if (model in models) model else null)
                } else {
                    p
                }
            }
        }
    }

    fun saveCheckedState() {
        _platformState.value.forEach { platform ->
            viewModelScope.launch {
                tokenDataSource.updateStatus(platform.name, platform.enabled)
            }
        }
    }

    fun saveTokenState() {
        _platformState.value.filter { it.enabled && it.token != null }.forEach { platform ->
            viewModelScope.launch {
                tokenDataSource.updateToken(platform.name, platform.token!!)
            }
        }
    }

    fun saveModelState() {
        _platformState.value.filter { it.enabled && it.token != null && it.model != null }.forEach { platform ->
            viewModelScope.launch {
                tokenDataSource.updateModel(platform.name, platform.model!!)
            }
        }
    }
}
