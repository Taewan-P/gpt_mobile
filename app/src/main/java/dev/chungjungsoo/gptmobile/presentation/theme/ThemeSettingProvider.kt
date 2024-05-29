package dev.chungjungsoo.gptmobile.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.hilt.navigation.compose.hiltViewModel
import dev.chungjungsoo.gptmobile.data.model.DynamicTheme
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import dev.chungjungsoo.gptmobile.util.collectManagedState

val LocalDynamicTheme = compositionLocalOf { DynamicTheme.OFF }
val LocalThemeMode = compositionLocalOf { ThemeMode.SYSTEM }
val LocalThemeViewModel = compositionLocalOf<ThemeViewModel> {
    error("CompositionLocal LocalThemeViewModel is not present")
}

@Composable
fun ThemeSettingProvider(
    themeViewModel: ThemeViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    themeViewModel.themeSetting.collectManagedState().value.run {
        CompositionLocalProvider(
            LocalThemeViewModel provides themeViewModel,
            LocalDynamicTheme provides dynamicTheme,
            LocalThemeMode provides themeMode,
            content = content
        )
    }
}
