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
                setBody(NetworkClient.json.encodeToString(GenerateContentRequest.serializer(), request))
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val errorBody = response.body<String>()
                    emit(
                        GenerateContentResponse(
                            error = ErrorDetail(
                                message = "API Error (${response.status.value}): $errorBody",
                                code = response.status.value,
                                status = "API_ERROR"
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
                        try {
                            val chunk = NetworkClient.json.decodeFromString<GenerateContentResponse>(data)
                            emit(chunk)
                        } catch (e: Exception) {
                            // Skip malformed chunks
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emit(
                GenerateContentResponse(
                    error = ErrorDetail(
                        message = e.message ?: "Unknown error",
                        code = -1,
                        status = "INTERNAL"
                    )
                )
            )
        }
    }
}
