package dev.chungjungsoo.gptmobile.data.model

/**
 * On-device local runtime execution backend.
 * QUALCOMM_QNN targets Hexagon NPU & Qualcomm AI Runtime (default for flagship Snapdragon).
 * LITERT_LM targets Google LiteRT-LM (TFLite runtime).
 */
enum class LocalRuntimeBackend(val displayName: String) {
    QUALCOMM_QNN("Qualcomm QNN · NPU"),
    LITERT_LM("LiteRT-LM");

    companion object {
        val DEFAULT = QUALCOMM_QNN

        fun fromString(value: String?): LocalRuntimeBackend =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DEFAULT
    }
}
