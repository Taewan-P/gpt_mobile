package dev.chungjungsoo.gptmobile.presentation.ui.chat

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aallam.openai.api.chat.ChatChunk
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

    private val sampleMessages = listOf(
        Message(id = 1, chatId = 1, content = "How can I print hello world in Python?", linkedMessageId = 0, platformType = null, imageData = null),
        Message(id = 2, chatId = 1, content = "To print \"Hello, world!\" in Python, you can use the print() function. Here's an example code: \n\n```python\nprint('hello world')\n```\n\n", linkedMessageId = 0, platformType = ApiType.OPENAI, imageData = null),
        Message(id = 3, chatId = 1, content = "Here is the code: \n\n```python\nprint('hello world')\n```\n\nWhen you run this code, you should see the text \"Hello, world!\" printed to the console.", linkedMessageId = 0, platformType = ApiType.ANTHROPIC, imageData = null)
//        Message(id = 4, chatId = 1, content = "How about in Kotlin?", linkedMessageId = 0, platformType = null, imageData = null),
    )

    private val openAIFlow = MutableSharedFlow<ApiState<ChatChunk>>()

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
        _userMessage.update { _messages.value[latestQuestionIndex] }
        _messages.update { it - setOf(_messages.value[latestQuestionIndex], message) }

        message.platformType?.let { updateLoadingState(it, LoadingState.Loading) }

        when (message.platformType) {
            ApiType.OPENAI -> {
                _openAIMessage.update { it.copy(content = "") }
                completeOpenAIChat()
            }

            ApiType.ANTHROPIC -> TODO()
            ApiType.GOOGLE -> TODO()
            else -> {}
        }
    }

    fun updateQuestion(q: String) = _question.update { q }

    private fun addMessage(message: Message) = _messages.update { it + listOf(message) }

    private fun clearQuestionAndAnswers() {
        _userMessage.update { it.copy(content = "") }
        _openAIMessage.update { it.copy(content = "") }
    }

    private fun completeChat() {
        enabledPlatformsInChat.forEach { apiType -> updateLoadingState(apiType, LoadingState.Loading) }

        if (ApiType.OPENAI in enabledPlatformsInChat.toSet()) {
            completeOpenAIChat()
        }
    }

    private fun completeOpenAIChat() {
        viewModelScope.launch {
            val chatFlow = chatRepository.completeOpenAIChat(messages = messages.value + listOf(_userMessage.value))

            chatFlow.collect { chunk ->
                if (chunk is ApiState.Success) {
                    openAIFlow.emit(chunk)

                    if (chunk.data.finishReason != null) {
                        // Finished
                        openAIFlow.emit(ApiState.Done)
                    }
                } else if (chunk !is ApiState.Loading) {
                    openAIFlow.emit(chunk)
                    return@collect
                }
            }
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
                    is ApiState.Success -> _openAIMessage.update { it.copy(content = it.content + (chunk.data.delta.content ?: "")) }
                    ApiState.Done -> {
                        // TODO: Remove this when multiple api calls are implemented
                        addMessage(_userMessage.value)
                        addMessage(_openAIMessage.value)
                        Log.d("message", "${_messages.value}")
                        updateLoadingState(ApiType.OPENAI, LoadingState.Idle)
                    }

                    is ApiState.Error -> {
                        _openAIMessage.update { it.copy(content = "Error: ${chunk.message}") }
                        addMessage(_userMessage.value)
                        addMessage(_openAIMessage.value)
                        updateLoadingState(ApiType.OPENAI, LoadingState.Idle)
                    }

                    else -> {}
                }
            }
        }

        viewModelScope.launch {
            _isIdle.collect { status ->
                if (status) {
                    // TODO: Add question and answers before clearing
                    clearQuestionAndAnswers()

                    if (::chatRoom.isInitialized && _messages.value.isNotEmpty()) {
                        chatRoom = chatRepository.saveChat(chatRoom, _messages.value)
                    }
                }
            }
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
