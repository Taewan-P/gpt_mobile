package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import dev.chungjungsoo.gptmobile.util.collectManagedState

@AndroidEntryPoint
class SettingActivity : ComponentActivity() {

    private val settingViewModel: SettingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val platformState by settingViewModel.platformState.collectManagedState()
            val isThemeDialogOpen by settingViewModel.isThemeDialogOpen.collectManagedState()

            GPTMobileTheme {
                SettingScreen(onThemeSettingClick = { settingViewModel.openThemeDialog() }) { finish() }

                if (isThemeDialogOpen) {
                    ThemeSettingDialog(
                        onDismiss = { settingViewModel.closeThemeDialog() }
                    )
                }
            }
        }
    }
}
