package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponsesRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ChatCompletionChunk
import dev.chungjungsoo.gptmobile.data.dto.openai.response.Choice
import dev.chungjungsoo.gptmobile.data.dto.openai.response.Delta
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ErrorDetail
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseCreatedEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseErrorEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseInProgressEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponsesStreamEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.UnknownEvent
import dev.chungjungsoo.gptmobile.data.model.FreeAiProvider
import dev.chungjungsoo.gptmobile.data.network.gateway.GatewayResponseMetadata
import dev.chungjungsoo.gptmobile.util.applyPlatformStreamingTimeout
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readLine
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable

class OpenAIAPIImpl @Inject constructor(
    private val networkClient: NetworkClient
) : OpenAIAPI {
    override suspend fun uploadFile(
        filePath: String,
        fileName: String,
        mimeType: String,
        config: ProviderRequestConfig
    ): UploadedProviderFile {
        val endpoint = config.buildEndpoint("files")
        val responseBody = networkClient().preparePost(endpoint) {
            config.token?.let { bearerAuth(it) }
            config.extraHeaders.forEach { (key, value) -> header(key, value) }
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("purpose", "user_data")
                        append(
                            "file",
                            File(filePath).readBytes(),
                            Headers.build {
                                append(HttpHeaders.ContentType, mimeType)
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            }
                        )
                    }
                )
            )
        }.body<String>()

        val uploadResponse = NetworkClient.openAIJson.decodeFromString<OpenAIFileResponse>(responseBody)
        return UploadedProviderFile(
            id = uploadResponse.id,
            mimeType = uploadResponse.mimeType ?: mimeType,
            name = uploadResponse.filename
        )
    }

    override suspend fun isFileAvailable(fileId: String, config: ProviderRequestConfig): Boolean {
        val endpoint = config.buildEndpoint("files/$fileId")
        return try {
            networkClient().prepareGet(endpoint) {
                config.token?.let { bearerAuth(it) }
                config.extraHeaders.forEach { (key, value) -> header(key, value) }
            }.execute { response ->
                response.status.isSuccess()
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun streamChatCompletion(
        request: ChatCompletionRequest,
        timeoutSeconds: Int,
        config: ProviderRequestConfig
    ): Flow<ChatCompletionChunk> = flow {
        var receivedAssistantPayload = false
        try {
            val free = config.freeProvider
            if (free != null) {
                check(free.isAvailable) { "LLM7 is awaiting provider approval for use in this app. Choose another Free provider." }
            }
            val preparedRequest = if (free == null) {
                request
            } else {
                request.copy(
                    model = free.model,
                    maxTokens = (request.maxTokens ?: free.maxOutputTokens).coerceIn(1, free.maxOutputTokens),
                    maxCompletionTokens = null,
                    tools = request.tools.takeIf { free.supportsTools },
                    toolChoice = request.toolChoice.takeIf { free.supportsTools },
                    models = null,
                    provider = null,
                    plugins = null,
                    topK = null,
                    reasoningEffort = null,
                    reasoning = null,
                    options = null,
                    transforms = null,
                    sessionId = null
                )
            }
            if (free == FreeAiProvider.POLLINATIONS) {
                val prompt = legacyPollinationsPrompt(preparedRequest)
                val legacyResponse = pollinationsCompletion {
                    FreeAiRequestLimiter.shared.withRequest(free) {
                        networkClient().prepareGet(pollinationsPromptUrl(prompt)) {
                            timeout { requestTimeoutMillis = timeoutSeconds.coerceIn(15, 120) * 1_000L }
                            accept(ContentType.Text.Plain)
                        }.execute { response ->
                            PollinationsResponse(response.status.value, response.body<String>(), response.headers[HttpHeaders.ContentType], response.headers[HttpHeaders.RetryAfter])
                        }
                    }
                }
                emit(ChatCompletionChunk(model = free.model, choices = listOf(Choice(delta = Delta(content = legacyResponse), finishReason = "stop"))))
                return@flow
            }
            val endpoint = config.buildEndpoint("chat/completions")

            val executeRequest: suspend () -> Unit = {
                networkClient().preparePost(endpoint) {
                    applyPlatformStreamingTimeout(timeoutSeconds)
                    contentType(ContentType.Application.Json)
                    setBody(NetworkClient.openAIJson.encodeToString(preparedRequest))
                    accept(if (request.stream) ContentType.Text.EventStream else ContentType.Application.Json)
                    config.token?.takeIf { it.isNotBlank() && free == null }?.let { bearerAuth(it) }
                    config.extraHeaders.forEach { (key, value) -> header(key, value) }
                }.execute { response ->
                    if (free != null && response.status.value == 429) {
                        throw FreeAiRateLimitException(free, FreeAiRequestLimiter.shared.defer(free, response.headers[HttpHeaders.RetryAfter]))
                    }
                    if (!response.status.isSuccess()) {
                        val errorBody = response.body<String>()
                        throwIfToolDefinitionsRejected(response.status.value, !request.tools.isNullOrEmpty(), errorBody)

                        val errorMessage = try {
                            val errorResponse = NetworkClient.openAIJson.decodeFromString<OpenAIErrorResponse>(errorBody)
                            errorResponse.error.message
                        } catch (_: Exception) {
                            "HTTP ${response.status.value}: $errorBody"
                        }

                        emit(
                            ChatCompletionChunk(
                                error = ErrorDetail(
                                    message = config.readableProviderError(errorMessage, response.status.value.toString()),
                                    type = "http_error",
                                    code = response.status.value.toString()
                                )
                            )
                        )
                        return@execute
                    }

                    // Capture Gateway headers from response
                    val gatewayJobId = response.headers["X-Gateway-Job-ID"]
                    val gatewayRequestId = response.headers["X-Gateway-Request-ID"]
                    val gatewayVersion = response.headers["X-Gateway-Version"]
                    val gatewayProgressProtocol = response.headers["X-Gateway-Progress-Protocol"]
                    val gatewaySingleflight = response.headers["X-Gateway-Singleflight"]

                    val gatewayMetadata = if (gatewayJobId != null || gatewayRequestId != null || gatewayVersion != null) {
                        GatewayResponseMetadata(
                            jobId = gatewayJobId,
                            requestId = gatewayRequestId,
                            version = gatewayVersion,
                            progressProtocol = gatewayProgressProtocol,
                            singleflightRole = gatewaySingleflight
                        )
                    } else {
                        null
                    }

                    // llama.cpp and other compatible providers return choices[].message for stream=false.
                    val responseType = response.contentType()
                    if (responseType?.match(ContentType.Application.Json) == true ||
                        (!request.stream && responseType?.match(ContentType.Text.EventStream) != true)
                    ) {
                        val decoded = NetworkClient.openAIJson.decodeFromString<ChatCompletionChunk>(response.body<String>())
                        val chunk = decoded.copy(
                            choices = decoded.choices?.map { choice ->
                                if (choice.message != null && choice.finishReason == null) choice.copy(finishReason = "stop") else choice
                            },
                            gatewayMetadata = gatewayMetadata ?: decoded.gatewayMetadata,
                            error = decoded.error?.let { error ->
                                error.copy(message = config.readableProviderError(error.message, error.code))
                            }
                        )
                        emit(chunk)
                        if (free != null) {
                            freeResponseOutcome(
                                free,
                                chunk.choices.orEmpty().any { !it.effectiveDelta.content.isNullOrBlank() },
                                chunk.choices.orEmpty().any { !it.effectiveDelta.toolCalls.isNullOrEmpty() },
                                chunk.error != null,
                                chunk.choices.orEmpty().any { it.finishReason == "length" }
                            )?.let { emit(it) }
                        }
                        return@execute
                    }

                    // If gateway metadata is present, emit an initial chunk carrying the metadata
                    var firstChunk = true
                    var receivedToolCalls = false
                    var receivedAnswer = false
                    var receivedError = false
                    var reachedOutputLimit = false

                    // Success - read SSE stream
                    val channel = response.bodyAsChannel()
                    while (!channel.isClosedForRead) {
                        val line = channel.readLine() ?: break
                        val data = SseUtils.extractSseData(line) ?: continue

                        // OpenAI sends "[DONE]" as final message
                        if (data == "[DONE]") {
                            if (receivedToolCalls) emit(ChatCompletionChunk(streamFinished = true))
                            break
                        }

                        val decoded = try {
                            NetworkClient.openAIJson.decodeFromString<ChatCompletionChunk>(data)
                        } catch (_: kotlinx.serialization.SerializationException) {
                            // Skip malformed data without swallowing collector failures or cancellation.
                            continue
                        }
                        val chunk = decoded.error?.let { error ->
                            decoded.copy(error = error.copy(message = config.readableProviderError(error.message, error.code)))
                        } ?: decoded
                        receivedAnswer = receivedAnswer || chunk.choices.orEmpty().any { !it.effectiveDelta.content.isNullOrBlank() }
                        receivedError = receivedError || chunk.error != null
                        reachedOutputLimit = reachedOutputLimit || chunk.choices.orEmpty().any { it.finishReason == "length" }
                        receivedAssistantPayload = receivedAssistantPayload || chunk.hasAssistantStreamPayload()
                        receivedToolCalls = receivedToolCalls || chunk.choices.orEmpty().any { !it.effectiveDelta.toolCalls.isNullOrEmpty() }
                        if (firstChunk && gatewayMetadata != null) {
                            firstChunk = false
                            emit(chunk.copy(gatewayMetadata = gatewayMetadata))
                        } else {
                            emit(chunk)
                        }
                    }

                    if (free != null) freeResponseOutcome(free, receivedAnswer, receivedToolCalls, receivedError, reachedOutputLimit)?.let { emit(it) }
                    // If no chunks were emitted but metadata was present, emit a metadata chunk
                    if (firstChunk && gatewayMetadata != null) {
                        emit(ChatCompletionChunk(gatewayMetadata = gatewayMetadata))
                    }
                }
            }
            if (free != null) FreeAiRequestLimiter.shared.withRequest(free, executeRequest) else executeRequest()
        } catch (e: Exception) {
            if (e is CancellationException || e is dev.chungjungsoo.gptmobile.data.agent.ToolDefinitionsRejectedException) throw e
            if (ResilientStreamingClient.shouldTreatPrematureCloseAsStreamEnd(receivedAssistantPayload, e)) {
                return@flow
            }
            val errorMessage = when (e) {
                is java.net.UnknownHostException -> "Network error: Unable to resolve host."
                is java.nio.channels.UnresolvedAddressException -> "Network error: Unable to resolve address. Check your internet connection."
                is java.net.ConnectException -> "Network error: Connection refused. Check the API URL."
                is HttpRequestTimeoutException -> "Request timed out."
                is java.net.SocketTimeoutException -> "Response timed out while waiting for the next chunk."
                is javax.net.ssl.SSLException -> "Network error: SSL/TLS connection failed."
                else -> if (ResilientStreamingClient.isPrematureConnectionClose(e)) {
                    "Connection closed before the response started. Please retry."
                } else {
                    e.message ?: "Unknown network error"
                }
            }
            emit(
                ChatCompletionChunk(
                    error = ErrorDetail(
                        message = errorMessage,
                        type = "network_error"
                    )
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    override fun streamResponses(
        request: ResponsesRequest,
        timeoutSeconds: Int,
        config: ProviderRequestConfig
    ): Flow<ResponsesStreamEvent> = flow {
        var receivedResponsePayload = false
        try {
            val endpoint = config.buildEndpoint("responses")

            networkClient().preparePost(endpoint) {
                applyPlatformStreamingTimeout(timeoutSeconds)
                contentType(ContentType.Application.Json)
                setBody(NetworkClient.openAIJson.encodeToString(request))
                accept(ContentType.Text.EventStream)
                config.token?.let { bearerAuth(it) }
                config.extraHeaders.forEach { (key, value) -> header(key, value) }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val errorBody = response.body<String>()
                    throwIfToolDefinitionsRejected(response.status.value, !request.tools.isNullOrEmpty(), errorBody)

                    val errorMessage = try {
                        val errorResponse = NetworkClient.openAIJson.decodeFromString<OpenAIErrorResponse>(errorBody)
                        errorResponse.error.message
                    } catch (_: Exception) {
                        "HTTP ${response.status.value}: $errorBody"
                    }

                    emit(ResponseErrorEvent(message = config.readableProviderError(errorMessage, response.status.value.toString()), code = response.status.value.toString()))
                    return@execute
                }

                // Success - read SSE stream
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readLine() ?: break
                    val data = SseUtils.extractSseData(line) ?: continue

                    if (data == "[DONE]") break

                    try {
                        val decoded = NetworkClient.openAIJson.decodeFromString<ResponsesStreamEvent>(data)
                        val streamEvent = if (decoded is ResponseErrorEvent) {
                            decoded.copy(message = config.readableProviderError(decoded.message, decoded.code))
                        } else {
                            decoded
                        }
                        receivedResponsePayload = receivedResponsePayload || streamEvent.hasResponseStreamPayload()
                        emit(streamEvent)
                    } catch (_: Exception) {
                        emit(UnknownEvent)
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException || e is dev.chungjungsoo.gptmobile.data.agent.ToolDefinitionsRejectedException) throw e
            if (ResilientStreamingClient.shouldTreatPrematureCloseAsStreamEnd(receivedResponsePayload, e)) {
                return@flow
            }
            val errorMessage = when (e) {
                is java.net.UnknownHostException -> "Network error: Unable to resolve host."
                is java.nio.channels.UnresolvedAddressException -> "Network error: Unable to resolve address. Check your internet connection."
                is java.net.ConnectException -> "Network error: Connection refused. Check the API URL."
                is HttpRequestTimeoutException -> "Request timed out."
                is java.net.SocketTimeoutException -> "Response timed out while waiting for the next chunk."
                is javax.net.ssl.SSLException -> "Network error: SSL/TLS connection failed."
                else -> if (ResilientStreamingClient.isPrematureConnectionClose(e)) {
                    "Connection closed before the response started. Please retry."
                } else {
                    e.message ?: "Unknown network error"
                }
            }
            emit(
                ResponseErrorEvent(
                    message = errorMessage,
                    code = "network_error"
                )
            )
        }
    }.flowOn(Dispatchers.IO)
}

private fun ChatCompletionChunk.hasAssistantStreamPayload(): Boolean =
    choices.orEmpty().any { choice ->
        val delta = choice.effectiveDelta
        !delta.content.isNullOrEmpty() ||
            !delta.effectiveReasoning.isNullOrEmpty() ||
            !delta.toolCalls.isNullOrEmpty() ||
            choice.finishReason != null
    }

private fun ResponsesStreamEvent.hasResponseStreamPayload(): Boolean = when (this) {
    is ResponseCreatedEvent,
    is ResponseInProgressEvent,
    UnknownEvent -> false

    else -> true
}

@Serializable
private data class OpenAIErrorResponse(
    val error: OpenAIError
)

@Serializable
private data class OpenAIError(
    val message: String,
    val type: String? = null,
    val param: String? = null,
    val code: String? = null
)

@Serializable
private data class OpenAIFileResponse(
    val id: String,
    val filename: String? = null,
    @kotlinx.serialization.SerialName("mime_type")
    val mimeType: String? = null
)

private fun freeResponseOutcome(provider: FreeAiProvider, hasAnswer: Boolean, hasTools: Boolean, hasError: Boolean, limited: Boolean): ChatCompletionChunk? = when {
    hasError || hasTools -> null
    limited -> ChatCompletionChunk(model = provider.model, choices = listOf(Choice(delta = Delta(content = "\n\nThis model reached its response limit. Would you like me to continue?"), finishReason = "stop")))
    !hasAnswer -> ChatCompletionChunk(error = ErrorDetail(message = "${provider.displayName} returned no answer. Retry later or choose another Free provider.", type = "empty_response"))
    else -> null
}
