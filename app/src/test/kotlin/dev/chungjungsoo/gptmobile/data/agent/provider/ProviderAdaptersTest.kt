package dev.chungjungsoo.gptmobile.data.agent.provider

import android.content.ContextWrapper
import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.agent.AgentRunEvent
import dev.chungjungsoo.gptmobile.data.agent.AgentRunner
import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolExchange
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ProviderEvent
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.context.ConversationTurn
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentBlock
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentBlockType
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentDeltaResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentStartResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.ContentStopResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.MessageResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.MessageStopResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.google.common.Content
import dev.chungjungsoo.gptmobile.data.dto.google.common.FunctionCall
import dev.chungjungsoo.gptmobile.data.dto.google.common.Part
import dev.chungjungsoo.gptmobile.data.dto.google.common.Role as GoogleRole
import dev.chungjungsoo.gptmobile.data.dto.google.request.GenerateContentRequest
import dev.chungjungsoo.gptmobile.data.dto.google.response.Candidate
import dev.chungjungsoo.gptmobile.data.dto.google.response.GenerateContentResponse
import dev.chungjungsoo.gptmobile.data.dto.groq.request.GroqChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.groq.response.GroqChatCompletionChunk
import dev.chungjungsoo.gptmobile.data.dto.groq.response.GroqChoice
import dev.chungjungsoo.gptmobile.data.dto.groq.response.GroqDelta
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponsesRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ChatCompletionChunk
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ChatFunctionDelta
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ChatToolCallDelta
import dev.chungjungsoo.gptmobile.data.dto.openai.response.Choice
import dev.chungjungsoo.gptmobile.data.dto.openai.response.Delta
import dev.chungjungsoo.gptmobile.data.dto.openai.response.OutputTextDeltaEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseCompletedEvent
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponseObject
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponsesStreamEvent
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPI
import dev.chungjungsoo.gptmobile.data.network.GoogleAPI
import dev.chungjungsoo.gptmobile.data.network.GroqAPI
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.network.OpenAIAPI
import dev.chungjungsoo.gptmobile.data.network.ProviderRequestConfig
import dev.chungjungsoo.gptmobile.data.network.UploadedProviderFile
import java.util.ArrayDeque
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderAdaptersTest {
    private val definition = AgentToolDefinition(
        name = "weather",
        description = "Weather",
        inputSchema = buildJsonObject { put("type", "object") }
    )
    private val call = ProviderEvent.ToolCall(
        callId = "call_exact",
        name = "weather",
        arguments = buildJsonObject { put("city", "Tokyo") }
    )
    private val result = AgentToolResult(
        callId = "call_exact",
        content = ToolResultContent.Text("sunny"),
        isError = false
    )

    @Test
    fun `openai responses adapter preserves ordinary text and per request credentials`() = runBlocking {
        val api = FakeOpenAIAPI(
            responseRounds = ArrayDeque(
                listOf(
                    flowOf(
                        OutputTextDeltaEvent("item_1", 0, 0, "hello"),
                        ResponseCompletedEvent(ResponseObject("resp_1", "completed"))
                    )
                )
            )
        )
        val adapter = OpenAIResponsesAdapter(api, attachmentEncoder())

        val events = adapter.openSession(turns(), platform(ClientType.OPENAI)).streamRound(emptyList(), emptyList()).toList()

        assertEquals(
            listOf(ProviderEvent.TextDelta("hello"), ProviderEvent.Completed),
            events
        )
        assertNull(api.responseRequests.single().tools)
        assertEquals(ProviderRequestConfig("https://provider.example/v1", "secret"), api.configs.single())
    }

    @Test
    fun `chat completions adapter replays exact tool call id on the next round`() = runBlocking {
        val api = FakeOpenAIAPI(
            chatRounds = ArrayDeque(
                listOf(
                    flowOf(
                        ChatCompletionChunk(
                            choices = listOf(
                                Choice(
                                    index = 0,
                                    delta = Delta(
                                        toolCalls = listOf(
                                            ChatToolCallDelta(
                                                index = 0,
                                                id = "call_exact",
                                                function = ChatFunctionDelta("weather", "{\"city\":\"Tokyo\"}")
                                            )
                                        )
                                    ),
                                    finishReason = "tool_calls"
                                )
                            )
                        )
                    ),
                    flowOf(
                        ChatCompletionChunk(
                            choices = listOf(
                                Choice(0, Delta(content = "done"), finishReason = "stop")
                            )
                        )
                    )
                )
            )
        )
        val adapter = OpenAICompatibleAdapter(api, FakeGroqAPI(), attachmentEncoder())
        val session = adapter.openSession(turns(), platform(ClientType.CUSTOM))

        assertEquals(listOf(call, ProviderEvent.Completed), session.streamRound(listOf(definition), emptyList()).toList())
        assertEquals(
            listOf(ProviderEvent.TextDelta("done"), ProviderEvent.Completed),
            session.streamRound(listOf(definition), listOf(AgentToolExchange(listOf(call), listOf(result)))).toList()
        )
        val continuation = api.chatRequests.last().messages.takeLast(2)
        assertEquals("call_exact", continuation[0].toolCalls!!.single().id)
        assertEquals("call_exact", continuation[1].toolCallId)
    }

    @Test
    fun `chat completions omits tools for chat only profiles`() = runBlocking {
        val api = FakeOpenAIAPI(chatRounds = ArrayDeque(listOf(emptyFlow())))

        OpenAICompatibleAdapter(api, FakeGroqAPI(), attachmentEncoder())
            .openSession(turns(), platform(ClientType.CUSTOM))
            .streamRound(emptyList(), emptyList())
            .toList()

        assertNull(api.chatRequests.single().tools)
    }

    @Test
    fun `groq adapter uses native tools and replays exact tool call id`() = runBlocking {
        val groq = FakeGroqAPI(
            ArrayDeque(
                listOf(
                    flowOf(
                        GroqChatCompletionChunk(
                            choices = listOf(
                                GroqChoice(
                                    index = 0,
                                    delta = GroqDelta(
                                        toolCalls = listOf(
                                            ChatToolCallDelta(
                                                index = 0,
                                                id = "call_exact",
                                                function = ChatFunctionDelta("weather", "{\"city\":\"Tokyo\"}")
                                            )
                                        )
                                    ),
                                    finishReason = "tool_calls"
                                )
                            )
                        )
                    ),
                    flowOf(
                        GroqChatCompletionChunk(
                            choices = listOf(
                                GroqChoice(0, delta = GroqDelta(content = "done"), finishReason = "stop")
                            )
                        )
                    )
                )
            )
        )
        val session = OpenAICompatibleAdapter(FakeOpenAIAPI(), groq, attachmentEncoder())
            .openSession(turns(), platform(ClientType.GROQ))

        assertEquals(listOf(call, ProviderEvent.Completed), session.streamRound(listOf(definition), emptyList()).toList())
        assertEquals(
            listOf(ProviderEvent.TextDelta("done"), ProviderEvent.Completed),
            session.streamRound(listOf(definition), listOf(AgentToolExchange(listOf(call), listOf(result)))).toList()
        )
        assertEquals("weather", groq.requests.first().tools!!.single().function.name)
        val continuation = groq.requests.last().messages.takeLast(2)
        assertEquals("call_exact", continuation[0].toolCalls!!.single().id)
        assertEquals("call_exact", continuation[1].toolCallId)
        assertEquals(
            listOf(
                ProviderRequestConfig("https://provider.example/v1", "secret"),
                ProviderRequestConfig("https://provider.example/v1", "secret")
            ),
            groq.configs
        )
    }

    @Test
    fun `groq omits tools for chat only profiles`() = runBlocking {
        val groq = FakeGroqAPI(ArrayDeque(listOf(emptyFlow())))

        OpenAICompatibleAdapter(FakeOpenAIAPI(), groq, attachmentEncoder())
            .openSession(turns(), platform(ClientType.GROQ))
            .streamRound(emptyList(), emptyList())
            .toList()

        assertNull(groq.requests.single().tools)
    }

    @Test
    fun `anthropic adapter replays exact tool use id on the next round`() = runBlocking {
        val api = FakeAnthropicAPI(
            ArrayDeque(
                listOf(
                    flowOf(
                        ContentStartResponseChunk(
                            index = 0,
                            contentBlock = ContentBlock(
                                type = ContentBlockType.TOOL_USE,
                                id = "call_exact",
                                name = "weather"
                            )
                        ),
                        ContentDeltaResponseChunk(
                            index = 0,
                            delta = ContentBlock(
                                type = ContentBlockType.INPUT_JSON_DELTA,
                                partialJson = "{\"city\":\"Tokyo\"}"
                            )
                        ),
                        ContentStopResponseChunk(0),
                        MessageStopResponseChunk
                    ),
                    flowOf(
                        ContentDeltaResponseChunk(
                            index = 0,
                            delta = ContentBlock(type = ContentBlockType.DELTA, text = "done")
                        ),
                        MessageStopResponseChunk
                    )
                )
            )
        )
        val session = AnthropicMessagesAdapter(api, attachmentEncoder())
            .openSession(turns(), platform(ClientType.ANTHROPIC))

        assertEquals(listOf(call, ProviderEvent.Completed), session.streamRound(listOf(definition), emptyList()).toList())
        assertEquals(
            listOf(ProviderEvent.TextDelta("done"), ProviderEvent.Completed),
            session.streamRound(listOf(definition), listOf(AgentToolExchange(listOf(call), listOf(result)))).toList()
        )
        val continuation = api.requests.last().messages.takeLast(2)
        val toolUse = continuation[0].content.single() as dev.chungjungsoo.gptmobile.data.dto.anthropic.common.ToolUseContent
        val toolResult = continuation[1].content.single() as dev.chungjungsoo.gptmobile.data.dto.anthropic.common.ToolResultContent
        assertEquals("call_exact", toolUse.id)
        assertEquals("call_exact", toolResult.toolUseId)
    }

    @Test
    fun `anthropic 4_6 reasoning uses adaptive thinking without a token budget`() = runBlocking {
        listOf("claude-sonnet-4-6", "claude-opus-4-6-20260205").forEach { model ->
            val api = FakeAnthropicAPI(ArrayDeque(listOf(emptyFlow())))

            AnthropicMessagesAdapter(api, attachmentEncoder())
                .openSession(turns(), platform(ClientType.ANTHROPIC).copy(model = model, reasoning = true))
                .streamRound(listOf(definition), emptyList())
                .toList()

            val thinking = requestJson(api.requests.single())["thinking"]!!.jsonObject
            assertEquals("adaptive", thinking["type"]!!.jsonPrimitive.content)
            assertEquals("summarized", thinking["display"]!!.jsonPrimitive.content)
            assertFalse(thinking.containsKey("budget_tokens"))
            assertEquals(emptySet<String>(), api.configs.single().anthropicBetaFeatures)
        }
    }

    @Test
    fun `anthropic reasoning disabled omits thinking configuration`() = runBlocking {
        listOf("claude-sonnet-4-5-20250929", "claude-opus-4-6").forEach { model ->
            val api = FakeAnthropicAPI(ArrayDeque(listOf(emptyFlow())))

            AnthropicMessagesAdapter(api, attachmentEncoder())
                .openSession(turns(), platform(ClientType.ANTHROPIC).copy(model = model, reasoning = false))
                .streamRound(listOf(definition), emptyList())
                .toList()

            assertFalse(requestJson(api.requests.single()).containsKey("thinking"))
            assertEquals(emptySet<String>(), api.configs.single().anthropicBetaFeatures)
        }
    }

    @Test
    fun `anthropic omits reasoning configuration for models without thinking support`() = runBlocking {
        val api = FakeAnthropicAPI(ArrayDeque(listOf(emptyFlow())))

        AnthropicMessagesAdapter(api, attachmentEncoder())
            .openSession(
                turns(),
                platform(ClientType.ANTHROPIC).copy(model = "claude-3-5-sonnet-20240620", reasoning = true)
            )
            .streamRound(listOf(definition), emptyList())
            .toList()

        assertFalse(requestJson(api.requests.single()).containsKey("thinking"))
        assertEquals(emptySet<String>(), api.configs.single().anthropicBetaFeatures)
    }

    @Test
    fun `anthropic reasoning disabled respects default on model capabilities`() = runBlocking {
        listOf("claude-opus-5", "claude-sonnet-5-20260801").forEach { model ->
            val api = FakeAnthropicAPI(ArrayDeque(listOf(emptyFlow())))

            AnthropicMessagesAdapter(api, attachmentEncoder())
                .openSession(turns(), platform(ClientType.ANTHROPIC).copy(model = model, reasoning = false))
                .streamRound(listOf(definition), emptyList())
                .toList()

            val thinking = requestJson(api.requests.single())["thinking"]!!.jsonObject
            assertEquals("disabled", thinking["type"]!!.jsonPrimitive.content)
            assertFalse(thinking.containsKey("budget_tokens"))
            assertEquals(emptySet<String>(), api.configs.single().anthropicBetaFeatures)
        }

        listOf("claude-fable-5", "claude-mythos-5").forEach { model ->
            val api = FakeAnthropicAPI(ArrayDeque(listOf(emptyFlow())))

            AnthropicMessagesAdapter(api, attachmentEncoder())
                .openSession(turns(), platform(ClientType.ANTHROPIC).copy(model = model, reasoning = false))
                .streamRound(listOf(definition), emptyList())
                .toList()

            assertFalse(requestJson(api.requests.single()).containsKey("thinking"))
        }
    }

    @Test
    fun `every declared anthropic model resolves a summarized thinking policy`() {
        ModelConstants.anthropicModels.forEach { model ->
            val policy = anthropicThinkingPolicy(model, reasoningEnabled = true, hasTools = true)

            assertEquals("$model should summarize reasoning", "summarized", policy.config?.display)
        }
    }

    @Test
    fun `anthropic manual thinking enables interleaving only for supported tool models`() = runBlocking {
        listOf("claude-sonnet-4-5-20250929", "claude-opus-4-1-20250805").forEach { model ->
            val api = FakeAnthropicAPI(ArrayDeque(listOf(emptyFlow())))

            AnthropicMessagesAdapter(api, attachmentEncoder())
                .openSession(turns(), platform(ClientType.ANTHROPIC).copy(model = model, reasoning = true))
                .streamRound(listOf(definition), emptyList())
                .toList()

            val thinking = requestJson(api.requests.single())["thinking"]!!.jsonObject
            assertEquals("enabled", thinking["type"]!!.jsonPrimitive.content)
            assertEquals(10_000, thinking["budget_tokens"]!!.jsonPrimitive.content.toInt())
            assertEquals("summarized", thinking["display"]!!.jsonPrimitive.content)
            assertEquals(setOf(ANTHROPIC_INTERLEAVED_THINKING_BETA), api.configs.single().anthropicBetaFeatures)
        }

        val haikuApi = FakeAnthropicAPI(ArrayDeque(listOf(emptyFlow())))
        AnthropicMessagesAdapter(haikuApi, attachmentEncoder())
            .openSession(
                turns(),
                platform(ClientType.ANTHROPIC).copy(model = "claude-haiku-4-5-20251001", reasoning = true)
            )
            .streamRound(listOf(definition), emptyList())
            .toList()

        assertEquals(emptySet<String>(), haikuApi.configs.single().anthropicBetaFeatures)
    }

    @Test
    fun `anthropic streamed tool call executes and continues through the agent runner`() = runBlocking {
        val api = FakeAnthropicAPI(
            ArrayDeque(
                listOf(
                    flowOf(
                        ContentStartResponseChunk(
                            index = 0,
                            contentBlock = ContentBlock(
                                type = ContentBlockType.TOOL_USE,
                                id = "call_exact",
                                name = "weather"
                            )
                        ),
                        ContentDeltaResponseChunk(
                            index = 0,
                            delta = ContentBlock(
                                type = ContentBlockType.INPUT_JSON_DELTA,
                                partialJson = "{\"city\":\"Tokyo\"}"
                            )
                        ),
                        ContentStopResponseChunk(0),
                        MessageStopResponseChunk
                    ),
                    flowOf(
                        ContentDeltaResponseChunk(
                            index = 0,
                            delta = ContentBlock(type = ContentBlockType.DELTA, text = "sunny")
                        ),
                        MessageStopResponseChunk
                    )
                )
            )
        )
        val session = AnthropicMessagesAdapter(api, attachmentEncoder())
            .openSession(turns(), platform(ClientType.ANTHROPIC))
        val tool = RecordingAgentTool(definition)

        val events = AgentRunner().run(session, listOf(tool)).toList()

        assertEquals(call.arguments, tool.arguments)
        assertEquals("call_exact", events.filterIsInstance<AgentRunEvent.ToolFinished>().single().result.callId)
        assertEquals("sunny", events.providerText())
        val continuation = api.requests.last().messages.takeLast(2)
        assertEquals(
            "call_exact",
            (continuation[1].content.single() as dev.chungjungsoo.gptmobile.data.dto.anthropic.common.ToolResultContent).toolUseId
        )
    }

    @Test
    fun `anthropic adapter replays signed thinking block before tool use`() = runBlocking {
        val firstRound = listOf(
            """{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":""}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"checking"}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"sig_exact"}}""",
            """{"type":"content_block_stop","index":0}""",
            """{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"call_exact","name":"weather","input":{}}}""",
            """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"city\":\"Tokyo\"}"}}""",
            """{"type":"content_block_stop","index":1}""",
            """{"type":"message_stop"}"""
        ).map { NetworkClient.json.decodeFromString<MessageResponseChunk>(it) }
        val api = FakeAnthropicAPI(
            ArrayDeque(
                listOf(
                    flowOf(*firstRound.toTypedArray()),
                    flowOf(MessageStopResponseChunk)
                )
            )
        )
        val session = AnthropicMessagesAdapter(api, attachmentEncoder())
            .openSession(
                turns(),
                platform(ClientType.ANTHROPIC).copy(model = "claude-opus-4-6", reasoning = true)
            )

        val emittedCall = session.streamRound(listOf(definition), emptyList()).toList()
            .filterIsInstance<ProviderEvent.ToolCall>()
            .single()
        session.streamRound(
            listOf(definition),
            listOf(AgentToolExchange(listOf(emittedCall), listOf(result)))
        ).toList()

        val messages = requestJson(api.requests.last())["messages"]!!.jsonArray
        val assistantContent = messages[messages.lastIndex - 1].jsonObject["content"]!!.jsonArray
        assertEquals("thinking", assistantContent[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("checking", assistantContent[0].jsonObject["thinking"]!!.jsonPrimitive.content)
        assertEquals("sig_exact", assistantContent[0].jsonObject["signature"]!!.jsonPrimitive.content)
        assertEquals("tool_use", assistantContent[1].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("adaptive", requestJson(api.requests.last())["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `anthropic adapter replays redacted thinking unchanged before tool results`() = runBlocking {
        val firstRound = listOf(
            """{"type":"content_block_start","index":0,"content_block":{"type":"redacted_thinking","data":"encrypted_exact"}}""",
            """{"type":"content_block_stop","index":0}""",
            """{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"call_exact","name":"weather","input":{}}}""",
            """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"city\":\"Tokyo\"}"}}""",
            """{"type":"content_block_stop","index":1}""",
            """{"type":"message_stop"}"""
        ).map { NetworkClient.json.decodeFromString<MessageResponseChunk>(it) }
        val api = FakeAnthropicAPI(
            ArrayDeque(
                listOf(
                    flowOf(*firstRound.toTypedArray()),
                    flowOf(MessageStopResponseChunk)
                )
            )
        )
        val session = AnthropicMessagesAdapter(api, attachmentEncoder())
            .openSession(
                turns(),
                platform(ClientType.ANTHROPIC).copy(model = "claude-sonnet-4-6", reasoning = true)
            )

        val emittedCall = session.streamRound(listOf(definition), emptyList()).toList()
            .filterIsInstance<ProviderEvent.ToolCall>()
            .single()
        session.streamRound(
            listOf(definition),
            listOf(AgentToolExchange(listOf(emittedCall), listOf(result)))
        ).toList()

        val messages = requestJson(api.requests.last())["messages"]!!.jsonArray
        val assistantContent = messages[messages.lastIndex - 1].jsonObject["content"]!!.jsonArray
        assertEquals("redacted_thinking", assistantContent[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("encrypted_exact", assistantContent[0].jsonObject["data"]!!.jsonPrimitive.content)
        assertEquals("tool_use", assistantContent[1].jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `anthropic omits tools for chat only profiles`() = runBlocking {
        val api = FakeAnthropicAPI(ArrayDeque(listOf(emptyFlow())))

        AnthropicMessagesAdapter(api, attachmentEncoder())
            .openSession(turns(), platform(ClientType.ANTHROPIC))
            .streamRound(emptyList(), emptyList())
            .toList()

        assertNull(api.requests.single().tools)
    }

    @Test
    fun `gemini adapter replays exact function call id on the next round`() = runBlocking {
        val api = FakeGoogleAPI(
            ArrayDeque(
                listOf(
                    flowOf(
                        GenerateContentResponse(
                            candidates = listOf(
                                Candidate(
                                    content = Content(
                                        role = GoogleRole.MODEL,
                                        parts = listOf(Part(functionCall = FunctionCall("call_exact", "weather", call.arguments)))
                                    )
                                )
                            )
                        )
                    ),
                    flowOf(
                        GenerateContentResponse(
                            candidates = listOf(
                                Candidate(content = Content(role = GoogleRole.MODEL, parts = listOf(Part.text("done"))))
                            )
                        )
                    )
                )
            )
        )
        val session = GeminiAdapter(api, attachmentEncoder())
            .openSession(turns(), platform(ClientType.GOOGLE))

        assertEquals(listOf(call, ProviderEvent.Completed), session.streamRound(listOf(definition), emptyList()).toList())
        assertEquals(
            listOf(ProviderEvent.TextDelta("done"), ProviderEvent.Completed),
            session.streamRound(listOf(definition), listOf(AgentToolExchange(listOf(call), listOf(result)))).toList()
        )
        assertEquals("AUTO", api.requests.first().toolConfig?.functionCallingConfig?.mode)
        assertEquals("weather", api.requests.first().tools!!.single().functionDeclarations.single().name)
        val continuation = api.requests.last().contents.takeLast(2)
        assertEquals("call_exact", continuation[0].parts.single().functionCall!!.id)
        assertEquals("call_exact", continuation[1].parts.single().functionResponse!!.id)
    }

    @Test
    fun `gemini streamed function call executes and continues through the agent runner`() = runBlocking {
        val api = FakeGoogleAPI(
            ArrayDeque(
                listOf(
                    flowOf(
                        GenerateContentResponse(
                            candidates = listOf(
                                Candidate(
                                    content = Content(
                                        role = GoogleRole.MODEL,
                                        parts = listOf(Part(functionCall = FunctionCall("call_exact", "weather", call.arguments)))
                                    )
                                )
                            )
                        )
                    ),
                    flowOf(
                        GenerateContentResponse(
                            candidates = listOf(
                                Candidate(content = Content(role = GoogleRole.MODEL, parts = listOf(Part.text("sunny"))))
                            )
                        )
                    )
                )
            )
        )
        val session = GeminiAdapter(api, attachmentEncoder())
            .openSession(turns(), platform(ClientType.GOOGLE))
        val tool = RecordingAgentTool(definition)

        val events = AgentRunner().run(session, listOf(tool)).toList()

        assertEquals(call.arguments, tool.arguments)
        assertEquals("call_exact", events.filterIsInstance<AgentRunEvent.ToolFinished>().single().result.callId)
        assertEquals("sunny", events.providerText())
        val continuation = api.requests.last().contents.takeLast(2)
        assertEquals("call_exact", continuation[1].parts.single().functionResponse!!.id)
    }

    @Test
    fun `gemini adapter preserves thought signature and omitted provider id`() = runBlocking {
        val firstRound = NetworkClient.json.decodeFromString<GenerateContentResponse>(
            """{"candidates":[{"index":0,"content":{"role":"model","parts":[{"thought":true,"text":"checking"},{"thoughtSignature":"signature_exact","functionCall":{"name":"weather","args":{"city":"Tokyo"}}}]}}]}"""
        )
        val api = FakeGoogleAPI(
            ArrayDeque(
                listOf(
                    flowOf(firstRound),
                    flowOf(GenerateContentResponse(candidates = emptyList()))
                )
            )
        )
        val session = GeminiAdapter(api, attachmentEncoder())
            .openSession(turns(), platform(ClientType.GOOGLE).copy(reasoning = true))

        val emittedCall = session.streamRound(listOf(definition), emptyList()).toList()
            .filterIsInstance<ProviderEvent.ToolCall>()
            .single()
        session.streamRound(
            listOf(definition),
            listOf(AgentToolExchange(listOf(emittedCall), listOf(result.copy(callId = emittedCall.callId))))
        ).toList()

        val contents = requestJson(api.requests.last())["contents"]!!.jsonArray
        val replayedCall = contents[contents.lastIndex - 1].jsonObject["parts"]!!.jsonArray[1].jsonObject
        val replayedResponse = contents.last().jsonObject["parts"]!!.jsonArray.single().jsonObject["functionResponse"]!!.jsonObject
        assertEquals("signature_exact", replayedCall["thoughtSignature"]!!.jsonPrimitive.content)
        assertFalse(replayedCall["functionCall"]!!.jsonObject.containsKey("id"))
        assertFalse(replayedResponse.containsKey("id"))
    }

    @Test
    fun `gemini strips additionalProperties from tool schemas`() {
        val schema = buildJsonObject {
            put("type", "object")
            put("properties", JsonObject(emptyMap()))
            put("additionalProperties", false)
        }

        val sanitized = geminiToolParameters(schema)

        assertEquals("object", sanitized.getValue("type").toString().trim('"'))
        assertFalse(sanitized.containsKey("additionalProperties"))
    }

    @Test
    fun `gemini keeps nested properties named additionalProperties`() {
        val schema = buildJsonObject {
            put("type", "object")
            put("additionalProperties", false)
            put(
                "properties",
                buildJsonObject {
                    put(
                        "additionalProperties",
                        buildJsonObject {
                            put("type", "string")
                        }
                    )
                    put(
                        "nested",
                        buildJsonObject {
                            put("type", "object")
                            put("additionalProperties", false)
                            put(
                                "properties",
                                buildJsonObject {
                                    put(
                                        "additionalProperties",
                                        buildJsonObject {
                                            put("type", "boolean")
                                            put("additionalProperties", false)
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }

        val sanitized = geminiToolParameters(schema)

        assertFalse(sanitized.containsKey("additionalProperties"))
        val properties = sanitized.getValue("properties").jsonObject
        assertEquals("string", properties.getValue("additionalProperties").jsonObject.getValue("type").jsonPrimitive.content)
        val nested = properties.getValue("nested").jsonObject
        assertFalse(nested.containsKey("additionalProperties"))
        val nestedProperty = nested.getValue("properties").jsonObject.getValue("additionalProperties").jsonObject
        assertEquals("boolean", nestedProperty.getValue("type").jsonPrimitive.content)
        assertFalse(nestedProperty.containsKey("additionalProperties"))
    }

    @Test
    fun `gemini omits tools for chat only profiles`() = runBlocking {
        val api = FakeGoogleAPI(ArrayDeque(listOf(emptyFlow())))

        GeminiAdapter(api, attachmentEncoder())
            .openSession(turns(), platform(ClientType.GOOGLE))
            .streamRound(emptyList(), emptyList())
            .toList()

        assertNull(api.requests.single().tools)
    }

    private fun turns() = listOf(
        ConversationTurn(
            userMessage = MessageV2(content = "hello", platformType = null),
            assistantMessage = null,
            isCurrentTurn = true
        )
    )

    private inline fun <reified T> requestJson(value: T) = NetworkClient.json
        .parseToJsonElement(NetworkClient.json.encodeToString(value))
        .jsonObject

    private fun platform(type: ClientType) = PlatformV2(
        uid = "profile",
        name = "Provider",
        compatibleType = type,
        apiUrl = "https://provider.example/v1",
        token = "secret",
        model = "model-test"
    )

    private fun attachmentEncoder() = ProviderAttachmentEncoder(ContextWrapper(null))

    private fun List<AgentRunEvent>.providerText(): String = mapNotNull { event ->
        ((event as? AgentRunEvent.Provider)?.event as? ProviderEvent.TextDelta)?.text
    }.joinToString("")

    private class RecordingAgentTool(
        override val definition: AgentToolDefinition
    ) : AgentTool {
        var arguments: JsonObject? = null

        override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult {
            this.arguments = arguments
            return AgentToolResult(
                callId = callId,
                content = ToolResultContent.Text("sunny"),
                isError = false
            )
        }
    }

    private class FakeOpenAIAPI(
        private val chatRounds: ArrayDeque<Flow<ChatCompletionChunk>> = ArrayDeque(),
        private val responseRounds: ArrayDeque<Flow<ResponsesStreamEvent>> = ArrayDeque()
    ) : OpenAIAPI {
        val chatRequests = mutableListOf<ChatCompletionRequest>()
        val responseRequests = mutableListOf<ResponsesRequest>()
        val configs = mutableListOf<ProviderRequestConfig>()

        override fun streamChatCompletion(
            request: ChatCompletionRequest,
            timeoutSeconds: Int,
            config: ProviderRequestConfig
        ): Flow<ChatCompletionChunk> {
            chatRequests += request
            configs += config
            return chatRounds.removeFirst()
        }

        override fun streamResponses(
            request: ResponsesRequest,
            timeoutSeconds: Int,
            config: ProviderRequestConfig
        ): Flow<ResponsesStreamEvent> {
            responseRequests += request
            configs += config
            return responseRounds.removeFirst()
        }

        override suspend fun uploadFile(
            filePath: String,
            fileName: String,
            mimeType: String,
            config: ProviderRequestConfig
        ) = UploadedProviderFile("file", mimeType)

        override suspend fun isFileAvailable(fileId: String, config: ProviderRequestConfig) = false
    }

    private class FakeGroqAPI(
        private val rounds: ArrayDeque<Flow<GroqChatCompletionChunk>> = ArrayDeque()
    ) : GroqAPI {
        val requests = mutableListOf<GroqChatCompletionRequest>()
        val configs = mutableListOf<ProviderRequestConfig>()

        override fun streamChatCompletion(
            request: GroqChatCompletionRequest,
            timeoutSeconds: Int,
            config: ProviderRequestConfig
        ): Flow<GroqChatCompletionChunk> {
            requests += request
            configs += config
            return rounds.removeFirst()
        }
    }

    private class FakeAnthropicAPI(
        private val rounds: ArrayDeque<Flow<MessageResponseChunk>>
    ) : AnthropicAPI {
        val requests = mutableListOf<dev.chungjungsoo.gptmobile.data.dto.anthropic.request.MessageRequest>()
        val configs = mutableListOf<ProviderRequestConfig>()

        override fun streamChatMessage(
            messageRequest: dev.chungjungsoo.gptmobile.data.dto.anthropic.request.MessageRequest,
            timeoutSeconds: Int,
            config: ProviderRequestConfig
        ): Flow<MessageResponseChunk> {
            requests += messageRequest
            configs += config
            return rounds.removeFirst()
        }

        override suspend fun uploadFile(
            filePath: String,
            fileName: String,
            mimeType: String,
            config: ProviderRequestConfig
        ) = UploadedProviderFile("file", mimeType)

        override suspend fun isFileAvailable(fileId: String, config: ProviderRequestConfig) = false
    }

    private class FakeGoogleAPI(
        private val rounds: ArrayDeque<Flow<GenerateContentResponse>>
    ) : GoogleAPI {
        val requests = mutableListOf<GenerateContentRequest>()

        override fun streamGenerateContent(
            request: GenerateContentRequest,
            model: String,
            timeoutSeconds: Int,
            config: ProviderRequestConfig
        ): Flow<GenerateContentResponse> {
            requests += request
            return rounds.removeFirst()
        }

        override suspend fun uploadFile(
            filePath: String,
            fileName: String,
            mimeType: String,
            config: ProviderRequestConfig
        ) = UploadedProviderFile("file", mimeType)

        override suspend fun isFileAvailable(fileName: String, config: ProviderRequestConfig) = false
    }
}
