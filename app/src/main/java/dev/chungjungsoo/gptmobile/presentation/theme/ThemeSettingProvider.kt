package dev.chungjungsoo.gptmobile.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import dev.chungjungsoo.gptmobile.util.ThemePreference
import dev.chungjungsoo.gptmobile.util.collectManagedState

val LocalDynamicTheme = compositionLocalOf { DynamicTheme.OFF }
val LocalThemeMode = compositionLocalOf { ThemeMode.SYSTEM }

@Composable
fun ThemeSettingProvider(content: @Composable () -> Unit) {
    ThemePreference.themeSetting.collectManagedState().value.run {
        CompositionLocalProvider(
            LocalDynamicTheme provides dynamicTheme,
            LocalThemeMode provides themeMode,
            content = content
        )
    }
}
