package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponsesRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseCompletedEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.mockk.every
import io.mockk.mockk
import java.net.UnknownHostException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIRecoveryTransportTest {
    private val config = ProviderRequestConfig("https://fixture.invalid/v1/", null)
    private val request = ChatCompletionRequest(model = "fixture", messages = emptyList())

    @Test
    fun `transient DNS failure gets bounded retries before primary handoff fails`() = runTest {
        var attempts = 0
        val engine = MockEngine {
            if (++attempts < 3) throw UnknownHostException("Unable to resolve host openrouter.ai")
            respond("""{"choices":[{"message":{"content":"recovered"}}]}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        withApi(engine) { api ->
            val chunks = api.streamChatCompletion(request, 5, config).toList()
            assertEquals(3, attempts)
            assertEquals("recovered", chunks.single().choices!!.single().effectiveDelta.content)
        }
    }

    @Test
    fun `permanent DNS outage stops after three attempts`() = runTest {
        var attempts = 0
        withApi(
            MockEngine {
                attempts++
                throw UnknownHostException("Unable to resolve host openrouter.ai")
            }
        ) { api ->
            assertTrue(api.streamChatCompletion(request, 5, config).toList().single().error != null)
            assertEquals(3, attempts)
        }
    }

    @Test
    fun `partial stream ends as transport error and is never replayed`() = runTest {
        var attempts = 0
        withApi(
            MockEngine {
                attempts++
                respond("data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n", headers = headersOf(HttpHeaders.ContentType, "text/event-stream"))
            }
        ) { api ->
            val chunks = api.streamChatCompletion(request, 5, config).toList()
            assertEquals(1, attempts)
            assertTrue(chunks.last().error?.message.orEmpty().contains("STREAM_INTERRUPTED"))
            assertFalse(chunks.any { it.streamFinished })
        }
    }

    @Test
    fun `responses capability rejection removes only unsupported sampling and retries`() = runTest {
        val bodies = mutableListOf<String>()
        withApi(
            MockEngine { incoming ->
                bodies += (incoming.body as TextContent).text
                if (bodies.size == 1) {
                    respond("""{"error":{"message":"Unsupported parameter: 'temperature' is not supported with this model."}}""", HttpStatusCode.BadRequest)
                } else {
                    respond("data: {\"type\":\"response.completed\",\"response\":{\"id\":\"r\",\"status\":\"completed\"}}\n\n", headers = headersOf(HttpHeaders.ContentType, "text/event-stream"))
                }
            }
        ) { api ->
            val events = api.streamResponses(ResponsesRequest(model = "fixture", input = emptyList(), temperature = .4f, maxOutputTokens = 128), 5, config).toList()
            assertTrue(events.single() is ResponseCompletedEvent)
            assertEquals(2, bodies.size)
            val second = NetworkClient.openAIJson.parseToJsonElement(bodies.last()).jsonObject
            assertFalse(second.containsKey("temperature"))
            assertTrue(second.containsKey("max_output_tokens"))
        }
    }

    @Test
    fun `gateway transient error retries before output but never after gateway tool execution`() = runTest {
        for (toolStarted in listOf(false, true)) {
            var attempts = 0
            withApi(
                MockEngine {
                    attempts++
                    val body = if (attempts == 1) {
                        val progress = if (toolStarted) "data: {\"gateway_progress\":{\"event\":\"tool_started\"}}\n\n" else ""
                        progress + "data: {\"error\":{\"message\":\"Software caused connection abort\"}}\n\ndata: [DONE]\n\n"
                    } else {
                        "data: {\"choices\":[{\"delta\":{\"content\":\"recovered\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
                    }
                    respond(body, headers = headersOf(HttpHeaders.ContentType, "text/event-stream"))
                }
            ) { api ->
                val chunks = api.streamChatCompletion(request, 5, config).toList()
                assertEquals(if (toolStarted) 1 else 2, attempts)
                assertEquals(toolStarted, chunks.any { it.error != null })
            }
        }
    }

    private suspend fun withApi(engine: MockEngine, block: suspend (OpenAIAPI) -> Unit) {
        val client = HttpClient(engine) { install(HttpTimeout) }
        val wrapper = mockk<NetworkClient>()
        every { wrapper() } returns client
        try {
            block(OpenAIAPIImpl(wrapper))
        } finally {
            client.close()
        }
    }
}
