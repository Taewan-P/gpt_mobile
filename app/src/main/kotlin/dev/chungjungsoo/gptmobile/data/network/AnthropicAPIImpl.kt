package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.MessageRequest
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ErrorDetail
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ErrorResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.MessageResponseChunk
import io.ktor.client.call.body
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.accept
import io.ktor.client.request.headers
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
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

        return flow<MessageResponseChunk> {
            try {
                networkClient()
                    .sse(
                        urlString = if (apiUrl.endsWith("/")) "${apiUrl}v1/messages" else "$apiUrl/v1/messages",
                        request = {
                            method = HttpMethod.Post
                            setBody(body)
                            accept(ContentType.Text.EventStream)
                            headers {
                                append(API_KEY_HEADER, token ?: "")
                                append(VERSION_HEADER, ANTHROPIC_VERSION)
                            }
                        }
                    ) {
                        incoming.collect { event -> event.data?.let { line -> emit(Json.decodeFromString(line)) } }
                    }
            } catch (e: Exception) {
                emit(ErrorResponseChunk(error = ErrorDetail(type = "network_error", message = e.message ?: "")))
            }
        }
    }

    private suspend inline fun <reified T> FlowCollector<T>.streamEventsFrom(response: HttpResponse) {
        val channel: ByteReadChannel = response.body()
        try {
            while (currentCoroutineContext().isActive && !channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: continue
                val value: T = when {
                    line.startsWith(STREAM_END_TOKEN) -> break
                    line.startsWith(STREAM_PREFIX) -> Json.decodeFromString(line.removePrefix(STREAM_PREFIX))
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
