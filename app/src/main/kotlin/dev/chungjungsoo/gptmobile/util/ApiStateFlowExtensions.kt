package dev.chungjungsoo.gptmobile.util

import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.presentation.ui.chat.ChatViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

private const val STREAM_PUBLISH_INTERVAL_MILLIS = 50L

suspend fun Flow<ApiState>.handleStates(
    messageFlow: MutableStateFlow<ChatViewModel.GroupedMessages>,
    platformIdx: Int,
    onLoadingComplete: () -> Unit,
    nanoTimeProvider: () -> Long = System::nanoTime
) {
    val buffer = StreamingMessageBuffer(nanoTimeProvider = nanoTimeProvider)
    var isCompletedSuccessfully = false
    var terminalError: String? = null

    try {
        collect { chunk ->
            when (chunk) {
                is ApiState.Thinking -> {
                    buffer.appendThought(chunk.thinkingChunk)
                    buffer.publishIfDue(messageFlow, platformIdx)
                }

                is ApiState.Success -> {
                    buffer.appendContent(chunk.textChunk)
                    buffer.publishIfDue(messageFlow, platformIdx)
                }

                ApiState.Done -> {
                    isCompletedSuccessfully = true
                }

                is ApiState.Error -> {
                    terminalError = chunk.message
                }

                else -> {}
            }
        }
    } finally {
        buffer.flush(messageFlow, platformIdx)
        when {
            terminalError != null -> messageFlow.setErrorMessage(platformIdx, terminalError)
            isCompletedSuccessfully -> messageFlow.setTimestamp(platformIdx)
        }
        onLoadingComplete()
    }
}

private class StreamingMessageBuffer(
    private val nanoTimeProvider: () -> Long
) {
    private val thoughts = StringBuilder()
    private val content = StringBuilder()
    private var lastPublishedAtNanos = 0L
    private var publishedThoughtLength = 0
    private var publishedContentLength = 0

    fun appendThought(chunk: String) {
        if (chunk.isNotEmpty()) {
            thoughts.append(chunk)
        }
    }

    fun appendContent(chunk: String) {
        if (chunk.isNotEmpty()) {
            content.append(chunk)
        }
    }

    fun publishIfDue(
        messageFlow: MutableStateFlow<ChatViewModel.GroupedMessages>,
        platformIdx: Int
    ) {
        if (!hasPendingChanges()) return

        val now = nanoTimeProvider()
        if (lastPublishedAtNanos == 0L ||
            now - lastPublishedAtNanos >= STREAM_PUBLISH_INTERVAL_MILLIS * 1_000_000
        ) {
            publish(messageFlow, platformIdx, now)
        }
    }

    fun flush(
        messageFlow: MutableStateFlow<ChatViewModel.GroupedMessages>,
        platformIdx: Int
    ) {
        if (!hasPendingChanges()) return
        publish(messageFlow, platformIdx, nanoTimeProvider())
    }

    private fun publish(
        messageFlow: MutableStateFlow<ChatViewModel.GroupedMessages>,
        platformIdx: Int,
        publishedAtNanos: Long
    ) {
        messageFlow.setBufferedText(
            platformIdx = platformIdx,
            content = content.toString(),
            thoughts = thoughts.toString()
        )
        publishedContentLength = content.length
        publishedThoughtLength = thoughts.length
        lastPublishedAtNanos = publishedAtNanos
    }

    private fun hasPendingChanges(): Boolean = content.length != publishedContentLength || thoughts.length != publishedThoughtLength
}

private fun MutableStateFlow<ChatViewModel.GroupedMessages>.setBufferedText(
    platformIdx: Int,
    content: String,
    thoughts: String
) {
    update { groupedMessages ->
        val updatedMessages = groupedMessages.assistantMessages.last().toMutableList()
        val currentMessage = updatedMessages[platformIdx]
        if (currentMessage.content == content && currentMessage.thoughts == thoughts) {
            return@update groupedMessages
        }
        updatedMessages[platformIdx] = currentMessage.copy(
            content = content,
            thoughts = thoughts
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
            content = buildAssistantErrorContent(updatedMessages[platformIdx].content, error),
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
