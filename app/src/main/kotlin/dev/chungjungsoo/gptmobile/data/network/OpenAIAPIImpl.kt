package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ChatCompletionChunk
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ErrorDetail
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.encodeToJsonElement

class OpenAIAPIImpl @Inject constructor(
    private val networkClient: NetworkClient
) : OpenAIAPI {

    private var token: String? = null
    private var apiUrl: String = "https://api.openai.com"

    override fun setToken(token: String?) {
        this.token = token
    }

    override fun setAPIUrl(url: String) {
        this.apiUrl = url
    }

    override fun streamChatCompletion(request: ChatCompletionRequest): Flow<ChatCompletionChunk> = flow {
        try {
            val endpoint = if (apiUrl.endsWith("/")) "${apiUrl}v1/chat/completions" else "$apiUrl/v1/chat/completions"

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
}
