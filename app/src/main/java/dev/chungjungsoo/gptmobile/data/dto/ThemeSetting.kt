package dev.chungjungsoo.gptmobile.data.dto

import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.ThemeMode

data class ThemeSetting(
    val dynamicTheme: DynamicTheme = DynamicTheme.OFF,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)
