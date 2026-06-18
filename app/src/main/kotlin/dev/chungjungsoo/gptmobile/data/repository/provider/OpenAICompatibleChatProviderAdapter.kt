package dev.chungjungsoo.gptmobile.data.repository.provider

import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.data.dto.openai.common.Role
import dev.chungjungsoo.gptmobile.data.dto.openai.common.TextContent
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionTool
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatMessage
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatToolCall
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatToolCallFunction
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.OpenAIAPI
import dev.chungjungsoo.gptmobile.data.repository.streamPreparedApiState
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext

class OpenAICompatibleChatProviderAdapter @Inject constructor(
    private val openAIAPI: OpenAIAPI,
    private val requestBuilder: ProviderRequestBuilder,
    private val toolExecutor: dev.chungjungsoo.gptmobile.data.tool.ToolExecutor,
    private val toolRegistry: dev.chungjungsoo.gptmobile.data.tool.ToolRegistry = dev.chungjungsoo.gptmobile.data.tool.DefaultToolRegistry()
) : ChatProviderAdapter {
    override fun supports(platform: PlatformV2): Boolean = platform.compatibleType in setOf(
        ClientType.OPENAI,
        ClientType.OLLAMA,
        ClientType.OPENROUTER,
        ClientType.CUSTOM
    )

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
                requestBuilder.validateInlineBudgetIfNeeded(contextTurns, platform)
                val systemPrompt = resolveWebSearchSystemPrompt(
                    toolExecutor = toolExecutor,
                    baseSystemPrompt = platform.systemPrompt,
                    userMessages = userMessages,
                    toolCallsEnabled = platform.toolCallsEnabled
                )
                requestBuilder.buildOpenAICompatibleMessages(contextTurns, systemPrompt)
            },
            stream = { messages ->
                flow {
                    if (platform.toolCallsEnabled && toolRegistry.toolsFor(platform).isNotEmpty()) {
                        streamChatWithTools(messages, platform).collect { emit(it) }
                    } else {
                        streamPlainChat(messages, platform).collect { emit(it) }
                    }
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

    private suspend fun streamPlainChat(
        baseMessages: List<ChatMessage>,
        platform: PlatformV2
    ): Flow<ApiState> = flow {
        val useStreaming = shouldUseStreaming(platform)
        val request = ChatCompletionRequest(
            model = platform.model,
            messages = baseMessages,
            stream = useStreaming,
            temperature = platform.temperature,
            topP = platform.topP
        )

        openAIAPI.streamChatCompletion(request, platform.timeout).collect { chunk ->
            when {
                chunk.error != null -> emit(ApiState.Error(chunk.error.message))
                else -> {
                    val choice = chunk.choices?.firstOrNull()
                    val assistantOutput = extractAssistantOutput(choice)
                    if (!assistantOutput.reasoning.isNullOrEmpty()) {
                        emit(ApiState.Thinking(assistantOutput.reasoning))
                    }
                    if (!assistantOutput.content.isNullOrEmpty()) {
                        emit(ApiState.Success(assistantOutput.content))
                    }
                }
            }
        }
    }

    private suspend fun streamChatWithTools(
        baseMessages: List<ChatMessage>,
        platform: PlatformV2
    ): Flow<ApiState> = flow {
        val toolDefinitions = toolRegistry.toolsFor(platform)
        val toolRequests = toolDefinitions.map { definition ->
            ChatCompletionTool.function(
                name = definition.name,
                description = definition.description,
                parameters = definition.parameters
            )
        }
        var conversation = baseMessages.toMutableList()

        var toolModeEnabled = true
        var fallbackAttempted = false
        var completed = false
        var round = 0

        while (round < MAX_TOOL_CALL_ROUNDS) {
            round += 1
            val assistantText = StringBuilder()
            val pendingToolCalls = mutableMapOf<Int, PendingChatToolCall>()
            var terminalError: String? = null
            val useStreaming = shouldUseStreaming(platform)

            val request = ChatCompletionRequest(
                model = platform.model,
                messages = conversation,
                stream = useStreaming,
                temperature = platform.temperature,
                topP = platform.topP,
                tools = if (toolModeEnabled) toolRequests else null
            )

            openAIAPI.streamChatCompletion(request, platform.timeout).collect { chunk ->
                when {
                    chunk.error != null -> terminalError = chunk.error.message
                    else -> {
                        val choice = chunk.choices?.firstOrNull()
                        val assistantOutput = extractAssistantOutput(choice)
                        assistantOutput.reasoning?.takeIf { it.isNotEmpty() }?.let {
                            emit(ApiState.Thinking(it))
                        }
                        assistantOutput.content?.takeIf { it.isNotEmpty() }?.let {
                            assistantText.append(it)
                            emit(ApiState.Success(it))
                        }
                        val toolCalls = choice?.delta?.toolCalls ?: choice?.message?.toolCalls ?: choice?.toolCalls
                        toolCalls?.forEachIndexed { fallbackIndex, delta ->
                            val index = delta.index ?: fallbackIndex
                            val current = pendingToolCalls[index] ?: PendingChatToolCall(index = index)
                            pendingToolCalls[index] = current.copy(
                                id = delta.id ?: current.id,
                                name = delta.function?.name ?: current.name,
                                arguments = mergeToolArguments(current.arguments, delta.function?.arguments)
                            )
                        }
                    }
                }
            }
            val error = terminalError
            if (error != null) {
                val shouldFallbackWithoutTools = toolModeEnabled && !fallbackAttempted && shouldRetryWithoutTools(error)
                if (shouldFallbackWithoutTools) {
                    toolModeEnabled = false
                    fallbackAttempted = true
                    continue
                }
                emit(ApiState.Error(error))
                return@flow
            }

            val completedToolCalls = pendingToolCalls.values
                .sortedBy { it.index }
                .mapNotNull { pending ->
                    val toolName = pending.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val id = pending.id ?: "tool-${pending.index}"
                    ChatToolCall(
                        id = id,
                        function = ChatToolCallFunction(
                            name = toolName,
                            arguments = pending.arguments.toString().ifBlank { "{}" }
                        )
                    )
                }

            if (completedToolCalls.isEmpty()) {
                if (assistantText.isEmpty()) {
                    if (toolModeEnabled && !fallbackAttempted) {
                        toolModeEnabled = false
                        fallbackAttempted = true
                        continue
                    }
                    emit(ApiState.Error("Provider returned an empty message."))
                }
                completed = true
                break
            }

            conversation.add(
                ChatMessage(
                    role = Role.ASSISTANT,
                    content = if (assistantText.isNotEmpty()) listOf(TextContent(assistantText.toString())) else listOf(TextContent("")),
                    toolCalls = completedToolCalls
                )
            )

            completedToolCalls.forEach { toolCall ->
                val toolName = toolCall.function.name.orEmpty()
                val arguments = parseToolArguments(toolCall.function.arguments)
                emit(ApiState.ToolStatus(buildToolStatus(toolName, arguments)))
                val result = withContext(Dispatchers.IO) {
                    toolExecutor.execute(toolName, arguments)
                }
                conversation.add(
                    ChatMessage(
                        role = Role.TOOL,
                        content = listOf(TextContent(result.output)),
                        toolCallId = toolCall.id
                    )
                )
            }
        }

        if (!completed) {
            emit(ApiState.Error("Tool call limit reached."))
        }
    }

    private fun flowOfError(e: Exception): Flow<ApiState> = flow {
        emit(ApiState.Error(e.message ?: "Failed to complete chat"))
    }

    private fun extractAssistantOutput(choice: dev.chungjungsoo.gptmobile.data.dto.openai.response.Choice?): AssistantOutput {
        val content = choice?.delta?.content ?: choice?.message?.content ?: choice?.text
        val reasoningContent = choice?.delta?.reasoningContent ?: choice?.message?.reasoningContent
        return AssistantOutput(
            content = content?.takeIf { it.isNotEmpty() },
            reasoning = reasoningContent?.takeIf { it.isNotEmpty() }
        )
    }

    private fun shouldRetryWithoutTools(errorMessage: String?): Boolean {
        val message = errorMessage?.lowercase().orEmpty()
        if (message.isBlank()) return false
        return listOf(
            "tool",
            "function call",
            "function_call",
            "unsupported",
            "not support"
        ).any { message.contains(it) }
    }

    private fun mergeToolArguments(current: StringBuilder, incomingRaw: String?): StringBuilder {
        val incoming = incomingRaw?.takeIf { it.isNotEmpty() } ?: return current
        val trimmed = incoming.trim()
        val looksLikeCompleteJsonObject = trimmed.startsWith("{") && trimmed.endsWith("}")
        if (looksLikeCompleteJsonObject) {
            current.setLength(0)
            current.append(incoming)
            return current
        }
        current.append(incoming)
        return current
    }

    private fun shouldUseStreaming(platform: PlatformV2): Boolean =
        listOf(
            platform.compatibleType == ClientType.OPENAI,
            platform.stream,
            !platform.toolCallsEnabled
        ).all { it }
}

private const val MAX_TOOL_CALL_ROUNDS = 8

private data class PendingChatToolCall(
    val index: Int,
    val id: String? = null,
    val name: String? = null,
    val arguments: StringBuilder = StringBuilder()
)

private data class AssistantOutput(
    val content: String?,
    val reasoning: String?
)
