package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2

@Composable
internal fun ChatContextDialog(
    platformName: String,
    model: String,
    contextWindowTokens: Int?,
    detectedContextWindowTokens: Int?,
    resumableReplies: Boolean,
    supportsResumableReplies: Boolean,
    canCompact: Boolean,
    maxContextWindowTokens: Int? = null,
    onConfirm: (contextWindowTokens: Int?, resumableReplies: Boolean, compactNow: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var tokenInput by remember(platformName, model, contextWindowTokens) {
        mutableStateOf(contextWindowTokens?.toString().orEmpty())
    }
    var resume by remember(platformName, model, resumableReplies) { mutableStateOf(resumableReplies) }
    val parsedTokens = tokenInput.trim().toIntOrNull()?.takeIf { it > 0 }
    val isAutomatic = tokenInput.isBlank() && detectedContextWindowTokens != null
    val exceedsEngine = parsedTokens != null && maxContextWindowTokens != null && parsedTokens > maxContextWindowTokens
    val isValid = (parsedTokens != null || isAutomatic) && !exceedsEngine
    val hasInputError = (tokenInput.isNotBlank() && parsedTokens == null) || exceedsEngine

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_context_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("$platformName · $model", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.chat_context_window)) },
                    placeholder = { detectedContextWindowTokens?.let { Text(it.toString()) } },
                    supportingText = {
                        Text(
                            if (exceedsEngine) {
                                stringResource(R.string.chat_context_local_max, maxContextWindowTokens)
                            } else {
                                stringResource(
                                    when {
                                        hasInputError -> R.string.chat_context_window_invalid
                                        detectedContextWindowTokens == null -> R.string.chat_context_window_unknown
                                        else -> R.string.chat_context_window_detected
                                    }
                                )
                            }
                        )
                    },
                    isError = hasInputError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                if (supportsResumableReplies) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {}
                            .toggleable(value = resume, role = Role.Switch, onValueChange = { resume = it }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.chat_resumable_replies), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.chat_resumable_replies_description), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = resume, onCheckedChange = null)
                    }
                }
                if (canCompact) {
                    TextButton(
                        onClick = { onConfirm(parsedTokens, resume, true) },
                        enabled = isValid
                    ) {
                        Text(stringResource(R.string.chat_compact_now))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(parsedTokens, resume, false) }, enabled = isValid) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
internal fun ChatContextPlatformPicker(
    platforms: List<PlatformV2>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_context_choose_platform)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                platforms.forEach { platform ->
                    TextButton(onClick = { onSelect(platform.uid) }, modifier = Modifier.fillMaxWidth()) {
                        Text(platform.name)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
