package dev.chungjungsoo.gptmobile.data.network

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseFunctionTool
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseInputContent
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseInputMessage
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponsesRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.response.FunctionCallArgumentsDeltaEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.OutputTextDeltaEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseCompletedEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseErrorEvent
import io.ktor.client.engine.cio.CIO
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIResumableStreamTest {

    @Test
    fun `dropped stream resumes from GET without duplicate text or tool events`() = withServer { env ->
        env.server.createContext("/") { exchange ->
            val recorded = env.record(exchange)
            when {
                recorded.method == "POST" && recorded.path == "/responses" -> {
                    exchange.writeSse(
                        listOf(
                            "event: response.created\n",
                            "data: {\"type\":\"response.created\",\n",
                            "data: \"sequence_number\":0,\"response\":{\"id\":\"resp_1\",\"status\":\"in_progress\"}}\n\n",
                            "event: response.output_text.delta\ndata: {\"type\":\"response.output_text.delta\",\"sequence_number\":1,\"item_id\":\"msg_1\",\"output_index\":0,\"content_index\":0,\"delta\":\"Hel\"}\n\n"
                        ),
                        closeConnection = true
                    )
                }

                recorded.method == "GET" && recorded.path == "/responses/resp_1" -> {
                    exchange.writeSse(
                        listOf(
                            sse("response.output_text.delta", "{\"type\":\"response.output_text.delta\",\"sequence_number\":1,\"item_id\":\"msg_1\",\"output_index\":0,\"content_index\":0,\"delta\":\"Hel\"}"),
                            sse("response.output_text.delta", "{\"type\":\"response.output_text.delta\",\"sequence_number\":2,\"item_id\":\"msg_1\",\"output_index\":0,\"content_index\":0,\"delta\":\"lo\"}"),
                            sse("response.function_call_arguments.delta", "{\"type\":\"response.function_call_arguments.delta\",\"sequence_number\":3,\"item_id\":\"fc_1\",\"output_index\":1,\"delta\":\"{}\"}"),
                            sse("response.function_call_arguments.delta", "{\"type\":\"response.function_call_arguments.delta\",\"sequence_number\":3,\"item_id\":\"fc_1\",\"output_index\":1,\"delta\":\"{}\"}"),
                            sse("response.completed", "{\"type\":\"response.completed\",\"sequence_number\":4,\"response\":{\"id\":\"resp_1\",\"status\":\"completed\"}}")
                        )
                    )
                }

                else -> exchange.respond(404, "{\"error\":{\"message\":\"unexpected\"}}")
            }
        }

        val events = runBlocking {
            env.network.streamResumableResponses(sampleRequest(), 5, env.config).toList()
        }

        val creates = env.requests.filter { it.method == "POST" && it.path == "/responses" }
        val resumes = env.requests.filter { it.method == "GET" && it.path == "/responses/resp_1" }
        val body = NetworkClient.openAIJson.parseToJsonElement(creates.single().body) as JsonObject
        assertEquals(true, body["background"]?.jsonPrimitive?.boolean)
        assertEquals(true, body["stream"]?.jsonPrimitive?.boolean)
        assertEquals(1, creates.size)
        assertEquals(1, resumes.size)
        assertEquals("true", query(resumes.single())["stream"])
        assertEquals("1", query(resumes.single())["starting_after"])
        assertEquals("Hello", events.filterIsInstance<OutputTextDeltaEvent>().joinToString("") { it.delta })
        assertEquals(1, events.filterIsInstance<FunctionCallArgumentsDeltaEvent>().size)
        assertTrue(events.any { it is ResponseCompletedEvent })
    }

    @Test
    fun `replayed older sequences do not rewind cursor or re-emit events`() = withServer { env ->
        val resumes = AtomicInteger()
        env.server.createContext("/") { exchange ->
            val recorded = env.record(exchange)
            when {
                recorded.method == "POST" && recorded.path == "/responses" -> {
                    exchange.writeSse(
                        listOf(
                            sse("response.created", "{\"type\":\"response.created\",\"sequence_number\":0,\"response\":{\"id\":\"resp_1\",\"status\":\"in_progress\"}}"),
                            textDelta(1, "a"),
                            textDelta(2, "b"),
                            textDelta(3, "c"),
                            textDelta(4, "d"),
                            textDelta(5, "e")
                        )
                    )
                }

                recorded.method == "GET" && recorded.path == "/responses/resp_1" -> {
                    when (resumes.incrementAndGet()) {
                        1 -> {
                            exchange.writeSse(
                                listOf(
                                    textDelta(2, "b"),
                                    textDelta(4, "d"),
                                    textDelta(5, "e"),
                                    textDelta(6, "f")
                                )
                            )
                        }

                        else -> {
                            exchange.writeSse(
                                listOf(sse("response.completed", "{\"type\":\"response.completed\",\"sequence_number\":7,\"response\":{\"id\":\"resp_1\",\"status\":\"completed\"}}"))
                            )
                        }
                    }
                }

                else -> exchange.respond(404, "{\"error\":{\"message\":\"unexpected\"}}")
            }
        }

        val events = runBlocking {
            env.network.streamResumableResponses(sampleRequest(), 5, env.config).toList()
        }

        assertEquals(1, env.requests.count { it.method == "POST" && it.path == "/responses" })
        assertEquals(2, resumes.get())
        assertEquals(listOf("5", "6"), env.requests.filter { it.method == "GET" }.map { query(it)["starting_after"] })
        assertEquals("abcdef", events.filterIsInstance<OutputTextDeltaEvent>().joinToString("") { it.delta })
        assertTrue(events.any { it is ResponseCompletedEvent })
    }

    @Test
    fun `missing sequence cursor after output pauses without regenerating`() = withServer { env ->
        env.server.createContext("/") { exchange ->
            val recorded = env.record(exchange)
            if (recorded.method == "POST" && recorded.path == "/responses") {
                exchange.writeSse(
                    listOf(
                        sse("response.created", "{\"type\":\"response.created\",\"response\":{\"id\":\"resp_1\",\"status\":\"in_progress\"}}"),
                        sse("response.output_text.delta", "{\"type\":\"response.output_text.delta\",\"item_id\":\"msg_1\",\"output_index\":0,\"content_index\":0,\"delta\":\"partial\"}")
                    )
                )
            } else {
                exchange.respond(500, "{\"error\":{\"message\":\"should not resume or recreate\"}}")
            }
        }

        val events = runBlocking {
            env.network.streamResumableResponses(sampleRequest(), 5, env.config).toList()
        }

        assertEquals(1, env.requests.count { it.method == "POST" && it.path == "/responses" })
        assertFalse(env.requests.any { it.method == "GET" })
        assertEquals("partial", events.filterIsInstance<OutputTextDeltaEvent>().joinToString("") { it.delta })
        val pause = events.filterIsInstance<ResponseErrorEvent>().single { it.code == "resume_unavailable" }
        assertTrue(pause.message.contains("preserving", ignoreCase = true))
        assertEquals(1, env.requests.count { it.method == "POST" && it.path == "/responses/resp_1/cancel" })
    }

    @Test
    fun `permanent response rejection is not retried or resumed`() = withServer { env ->
        env.server.createContext("/") { exchange ->
            env.record(exchange)
            exchange.respond(401, "{\"error\":{\"message\":\"invalid api key\"}}")
        }

        val events = runBlocking {
            env.network.streamResumableResponses(sampleRequest(), 5, env.config).toList()
        }

        assertEquals(1, env.requests.size)
        assertEquals("POST", env.requests.single().method)
        assertEquals("/responses", env.requests.single().path)
        val error = events.filterIsInstance<ResponseErrorEvent>().single()
        assertEquals("401", error.code)
        assertTrue(error.message.contains("invalid api key"))
    }

    @Test
    fun `transient initial HTTP rejection does not create a second background job`() = withServer { env ->
        env.server.createContext("/") { exchange ->
            env.record(exchange)
            exchange.respond(502, "{\"error\":{\"message\":\"upstream failed after accepting work\"}}")
        }

        val events = runBlocking {
            env.network.streamResumableResponses(sampleRequest(), 5, env.config).toList()
        }

        assertEquals(1, env.requests.count { it.method == "POST" && it.path == "/responses" })
        assertFalse(env.requests.any { it.method == "GET" })
        val error = events.filterIsInstance<ResponseErrorEvent>().single()
        assertEquals("502", error.code)
    }

    @Test
    fun `caller cancellation posts cancel and does not reconnect`() = withServer { env ->
        val started = CountDownLatch(1)
        val hold = AtomicBoolean(true)
        env.server.createContext("/") { exchange ->
            val recorded = env.record(exchange)
            when {
                recorded.method == "POST" && recorded.path == "/responses" -> {
                    exchange.writeSse(
                        listOf(
                            sse("response.created", "{\"type\":\"response.created\",\"sequence_number\":0,\"response\":{\"id\":\"resp_1\",\"status\":\"in_progress\"}}"),
                            sse("response.output_text.delta", "{\"type\":\"response.output_text.delta\",\"sequence_number\":1,\"item_id\":\"msg_1\",\"output_index\":0,\"content_index\":0,\"delta\":\"Hi\"}")
                        )
                    )
                }

                recorded.method == "POST" && recorded.path == "/responses/resp_1/cancel" -> {
                    exchange.respond(200, "{\"id\":\"resp_1\",\"object\":\"response\",\"status\":\"cancelled\"}")
                }

                recorded.method == "GET" && recorded.path == "/responses/resp_1" -> {
                    started.countDown()
                    while (hold.get()) {
                        Thread.sleep(50)
                    }
                    runCatching { exchange.respond(200, "") }
                }

                else -> exchange.respond(500, "{\"error\":{\"message\":\"no extra reconnect after cancel\"}}")
            }
        }

        val received = CountDownLatch(1)
        runBlocking {
            val job = launch(Dispatchers.IO) {
                env.network.streamResumableResponses(sampleRequest(), 5, env.config).collect {
                    received.countDown()
                }
            }
            assertTrue(received.await(5, TimeUnit.SECONDS))
            assertTrue(started.await(5, TimeUnit.SECONDS))
            job.cancelAndJoin()
        }
        hold.set(false)

        assertEquals(1, env.requests.count { it.method == "POST" && it.path == "/responses" })
        assertEquals(1, env.requests.count { it.method == "POST" && it.path == "/responses/resp_1/cancel" })
        assertEquals(1, env.requests.count { it.method == "GET" })
    }

    @Test
    fun `unsuccessful remote cancel reports unconfirmed cancellation`() = withServer { env ->
        val started = CountDownLatch(1)
        val hold = AtomicBoolean(true)
        val unconfirmed = CopyOnWriteArrayList<String>()
        env.server.createContext("/") { exchange ->
            val recorded = env.record(exchange)
            when {
                recorded.method == "POST" && recorded.path == "/responses" -> {
                    exchange.writeSse(
                        listOf(sse("response.created", "{\"type\":\"response.created\",\"sequence_number\":0,\"response\":{\"id\":\"resp_1\",\"status\":\"in_progress\"}}"))
                    )
                }

                recorded.method == "POST" && recorded.path == "/responses/resp_1/cancel" -> {
                    exchange.respond(500, "{\"error\":{\"message\":\"offline\"}}")
                }

                recorded.method == "GET" && recorded.path == "/responses/resp_1" -> {
                    started.countDown()
                    while (hold.get()) {
                        Thread.sleep(50)
                    }
                    runCatching { exchange.respond(200, "") }
                }

                else -> exchange.respond(500, "{\"error\":{\"message\":\"unexpected\"}}")
            }
        }

        val received = CountDownLatch(1)
        runBlocking {
            val job = launch(Dispatchers.IO) {
                env.network.streamResumableResponses(
                    sampleRequest(),
                    5,
                    env.config
                ) { responseId ->
                    unconfirmed += responseId
                }.collect {
                    received.countDown()
                }
            }
            assertTrue(received.await(5, TimeUnit.SECONDS))
            assertTrue(started.await(5, TimeUnit.SECONDS))
            job.cancelAndJoin()
        }
        hold.set(false)

        assertEquals(listOf("resp_1"), unconfirmed.toList())
        assertEquals(1, env.requests.count { it.method == "GET" })
    }

    @Test
    fun `http 2xx in-progress cancel is unconfirmed`() = withServer { env ->
        val started = CountDownLatch(1)
        val hold = AtomicBoolean(true)
        val unconfirmed = CopyOnWriteArrayList<String>()
        env.server.createContext("/") { exchange ->
            val recorded = env.record(exchange)
            when {
                recorded.method == "POST" && recorded.path == "/responses" -> {
                    exchange.writeSse(
                        listOf(sse("response.created", "{\"type\":\"response.created\",\"sequence_number\":0,\"response\":{\"id\":\"resp_1\",\"status\":\"in_progress\"}}"))
                    )
                }

                recorded.method == "POST" && recorded.path == "/responses/resp_1/cancel" -> {
                    exchange.respond(200, "{\"id\":\"resp_1\",\"object\":\"response\",\"status\":\"in_progress\"}")
                }

                recorded.method == "GET" && recorded.path == "/responses/resp_1" -> {
                    started.countDown()
                    while (hold.get()) {
                        Thread.sleep(50)
                    }
                    runCatching { exchange.respond(200, "") }
                }

                else -> exchange.respond(500, "{\"error\":{\"message\":\"unexpected\"}}")
            }
        }

        val received = CountDownLatch(1)
        runBlocking {
            val job = launch(Dispatchers.IO) {
                env.network.streamResumableResponses(
                    sampleRequest(),
                    5,
                    env.config
                ) { responseId ->
                    unconfirmed += responseId
                }.collect {
                    received.countDown()
                }
            }
            assertTrue(received.await(5, TimeUnit.SECONDS))
            assertTrue(started.await(5, TimeUnit.SECONDS))
            job.cancelAndJoin()
        }
        hold.set(false)

        assertEquals(listOf("resp_1"), unconfirmed.toList())
        assertEquals(1, env.requests.count { it.method == "POST" && it.path == "/responses/resp_1/cancel" })
        assertEquals(1, env.requests.count { it.method == "GET" })
    }

    @Test
    fun `changed response id is rejected without a second create`() = withServer { env ->
        env.server.createContext("/") { exchange ->
            val recorded = env.record(exchange)
            when {
                recorded.method == "POST" && recorded.path == "/responses" -> {
                    exchange.writeSse(
                        listOf(
                            sse("response.created", "{\"type\":\"response.created\",\"sequence_number\":0,\"response\":{\"id\":\"resp_1\",\"status\":\"in_progress\"}}"),
                            sse("response.created", "{\"type\":\"response.created\",\"sequence_number\":1,\"response\":{\"id\":\"resp_2\",\"status\":\"in_progress\"}}")
                        )
                    )
                }

                recorded.method == "POST" && recorded.path == "/responses/resp_1/cancel" -> {
                    exchange.respond(200, "{\"id\":\"resp_1\",\"object\":\"response\",\"status\":\"cancelled\"}")
                }

                else -> exchange.respond(500, "{\"error\":{\"message\":\"no recreate or foreign cancel\"}}")
            }
        }

        val events = runBlocking {
            env.network.streamResumableResponses(sampleRequest(), 5, env.config).toList()
        }

        assertEquals(1, env.requests.count { it.method == "POST" && it.path == "/responses" })
        assertFalse(env.requests.any { it.method == "GET" })
        assertEquals(1, env.requests.count { it.method == "POST" && it.path == "/responses/resp_1/cancel" })
        assertFalse(env.requests.any { it.path.contains("resp_2") })
        val error = events.filterIsInstance<ResponseErrorEvent>().single { it.code == "response_identity" }
        assertTrue(error.message.contains("id", ignoreCase = true))
    }

    @Test
    fun `exhausted resume budget cancels the remote job and does not recreate`() = withServer { env ->
        env.server.createContext("/") { exchange ->
            val recorded = env.record(exchange)
            when {
                recorded.method == "POST" && recorded.path == "/responses" -> {
                    exchange.writeSse(
                        listOf(
                            sse("response.created", "{\"type\":\"response.created\",\"sequence_number\":0,\"response\":{\"id\":\"resp_1\",\"status\":\"in_progress\"}}"),
                            textDelta(1, "Hi")
                        )
                    )
                }

                recorded.method == "GET" && recorded.path == "/responses/resp_1" -> {
                    exchange.writeSse(emptyList())
                }

                recorded.method == "POST" && recorded.path == "/responses/resp_1/cancel" -> {
                    exchange.respond(200, "{\"id\":\"resp_1\",\"object\":\"response\",\"status\":\"cancelled\"}")
                }

                else -> exchange.respond(500, "{\"error\":{\"message\":\"unexpected\"}}")
            }
        }

        val events = runBlocking {
            env.network.streamResumableResponses(sampleRequest(), 5, env.config).toList()
        }

        assertEquals(1, env.requests.count { it.method == "POST" && it.path == "/responses" })
        assertEquals(2, env.requests.count { it.method == "GET" && it.path == "/responses/resp_1" })
        assertEquals(1, env.requests.count { it.method == "POST" && it.path == "/responses/resp_1/cancel" })
        assertEquals("Hi", events.filterIsInstance<OutputTextDeltaEvent>().joinToString("") { it.delta })
        assertTrue(events.any { it is ResponseErrorEvent && it.code == "resume_unavailable" })
    }

    @Test
    fun `ordinary streamResponses stays non-background and opt-in routes to resume`() = withServer { env ->
        env.server.createContext("/") { exchange ->
            val recorded = env.record(exchange)
            if (recorded.method == "POST" && recorded.path == "/responses") {
                exchange.writeSse(
                    listOf(sse("response.completed", "{\"type\":\"response.completed\",\"sequence_number\":0,\"response\":{\"id\":\"resp_1\",\"status\":\"completed\"}}"))
                )
            } else {
                exchange.respond(404, "{\"error\":{\"message\":\"unexpected\"}}")
            }
        }

        val api = OpenAIAPIImpl(env.network)
        val ordinary = runBlocking {
            api.streamResponses(sampleRequest(), 5, env.config).toList()
        }
        val ordinaryBody = NetworkClient.openAIJson.parseToJsonElement(env.requests.single().body) as JsonObject
        assertFalse(ordinaryBody.containsKey("background"))
        assertTrue(ordinary.any { it is ResponseCompletedEvent })

        env.requests.clear()
        val optedIn = runBlocking {
            api.streamResponses(sampleRequest(), 5, env.config.copy(resumableReplies = true)).toList()
        }
        val resumableBody = NetworkClient.openAIJson.parseToJsonElement(env.requests.single().body) as JsonObject
        assertEquals(true, resumableBody["background"]?.jsonPrimitive?.boolean)
        assertTrue(optedIn.any { it is ResponseCompletedEvent })
    }

    private fun sampleRequest() = ResponsesRequest(
        model = "gpt-test",
        input = listOf(ResponseInputMessage(role = "user", content = ResponseInputContent.text("hi"))),
        tools = listOf(
            ResponseFunctionTool(
                name = "lookup",
                description = "lookup",
                parameters = buildJsonObject { put("type", "object") }
            )
        )
    )

    private fun withServer(block: (TestEnv) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool()
        server.executor = executor
        server.start()
        val network = NetworkClient(CIO)
        try {
            block(TestEnv(server, network, CopyOnWriteArrayList(), ProviderRequestConfig(baseUrl(server), "token")))
        } finally {
            network().close()
            server.stop(0)
            executor.shutdownNow()
        }
    }

    private fun query(request: RecordedRequest) = request.query.orEmpty()
        .split("&")
        .filter { it.isNotEmpty() }
        .associate { part ->
            val pieces = part.split("=", limit = 2)
            pieces[0] to pieces.getOrElse(1) { "" }
        }

    private fun sse(type: String, json: String) = "event: $type\ndata: $json\n\n"

    private fun textDelta(seq: Int, delta: String) = sse(
        "response.output_text.delta",
        "{\"type\":\"response.output_text.delta\",\"sequence_number\":$seq,\"item_id\":\"msg_1\",\"output_index\":0,\"content_index\":0,\"delta\":\"$delta\"}"
    )

    private class TestEnv(
        val server: HttpServer,
        val network: NetworkClient,
        val requests: CopyOnWriteArrayList<RecordedRequest>,
        val config: ProviderRequestConfig
    ) {
        fun record(exchange: HttpExchange): RecordedRequest {
            val recorded = RecordedRequest(
                method = exchange.requestMethod,
                path = exchange.requestURI.path,
                query = exchange.requestURI.query,
                body = exchange.requestBody.use { it.readBytes().decodeToString() }
            )
            requests += recorded
            return recorded
        }
    }

    private data class RecordedRequest(
        val method: String,
        val path: String,
        val query: String?,
        val body: String
    )

    private fun baseUrl(server: HttpServer) = "http://127.0.0.1:" + server.address.port + "/"

    private fun HttpExchange.respond(status: Int, body: String) {
        responseHeaders.add("Content-Type", "application/json")
        val bytes = body.toByteArray()
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private fun HttpExchange.writeSse(chunks: List<String>, closeConnection: Boolean = true) {
        responseHeaders.add("Content-Type", "text/event-stream")
        responseHeaders.add("Connection", "close")
        val bytes = chunks.joinToString("").toByteArray()
        if (closeConnection) {
            sendResponseHeaders(200, 0)
            responseBody.use { output ->
                output.write(bytes)
                output.flush()
            }
        } else {
            sendResponseHeaders(200, 0)
            val padded = bytes + (": " + "x".repeat(32_768) + "\n\n").toByteArray()
            responseBody.write(padded)
            responseBody.flush()
        }
    }
}
