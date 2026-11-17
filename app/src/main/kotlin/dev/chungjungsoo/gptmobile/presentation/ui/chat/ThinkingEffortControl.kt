package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.model.ThinkingEffort
import dev.chungjungsoo.gptmobile.data.model.ThinkingEffortControlState
import dev.chungjungsoo.gptmobile.data.model.ThinkingEffortControlStatus
import dev.chungjungsoo.gptmobile.presentation.ui.setting.thinkingEffortLabel

@Composable
fun ThinkingEffortControl(
    state: ThinkingEffortControlState,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier.padding(start = 8.dp, top = 4.dp)) {
        TextButton(
            onClick = { expanded = true },
            enabled = state.enabled
        ) {
            Text(thinkingEffortControlLabel(state))
        }
        if (state.adjustedToNearest && state.storedEffort != null && state.selectedEffort != null) {
            Text(
                text = stringResource(
                    R.string.thinking_effort_using_nearest,
                    thinkingEffortLabel(state.storedEffort),
                    thinkingEffortLabel(state.selectedEffort)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        DropdownMenu(
            expanded = expanded && state.enabled,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = MenuDefaults.TonalElevation,
            shadowElevation = MenuDefaults.ShadowElevation
        ) {
            ThinkingEffortChoices(
                offered = state.offered,
                selectedEffort = state.selectedEffort,
                profileDefaultSelected = state.followsProfileDefault
            ) { value ->
                expanded = false
                onSelect(value)
            }
        }
    }
}

@Composable
internal fun ThinkingEffortChoices(
    offered: List<ThinkingEffort>,
    selectedEffort: ThinkingEffort?,
    profileDefaultSelected: Boolean,
    onSelect: (String?) -> Unit
) {
    offered.forEach { effort ->
        EffortMenuItem(
            title = thinkingEffortLabel(effort),
            description = thinkingEffortMenuDescription(effort),
            selected = !profileDefaultSelected && selectedEffort == effort,
            onClick = { onSelect(effort.storageValue) }
        )
    }
    if (offered.isNotEmpty()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    val profileDescription = if (profileDefaultSelected && selectedEffort != null) {
        stringResource(
            R.string.thinking_effort_profile_default_with_level,
            thinkingEffortLabel(selectedEffort)
        )
    } else {
        stringResource(R.string.thinking_effort_profile_default_description)
    }
    EffortMenuItem(
        title = stringResource(R.string.thinking_effort_profile_default),
        description = profileDescription,
        selected = profileDefaultSelected,
        onClick = { onSelect(null) }
    )
}

@Composable
private fun EffortMenuItem(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    DropdownMenuItem(
        modifier = if (selected) {
            Modifier.background(colors.secondaryContainer)
        } else {
            Modifier
        },
        text = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) colors.onSecondaryContainer else colors.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
                )
            }
        },
        leadingIcon = {
            EffortSelectionMark(selected = selected)
        },
        colors = MenuDefaults.itemColors(
            textColor = colors.onSurface,
            leadingIconColor = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
        ),
        contentPadding = MenuDefaults.DropdownMenuItemContentPadding,
        onClick = onClick
    )
}

@Composable
private fun EffortSelectionMark(selected: Boolean) {
    if (selected) {
        Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = stringResource(R.string.thinking_effort_selected)
        )
    } else {
        Box(Modifier.size(24.dp))
    }
}

@Composable
private fun thinkingEffortControlLabel(state: ThinkingEffortControlState): String = when (state.status) {
    ThinkingEffortControlStatus.UNSUPPORTED -> if (state.rejectedByModel) {
        stringResource(R.string.thinking_effort_rejected)
    } else {
        stringResource(R.string.thinking_effort_unavailable)
    }
    ThinkingEffortControlStatus.REQUIRES_EXTENDED_THINKING ->
        stringResource(R.string.thinking_effort_requires_extended_thinking)

    ThinkingEffortControlStatus.ADJUSTABLE -> {
        val effort = state.selectedEffort
        if (effort == null) {
            stringResource(R.string.thinking_effort_chat_value, stringResource(R.string.thinking_effort_mixed))
        } else {
            stringResource(R.string.thinking_effort_chat_value, thinkingEffortLabel(effort))
        }
    }
}

@Composable
private fun thinkingEffortMenuDescription(effort: ThinkingEffort): String = when (effort) {
    ThinkingEffort.MINIMAL -> stringResource(R.string.thinking_effort_minimal_description)
    ThinkingEffort.LOW -> stringResource(R.string.thinking_effort_low_description)
    ThinkingEffort.MEDIUM -> stringResource(R.string.thinking_effort_medium_description)
    ThinkingEffort.HIGH -> stringResource(R.string.thinking_effort_high_description)
    ThinkingEffort.MAX -> stringResource(R.string.thinking_effort_max_description)
}
