package dev.chungjungsoo.gptmobile.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.ThemeMode

@Immutable
data class ExtendedColorScheme(
    val customColor1: ColorFamily,
    val chatGPTOfficialColor: ColorFamily,
    val customColor2: ColorFamily
)

private val lightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight
)

// Deliberately cyan-black rather than neutral/absolute black. Keeping every surface
// in the same hue family also prevents cards and system bars from looking green.
private val darkScheme = darkColorScheme(
    primary = Color(0xFF55DFF2),
    onPrimary = Color(0xFF002A31),
    primaryContainer = Color(0xFF004E5B),
    onPrimaryContainer = Color(0xFFB8F4FF),
    secondary = Color(0xFF9EDCE5),
    onSecondary = Color(0xFF082F35),
    secondaryContainer = Color(0xFF123F47),
    onSecondaryContainer = Color(0xFFC5F0F6),
    tertiary = Color(0xFF8CDCE8),
    onTertiary = Color(0xFF00343C),
    tertiaryContainer = Color(0xFF07505B),
    onTertiaryContainer = Color(0xFFC2F4FC),
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = Color(0xFF001A1F),
    onBackground = Color(0xFFE0F4F7),
    surface = Color(0xFF001A1F),
    onSurface = Color(0xFFE0F4F7),
    surfaceVariant = Color(0xFF12383F),
    onSurfaceVariant = Color(0xFFB8DDE2),
    outline = Color(0xFF71949A),
    outlineVariant = Color(0xFF294D53),
    scrim = Color(0xFF001014),
    inverseSurface = Color(0xFFD7F2F5),
    inverseOnSurface = Color(0xFF123338),
    inversePrimary = Color(0xFF006878),
    surfaceDim = Color(0xFF001A1F),
    surfaceBright = Color(0xFF173C43),
    surfaceContainerLowest = Color(0xFF001419),
    surfaceContainerLow = Color(0xFF062329),
    surfaceContainer = Color(0xFF0A292F),
    surfaceContainerHigh = Color(0xFF103239),
    surfaceContainerHighest = Color(0xFF173C43)
)

val extendedLight = ExtendedColorScheme(
    ColorFamily(customColor1Light, onCustomColor1Light, customColor1ContainerLight, onCustomColor1ContainerLight),
    ColorFamily(chatGPTOfficialColorLight, onChatGPTOfficialColorLight, chatGPTOfficialColorContainerLight, onChatGPTOfficialColorContainerLight),
    ColorFamily(customColor2Light, onCustomColor2Light, customColor2ContainerLight, onCustomColor2ContainerLight)
)

val extendedDark = ExtendedColorScheme(
    ColorFamily(customColor1Dark, onCustomColor1Dark, customColor1ContainerDark, onCustomColor1ContainerDark),
    ColorFamily(chatGPTOfficialColorDark, onChatGPTOfficialColorDark, chatGPTOfficialColorContainerDark, onChatGPTOfficialColorContainerDark),
    ColorFamily(customColor2Dark, onCustomColor2Dark, customColor2ContainerDark, onCustomColor2ContainerDark)
)

val extendedLightMediumContrast = ExtendedColorScheme(
    ColorFamily(customColor1LightMediumContrast, onCustomColor1LightMediumContrast, customColor1ContainerLightMediumContrast, onCustomColor1ContainerLightMediumContrast),
    ColorFamily(chatGPTOfficialColorLightMediumContrast, onChatGPTOfficialColorLightMediumContrast, chatGPTOfficialColorContainerLightMediumContrast, onChatGPTOfficialColorContainerLightMediumContrast),
    ColorFamily(customColor2LightMediumContrast, onCustomColor2LightMediumContrast, customColor2ContainerLightMediumContrast, onCustomColor2ContainerLightMediumContrast)
)

val extendedLightHighContrast = ExtendedColorScheme(
    ColorFamily(customColor1LightHighContrast, onCustomColor1LightHighContrast, customColor1ContainerLightHighContrast, onCustomColor1ContainerLightHighContrast),
    ColorFamily(chatGPTOfficialColorLightHighContrast, onChatGPTOfficialColorLightHighContrast, chatGPTOfficialColorContainerLightHighContrast, onChatGPTOfficialColorContainerLightHighContrast),
    ColorFamily(customColor2LightHighContrast, onCustomColor2LightHighContrast, customColor2ContainerLightHighContrast, onCustomColor2ContainerLightHighContrast)
)

val extendedDarkMediumContrast = ExtendedColorScheme(
    ColorFamily(customColor1DarkMediumContrast, onCustomColor1DarkMediumContrast, customColor1ContainerDarkMediumContrast, onCustomColor1ContainerDarkMediumContrast),
    ColorFamily(chatGPTOfficialColorDarkMediumContrast, onChatGPTOfficialColorDarkMediumContrast, chatGPTOfficialColorContainerDarkMediumContrast, onChatGPTOfficialColorContainerDarkMediumContrast),
    ColorFamily(customColor2DarkMediumContrast, onCustomColor2DarkMediumContrast, customColor2ContainerDarkMediumContrast, onCustomColor2ContainerDarkMediumContrast)
)

val extendedDarkHighContrast = ExtendedColorScheme(
    ColorFamily(customColor1DarkHighContrast, onCustomColor1DarkHighContrast, customColor1ContainerDarkHighContrast, onCustomColor1ContainerDarkHighContrast),
    ColorFamily(chatGPTOfficialColorDarkHighContrast, onChatGPTOfficialColorDarkHighContrast, chatGPTOfficialColorContainerDarkHighContrast, onChatGPTOfficialColorContainerDarkHighContrast),
    ColorFamily(customColor2DarkHighContrast, onCustomColor2DarkHighContrast, customColor2ContainerDarkHighContrast, onCustomColor2ContainerDarkHighContrast)
)

@Immutable
data class ColorFamily(
    val color: Color,
    val onColor: Color,
    val colorContainer: Color,
    val onColorContainer: Color
)

val unspecified_scheme = ColorFamily(Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified)

@Composable
fun GPTMobileTheme(
    dynamicTheme: DynamicTheme = DynamicTheme.OFF,
    themeMode: ThemeMode = ThemeMode.LIGHT,
    customPrimaryArgb: Long? = null,
    customPalette: dev.chungjungsoo.gptmobile.data.dto.CustomThemePalette? = null,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val customPrimary = customPrimaryArgb?.let(::Color)
    val baseScheme = when {
        dynamicTheme == DynamicTheme.ON -> if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        useDarkTheme -> darkScheme
        else -> lightScheme
    }
    fun foreground(color: Color): Color = if (androidx.core.graphics.ColorUtils.calculateLuminance(color.toArgb()) > 0.179) Color.Black else Color.White
    val colorScheme = when {
        dynamicTheme == DynamicTheme.ON -> baseScheme
        customPalette != null -> {
            val primary = Color(customPalette.primary)
            val secondary = Color(customPalette.secondary)
            val background = Color(customPalette.background)
            val surface = Color(customPalette.surface)
            val surfaceText = foreground(surface)
            val container = androidx.compose.ui.graphics.lerp(surface, primary, 0.18f)
            baseScheme.copy(
                primary = primary, onPrimary = foreground(primary),
                secondary = secondary, onSecondary = foreground(secondary),
                tertiary = secondary, onTertiary = foreground(secondary),
                primaryContainer = container, onPrimaryContainer = foreground(container),
                secondaryContainer = androidx.compose.ui.graphics.lerp(surface, secondary, 0.18f),
                onSecondaryContainer = foreground(androidx.compose.ui.graphics.lerp(surface, secondary, 0.18f)),
                tertiaryContainer = container, onTertiaryContainer = foreground(container),
                background = background, onBackground = foreground(background),
                surface = surface, onSurface = surfaceText, onSurfaceVariant = surfaceText.copy(alpha = 0.8f),
                surfaceVariant = androidx.compose.ui.graphics.lerp(surface, surfaceText, 0.06f),
                surfaceContainer = surface, surfaceContainerLow = surface, surfaceContainerLowest = background,
                surfaceContainerHigh = androidx.compose.ui.graphics.lerp(surface, surfaceText, 0.06f),
                surfaceContainerHighest = androidx.compose.ui.graphics.lerp(surface, surfaceText, 0.1f),
                surfaceDim = background, surfaceBright = surface,
                outline = surfaceText.copy(alpha = 0.6f), outlineVariant = surfaceText.copy(alpha = 0.3f)
            )
        }
        customPrimary != null -> baseScheme.copy(primary = customPrimary, onPrimary = foreground(customPrimary), secondary = customPrimary, tertiary = customPrimary)
        else -> baseScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = foreground(colorScheme.background) == Color.Black
        }
    }
    MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
}
