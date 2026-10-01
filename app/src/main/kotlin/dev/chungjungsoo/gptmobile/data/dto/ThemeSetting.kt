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
        SavedThemeProfile("Midnight", CustomThemePalette(0xFF91B5FF, 0xFFC2C8FF, 0xFF0C1020, 0xFF181E34)),
        SavedThemeProfile("Rose", CustomThemePalette(0xFFFFB0CD, 0xFFD4B7FA, 0xFF21121C, 0xFF332030)),
        SavedThemeProfile("Ocean", CustomThemePalette(0xFF68CFFF, 0xFF80E5D4, 0xFF071C2A, 0xFF102D3E)),
        SavedThemeProfile("Amber", CustomThemePalette(0xFFFFCD70, 0xFFE3B98B, 0xFF201A10, 0xFF332B1B)),
        SavedThemeProfile("Graphite", CustomThemePalette(0xFFC8D0DA, 0xFFA3C2D0, 0xFF151719, 0xFF25282C)),
        SavedThemeProfile("Neon", CustomThemePalette(0xFFA7F36B, 0xFF72E3E0, 0xFF10170E, 0xFF1E2A18)),
        SavedThemeProfile("Lavender", CustomThemePalette(0xFF7651A6, 0xFF8F557A, 0xFFFAF5FF, 0xFFF0E7FA), ThemeMode.LIGHT),
        SavedThemeProfile("Sand", CustomThemePalette(0xFF82612D, 0xFF74634A, 0xFFFFF9EE, 0xFFF2E8D5), ThemeMode.LIGHT),
        SavedThemeProfile("Mint", CustomThemePalette(0xFF216B55, 0xFF43685E, 0xFFF1FCF6, 0xFFE0F1E8), ThemeMode.LIGHT),
        SavedThemeProfile("Sky", CustomThemePalette(0xFF245C9E, 0xFF53688F, 0xFFF4F9FF, 0xFFE5EEFA), ThemeMode.LIGHT),
        SavedThemeProfile("Daylight", CustomThemePalette(0xFF006A7A, 0xFF65558F, 0xFFF5FAFC, 0xFFEAF1F5), ThemeMode.LIGHT)
    )
}
