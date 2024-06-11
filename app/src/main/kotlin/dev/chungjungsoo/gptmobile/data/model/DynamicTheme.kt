package dev.chungjungsoo.gptmobile.data.model

enum class DynamicTheme(mode: Int) {
    ON(1),
    OFF(0);

    companion object {
        fun getByValue(value: Int) = entries.firstOrNull { it.ordinal == value }
    }
}
