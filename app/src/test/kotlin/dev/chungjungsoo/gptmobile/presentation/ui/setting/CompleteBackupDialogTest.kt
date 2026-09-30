package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import dev.chungjungsoo.gptmobile.data.backup.BackupStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34], qualifiers = "w320dp-h480dp")
class CompleteBackupDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun restoreContentsUseOneScrollContainerWithoutCrashing() {
        compose.setContent {
            MaterialTheme {
                AlertDialog(
                    onDismissRequest = {},
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            BackupSelectionContent(SettingViewModelV2.BackupUiState()) { _, _ -> }
                        }
                    },
                    confirmButton = { Text("Restore") }
                )
            }
        }
        compose.onNodeWithText("Agent & tool history").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun passwordFieldOnlyExistsWhenEncryptionIsEnabled() {
        compose.setContent {
            MaterialTheme {
                var state by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(SettingViewModelV2.BackupUiState()) }
                CompleteBackupDialog(
                    state = state,
                    backupStatus = BackupStatus(),
                    onBackup = {},
                    onRestore = {},
                    onPasswordProtectionChange = { state = state.copy(passwordProtectionEnabled = it) },
                    onDismiss = {}
                )
            }
        }
        compose.onNodeWithTag("backup_password").assertDoesNotExist()
        compose.onNodeWithTag("backup_encrypt").performScrollTo().performClick()
        compose.onNodeWithTag("backup_password").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("backup_encrypt").performScrollTo().performClick()
        compose.onNodeWithTag("backup_password").assertDoesNotExist()
    }

    @Test
    fun largeTextKeepsBackupRestoreAndContentsAccessible() {
        var backups = 0
        var restores = 0
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)
                ) {
                    CompleteBackupDialog(
                        state = SettingViewModelV2.BackupUiState(),
                        backupStatus = BackupStatus(),
                        onBackup = { backups++ },
                        onRestore = { restores++ },
                        onDismiss = {}
                    )
                }
            }
        }

        compose.onNodeWithTag("backup_all").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("restore_all").performScrollTo().assertIsDisplayed()

        assertEquals(0, backups)
        assertEquals(0, restores)
    }

    @Test
    @Config(qualifiers = "w640dp-h320dp-land")
    fun landscapeBusyStateDisablesBackupRestoreAndContents() {
        compose.setContent {
            MaterialTheme {
                CompleteBackupDialog(
                    state = SettingViewModelV2.BackupUiState(isBusy = true, isWorking = true),
                    backupStatus = BackupStatus(),
                    onBackup = {},
                    onRestore = {},
                    onDismiss = {}
                )
            }
        }
        compose.onNodeWithTag("backup_all").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("restore_all").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
    }
}
