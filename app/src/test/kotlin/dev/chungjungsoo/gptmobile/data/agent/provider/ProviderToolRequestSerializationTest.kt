package dev.chungjungsoo.gptmobile.data.agent.provider

import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.MessageRole
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.ToolResultContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.ToolUseContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.AnthropicTool
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.InputMessage
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.MessageRequest
import dev.chungjungsoo.gptmobile.data.dto.google.common.Content
import dev.chungjungsoo.gptmobile.data.dto.google.common.FunctionCall
import dev.chungjungsoo.gptmobile.data.dto.google.common.FunctionResponse
import dev.chungjungsoo.gptmobile.data.dto.google.common.Part
import dev.chungjungsoo.gptmobile.data.dto.google.common.Role as GoogleRole
import dev.chungjungsoo.gptmobile.data.dto.google.request.FunctionDeclaration
import dev.chungjungsoo.gptmobile.data.dto.google.request.GenerateContentRequest
import dev.chungjungsoo.gptmobile.data.dto.google.request.GoogleTool
import dev.chungjungsoo.gptmobile.data.dto.openai.common.Role
import dev.chungjungsoo.gptmobile.data.dto.openai.common.TextContent
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatFunction
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatFunctionTool
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatMessage
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatToolCall
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseFunctionCallOutput
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseFunctionTool
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponsesRequest
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderToolRequestSerializationTest {
    private val schema = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put("city", buildJsonObject { put("type", "string") })
            }
        )
    }
    private val args = buildJsonObject { put("city", "Tokyo") }

    @Test
    fun `responses request sends function output with exact call id`() {
        val request = ResponsesRequest(
            model = "gpt-test",
            input = listOf(ResponseFunctionCallOutput("call_exact", "sunny")),
            previousResponseId = "resp_previous",
            tools = listOf(ResponseFunctionTool("weather", "Weather", schema))
        )

        val json = encoded(request)
        val tool = json["tools"]!!.jsonArray.single().jsonObject
        val output = json["input"]!!.jsonArray.single().jsonObject

        assertEquals("function", tool.string("type"))
        assertEquals("weather", tool.string("name"))
        assertEquals(schema, tool["parameters"])
        assertEquals("resp_previous", json.string("previous_response_id"))
        assertEquals("function_call_output", output.string("type"))
        assertEquals("call_exact", output.string("call_id"))
        assertEquals("sunny", output.string("output"))
    }

    @Test
    fun `chat completions request replays assistant call and tool result`() {
        val request = ChatCompletionRequest(
            model = "compatible-test",
            messages = listOf(
                ChatMessage(
                    role = Role.ASSISTANT,
                    content = null,
                    toolCalls = listOf(ChatToolCall("call_exact", ChatFunction("weather", args.toString())))
                ),
                ChatMessage(
                    role = Role.TOOL,
                    content = listOf(TextContent("sunny")),
                    toolCallId = "call_exact"
                )
            ),
            tools = listOf(ChatFunctionTool("weather", "Weather", schema))
        )

        val json = encoded(request)
        val messages = json["messages"]!!.jsonArray
        val assistantCall = messages[0].jsonObject["tool_calls"]!!.jsonArray.single().jsonObject
        val toolResult = messages[1].jsonObject

        assertEquals("call_exact", assistantCall.string("id"))
        assertEquals("weather", assistantCall["function"]!!.jsonObject.string("name"))
        assertEquals("tool", toolResult.string("role"))
        assertEquals("call_exact", toolResult.string("tool_call_id"))
    }

    @Test
    fun `anthropic request replays tool use and matching tool result`() {
        val request = MessageRequest(
            model = "claude-test",
            messages = listOf(
                InputMessage(
                    role = MessageRole.ASSISTANT,
                    content = listOf(ToolUseContent("call_exact", "weather", args))
                ),
                InputMessage(
                    role = MessageRole.USER,
                    content = listOf(ToolResultContent("call_exact", "sunny", isError = false))
                )
            ),
            maxTokens = 1024,
            tools = listOf(AnthropicTool("weather", "Weather", schema))
        )

        val json = encoded(request)
        val messages = json["messages"]!!.jsonArray
        val toolUse = messages[0].jsonObject["content"]!!.jsonArray.single().jsonObject
        val toolResult = messages[1].jsonObject["content"]!!.jsonArray.single().jsonObject

        assertEquals("tool_use", toolUse.string("type"))
        assertEquals("call_exact", toolUse.string("id"))
        assertEquals(args, toolUse["input"])
        assertEquals("tool_result", toolResult.string("type"))
        assertEquals("call_exact", toolResult.string("tool_use_id"))
        assertEquals(false, toolResult["is_error"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `gemini request replays function call and response with exact id`() {
        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    role = GoogleRole.MODEL,
                    parts = listOf(Part(functionCall = FunctionCall("call_exact", "weather", args)))
                ),
                Content(
                    role = GoogleRole.USER,
                    parts = listOf(
                        Part(
                            functionResponse = FunctionResponse(
                                id = "call_exact",
                                name = "weather",
                                response = buildJsonObject { put("result", "sunny") }
                            )
                        )
                    )
                )
            ),
            tools = listOf(GoogleTool(listOf(FunctionDeclaration("weather", "Weather", schema))))
        )

        val json = encoded(request)
        val functionCall = json["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray.single().jsonObject["functionCall"]!!.jsonObject
        val functionResponse = json["contents"]!!.jsonArray[1].jsonObject["parts"]!!.jsonArray.single().jsonObject["functionResponse"]!!.jsonObject

        assertEquals("call_exact", functionCall.string("id"))
        assertEquals(args, functionCall["args"])
        assertEquals("call_exact", functionResponse.string("id"))
        assertEquals("sunny", functionResponse["response"]!!.jsonObject.string("result"))
    }

    private inline fun <reified T> encoded(value: T): JsonObject = NetworkClient.json
        .parseToJsonElement(NetworkClient.json.encodeToString(value))
        .jsonObject

    private fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content
}
