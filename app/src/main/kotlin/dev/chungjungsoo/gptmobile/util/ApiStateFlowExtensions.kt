package dev.chungjungsoo.gptmobile.util

import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.presentation.ui.chat.ChatViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

suspend fun Flow<ApiState>.handleStates(
    messageFlow: MutableStateFlow<ChatViewModel.GroupedMessages>,
    platformIdx: Int,
    onLoadingComplete: () -> Unit
) = collect { chunk ->
    when (chunk) {
        is ApiState.Thinking -> messageFlow.addThought(platformIdx, chunk.thinkingChunk)

        is ApiState.Success -> messageFlow.addContent(platformIdx, chunk.textChunk)

        ApiState.Done -> {
            messageFlow.setTimestamp(platformIdx)
            onLoadingComplete()
        }

        is ApiState.Error -> {
            messageFlow.setErrorMessage(platformIdx, chunk.message)
            onLoadingComplete()
        }

        else -> {}
    }
}

private fun MutableStateFlow<ChatViewModel.GroupedMessages>.addThought(platformIdx: Int, thought: String) {
    update { groupedMessages ->
        val updatedMessages = groupedMessages.assistantMessages.last().toMutableList()
        updatedMessages[platformIdx] = updatedMessages[platformIdx].copy(
            thoughts = updatedMessages[platformIdx].thoughts + thought
        )
        val assistantMessages = groupedMessages.assistantMessages.toMutableList()
        assistantMessages[assistantMessages.lastIndex] = updatedMessages

        groupedMessages.copy(assistantMessages = assistantMessages)
    }
}

private fun MutableStateFlow<ChatViewModel.GroupedMessages>.addContent(platformIdx: Int, text: String) {
    update { groupedMessages ->
        val updatedMessages = groupedMessages.assistantMessages.last().toMutableList()
        updatedMessages[platformIdx] = updatedMessages[platformIdx].copy(
            content = updatedMessages[platformIdx].content + text
        )
        val assistantMessages = groupedMessages.assistantMessages.toMutableList()
        assistantMessages[assistantMessages.lastIndex] = updatedMessages

        groupedMessages.copy(assistantMessages = assistantMessages)
    }
}

private fun MutableStateFlow<ChatViewModel.GroupedMessages>.setErrorMessage(platformIdx: Int, error: String) {
    update { groupedMessages ->
        val updatedMessages = groupedMessages.assistantMessages.last().toMutableList()
        updatedMessages[platformIdx] = updatedMessages[platformIdx].copy(
            content = "Error: $error",
            createdAt = System.currentTimeMillis() / 1000
        )
        val assistantMessages = groupedMessages.assistantMessages.toMutableList()
        assistantMessages[assistantMessages.lastIndex] = updatedMessages

        groupedMessages.copy(assistantMessages = assistantMessages)
    }
}

private fun MutableStateFlow<ChatViewModel.GroupedMessages>.setTimestamp(platformIdx: Int) {
    update { groupedMessages ->
        val updatedMessages = groupedMessages.assistantMessages.last().toMutableList()
        updatedMessages[platformIdx] = updatedMessages[platformIdx].copy(
            createdAt = System.currentTimeMillis() / 1000
        )
        val assistantMessages = groupedMessages.assistantMessages.toMutableList()
        assistantMessages[assistantMessages.lastIndex] = updatedMessages

        groupedMessages.copy(assistantMessages = assistantMessages)
    }
}
