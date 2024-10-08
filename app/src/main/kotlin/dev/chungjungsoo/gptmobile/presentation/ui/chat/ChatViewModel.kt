package dev.chungjungsoo.gptmobile.presentation.ui.chat

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.database.entity.Message
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.data.model.ApiType
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
    private val currentTimeStamp: Long
        get() = System.currentTimeMillis() / 1000

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

    private val _ollamaLoadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val ollamaLoadingState = _ollamaLoadingState.asStateFlow()

    private val _isIdle = MutableStateFlow(true)
    val isIdle = _isIdle.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded = _isLoaded.asStateFlow()

    private val _userMessage = MutableStateFlow(Message(chatId = chatRoomId, content = "", platformType = null))
    val userMessage = _userMessage.asStateFlow()

    private val _openAIMessage = MutableStateFlow(Message(chatId = chatRoomId, content = "", platformType = ApiType.OPENAI))
    val openAIMessage = _openAIMessage.asStateFlow()

    private val _anthropicMessage = MutableStateFlow(Message(chatId = chatRoomId, content = "", platformType = ApiType.ANTHROPIC))
    val anthropicMessage = _anthropicMessage.asStateFlow()

    private val _googleMessage = MutableStateFlow(Message(chatId = chatRoomId, content = "", platformType = ApiType.GOOGLE))
    val googleMessage = _googleMessage.asStateFlow()

    private val _ollamaMessage = MutableStateFlow(Message(chatId = chatRoomId, content = "", platformType = ApiType.OLLAMA))
    val ollamaMessage = _ollamaMessage.asStateFlow()

    private val openAIFlow = MutableSharedFlow<ApiState>()
    private val anthropicFlow = MutableSharedFlow<ApiState>()
    private val googleFlow = MutableSharedFlow<ApiState>()
    private val ollamaFlow = MutableSharedFlow<ApiState>()

    init {
        Log.d("ViewModel", "$chatRoomId")
        Log.d("ViewModel", "$enabledPlatformsInChat")
        fetchChatRoom()
        viewModelScope.launch {
            fetchMessages()
        }
        fetchEnabledPlatformsInApp()
        observeFlow()
    }

    fun askQuestion() {
        Log.d("Question: ", _question.value)
        _userMessage.update { it.copy(content = _question.value, createdAt = currentTimeStamp) }
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
                else -> restoreMessageState(apiType, previousAnswers) // Restore messages that are not retrying
            }
        }

        when (message.platformType) {
            ApiType.OPENAI -> {
                _openAIMessage.update { it.copy(id = message.id, content = "", createdAt = currentTimeStamp) }
                completeOpenAIChat()
            }

            ApiType.ANTHROPIC -> {
                _anthropicMessage.update { it.copy(id = message.id, content = "", createdAt = currentTimeStamp) }
                completeAnthropicChat()
            }

            ApiType.GOOGLE -> {
                _googleMessage.update { it.copy(id = message.id, content = "", createdAt = currentTimeStamp) }
                completeGoogleChat()
            }

            ApiType.OLLAMA -> {
                _ollamaMessage.update { it.copy(id = message.id, content = "", createdAt = currentTimeStamp) }
                completeOllamaChat()
            }

            else -> {}
        }
    }

    fun updateQuestion(q: String) = _question.update { q }

    private fun addMessage(message: Message) = _messages.update { it + listOf(message) }

    private fun clearQuestionAndAnswers() {
        _userMessage.update { it.copy(id = 0, content = "") }
        _openAIMessage.update { it.copy(id = 0, content = "") }
        _anthropicMessage.update { it.copy(id = 0, content = "") }
        _googleMessage.update { it.copy(id = 0, content = "") }
        _ollamaMessage.update { it.copy(id = 0, content = "") }
    }

    private fun completeChat() {
        enabledPlatformsInChat.forEach { apiType -> updateLoadingState(apiType, LoadingState.Loading) }
        val enabledPlatforms = enabledPlatformsInChat.toSet()

        if (ApiType.OPENAI in enabledPlatforms) {
            completeOpenAIChat()
        }

        if (ApiType.ANTHROPIC in enabledPlatforms) {
            completeAnthropicChat()
        }

        if (ApiType.GOOGLE in enabledPlatforms) {
            completeGoogleChat()
        }

        if (ApiType.OLLAMA in enabledPlatforms) {
            completeOllamaChat()
        }
    }

    private fun completeAnthropicChat() {
        viewModelScope.launch {
            val chatFlow = chatRepository.completeAnthropicChat(question = _userMessage.value, history = _messages.value)
            chatFlow.collect { chunk -> anthropicFlow.emit(chunk) }
        }
    }

    private fun completeGoogleChat() {
        viewModelScope.launch {
            val chatFlow = chatRepository.completeGoogleChat(question = _userMessage.value, history = _messages.value)
            chatFlow.collect { chunk -> googleFlow.emit(chunk) }
        }
    }

    private fun completeOllamaChat() {
        viewModelScope.launch {
            val chatFlow = chatRepository.completeOllamaChat(question = _userMessage.value, history = _messages.value)
            chatFlow.collect { chunk -> ollamaFlow.emit(chunk) }
        }
    }

    private fun completeOpenAIChat() {
        viewModelScope.launch {
            val chatFlow = chatRepository.completeOpenAIChat(question = _userMessage.value, history = _messages.value)
            chatFlow.collect { chunk -> openAIFlow.emit(chunk) }
        }
    }

    private suspend fun fetchMessages() {
        // If the room isn't new
        if (chatRoomId != 0) {
            _messages.update { chatRepository.fetchMessages(chatRoomId) }
            _isLoaded.update { true } // Finish fetching
            return
        }

        // When message id should sync after saving chats
        if (chatRoom.id != 0) {
            _messages.update { chatRepository.fetchMessages(chatRoom.id) }
            return
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
                    is ApiState.Success -> _openAIMessage.update { it.copy(content = it.content + chunk.textChunk) }
                    ApiState.Done -> {
                        _openAIMessage.update { it.copy(createdAt = currentTimeStamp) }
                        updateLoadingState(ApiType.OPENAI, LoadingState.Idle)
                    }

                    is ApiState.Error -> {
                        _openAIMessage.update { it.copy(content = "Error: ${chunk.message}", createdAt = currentTimeStamp) }
                        updateLoadingState(ApiType.OPENAI, LoadingState.Idle)
                    }

                    else -> {}
                }
            }
        }

        viewModelScope.launch {
            anthropicFlow.collect { chunk ->
                when (chunk) {
                    is ApiState.Success -> _anthropicMessage.update { it.copy(content = it.content + chunk.textChunk) }
                    ApiState.Done -> {
                        _anthropicMessage.update { it.copy(createdAt = currentTimeStamp) }
                        updateLoadingState(ApiType.ANTHROPIC, LoadingState.Idle)
                    }

                    is ApiState.Error -> {
                        _anthropicMessage.update { it.copy(content = "Error: ${chunk.message}", createdAt = currentTimeStamp) }
                        updateLoadingState(ApiType.ANTHROPIC, LoadingState.Idle)
                    }

                    else -> {}
                }
            }
        }

        viewModelScope.launch {
            googleFlow.collect { chunk ->
                when (chunk) {
                    is ApiState.Success -> _googleMessage.update { it.copy(content = it.content + chunk.textChunk) }
                    ApiState.Done -> {
                        _googleMessage.update { it.copy(createdAt = currentTimeStamp) }
                        updateLoadingState(ApiType.GOOGLE, LoadingState.Idle)
                    }

                    is ApiState.Error -> {
                        _googleMessage.update { it.copy(content = "Error: ${chunk.message}", createdAt = currentTimeStamp) }
                        updateLoadingState(ApiType.GOOGLE, LoadingState.Idle)
                    }

                    else -> {}
                }
            }
        }

        viewModelScope.launch {
            ollamaFlow.collect { chunk ->
                when (chunk) {
                    is ApiState.Success -> _ollamaMessage.update { it.copy(content = it.content + chunk.textChunk) }
                    ApiState.Done -> {
                        _ollamaMessage.update { it.copy(createdAt = currentTimeStamp) }
                        updateLoadingState(ApiType.OLLAMA, LoadingState.Idle)
                    }

                    is ApiState.Error -> {
                        _ollamaMessage.update { it.copy(content = "Error: ${chunk.message}", createdAt = currentTimeStamp) }
                        updateLoadingState(ApiType.OLLAMA, LoadingState.Idle)
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
                        fetchMessages() // For syncing message ids
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
            ApiType.OPENAI -> _openAIMessage.update { message }
            ApiType.ANTHROPIC -> _anthropicMessage.update { message }
            ApiType.GOOGLE -> _googleMessage.update { message }
            ApiType.OLLAMA -> _ollamaMessage.update { message }
        }
    }

    private fun syncQuestionAndAnswers() {
        addMessage(_userMessage.value)
        val enabledPlatforms = enabledPlatformsInChat.toSet()

        if (ApiType.OPENAI in enabledPlatforms) {
            addMessage(_openAIMessage.value)
        }

        if (ApiType.ANTHROPIC in enabledPlatforms) {
            addMessage(_anthropicMessage.value)
        }

        if (ApiType.GOOGLE in enabledPlatforms) {
            addMessage(_googleMessage.value)
        }

        if (ApiType.OLLAMA in enabledPlatforms) {
            addMessage(_ollamaMessage.value)
        }
    }

    private fun updateLoadingState(apiType: ApiType, loadingState: LoadingState) {
        when (apiType) {
            ApiType.OPENAI -> _openaiLoadingState.update { loadingState }
            ApiType.ANTHROPIC -> _anthropicLoadingState.update { loadingState }
            ApiType.GOOGLE -> _googleLoadingState.update { loadingState }
            ApiType.OLLAMA -> _ollamaLoadingState.update { loadingState }
        }

        var result = true
        enabledPlatformsInChat.forEach {
            val state = when (it) {
                ApiType.OPENAI -> _openaiLoadingState
                ApiType.ANTHROPIC -> _anthropicLoadingState
                ApiType.GOOGLE -> _googleLoadingState
                ApiType.OLLAMA -> _ollamaLoadingState
            }

            result = result && (state.value is LoadingState.Idle)
        }

        _isIdle.update { result }
    }
}
