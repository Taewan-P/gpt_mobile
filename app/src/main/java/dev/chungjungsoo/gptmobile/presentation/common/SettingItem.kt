package dev.chungjungsoo.gptmobile.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R

@Composable
fun SettingItem(
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    onItemClick: () -> Unit,
    showTrailingIcon: Boolean
) {
    val modifier = if (enabled) {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onItemClick)
            .padding(horizontal = 8.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    }
    val colors = ListItemDefaults.colors()

    ListItem(
        modifier = modifier,
        headlineContent = { Text(title) },
        supportingContent = {
            description?.let { Text(it) }
        },
        trailingContent = {
            if (showTrailingIcon) {
                Icon(
                    ImageVector.vectorResource(id = R.drawable.ic_round_arrow_right),
                    contentDescription = stringResource(R.string.arrow_icon)
                )
            }
        },
        colors = ListItemDefaults.colors(
            headlineColor = if (enabled) colors.headlineColor else colors.disabledHeadlineColor,
            supportingColor = if (enabled) colors.supportingTextColor else colors.disabledHeadlineColor,
            trailingIconColor = if (enabled) colors.trailingIconColor else colors.disabledTrailingIconColor
        )
    )
}
