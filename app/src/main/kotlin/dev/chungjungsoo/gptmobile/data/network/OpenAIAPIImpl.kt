package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponsesRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ChatCompletionChunk
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ErrorDetail
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseErrorEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponsesStreamEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.UnknownEvent
import io.ktor.client.call.body
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.encodeToJsonElement

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

    override fun streamChatCompletion(request: ChatCompletionRequest): Flow<ChatCompletionChunk> = flow {
        try {
            val endpoint = if (apiUrl.endsWith("/")) "${apiUrl}chat/completions" else "$apiUrl/chat/completions"

            networkClient()
                .sse(
                    urlString = endpoint,
                    request = {
                        method = HttpMethod.Post
                        setBody(NetworkClient.openAIJson.encodeToJsonElement(request))
                        accept(ContentType.Text.EventStream)
                        token?.let { bearerAuth(it) }
                    }
                ) {
                    incoming.collect { event ->
                        event.data?.let { data ->
                            // OpenAI sends "[DONE]" as final message
                            if (data.trim() != "[DONE]") {
                                try {
                                    val chunk = NetworkClient.openAIJson.decodeFromString<ChatCompletionChunk>(data)
                                    emit(chunk)
                                } catch (_: Exception) {
                                    // Skip malformed chunks
                                }
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            emit(
                ChatCompletionChunk(
                    error = ErrorDetail(
                        message = e.message ?: "Unknown error",
                        type = "network_error"
                    )
                )
            )
        }
    }

    override fun streamResponses(request: ResponsesRequest): Flow<ResponsesStreamEvent> = flow {
        try {
            val endpoint = if (apiUrl.endsWith("/")) "${apiUrl}responses" else "$apiUrl/responses"

            networkClient().preparePost(endpoint) {
                contentType(ContentType.Application.Json)
                setBody(NetworkClient.openAIJson.encodeToJsonElement(request))
                accept(ContentType.Text.EventStream)
                token?.let { bearerAuth(it) }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val errorBody = response.body<String>()

                    val errorMessage = try {
                        val errorResponse = NetworkClient.openAIJson.decodeFromString<OpenAIErrorResponse>(errorBody)
                        errorResponse.error.message
                    } catch (_: Exception) {
                        "HTTP ${response.status.value}: $errorBody"
                    }

                    emit(ResponseErrorEvent(message = errorMessage, code = response.status.value.toString()))
                    return@execute
                }

                // Success - read SSE stream
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break

                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ").trim()
                        if (data == "[DONE]") break

                        try {
                            val streamEvent = NetworkClient.openAIJson.decodeFromString<ResponsesStreamEvent>(data)
                            emit(streamEvent)
                        } catch (_: Exception) {
                            emit(UnknownEvent)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emit(
                ResponseErrorEvent(
                    message = e.message ?: "Unknown error",
                    code = "network_error"
                )
            )
        }
    }
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
