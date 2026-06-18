package dev.chungjungsoo.gptmobile.data.model

object GeminiSafetySettings {
    const val BLOCK_NONE = "BLOCK_NONE"
    const val BLOCK_ONLY_HIGH = "BLOCK_ONLY_HIGH"
    const val BLOCK_MEDIUM_AND_ABOVE = "BLOCK_MEDIUM_AND_ABOVE"
    const val BLOCK_LOW_AND_ABOVE = "BLOCK_LOW_AND_ABOVE"

    const val HARM_CATEGORY_HARASSMENT = "HARM_CATEGORY_HARASSMENT"
    const val HARM_CATEGORY_HATE_SPEECH = "HARM_CATEGORY_HATE_SPEECH"
    const val HARM_CATEGORY_SEXUALLY_EXPLICIT = "HARM_CATEGORY_SEXUALLY_EXPLICIT"
    const val HARM_CATEGORY_DANGEROUS_CONTENT = "HARM_CATEGORY_DANGEROUS_CONTENT"

    val supportedThresholds = listOf(BLOCK_NONE, BLOCK_ONLY_HIGH, BLOCK_MEDIUM_AND_ABOVE, BLOCK_LOW_AND_ABOVE)

    fun normalizeThreshold(value: String?): String = value?.takeIf { it in supportedThresholds } ?: BLOCK_NONE

    fun labelFor(threshold: String): String = when (normalizeThreshold(threshold)) {
        BLOCK_ONLY_HIGH -> "Block high only"
        BLOCK_MEDIUM_AND_ABOVE -> "Block medium and above"
        BLOCK_LOW_AND_ABOVE -> "Block low and above"
        else -> "Block none"
    }
}
