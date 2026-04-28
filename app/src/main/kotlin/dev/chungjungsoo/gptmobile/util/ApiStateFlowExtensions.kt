package dev.chungjungsoo.gptmobile.util

import dev.chungjungsoo.gptmobile.data.database.entity.AssistantRevision
import dev.chungjungsoo.gptmobile.data.database.entity.resetActiveRevision
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.presentation.ui.chat.ChatViewModel
import dev.chungjungsoo.gptmobile.presentation.ui.chat.updateAssistantSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

private const val STREAM_PUBLISH_INTERVAL_MILLIS = 50L

suspend fun Flow<ApiState>.handleStates(
    messageFlow: MutableStateFlow<ChatViewModel.GroupedMessages>,
    turnIndex: Int,
    platformIdx: Int,
    onLoadingComplete: () -> Unit,
    nanoTimeProvider: () -> Long = System::nanoTime,
    currentTimeProvider: () -> Long = { System.currentTimeMillis() / 1000 },
    revisionToAppendOnSuccess: AssistantRevision? = null
) {
    val buffer = StreamingMessageBuffer(nanoTimeProvider = nanoTimeProvider)
    var isCompletedSuccessfully = false
    var terminalError: String? = null

    try {
        collect { chunk ->
            when (chunk) {
                is ApiState.Thinking -> {
                    buffer.appendThought(chunk.thinkingChunk)
                    buffer.publishIfDue(messageFlow, turnIndex, platformIdx)
                }

                is ApiState.Success -> {
                    buffer.appendContent(chunk.textChunk)
                    buffer.publishIfDue(messageFlow, turnIndex, platformIdx)
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
        buffer.flush(messageFlow, turnIndex, platformIdx)
        when {
            terminalError != null -> messageFlow.setErrorMessage(
                turnIndex = turnIndex,
                platformIdx = platformIdx,
                error = terminalError,
                currentTimeProvider = currentTimeProvider,
                revisionToAppend = revisionToAppendOnSuccess
            )

            isCompletedSuccessfully -> messageFlow.setTimestamp(
                turnIndex = turnIndex,
                platformIdx = platformIdx,
                currentTimeProvider = currentTimeProvider,
                revisionToAppend = revisionToAppendOnSuccess
            )
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
        turnIndex: Int,
        platformIdx: Int
    ) {
        if (!hasPendingChanges()) return

        val now = nanoTimeProvider()
        if (lastPublishedAtNanos == 0L ||
            now - lastPublishedAtNanos >= STREAM_PUBLISH_INTERVAL_MILLIS * 1_000_000
        ) {
            publish(messageFlow, turnIndex, platformIdx, now)
        }
    }

    fun flush(
        messageFlow: MutableStateFlow<ChatViewModel.GroupedMessages>,
        turnIndex: Int,
        platformIdx: Int
    ) {
        if (!hasPendingChanges()) return
        publish(messageFlow, turnIndex, platformIdx, nanoTimeProvider())
    }

    private fun publish(
        messageFlow: MutableStateFlow<ChatViewModel.GroupedMessages>,
        turnIndex: Int,
        platformIdx: Int,
        publishedAtNanos: Long
    ) {
        messageFlow.setBufferedText(
            turnIndex = turnIndex,
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
    turnIndex: Int,
    platformIdx: Int,
    content: String,
    thoughts: String
) {
    update { groupedMessages ->
        updateAssistantSlot(
            groupedMessages = groupedMessages,
            turnIndex = turnIndex,
            platformIndex = platformIdx
        ) { currentMessage ->
            if (currentMessage.content == content && currentMessage.thoughts == thoughts) {
                currentMessage
            } else {
                currentMessage.copy(
                    content = content,
                    thoughts = thoughts
                )
            }
        }
    }
}

private fun MutableStateFlow<ChatViewModel.GroupedMessages>.setErrorMessage(
    turnIndex: Int,
    platformIdx: Int,
    error: String,
    currentTimeProvider: () -> Long,
    revisionToAppend: AssistantRevision?
) {
    update { groupedMessages ->
        updateAssistantSlot(
            groupedMessages = groupedMessages,
            turnIndex = turnIndex,
            platformIndex = platformIdx
        ) { currentMessage ->
            currentMessage.copy(
                content = buildAssistantErrorContent(currentMessage.content, error),
                createdAt = currentTimeProvider(),
                revisions = revisionToAppend
                    ?.let { listOf(it) + currentMessage.revisions }
                    ?: currentMessage.revisions
            )
        }
    }
}

private fun MutableStateFlow<ChatViewModel.GroupedMessages>.setTimestamp(
    turnIndex: Int,
    platformIdx: Int,
    currentTimeProvider: () -> Long,
    revisionToAppend: AssistantRevision?
) {
    update { groupedMessages ->
        updateAssistantSlot(
            groupedMessages = groupedMessages,
            turnIndex = turnIndex,
            platformIndex = platformIdx
        ) { currentMessage ->
            currentMessage.copy(
                createdAt = currentTimeProvider(),
                revisions = revisionToAppend
                    ?.let { listOf(it) + currentMessage.revisions }
                    ?: currentMessage.revisions
            ).resetActiveRevision()
        }
    }
}
