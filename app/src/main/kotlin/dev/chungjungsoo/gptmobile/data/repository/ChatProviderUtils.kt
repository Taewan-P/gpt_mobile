package dev.chungjungsoo.gptmobile.data.repository

import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.data.dto.groq.request.GroqChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatMessage
import dev.chungjungsoo.gptmobile.util.stripAssistantErrorNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

internal fun MessageV2.sendableAssistantContent(): String {
    val strippedContent = stripAssistantErrorNote(effectiveContent()).trim()
    return if (strippedContent.startsWith("Error: ")) "" else strippedContent
}

internal fun MessageV2.hasSendableAssistantPayload(): Boolean = sendableAssistantContent().isNotBlank() || attachments.isNotEmpty()

internal fun createGroqChatCompletionRequest(
    messages: List<ChatMessage>,
    platform: PlatformV2
): GroqChatCompletionRequest {
    val isGptOssModel = isGroqGptOssModel(platform.model)

    return GroqChatCompletionRequest(
        model = platform.model,
        messages = messages,
        stream = platform.stream,
        temperature = platform.temperature,
        topP = platform.topP,
        maxCompletionTokens = null,
        reasoningEffort = if (platform.reasoning && isGptOssModel) "medium" else null,
        reasoningFormat = when {
            platform.reasoning && !isGptOssModel -> "parsed"
            !platform.reasoning && !isGptOssModel -> "hidden"
            else -> null
        },
        includeReasoning = when {
            platform.reasoning && isGptOssModel -> true
            !platform.reasoning && isGptOssModel -> false
            else -> null
        }
    )
}

internal fun isGroqGptOssModel(model: String): Boolean = model.contains("gpt-oss", ignoreCase = true)

internal fun validateResponseInputPartsOrThrow(messageContent: String, partCount: Int, messageId: Int) {
    if (messageContent.isBlank() && partCount == 0) {
        throw IllegalStateException("No encodable message content for messageId=$messageId")
    }
}

internal fun <T> streamPreparedApiState(
    prepare: suspend () -> T,
    stream: suspend (T) -> Flow<ApiState>
): Flow<ApiState> = flow {
    emit(ApiState.Loading)
    val preparedRequest = withContext(Dispatchers.Default) { prepare() }
    emitAll(stream(preparedRequest))
}
