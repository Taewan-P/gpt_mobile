package dev.chungjungsoo.gptmobile.presentation.ui.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.presentation.common.RadioItem
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.LocalModelDownloadStatus
import dev.chungjungsoo.gptmobile.presentation.ui.localmodel.LocalModelRequirements
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LocalModelListItem

@Composable
fun LocalModelPicker(
    models: List<DownloadedLocalModelOption>,
    selectedCatalogEntryId: String,
    onModelSelected: (String) -> Unit,
    onNavigateToLocalModels: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.local_platform_select_model),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.local_platform_select_model_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (models.isEmpty()) {
            Text(
                text = stringResource(R.string.local_platform_no_downloaded_models),
                style = MaterialTheme.typography.bodyLarge
            )
            TextButton(onClick = onNavigateToLocalModels) {
                Text(text = stringResource(R.string.local_platform_go_to_local_models))
            }
        } else {
            models.forEach { option ->
                RadioItem(
                    value = option.catalogEntryId,
                    selected = option.catalogEntryId == selectedCatalogEntryId,
                    title = option.displayName,
                    description = option.catalogEntryId,
                    onSelected = onModelSelected,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun LocalModelCatalogPicker(
    items: List<LocalModelListItem>,
    selectedCatalogEntryId: String,
    checkingAccessEntryId: String?,
    onModelSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    showPendingActivationHint: Boolean = false,
    onNavigateToLocalModels: (() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.local_platform_select_model),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.local_platform_catalog_select_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (showPendingActivationHint) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.local_platform_pending_activation_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.local_models_empty),
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            items.forEach { item ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    RadioItem(
                        value = item.entry.id,
                        selected = item.entry.id == selectedCatalogEntryId,
                        title = item.entry.displayName,
                        description = null,
                        onSelected = onModelSelected
                    )
                    LocalModelRequirements(
                        item = item,
                        modifier = Modifier.padding(horizontal = 20.dp),
                        isCapabilitiesVisible = false
                    )
                    LocalModelDownloadStatus(
                        item = item,
                        isCheckingAccess = checkingAccessEntryId == item.entry.id,
                        onDownload = { onModelSelected(item.entry.id) },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
        }
        if (onNavigateToLocalModels != null) {
            TextButton(onClick = onNavigateToLocalModels) {
                Text(text = stringResource(R.string.local_platform_go_to_local_models))
            }
        }
    }
}
