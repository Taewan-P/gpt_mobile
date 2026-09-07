package dev.chungjungsoo.gptmobile.util

import dev.chungjungsoo.gptmobile.data.database.entity.AssistantRevision
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItem
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItemType
import dev.chungjungsoo.gptmobile.data.database.entity.resetActiveRevision
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.presentation.ui.chat.ChatViewModel
import dev.chungjungsoo.gptmobile.presentation.ui.chat.updateAssistantSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

private const val STREAM_PUBLISH_INTERVAL_MILLIS = 50L

sealed interface ApiStateFlowOutcome {
    data object Completed : ApiStateFlowOutcome
    data class Failed(val message: String) : ApiStateFlowOutcome
    data object Incomplete : ApiStateFlowOutcome
}

suspend fun Flow<ApiState>.handleStates(
    messageFlow: MutableStateFlow<ChatViewModel.GroupedMessages>,
    turnIndex: Int,
    platformIdx: Int,
    onLoadingComplete: () -> Unit,
    onNotice: (String) -> Unit = {},
    nanoTimeProvider: () -> Long = System::nanoTime,
    currentTimeProvider: () -> Long = { System.currentTimeMillis() / 1000 },
    revisionToAppendOnSuccess: AssistantRevision? = null
): ApiStateFlowOutcome {
    try {
        val outcome = collectApiStateUpdates(
            onUpdate = { content, thoughts, timeline ->
                messageFlow.setBufferedText(turnIndex, platformIdx, content, thoughts, timeline)
            },
            onNotice = { message, _ -> onNotice(message) },
            nanoTimeProvider = nanoTimeProvider
        )
        when (outcome) {
            is ApiStateFlowOutcome.Failed -> messageFlow.setErrorMessage(
                turnIndex = turnIndex,
                platformIdx = platformIdx,
                error = outcome.message,
                currentTimeProvider = currentTimeProvider,
                revisionToAppend = revisionToAppendOnSuccess
            )

            ApiStateFlowOutcome.Completed -> messageFlow.setTimestamp(
                turnIndex = turnIndex,
                platformIdx = platformIdx,
                currentTimeProvider = currentTimeProvider,
                revisionToAppend = revisionToAppendOnSuccess
            )

            ApiStateFlowOutcome.Incomplete -> Unit
        }
        return outcome
    } finally {
        onLoadingComplete()
    }
}

internal suspend fun Flow<ApiState>.collectApiStateUpdates(
    onUpdate: suspend (content: String, thoughts: String, timeline: List<AssistantTimelineItem>) -> Unit,
    onNotice: (String, Boolean) -> Unit = { _, _ -> },
    nanoTimeProvider: () -> Long = System::nanoTime,
    publishIntervalMillis: Long = STREAM_PUBLISH_INTERVAL_MILLIS
): ApiStateFlowOutcome {
    val buffer = StreamingMessageBuffer(nanoTimeProvider, publishIntervalMillis)
    var isCompletedSuccessfully = false
    var terminalError: String? = null

    try {
        collect { chunk ->
            when (chunk) {
                is ApiState.Thinking -> {
                    buffer.appendThought(chunk.thinkingChunk)
                    buffer.publishIfDue(onUpdate)
                }

                is ApiState.Success -> {
                    buffer.appendContent(chunk.textChunk)
                    buffer.publishIfDue(onUpdate)
                }

                is ApiState.ToolCall -> {
                    buffer.appendTool(chunk.toolSequence)
                    buffer.publishIfDue(onUpdate)
                }

                is ApiState.Notice -> {
                    if (chunk.persistent) {
                        buffer.appendNotice(chunk.message)
                    }
                    onNotice(chunk.message, chunk.persistent)
                    if (chunk.persistent) {
                        buffer.publishNow(onUpdate)
                    }
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
        buffer.flush(onUpdate)
    }

    return when {
        terminalError != null -> ApiStateFlowOutcome.Failed(terminalError)
        isCompletedSuccessfully -> ApiStateFlowOutcome.Completed
        else -> ApiStateFlowOutcome.Incomplete
    }
}

private class StreamingMessageBuffer(
    private val nanoTimeProvider: () -> Long,
    private val publishIntervalMillis: Long
) {
    private val thoughts = StringBuilder()
    private val content = StringBuilder()
    private val timeline = mutableListOf<AssistantTimelineItem>()
    private var lastPublishedAtNanos = 0L
    private var publishedThoughtLength = 0
    private var publishedContentLength = 0
    private var timelineVersion = 0
    private var publishedTimelineVersion = 0

    fun appendThought(chunk: String) {
        if (chunk.isNotEmpty()) {
            thoughts.append(chunk)
            appendTimelineText(AssistantTimelineItemType.THINKING, chunk)
        }
    }

    fun appendContent(chunk: String) {
        if (chunk.isNotEmpty()) {
            content.append(chunk)
            appendTimelineText(AssistantTimelineItemType.TEXT, chunk)
        }
    }

    fun appendTool(toolSequence: Int) {
        timeline += AssistantTimelineItem(
            type = AssistantTimelineItemType.TOOL,
            toolSequence = toolSequence
        )
        timelineVersion += 1
    }

    fun appendNotice(message: String) {
        if (message.isBlank()) return
        timeline += AssistantTimelineItem(type = AssistantTimelineItemType.NOTICE, content = message)
        timelineVersion += 1
    }

    suspend fun publishIfDue(
        onUpdate: suspend (content: String, thoughts: String, timeline: List<AssistantTimelineItem>) -> Unit
    ) {
        if (!hasPendingChanges()) return

        val now = nanoTimeProvider()
        if (lastPublishedAtNanos == 0L ||
            now - lastPublishedAtNanos >= publishIntervalMillis * 1_000_000
        ) {
            publish(onUpdate, now)
        }
    }

    suspend fun flush(
        onUpdate: suspend (content: String, thoughts: String, timeline: List<AssistantTimelineItem>) -> Unit
    ) {
        if (!hasPendingChanges()) return
        publish(onUpdate, nanoTimeProvider())
    }

    suspend fun publishNow(
        onUpdate: suspend (content: String, thoughts: String, timeline: List<AssistantTimelineItem>) -> Unit
    ) {
        if (!hasPendingChanges()) return
        publish(onUpdate, nanoTimeProvider())
    }

    private suspend fun publish(
        onUpdate: suspend (content: String, thoughts: String, timeline: List<AssistantTimelineItem>) -> Unit,
        publishedAtNanos: Long
    ) {
        onUpdate(content.toString(), thoughts.toString(), timeline.toList())
        publishedContentLength = content.length
        publishedThoughtLength = thoughts.length
        publishedTimelineVersion = timelineVersion
        lastPublishedAtNanos = publishedAtNanos
    }

    private fun appendTimelineText(type: AssistantTimelineItemType, chunk: String) {
        val last = timeline.lastOrNull()
        if (last?.type == type && last.toolSequence == null) {
            timeline[timeline.lastIndex] = last.copy(content = last.content + chunk)
        } else {
            timeline += AssistantTimelineItem(type = type, content = chunk)
        }
        timelineVersion += 1
    }

    private fun hasPendingChanges(): Boolean = content.length != publishedContentLength ||
        thoughts.length != publishedThoughtLength ||
        timelineVersion != publishedTimelineVersion
}

private fun MutableStateFlow<ChatViewModel.GroupedMessages>.setBufferedText(
    turnIndex: Int,
    platformIdx: Int,
    content: String,
    thoughts: String,
    timeline: List<AssistantTimelineItem>
) {
    update { groupedMessages ->
        updateAssistantSlot(
            groupedMessages = groupedMessages,
            turnIndex = turnIndex,
            platformIndex = platformIdx
        ) { currentMessage ->
            if (currentMessage.content == content &&
                currentMessage.thoughts == thoughts &&
                currentMessage.timeline == timeline
            ) {
                currentMessage
            } else {
                currentMessage.copy(
                    content = content,
                    thoughts = thoughts,
                    timeline = timeline
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
            val updatedContent = buildAssistantErrorContent(currentMessage.content, error)
            val appendedError = assistantErrorAppendedText(currentMessage.content, updatedContent)
            currentMessage.copy(
                content = updatedContent,
                timeline = currentMessage.timeline.appendErrorText(appendedError),
                createdAt = currentTimeProvider(),
                revisions = revisionToAppend
                    ?.let { listOf(it) + currentMessage.revisions }
                    ?: currentMessage.revisions
            )
        }
    }
}

private fun List<AssistantTimelineItem>.appendErrorText(errorText: String): List<AssistantTimelineItem> {
    if (isEmpty()) return this
    val last = last()
    return if (last.type == AssistantTimelineItemType.TEXT && last.toolSequence == null) {
        dropLast(1) + last.copy(content = last.content + errorText)
    } else {
        this + AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = errorText)
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
