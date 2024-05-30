package dev.chungjungsoo.gptmobile.presentation.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(private val settingRepository: SettingRepository) : ViewModel() {

    sealed class SplashEvent {
        data object OpenIntro : SplashEvent()
        data object OpenHome : SplashEvent()
    }

    private val _isReady: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _event: MutableSharedFlow<SplashEvent> = MutableSharedFlow()
    val event: SharedFlow<SplashEvent> = _event.asSharedFlow()

    init {
        viewModelScope.launch {
            val enabledPlatforms = fetchPlatformSettings()

            if (enabledPlatforms.isEmpty() ||
                enabledPlatforms.any { it.token == null || it.model == null }
            ) {
                // Initialize
                sendSplashEvent(SplashEvent.OpenIntro)
            } else {
                sendSplashEvent(SplashEvent.OpenHome)
            }

            setAsReady()
        }
    }

    private suspend fun fetchPlatformSettings() = settingRepository.fetchPlatforms().filter { it.enabled }

    private suspend fun sendSplashEvent(event: SplashEvent) {
        _event.emit(event)
    }

    private fun setAsReady() {
        _isReady.update { true }
    }
}
