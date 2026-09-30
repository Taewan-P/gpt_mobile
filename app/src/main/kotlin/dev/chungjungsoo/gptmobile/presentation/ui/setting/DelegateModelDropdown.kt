package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.isPrivateDestination

@Composable
fun DelegateModelDropdown(
    profiles: List<PlatformV2>,
    selectedProfileUid: String,
    enabled: Boolean,
    onSelected: (PlatformV2?) -> Unit,
    automaticLabel: String = "Automatic"
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = profiles.firstOrNull { it.uid == selectedProfileUid }
    Column {
        Text("Delegate model", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("delegate_model_dropdown")
            ) {
                Column(Modifier.weight(1f)) {
                    Text(selected?.name ?: if (selectedProfileUid.isBlank()) automaticLabel else "Selected model unavailable")
                    selected?.let { Text(it.model, style = MaterialTheme.typography.bodySmall) }
                }
                Icon(Icons.Default.ArrowDropDown, "Choose delegate model")
            }
            DropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 360.dp)
            ) {
                DropdownMenuItem(
                    text = { Text(automaticLabel) },
                    onClick = {
                        expanded = false
                        onSelected(null)
                    }
                )
                profiles.forEach { profile ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(profile.name)
                                Text(profile.model, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        leadingIcon = { Icon(if (profile.isPrivateDestination()) Icons.Default.Memory else Icons.Default.Cloud, if (profile.isPrivateDestination()) "Local or private model" else "Online model") },
                        onClick = {
                            expanded = false
                            onSelected(profile)
                        }
                    )
                }
            }
        }
        if (profiles.isEmpty()) {
            Text("Add an enabled AI profile in Settings to choose a helper.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (selected != null && !selected.isPrivateDestination()) {
            Text("This online model receives the tasks and tool results you delegate to it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
