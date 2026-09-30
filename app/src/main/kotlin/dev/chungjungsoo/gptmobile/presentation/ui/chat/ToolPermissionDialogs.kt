package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.permissions.ToolApproval

@Composable
internal fun ToolApprovalDialog(
    approval: ToolApproval,
    onDeny: () -> Unit,
    onAllowOnce: () -> Unit,
    onAlwaysAllowTool: () -> Unit,
    onAlwaysAllowProvider: () -> Unit
) {
    var allowProvider by remember(approval.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDeny,
        icon = {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    painter = painterResource(R.drawable.mcp_brand_protocol),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp).size(28.dp)
                )
            }
        },
        title = { Text("Allow this tool?", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${approval.connection} wants to run ${approval.tool}.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Security, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Requested action", style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            approval.argumentPreview.ifBlank { "No additional arguments." },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = allowProvider, onCheckedChange = { allowProvider = it })
                    Column(Modifier.weight(1f)) {
                        Text("Allow all from this provider", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Future actions from ${approval.connection} can run without another prompt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(
                    onClick = onAlwaysAllowTool,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Always allow this tool")
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = if (allowProvider) onAlwaysAllowProvider else onAllowOnce
            ) {
                Text(if (allowProvider) "Allow provider" else "Allow once")
            }
        },
        dismissButton = { OutlinedButton(onClick = onDeny) { Text("Deny") } }
    )
}

@Composable
internal fun FreeToolConsentDialog(
    request: ChatViewModel.FreeToolConsentRequest,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    var progress by remember(request.profileUid, request.toolId) { mutableFloatStateOf(0f) }
    val unlocked = progress >= 0.95f
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = if (unlocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    imageVector = if (unlocked) Icons.Outlined.CheckCircle else Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.padding(14.dp).size(30.dp),
                    tint = if (unlocked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        },
        title = { Text(if (unlocked) "Tool unlocked" else "Unlock external tool data") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${request.profileName} is a free AI profile. Using ${request.toolName} can send the tool request and returned data to that free model provider.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Data tracking is on at the free provider: requests and tool results may be logged under its data policy. You can disable this tool in Options at any time.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Slide to acknowledge data sharing", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = progress,
                            onValueChange = { progress = it },
                            valueRange = 0f..1f
                        )
                        Text(
                            if (unlocked) "Acknowledged. This profile + tool permission will be remembered." else "Move the slider fully to the right to continue.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onAccept, enabled = unlocked) {
                Text("Accept & unlock")
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Not now") } }
    )
}
