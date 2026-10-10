package dev.chungjungsoo.gptmobile.data.agent.provider

import dev.chungjungsoo.gptmobile.data.agent.ProviderEvent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.MessageResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.google.response.GenerateContentResponse
import dev.chungjungsoo.gptmobile.data.dto.groq.response.GroqUsage
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ChatCompletionChunk
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ChatCompletionUsage
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponsesStreamEvent
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderEventAssemblerTest {

    @Test
    fun `responses assembler preserves function call id and completed arguments`() {
        val assembler = OpenAIResponsesEventAssembler()
        val events = listOf(
            """{"type":"response.output_item.added","output_index":0,"item":{"type":"function_call","id":"fc_1","call_id":"call_exact_1","name":"weather","arguments":""}}""",
            """{"type":"response.function_call_arguments.delta","item_id":"fc_1","output_index":0,"delta":"{\"city\":"}""",
            """{"type":"response.function_call_arguments.done","item_id":"fc_1","output_index":0,"arguments":"{\"city\":\"Tokyo\"}"}"""
        ).flatMap { fixture ->
            assembler.accept(NetworkClient.openAIJson.decodeFromString<ResponsesStreamEvent>(fixture))
        }

        assertEquals(
            listOf(
                ProviderEvent.ToolCall(
                    callId = "call_exact_1",
                    name = "weather",
                    arguments = buildJsonObject { put("city", "Tokyo") }
                )
            ),
            events
        )
    }

    @Test
    fun `chat completions assembler joins indexed tool call deltas`() {
        val assembler = ChatCompletionsEventAssembler()
        val fixtures = listOf(
            """{"id":"chat_1","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_exact_2","type":"function","function":{"name":"search","arguments":"{\"query\":"}}]},"finish_reason":null}]}""",
            """{"id":"chat_1","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"agents\"}"}}]},"finish_reason":"tool_calls"}]}"""
        )
        val events = fixtures.flatMap { fixture ->
            val chunk = NetworkClient.openAIJson.decodeFromString<ChatCompletionChunk>(fixture)
            val choice = chunk.choices.orEmpty().first()
            assembler.accept(
                content = choice.delta.content,
                reasoning = null,
                toolCalls = choice.delta.toolCalls,
                finishReason = choice.finishReason
            )
        }

        assertEquals(
            listOf(
                ProviderEvent.ToolCall(
                    callId = "call_exact_2",
                    name = "search",
                    arguments = buildJsonObject { put("query", "agents") }
                )
            ),
            events
        )
    }

    @Test
    fun `chat completions assembler keeps the tool name when later deltas blank it`() {
        val events = assembleChatChunks(
            """{"id":"chat_1","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_00_M4HFfnH6Z","type":"function","function":{"name":"web_search","arguments":""}}]},"finish_reason":null}]}""",
            """{"id":"chat_1","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"","type":"function","function":{"name":"","arguments":"{\"query\":\"today's AI news\",\"maxResults\":5}"}}]},"finish_reason":"tool_calls"}]}"""
        )

        assertEquals(
            listOf(
                ProviderEvent.ToolCall(
                    callId = "call_00_M4HFfnH6Z",
                    name = "web_search",
                    arguments = buildJsonObject {
                        put("query", "today's AI news")
                        put("maxResults", 5)
                    }
                )
            ),
            events
        )
    }

    @Test
    fun `chat completions assembler accepts a name delta that omits index`() {
        val events = assembleChatChunks(
            """{"id":"chat_1","choices":[{"index":0,"delta":{"tool_calls":[{"id":"call_00_abc","type":"function","function":{"name":"web_search","arguments":""}}]},"finish_reason":null}]}""",
            """{"id":"chat_1","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"name":"","arguments":"{\"query\":\"agents\",\"maxResults\":3}"}}]},"finish_reason":"tool_calls"}]}"""
        )

        assertEquals(
            listOf(
                ProviderEvent.ToolCall(
                    callId = "call_00_abc",
                    name = "web_search",
                    arguments = buildJsonObject {
                        put("query", "agents")
                        put("maxResults", 3)
                    }
                )
            ),
            events
        )
    }

    @Test
    fun `chat completions assembler merges argument deltas that repeat the call id on another index`() {
        val events = assembleChatChunks(
            """{"id":"chat_1","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_00_abc","type":"function","function":{"name":"web_search","arguments":""}}]},"finish_reason":null}]}""",
            """{"id":"chat_1","choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"id":"call_00_abc","type":"function","function":{"name":"","arguments":"{\"query\":\"agents\"}"}}]},"finish_reason":"tool_calls"}]}"""
        )

        assertEquals(
            listOf(
                ProviderEvent.ToolCall(
                    callId = "call_00_abc",
                    name = "web_search",
                    arguments = buildJsonObject { put("query", "agents") }
                )
            ),
            events
        )
    }

    @Test
    fun `chat completions assembler keeps argument fragments that arrive under a new index`() {
        val events = assembleChatChunks(
            """{"id":"chat_1","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_00_abc","type":"function","function":{"name":"web_search","arguments":""}}]},"finish_reason":null}]}""",
            """{"id":"chat_1","choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"function":{"arguments":"{\"query\":\"agents\"}"}}]},"finish_reason":"tool_calls"}]}"""
        )

        assertEquals(
            listOf(
                ProviderEvent.ToolCall(
                    callId = "call_00_abc",
                    name = "web_search",
                    arguments = buildJsonObject { put("query", "agents") }
                )
            ),
            events
        )
    }

    @Test
    fun `chat completions assembler joins split function names and ignores a repeated full name`() {
        val split = assembleChatChunks(
            """{"id":"chat_1","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_frag","type":"function","function":{"name":"web_","arguments":""}}]},"finish_reason":null}]}""",
            """{"id":"chat_1","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"name":"search","arguments":"{\"query\":\"q\"}"}}]},"finish_reason":"tool_calls"}]}"""
        )
        val repeated = assembleChatChunks(
            """{"id":"chat_1","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_rep","type":"function","function":{"name":"web_search","arguments":""}}]},"finish_reason":null}]}""",
            """{"id":"chat_1","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"name":"web_search","arguments":"{\"query\":\"q\"}"}}]},"finish_reason":"tool_calls"}]}"""
        )

        assertEquals("web_search", (split.single() as ProviderEvent.ToolCall).name)
        assertEquals("web_search", (repeated.single() as ProviderEvent.ToolCall).name)
    }

    @Test
    fun `chat completions assembler does not merge a later tool call into the first one`() {
        val events = assembleChatChunks(
            """{"id":"chat_1","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_a","type":"function","function":{"name":"web_search","arguments":"{\"query\":\"q\"}"}}]},"finish_reason":null}]}""",
            """{"id":"chat_1","choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"id":"call_b","type":"function","function":{"name":"read_url","arguments":"{\"url\":\"https://example.com\"}"}}]},"finish_reason":"tool_calls"}]}"""
        )

        assertEquals(
            listOf(
                ProviderEvent.ToolCall("call_a", "web_search", buildJsonObject { put("query", "q") }),
                ProviderEvent.ToolCall("call_b", "read_url", buildJsonObject { put("url", "https://example.com") })
            ),
            events
        )
    }

    @Test
    fun `chat completions assembler keeps parallel tool calls when continuation names are blank`() {
        val events = assembleChatChunks(
            """{"id":"chat_1","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_a","type":"function","function":{"name":"web_search","arguments":""}},{"index":1,"id":"call_b","type":"function","function":{"name":"read_url","arguments":""}}]},"finish_reason":null}]}""",
            """{"id":"chat_1","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"name":"","arguments":"{\"query\":\"q\"}"}},{"index":1,"function":{"name":"","arguments":"{\"url\":\"https://example.com\"}"}}]},"finish_reason":"tool_calls"}]}"""
        )

        assertEquals(
            listOf(
                ProviderEvent.ToolCall("call_a", "web_search", buildJsonObject { put("query", "q") }),
                ProviderEvent.ToolCall("call_b", "read_url", buildJsonObject { put("url", "https://example.com") })
            ),
            events
        )
    }

    @Test
    fun `chat completions assembler does not invent a tool name the provider never sent`() {
        val events = assembleChatChunks(
            """{"id":"chat_1","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_x","type":"function","function":{"name":"","arguments":"{\"query\":\"q\"}"}}]},"finish_reason":"tool_calls"}]}"""
        )

        assertEquals(
            listOf(ProviderEvent.Failed("Provider returned an incomplete function call.")),
            events
        )
    }

    @Test
    fun `anthropic assembler joins tool use input json deltas`() {
        val assembler = AnthropicEventAssembler()
        val events = listOf(
            """{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"call_exact_3","name":"lookup","input":{}}}""",
            """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"id\":"}}""",
            """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"7}"}}""",
            """{"type":"content_block_stop","index":1}"""
        ).flatMap { fixture ->
            assembler.accept(NetworkClient.json.decodeFromString<MessageResponseChunk>(fixture))
        }

        assertEquals(
            listOf(
                ProviderEvent.ToolCall(
                    callId = "call_exact_3",
                    name = "lookup",
                    arguments = buildJsonObject { put("id", 7) }
                )
            ),
            events
        )
    }

    @Test
    fun `gemini mapper preserves function call id and structured args`() {
        val fixture = """{"candidates":[{"index":0,"content":{"role":"model","parts":[{"functionCall":{"id":"call_exact_4","name":"read_url","args":{"url":"https://example.com"}}}]}}]}"""
        val response = NetworkClient.json.decodeFromString<GenerateContentResponse>(fixture)

        assertEquals(
            listOf(
                ProviderEvent.ToolCall(
                    callId = "call_exact_4",
                    name = "read_url",
                    arguments = buildJsonObject { put("url", "https://example.com") }
                )
            ),
            GeminiEventMapper.accept(response)
        )
    }

    @Test
    fun `gemini mapper accepts function calls without provider ids`() {
        val fixture = """{"candidates":[{"index":0,"content":{"role":"model","parts":[{"functionCall":{"name":"read_url","args":{"url":"https://example.com"}}}]}}]}"""
        val response = NetworkClient.json.decodeFromString<GenerateContentResponse>(fixture)

        val event = GeminiEventMapper.accept(response).single() as ProviderEvent.ToolCall

        assertTrue(event.callId.isNotBlank())
        assertEquals("read_url", event.name)
        assertEquals(buildJsonObject { put("url", "https://example.com") }, event.arguments)
    }

    @Test
    fun `responses assembler emits usage before completed and ignores cached subset`() {
        val assembler = OpenAIResponsesEventAssembler()
        val fixture = """{"type":"response.completed","response":{"id":"resp_1","status":"completed","usage":{"input_tokens":12,"output_tokens":4,"input_tokens_details":{"cached_tokens":5}}}}"""

        assertEquals(
            listOf(ProviderEvent.Usage(12, 4), ProviderEvent.Completed),
            assembler.accept(NetworkClient.openAIJson.decodeFromString<ResponsesStreamEvent>(fixture))
        )
    }

    @Test
    fun `responses assembler does not fabricate usage`() {
        val assembler = OpenAIResponsesEventAssembler()
        val fixture = """{"type":"response.completed","response":{"id":"resp_1","status":"completed"}}"""

        assertEquals(
            listOf(ProviderEvent.Completed),
            assembler.accept(NetworkClient.openAIJson.decodeFromString<ResponsesStreamEvent>(fixture))
        )
    }

    @Test
    fun `anthropic assembler adds cache tokens and emits usage before completed`() {
        val assembler = AnthropicEventAssembler()
        val events = listOf(
            """{"type":"message_start","message":{"id":"msg_1","role":"assistant","content":[],"model":"claude","usage":{"input_tokens":10,"cache_creation_input_tokens":4,"cache_read_input_tokens":20,"output_tokens":1}}}""",
            """{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":7}}""",
            """{"type":"message_stop"}"""
        ).flatMap { fixture ->
            assembler.accept(NetworkClient.json.decodeFromString<MessageResponseChunk>(fixture))
        }

        assertEquals(listOf(ProviderEvent.Usage(34, 7), ProviderEvent.Completed), events)
    }

    @Test
    fun `anthropic assembler does not fabricate usage without provider counts`() {
        val assembler = AnthropicEventAssembler()

        assertEquals(
            listOf(ProviderEvent.Completed),
            assembler.accept(NetworkClient.json.decodeFromString<MessageResponseChunk>("""{"type":"message_stop"}"""))
        )
    }

    @Test
    fun `gemini mapper reads usageMetadata without adding cached subset`() {
        val response = NetworkClient.json.decodeFromString<GenerateContentResponse>(
            """{"candidates":[{"index":0,"content":{"role":"model","parts":[{"text":"hi"}]}}],"usageMetadata":{"promptTokenCount":11,"cachedContentTokenCount":4,"candidatesTokenCount":2,"thoughtsTokenCount":3}}"""
        )

        assertEquals(listOf(ProviderEvent.TextDelta("hi")), GeminiEventMapper.accept(response))
        assertEquals(ProviderEvent.Usage(11, 5), GeminiEventMapper.usage(response))
    }

    @Test
    fun `gemini mapper does not fabricate usage`() {
        val response = NetworkClient.json.decodeFromString<GenerateContentResponse>(
            """{"candidates":[{"index":0,"content":{"role":"model","parts":[{"text":"hi"}]}}]}"""
        )

        assertEquals(null, GeminiEventMapper.usage(response))
    }

    @Test
    fun `chat completion usage ignores cached subset and requires both counts`() {
        assertEquals(
            ProviderEvent.Usage(9, 2),
            ChatCompletionUsage(promptTokens = 9, completionTokens = 2).toProviderUsage()
        )
        assertEquals(null, ChatCompletionUsage(promptTokens = 9).toProviderUsage())
    }

    @Test
    fun `groq usage adds cache input tokens when the contract supplies them`() {
        assertEquals(
            ProviderEvent.Usage(14, 3),
            GroqUsage(
                promptTokens = 8,
                completionTokens = 3,
                cachedTokens = 2,
                cacheCreationInputTokens = 1,
                cacheReadInputTokens = 5
            ).toProviderUsage()
        )
        assertEquals(null, GroqUsage(promptTokens = 8).toProviderUsage())
    }

    private fun assembleChatChunks(vararg fixtures: String): List<ProviderEvent> {
        val assembler = ChatCompletionsEventAssembler()
        return fixtures.flatMap { fixture ->
            val chunk = NetworkClient.openAIJson.decodeFromString<ChatCompletionChunk>(fixture)
            val choice = chunk.choices.orEmpty().first()
            assembler.accept(
                content = choice.delta.content,
                reasoning = choice.delta.reasoning,
                toolCalls = choice.delta.toolCalls,
                finishReason = choice.finishReason
            )
        }
    }
}
