package dev.chungjungsoo.gptmobile.presentation.ui.localmodel

import android.content.Intent
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceTokenStore
import dev.chungjungsoo.gptmobile.data.localmodel.GatedDownloadCoordinator
import dev.chungjungsoo.gptmobile.data.localmodel.GatedDownloadStep
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStatus
import dev.chungjungsoo.gptmobile.data.localmodel.SocVariantResolver
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LocalModelDownloadUiState
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LocalModelItemStatus
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LocalModelsDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LocalModelDownloadActions(
    private val localModelRepository: LocalModelRepository,
    private val gatedDownloadCoordinator: GatedDownloadCoordinator,
    private val huggingFaceTokenStore: HuggingFaceTokenStore,
    private val downloadGuards: LocalDownloadGuards,
    private val huggingFaceAuthClient: HuggingFaceAuthClient,
    private val scope: CoroutineScope,
    private val deviceSocModel: String = ""
) {
    private var pendingGatedEntry: CatalogEntry? = null

    private val _uiState = MutableStateFlow(LocalModelDownloadUiState())
    val uiState: StateFlow<LocalModelDownloadUiState> = _uiState.asStateFlow()

    fun requestDownload(entry: CatalogEntry, currentStatus: LocalModelItemStatus? = null) {
        if (_uiState.value.checkingAccessEntryId != null) return
        if (currentStatus == LocalModelItemStatus.DOWNLOADING || currentStatus == LocalModelItemStatus.READY) {
            return
        }
        if (currentStatus == LocalModelItemStatus.FAILED) {
            if (downloadGuards.isMeteredConnection()) {
                _uiState.update { it.copy(dialog = LocalModelsDialog.MeteredConfirm(entryWithResolvedSize(entry))) }
            } else {
                beginDownload(entry)
            }
            return
        }
        when {
            downloadGuards.belowRamRequirement(entry) -> {
                _uiState.update { it.copy(dialog = LocalModelsDialog.RamWarning(entry)) }
            }

            downloadGuards.isMeteredConnection() -> {
                _uiState.update { it.copy(dialog = LocalModelsDialog.MeteredConfirm(entryWithResolvedSize(entry))) }
            }

            else -> beginDownload(entry)
        }
    }

    fun confirmRamWarning() {
        val entry = (_uiState.value.dialog as? LocalModelsDialog.RamWarning)?.entry ?: return
        dismissDialog()
        if (downloadGuards.isMeteredConnection()) {
            _uiState.update { it.copy(dialog = LocalModelsDialog.MeteredConfirm(entryWithResolvedSize(entry))) }
        } else {
            beginDownload(entry)
        }
    }

    fun confirmMeteredDownload() {
        val entry = (_uiState.value.dialog as? LocalModelsDialog.MeteredConfirm)?.entry ?: return
        dismissDialog()
        beginDownload(entry)
    }

    fun dismissDialog() {
        pendingGatedEntry = null
        _uiState.update { it.copy(dialog = LocalModelsDialog.Hidden) }
    }

    fun startHuggingFaceSignIn(): Intent? {
        val intent = huggingFaceAuthClient.authorizationIntent()
        if (intent == null) {
            _uiState.update { it.copy(dialog = LocalModelsDialog.OAuthNotConfigured()) }
        }
        return intent
    }

    fun onAuthActivityResult(data: Intent?) {
        val entry = pendingGatedEntry
            ?: (_uiState.value.dialog as? LocalModelsDialog.SignIn)?.entry
            ?: return
        scope.launch {
            when (val result = huggingFaceAuthClient.completeSignIn(data)) {
                HuggingFaceSignInResult.Cancelled -> dismissDialog()

                HuggingFaceSignInResult.Failed -> {
                    pendingGatedEntry = null
                    _uiState.update { it.copy(dialog = LocalModelsDialog.SignInFailed) }
                }

                is HuggingFaceSignInResult.Success -> {
                    huggingFaceTokenStore.saveAccessToken(result.accessToken)
                    pendingGatedEntry = null
                    _uiState.update { it.copy(dialog = LocalModelsDialog.Hidden) }
                    beginDownload(entry)
                }
            }
        }
    }

    fun onLicenseTabClosed() {
        val entry = pendingGatedEntry
            ?: (_uiState.value.dialog as? LocalModelsDialog.License)?.entry
            ?: return
        pendingGatedEntry = null
        _uiState.update { it.copy(dialog = LocalModelsDialog.Hidden) }
        beginDownload(entry)
    }

    fun retryAfterLicense() {
        onLicenseTabClosed()
    }

    fun openAccessTokenDialog(isSessionExpired: Boolean = false) {
        val expired = isSessionExpired ||
            (_uiState.value.dialog as? LocalModelsDialog.OAuthNotConfigured)?.isSessionExpired == true
        _uiState.update { it.copy(dialog = LocalModelsDialog.EnterAccessToken(expired)) }
    }

    fun saveAccessTokenAndRetry(token: String) {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            huggingFaceTokenStore.saveAccessToken(trimmed)
            retryPendingDownload()
        }
    }

    fun retryAfterAccessToken() {
        retryPendingDownload()
    }

    private fun retryPendingDownload() {
        val entry = pendingGatedEntry
        _uiState.update { it.copy(dialog = LocalModelsDialog.Hidden) }
        if (entry != null) {
            beginDownload(entry)
        }
    }

    fun release() {
        huggingFaceAuthClient.dispose()
    }

    private fun entryWithResolvedSize(entry: CatalogEntry): CatalogEntry {
        val resolvedSize = SocVariantResolver.resolve(entry, deviceSocModel).sizeInBytes
        return if (resolvedSize > 0L) entry.copy(sizeInBytes = resolvedSize) else entry
    }

    private fun beginDownload(entry: CatalogEntry) {
        scope.launch {
            val existing = localModelRepository.getById(entry.id)
            if (existing?.status == LocalModelStatus.DOWNLOADING || existing?.status == LocalModelStatus.READY) {
                return@launch
            }
            if (!entry.isGated) {
                localModelRepository.startDownload(entry)
                return@launch
            }
            _uiState.update { it.copy(checkingAccessEntryId = entry.id) }
            val step = runCatching { gatedDownloadCoordinator.resolve(entry) }
                .getOrDefault(GatedDownloadStep.Error)
            _uiState.update { it.copy(checkingAccessEntryId = null) }
            when (step) {
                GatedDownloadStep.Proceed -> localModelRepository.startDownload(entry)

                is GatedDownloadStep.NeedsSignIn -> {
                    pendingGatedEntry = entry
                    _uiState.update { it.copy(dialog = LocalModelsDialog.SignIn(entry, step.isSessionExpired)) }
                }

                is GatedDownloadStep.NeedsLicense -> {
                    pendingGatedEntry = entry
                    _uiState.update { it.copy(dialog = LocalModelsDialog.License(entry, step.modelPageUrl)) }
                }

                is GatedDownloadStep.OAuthNotConfigured -> {
                    pendingGatedEntry = entry
                    _uiState.update {
                        it.copy(dialog = LocalModelsDialog.OAuthNotConfigured(step.isSessionExpired))
                    }
                }

                GatedDownloadStep.Error -> _uiState.update {
                    it.copy(dialog = LocalModelsDialog.ProbeError)
                }
            }
        }
    }
}
