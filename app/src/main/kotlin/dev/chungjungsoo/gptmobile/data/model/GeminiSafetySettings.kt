package dev.chungjungsoo.gptmobile.data.model

import androidx.annotation.StringRes
import dev.chungjungsoo.gptmobile.R

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

    @StringRes
    fun labelResFor(threshold: String): Int = when (normalizeThreshold(threshold)) {
        BLOCK_ONLY_HIGH -> R.string.gemini_safety_block_only_high
        BLOCK_MEDIUM_AND_ABOVE -> R.string.gemini_safety_block_medium_and_above
        BLOCK_LOW_AND_ABOVE -> R.string.gemini_safety_block_low_and_above
        else -> R.string.gemini_safety_block_none
    }
}
