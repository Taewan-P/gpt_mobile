package dev.chungjungsoo.gptmobile.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.model.FreeAiProvider
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeProviderPicker(
    apiUrl: String,
    onProviderSelected: (FreeAiProvider) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val selected = FreeAiProvider.fromApiUrl(apiUrl)
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var testStatus by remember(apiUrl) { mutableStateOf<dev.chungjungsoo.gptmobile.data.network.ApiKeyValidator.ValidationResult?>(null) }
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Outlined.AutoAwesome, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column {
                    Text(stringResource(R.string.free_ai), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.free_ai_no_account), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled && !testing) expanded = it }) {
                OutlinedTextField(
                    value = selected?.displayName.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    enabled = enabled,
                    label = { Text(stringResource(R.string.free_ai_provider)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    singleLine = true
                )
                ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                    FreeAiProvider.entries.forEach { provider ->
                        DropdownMenuItem(
                            enabled = provider.isAvailable && !testing,
                            text = {
                                Column {
                                    Text(provider.displayName)
                                    Text(
                                        stringResource(if (provider.isAvailable) provider.descriptionRes() else R.string.free_ai_awaiting_approval),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onProviderSelected(provider)
                                expanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }
            selected?.let { provider ->
                Text(
                    stringResource(if (provider.isAvailable) provider.descriptionRes() else R.string.free_ai_approval_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (provider.isAvailable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                )
                Text(stringResource(R.string.free_ai_model, provider.model), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            androidx.compose.material3.TextButton(enabled = enabled && !testing && selected?.isAvailable == true, onClick = {
                val testedUrl = apiUrl
                testing = true
                testStatus = null
                scope.launch {
                    try {
                        testStatus = dev.chungjungsoo.gptmobile.data.network.ApiKeyValidator.validate(dev.chungjungsoo.gptmobile.data.model.ClientType.FREE, testedUrl, "")
                    } finally {
                        testing = false
                    }
                }
            }) { Text(if (testing) "Testing provider…" else "Test provider") }
            when (val result = testStatus) {
                is dev.chungjungsoo.gptmobile.data.network.ApiKeyValidator.ValidationResult.Success -> Text(result.message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                is dev.chungjungsoo.gptmobile.data.network.ApiKeyValidator.ValidationResult.Error -> Text(result.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                else -> Unit
            }
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.free_ai_memory_off), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(stringResource(R.string.free_ai_memory_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            Text(stringResource(R.string.free_ai_public_prompts), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun FreeAiProvider.descriptionRes(): Int = when (this) {
    FreeAiProvider.KILO -> R.string.free_ai_kilo_description
    FreeAiProvider.POLLINATIONS -> R.string.free_ai_pollinations_description
    FreeAiProvider.OVHCLOUD -> R.string.free_ai_ovh_description
    FreeAiProvider.LLM7 -> R.string.free_ai_llm7_description
}
