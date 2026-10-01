package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.data.agent.tool.DelegationRecoveryRequest

@Composable
fun DelegationRecoveryDialog(
    request: DelegationRecoveryRequest,
    onSwitch: (String) -> Unit,
    onCancelDelegation: () -> Unit
) {
    var selectedUid by remember(request.id) {
        mutableStateOf(request.options.firstOrNull()?.profileUid)
    }
    var expanded by remember(request.id) { mutableStateOf(false) }
    val selected = request.options.firstOrNull { it.profileUid == selectedUid }
        ?: request.options.firstOrNull()

    AlertDialog(
        onDismissRequest = onCancelDelegation,
        title = { Text("Delegation failed") },
        text = {
            Column {
                Text(
                    "${request.failedProfileName} could not continue. " +
                        "Choose another delegate, or cancel delegation and let the primary AI finish this response."
                )
                if (request.reason.isNotBlank()) {
                    Text(
                        request.reason,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            selected?.let { option ->
                                buildString {
                                    append(option.profileName)
                                    if (option.model.isNotBlank()) append(" · ${option.model}")
                                    append(" · Delegation score ")
                                    append(option.delegationScore?.toString() ?: "Not benchmarked")
                                }
                            } ?: "No alternate delegation models",
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose delegation model")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        request.options.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(option.profileName)
                                        Text(
                                            buildString {
                                                append(option.provider.lowercase().replaceFirstChar { it.uppercase() } + " · ")
                                                if (option.model.isNotBlank()) append(option.model + " · ")
                                                append("Delegation score ")
                                                append(option.delegationScore?.toString() ?: "Not benchmarked")
                                            }
                                        )
                                    }
                                },
                                onClick = {
                                    selectedUid = option.profileUid
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected != null,
                onClick = { selected?.let { onSwitch(it.profileUid) } }
            ) {
                Text("Switch delegation")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelDelegation) {
                Text("Cancel delegation")
            }
        }
    )
}
