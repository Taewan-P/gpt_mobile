package dev.chungjungsoo.gptmobile.presentation.ui.setting

import dev.chungjungsoo.gptmobile.data.backup.CompleteBackupSection
import dev.chungjungsoo.gptmobile.data.dto.CustomThemePalette
import dev.chungjungsoo.gptmobile.data.dto.ThemeBackupDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeAndBackupDefaultsTest {
    @Test fun backupAndRestoreOptionsStartWithLargeOptionalSectionsDisabled() {
        val state = SettingViewModelV2.BackupUiState()
        listOf(CompleteBackupSection.TOOLS, CompleteBackupSection.AGENT_HISTORY, CompleteBackupSection.ATTACHMENTS, CompleteBackupSection.LOCAL_MODELS).forEach {
            assertFalse(state.selection.includes(it))
        }
        assertFalse(state.passwordProtectionEnabled)
    }

    @Test fun customPaletteRoundTripsAndOldBackupsKeepDefaultPalette() {
        val theme = ThemeBackupDto(savedProfiles = dev.chungjungsoo.gptmobile.data.dto.ThemePresets.profiles, customPrimaryArgb = 0xFF123456, customPalette = CustomThemePalette(0xFF123456, 0xFFABCDEF, 0xFF101010, 0xFF202020))
        assertEquals(theme, Json.decodeFromString<ThemeBackupDto>(Json.encodeToString(theme)))
        assertNull(Json.decodeFromString<ThemeBackupDto>("""{"themeMode":0,"dynamicTheme":false}""").customPalette)
    }
}
