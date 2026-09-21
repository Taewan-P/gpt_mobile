package dev.chungjungsoo.gptmobile.presentation.theme

import android.app.UiModeManager
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeSyncTest {
    @Test
    fun themeModesMapToApplicationNightModes() {
        assertEquals(UiModeManager.MODE_NIGHT_NO, ThemeMode.LIGHT.toApplicationNightMode())
        assertEquals(UiModeManager.MODE_NIGHT_YES, ThemeMode.DARK.toApplicationNightMode())
        assertEquals(UiModeManager.MODE_NIGHT_AUTO, ThemeMode.SYSTEM.toApplicationNightMode())
    }
}
