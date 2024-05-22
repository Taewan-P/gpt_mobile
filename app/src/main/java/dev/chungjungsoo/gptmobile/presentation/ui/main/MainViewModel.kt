package dev.chungjungsoo.gptmobile.presentation.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.datastore.TokenDataSource
import dev.chungjungsoo.gptmobile.data.dto.ApiType
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(tokenDataSource: TokenDataSource) : ViewModel() {
    sealed class SplashEvent {
        data object OpenIntro : SplashEvent()
        data object OpenHome : SplashEvent()
    }

    private val isStatusNotInitialized: (Map.Entry<ApiType, Boolean?>) -> Boolean = { it.value == null }
    private val isTokenNotInitialized: (Map.Entry<ApiType, String?>) -> Boolean = { it.value == null }
    private val isModelNotInitialized: (Map.Entry<ApiType, String?>) -> Boolean = { it.value == null }

    private val _isReady: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _event: MutableSharedFlow<SplashEvent> = MutableSharedFlow()
    val event: SharedFlow<SplashEvent> = _event.asSharedFlow()

    init {
        viewModelScope.launch {
            val status = mapOf(
                ApiType.OPENAI to tokenDataSource.getStatus(ApiType.OPENAI),
                ApiType.ANTHROPIC to tokenDataSource.getStatus(ApiType.ANTHROPIC),
                ApiType.GOOGLE to tokenDataSource.getStatus(ApiType.GOOGLE)
            )
            val tokens = mapOf(
                ApiType.OPENAI to tokenDataSource.getToken(ApiType.OPENAI),
                ApiType.ANTHROPIC to tokenDataSource.getToken(ApiType.ANTHROPIC),
                ApiType.GOOGLE to tokenDataSource.getToken(ApiType.GOOGLE)
            )
            val models = mapOf(
                ApiType.OPENAI to tokenDataSource.getModel(ApiType.OPENAI),
                ApiType.ANTHROPIC to tokenDataSource.getModel(ApiType.ANTHROPIC),
                ApiType.GOOGLE to tokenDataSource.getModel(ApiType.GOOGLE)
            )

            if (status.all(isStatusNotInitialized) ||
                tokens.all(isTokenNotInitialized) ||
                models.all(isModelNotInitialized)
            ) {
                // Initialize
                sendSplashEvent(SplashEvent.OpenIntro)
            } else {
                sendSplashEvent(SplashEvent.OpenHome)
            }

            setAsReady()
        }
    }

    private suspend fun sendSplashEvent(event: SplashEvent) {
        _event.emit(event)
    }

    private fun setAsReady() {
        _isReady.value = true
    }
}
