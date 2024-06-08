package dev.chungjungsoo.gptmobile.presentation.ui.chat

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.entity.Message
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import javax.inject.Inject
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

    private val chatRoomId: Int = checkNotNull(savedStateHandle["chatRoomId"])
    private val enabledPlatformString: String = checkNotNull(savedStateHandle["enabledPlatforms"])
    val enabledPlatformsInChat = enabledPlatformString.split(',').map { s -> ApiType.valueOf(s) }

    private val _enabledPlatformsInApp = MutableStateFlow(listOf<ApiType>())
    val enabledPlatformsInApp = _enabledPlatformsInApp.asStateFlow()

    private val _messages = MutableStateFlow(listOf<Message>())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _question = MutableStateFlow("")
    val question: StateFlow<String> = _question.asStateFlow()

    private val sampleMessages = listOf(
        Message(id = 1, chatId = 1, content = "How can I print hello world in Python?", linkedMessageId = 0, platformType = null, imageData = null),
        Message(id = 2, chatId = 1, content = "To print \"Hello, world!\" in Python, you can use the print() function. Here's an example code: \n\n```python\nprint('hello world')\n```\n\n", linkedMessageId = 0, platformType = ApiType.OPENAI, imageData = null),
        Message(id = 3, chatId = 1, content = "Here is the code: \n\n```python\nprint('hello world')\n```\n\nWhen you run this code, you should see the text \"Hello, world!\" printed to the console.", linkedMessageId = 0, platformType = ApiType.ANTHROPIC, imageData = null)
//        Message(id = 4, chatId = 1, content = "How about in Kotlin?", linkedMessageId = 0, platformType = null, imageData = null),
    )

    init {
        Log.d("ViewModel", "$chatRoomId")
        Log.d("ViewModel", "$enabledPlatformsInChat")
        fetchMessages()
        fetchEnabledPlatformsInApp()
    }

    fun updateQuestion(q: String) {
        _question.update { q }
    }

    fun fetchMessages() {
        viewModelScope.launch {
            _messages.update { chatRepository.fetchMessages(chatRoomId) }
        }
    }

    private fun fetchEnabledPlatformsInApp() {
        viewModelScope.launch {
            val enabled = settingRepository.fetchPlatforms().filter { it.enabled }.map { it.name }
            _enabledPlatformsInApp.update { enabled }
        }
    }
}
