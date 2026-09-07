package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.ResourceLink
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpToolMapperTest {
    @Test
    fun `model tool names are stable bounded and collision resistant`() {
        assertEquals("mcp__docs__read", namespaceMcpToolName("docs", "read"))

        val spaced = namespaceMcpToolName("docs", "read page")
        assertEquals(spaced, namespaceMcpToolName("docs", "read page"))
        assertNotEquals(spaced, namespaceMcpToolName("docs", "read_page"))
        assertTrue(spaced.matches(Regex("[A-Za-z0-9_-]+")))
        assertTrue(namespaceMcpToolName("a".repeat(32), "x".repeat(200)).length <= 64)
    }

    @Test
    fun `remote tool schema maps to provider neutral definition`() {
        val definition = mcpToolDefinition(
            alias = "docs",
            tool = Tool(
                name = "read",
                description = "Read a document",
                inputSchema = ToolSchema(
                    properties = buildJsonObject {
                        put("uri", buildJsonObject { put("type", "string") })
                    },
                    required = listOf("uri")
                )
            )
        )

        assertEquals("mcp__docs__read", definition.name)
        assertEquals("Read a document", definition.description)
        assertEquals("object", definition.inputSchema["type"]!!.jsonPrimitive.content)
        assertEquals("string", definition.inputSchema["properties"]!!.jsonObject["uri"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("uri", definition.inputSchema["required"]!!.jsonArray.single().jsonPrimitive.content)
    }

    @Test
    fun `text and resource results are forwarded without binary blocks`() {
        val mapped = mapMcpToolResult(
            callId = "call-1",
            result = CallToolResult(
                content = listOf(
                    TextContent("hello"),
                    ResourceLink(name = "Document", uri = "https://example.com/doc", mimeType = "text/plain"),
                    ImageContent(data = "base64-image", mimeType = "image/png")
                ),
                structuredContent = buildJsonObject { put("count", 1) }
            )
        )

        val modelJson = (mapped.content as ToolResultContent.Json).value.jsonObject
        assertEquals("hello", modelJson["text"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals(1, modelJson["structuredContent"]!!.jsonObject["count"]!!.jsonPrimitive.content.toInt())
        assertEquals("https://example.com/doc", modelJson["resourceLinks"]!!.jsonArray.single().jsonObject["uri"]!!.jsonPrimitive.content)
        val trace = (mapped.traceContent as ToolResultContent.Text).text
        assertTrue(trace.contains("image/png"))
        assertTrue(trace.contains("omitted"))
        assertTrue(!modelJson.toString().contains("base64-image"))
    }

    @Test
    fun `unsupported only result gives model a safe empty summary and trace detail`() {
        val mapped = mapMcpToolResult(
            "call-2",
            CallToolResult(content = listOf(ImageContent(data = "secret-binary", mimeType = "image/webp")), isError = true)
        )

        assertEquals(
            "MCP tool returned no model-compatible content.",
            (mapped.content as ToolResultContent.Text).text
        )
        assertTrue(mapped.isError)
        val traceText = (mapped.traceContent as ToolResultContent.Text).text
        assertTrue(traceText.contains("image/webp"))
        assertTrue(!traceText.contains("secret-binary"))
    }

    @Test
    fun `oversized text is bounded before model and trace content are built`() {
        val mapped = mapMcpToolResult(
            "call-large-text",
            CallToolResult(content = listOf(TextContent("x".repeat(1024 * 1024))))
        )

        val modelText = (mapped.content as ToolResultContent.Text).text
        val traceText = (mapped.traceContent as ToolResultContent.Text).text
        assertTrue(modelText.toByteArray().size <= 64 * 1024)
        assertTrue(traceText.length < 70 * 1024)
        assertTrue(traceText.contains("truncated"))
    }

    @Test
    fun `oversized structured result is omitted without serializing it into trace`() {
        val mapped = mapMcpToolResult(
            "call-large-json",
            CallToolResult(
                content = emptyList(),
                structuredContent = buildJsonObject { put("payload", "x".repeat(1024 * 1024)) }
            )
        )

        assertEquals("MCP tool returned no model-compatible content.", (mapped.content as ToolResultContent.Text).text)
        val trace = (mapped.traceContent as ToolResultContent.Text).text
        assertTrue(trace.contains("structured JSON omitted"))
        assertTrue(trace.length < 512)
    }
}
