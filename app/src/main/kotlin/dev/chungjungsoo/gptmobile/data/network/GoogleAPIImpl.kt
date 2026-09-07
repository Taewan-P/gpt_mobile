package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.dto.google.request.GenerateContentRequest
import dev.chungjungsoo.gptmobile.data.dto.google.response.ErrorDetail
import dev.chungjungsoo.gptmobile.data.dto.google.response.GenerateContentResponse
import dev.chungjungsoo.gptmobile.util.applyPlatformStreamingTimeout
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
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

class GoogleAPIImpl @Inject constructor(
    private val networkClient: NetworkClient
) : GoogleAPI {
    override suspend fun uploadFile(
        filePath: String,
        fileName: String,
        mimeType: String,
        config: ProviderRequestConfig
    ): UploadedProviderFile {
        val file = File(filePath)
        val apiUrl = config.apiUrl
        val startEndpoint = if (apiUrl.endsWith("/")) "${apiUrl}upload/v1beta/files" else "$apiUrl/upload/v1beta/files"
        val uploadUrl = networkClient().preparePost(startEndpoint) {
            header(GOOGLE_API_KEY_HEADER, config.token ?: "")
            contentType(ContentType.Application.Json)
            header("X-Goog-Upload-Protocol", "resumable")
            header("X-Goog-Upload-Command", "start")
            header("X-Goog-Upload-Header-Content-Length", file.length().toString())
            header("X-Goog-Upload-Header-Content-Type", mimeType)
            setBody("""{"file":{"display_name":"$fileName"}}""")
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throw IllegalStateException(response.body<String>())
            }
            response.headers["x-goog-upload-url"] ?: response.headers["X-Goog-Upload-URL"]
        } ?: throw IllegalStateException("Failed to obtain Google upload URL")

        val responseBody = networkClient().preparePost(uploadUrl) {
            header("X-Goog-Upload-Offset", "0")
            header("X-Goog-Upload-Command", "upload, finalize")
            header(HttpHeaders.ContentLength, file.length().toString())
            contentType(ContentType.parse(mimeType))
            setBody(file.readBytes())
        }.body<String>()

        val uploadResponse = NetworkClient.json.decodeFromString<GoogleFileUploadResponse>(responseBody)
        return UploadedProviderFile(
            id = uploadResponse.file.uri ?: uploadResponse.file.name ?: "",
            mimeType = uploadResponse.file.mimeType ?: mimeType,
            name = uploadResponse.file.name,
            uri = uploadResponse.file.uri
        )
    }

    override suspend fun isFileAvailable(fileName: String, config: ProviderRequestConfig): Boolean {
        val apiUrl = config.apiUrl
        val endpoint = if (apiUrl.endsWith("/")) "${apiUrl}v1beta/$fileName" else "$apiUrl/v1beta/$fileName"
        return try {
            networkClient().prepareGet(endpoint) {
                header(GOOGLE_API_KEY_HEADER, config.token ?: "")
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    false
                } else {
                    val metadata = NetworkClient.json.decodeFromString<GoogleFileMetadata>(response.body<String>())
                    metadata.state == null || metadata.state == "ACTIVE"
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun streamGenerateContent(
        request: GenerateContentRequest,
        model: String,
        timeoutSeconds: Int,
        config: ProviderRequestConfig
    ): Flow<GenerateContentResponse> = flow {
        try {
            val apiUrl = config.apiUrl
            val endpoint = if (apiUrl.endsWith("/")) {
                "${apiUrl}v1beta/models/$model:streamGenerateContent"
            } else {
                "$apiUrl/v1beta/models/$model:streamGenerateContent"
            }

            networkClient().preparePost(endpoint) {
                applyPlatformStreamingTimeout(timeoutSeconds)
                header(GOOGLE_API_KEY_HEADER, config.token ?: "")
                parameter("alt", "sse")
                contentType(ContentType.Application.Json)
                setBody(NetworkClient.json.encodeToString(request))
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val errorBody = response.body<String>()
                    throwIfToolDefinitionsRejected(
                        response.status.value,
                        !request.tools.isNullOrEmpty(),
                        errorBody
                    )

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
                    val line = channel.readLine() ?: break

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
            if (e is CancellationException || e is dev.chungjungsoo.gptmobile.data.agent.ToolDefinitionsRejectedException) throw e
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
                GenerateContentResponse(
                    error = ErrorDetail(
                        message = errorMessage,
                        code = -1,
                        status = "NETWORK_ERROR"
                    )
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        const val GOOGLE_API_KEY_HEADER = "x-goog-api-key"
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

@Serializable
private data class GoogleFileUploadResponse(
    val file: GoogleFileMetadata
)

@Serializable
private data class GoogleFileMetadata(
    val name: String? = null,
    val uri: String? = null,
    @kotlinx.serialization.SerialName("mime_type")
    val mimeType: String? = null,
    val state: String? = null
)
