package dev.chungjungsoo.gptmobile.presentation.theme

import android.app.UiModeManager
import dev.chungjungsoo.gptmobile.data.model.ThemeMode

internal fun ThemeMode.toApplicationNightMode(): Int = when (this) {
    ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
    ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
    ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
}
