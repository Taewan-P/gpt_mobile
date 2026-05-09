package dev.chungjungsoo.gptmobile.data.tool

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

@Serializable
data class SearchResultItem(
    val title: String,
    val url: String,
    val snippet: String
)

@Serializable
data class SearchResultPayload(
    val query: String,
    val items: List<SearchResultItem>
)

@Serializable
data class WebPagePayload(
    val url: String,
    val title: String,
    val excerpt: String,
    val content: String
)

data class ToolExecutionResult(
    val output: String
)

