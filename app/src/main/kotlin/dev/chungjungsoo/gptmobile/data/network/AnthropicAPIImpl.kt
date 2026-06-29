package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.MessageRequest
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ErrorDetail
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ErrorResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.MessageResponseChunk
import dev.chungjungsoo.gptmobile.util.applyPlatformStreamingTimeout
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.accept
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.headers
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
import kotlinx.serialization.json.Json

class AnthropicAPIImpl @Inject constructor(
    private val networkClient: NetworkClient
) : AnthropicAPI {

    private var token: String? = null
    private var apiUrl: String = ModelConstants.ANTHROPIC_API_URL

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        explicitNulls = false
    }

    override fun setToken(token: String?) {
        this.token = token
    }

    override fun setAPIUrl(url: String) {
        this.apiUrl = url
    }

    override suspend fun uploadFile(filePath: String, fileName: String, mimeType: String): UploadedProviderFile {
        val endpoint = if (apiUrl.endsWith("/")) "${apiUrl}files" else "$apiUrl/files"
        val responseBody = networkClient().preparePost(endpoint) {
            setBody(
                MultiPartFormDataContent(
                    formData {
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
            headers {
                append(API_KEY_HEADER, token ?: "")
                append(VERSION_HEADER, ANTHROPIC_VERSION)
                append(BETA_HEADER, ANTHROPIC_FILES_BETA)
            }
        }.body<String>()

        val uploadResponse = json.decodeFromString<AnthropicFileResponse>(responseBody)
        return UploadedProviderFile(
            id = uploadResponse.id,
            mimeType = uploadResponse.mimeType ?: mimeType,
            name = uploadResponse.filename
        )
    }

    override suspend fun isFileAvailable(fileId: String): Boolean {
        val endpoint = if (apiUrl.endsWith("/")) "${apiUrl}files/$fileId" else "$apiUrl/files/$fileId"
        return try {
            networkClient().prepareGet(endpoint) {
                headers {
                    append(API_KEY_HEADER, token ?: "")
                    append(VERSION_HEADER, ANTHROPIC_VERSION)
                    append(BETA_HEADER, ANTHROPIC_FILES_BETA)
                }
            }.execute { response ->
                response.status.isSuccess()
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun streamChatMessage(messageRequest: MessageRequest, timeoutSeconds: Int): Flow<MessageResponseChunk> = flow {
        try {
            val endpoint = if (apiUrl.endsWith("/")) "${apiUrl}messages" else "$apiUrl/messages"

            networkClient().preparePost(endpoint) {
                applyPlatformStreamingTimeout(timeoutSeconds)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(messageRequest))
                accept(ContentType.Text.EventStream)
                headers {
                    append(API_KEY_HEADER, token ?: "")
                    append(VERSION_HEADER, ANTHROPIC_VERSION)
                    append(BETA_HEADER, ANTHROPIC_FILES_BETA)
                }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val errorBody = response.body<String>()

                    val errorMessage = try {
                        val errorResponse = json.decodeFromString<AnthropicErrorResponse>(errorBody)
                        errorResponse.error.message
                    } catch (_: Exception) {
                        "HTTP ${response.status.value}: $errorBody"
                    }

                    emit(ErrorResponseChunk(error = ErrorDetail(type = "api_error", message = errorMessage)))
                    return@execute
                }

                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readLine() ?: break

                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ").trim()
                        try {
                            val chunk = json.decodeFromString<MessageResponseChunk>(data)
                            emit(chunk)
                        } catch (_: Exception) {
                            // Skip malformed chunks
                        }
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
            emit(ErrorResponseChunk(error = ErrorDetail(type = "network_error", message = errorMessage)))
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        private const val API_KEY_HEADER = "x-api-key"
        private const val VERSION_HEADER = "anthropic-version"
        private const val BETA_HEADER = "anthropic-beta"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val ANTHROPIC_FILES_BETA = "files-api-2025-04-14"
    }
}

@Serializable
private data class AnthropicErrorResponse(
    val type: String,
    val error: AnthropicError
)

@Serializable
private data class AnthropicError(
    val type: String,
    val message: String
)

@Serializable
private data class AnthropicFileResponse(
    val id: String,
    val filename: String? = null,
    @kotlinx.serialization.SerialName("mime_type")
    val mimeType: String? = null
)
