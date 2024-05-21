package dev.chungjungsoo.gptmobile.presentation.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.datastore.TokenDataSource
import dev.chungjungsoo.gptmobile.data.dto.ApiType
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SetupViewModel @Inject constructor(private val tokenDataSource: TokenDataSource) : ViewModel() {

    data class Platform(
        val name: ApiType,
        val enabled: Boolean = false,
        val token: String? = null
    )

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

    fun saveCheckedState() {
        _platformState.value.forEach { platform ->
            viewModelScope.launch {
                tokenDataSource.updateStatus(platform.name, platform.enabled)
            }
        }
    }
}
