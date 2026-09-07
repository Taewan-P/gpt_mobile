package dev.chungjungsoo.gptmobile.data.agent.provider

import dev.chungjungsoo.gptmobile.data.agent.AgentProviderSession
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolExchange
import dev.chungjungsoo.gptmobile.data.agent.ProviderEvent
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.context.ConversationTurn
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.MessageContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.MessageRole
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.ToolResultContent as AnthropicToolResultContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.ToolUseContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.AnthropicTool
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.InputMessage
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.MessageRequest
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.ThinkingConfig as AnthropicThinkingConfig
import dev.chungjungsoo.gptmobile.data.dto.google.common.Content
import dev.chungjungsoo.gptmobile.data.dto.google.common.FunctionCall
import dev.chungjungsoo.gptmobile.data.dto.google.common.FunctionResponse
import dev.chungjungsoo.gptmobile.data.dto.google.common.Part
import dev.chungjungsoo.gptmobile.data.dto.google.common.Role as GoogleRole
import dev.chungjungsoo.gptmobile.data.dto.google.request.FunctionDeclaration
import dev.chungjungsoo.gptmobile.data.dto.google.request.GenerateContentRequest
import dev.chungjungsoo.gptmobile.data.dto.google.request.GenerationConfig
import dev.chungjungsoo.gptmobile.data.dto.google.request.GoogleFunctionCallingConfig
import dev.chungjungsoo.gptmobile.data.dto.google.request.GoogleTool
import dev.chungjungsoo.gptmobile.data.dto.google.request.GoogleToolConfig
import dev.chungjungsoo.gptmobile.data.dto.google.request.SafetySetting
import dev.chungjungsoo.gptmobile.data.dto.google.request.ThinkingConfig as GoogleThinkingConfig
import dev.chungjungsoo.gptmobile.data.dto.groq.request.GroqChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.common.Role as OpenAIRole
import dev.chungjungsoo.gptmobile.data.dto.openai.common.TextContent as OpenAITextContent
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatFunction
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatFunctionTool
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatMessage
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatToolCall
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ReasoningConfig
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseFunctionCallOutput
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseFunctionTool
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponsesRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseCompletedEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseCreatedEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseFailedEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseInProgressEvent
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.GeminiSafetySettings
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPI
import dev.chungjungsoo.gptmobile.data.network.GoogleAPI
import dev.chungjungsoo.gptmobile.data.network.GroqAPI
import dev.chungjungsoo.gptmobile.data.network.OpenAIAPI
import dev.chungjungsoo.gptmobile.data.network.ProviderRequestConfig
import dev.chungjungsoo.gptmobile.data.repository.GroqReasoningParser
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class OpenAIResponsesAdapter @Inject constructor(
    private val api: OpenAIAPI,
    private val attachmentEncoder: ProviderAttachmentEncoder
) {
    suspend fun openSession(turns: List<ConversationTurn>, platform: PlatformV2): AgentProviderSession {
        val initialInput = attachmentEncoder.responsesInput(turns, platform.uid)
        val config = ProviderRequestConfig(platform.apiUrl, platform.token)
        var previousResponseId: String? = null
        return object : AgentProviderSession {
            override fun streamRound(
                tools: List<AgentToolDefinition>,
                exchanges: List<AgentToolExchange>
            ): Flow<ProviderEvent> = flow {
                val request = ResponsesRequest(
                    model = platform.model,
                    input = if (exchanges.isEmpty()) {
                        initialInput
                    } else {
                        exchanges.last().results.map { result ->
                            ResponseFunctionCallOutput(result.callId, result.modelText())
                        }
                    },
                    stream = true,
                    instructions = platform.systemPrompt?.takeIf { it.isNotBlank() },
                    temperature = if (platform.reasoning) null else platform.temperature,
                    topP = if (platform.reasoning) null else platform.topP,
                    reasoning = if (platform.reasoning) ReasoningConfig(effort = "medium", summary = "auto") else null,
                    previousResponseId = previousResponseId,
                    tools = tools.takeIf { it.isNotEmpty() }?.map { definition ->
                        ResponseFunctionTool(definition.name, definition.description, definition.inputSchema)
                    }
                )
                val assembler = OpenAIResponsesEventAssembler()
                var failed = false
                api.streamResponses(request, platform.timeout, config).collect { event ->
                    when (event) {
                        is ResponseCreatedEvent -> previousResponseId = event.response.id
                        is ResponseInProgressEvent -> previousResponseId = event.response.id
                        is ResponseCompletedEvent -> previousResponseId = event.response.id
                        is ResponseFailedEvent -> previousResponseId = event.response.id
                        else -> Unit
                    }
                    assembler.accept(event).forEach { mapped ->
                        when (mapped) {
                            ProviderEvent.Completed -> Unit

                            is ProviderEvent.Failed -> {
                                failed = true
                                emit(mapped)
                            }

                            else -> emit(mapped)
                        }
                    }
                }
                if (!failed) emit(ProviderEvent.Completed)
            }
        }
    }
}

class OpenAICompatibleAdapter @Inject constructor(
    private val openAIAPI: OpenAIAPI,
    private val groqAPI: GroqAPI,
    private val attachmentEncoder: ProviderAttachmentEncoder
) {
    suspend fun openSession(turns: List<ConversationTurn>, platform: PlatformV2): AgentProviderSession {
        val initialMessages = attachmentEncoder.openAIChatMessages(turns, platform.systemPrompt)
        val config = ProviderRequestConfig(platform.apiUrl, platform.token)
        return object : AgentProviderSession {
            override fun streamRound(
                tools: List<AgentToolDefinition>,
                exchanges: List<AgentToolExchange>
            ): Flow<ProviderEvent> = flow {
                val messages = initialMessages + exchanges.flatMap { it.toChatMessages() }
                val requestTools = tools.takeIf { it.isNotEmpty() }?.map { definition ->
                    ChatFunctionTool(definition.name, definition.description, definition.inputSchema)
                }
                if (platform.compatibleType == ClientType.GROQ) {
                    val request = createGroqChatCompletionRequest(messages, platform).copy(tools = requestTools)
                    val assembler = ChatCompletionsEventAssembler()
                    val reasoningParser = GroqReasoningParser()
                    var failed = false
                    groqAPI.streamChatCompletion(request, platform.timeout, config).collect { chunk ->
                        chunk.error?.let { error ->
                            failed = true
                            emit(ProviderEvent.Failed(error.message))
                        } ?: chunk.choices.orEmpty().forEach { choice ->
                            reasoningParser.append(
                                contentChunk = choice.delta?.content ?: choice.message?.content,
                                reasoningChunk = choice.delta?.reasoning ?: choice.message?.reasoning
                            ).forEach { state ->
                                state.toProviderEvent()?.let { emit(it) }
                            }
                            if (choice.finishReason == "length") {
                                failed = true
                                emit(ProviderEvent.Failed(GROQ_OUTPUT_LIMIT_MESSAGE))
                            } else {
                                assembler.accept(
                                    content = null,
                                    reasoning = null,
                                    toolCalls = choice.delta?.toolCalls,
                                    finishReason = choice.finishReason
                                ).forEach { emit(it) }
                            }
                        }
                    }
                    reasoningParser.flush().forEach { state ->
                        state.toProviderEvent()?.let { emit(it) }
                    }
                    if (!failed) emit(ProviderEvent.Completed)
                    return@flow
                }

                val request = ChatCompletionRequest(
                    model = platform.model,
                    messages = messages,
                    stream = platform.stream,
                    temperature = platform.temperature,
                    topP = platform.topP,
                    tools = requestTools
                )
                val assembler = ChatCompletionsEventAssembler()
                var failed = false
                openAIAPI.streamChatCompletion(request, platform.timeout, config).collect { chunk ->
                    chunk.error?.let { error ->
                        failed = true
                        emit(ProviderEvent.Failed(error.message))
                    } ?: chunk.choices.orEmpty().forEach { choice ->
                        assembler.accept(
                            content = choice.delta.content,
                            reasoning = choice.delta.reasoning,
                            toolCalls = choice.delta.toolCalls,
                            finishReason = choice.finishReason
                        ).forEach { emit(it) }
                    }
                }
                if (!failed) emit(ProviderEvent.Completed)
            }
        }
    }
}

class AnthropicMessagesAdapter @Inject constructor(
    private val api: AnthropicAPI,
    private val attachmentEncoder: ProviderAttachmentEncoder
) {
    suspend fun openSession(turns: List<ConversationTurn>, platform: PlatformV2): AgentProviderSession {
        val initialMessages = attachmentEncoder.anthropicMessages(turns, platform.uid)
        val assistantContentByRound = mutableMapOf<Int, List<MessageContent>>()
        return object : AgentProviderSession {
            override fun streamRound(
                tools: List<AgentToolDefinition>,
                exchanges: List<AgentToolExchange>
            ): Flow<ProviderEvent> = flow {
                val thinkingPolicy = anthropicThinkingPolicy(
                    model = platform.model,
                    reasoningEnabled = platform.reasoning,
                    hasTools = tools.isNotEmpty()
                )
                val isThinkingActive = thinkingPolicy.config?.type?.let { it != "disabled" } == true
                val request = MessageRequest(
                    model = platform.model,
                    messages = initialMessages + exchanges.flatMapIndexed { index, exchange ->
                        exchange.toAnthropicMessages(assistantContentByRound[index])
                    },
                    maxTokens = if (isThinkingActive) 16000 else 4096,
                    stream = platform.stream,
                    systemPrompt = platform.systemPrompt,
                    temperature = if (isThinkingActive) null else platform.temperature,
                    topP = if (isThinkingActive) null else platform.topP,
                    thinking = thinkingPolicy.config,
                    tools = tools.takeIf { it.isNotEmpty() }?.map { definition ->
                        AnthropicTool(definition.name, definition.description, definition.inputSchema)
                    }
                )
                val assembler = AnthropicEventAssembler()
                var failed = false
                api.streamChatMessage(
                    request,
                    platform.timeout,
                    ProviderRequestConfig(
                        apiUrl = platform.apiUrl,
                        token = platform.token,
                        anthropicBetaFeatures = thinkingPolicy.betaFeatures
                    )
                ).collect { chunk ->
                    assembler.accept(chunk).forEach { mapped ->
                        when (mapped) {
                            ProviderEvent.Completed -> Unit

                            is ProviderEvent.Failed -> {
                                failed = true
                                emit(mapped)
                            }

                            else -> emit(mapped)
                        }
                    }
                }
                assembler.replayContent().takeIf { it.isNotEmpty() }?.let { assistantContentByRound[exchanges.size] = it }
                if (!failed) emit(ProviderEvent.Completed)
            }
        }
    }
}

internal data class AnthropicThinkingPolicy(
    val config: AnthropicThinkingConfig?,
    val betaFeatures: Set<String>
)

internal fun anthropicThinkingPolicy(
    model: String,
    reasoningEnabled: Boolean,
    hasTools: Boolean
): AnthropicThinkingPolicy {
    val normalizedModel = model.lowercase()
    if (!reasoningEnabled) {
        val config = AnthropicThinkingConfig(type = "disabled")
            .takeIf { DEFAULT_ON_DISABLEABLE_ANTHROPIC_MODEL_PATTERN.containsMatchIn(normalizedModel) }
        return AnthropicThinkingPolicy(config = config, betaFeatures = emptySet())
    }

    val usesAdaptiveThinking = ADAPTIVE_ANTHROPIC_MODEL_PATTERN.containsMatchIn(normalizedModel) ||
        normalizedModel.contains("mythos") ||
        normalizedModel.contains("fable")
    if (usesAdaptiveThinking) {
        return AnthropicThinkingPolicy(
            config = AnthropicThinkingConfig(type = "adaptive", display = "summarized"),
            betaFeatures = emptySet()
        )
    }
    if (!MANUAL_THINKING_ANTHROPIC_MODEL_PATTERN.containsMatchIn(normalizedModel)) {
        return AnthropicThinkingPolicy(config = null, betaFeatures = emptySet())
    }

    val supportsManualInterleaving = hasTools &&
        (normalizedModel.contains("opus") || normalizedModel.contains("sonnet")) &&
        MANUAL_INTERLEAVED_ANTHROPIC_MODEL_PATTERN.containsMatchIn(normalizedModel)
    return AnthropicThinkingPolicy(
        config = AnthropicThinkingConfig(type = "enabled", budgetTokens = 10_000, display = "summarized"),
        betaFeatures = if (supportsManualInterleaving) setOf(ANTHROPIC_INTERLEAVED_THINKING_BETA) else emptySet()
    )
}

internal const val ANTHROPIC_INTERLEAVED_THINKING_BETA = "interleaved-thinking-2025-05-14"
private val ADAPTIVE_ANTHROPIC_MODEL_PATTERN = Regex(
    "(?:^|-)4-(?:6|7|8)(?:-|$)|claude-(?:opus|sonnet|haiku)-5(?:-|$)|claude-5-(?:opus|sonnet|haiku)(?:-|$)"
)
private val DEFAULT_ON_DISABLEABLE_ANTHROPIC_MODEL_PATTERN =
    Regex("claude-(?:opus|sonnet)-5(?:-|$)|claude-5-(?:opus|sonnet)(?:-|$)")
private val MANUAL_THINKING_ANTHROPIC_MODEL_PATTERN = Regex("(?:^|-)3-7(?:-|$)|(?:^|-)4(?:-|$)")
private val MANUAL_INTERLEAVED_ANTHROPIC_MODEL_PATTERN = Regex("(?:^|-)4(?:-|$)")

internal fun geminiToolParameters(schema: JsonObject): JsonObject {
    if ("additionalProperties" !in schema && schema.values.none { it is JsonObject || it is JsonArray }) {
        return schema
    }
    return buildJsonObject {
        schema.forEach { (key, value) ->
            if (key == "additionalProperties") return@forEach
            put(key, if (key in SCHEMA_MAP_KEYWORDS) stripAdditionalPropertiesInSchemaMap(value) else stripAdditionalProperties(value))
        }
    }
}

// Keys under these keywords are caller-defined names, so a property literally named
// additionalProperties must survive while the keyword itself is stripped everywhere else.
private val SCHEMA_MAP_KEYWORDS = setOf("properties", "patternProperties", "definitions", "\$defs")

private fun stripAdditionalPropertiesInSchemaMap(value: JsonElement): JsonElement = when (value) {
    is JsonObject -> buildJsonObject {
        value.forEach { (name, member) -> put(name, stripAdditionalProperties(member)) }
    }

    else -> stripAdditionalProperties(value)
}

private fun stripAdditionalProperties(value: JsonElement): JsonElement = when (value) {
    is JsonObject -> geminiToolParameters(value)
    is JsonArray -> JsonArray(value.map(::stripAdditionalProperties))
    else -> value
}

class GeminiAdapter @Inject constructor(
    private val api: GoogleAPI,
    private val attachmentEncoder: ProviderAttachmentEncoder
) {
    suspend fun openSession(turns: List<ConversationTurn>, platform: PlatformV2): AgentProviderSession {
        val initialContents = attachmentEncoder.googleContents(turns, platform.uid)
        val config = ProviderRequestConfig(platform.apiUrl, platform.token)
        val modelPartsByRound = mutableMapOf<Int, List<Part>>()
        return object : AgentProviderSession {
            override fun streamRound(
                tools: List<AgentToolDefinition>,
                exchanges: List<AgentToolExchange>
            ): Flow<ProviderEvent> = flow {
                val request = GenerateContentRequest(
                    contents = initialContents + exchanges.flatMapIndexed { index, exchange ->
                        exchange.toGeminiContents(modelPartsByRound[index])
                    },
                    generationConfig = GenerationConfig(
                        temperature = platform.temperature,
                        topP = platform.topP,
                        thinkingConfig = if (platform.reasoning) GoogleThinkingConfig(includeThoughts = true) else null
                    ),
                    systemInstruction = platform.systemPrompt?.takeIf { it.isNotBlank() }?.let { prompt ->
                        Content(parts = listOf(Part.text(prompt)))
                    },
                    safetySettings = platform.googleSafetySettings(),
                    tools = tools.takeIf { it.isNotEmpty() }?.let { definitions ->
                        listOf(
                            GoogleTool(
                                definitions.map { definition ->
                                    FunctionDeclaration(
                                        definition.name,
                                        definition.description,
                                        geminiToolParameters(definition.inputSchema)
                                    )
                                }
                            )
                        )
                    },
                    toolConfig = tools.takeIf { it.isNotEmpty() }?.let {
                        GoogleToolConfig(GoogleFunctionCallingConfig(mode = "AUTO"))
                    }
                )
                var failed = false
                api.streamGenerateContent(request, platform.model, platform.timeout, config).collect { response ->
                    val parts = response.candidates.orEmpty().flatMap { it.content?.parts.orEmpty() }
                    if (parts.isNotEmpty()) {
                        modelPartsByRound[exchanges.size] = modelPartsByRound[exchanges.size].orEmpty() + parts
                    }
                    val safetyError = when {
                        response.promptFeedback?.blockReason != null ->
                            "Gemini safety settings blocked the prompt: ${response.promptFeedback.blockReason}"

                        response.candidates.orEmpty().any { it.finishReason == "SAFETY" } ->
                            "Gemini safety settings blocked the response."

                        else -> null
                    }
                    if (safetyError != null) {
                        failed = true
                        emit(ProviderEvent.Failed(safetyError))
                    } else {
                        GeminiEventMapper.accept(response).forEach { mapped ->
                            if (mapped is ProviderEvent.Failed) failed = true
                            emit(mapped)
                        }
                    }
                }
                if (!failed) emit(ProviderEvent.Completed)
            }
        }
    }
}

private fun AgentToolExchange.toChatMessages(): List<ChatMessage> = listOf(
    ChatMessage(
        role = OpenAIRole.ASSISTANT,
        toolCalls = calls.map { call ->
            ChatToolCall(call.callId, ChatFunction(call.name, call.arguments.toString()))
        }
    )
) + results.map { result ->
    ChatMessage(
        role = OpenAIRole.TOOL,
        content = listOf(OpenAITextContent(result.modelText())),
        toolCallId = result.callId
    )
}

private fun dev.chungjungsoo.gptmobile.data.dto.ApiState.toProviderEvent(): ProviderEvent? = when (this) {
    is dev.chungjungsoo.gptmobile.data.dto.ApiState.Success -> ProviderEvent.TextDelta(textChunk)
    is dev.chungjungsoo.gptmobile.data.dto.ApiState.Thinking -> ProviderEvent.ThinkingDelta(thinkingChunk)
    is dev.chungjungsoo.gptmobile.data.dto.ApiState.Notice -> null
    is dev.chungjungsoo.gptmobile.data.dto.ApiState.Error -> ProviderEvent.Failed(message)
    else -> null
}

private fun AgentToolExchange.toAnthropicMessages(assistantContent: List<MessageContent>?): List<InputMessage> = listOf(
    InputMessage(
        MessageRole.ASSISTANT,
        assistantContent ?: calls.map { call -> ToolUseContent(call.callId, call.name, call.arguments) }
    ),
    InputMessage(
        MessageRole.USER,
        results.map { result ->
            AnthropicToolResultContent(result.callId, result.modelText(), result.isError)
        }
    )
)

private fun AgentToolExchange.toGeminiContents(modelParts: List<Part>?): List<Content> {
    val callsById = calls.associateBy { it.callId }
    val originalCalls = modelParts.orEmpty().mapNotNull { it.functionCall }
    return listOf(
        Content(
            GoogleRole.MODEL,
            modelParts ?: calls.map { call -> Part(functionCall = FunctionCall(call.callId, call.name, call.arguments)) }
        ),
        Content(
            GoogleRole.USER,
            results.mapNotNull { result ->
                val call = callsById[result.callId] ?: return@mapNotNull null
                val providerCallId = if (modelParts == null) result.callId else originalCalls.getOrNull(calls.indexOf(call))?.id
                Part(
                    functionResponse = FunctionResponse(
                        id = providerCallId,
                        name = call.name,
                        response = result.modelJson()
                    )
                )
            }
        )
    )
}

private fun dev.chungjungsoo.gptmobile.data.agent.AgentToolResult.modelText(): String = when (val value = content) {
    is ToolResultContent.Text -> value.text
    is ToolResultContent.Json -> value.value.toString()
    is ToolResultContent.ResourceLinks -> value.links.joinToString("\n") { link -> link.uri }
}

private fun dev.chungjungsoo.gptmobile.data.agent.AgentToolResult.modelJson(): JsonObject = when (val value = content) {
    is ToolResultContent.Json -> value.value.asResponseObject()

    is ToolResultContent.Text -> buildJsonObject {
        put("result", value.text)
        if (isError) put("isError", true)
    }

    is ToolResultContent.ResourceLinks -> buildJsonObject {
        put("resources", JsonArray(value.links.map { link -> JsonPrimitive(link.uri) }))
        if (isError) put("isError", true)
    }
}

private fun JsonElement.asResponseObject(): JsonObject = this as? JsonObject ?: buildJsonObject { put("result", this@asResponseObject) }

private fun PlatformV2.googleSafetySettings(): List<SafetySetting> = listOf(
    SafetySetting(
        GeminiSafetySettings.HARM_CATEGORY_HARASSMENT,
        GeminiSafetySettings.normalizeThreshold(harassmentSafetyThreshold)
    ),
    SafetySetting(
        GeminiSafetySettings.HARM_CATEGORY_HATE_SPEECH,
        GeminiSafetySettings.normalizeThreshold(hateSpeechSafetyThreshold)
    ),
    SafetySetting(
        GeminiSafetySettings.HARM_CATEGORY_SEXUALLY_EXPLICIT,
        GeminiSafetySettings.normalizeThreshold(sexuallyExplicitSafetyThreshold)
    ),
    SafetySetting(
        GeminiSafetySettings.HARM_CATEGORY_DANGEROUS_CONTENT,
        GeminiSafetySettings.normalizeThreshold(dangerousContentSafetyThreshold)
    )
)

private fun createGroqChatCompletionRequest(
    messages: List<ChatMessage>,
    platform: PlatformV2
): GroqChatCompletionRequest {
    val isGptOssModel = platform.model.contains("gpt-oss", ignoreCase = true)
    return GroqChatCompletionRequest(
        model = platform.model,
        messages = messages,
        stream = platform.stream,
        temperature = platform.temperature,
        topP = platform.topP,
        maxCompletionTokens = if (platform.reasoning) 8_192 else null,
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

private const val GROQ_OUTPUT_LIMIT_MESSAGE =
    "Groq reached the model output limit before producing a final answer."
