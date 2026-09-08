package dev.chungjungsoo.gptmobile.data.context

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object RemoteContextWindowParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun geminiInputTokenLimit(body: String): Int? {
        val root = parseObject(body) ?: return null
        return positiveInt(root["inputTokenLimit"] ?: root["input_token_limit"])
    }

    fun openRouterContextLength(body: String, model: String): Int? {
        val match = matchingModel(body, model) ?: return null
        return positiveInt(match["context_length"] ?: match["contextLength"])
    }

    fun groqContextWindow(body: String, model: String): Int? {
        val match = matchingModel(body, model) ?: return null
        return positiveInt(match["context_window"])
    }

    fun anthropicMaxInputTokens(body: String, model: String): Int? {
        val match = matchingModel(body, model) ?: return null
        return positiveInt(match["max_input_tokens"])
    }

    fun ollamaContextLength(body: String): Int? {
        val root = parseObject(body) ?: return null
        val modelInfo = root["model_info"] as? JsonObject
        modelInfo?.entries?.firstOrNull { (key, _) ->
            key.endsWith("context_length")
        }?.let { (_, value) ->
            positiveInt(value)?.let { return it }
        }
        positiveInt(root["context_length"] ?: root["contextLength"])?.let { return it }
        val parameters = root["parameters"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val numCtx = Regex("num_ctx\\s+(\\d+)").find(parameters)?.groupValues?.get(1)?.toIntOrNull()
        return numCtx?.takeIf { it > 0 }
    }

    private fun parse(body: String): JsonElement? = runCatching { json.parseToJsonElement(body) }.getOrNull()

    private fun parseObject(body: String): JsonObject? = parse(body) as? JsonObject

    private fun matchingModel(body: String, model: String): JsonObject? {
        val root = parse(body) ?: return null
        val objects = when {
            root is JsonObject && root["data"] != null -> root["data"]!!.jsonArray.map { it.jsonObject }
            root is JsonObject && root["id"] != null -> listOf(root)
            else -> emptyList()
        }
        return objects.firstOrNull { item ->
            item["id"]?.jsonPrimitive?.contentOrNull == model
        } ?: objects.singleOrNull()
    }

    private fun positiveInt(element: JsonElement?): Int? {
        val primitive = element as? JsonPrimitive ?: return null
        val value = primitive.intOrNull ?: primitive.contentOrNull?.toIntOrNull() ?: return null
        return value.takeIf { it > 0 }
    }
}
