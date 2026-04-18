package dev.chungjungsoo.gptmobile.util

private const val RESPONSE_STOPPED_PREFIX = "\n\n[Response stopped: "
private const val ASSISTANT_ERROR_PREFIX = "Error: "

internal fun buildAssistantErrorContent(existingContent: String, error: String): String = when {
    existingContent.isBlank() -> "$ASSISTANT_ERROR_PREFIX$error"
    else -> "$existingContent$RESPONSE_STOPPED_PREFIX$error]"
}

internal fun stripAssistantErrorNote(content: String): String {
    val markerIndex = content.lastIndexOf(RESPONSE_STOPPED_PREFIX)
    return if (markerIndex >= 0 && content.endsWith("]")) {
        content.substring(0, markerIndex)
    } else {
        content
    }
}

internal fun isAssistantErrorMessage(content: String): Boolean = content.trimStart().startsWith(ASSISTANT_ERROR_PREFIX)
