package dev.chungjungsoo.gptmobile.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R

@Composable
fun SettingItem(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    onItemClick: () -> Unit,
    showTrailingIcon: Boolean,
    showLeadingIcon: Boolean,
    leadingIcon: @Composable () -> Unit? = {}
) {
    val interactionModifier = if (enabled) {
        Modifier.clickable(
            role = Role.Button,
            onClick = onItemClick
        )
    } else {
        Modifier.semantics {
            disabled()
            role = Role.Button
        }
    }
    val colors = ListItemDefaults.colors()

    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { }
            .then(interactionModifier)
            .padding(horizontal = 8.dp),
        headlineContent = {
            Text(
                text = title,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        },
        supportingContent = {
            description?.let {
                Text(
                    text = it,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2
                )
            }
        },
        leadingContent = if (showLeadingIcon) {
            {
                Box(modifier = Modifier.clearAndSetSemantics { }) {
                    leadingIcon()
                }
            }
        } else {
            null
        },
        trailingContent = if (showTrailingIcon) {
            {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_round_arrow_right),
                    contentDescription = null
                )
            }
        } else {
            null
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = if (enabled) colors.headlineColor else colors.disabledHeadlineColor,
            supportingColor = if (enabled) colors.supportingTextColor else colors.disabledHeadlineColor,
            trailingIconColor = if (enabled) colors.trailingIconColor else colors.disabledTrailingIconColor
        )
    )
}
