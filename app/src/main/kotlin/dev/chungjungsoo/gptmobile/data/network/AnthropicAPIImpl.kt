package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.MessageRequest
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ErrorDetail
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ErrorResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.MessageResponseChunk
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.headers
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readUTF8Line
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

class AnthropicAPIImpl @Inject constructor(
    private val networkClient: NetworkClient
) : AnthropicAPI {

    private var token: String? = null
    private var apiUrl: String = ModelConstants.ANTHROPIC_API_URL

    override fun setToken(token: String?) {
        this.token = token
    }

    override fun setAPIUrl(url: String) {
        this.apiUrl = url
    }

    override fun streamChatMessage(messageRequest: MessageRequest): Flow<MessageResponseChunk> {
        val body = Json.encodeToJsonElement(messageRequest)

        val builder = HttpRequestBuilder().apply {
            method = HttpMethod.Post
            if (apiUrl.endsWith("/")) url("${apiUrl}v1/messages") else url("$apiUrl/v1/messages")
            contentType(ContentType.Application.Json)
            setBody(body)
            accept(ContentType.Text.EventStream)
            headers {
                append(API_KEY_HEADER, token ?: "")
                append(VERSION_HEADER, ANTHROPIC_VERSION)
            }
        }

        return flow {
            try {
                HttpStatement(builder = builder, client = networkClient()).execute {
                    streamEventsFrom(it)
                }
            } catch (e: Exception) {
                emit(ErrorResponseChunk(error = ErrorDetail(type = "network_error", message = e.message ?: "")))
            }
        }
    }

    private suspend inline fun <reified T> FlowCollector<T>.streamEventsFrom(response: HttpResponse) {
        val channel: ByteReadChannel = response.body()
        val jsonInstance = Json { ignoreUnknownKeys = true }

        try {
            while (currentCoroutineContext().isActive && !channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: continue
                val value: T = when {
                    line.startsWith(STREAM_END_TOKEN) -> break
                    line.startsWith(STREAM_PREFIX) -> jsonInstance.decodeFromString(line.removePrefix(STREAM_PREFIX))
                    else -> continue
                }
                emit(value)
            }
        } finally {
            channel.cancel()
        }
    }

    companion object {
        private const val STREAM_PREFIX = "data:"
        private const val STREAM_END_TOKEN = "event: message_stop"
        private const val API_KEY_HEADER = "x-api-key"
        private const val VERSION_HEADER = "anthropic-version"
        private const val ANTHROPIC_VERSION = "2023-06-01"
    }
}
