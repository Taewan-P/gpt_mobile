package dev.chungjungsoo.gptmobile.util

import dev.chungjungsoo.gptmobile.data.database.entity.Message
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

suspend fun Flow<ApiState>.handleStates(
    messageFlow: MutableStateFlow<Message>,
    onLoadingComplete: () -> Unit
) = collect { chunk ->
    when (chunk) {
        is ApiState.Success -> messageFlow.addContent(chunk.textChunk)
        ApiState.Done -> {
            messageFlow.setTimestamp()
            onLoadingComplete()
        }

        is ApiState.Error -> {
            messageFlow.setErrorMessage(chunk.message)
            onLoadingComplete()
        }

        else -> {}
    }
}

private fun MutableStateFlow<Message>.addContent(text: String) = update { it.copy(content = it.content + text) }

private fun MutableStateFlow<Message>.setErrorMessage(error: String) = update { it.copy(content = "Error: $error", createdAt = System.currentTimeMillis() / 1000) }

private fun MutableStateFlow<Message>.setTimestamp() = update { it.copy(createdAt = System.currentTimeMillis() / 1000) }
