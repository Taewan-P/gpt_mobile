package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.model.ThinkingEffort
import dev.chungjungsoo.gptmobile.data.model.ThinkingEffortControlState
import dev.chungjungsoo.gptmobile.data.model.ThinkingEffortControlStatus
import dev.chungjungsoo.gptmobile.presentation.common.SettingItem
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import dev.chungjungsoo.gptmobile.presentation.ui.setting.ExtendedThinkingSwitch
import dev.chungjungsoo.gptmobile.presentation.ui.setting.ThinkingEffortPickerDialog
import org.junit.Rule
import org.junit.Test

class ThinkingEffortPaparazziTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(softButtons = false)
    )

    @Test
    fun settingsBaseEffort() {
        paparazzi.snapshot {
            GPTMobileTheme {
                SettingsEffortPreview(
                    enabled = true,
                    description = stringResource(R.string.thinking_effort_medium)
                )
            }
        }
    }

    @Test
    fun settingsEffortPicker() {
        paparazzi.snapshot {
            GPTMobileTheme {
                ThinkingEffortPickerDialog(
                    selectedEffort = ThinkingEffort.MEDIUM.storageValue,
                    onDismissRequest = {},
                    onConfirmRequest = {}
                )
            }
        }
    }

    @Test
    fun chatEffortCollapsed() {
        paparazzi.snapshot {
            GPTMobileTheme {
                ThinkingEffortControl(
                    state = adjustable(ThinkingEffort.MEDIUM),
                    onSelect = {}
                )
            }
        }
    }

    @Test
    fun chatEffortExpanded() {
        paparazzi.snapshot {
            GPTMobileTheme {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    ThinkingEffortControl(
                        state = adjustable(ThinkingEffort.HIGH),
                        onSelect = {}
                    )
                    ThinkingEffortChoices(onSelect = {})
                }
            }
        }
    }

    @Test
    fun unsupportedEffortDisabled() {
        paparazzi.snapshot {
            GPTMobileTheme {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsEffortPreview(
                        enabled = false,
                        description = stringResource(R.string.thinking_effort_unavailable_description)
                    )
                    ThinkingEffortControl(
                        state = ThinkingEffortControlState(
                            enabled = false,
                            selectedEffort = null,
                            followsProfileDefault = true,
                            status = ThinkingEffortControlStatus.UNSUPPORTED
                        ),
                        onSelect = {}
                    )
                }
            }
        }
    }

    private fun adjustable(effort: ThinkingEffort) = ThinkingEffortControlState(
        enabled = true,
        selectedEffort = effort,
        followsProfileDefault = true,
        status = ThinkingEffortControlStatus.ADJUSTABLE
    )
}

@Composable
private fun SettingsEffortPreview(enabled: Boolean, description: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ExtendedThinkingSwitch(
            modifier = Modifier.heightIn(min = 64.dp),
            enabled = true,
            isChecked = true,
            onCheckedChange = {}
        )
        SettingItem(
            modifier = Modifier.heightIn(min = 64.dp),
            title = stringResource(R.string.thinking_effort),
            description = description,
            enabled = enabled,
            onItemClick = {},
            showTrailingIcon = true,
            showLeadingIcon = true,
            leadingIcon = {
                Icon(imageVector = Icons.Outlined.Speed, contentDescription = null)
            }
        )
    }
}
