package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.dto.google.request.GenerateContentRequest
import dev.chungjungsoo.gptmobile.data.dto.google.response.ErrorDetail
import dev.chungjungsoo.gptmobile.data.dto.google.response.GenerateContentResponse
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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

            networkClient()
                .sse(
                    urlString = endpoint,
                    request = {
                        method = HttpMethod.Post
                        parameter("key", token ?: "")
                        parameter("alt", "sse")
                        contentType(ContentType.Application.Json)
                        setBody(NetworkClient.json.encodeToJsonElement(request))
                    }
                ) {
                    incoming.collect { event ->
                        event.data?.let { data ->
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
