package dev.chungjungsoo.gptmobile.presentation.ui.chat

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.database.entity.Message
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.network.ApiState
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val settingRepository: SettingRepository
) : ViewModel() {

    sealed class LoadingState {
        data object Idle : LoadingState()
        data object Loading : LoadingState()
    }

    private val chatRoomId: Int = checkNotNull(savedStateHandle["chatRoomId"])
    private val enabledPlatformString: String = checkNotNull(savedStateHandle["enabledPlatforms"])
    val enabledPlatformsInChat = enabledPlatformString.split(',').map { s -> ApiType.valueOf(s) }
    private lateinit var chatRoom: ChatRoom

    private val _enabledPlatformsInApp = MutableStateFlow(listOf<ApiType>())
    val enabledPlatformsInApp = _enabledPlatformsInApp.asStateFlow()

    private val _messages = MutableStateFlow(listOf<Message>())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _question = MutableStateFlow("")
    val question: StateFlow<String> = _question.asStateFlow()

    private val _openaiLoadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val openaiLoadingState = _openaiLoadingState.asStateFlow()

    private val _anthropicLoadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val anthropicLoadingState = _anthropicLoadingState.asStateFlow()

    private val _googleLoadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val googleLoadingState = _googleLoadingState.asStateFlow()

    private val _isIdle = MutableStateFlow(true)
    val isIdle = _isIdle.asStateFlow()

    private val _userMessage = MutableStateFlow(Message(chatId = chatRoomId, content = "", platformType = null))
    val userMessage = _userMessage.asStateFlow()

    private val _openAIMessage = MutableStateFlow(Message(chatId = chatRoomId, content = "", platformType = ApiType.OPENAI))
    val openAIMessage = _openAIMessage.asStateFlow()

    private val _googleMessage = MutableStateFlow(Message(chatId = chatRoomId, content = "", platformType = ApiType.GOOGLE))
    val googleMessage = _googleMessage.asStateFlow()

    private val openAIFlow = MutableSharedFlow<ApiState>()
    private val googleFlow = MutableSharedFlow<ApiState>()

    init {
        Log.d("ViewModel", "$chatRoomId")
        Log.d("ViewModel", "$enabledPlatformsInChat")
        fetchChatRoom()
        fetchMessages()
        fetchEnabledPlatformsInApp()
        observeFlow()
    }

    fun askQuestion() {
        Log.d("Question: ", _question.value)
        _userMessage.update { it.copy(content = _question.value) }
        _question.update { "" }
        completeChat()
    }

    fun retryQuestion(message: Message) {
        val latestQuestionIndex = _messages.value.indexOfLast { it.platformType == null }

        if (latestQuestionIndex != -1) {
            _userMessage.update { _messages.value[latestQuestionIndex] }
        }

        val previousAnswers = enabledPlatformsInChat.mapNotNull { apiType -> _messages.value.lastOrNull { it.platformType == apiType } }
        _messages.update {
            if (latestQuestionIndex != -1) {
                it - setOf(_messages.value[latestQuestionIndex]) - previousAnswers.toSet()
            } else {
                it - previousAnswers.toSet()
            }
        }

        message.platformType?.let { updateLoadingState(it, LoadingState.Loading) }
        enabledPlatformsInChat.forEach { apiType ->
            when (apiType) {
                message.platformType -> {}
                else -> restoreMessageState(apiType, previousAnswers)
            }
        }

        when (message.platformType) {
            ApiType.OPENAI -> {
                _openAIMessage.update { it.copy(content = "") }
                completeOpenAIChat()
            }

            ApiType.ANTHROPIC -> TODO()
            ApiType.GOOGLE -> {
                _googleMessage.update { it.copy(content = "") }
                completeGoogleChat()
            }

            else -> {}
        }
    }

    fun updateQuestion(q: String) = _question.update { q }

    private fun addMessage(message: Message) = _messages.update { it + listOf(message) }

    private fun clearQuestionAndAnswers() {
        _userMessage.update { it.copy(content = "") }
        _openAIMessage.update { it.copy(content = "") }
        _googleMessage.update { it.copy(content = "") }
    }

    private fun completeChat() {
        enabledPlatformsInChat.forEach { apiType -> updateLoadingState(apiType, LoadingState.Loading) }

        if (ApiType.OPENAI in enabledPlatformsInChat.toSet()) {
            completeOpenAIChat()
        }

        if (ApiType.GOOGLE in enabledPlatformsInChat.toSet()) {
            completeGoogleChat()
        }
    }

    private fun completeGoogleChat() {
        viewModelScope.launch {
            val chatFlow = chatRepository.completeGoogleChat(question = _userMessage.value, history = messages.value)
            chatFlow.collect { chunk -> googleFlow.emit(chunk) }
        }
    }

    private fun completeOpenAIChat() {
        viewModelScope.launch {
            val chatFlow = chatRepository.completeOpenAIChat(question = _userMessage.value, history = messages.value)
            chatFlow.collect { chunk -> openAIFlow.emit(chunk) }
        }
    }

    private fun fetchMessages() {
        viewModelScope.launch {
            if (chatRoomId != 0) {
                _messages.update { chatRepository.fetchMessages(chatRoomId) }
            }
        }
    }

    private fun fetchChatRoom() {
        viewModelScope.launch {
            chatRoom = if (chatRoomId == 0) {
                ChatRoom(title = "Untitled Chat", enabledPlatform = enabledPlatformsInChat)
            } else {
                chatRepository.fetchChatList().first { it.id == chatRoomId }
            }
            Log.d("ViewModel", "chatroom: $chatRoom")
        }
    }

    private fun fetchEnabledPlatformsInApp() {
        viewModelScope.launch {
            val enabled = settingRepository.fetchPlatforms().filter { it.enabled }.map { it.name }
            _enabledPlatformsInApp.update { enabled }
        }
    }

    private fun observeFlow() {
        viewModelScope.launch {
            openAIFlow.collect { chunk ->
                when (chunk) {
                    is ApiState.Success -> _openAIMessage.update { it.copy(content = it.content + (chunk.textChunk)) }
                    ApiState.Done -> updateLoadingState(ApiType.OPENAI, LoadingState.Idle)
                    is ApiState.Error -> {
                        _openAIMessage.update { it.copy(content = "Error: ${chunk.message}") }
                        updateLoadingState(ApiType.OPENAI, LoadingState.Idle)
                    }

                    else -> {}
                }
            }
        }

        viewModelScope.launch {
            googleFlow.collect { chunk ->
                when (chunk) {
                    is ApiState.Success -> _googleMessage.update { message -> message.copy(content = message.content + chunk.textChunk) }
                    ApiState.Done -> updateLoadingState(ApiType.GOOGLE, LoadingState.Idle)
                    is ApiState.Error -> {
                        _googleMessage.update { it.copy(content = "Error: ${chunk.message}") }
                        updateLoadingState(ApiType.GOOGLE, LoadingState.Idle)
                    }

                    else -> {}
                }
            }
        }

        viewModelScope.launch {
            _isIdle.collect { status ->
                if (status) {
                    Log.d("status", "val: ${_userMessage.value}")
                    if (::chatRoom.isInitialized && _userMessage.value.content.isNotBlank()) {
                        syncQuestionAndAnswers()
                        Log.d("message", "${_messages.value}")
                        chatRoom = chatRepository.saveChat(chatRoom, _messages.value)
                    }
                    clearQuestionAndAnswers()
                }
            }
        }
    }

    private fun restoreMessageState(apiType: ApiType, previousAnswers: List<Message>) {
        val message = previousAnswers.firstOrNull { it.platformType == apiType }

        if (message == null) return

        when (apiType) {
            ApiType.OPENAI -> _openAIMessage.update { it.copy(content = message.content) }
            ApiType.ANTHROPIC -> TODO()
            ApiType.GOOGLE -> _googleMessage.update { it.copy(content = message.content) }
        }
    }

    private fun syncQuestionAndAnswers() {
        addMessage(_userMessage.value)

        if (ApiType.OPENAI in enabledPlatformsInChat.toSet()) {
            addMessage(_openAIMessage.value)
        }

        if (ApiType.GOOGLE in enabledPlatformsInChat.toSet()) {
            addMessage(_googleMessage.value)
        }
    }

    private fun updateLoadingState(apiType: ApiType, loadingState: LoadingState) {
        when (apiType) {
            ApiType.OPENAI -> _openaiLoadingState.update { loadingState }
            ApiType.ANTHROPIC -> _anthropicLoadingState.update { loadingState }
            ApiType.GOOGLE -> _googleLoadingState.update { loadingState }
        }

        var result = true
        enabledPlatformsInChat.forEach {
            val state = when (it) {
                ApiType.OPENAI -> _openaiLoadingState
                ApiType.ANTHROPIC -> _anthropicLoadingState
                ApiType.GOOGLE -> _googleLoadingState
            }

            result = result && (state.value is LoadingState.Idle)
        }

        _isIdle.update { result }
    }
}
