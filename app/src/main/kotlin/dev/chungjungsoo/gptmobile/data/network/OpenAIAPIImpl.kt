package dev.chungjungsoo.gptmobile.data.network

import android.util.Log
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ChatCompletionChunk
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ErrorDetail
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
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
            val requestBody = NetworkClient.openAIJson.encodeToString(ChatCompletionRequest.serializer(), request)

            networkClient().preparePost(endpoint) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
                token?.let { bearerAuth(it) }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val errorBody = response.body<String>()
                    Log.e("OpenAIAPI", "API Error: $errorBody")
                    emit(
                        ChatCompletionChunk(
                            error = ErrorDetail(
                                message = "API Error (${response.status.value}): $errorBody",
                                type = "api_error"
                            )
                        )
                    )
                    return@execute
                }

                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break

                    // SSE format: "data: {...}"
                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ").trim()
                        if (data == "[DONE]") {
                            break
                        }

                        try {
                            val chunk = NetworkClient.openAIJson.decodeFromString<ChatCompletionChunk>(data)
                            emit(chunk)
                        } catch (e: Exception) {
                            Log.e("OpenAIAPI", "Failed to parse chunk: $data", e)
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
