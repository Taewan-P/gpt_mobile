package dev.chungjungsoo.gptmobile.presentation.ui.migrate

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MigrateViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {
    enum class MigrationState {
        READY,
        MIGRATING,
        MIGRATED,
        ERROR,
        BLOCKED
    }

    data class MigrationUIState(
        val platformState: MigrationState = MigrationState.READY,
        val chatState: MigrationState = MigrationState.BLOCKED,
        val numberOfPlatforms: Int = 0,
        val numberOfChats: Int = 0
    )

    private val _uiState = MutableStateFlow(MigrationUIState())
    val uiState = _uiState.asStateFlow()

    init {
        updateAvailableMigrations()
    }

    fun migratePlatform() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(platformState = MigrationState.MIGRATING) }
                settingRepository.migrateToPlatformV2()
                _uiState.update { it.copy(platformState = MigrationState.MIGRATED) }
            } catch (e: Exception) {
                _uiState.update { it.copy(platformState = MigrationState.ERROR) }
                Log.e("Migration", "Error migrating platform", e)
            }
        }
    }

    fun migrateChats() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(chatState = MigrationState.MIGRATING) }
                chatRepository.migrateToChatRoomV2MessageV2()
                _uiState.update { it.copy(chatState = MigrationState.MIGRATED) }
            } catch (e: Exception) {
                _uiState.update { it.copy(chatState = MigrationState.ERROR) }
                Log.e("Migration", "Error migrating platform", e)
            }
        }
    }

    private fun updateAvailableMigrations() {
        viewModelScope.launch {
            val numberOfPlatforms = settingRepository.fetchPlatforms().filter { it.enabled }.size
            val numberOfChats = chatRepository.fetchChatList().size
            _uiState.update {
                it.copy(
                    numberOfPlatforms = numberOfPlatforms,
                    numberOfChats = numberOfChats
                )
            }
        }
    }
}
