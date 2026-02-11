package dev.chungjungsoo.gptmobile.data.tool.builtin

import dev.chungjungsoo.gptmobile.data.dto.tool.Tool
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.tool.BuiltInTool
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class WebFetchTool @Inject constructor(
    private val networkClient: NetworkClient
) : BuiltInTool {

    override val definition: Tool = Tool(
        name = "web_fetch",
        description = "Fetch a URL and return cleaned page text",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("properties", buildJsonObject {
                put("url", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("HTTP or HTTPS URL"))
                })
                put("max_chars", buildJsonObject {
                    put("type", JsonPrimitive("integer"))
                    put("description", JsonPrimitive("Maximum characters returned"))
                    put("default", JsonPrimitive(DEFAULT_MAX_CHARS))
                })
            })
            put("required", buildJsonArray { add(JsonPrimitive("url")) })
        }
    )

    override suspend fun execute(arguments: JsonObject): String {
        val url = arguments["url"]?.let { it.jsonPrimitive.content }?.trim().orEmpty()
        if (url.isBlank()) {
            return jsonError("Missing url parameter")
        }

        if (!(url.startsWith("https://") || url.startsWith("http://"))) {
            return jsonError("Only http:// and https:// URLs are supported")
        }

        val maxChars = (arguments["max_chars"]?.let { it.jsonPrimitive.content.toIntOrNull() } ?: DEFAULT_MAX_CHARS)
            .coerceIn(MIN_MAX_CHARS, HARD_MAX_CHARS)

        return runCatching {
            val response = networkClient().get(url) {
                header(HttpHeaders.Accept, "text/html, text/plain, application/json")
            }
            val body = response.body<String>()
            val sanitized = sanitize(body)
            val cleaned = sanitized.take(maxChars)
            buildJsonObject {
                put("url", JsonPrimitive(url))
                put("status", JsonPrimitive(response.status.value))
                put("content", JsonPrimitive(cleaned))
                put("truncated", JsonPrimitive(sanitized.length > maxChars))
            }.toString()
        }.getOrElse { throwable ->
            jsonError(throwable.message ?: "Failed to run web_fetch")
        }
    }

    private fun sanitize(input: String): String {
        val noScripts = SCRIPT_STYLE_REGEX.replace(input, " ")
        val noTags = TAG_REGEX.replace(noScripts, " ")
        return noTags
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    private fun jsonError(message: String): String = buildJsonObject {
        put("error", JsonPrimitive(message))
    }.toString()

    companion object {
        private const val DEFAULT_MAX_CHARS = 4000
        private const val MIN_MAX_CHARS = 200
        private const val HARD_MAX_CHARS = 12000

        private val SCRIPT_STYLE_REGEX = Regex("(?is)<(script|style)[^>]*>.*?</\\1>")
        private val TAG_REGEX = Regex("(?is)<[^>]+>")
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}
