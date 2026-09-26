package dev.chungjungsoo.gptmobile.data.dto

import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import kotlinx.serialization.Serializable

data class ThemeSetting(
    val dynamicTheme: DynamicTheme = DynamicTheme.OFF,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val customPrimaryArgb: Long? = null,
    val customPalette: CustomThemePalette? = null,
    val savedProfiles: List<SavedThemeProfile> = emptyList()
)

@Serializable
data class CustomThemePalette(
    val primary: Long,
    val secondary: Long,
    val background: Long,
    val surface: Long
)

@Serializable
data class SavedThemeProfile(val name: String, val palette: CustomThemePalette, val mode: ThemeMode = ThemeMode.DARK)

object ThemePresets {
    val profiles = listOf(
        SavedThemeProfile("Arctic", CustomThemePalette(0xFF55DFF2, 0xFF9EDCE5, 0xFF001A1F, 0xFF062329)),
        SavedThemeProfile("Orchid", CustomThemePalette(0xFFD0BCFF, 0xFFE8B9D5, 0xFF191323, 0xFF251D33)),
        SavedThemeProfile("Forest", CustomThemePalette(0xFF8CD9AD, 0xFFC2D69A, 0xFF101C16, 0xFF1B2B22)),
        SavedThemeProfile("Ember", CustomThemePalette(0xFFFFB59C, 0xFFFFD38C, 0xFF241711, 0xFF34231D)),
        SavedThemeProfile("Daylight", CustomThemePalette(0xFF006A7A, 0xFF65558F, 0xFFF5FAFC, 0xFFEAF1F5), ThemeMode.LIGHT)
    )
}
