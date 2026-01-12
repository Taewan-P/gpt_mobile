package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.MessageRequest
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ErrorDetail
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ErrorResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.MessageResponseChunk
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.headers
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

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

    override fun streamChatMessage(messageRequest: MessageRequest): Flow<MessageResponseChunk> = flow {
        try {
            val endpoint = if (apiUrl.endsWith("/")) "${apiUrl}v1/messages" else "$apiUrl/v1/messages"

            networkClient().preparePost(endpoint) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToJsonElement(messageRequest))
                accept(ContentType.Text.EventStream)
                headers {
                    append(API_KEY_HEADER, token ?: "")
                    append(VERSION_HEADER, ANTHROPIC_VERSION)
                }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val errorBody = response.body<String>()

                    // Parse error - Anthropic format: {"type": "error", "error": {"type": "...", "message": "..."}}
                    val errorMessage = try {
                        val errorResponse = json.decodeFromString<AnthropicErrorResponse>(errorBody)
                        errorResponse.error.message
                    } catch (_: Exception) {
                        "HTTP ${response.status.value}: $errorBody"
                    }

                    emit(ErrorResponseChunk(error = ErrorDetail(type = "api_error", message = errorMessage)))
                    return@execute
                }

                // Success - read SSE stream
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break

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
            emit(ErrorResponseChunk(error = ErrorDetail(type = "network_error", message = e.message ?: "Unknown error")))
        }
    }

    companion object {
        private const val API_KEY_HEADER = "x-api-key"
        private const val VERSION_HEADER = "anthropic-version"
        private const val ANTHROPIC_VERSION = "2023-06-01"
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
