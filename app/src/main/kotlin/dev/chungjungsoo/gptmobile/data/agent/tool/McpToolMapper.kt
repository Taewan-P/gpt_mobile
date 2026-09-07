package dev.chungjungsoo.gptmobile.data.agent.tool

import dev.chungjungsoo.gptmobile.data.agent.AgentResourceLink
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import io.modelcontextprotocol.kotlin.sdk.types.AudioContent
import io.modelcontextprotocol.kotlin.sdk.types.BlobResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.EmbeddedResource
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.ResourceLink
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import java.security.MessageDigest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun namespaceMcpToolName(alias: String, toolName: String): String {
    val raw = "mcp__${alias}__$toolName"
    val safe = raw.map { character ->
        if ((character.isLetterOrDigit() && character.code < 128) || character == '_' || character == '-') character else '_'
    }.joinToString("")
    if (safe == raw && safe.length <= MAX_MODEL_TOOL_NAME_LENGTH) return safe

    val suffix = raw.sha256().take(10)
    return safe.take(MAX_MODEL_TOOL_NAME_LENGTH - suffix.length - 2).trimEnd('_') + "__" + suffix
}

internal fun mcpToolDefinition(alias: String, tool: Tool): AgentToolDefinition = AgentToolDefinition(
    name = namespaceMcpToolName(alias, tool.name),
    description = tool.description.orEmpty().safeTake(MAX_TOOL_DESCRIPTION_CHARS),
    inputSchema = buildJsonObject {
        require(tool.name.isNotBlank() && tool.name.length <= MAX_REMOTE_TOOL_NAME_LENGTH && tool.name.none(Char::isISOControl)) {
            "MCP tool name is invalid."
        }
        put("type", tool.inputSchema.type)
        put("properties", tool.inputSchema.properties ?: JsonObject(emptyMap()))
        if (!tool.inputSchema.required.isNullOrEmpty()) {
            put("required", JsonArray(tool.inputSchema.required.orEmpty().map(::JsonPrimitive)))
        }
        if (!tool.inputSchema.defs.isNullOrEmpty()) put("\$defs", tool.inputSchema.defs ?: JsonObject(emptyMap()))
    }.also { schema ->
        require(schema.estimatedJsonUtf8Bytes(MAX_TOOL_SCHEMA_BYTES) != null) {
            "MCP tool schema is too large."
        }
    }
)

internal fun mapMcpToolResult(callId: String, result: CallToolResult): AgentToolResult {
    val text = mutableListOf<String>()
    val links = mutableListOf<AgentResourceLink>()
    val omitted = mutableListOf<String>()
    val budget = Utf8Budget(MAX_MCP_MODEL_OUTPUT_BYTES)

    fun omit(message: String) {
        if (omitted.size < MAX_OMISSION_NOTICES) omitted += message.safeTake(MAX_OMISSION_NOTICE_CHARS)
    }

    fun addText(value: String) {
        val bounded = budget.take(value)
        if (bounded.isNotEmpty()) text += bounded
        if (bounded.length != value.length) omit("text output truncated to the model-visible limit")
    }

    fun addLink(uri: String, name: String? = null, mimeType: String? = null) {
        if (links.size >= MAX_RESOURCE_LINKS || !budget.consumeString(uri)) {
            omit("resource link omitted from model context")
            return
        }
        links += AgentResourceLink(
            uri = uri,
            name = name?.let(budget::take),
            mimeType = mimeType?.let(budget::take)
        )
    }

    result.content.forEach { block ->
        when (block) {
            is TextContent -> addText(block.text)

            is ResourceLink -> addLink(block.uri, block.title ?: block.name, block.mimeType)

            is EmbeddedResource -> when (val resource = block.resource) {
                is TextResourceContents -> {
                    addText(resource.text)
                    addLink(resource.uri, mimeType = resource.mimeType)
                }

                is BlobResourceContents -> omit("${resource.mimeType ?: "binary resource"} omitted from model context")

                else -> omit("unsupported MCP resource omitted from model context")
            }

            is ImageContent -> omit("${block.mimeType} image omitted from model context")

            is AudioContent -> omit("${block.mimeType} audio omitted from model context")
        }
    }

    val structured = result.structuredContent?.takeIf { value ->
        val size = value.estimatedJsonUtf8Bytes(budget.remaining)
        if (size == null) {
            omit("structured JSON omitted from model context because it exceeded the model-visible limit")
            false
        } else {
            budget.consume(size)
            true
        }
    }
    val modelContent = modelContent(text, structured, links)
    val traceContent = if (omitted.isEmpty()) {
        null
    } else {
        ToolResultContent.Text(buildTrace(text, structured, links, omitted))
    }
    return AgentToolResult(
        callId = callId,
        content = modelContent,
        isError = result.isError == true,
        traceContent = traceContent
    )
}

private fun modelContent(
    text: List<String>,
    structured: JsonObject?,
    links: List<AgentResourceLink>
): ToolResultContent {
    val populatedKinds = listOf(text.isNotEmpty(), structured != null, links.isNotEmpty()).count { it }
    if (populatedKinds == 0) return ToolResultContent.Text("MCP tool returned no model-compatible content.")
    if (populatedKinds == 1) {
        if (structured != null) return ToolResultContent.Json(structured)
        if (links.isNotEmpty()) return ToolResultContent.ResourceLinks(links)
        return ToolResultContent.Text(text.joinToString("\n"))
    }

    return ToolResultContent.Json(
        buildJsonObject {
            if (text.isNotEmpty()) put("text", JsonArray(text.map(::JsonPrimitive)))
            if (structured != null) put("structuredContent", structured)
            if (links.isNotEmpty()) {
                put(
                    "resourceLinks",
                    buildJsonArray {
                        links.forEach { link ->
                            add(
                                buildJsonObject {
                                    put("uri", link.uri)
                                    link.name?.let { put("name", it) }
                                    link.mimeType?.let { put("mimeType", it) }
                                }
                            )
                        }
                    }
                )
            }
        }
    )
}

private fun buildTrace(
    text: List<String>,
    structured: JsonElement?,
    links: List<AgentResourceLink>,
    omitted: List<String>
): String = buildList {
    addAll(text)
    structured?.let { add("Structured content: $it") }
    links.forEach { add("Resource: ${it.name ?: it.uri} (${it.uri})") }
    omitted.forEach { add("[$it]") }
}.joinToString("\n")

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }

private const val MAX_MODEL_TOOL_NAME_LENGTH = 64
private const val MAX_REMOTE_TOOL_NAME_LENGTH = 512
private const val MAX_TOOL_DESCRIPTION_CHARS = 4 * 1024
private const val MAX_TOOL_SCHEMA_BYTES = 64 * 1024
private const val MAX_MCP_MODEL_OUTPUT_BYTES = 64 * 1024
private const val MAX_RESOURCE_LINKS = 100
private const val MAX_OMISSION_NOTICES = 16
private const val MAX_OMISSION_NOTICE_CHARS = 256
private const val MAX_JSON_DEPTH = 128

private fun String.safeTake(maxChars: Int): String = take(maxChars).let { value ->
    if (value.lastOrNull()?.isHighSurrogate() == true) value.dropLast(1) else value
}

private class Utf8Budget(maxBytes: Int) {
    var remaining = maxBytes
        private set

    fun consume(bytes: Int): Boolean {
        if (bytes > remaining) return false
        remaining -= bytes
        return true
    }

    fun consumeString(value: String): Boolean {
        val bytes = value.utf8BytesWithin(remaining) ?: return false
        return consume(bytes)
    }

    fun take(value: String): String {
        val result = StringBuilder(minOf(value.length, remaining))
        var index = 0
        var bytes = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            val size = codePoint.utf8Bytes()
            if (bytes + size > remaining) break
            result.appendCodePoint(codePoint)
            bytes += size
            index += Character.charCount(codePoint)
        }
        remaining -= bytes
        return result.toString()
    }
}

private fun JsonElement.estimatedJsonUtf8Bytes(limit: Int): Int? {
    val counter = ByteCounter(limit)
    return if (estimateJsonBytes(this, counter, 0)) counter.used else null
}

private fun estimateJsonBytes(value: JsonElement, counter: ByteCounter, depth: Int): Boolean {
    if (depth > MAX_JSON_DEPTH) return false
    return when (value) {
        JsonNull -> counter.consume(4)

        is JsonPrimitive -> if (value.isString) {
            counter.consumeJsonString(value.content)
        } else {
            counter.consumeString(value.content)
        }

        is JsonArray -> {
            if (!counter.consume(2)) return false
            value.forEachIndexed { index, child ->
                if (index > 0 && !counter.consume(1)) return false
                if (!estimateJsonBytes(child, counter, depth + 1)) return false
            }
            true
        }

        is JsonObject -> {
            if (!counter.consume(2)) return false
            value.entries.forEachIndexed { index, (key, child) ->
                if (index > 0 && !counter.consume(1)) return false
                if (!counter.consumeJsonString(key) || !counter.consume(1)) return false
                if (!estimateJsonBytes(child, counter, depth + 1)) return false
            }
            true
        }
    }
}

private class ByteCounter(private val limit: Int) {
    var used = 0
        private set

    fun consume(bytes: Int): Boolean {
        if (bytes > limit - used) return false
        used += bytes
        return true
    }

    fun consumeString(value: String): Boolean {
        val bytes = value.utf8BytesWithin(limit - used) ?: return false
        return consume(bytes)
    }

    fun consumeJsonString(value: String): Boolean {
        if (!consume(2)) return false
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            val bytes = when (codePoint) {
                '"'.code, '\\'.code -> 2
                in 0..0x1f -> 6
                else -> codePoint.utf8Bytes()
            }
            if (!consume(bytes)) return false
            index += Character.charCount(codePoint)
        }
        return true
    }
}

private fun String.utf8BytesWithin(limit: Int): Int? {
    var bytes = 0
    var index = 0
    while (index < length) {
        val codePoint = codePointAt(index)
        bytes += codePoint.utf8Bytes()
        if (bytes > limit) return null
        index += Character.charCount(codePoint)
    }
    return bytes
}

private fun Int.utf8Bytes(): Int = when {
    this <= 0x7f -> 1
    this <= 0x7ff -> 2
    this <= 0xffff -> 3
    else -> 4
}
