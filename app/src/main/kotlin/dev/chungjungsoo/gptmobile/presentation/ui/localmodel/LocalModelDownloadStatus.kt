package dev.chungjungsoo.gptmobile.presentation.ui.localmodel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.catalog.CatalogCapabilities
import dev.chungjungsoo.gptmobile.data.catalog.ModelCatalogParser
import dev.chungjungsoo.gptmobile.data.localmodel.DownloadFailureKind
import dev.chungjungsoo.gptmobile.data.localmodel.DownloadProgress
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LocalModelItemStatus
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LocalModelListItem
import dev.chungjungsoo.gptmobile.presentation.ui.setting.downloadFailureMessageRes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocalModelRequirements(
    item: LocalModelListItem,
    modifier: Modifier = Modifier,
    isCapabilitiesVisible: Boolean = true
) {
    val entry = item.entry
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(
                R.string.local_model_requirements,
                ModelCatalogParser.formatDownloadSize(
                    item.downloadSizeBytes.takeIf { it > 0L } ?: entry.sizeInBytes
                ),
                entry.minRamGb
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        val capabilityLabels = if (isCapabilitiesVisible) capabilityLabels(entry.capabilities) else emptyList()
        if (capabilityLabels.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                capabilityLabels.forEach { label ->
                    CapabilityBadge(text = label)
                }
            }
        }
    }
}

@Composable
fun LocalModelDownloadStatus(
    item: LocalModelListItem,
    isCheckingAccess: Boolean,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val entry = item.entry
    Column(modifier = modifier.fillMaxWidth()) {
        when {
            isCheckingAccess -> {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
                Text(
                    text = stringResource(R.string.local_model_checking_access),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item.status == LocalModelItemStatus.NOT_DOWNLOADED -> {
                if (entry.isGated) {
                    Text(
                        text = stringResource(R.string.local_model_gated_hint),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                TextButton(onClick = onDownload) {
                    Text(stringResource(R.string.download))
                }
            }

            item.status == LocalModelItemStatus.DOWNLOADING -> {
                val fraction = DownloadProgress.fraction(item.receivedBytes, item.diskBytes)
                val percent = DownloadProgress.percent(item.receivedBytes, item.diskBytes)
                if (fraction == null) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                }
                Text(
                    text = downloadProgressText(item, percent),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (onCancel != null) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.local_model_cancel_download))
                    }
                }
            }

            item.status == LocalModelItemStatus.READY -> {
                Text(
                    text = stringResource(
                        R.string.local_model_on_disk,
                        ModelCatalogParser.formatDownloadSize(item.diskBytes)
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.delete))
                    }
                }
            }

            item.status == LocalModelItemStatus.FAILED -> {
                Text(
                    text = if (item.failureKind != DownloadFailureKind.GENERIC) {
                        stringResource(downloadFailureMessageRes(item.failureKind))
                    } else {
                        item.errorMessage ?: stringResource(R.string.local_model_failed)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row {
                    TextButton(onClick = onDownload) {
                        Text(stringResource(R.string.retry))
                    }
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) {
                            Text(stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun downloadProgressText(item: LocalModelListItem, percent: Int): String {
    val rate = if (item.bytesPerSecond > 0L) {
        stringResource(R.string.local_model_download_rate, ModelCatalogParser.formatDownloadSize(item.bytesPerSecond))
    } else {
        null
    }
    val eta = formatEta(item.remainingMs)
    return when {
        rate != null && eta != null -> stringResource(R.string.local_model_download_progress, percent, rate, eta)
        rate != null -> stringResource(R.string.local_model_download_progress_rate, percent, rate)
        else -> stringResource(R.string.local_model_download_percent, percent)
    }
}

@Composable
private fun formatEta(remainingMs: Long): String? {
    if (remainingMs <= 0L) return null
    val totalSeconds = remainingMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> stringResource(R.string.local_model_eta_hours_minutes, hours, minutes)
        minutes > 0 -> stringResource(R.string.local_model_eta_minutes_seconds, minutes, seconds)
        else -> stringResource(R.string.local_model_eta_seconds, seconds)
    }
}

@Composable
private fun CapabilityBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun capabilityLabels(capabilities: CatalogCapabilities): List<String> = buildList {
    if (capabilities.vision) add(stringResource(R.string.capability_vision))
    if (capabilities.tools) add(stringResource(R.string.capability_tools))
    if (capabilities.thinking) add(stringResource(R.string.capability_thinking))
}
