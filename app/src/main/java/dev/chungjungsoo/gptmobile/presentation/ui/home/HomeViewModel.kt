package dev.chungjungsoo.gptmobile.presentation.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.datastore.SettingDataSource
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val settingDataSource: SettingDataSource
) : ViewModel() {

    private val _chatList = MutableStateFlow(listOf<ChatRoom>())
    val chatList: StateFlow<List<ChatRoom>> = _chatList.asStateFlow()

    private val _platformState = MutableStateFlow(listOf<Platform>())
    val platformState: StateFlow<List<Platform>> = _platformState.asStateFlow()

    private val _showSelectModelDialog = MutableStateFlow(false)
    val showSelectModelDialog: StateFlow<Boolean> = _showSelectModelDialog.asStateFlow()

    init {
        fetchPlatformStatus()
        fetchChats()
    }

    fun createEmptyChatRoom(): ChatRoom {
        val enabledPlatforms = _platformState.value.filter { it.selected }.map { it.name }
        val chatRoom = ChatRoom(title = "Untitled Chat", enabledPlatform = enabledPlatforms)
        viewModelScope.launch {
            chatRepository.saveChat(chatRoom, listOf())
        }
        return chatRoom
    }

    fun updateCheckedState(platform: Platform) {
        val index = _platformState.value.indexOf(platform)

        if (index >= 0) {
            _platformState.update {
                it.mapIndexed { i, p ->
                    if (index == i) {
                        p.copy(selected = p.selected.not())
                    } else {
                        p
                    }
                }
            }
        }
    }

    fun openSelectModelDialog() {
        _showSelectModelDialog.update { true }
    }

    fun closeSelectModelDialog() {
        _showSelectModelDialog.update { false }
    }

    fun fetchChats() {
        viewModelScope.launch {
            _chatList.update { chatRepository.fetchChatList() }

            Log.d("chats", "${chatList.value}")
        }
    }

    fun fetchPlatformStatus() {
        viewModelScope.launch {
            val platforms = ApiType.entries.map { apiType ->
                val status = settingDataSource.getStatus(apiType)
                val token = settingDataSource.getToken(apiType)
                val model = settingDataSource.getModel(apiType)

                Platform(apiType, enabled = status ?: false, token = token, model = model)
            }
            _platformState.update { platforms }
        }
    }
}
