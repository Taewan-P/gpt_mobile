package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.agent.ToolDefinitionsRejectedException
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponsesRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseErrorEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponsesStreamEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.UnknownEvent
import dev.chungjungsoo.gptmobile.util.applyPlatformStreamingTimeout
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readLine
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val SAFE_RESPONSE_ID = Regex("^[A-Za-z0-9_-]+$")
private val TERMINAL_TYPES = setOf(
    "response.completed",
    "response.failed",
    "response.incomplete",
    "response.cancelled",
    "response.canceled"
)
private val NON_OUTPUT_TYPES = setOf("response.created", "response.in_progress", "response.queued")
private const val REMOTE_CANCEL_TIMEOUT_MS = 5_000L
private const val MAX_RESUME_ATTEMPTS = 2
private val CONFIRMED_CANCEL_STATUSES = setOf(
    "cancelled",
    "canceled",
    "completed",
    "failed",
    "incomplete"
)

internal fun NetworkClient.streamResumableResponses(
    request: ResponsesRequest,
    timeoutSeconds: Int,
    config: ProviderRequestConfig,
    onUnconfirmedRemoteCancellation: (suspend (responseId: String) -> Unit)? = null
): Flow<ResponsesStreamEvent> = flow {
    val client = this@streamResumableResponses()
    val responsesUrl = responsesUrl(config.apiUrl)
    val body = backgroundRequestBody(request)
    var responseId: String? = null
    var lastSequence: Int? = null
    var sawOutput = false
    var outputWithoutCursor = false
    var remoteFinished = false
    var resumeAttempts = 0

    try {
        while (currentCoroutineContext().isActive) {
            val id = responseId
            if (id != null) {
                if (!canResume(id, sawOutput, outputWithoutCursor, lastSequence) || resumeAttempts >= MAX_RESUME_ATTEMPTS) {
                    emit(pauseEvent())
                    break
                }
                if (resumeAttempts > 0) {
                    delay((250L shl (resumeAttempts - 1)).coerceAtMost(2_000L))
                }
                resumeAttempts++
            }

            try {
                val call = if (id == null) {
                    client.preparePost(responsesUrl) {
                        // A failed background POST may still have created remote work. Without a
                        // response id it cannot be recovered or cancelled, so only connection
                        // failures that happen before a response are retried here.
                        retryGenerationRequest(retryHttpResponses = false)
                        applyPlatformStreamingTimeout(timeoutSeconds)
                        contentType(ContentType.Application.Json)
                        setBody(body)
                        accept(ContentType.Text.EventStream)
                        config.token?.let { bearerAuth(it) }
                    }
                } else {
                    client.prepareGet(resumeUrl(responsesUrl, id, lastSequence)) {
                        retryGenerationRequest()
                        applyPlatformStreamingTimeout(timeoutSeconds)
                        accept(ContentType.Text.EventStream)
                        config.token?.let { bearerAuth(it) }
                    }
                }

                var halt = false
                call.execute { response ->
                    if (!response.status.isSuccess()) {
                        val errorBody = response.body<String>()
                        throwIfToolDefinitionsRejected(response.status.value, !request.tools.isNullOrEmpty(), errorBody)
                        emit(httpErrorEvent(response.status.value, errorBody))
                        if (id == null) remoteFinished = true
                        halt = true
                        return@execute
                    }
                    consumeSse(response.bodyAsChannel()) { data ->
                        val parsed = parseEvent(data) ?: return@consumeSse false
                        parsed.responseId?.let { createdId ->
                            val current = responseId
                            when {
                                current == null -> responseId = createdId

                                createdId != current -> {
                                    emit(
                                        ResponseErrorEvent(
                                            code = "response_identity",
                                            message = "Unexpected response id change."
                                        )
                                    )
                                    halt = true
                                    return@consumeSse true
                                }
                            }
                        }
                        if (parsed.type != null && parsed.type !in NON_OUTPUT_TYPES) {
                            sawOutput = true
                            if (parsed.sequence == null) outputWithoutCursor = true
                        }
                        val sequence = parsed.sequence
                        if (sequence != null) {
                            val previous = lastSequence
                            if (previous != null && sequence <= previous) return@consumeSse false
                            lastSequence = sequence
                        }
                        emit(parsed.event)
                        if (parsed.type in TERMINAL_TYPES) {
                            remoteFinished = true
                            halt = true
                            return@consumeSse true
                        }
                        false
                    }
                }
                currentCoroutineContext().ensureActive()
                if (halt || remoteFinished) break
                if (responseId == null) {
                    emit(ResponseErrorEvent(code = "network_error", message = "Stream ended before a response id was established."))
                    remoteFinished = true
                    break
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ToolDefinitionsRejectedException) {
                throw e
            } catch (e: Exception) {
                if (responseId != null && canResume(responseId, sawOutput, outputWithoutCursor, lastSequence)) {
                    continue
                }
                if (responseId != null && sawOutput) {
                    emit(pauseEvent())
                    break
                }
                emit(networkErrorEvent(e))
                break
            }
        }
    } finally {
        val id = responseId
        if (!remoteFinished && id != null && pathSegment(id) != null) {
            withContext(NonCancellable) {
                val confirmed = withTimeoutOrNull(REMOTE_CANCEL_TIMEOUT_MS) {
                    cancelRemoteResponse(client, config, responsesUrl, id)
                } ?: false
                if (!confirmed) onUnconfirmedRemoteCancellation?.invoke(id)
            }
        }
    }
}.flowOn(Dispatchers.IO)

private fun canResume(
    responseId: String,
    sawOutput: Boolean,
    outputWithoutCursor: Boolean,
    lastSequence: Int?
): Boolean {
    if (pathSegment(responseId) == null) return false
    if (outputWithoutCursor) return false
    return lastSequence != null || !sawOutput
}

private fun pauseEvent() = ResponseErrorEvent(
    code = "resume_unavailable",
    message = "Cannot resume the response; preserving received output."
)

private fun backgroundRequestBody(request: ResponsesRequest): String {
    val encoded = NetworkClient.openAIJson.parseToJsonElement(
        NetworkClient.openAIJson.encodeToString(request)
    ).jsonObject
    return JsonObject(
        encoded + ("background" to JsonPrimitive(true)) + ("stream" to JsonPrimitive(true))
    ).toString()
}

private fun responsesUrl(apiUrl: String) = if (apiUrl.endsWith("/")) "${apiUrl}responses" else "$apiUrl/responses"

private fun resumeUrl(responsesUrl: String, responseId: String, lastSequence: Int?): String = buildString {
    append(responsesUrl)
    append('/')
    append(pathSegment(responseId))
    append("?stream=true")
    lastSequence?.let { append("&starting_after=").append(it) }
}

// ponytail: whitelist OpenAI response ids; encode-all-chars if ids gain punctuation
private fun pathSegment(id: String): String? = id.takeIf { SAFE_RESPONSE_ID.matches(it) }

private suspend fun consumeSse(channel: ByteReadChannel, onData: suspend (String) -> Boolean) {
    val dataLines = mutableListOf<String>()
    suspend fun flush(): Boolean {
        if (dataLines.isEmpty()) return false
        val data = dataLines.joinToString("\n")
        dataLines.clear()
        if (data == "[DONE]") return true
        return onData(data)
    }
    while (!channel.isClosedForRead) {
        val line = channel.readLine() ?: break
        when {
            line.isEmpty() -> if (flush()) return

            line.startsWith("data:") -> {
                val value = line.removePrefix("data:")
                dataLines += if (value.startsWith(" ")) value.drop(1) else value
            }
        }
    }
    flush()
}

private class ParsedEvent(
    val type: String?,
    val sequence: Int?,
    val responseId: String?,
    val event: ResponsesStreamEvent
)

private fun parseEvent(data: String): ParsedEvent? {
    val obj = runCatching { NetworkClient.openAIJson.parseToJsonElement(data).jsonObject }.getOrNull()
        ?: return ParsedEvent(null, null, null, UnknownEvent)
    val type = obj["type"]?.jsonPrimitive?.contentOrNull
    val sequence = obj["sequence_number"]?.jsonPrimitive?.intOrNull
    val responseId = obj["response"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
    val event = runCatching {
        NetworkClient.openAIJson.decodeFromJsonElement(ResponsesStreamEvent.serializer(), obj)
    }.getOrElse {
        if (type in TERMINAL_TYPES) {
            val status = obj["response"]?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull
            val message = obj["response"]?.jsonObject?.get("error")?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
            ResponseErrorEvent(code = type, message = message ?: status ?: type ?: "terminal")
        } else {
            UnknownEvent
        }
    }
    return ParsedEvent(type, sequence, responseId, event)
}

private fun httpErrorEvent(status: Int, errorBody: String): ResponseErrorEvent {
    val message = runCatching {
        NetworkClient.openAIJson.parseToJsonElement(errorBody).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
    }.getOrNull() ?: "HTTP $status: $errorBody"
    return ResponseErrorEvent(code = status.toString(), message = message)
}

private fun networkErrorEvent(error: Exception): ResponseErrorEvent {
    val message = when (error) {
        is UnknownHostException -> "Network error: Unable to resolve host."
        is UnresolvedAddressException -> "Network error: Unable to resolve address. Check your internet connection."
        is ConnectException -> "Network error: Connection refused. Check the API URL."
        is HttpRequestTimeoutException -> "Request timed out."
        is SocketTimeoutException -> "Response timed out while waiting for the next chunk."
        is SSLException -> "Network error: SSL/TLS connection failed."
        else -> error.message ?: "Unknown network error"
    }
    return ResponseErrorEvent(code = "network_error", message = message)
}

private suspend fun cancelRemoteResponse(
    client: HttpClient,
    config: ProviderRequestConfig,
    responsesUrl: String,
    responseId: String
): Boolean {
    val segment = pathSegment(responseId) ?: return false
    return try {
        client.preparePost("$responsesUrl/$segment/cancel") {
            timeout {
                requestTimeoutMillis = REMOTE_CANCEL_TIMEOUT_MS
                connectTimeoutMillis = REMOTE_CANCEL_TIMEOUT_MS
                socketTimeoutMillis = REMOTE_CANCEL_TIMEOUT_MS
            }
            contentType(ContentType.Application.Json)
            config.token?.let { bearerAuth(it) }
            setBody("{}")
        }.execute { response ->
            val body = response.body<String>()
            response.status.isSuccess() && cancelStatusAcknowledged(body)
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        false
    }
}

private fun cancelStatusAcknowledged(body: String): Boolean {
    val obj = runCatching { NetworkClient.openAIJson.parseToJsonElement(body).jsonObject }.getOrNull() ?: return false
    val status = obj["status"]?.jsonPrimitive?.contentOrNull
        ?: obj["response"]?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull
        ?: return false
    return status in CONFIRMED_CANCEL_STATUSES
}
