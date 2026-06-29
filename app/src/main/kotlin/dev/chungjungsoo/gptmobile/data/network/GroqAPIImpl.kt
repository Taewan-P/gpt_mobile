package dev.chungjungsoo.gptmobile.data.network

import android.util.Log
import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.dto.groq.request.GroqChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.groq.response.GroqChatCompletionChunk
import dev.chungjungsoo.gptmobile.data.dto.groq.response.GroqErrorDetail
import dev.chungjungsoo.gptmobile.util.applyPlatformStreamingTimeout
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readLine
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable

class GroqAPIImpl @Inject constructor(
    private val networkClient: NetworkClient
) : GroqAPI {

    override fun streamChatCompletion(
        request: GroqChatCompletionRequest,
        timeoutSeconds: Int,
        token: String?,
        apiUrl: String
    ): Flow<GroqChatCompletionChunk> = flow {
        try {
            val resolvedApiUrl = apiUrl.ifBlank { ModelConstants.GROQ_API_URL }
            val endpoint = if (resolvedApiUrl.endsWith("/")) "${resolvedApiUrl}chat/completions" else "$resolvedApiUrl/chat/completions"

            networkClient().preparePost(endpoint) {
                applyPlatformStreamingTimeout(timeoutSeconds)
                contentType(ContentType.Application.Json)
                setBody(NetworkClient.openAIJson.encodeToString(request))
                accept(if (request.stream) ContentType.Text.EventStream else ContentType.Application.Json)
                token?.let { bearerAuth(it) }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val errorBody = response.body<String>()
                    val errorMessage = try {
                        val errorResponse = NetworkClient.openAIJson.decodeFromString<GroqErrorResponse>(errorBody)
                        errorResponse.error.message
                    } catch (_: Exception) {
                        "HTTP ${response.status.value}: $errorBody"
                    }

                    emit(
                        GroqChatCompletionChunk(
                            error = GroqErrorDetail(
                                message = errorMessage,
                                type = "http_error",
                                code = response.status.value.toString()
                            )
                        )
                    )
                    return@execute
                }

                if (!request.stream) {
                    emit(NetworkClient.openAIJson.decodeFromString<GroqChatCompletionChunk>(response.body()))
                    return@execute
                }

                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readLine() ?: break
                    if (!line.startsWith("data:")) continue

                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break

                    try {
                        emit(NetworkClient.openAIJson.decodeFromString<GroqChatCompletionChunk>(data))
                    } catch (e: Exception) {
                        Log.w("GroqAPI", "Skipping malformed Groq chunk: $data", e)
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
                GroqChatCompletionChunk(
                    error = GroqErrorDetail(
                        message = errorMessage,
                        type = "network_error"
                    )
                )
            )
        }
    }.flowOn(Dispatchers.IO)
}

@Serializable
private data class GroqErrorResponse(
    val error: GroqErrorPayload
)

@Serializable
private data class GroqErrorPayload(
    val message: String,
    val type: String? = null,
    val code: String? = null
)
