package dev.chungjungsoo.gptmobile.data.repository.provider

import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponsesRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseInputItem
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseTool
import dev.chungjungsoo.gptmobile.data.dto.openai.response.FunctionCallArgumentsDeltaEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.FunctionCallArgumentsDoneEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.OutputItemAddedEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.OutputItemDoneEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.OutputTextDeltaEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.OutputTextDoneEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ReasoningSummaryTextDeltaEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseCompletedEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseErrorEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseFailedEvent
import dev.chungjungsoo.gptmobile.data.network.OpenAIAPI
import dev.chungjungsoo.gptmobile.data.repository.streamPreparedApiState
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

class OpenAIResponsesChatProviderAdapter @Inject constructor(
    private val openAIAPI: OpenAIAPI,
    private val requestBuilder: ProviderRequestBuilder,
    private val toolExecutor: dev.chungjungsoo.gptmobile.data.tool.ToolExecutor,
    private val toolRegistry: dev.chungjungsoo.gptmobile.data.tool.ToolRegistry = dev.chungjungsoo.gptmobile.data.tool.DefaultToolRegistry()
) : ChatProviderAdapter {
    override fun supports(platform: PlatformV2): Boolean = platform.compatibleType == dev.chungjungsoo.gptmobile.data.model.ClientType.OPENAI

    override suspend fun completeChat(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): Flow<ApiState> = try {
        openAIAPI.setToken(platform.token)
        openAIAPI.setAPIUrl(platform.apiUrl)

        streamPreparedApiState(
            prepare = {
                val contextTurns = requestBuilder.buildContextTurns(userMessages, assistantMessages, platform)
                val inputItems = requestBuilder.buildResponsesInputItems(contextTurns, platform.uid)
                val systemPrompt = resolveWebSearchSystemPrompt(
                    toolExecutor = toolExecutor,
                    baseSystemPrompt = platform.systemPrompt,
                    userMessages = userMessages,
                    toolCallsEnabled = platform.toolCallsEnabled
                )
                val tools = if (platform.toolCallsEnabled) {
                    toolRegistry.toolsFor(platform).map { definition ->
                        ResponseTool.function(
                            name = definition.name,
                            description = definition.description,
                            parameters = definition.parameters
                        )
                    }
                } else {
                    emptyList()
                }

                requestBuilder.buildResponsesRequest(
                    model = platform.model,
                    input = inputItems,
                    systemPrompt = systemPrompt,
                    temperature = platform.temperature,
                    topP = platform.topP,
                    reasoning = platform.reasoning,
                    tools = tools
                )
            },
            stream = { request ->
                flow {
                    streamResponsesWithTools(request, platform).collect { emit(it) }
                }
            }
        ).catch { e ->
            emit(ApiState.Error(e.message ?: "Unknown error"))
        }.onCompletion {
            emit(ApiState.Done)
        }
    } catch (e: Exception) {
        flowOfError(e)
    }

    private suspend fun streamResponsesWithTools(
        initialRequest: ResponsesRequest,
        platform: PlatformV2
    ): Flow<ApiState> = flow {
        var inputItems = initialRequest.input
        var completed = false
        var round = 0

        while (round < MAX_TOOL_CALL_ROUNDS) {
            round += 1
            val pendingCalls = linkedMapOf<String, PendingResponseToolCall>()
            var terminalError: String? = null
            var sawVisibleText = false
            val request = initialRequest.copy(
                input = inputItems,
                previousResponseId = null
            )

            openAIAPI.streamResponses(
                request = request,
                timeoutSeconds = platform.timeout
            ).collect { event ->
                when (event) {
                    is ReasoningSummaryTextDeltaEvent -> emit(ApiState.Thinking(event.delta))
                    is OutputTextDeltaEvent -> {
                        if (event.delta.isNotEmpty()) {
                            sawVisibleText = true
                            emit(ApiState.Success(event.delta))
                        }
                    }
                    is OutputTextDoneEvent -> {
                        if (!sawVisibleText && event.text.isNotEmpty()) {
                            sawVisibleText = true
                            emit(ApiState.Success(event.text))
                        }
                    }
                    is OutputItemAddedEvent -> updatePendingCall(pendingCalls, event.outputIndex, event.item.id, event.item.name, event.item.callId, event.item.arguments, event.item.status)
                    is OutputItemDoneEvent -> updatePendingCall(pendingCalls, event.outputIndex, event.item.id, event.item.name, event.item.callId, event.item.arguments, event.item.status)
                    is FunctionCallArgumentsDeltaEvent -> appendPendingCallArguments(pendingCalls, event.outputIndex, event.itemId, event.delta)
                    is FunctionCallArgumentsDoneEvent -> updatePendingCall(pendingCalls, event.outputIndex, event.itemId, null, null, event.arguments)
                    is ResponseCompletedEvent -> {}
                    is ResponseFailedEvent -> terminalError = event.response.error?.message ?: "Response failed"
                    is ResponseErrorEvent -> terminalError = event.message
                    else -> {}
                }
            }
            val error = terminalError
            if (error != null) {
                emit(ApiState.Error(error))
                return@flow
            }

            val toolCalls = pendingCalls.values
                .sortedWith(compareBy<PendingResponseToolCall> { it.outputIndex ?: Int.MAX_VALUE }.thenBy { it.id })
                .mapNotNull { call ->
                    val name = call.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val callId = call.callId ?: call.id ?: return@mapNotNull null
                    val argumentsText = call.arguments?.takeIf { it.isNotEmpty() } ?: "{}"
                    val arguments = parseToolArguments(argumentsText)
                    PendingResponseToolInvocation(
                        id = call.id,
                        callId = callId,
                        toolName = name,
                        argumentsText = argumentsText,
                        arguments = arguments,
                        status = call.status ?: "completed"
                    )
                }

            if (toolCalls.isEmpty()) {
                if (!sawVisibleText) {
                    emit(ApiState.Error("Provider returned an empty message."))
                }
                completed = true
                break
            }

            val outputs = toolCalls.map { invocation ->
                emit(ApiState.ToolStatus(buildToolStatus(invocation.toolName, invocation.arguments)))
                val result = withContext(Dispatchers.IO) {
                    toolExecutor.execute(invocation.toolName, invocation.arguments)
                }
                ResponseInputItem.FunctionCallOutput(
                    callId = invocation.callId,
                    output = result.output
                )
            }

            inputItems = inputItems +
                toolCalls.map { invocation ->
                    ResponseInputItem.FunctionCall(
                        id = invocation.id,
                        callId = invocation.callId,
                        name = invocation.toolName,
                        arguments = invocation.argumentsText,
                        status = invocation.status
                    )
                } +
                outputs
        }

        if (!completed) {
            emit(ApiState.Error("Tool call limit reached."))
        }
    }

    private fun updatePendingCall(
        pendingCalls: MutableMap<String, PendingResponseToolCall>,
        outputIndex: Int,
        itemId: String,
        name: String? = null,
        callId: String? = null,
        arguments: String? = null,
        status: String? = null
    ) {
        val existing = pendingCalls[itemId] ?: PendingResponseToolCall(id = itemId)
        pendingCalls[itemId] = existing.copy(
            outputIndex = outputIndex,
            name = name ?: existing.name,
            callId = callId ?: existing.callId,
            status = status ?: existing.status,
            arguments = when {
                !arguments.isNullOrEmpty() -> arguments
                else -> existing.arguments
            }
        )
    }

    private fun appendPendingCallArguments(
        pendingCalls: MutableMap<String, PendingResponseToolCall>,
        outputIndex: Int,
        itemId: String,
        delta: String
    ) {
        if (delta.isEmpty()) return

        val existing = pendingCalls[itemId] ?: PendingResponseToolCall(id = itemId)
        val updatedArguments = when {
            existing.arguments.isNullOrEmpty() -> delta
            else -> existing.arguments + delta
        }

        pendingCalls[itemId] = existing.copy(
            outputIndex = outputIndex,
            arguments = updatedArguments
        )
    }

    private fun flowOfError(e: Exception): Flow<ApiState> = flow {
        emit(ApiState.Error(e.message ?: "Failed to complete chat"))
    }
}

private const val MAX_TOOL_CALL_ROUNDS = 8

private data class PendingResponseToolCall(
    val id: String,
    val outputIndex: Int? = null,
    val callId: String? = null,
    val name: String? = null,
    val arguments: String? = null,
    val status: String? = null
)

private data class PendingResponseToolInvocation(
    val id: String?,
    val callId: String,
    val toolName: String,
    val argumentsText: String,
    val arguments: JsonObject,
    val status: String?
)
