package dev.chungjungsoo.gptmobile.data.agent.provider

import dev.chungjungsoo.gptmobile.data.agent.ProviderEvent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.MessageResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.google.response.GenerateContentResponse
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ChatCompletionChunk
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
}
