package dev.chungjungsoo.gptmobile.presentation.ui.localmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.data.catalog.ModelCatalogParser

@Composable
fun LocalModelReplacementHost(
    viewModel: LocalModelReplacementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val confirmation = uiState.confirmation
    if (confirmation != null) {
        LocalModelReplacementConfirmDialog(
            modelName = confirmation.entry.displayName.ifBlank { confirmation.entry.id },
            accelerator = confirmation.accelerator.uppercase(),
            downloadSize = ModelCatalogParser.formatDownloadSize(confirmation.target.sizeInBytes),
            onConfirm = viewModel::confirmReplacement,
            onDismiss = viewModel::dismissConfirmation
        )
    }
    LocalModelDownloadDialogHost(
        dialog = uiState.download.dialog,
        onConfirmRamWarning = viewModel::confirmRamWarning,
        onConfirmMeteredDownload = viewModel::confirmMeteredDownload,
        onDismissDialog = viewModel::dismissConfirmation,
        onStartSignIn = viewModel::startHuggingFaceSignIn,
        onAuthActivityResult = viewModel::onAuthActivityResult,
        onLicenseTabClosed = viewModel::onLicenseTabClosed,
        onRetryAfterLicense = viewModel::retryAfterLicense,
        onEnterAccessToken = viewModel::openAccessTokenDialog,
        onSaveAccessToken = viewModel::saveAccessToken
    )
}
