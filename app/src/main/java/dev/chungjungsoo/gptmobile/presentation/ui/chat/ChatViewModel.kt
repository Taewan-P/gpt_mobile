package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.entity.Message
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel @Inject constructor(private val chatRepository: ChatRepository) : ViewModel() {

    private val _messages = MutableStateFlow(listOf<Message>())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _question = MutableStateFlow("")
    val question: StateFlow<String> = _question.asStateFlow()

    fun updateQuestion(q: String) {
        _question.update { q }
    }

    fun fetchMessages(chatRoomId: Int) {
        viewModelScope.launch {
            _messages.update { chatRepository.fetchMessages(chatRoomId) }
        }
    }
}
