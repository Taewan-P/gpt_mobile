package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.dto.google.request.GenerateContentRequest
import dev.chungjungsoo.gptmobile.data.dto.google.response.ErrorDetail
import dev.chungjungsoo.gptmobile.data.dto.google.response.GenerateContentResponse
import io.ktor.client.call.body
import io.ktor.client.request.parameter
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
import kotlinx.serialization.json.encodeToJsonElement

class GoogleAPIImpl @Inject constructor(
    private val networkClient: NetworkClient
) : GoogleAPI {

    private var token: String? = null
    private var apiUrl: String = "https://generativelanguage.googleapis.com"

    override fun setToken(token: String?) {
        this.token = token
    }

    override fun setAPIUrl(url: String) {
        this.apiUrl = url
    }

    override fun streamGenerateContent(request: GenerateContentRequest, model: String): Flow<GenerateContentResponse> = flow {
        try {
            val endpoint = if (apiUrl.endsWith("/")) {
                "${apiUrl}v1beta/models/$model:streamGenerateContent"
            } else {
                "$apiUrl/v1beta/models/$model:streamGenerateContent"
            }

            networkClient().preparePost(endpoint) {
                parameter("key", token ?: "")
                parameter("alt", "sse")
                contentType(ContentType.Application.Json)
                setBody(NetworkClient.json.encodeToJsonElement(request))
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val errorBody = response.body<String>()

                    // Parse error - Google returns array format: [{"error": {...}}]
                    val errorMessage = try {
                        val errorList = NetworkClient.json.decodeFromString<List<GoogleErrorResponse>>(errorBody)
                        errorList.firstOrNull()?.error?.message ?: "Unknown error"
                    } catch (_: Exception) {
                        // Try single object format as fallback
                        try {
                            val errorResponse = NetworkClient.json.decodeFromString<GoogleErrorResponse>(errorBody)
                            errorResponse.error.message
                        } catch (_: Exception) {
                            "HTTP ${response.status.value}: $errorBody"
                        }
                    }

                    emit(
                        GenerateContentResponse(
                            error = ErrorDetail(
                                message = errorMessage,
                                code = response.status.value,
                                status = "ERROR"
                            )
                        )
                    )
                    return@execute
                }

                // Success - read SSE stream
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break

                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ").trim()
                        try {
                            val chunk = NetworkClient.json.decodeFromString<GenerateContentResponse>(data)
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
                is java.net.SocketTimeoutException -> "Network error: Connection timed out."
                is javax.net.ssl.SSLException -> "Network error: SSL/TLS connection failed."
                else -> e.message ?: "Unknown network error"
            }
            emit(
                GenerateContentResponse(
                    error = ErrorDetail(
                        message = errorMessage,
                        code = -1,
                        status = "NETWORK_ERROR"
                    )
                )
            )
        }
    }
}

@Serializable
private data class GoogleErrorResponse(
    val error: GoogleError
)

@Serializable
private data class GoogleError(
    val code: Int? = null,
    val message: String,
    val status: String? = null
)
