package dev.chungjungsoo.gptmobile.data.model

enum class ThemeMode(mode: Int) {
    SYSTEM(0),
    DARK(1),
    LIGHT(2);

    companion object {
        fun getByValue(value: Int) = entries.firstOrNull { it.ordinal == value }
    }
}
