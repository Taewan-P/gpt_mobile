package dev.chungjungsoo.gptmobile.data.tool

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import javax.inject.Inject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

interface ToolRegistry {
    fun toolsFor(platform: PlatformV2): List<ToolDefinition>

    companion object {
        fun defaultTools(): List<ToolDefinition> = listOf(
            ToolDefinition(
                name = "search_web",
                description = "Search the public web for current or factual information and return the most relevant results. Use this when the answer may have changed recently, when you need verification from sources, or when the user asks for live information. If the first search is weak, irrelevant, or blocked, refine the query and search again before giving up. Do not ask the user to search manually.",
                parameters = buildJsonObject {
                    put(
                        "type",
                        "object"
                    )
                    put(
                        "properties",
                        buildJsonObject {
                            put(
                                "query",
                                buildJsonObject {
                                    put(
                                        "type",
                                        "string"
                                    )
                                    put(
                                        "description",
                                        "The exact web search query to run. Prefer a concise, specific query that names the topic, entity, date, or source when helpful."
                                    )
                                }
                            )
                            put(
                                "top_k",
                                buildJsonObject {
                                    put(
                                        "type",
                                        "integer"
                                    )
                                    put(
                                        "minimum",
                                        1
                                    )
                                    put(
                                        "maximum",
                                        10
                                    )
                                    put(
                                        "description",
                                        "Maximum number of results to return. Use a smaller value for focused searches and a larger value when the first page may be noisy."
                                    )
                                }
                            )
                        }
                    )
                    putJsonArray("required") {
                        add(JsonPrimitive("query"))
                    }
                    put(
                        "additionalProperties",
                        false
                    )
                }
            ),
            ToolDefinition(
                name = "open_url",
                description = "Fetch the readable content of a public HTTP or HTTPS page and return a cleaned summary of the page text. Use this after search_web when a result looks promising, when you need to verify a source directly, or when the user asked for details from a specific page. If the page is blocked, empty, or mostly navigation, try another promising URL before giving up.",
                parameters = buildJsonObject {
                    put(
                        "type",
                        "object"
                    )
                    put(
                        "properties",
                        buildJsonObject {
                            put(
                                "url",
                                buildJsonObject {
                                    put(
                                        "type",
                                        "string"
                                    )
                                    put(
                                        "description",
                                        "A public HTTP or HTTPS URL that can be fetched without login, private network access, or special credentials."
                                    )
                                }
                            )
                        }
                    )
                    putJsonArray("required") {
                        add(JsonPrimitive("url"))
                    }
                    put(
                        "additionalProperties",
                        false
                    )
                }
            )
        )
    }
}

class DefaultToolRegistry @Inject constructor() : ToolRegistry {
    override fun toolsFor(platform: PlatformV2): List<ToolDefinition> = if (
        platform.toolCallsEnabled
            && platform.compatibleType in setOf(
                ClientType.OPENAI,
                ClientType.OPENROUTER,
                ClientType.OLLAMA,
                ClientType.CUSTOM
            )
    ) {
        ToolRegistry.defaultTools()
    } else {
        emptyList()
    }
}
