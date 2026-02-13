package dev.chungjungsoo.gptmobile.data.repository

import android.util.Log
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomDao
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageDao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageV2Dao
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoom
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.Message
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.ImageContent as AnthropicImageContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.ImageSource
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.ImageSourceType
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.MediaType
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.MessageContent as AnthropicMessageContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.MessageRole
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.TextContent as AnthropicTextContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.ToolResultContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.ToolUseContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.AnthropicTool
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.InputMessage
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.MessageRequest
import dev.chungjungsoo.gptmobile.data.dto.google.common.Content
import dev.chungjungsoo.gptmobile.data.dto.google.common.FunctionCall
import dev.chungjungsoo.gptmobile.data.dto.google.common.Part
import dev.chungjungsoo.gptmobile.data.dto.google.common.Role as GoogleRole
import dev.chungjungsoo.gptmobile.data.dto.google.request.GenerateContentRequest
import dev.chungjungsoo.gptmobile.data.dto.google.request.GenerationConfig
import dev.chungjungsoo.gptmobile.data.dto.google.request.GoogleTool
import dev.chungjungsoo.gptmobile.data.dto.openai.common.ImageContent as OpenAIImageContent
import dev.chungjungsoo.gptmobile.data.dto.openai.common.ImageUrl
import dev.chungjungsoo.gptmobile.data.dto.openai.common.MessageContent as OpenAIMessageContent
import dev.chungjungsoo.gptmobile.data.dto.openai.common.Role as OpenAIRole
import dev.chungjungsoo.gptmobile.data.dto.openai.common.TextContent as OpenAITextContent
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatMessage
import dev.chungjungsoo.gptmobile.data.dto.openai.request.OpenAIFunctionCall
import dev.chungjungsoo.gptmobile.data.dto.openai.request.OpenAIToolCall
import dev.chungjungsoo.gptmobile.data.dto.openai.request.OpenAITool
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ReasoningConfig
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseContentPart
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseInputContent
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseInputMessage
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponsesRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.response.OutputItemDoneEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.OutputTextDeltaEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ReasoningSummaryTextDeltaEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseErrorEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseFailedEvent
import dev.chungjungsoo.gptmobile.data.dto.tool.ProviderTools
import dev.chungjungsoo.gptmobile.data.dto.tool.Tool
import dev.chungjungsoo.gptmobile.data.dto.tool.ToolCall
import dev.chungjungsoo.gptmobile.data.dto.tool.ToolConverter
import dev.chungjungsoo.gptmobile.data.dto.tool.ToolResult
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPI
import dev.chungjungsoo.gptmobile.data.network.GoogleAPI
import dev.chungjungsoo.gptmobile.data.network.OpenAIAPI
import dev.chungjungsoo.gptmobile.data.tool.ToolExecutor
import dev.chungjungsoo.gptmobile.util.FileUtils
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class ChatRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatRoomDao: ChatRoomDao,
    private val messageDao: MessageDao,
    private val chatRoomV2Dao: ChatRoomV2Dao,
    private val messageV2Dao: MessageV2Dao,
    private val settingRepository: SettingRepository,
    private val openAIAPI: OpenAIAPI,
    private val anthropicAPI: AnthropicAPI,
    private val googleAPI: GoogleAPI,
    private val toolExecutor: ToolExecutor
) : ChatRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val maxToolIterations = 10

    private fun isImageFile(extension: String): Boolean = extension in setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff", "svg")

    private fun isDocumentFile(extension: String): Boolean = extension in setOf("pdf", "txt", "doc", "docx", "xls", "xlsx")

    private fun getMimeType(extension: String): String = when (extension) {
        // Images
        "jpg", "jpeg" -> "image/jpeg"

        "png" -> "image/png"

        "gif" -> "image/gif"

        "bmp" -> "image/bmp"

        "webp" -> "image/webp"

        "tiff" -> "image/tiff"

        "svg" -> "image/svg+xml"

        // Documents
        "pdf" -> "application/pdf"

        "txt" -> "text/plain"

        "doc" -> "application/msword"

        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

        "xls" -> "application/vnd.ms-excel"

        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

        else -> "application/octet-stream"
    }

    override suspend fun completeChat(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2,
        tools: List<Tool>?
    ): Flow<ApiState> {
        val providerTools = tools?.let { ToolConverter.convertToolsForProvider(it, platform.compatibleType) }
        
        return when (platform.compatibleType) {
            ClientType.OPENAI -> {
                completeChatWithOpenAIResponses(userMessages, assistantMessages, platform, providerTools)
            }

            ClientType.GROQ, ClientType.OLLAMA, ClientType.OPENROUTER, ClientType.CUSTOM -> {
                completeChatWithOpenAIChatCompletions(userMessages, assistantMessages, platform, providerTools)
            }

            ClientType.ANTHROPIC -> {
                completeChatWithAnthropic(userMessages, assistantMessages, platform, providerTools)
            }

            ClientType.GOOGLE -> {
                completeChatWithGoogle(userMessages, assistantMessages, platform, providerTools)
            }
        }
    }

    private suspend fun completeChatWithOpenAIResponses(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2,
        providerTools: ProviderTools?
    ): Flow<ApiState> = flow {
        try {
            openAIAPI.setToken(platform.token)
            openAIAPI.setAPIUrl(platform.apiUrl)

            emit(ApiState.Loading)

            val openAITools = (providerTools as? ProviderTools.OpenAI)?.tools
            val inputMessages = buildInitialResponsesInput(userMessages, assistantMessages, platform)
            var unproductiveToolIterations = 0

            repeat(maxToolIterations) {
                val request = ResponsesRequest(
                    model = platform.model,
                    input = inputMessages,
                    stream = true,
                    instructions = platform.systemPrompt?.takeIf { it.isNotBlank() },
                    temperature = if (platform.reasoning) null else platform.temperature,
                    topP = if (platform.reasoning) null else platform.topP,
                    reasoning = if (platform.reasoning) {
                        ReasoningConfig(
                            effort = "medium",
                            summary = "auto"
                        )
                    } else {
                        null
                    },
                    tools = openAITools,
                    toolChoice = if (openAITools.isNullOrEmpty()) null else "auto"
                )

                val parsedToolCalls = mutableListOf<ToolCall>()
                var hasError = false

                openAIAPI.streamResponses(request).collect { event ->
                    when (event) {
                        is ReasoningSummaryTextDeltaEvent -> emit(ApiState.Thinking(event.delta))
                        is OutputTextDeltaEvent -> emit(ApiState.Success(event.delta))
                        is OutputItemDoneEvent -> {
                            if (event.item.type == "function_call") {
                                toToolCall(
                                    id = event.item.callId ?: event.item.id,
                                    name = event.item.name,
                                    arguments = event.item.arguments
                                )?.let { parsedToolCalls.add(it) }
                            }
                        }
                        is ResponseFailedEvent -> {
                            hasError = true
                            emit(ApiState.Error(event.response.error?.message ?: "Response failed"))
                        }
                        is ResponseErrorEvent -> {
                            hasError = true
                            emit(ApiState.Error(event.message))
                        }
                        else -> {}
                    }
                }

                if (hasError) {
                    return@flow
                }

                if (parsedToolCalls.isEmpty()) {
                    emit(ApiState.Done)
                    return@flow
                }

                emit(ApiState.ToolCallRequested(parsedToolCalls))
                val results = executeToolCalls(parsedToolCalls) { emit(it) }
                emit(ApiState.ToolResultReceived(results))
                terminalToolError(results)?.let {
                    emit(ApiState.Error(it))
                    return@flow
                }
                unproductiveToolIterations = updateUnproductiveIterations(unproductiveToolIterations, results)
                if (unproductiveToolIterations >= MAX_UNPRODUCTIVE_TOOL_ITERATIONS) {
                    emit(ApiState.Error("Tool loop detected. No useful tool output was returned."))
                    return@flow
                }

                inputMessages.add(
                    ResponseInputMessage(
                        role = "assistant",
                        content = ResponseInputContent.parts(
                            parsedToolCalls.map {
                                ResponseContentPart(
                                    type = "function_call",
                                    text = null,
                                    imageUrl = null,
                                    detail = null,
                                    callId = it.id,
                                    name = it.name,
                                    arguments = json.encodeToString(JsonObject.serializer(), it.arguments)
                                )
                            }
                        )
                    )
                )

                inputMessages.add(
                    ResponseInputMessage(
                        role = "user",
                        content = ResponseInputContent.parts(
                            results.map {
                                ResponseContentPart(
                                    type = "function_call_output",
                                    text = null,
                                    imageUrl = null,
                                    detail = null,
                                    callId = it.callId,
                                    output = it.output
                                )
                            }
                        )
                    )
                )
            }

            emit(ApiState.Error("Too many tool call iterations"))
        } catch (e: Exception) {
            emit(ApiState.Error(e.message ?: "Failed to complete chat"))
        }
    }

    private suspend fun completeChatWithOpenAIChatCompletions(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2,
        providerTools: ProviderTools?
    ): Flow<ApiState> = flow {
        try {
            openAIAPI.setToken(platform.token)
            openAIAPI.setAPIUrl(platform.apiUrl)

            emit(ApiState.Loading)

            val messages = buildInitialChatMessages(userMessages, assistantMessages, platform)
            val openAITools = (providerTools as? ProviderTools.OpenAI)?.tools
            var unproductiveToolIterations = 0

            repeat(maxToolIterations) {
                val request = ChatCompletionRequest(
                    model = platform.model,
                    messages = messages,
                    stream = platform.stream,
                    temperature = platform.temperature,
                    topP = platform.topP,
                    tools = openAITools,
                    toolChoice = if (openAITools.isNullOrEmpty()) null else "auto"
                )

                val toolCallBuilders = linkedMapOf<Int, MutableOpenAIToolCall>()
                var sawToolCalls = false
                var hasStreamError = false
                var streamErrorMessage = ""
                val announcedToolCallIds = mutableSetOf<String>()

                openAIAPI.streamChatCompletion(request).collect { chunk ->
                    when {
                        chunk.error != null -> {
                            hasStreamError = true
                            streamErrorMessage = chunk.error.message
                            return@collect
                        }
                        chunk.choices?.firstOrNull()?.delta?.content != null -> {
                            emit(ApiState.Success(chunk.choices.first().delta.content ?: ""))
                        }
                    }

                    val deltaToolCalls = chunk.choices?.firstOrNull()?.delta?.toolCalls.orEmpty()
                    if (deltaToolCalls.isNotEmpty()) {
                        sawToolCalls = true
                    }

                    deltaToolCalls.forEach { delta ->
                        val builder = toolCallBuilders.getOrPut(delta.index) { MutableOpenAIToolCall() }
                        delta.id?.let { builder.id = it }
                        delta.function?.name?.let { builder.name = it }
                        delta.function?.arguments?.let { chunk ->
                            builder.arguments.append(chunk)
                            Log.i(
                                TAG,
                                "toolCallDelta index=${delta.index} id=${builder.id} name=${builder.name} chunkLen=${chunk.length} totalLen=${builder.arguments.length}"
                            )
                        }
                        val callId = builder.id
                        val toolName = builder.name
                        if (callId != null && toolName != null && announcedToolCallIds.add(callId)) {
                            emit(
                                ApiState.ToolCallRequested(
                                    listOf(
                                        ToolCall(
                                            id = callId,
                                            name = toolName,
                                            arguments = buildJsonObject {}
                                        )
                                    )
                                )
                            )
                        }
                    }
                }

                if (hasStreamError) {
                    emit(ApiState.Error(streamErrorMessage.ifBlank { "Chat completion failed" }))
                    return@flow
                }

                if (!sawToolCalls || toolCallBuilders.isEmpty()) {
                    emit(ApiState.Done)
                    return@flow
                }

                val toolCalls = toolCallBuilders.values.mapNotNull { it.toToolCallWithLog() }
                if (toolCalls.isEmpty()) {
                    Log.w(
                        TAG,
                        "Failed to parse tool calls. builders=${
                            toolCallBuilders.values.joinToString { builder ->
                                "id=${builder.id},name=${builder.name},argsLen=${builder.arguments.length},args=${builder.arguments.toString().take(MAX_LOGGED_ARGUMENT_CHARS)}"
                            }
                        }"
                    )
                    emit(ApiState.Error("Failed to parse tool calls"))
                    return@flow
                }

                emit(ApiState.ToolCallRequested(toolCalls))
                val results = executeToolCalls(toolCalls) { emit(it) }
                emit(ApiState.ToolResultReceived(results))
                terminalToolError(results)?.let {
                    emit(ApiState.Error(it))
                    return@flow
                }
                unproductiveToolIterations = updateUnproductiveIterations(unproductiveToolIterations, results)
                if (unproductiveToolIterations >= MAX_UNPRODUCTIVE_TOOL_ITERATIONS) {
                    emit(ApiState.Error("Tool loop detected. No useful tool output was returned."))
                    return@flow
                }

                messages.add(
                    ChatMessage(
                        role = OpenAIRole.ASSISTANT,
                        content = null,
                        toolCalls = toolCalls.map {
                            OpenAIToolCall(
                                id = it.id,
                                function = OpenAIFunctionCall(
                                    name = it.name,
                                    arguments = json.encodeToString(JsonObject.serializer(), it.arguments)
                                )
                            )
                        }
                    )
                )

                results.forEach { result ->
                    messages.add(
                        ChatMessage(
                            role = OpenAIRole.TOOL,
                            content = listOf(OpenAITextContent(text = result.output)),
                            toolCallId = result.callId
                        )
                    )
                }
            }

            emit(ApiState.Error("Too many tool call iterations"))
        } catch (e: Exception) {
            emit(ApiState.Error(e.message ?: "Failed to complete chat"))
        }
    }

    private fun transformMessageV2ToChatMessage(message: MessageV2, isUser: Boolean): ChatMessage {
        val content = mutableListOf<OpenAIMessageContent>()

        // Add text content
        if (message.content.isNotBlank()) {
            content.add(OpenAITextContent(text = message.content))
        }

        // Add file content (images)
        message.files.forEach { fileUri ->
            val mimeType = FileUtils.getMimeType(context, fileUri)
            if (FileUtils.isImage(mimeType)) {
                val base64 = FileUtils.readAndEncodeFile(context, fileUri)
                if (base64 != null) {
                    content.add(
                        OpenAIImageContent(
                            imageUrl = ImageUrl(url = "data:$mimeType;base64,$base64")
                        )
                    )
                }
            }
        }

        return ChatMessage(
            role = if (isUser) OpenAIRole.USER else OpenAIRole.ASSISTANT,
            content = content
        )
    }

    private fun transformMessageV2ToResponsesInput(message: MessageV2, isUser: Boolean): ResponseInputMessage {
        val role = if (isUser) "user" else "assistant"

        // Check if there are any image files
        val imageFiles = message.files.filter { fileUri ->
            val mimeType = FileUtils.getMimeType(context, fileUri)
            FileUtils.isImage(mimeType)
        }

        // If no images, use simple text content
        if (imageFiles.isEmpty()) {
            return ResponseInputMessage(
                role = role,
                content = ResponseInputContent.text(message.content)
            )
        }

        // Build content parts for text + images
        val parts = mutableListOf<ResponseContentPart>()

        // Add text content if not blank
        if (message.content.isNotBlank()) {
            parts.add(ResponseContentPart.text(message.content))
        }

        // Add image content
        imageFiles.forEach { fileUri ->
            val mimeType = FileUtils.getMimeType(context, fileUri)
            val base64 = FileUtils.readAndEncodeFile(context, fileUri)
            if (base64 != null) {
                parts.add(ResponseContentPart.image("data:$mimeType;base64,$base64"))
            }
        }

        return ResponseInputMessage(
            role = role,
            content = ResponseInputContent.parts(parts)
        )
    }

    private suspend fun completeChatWithAnthropic(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2,
        providerTools: ProviderTools?
    ): Flow<ApiState> = flow {
        try {
            anthropicAPI.setToken(platform.token)
            anthropicAPI.setAPIUrl(platform.apiUrl)

            emit(ApiState.Loading)

            val messages = buildInitialAnthropicMessages(userMessages, assistantMessages, platform)
            val anthropicTools = (providerTools as? ProviderTools.Anthropic)?.tools
            var unproductiveToolIterations = 0

            repeat(maxToolIterations) {
                val request = MessageRequest(
                    model = platform.model,
                    messages = messages,
                    maxTokens = if (platform.reasoning) 16000 else 4096,
                    stream = platform.stream,
                    systemPrompt = platform.systemPrompt,
                    temperature = if (platform.reasoning) null else platform.temperature,
                    topP = if (platform.reasoning) null else platform.topP,
                    thinking = if (platform.reasoning) {
                        dev.chungjungsoo.gptmobile.data.dto.anthropic.request.ThinkingConfig(
                            type = "enabled",
                            budgetTokens = 10000
                        )
                    } else {
                        null
                    },
                    tools = anthropicTools
                )

                val toolCallBuilders = mutableMapOf<Int, MutableAnthropicToolCall>()
                var sawToolCalls = false
                var hasError = false

                anthropicAPI.streamChatMessage(request).collect { chunk ->
                    when (chunk) {
                        is dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentStartResponseChunk -> {
                            if (chunk.contentBlock.type == dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentBlockType.TOOL_USE) {
                                sawToolCalls = true
                                toolCallBuilders[chunk.index] = MutableAnthropicToolCall(
                                    id = chunk.contentBlock.id,
                                    name = chunk.contentBlock.name,
                                    argumentsObject = chunk.contentBlock.input,
                                    argumentsBuilder = StringBuilder()
                                )
                            }
                        }
                        is dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentDeltaResponseChunk -> {
                            when (chunk.delta.type) {
                                dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentBlockType.THINKING_DELTA -> {
                                    chunk.delta.thinking?.let { emit(ApiState.Thinking(it)) }
                                }
                                dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentBlockType.DELTA -> {
                                    chunk.delta.text?.let { emit(ApiState.Success(it)) }
                                }
                                dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentBlockType.INPUT_JSON_DELTA -> {
                                    toolCallBuilders[chunk.index]?.argumentsBuilder?.append(chunk.delta.partialJson ?: "")
                                }
                                else -> {}
                            }
                        }
                        is dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ErrorResponseChunk -> {
                            hasError = true
                            emit(ApiState.Error(chunk.error.message))
                        }
                        else -> {}
                    }
                }

                if (hasError) {
                    return@flow
                }

                if (!sawToolCalls || toolCallBuilders.isEmpty()) {
                    emit(ApiState.Done)
                    return@flow
                }

                val toolCalls = toolCallBuilders.values.mapNotNull { it.toToolCall() }
                if (toolCalls.isEmpty()) {
                    emit(ApiState.Error("Failed to parse tool calls"))
                    return@flow
                }

                emit(ApiState.ToolCallRequested(toolCalls))
                val results = executeToolCalls(toolCalls) { emit(it) }
                emit(ApiState.ToolResultReceived(results))
                terminalToolError(results)?.let {
                    emit(ApiState.Error(it))
                    return@flow
                }
                unproductiveToolIterations = updateUnproductiveIterations(unproductiveToolIterations, results)
                if (unproductiveToolIterations >= MAX_UNPRODUCTIVE_TOOL_ITERATIONS) {
                    emit(ApiState.Error("Tool loop detected. No useful tool output was returned."))
                    return@flow
                }

                messages.add(
                    InputMessage(
                        role = MessageRole.ASSISTANT,
                        content = toolCalls.map {
                            ToolUseContent(
                                id = it.id,
                                name = it.name,
                                input = it.arguments
                            )
                        }
                    )
                )
                messages.add(
                    InputMessage(
                        role = MessageRole.USER,
                        content = results.map {
                            ToolResultContent(
                                toolUseId = it.callId,
                                content = it.output,
                                isError = it.isError
                            )
                        }
                    )
                )
            }

            emit(ApiState.Error("Too many tool call iterations"))
        } catch (e: Exception) {
            emit(ApiState.Error(e.message ?: "Failed to complete chat"))
        }
    }

    private fun transformMessageV2ToAnthropic(message: MessageV2, role: MessageRole): InputMessage {
        val content = mutableListOf<AnthropicMessageContent>()

        // Add text content
        if (message.content.isNotBlank()) {
            content.add(AnthropicTextContent(text = message.content))
        }

        // Add file content (images)
        message.files.forEach { fileUri ->
            val mimeType = FileUtils.getMimeType(context, fileUri)
            if (FileUtils.isImage(mimeType)) {
                val base64 = FileUtils.readAndEncodeFile(context, fileUri)
                if (base64 != null) {
                    val mediaType = when {
                        mimeType.contains("jpeg") || mimeType.contains("jpg") -> MediaType.JPEG
                        mimeType.contains("png") -> MediaType.PNG
                        mimeType.contains("gif") -> MediaType.GIF
                        mimeType.contains("webp") -> MediaType.WEBP
                        else -> MediaType.JPEG // Default
                    }

                    content.add(
                        AnthropicImageContent(
                            source = ImageSource(
                                type = ImageSourceType.BASE64,
                                mediaType = mediaType,
                                data = base64
                            )
                        )
                    )
                }
            }
        }

        return InputMessage(role = role, content = content)
    }

    private suspend fun completeChatWithGoogle(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2,
        providerTools: ProviderTools?
    ): Flow<ApiState> = flow {
        try {
            googleAPI.setToken(platform.token)
            googleAPI.setAPIUrl(platform.apiUrl)

            emit(ApiState.Loading)

            val contents = buildInitialGoogleContents(userMessages, assistantMessages, platform)
            val googleTools = (providerTools as? ProviderTools.Google)?.tools
            var unproductiveToolIterations = 0

            repeat(maxToolIterations) {
                val request = GenerateContentRequest(
                    contents = contents,
                    generationConfig = GenerationConfig(
                        temperature = platform.temperature,
                        topP = platform.topP,
                        thinkingConfig = if (platform.reasoning) {
                            dev.chungjungsoo.gptmobile.data.dto.google.request.ThinkingConfig(
                                includeThoughts = true
                            )
                        } else {
                            null
                        }
                    ),
                    systemInstruction = platform.systemPrompt?.takeIf { it.isNotBlank() }?.let {
                        Content(parts = listOf(Part.text(it)))
                    },
                    tools = googleTools
                )

                val toolCalls = mutableListOf<ToolCall>()
                var hasError = false

                googleAPI.streamGenerateContent(request, platform.model).collect { response ->
                    when {
                        response.error != null -> {
                            hasError = true
                            emit(ApiState.Error(response.error.message))
                            return@collect
                        }
                        response.candidates?.firstOrNull()?.content?.parts != null -> {
                            val parts = response.candidates.first().content.parts
                            parts.forEach { part ->
                                part.text?.let { text ->
                                    if (part.thought == true) {
                                        emit(ApiState.Thinking(text))
                                    } else {
                                        emit(ApiState.Success(text))
                                    }
                                }

                                part.functionCall?.let { functionCall ->
                                    toolCalls.add(
                                        ToolCall(
                                            id = "google_${functionCall.name}_${toolCalls.size}",
                                            name = functionCall.name,
                                            arguments = functionCall.args
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                if (hasError) {
                    return@flow
                }

                if (toolCalls.isEmpty()) {
                    emit(ApiState.Done)
                    return@flow
                }

                emit(ApiState.ToolCallRequested(toolCalls))
                val results = executeToolCalls(toolCalls) { emit(it) }
                emit(ApiState.ToolResultReceived(results))
                terminalToolError(results)?.let {
                    emit(ApiState.Error(it))
                    return@flow
                }
                unproductiveToolIterations = updateUnproductiveIterations(unproductiveToolIterations, results)
                if (unproductiveToolIterations >= MAX_UNPRODUCTIVE_TOOL_ITERATIONS) {
                    emit(ApiState.Error("Tool loop detected. No useful tool output was returned."))
                    return@flow
                }

                contents.add(
                    Content(
                        role = GoogleRole.MODEL,
                        parts = toolCalls.map {
                            Part(functionCall = FunctionCall(name = it.name, args = it.arguments))
                        }
                    )
                )
                contents.add(
                    Content(
                        role = GoogleRole.USER,
                        parts = results.map {
                            Part.functionResponse(
                                name = it.name,
                                response = buildJsonObject {
                                    put("output", it.output)
                                    put("is_error", it.isError)
                                }
                            )
                        }
                    )
                )
            }

            emit(ApiState.Error("Too many tool call iterations"))
        } catch (e: Exception) {
            emit(ApiState.Error(e.message ?: "Failed to complete chat"))
        }
    }

    private fun transformMessageV2ToGoogle(message: MessageV2, role: GoogleRole): Content {
        val parts = mutableListOf<Part>()

        // Add text content
        if (message.content.isNotBlank()) {
            parts.add(Part.text(message.content))
        }

        // Add file content (images)
        message.files.forEach { fileUri ->
            val mimeType = FileUtils.getMimeType(context, fileUri)
            if (FileUtils.isImage(mimeType)) {
                val base64 = FileUtils.readAndEncodeFile(context, fileUri)
                if (base64 != null) {
                    parts.add(Part.inlineData(mimeType, base64))
                }
            }
        }

        return Content(role = role, parts = parts)
    }

    private fun buildInitialChatMessages(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): MutableList<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()

        platform.systemPrompt?.takeIf { it.isNotBlank() }?.let { systemPrompt ->
            messages.add(
                ChatMessage(
                    role = OpenAIRole.SYSTEM,
                    content = listOf(OpenAITextContent(text = systemPrompt))
                )
            )
        }

        userMessages.forEachIndexed { index, userMsg ->
            messages.add(transformMessageV2ToChatMessage(userMsg, isUser = true))

            if (index < assistantMessages.size) {
                assistantMessages[index]
                    .firstOrNull { it.content.isNotBlank() && it.platformType == platform.uid }
                    ?.let { assistantMsg ->
                        messages.add(transformMessageV2ToChatMessage(assistantMsg, isUser = false))
                    }
            }
        }

        return messages
    }

    private fun buildInitialResponsesInput(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): MutableList<ResponseInputMessage> {
        val inputMessages = mutableListOf<ResponseInputMessage>()

        userMessages.forEachIndexed { index, userMsg ->
            inputMessages.add(transformMessageV2ToResponsesInput(userMsg, isUser = true))
            if (index < assistantMessages.size) {
                assistantMessages[index]
                    .firstOrNull { it.content.isNotBlank() && it.platformType == platform.uid }
                    ?.let { assistantMsg ->
                        inputMessages.add(transformMessageV2ToResponsesInput(assistantMsg, isUser = false))
                    }
            }
        }

        return inputMessages
    }

    private fun buildInitialAnthropicMessages(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): MutableList<InputMessage> {
        val messages = mutableListOf<InputMessage>()
        userMessages.forEachIndexed { index, userMsg ->
            messages.add(transformMessageV2ToAnthropic(userMsg, MessageRole.USER))

            if (index < assistantMessages.size) {
                assistantMessages[index]
                    .firstOrNull { it.content.isNotBlank() && it.platformType == platform.uid }
                    ?.let { assistantMsg ->
                        messages.add(transformMessageV2ToAnthropic(assistantMsg, MessageRole.ASSISTANT))
                    }
            }
        }
        return messages
    }

    private fun buildInitialGoogleContents(
        userMessages: List<MessageV2>,
        assistantMessages: List<List<MessageV2>>,
        platform: PlatformV2
    ): MutableList<Content> {
        val contents = mutableListOf<Content>()
        userMessages.forEachIndexed { index, userMsg ->
            contents.add(transformMessageV2ToGoogle(userMsg, GoogleRole.USER))

            if (index < assistantMessages.size) {
                assistantMessages[index]
                    .firstOrNull { it.content.isNotBlank() && it.platformType == platform.uid }
                    ?.let { assistantMsg ->
                        contents.add(transformMessageV2ToGoogle(assistantMsg, GoogleRole.MODEL))
                    }
            }
        }

        return contents
    }

    private suspend fun executeToolCalls(
        toolCalls: List<ToolCall>,
        emitState: suspend (ApiState) -> Unit
    ): List<ToolResult> {
        val results = mutableListOf<ToolResult>()
        toolCalls.forEach { toolCall ->
            emitState(ApiState.ToolExecuting(toolCall.name))
            results.add(toolExecutor.execute(toolCall))
        }
        return results
    }

    private fun updateUnproductiveIterations(current: Int, results: List<ToolResult>): Int {
        if (results.isEmpty()) {
            return current
        }
        return if (results.all { isUnproductiveToolResult(it) }) current + 1 else 0
    }

    private fun terminalToolError(results: List<ToolResult>): String? {
        val output = results.joinToString("\n") { it.output }.lowercase()
        return when {
            output.contains("invalid api key") && output.contains("ctx7sk") ->
                "Context7 API key is missing or invalid. Add a valid key (ctx7sk...) in MCP server headers."
            else -> null
        }
    }

    private fun isUnproductiveToolResult(result: ToolResult): Boolean {
        if (result.isError) {
            return true
        }
        val normalized = result.output.lowercase()
        return normalized.isBlank() ||
            normalized.contains("\"results\":[]") ||
            normalized.contains("\"results\": []") ||
            normalized.contains("\"error\"") ||
            normalized.contains("mcp error") ||
            normalized.contains("input validation error") ||
            normalized.contains("invalid api key")
    }

    private fun toToolCall(id: String?, name: String?, arguments: String?): ToolCall? {
        if (id.isNullOrBlank() || name.isNullOrBlank()) {
            return null
        }

        val argumentsObject = parseArguments(arguments)
        return ToolCall(id = id, name = name, arguments = argumentsObject)
    }

    private fun parseArguments(arguments: String?): JsonObject {
        val payload = arguments?.trim().orEmpty()
        if (payload.isBlank()) {
            return buildJsonObject {}
        }

        val parsed = runCatching { Json.parseToJsonElement(payload) }.getOrNull() ?: return buildJsonObject {}
        return when (parsed) {
            is JsonObject -> parsed
            is JsonPrimitive -> {
                val nestedJson = parsed.content.trim()
                runCatching { Json.parseToJsonElement(nestedJson).jsonObject }.getOrElse { buildJsonObject {} }
            }
            else -> buildJsonObject {}
        }
    }

    private data class MutableOpenAIToolCall(
        var id: String? = null,
        var name: String? = null,
        val arguments: StringBuilder = StringBuilder()
    ) {
        fun toToolCallWithLog(): ToolCall? {
            val callId = id ?: return null
            val toolName = name ?: return null
            val args = try {
                Json.parseToJsonElement(arguments.toString()).jsonObject
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "toolCallParseFailed id=$callId name=$toolName argsRaw=${arguments.toString().take(MAX_LOGGED_ARGUMENT_CHARS)}",
                    e
                )
                buildJsonObject {}
            }

            return ToolCall(
                id = callId,
                name = toolName,
                arguments = args
            )
        }
    }

    private data class MutableAnthropicToolCall(
        var id: String?,
        var name: String?,
        var argumentsObject: JsonObject?,
        val argumentsBuilder: StringBuilder
    ) {
        fun toToolCall(): ToolCall? {
            val callId = id ?: return null
            val toolName = name ?: return null
            val args = argumentsObject ?: try {
                Json.parseToJsonElement(argumentsBuilder.toString()).jsonObject
            } catch (_: Exception) {
                buildJsonObject {}
            }

            return ToolCall(
                id = callId,
                name = toolName,
                arguments = args
            )
        }
    }

    companion object {
        private const val MAX_UNPRODUCTIVE_TOOL_ITERATIONS = 3
        private const val TAG = "ChatRepositoryImpl"
        private const val MAX_LOGGED_ARGUMENT_CHARS = 600
    }

    override suspend fun fetchChatList(): List<ChatRoom> = chatRoomDao.getChatRooms()

    override suspend fun fetchChatListV2(): List<ChatRoomV2> = chatRoomV2Dao.getChatRooms()

    override suspend fun searchChatsV2(query: String): List<ChatRoomV2> {
        if (query.isBlank()) {
            return chatRoomV2Dao.getChatRooms()
        }

        // Search by title
        val titleMatches = chatRoomV2Dao.searchChatRoomsByTitle(query)

        // Search by message content and get chat IDs
        val messageMatchChatIds = messageV2Dao.searchMessagesByContent(query)

        // Get all chat rooms and filter by message match IDs
        val allChatRooms = chatRoomV2Dao.getChatRooms()
        val messageMatches = allChatRooms.filter { it.id in messageMatchChatIds }

        // Combine results and remove duplicates, maintaining order by updatedAt
        return (titleMatches + messageMatches)
            .distinctBy { it.id }
            .sortedByDescending { it.updatedAt }
    }

    override suspend fun fetchMessages(chatId: Int): List<Message> = messageDao.loadMessages(chatId)

    override suspend fun fetchMessagesV2(chatId: Int): List<MessageV2> = messageV2Dao.loadMessages(chatId)

    override suspend fun migrateToChatRoomV2MessageV2() {
        val leftOverChatRoomV2s = chatRoomV2Dao.getChatRooms()
        chatRoomV2Dao.deleteChatRooms(*leftOverChatRoomV2s.toTypedArray())

        val chatList = fetchChatList()
        val platforms = settingRepository.fetchPlatformV2s()
        val apiTypeMap = mutableMapOf<ApiType, String>()

        platforms.forEach { platform ->
            when (platform.name) {
                "OpenAI" -> apiTypeMap[ApiType.OPENAI] = platform.uid
                "Anthropic" -> apiTypeMap[ApiType.ANTHROPIC] = platform.uid
                "Google" -> apiTypeMap[ApiType.GOOGLE] = platform.uid
                "Groq" -> apiTypeMap[ApiType.GROQ] = platform.uid
                "Ollama" -> apiTypeMap[ApiType.OLLAMA] = platform.uid
            }
        }

        chatList.forEach { chatRoom ->
            val messages = messageDao.loadMessages(chatRoom.id).map { m ->
                MessageV2(
                    id = m.id,
                    chatId = m.chatId,
                    content = m.content,
                    files = listOf(),
                    revisions = listOf(),
                    linkedMessageId = m.linkedMessageId,
                    platformType = m.platformType?.let { apiTypeMap[it] },
                    createdAt = m.createdAt
                )
            }

            chatRoomV2Dao.addChatRoom(
                ChatRoomV2(
                    id = chatRoom.id,
                    title = chatRoom.title,
                    enabledPlatform = chatRoom.enabledPlatform.map { apiTypeMap[it] ?: "" },
                    createdAt = chatRoom.createdAt,
                    updatedAt = chatRoom.createdAt
                )
            )

            messageV2Dao.addMessages(*messages.toTypedArray())
        }
    }

    override fun generateDefaultChatTitle(messages: List<MessageV2>): String? = messages.sortedBy { it.createdAt }.firstOrNull { it.platformType == null }?.content?.replace('\n', ' ')?.take(50)

    override suspend fun updateChatTitle(chatRoom: ChatRoomV2, title: String) {
        chatRoomV2Dao.editChatRoom(chatRoom.copy(title = title.replace('\n', ' ').take(50)))
    }

    override suspend fun saveChat(chatRoom: ChatRoomV2, messages: List<MessageV2>): ChatRoomV2 {
        if (chatRoom.id == 0) {
            // New Chat
            val chatId = chatRoomV2Dao.addChatRoom(chatRoom)
            val updatedMessages = messages.map { it.copy(chatId = chatId.toInt()) }
            messageV2Dao.addMessages(*updatedMessages.toTypedArray())

            val savedChatRoom = chatRoom.copy(id = chatId.toInt())
            updateChatTitle(savedChatRoom, updatedMessages[0].content)

            return savedChatRoom.copy(title = updatedMessages[0].content.replace('\n', ' ').take(50))
        }

        val savedMessages = fetchMessagesV2(chatRoom.id)
        val updatedMessages = messages.map { it.copy(chatId = chatRoom.id) }

        val shouldBeDeleted = savedMessages.filter { m ->
            updatedMessages.firstOrNull { it.id == m.id } == null
        }
        val shouldBeUpdated = updatedMessages.filter { m ->
            savedMessages.firstOrNull { it.id == m.id && it != m } != null
        }
        val shouldBeAdded = updatedMessages.filter { m ->
            savedMessages.firstOrNull { it.id == m.id } == null
        }

        chatRoomV2Dao.editChatRoom(chatRoom)
        messageV2Dao.deleteMessages(*shouldBeDeleted.toTypedArray())
        messageV2Dao.editMessages(*shouldBeUpdated.toTypedArray())
        messageV2Dao.addMessages(*shouldBeAdded.toTypedArray())

        return chatRoom
    }

    override suspend fun deleteChats(chatRooms: List<ChatRoom>) {
        chatRoomDao.deleteChatRooms(*chatRooms.toTypedArray())
    }

    override suspend fun deleteChatsV2(chatRooms: List<ChatRoomV2>) {
        chatRoomV2Dao.deleteChatRooms(*chatRooms.toTypedArray())
    }
}
