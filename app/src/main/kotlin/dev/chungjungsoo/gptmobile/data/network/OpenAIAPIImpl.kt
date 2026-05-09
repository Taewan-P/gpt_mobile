package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponsesRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ChatCompletionChunk
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ErrorDetail
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseErrorEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponsesStreamEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.UnknownEvent
import dev.chungjungsoo.gptmobile.util.applyPlatformStreamingTimeout
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import org.jsoup.Jsoup

class OpenAIAPIImpl @Inject constructor(
    private val networkClient: NetworkClient
) : OpenAIAPI {

    private var token: String? = null
    private var apiUrl: String = ModelConstants.OPENAI_API_URL

    override fun setToken(token: String?) {
        this.token = token
    }

    override fun setAPIUrl(url: String) {
        this.apiUrl = url
    }

    override suspend fun createConversation(): String {
        val endpoint = if (apiUrl.endsWith("/")) "${apiUrl}v1/conversations" else "$apiUrl/v1/conversations"
        return networkClient().preparePost(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { })
            token?.let { bearerAuth(it) }
        }.execute { response ->
            val responseBody = response.body<String>()
            if (!response.status.isSuccess()) {
                throw IllegalStateException(parseOpenAIHttpError(response.status.value, responseBody))
            }

            NetworkClient.openAIJson.decodeFromString<OpenAIConversationResponse>(responseBody).id
        }
    }

    override suspend fun uploadFile(filePath: String, fileName: String, mimeType: String): UploadedProviderFile {
        val endpoint = if (apiUrl.endsWith("/")) "${apiUrl}v1/files" else "$apiUrl/v1/files"
        val responseBody = networkClient().preparePost(endpoint) {
            token?.let { bearerAuth(it) }
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

    override suspend fun isFileAvailable(fileId: String): Boolean {
        val endpoint = if (apiUrl.endsWith("/")) "${apiUrl}v1/files/$fileId" else "$apiUrl/v1/files/$fileId"
        return try {
            networkClient().prepareGet(endpoint) {
                token?.let { bearerAuth(it) }
            }.execute { response ->
                response.status.isSuccess()
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun streamChatCompletion(request: ChatCompletionRequest, timeoutSeconds: Int): Flow<ChatCompletionChunk> = flow {
        try {
            val endpoint = if (apiUrl.endsWith("/")) "${apiUrl}v1/chat/completions" else "$apiUrl/v1/chat/completions"

            networkClient().preparePost(endpoint) {
                applyPlatformStreamingTimeout(timeoutSeconds)
                contentType(ContentType.Application.Json)
                setBody(NetworkClient.openAIJson.encodeToString(request))
                accept(if (request.stream) ContentType.Text.EventStream else ContentType.Application.Json)
                token?.let { bearerAuth(it) }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val errorBody = response.body<String>()
                    val errorMessage = parseOpenAIHttpError(response.status.value, errorBody)

                    emit(
                        ChatCompletionChunk(
                            error = ErrorDetail(
                                message = errorMessage,
                                type = "http_error",
                                code = response.status.value.toString()
                            )
                        )
                    )
                    return@execute
                }

                if (!request.stream) {
                    val responseBody = response.body<String>()
                    try {
                        val chunk = NetworkClient.openAIJson.decodeFromString<ChatCompletionChunk>(responseBody)
                        emit(chunk)
                    } catch (_: Exception) {
                        emit(
                            ChatCompletionChunk(
                                error = ErrorDetail(
                                    message = "Provider returned an unsupported response format.",
                                    type = "parse_error"
                                )
                            )
                        )
                    }
                    return@execute
                }

                // Success - read SSE stream
                val channel = response.bodyAsChannel()
                val rawResponseBuffer = StringBuilder()
                var sawStreamPayload = false
                var parsedChunkCount = 0
                var lastDataPayload: String? = null
                while (!channel.isClosedForRead) {
                    val line = channel.readLine() ?: break

                    if (line.startsWith("data:")) {
                        sawStreamPayload = true
                        val data = line.removePrefix("data:").trimStart().trim()
                        lastDataPayload = data
                        // OpenAI sends "[DONE]" as final message
                        if (data == "[DONE]") break

                        try {
                            val chunk = NetworkClient.openAIJson.decodeFromString<ChatCompletionChunk>(data)
                            emit(chunk)
                            parsedChunkCount++
                        } catch (_: Exception) {
                            // Skip malformed chunks
                        }
                    } else if (line.isNotBlank()) {
                        rawResponseBuffer.append(line.trim())
                    }
                }

                if (!sawStreamPayload && rawResponseBuffer.isNotBlank()) {
                    try {
                        val chunk = NetworkClient.openAIJson.decodeFromString<ChatCompletionChunk>(rawResponseBuffer.toString())
                        emit(chunk)
                    } catch (_: Exception) {
                        emit(
                            ChatCompletionChunk(
                                error = ErrorDetail(
                                    message = "Provider returned an unsupported response format.",
                                    type = "parse_error"
                                )
                            )
                        )
                    }
                } else if (sawStreamPayload && parsedChunkCount == 0) {
                    val fallbackPayload = lastDataPayload?.takeIf { it.isNotBlank() } ?: rawResponseBuffer.toString()
                    try {
                        val chunk = NetworkClient.openAIJson.decodeFromString<ChatCompletionChunk>(fallbackPayload)
                        emit(chunk)
                    } catch (_: Exception) {
                        emit(
                            ChatCompletionChunk(
                                error = ErrorDetail(
                                    message = "Provider returned an unsupported response format.",
                                    type = "parse_error"
                                )
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            val errorMessage = when (e) {
                is java.net.UnknownHostException -> "Network error: Unable to resolve host."
                is java.nio.channels.UnresolvedAddressException -> "Network error: Unable to resolve address. Check your internet connection."
                is java.net.ConnectException -> "Network error: Connection refused. Check the API URL."
                is HttpRequestTimeoutException -> "Request timed out."
                is java.net.SocketTimeoutException -> "Response timed out while waiting for the next chunk."
                is javax.net.ssl.SSLException -> "Network error: SSL/TLS connection failed."
                else -> e.message ?: "Unknown network error"
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

    override fun streamResponses(request: ResponsesRequest, timeoutSeconds: Int): Flow<ResponsesStreamEvent> = flow {
        try {
            val endpoint = if (apiUrl.endsWith("/")) "${apiUrl}v1/responses" else "$apiUrl/v1/responses"

            networkClient().preparePost(endpoint) {
                applyPlatformStreamingTimeout(timeoutSeconds)
                contentType(ContentType.Application.Json)
                setBody(NetworkClient.openAIJson.encodeToString(request))
                accept(ContentType.Text.EventStream)
                token?.let { bearerAuth(it) }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val errorBody = response.body<String>()
                    val errorMessage = parseOpenAIHttpError(response.status.value, errorBody)

                    emit(ResponseErrorEvent(message = errorMessage, code = response.status.value.toString()))
                    return@execute
                }

                // Success - read SSE stream
                val channel = response.bodyAsChannel()
                val rawResponseBuffer = StringBuilder()
                var sawStreamPayload = false
                while (!channel.isClosedForRead) {
                    val line = channel.readLine() ?: break

                    if (line.startsWith("data:")) {
                        sawStreamPayload = true
                        val data = line.removePrefix("data:").trimStart().trim()
                        if (data == "[DONE]") break

                        try {
                            val streamEvent = NetworkClient.openAIJson.decodeFromString<ResponsesStreamEvent>(data)
                            emit(streamEvent)
                        } catch (_: Exception) {
                            emit(UnknownEvent)
                        }
                    } else if (
                        line.startsWith("event:") ||
                        line.startsWith("id:") ||
                        line.startsWith("retry:") ||
                        line.startsWith(":")
                    ) {
                        continue
                    } else if (line.isNotBlank()) {
                        rawResponseBuffer.append(line.trim())
                    }
                }

                if (!sawStreamPayload && rawResponseBuffer.isNotBlank()) {
                    try {
                        val streamEvent = NetworkClient.openAIJson.decodeFromString<ResponsesStreamEvent>(rawResponseBuffer.toString())
                        emit(streamEvent)
                    } catch (_: Exception) {
                        emit(
                            ResponseErrorEvent(
                                message = "Provider returned an unsupported response format.",
                                code = "parse_error"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            val errorMessage = when (e) {
                is java.net.UnknownHostException -> "Network error: Unable to resolve host."
                is java.nio.channels.UnresolvedAddressException -> "Network error: Unable to resolve address. Check your internet connection."
                is java.net.ConnectException -> "Network error: Connection refused. Check the API URL."
                is HttpRequestTimeoutException -> "Request timed out."
                is java.net.SocketTimeoutException -> "Response timed out while waiting for the next chunk."
                is javax.net.ssl.SSLException -> "Network error: SSL/TLS connection failed."
                else -> e.message ?: "Unknown network error"
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

@Serializable
private data class OpenAIConversationResponse(
    val id: String
)

private fun parseOpenAIHttpError(statusCode: Int, responseBody: String): String {
    val openAIErrorMessage = runCatching {
        NetworkClient.openAIJson.decodeFromString<OpenAIErrorResponse>(responseBody).error.message
    }.getOrNull()
    if (!openAIErrorMessage.isNullOrBlank()) {
        return openAIErrorMessage
    }

    val trimmed = responseBody.trim()
    if (trimmed.startsWith("<!DOCTYPE", ignoreCase = true) || trimmed.startsWith("<html", ignoreCase = true)) {
        val document = Jsoup.parse(trimmed)
        val title = document.title().trim()
        val bodyText = document.body()?.text().orEmpty().replace(Regex("\\s+"), " ").trim()
        val summary = when {
            bodyText.isNotBlank() -> bodyText.take(240)
            title.isNotBlank() -> title
            else -> "Upstream gateway error."
        }
        return "HTTP $statusCode: $summary"
    }

    return "HTTP $statusCode: ${trimmed.take(240)}"
}
