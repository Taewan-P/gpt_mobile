package dev.chungjungsoo.gptmobile.data.dto.tool

import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.MessageRole
import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.TextContent
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.AnthropicTool
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.InputMessage
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.MessageRequest
import dev.chungjungsoo.gptmobile.data.dto.google.common.Content
import dev.chungjungsoo.gptmobile.data.dto.google.common.Part
import dev.chungjungsoo.gptmobile.data.dto.google.request.GenerateContentRequest
import dev.chungjungsoo.gptmobile.data.dto.google.request.GoogleTool
import dev.chungjungsoo.gptmobile.data.dto.openai.common.Role
import dev.chungjungsoo.gptmobile.data.dto.openai.common.TextContent as OpenAITextContent
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatMessage
import dev.chungjungsoo.gptmobile.data.dto.openai.request.OpenAIFunction
import dev.chungjungsoo.gptmobile.data.dto.openai.request.OpenAITool
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseInputContent
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseInputMessage
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponsesRequest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ToolRequestDtosTest {

    private val schema = buildJsonObject {
        put("type", JsonPrimitive("object"))
    }

    @Test
    fun chatCompletionRequest_withTools_setsToolsAndToolChoice() {
        val openAITool = OpenAITool(
            function = OpenAIFunction(
                name = "get_weather",
                description = "Get weather",
                parameters = schema
            )
        )

        val request = ChatCompletionRequest(
            model = "gpt-4o-mini",
            messages = listOf(
                ChatMessage(role = Role.USER, content = listOf(OpenAITextContent("hi")))
            ),
            tools = listOf(openAITool),
            toolChoice = "auto"
        )

        assertNotNull(request.tools)
        assertEquals(1, request.tools?.size)
        assertEquals("get_weather", request.tools?.first()?.function?.name)
        assertEquals("auto", request.toolChoice)
    }

    @Test
    fun responsesRequest_withTools_setsToolsAndToolChoice() {
        val openAITool = OpenAITool(
            function = OpenAIFunction(
                name = "get_weather",
                description = "Get weather",
                parameters = schema
            )
        )

        val request = ResponsesRequest(
            model = "o3-mini",
            input = listOf(
                ResponseInputMessage(
                    role = "user",
                    content = ResponseInputContent.text("hello")
                )
            ),
            tools = listOf(openAITool),
            toolChoice = "auto"
        )

        assertNotNull(request.tools)
        assertEquals(1, request.tools?.size)
        assertEquals("get_weather", request.tools?.first()?.function?.name)
        assertEquals("auto", request.toolChoice)
    }

    @Test
    fun anthropicMessageRequest_withTools_setsTools() {
        val anthropicTool = AnthropicTool(
            name = "get_weather",
            description = "Get weather",
            inputSchema = schema
        )

        val request = MessageRequest(
            model = "claude-sonnet-4-5",
            messages = listOf(
                InputMessage(role = MessageRole.USER, content = listOf(TextContent("hi")))
            ),
            maxTokens = 1024,
            tools = listOf(anthropicTool)
        )

        assertNotNull(request.tools)
        assertEquals(1, request.tools?.size)
        assertEquals("get_weather", request.tools?.first()?.name)
    }

    @Test
    fun googleGenerateContentRequest_withTools_setsTools() {
        val googleTool = GoogleTool(functionDeclarations = listOf())

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part.text("hi")))),
            tools = listOf(googleTool)
        )

        assertNotNull(request.tools)
        assertEquals(1, request.tools?.size)
        assertEquals(0, request.tools?.first()?.functionDeclarations?.size)
    }
}

