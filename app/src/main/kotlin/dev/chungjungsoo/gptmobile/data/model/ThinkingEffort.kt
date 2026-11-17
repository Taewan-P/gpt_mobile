package dev.chungjungsoo.gptmobile.data.model

/**
 * App-wide thinking effort. Providers receive a mapped value, and levels a
 * model does not accept are clamped to the nearest supported one.
 */
enum class ThinkingEffort(val storageValue: String) {
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    MAX("max");

    companion object {
        val DEFAULT = MEDIUM

        fun fromStorage(value: String?): ThinkingEffort = fromStorageOrNull(value) ?: DEFAULT

        fun fromStorageOrNull(value: String?): ThinkingEffort? {
            val normalized = value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            return entries.firstOrNull { effort -> effort.storageValue == normalized }
        }
    }
}
