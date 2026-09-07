package dev.chungjungsoo.gptmobile.data.agent.provider

import dev.chungjungsoo.gptmobile.data.agent.ProviderEvent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.MessageContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.RedactedThinkingContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.TextContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.ThinkingContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.ToolUseContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentBlockType
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentDeltaResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentStartResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentStopResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ErrorResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.MessageResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.MessageStopResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.google.response.GenerateContentResponse
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ChatToolCallDelta
import dev.chungjungsoo.gptmobile.data.dto.openai.response.FunctionCallArgumentsDeltaEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.FunctionCallArgumentsDoneEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.OutputItemAddedEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.OutputTextDeltaEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ReasoningSummaryTextDeltaEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseCompletedEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseErrorEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseFailedEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponsesStreamEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class OpenAIResponsesEventAssembler {
    private data class PendingCall(
        val callId: String,
        val name: String,
        val arguments: StringBuilder
    )

    private val pending = mutableMapOf<String, PendingCall>()

    fun accept(event: ResponsesStreamEvent): List<ProviderEvent> = when (event) {
        is ReasoningSummaryTextDeltaEvent -> listOf(ProviderEvent.ThinkingDelta(event.delta))

        is OutputTextDeltaEvent -> listOf(ProviderEvent.TextDelta(event.delta))

        is OutputItemAddedEvent -> {
            if (event.item.type != "function_call") {
                emptyList()
            } else {
                val callId = event.item.callId
                val name = event.item.name
                if (callId == null || name == null) {
                    listOf(ProviderEvent.Failed("OpenAI returned an incomplete function call."))
                } else {
                    pending[event.item.id] = PendingCall(
                        callId = callId,
                        name = name,
                        arguments = StringBuilder(event.item.arguments.orEmpty())
                    )
                    emptyList()
                }
            }
        }

        is FunctionCallArgumentsDeltaEvent -> {
            pending[event.itemId]?.arguments?.append(event.delta)
            emptyList()
        }

        is FunctionCallArgumentsDoneEvent -> {
            val call = pending.remove(event.itemId)
                ?: return listOf(ProviderEvent.Failed("OpenAI returned arguments for an unknown function call."))
            toolCall(call.callId, call.name, event.arguments)
        }

        is ResponseFailedEvent -> listOf(ProviderEvent.Failed(event.response.error?.message ?: "Response failed"))

        is ResponseErrorEvent -> listOf(ProviderEvent.Failed(event.message))

        is ResponseCompletedEvent -> listOf(ProviderEvent.Completed)

        else -> emptyList()
    }
}

class ChatCompletionsEventAssembler {
    private data class PendingCall(
        var callId: String? = null,
        var name: String? = null,
        val arguments: StringBuilder = StringBuilder()
    )

    private val pending = sortedMapOf<Int, PendingCall>()

    fun accept(
        content: String?,
        reasoning: String?,
        toolCalls: List<ChatToolCallDelta>?,
        finishReason: String?
    ): List<ProviderEvent> {
        val events = mutableListOf<ProviderEvent>()
        reasoning?.takeIf { it.isNotEmpty() }?.let { events += ProviderEvent.ThinkingDelta(it) }
        content?.takeIf { it.isNotEmpty() }?.let { events += ProviderEvent.TextDelta(it) }
        toolCalls.orEmpty().forEach { delta ->
            val call = pending.getOrPut(delta.index) { PendingCall() }
            delta.id?.let { call.callId = it }
            delta.function?.name?.let { call.name = it }
            delta.function?.arguments?.let { call.arguments.append(it) }
        }
        if (finishReason == "tool_calls") {
            pending.values.forEach { call ->
                val callId = call.callId
                val name = call.name
                events += if (callId == null || name == null) {
                    ProviderEvent.Failed("Provider returned an incomplete function call.")
                } else {
                    toolCall(callId, name, call.arguments.toString()).single()
                }
            }
            pending.clear()
        }
        return events
    }
}

class AnthropicEventAssembler {
    private data class PendingCall(
        val callId: String,
        val name: String,
        val arguments: StringBuilder = StringBuilder()
    )

    private val pending = mutableMapOf<Int, PendingCall>()
    private val pendingText = mutableMapOf<Int, StringBuilder>()
    private val pendingThinking = mutableMapOf<Int, Pair<StringBuilder, StringBuilder>>()
    private val pendingRedactedThinking = mutableMapOf<Int, String>()
    private val completed = sortedMapOf<Int, MessageContent>()

    fun accept(event: MessageResponseChunk): List<ProviderEvent> = when (event) {
        is ContentStartResponseChunk -> {
            when (event.contentBlock.type) {
                ContentBlockType.TEXT -> pendingText[event.index] = StringBuilder(event.contentBlock.text.orEmpty())

                ContentBlockType.THINKING -> pendingThinking[event.index] =
                    StringBuilder(event.contentBlock.thinking.orEmpty()) to StringBuilder(event.contentBlock.signature.orEmpty())

                // Anthropic rejects a replayed redacted_thinking block without its encrypted payload.
                ContentBlockType.REDACTED_THINKING ->
                    event.contentBlock.data
                        ?.takeIf(String::isNotBlank)
                        ?.let { pendingRedactedThinking[event.index] = it }

                ContentBlockType.TOOL_USE -> {
                    val callId = event.contentBlock.id
                    val name = event.contentBlock.name
                    if (callId == null || name == null) {
                        return listOf(ProviderEvent.Failed("Anthropic returned an incomplete tool use block."))
                    }
                    val initialInput = event.contentBlock.input
                        ?.takeIf { it.isNotEmpty() }
                        ?.toString()
                        .orEmpty()
                    pending[event.index] = PendingCall(callId, name, StringBuilder(initialInput))
                }

                else -> Unit
            }
            emptyList()
        }

        is ContentDeltaResponseChunk -> when (event.delta.type) {
            ContentBlockType.TEXT, ContentBlockType.DELTA -> event.delta.text?.let {
                pendingText.getOrPut(event.index) { StringBuilder() }.append(it)
                listOf(ProviderEvent.TextDelta(it))
            }.orEmpty()

            ContentBlockType.THINKING, ContentBlockType.THINKING_DELTA -> event.delta.thinking?.let {
                pendingThinking.getOrPut(event.index) { StringBuilder() to StringBuilder() }.first.append(it)
                listOf(ProviderEvent.ThinkingDelta(it))
            }.orEmpty()

            ContentBlockType.SIGNATURE, ContentBlockType.SIGNATURE_DELTA -> {
                pendingThinking.getOrPut(event.index) { StringBuilder() to StringBuilder() }.second.append(event.delta.signature.orEmpty())
                emptyList()
            }

            ContentBlockType.INPUT_JSON_DELTA -> {
                pending[event.index]?.arguments?.append(event.delta.partialJson.orEmpty())
                emptyList()
            }

            else -> emptyList()
        }

        is ContentStopResponseChunk -> {
            pendingText.remove(event.index)?.let { completed[event.index] = TextContent(it.toString()) }
            pendingThinking.remove(event.index)?.let { (thinking, signature) ->
                completed[event.index] = ThinkingContent(thinking.toString(), signature.toString())
            }
            pendingRedactedThinking.remove(event.index)?.let { data ->
                completed[event.index] = RedactedThinkingContent(data)
            }
            val call = pending.remove(event.index) ?: return emptyList()
            val events = toolCall(call.callId, call.name, call.arguments.toString())
            if (events.singleOrNull() is ProviderEvent.ToolCall) {
                completed[event.index] = ToolUseContent(
                    call.callId,
                    call.name,
                    Json.parseToJsonElement(call.arguments.toString().ifBlank { "{}" }) as JsonObject
                )
            }
            events
        }

        is ErrorResponseChunk -> listOf(ProviderEvent.Failed(event.error.message))

        MessageStopResponseChunk -> listOf(ProviderEvent.Completed)

        else -> emptyList()
    }

    fun replayContent(): List<MessageContent> = completed.values.toList()
}

object GeminiEventMapper {
    fun accept(response: GenerateContentResponse): List<ProviderEvent> {
        response.error?.let { return listOf(ProviderEvent.Failed(it.message)) }
        val events = mutableListOf<ProviderEvent>()
        response.candidates.orEmpty().flatMap { it.content?.parts.orEmpty() }.forEach { part ->
            part.text?.let { text ->
                events += if (part.thought == true) ProviderEvent.ThinkingDelta(text) else ProviderEvent.TextDelta(text)
            }
            part.functionCall?.let { call ->
                events += ProviderEvent.ToolCall(call.id ?: java.util.UUID.randomUUID().toString(), call.name, call.args)
            }
        }
        return events
    }
}

private fun toolCall(callId: String, name: String, arguments: String): List<ProviderEvent> = try {
    val parsed = Json.parseToJsonElement(arguments.ifBlank { "{}" })
    if (parsed is JsonObject) {
        listOf(ProviderEvent.ToolCall(callId, name, parsed))
    } else {
        listOf(ProviderEvent.Failed("Tool arguments must be a JSON object."))
    }
} catch (_: Exception) {
    listOf(ProviderEvent.Failed("Tool arguments were not valid JSON."))
}
