package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.agent.ToolDefinitionsRejectedException

data class ProviderRequestConfig(
    val apiUrl: String,
    val token: String?,
    val anthropicBetaFeatures: Set<String> = emptySet()
)

internal fun throwIfToolDefinitionsRejected(
    statusCode: Int,
    hasTools: Boolean,
    errorBody: String
) {
    val normalizedError = errorBody.lowercase()
    val explicitlyUnsupported = listOf(
        "does not support tools",
        "doesn't support tools",
        "tools are not supported",
        "tools not supported",
        "tool use is not supported",
        "tool calling is not supported",
        "unsupported parameter: 'tools'",
        "unsupported parameter: \"tools\"",
        "'tools' is not supported",
        "\"tools\" is not supported",
        "unknown field: tools",
        "unrecognized field \"tools\""
    ).any(normalizedError::contains)
    val noOpenRouterToolEndpoints = statusCode == 404 &&
        normalizedError.contains("no endpoints found that support tool use")
    if (hasTools && ((statusCode in setOf(400, 404, 422) && explicitlyUnsupported) || noOpenRouterToolEndpoints)) {
        throw ToolDefinitionsRejectedException("HTTP $statusCode rejected tool definitions")
    }
}
