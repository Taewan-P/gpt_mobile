package dev.chungjungsoo.gptmobile.presentation.ui.localmodel

import android.content.Intent
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceTokenStore
import dev.chungjungsoo.gptmobile.data.localmodel.GatedDownloadCoordinator
import dev.chungjungsoo.gptmobile.data.localmodel.GatedDownloadStep
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelStatus
import dev.chungjungsoo.gptmobile.data.localmodel.ResolvedModelDownload
import dev.chungjungsoo.gptmobile.data.localmodel.SocVariantResolver
import dev.chungjungsoo.gptmobile.data.localruntime.localModelExecutionTargets
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LocalModelDownloadUiState
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LocalModelItemStatus
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LocalModelsDialog
import kotlinx.coroutines.CancellationException
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
    private val deviceSocModel: String = "",
    private val onDownloadStarted: () -> Unit = {},
    private val isNpuAvailable: () -> Boolean = { false }
) {
    private var pendingGatedEntry: CatalogEntry? = null
    private var pendingResolvedTarget: ResolvedModelDownload? = null
    private var allowReadyReplacement: Boolean = false
    private var beginGeneration: Int = 0

    private val _uiState = MutableStateFlow(LocalModelDownloadUiState())
    val uiState: StateFlow<LocalModelDownloadUiState> = _uiState.asStateFlow()

    fun requestDownload(entry: CatalogEntry, currentStatus: LocalModelItemStatus? = null) {
        val resolved = localModelExecutionTargets(entry, "auto", deviceSocModel, isNpuAvailable()).firstOrNull()?.download
            ?: SocVariantResolver.resolve(entry, "", "cpu")
        requestDownload(entry, currentStatus, resolved, allowReadyReplacement = false)
    }

    fun requestConfirmedReplacement(entry: CatalogEntry, resolved: ResolvedModelDownload): Boolean {
        if (_uiState.value.checkingAccessEntryId != null) return false
        if (_uiState.value.dialog !is LocalModelsDialog.Hidden) return false
        requestDownload(
            entry,
            LocalModelItemStatus.READY,
            resolved,
            allowReadyReplacement = true
        )
        return true
    }

    private fun requestDownload(
        entry: CatalogEntry,
        currentStatus: LocalModelItemStatus?,
        resolved: ResolvedModelDownload?,
        allowReadyReplacement: Boolean
    ) {
        if (_uiState.value.checkingAccessEntryId != null || _uiState.value.dialog !is LocalModelsDialog.Hidden) return
        beginGeneration += 1
        if (currentStatus == LocalModelItemStatus.DOWNLOADING) return
        if (currentStatus == LocalModelItemStatus.READY && !allowReadyReplacement) {
            return
        }
        pendingResolvedTarget = resolved
        this.allowReadyReplacement = allowReadyReplacement
        val downloadEntry = resolved?.let { entryWithExplicitTarget(entry, it) } ?: entry
        if (currentStatus == LocalModelItemStatus.FAILED) {
            if (downloadGuards.isMeteredConnection()) {
                _uiState.update { it.copy(dialog = LocalModelsDialog.MeteredConfirm(entryWithDisplaySize(downloadEntry, resolved))) }
            } else {
                beginDownload(downloadEntry)
            }
            return
        }
        when {
            downloadGuards.belowRamRequirement(downloadEntry) -> {
                _uiState.update { it.copy(dialog = LocalModelsDialog.RamWarning(downloadEntry)) }
            }

            downloadGuards.isMeteredConnection() -> {
                _uiState.update { it.copy(dialog = LocalModelsDialog.MeteredConfirm(entryWithDisplaySize(downloadEntry, resolved))) }
            }

            else -> beginDownload(downloadEntry)
        }
    }

    fun confirmRamWarning() {
        val entry = (_uiState.value.dialog as? LocalModelsDialog.RamWarning)?.entry ?: return
        hideCurrentDialog()
        if (downloadGuards.isMeteredConnection()) {
            _uiState.update { it.copy(dialog = LocalModelsDialog.MeteredConfirm(entryWithDisplaySize(entry, pendingResolvedTarget))) }
        } else {
            beginDownload(entry)
        }
    }

    fun confirmMeteredDownload() {
        val entry = (_uiState.value.dialog as? LocalModelsDialog.MeteredConfirm)?.entry ?: return
        hideCurrentDialog()
        beginDownload(entry)
    }

    fun dismissDialog() {
        pendingGatedEntry = null
        pendingResolvedTarget = null
        allowReadyReplacement = false
        beginGeneration += 1
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
        val requestGeneration = beginGeneration
        scope.launch {
            val result = huggingFaceAuthClient.completeSignIn(data)
            if (requestGeneration != beginGeneration) return@launch
            when (result) {
                HuggingFaceSignInResult.Cancelled -> {
                    dismissDialog()
                    onDownloadStarted()
                }

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

    private fun hideCurrentDialog() {
        _uiState.update { it.copy(dialog = LocalModelsDialog.Hidden) }
    }

    private fun entryWithDisplaySize(entry: CatalogEntry, resolved: ResolvedModelDownload?): CatalogEntry {
        val resolvedSize = resolved?.sizeInBytes
            ?: SocVariantResolver.resolve(entry, deviceSocModel).sizeInBytes
        return if (resolvedSize > 0L) entry.copy(sizeInBytes = resolvedSize) else entry
    }

    private fun entryWithExplicitTarget(entry: CatalogEntry, resolved: ResolvedModelDownload): CatalogEntry = entry.copy(
        downloadUrl = resolved.downloadUrl,
        sizeInBytes = resolved.sizeInBytes.takeIf { it > 0L } ?: entry.sizeInBytes,
        socToModelFiles = emptyMap()
    )

    private fun beginDownload(entry: CatalogEntry) {
        val requestGeneration = beginGeneration
        scope.launch {
            if (requestGeneration != beginGeneration) return@launch
            val existing = localModelRepository.getById(entry.id)
            val isReadyReplacement = allowReadyReplacement && pendingResolvedTarget != null
            if (existing?.status == LocalModelStatus.DOWNLOADING) {
                finishStartedFlow()
                return@launch
            }
            if (existing?.status == LocalModelStatus.READY && !isReadyReplacement) {
                finishStartedFlow()
                return@launch
            }
            val resolved = pendingResolvedTarget
            if (!entry.isGated) {
                startResolvedDownload(entry, resolved, requestGeneration)
                return@launch
            }
            _uiState.update { it.copy(checkingAccessEntryId = entry.id) }
            val step = runCatching { gatedDownloadCoordinator.resolve(entry) }
                .getOrDefault(GatedDownloadStep.Error)
            if (requestGeneration != beginGeneration) {
                _uiState.update { it.copy(checkingAccessEntryId = null) }
                return@launch
            }
            _uiState.update { it.copy(checkingAccessEntryId = null) }
            when (step) {
                GatedDownloadStep.Proceed -> startResolvedDownload(entry, resolved, requestGeneration)

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

    private suspend fun startResolvedDownload(
        entry: CatalogEntry,
        resolved: ResolvedModelDownload?,
        requestGeneration: Int
    ) {
        try {
            if (requestGeneration != beginGeneration) return
            if (resolved != null) {
                localModelRepository.startDownload(entry, resolved)
            } else {
                localModelRepository.startDownload(entry)
            }
            if (requestGeneration == beginGeneration) {
                finishStartedFlow()
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (error: Exception) {
            if (requestGeneration != beginGeneration) return
            _uiState.update {
                it.copy(dialog = LocalModelsDialog.DownloadError(error.message ?: "Could not start the model download"), checkingAccessEntryId = null)
            }
        }
    }

    private fun finishStartedFlow() {
        pendingResolvedTarget = null
        allowReadyReplacement = false
        onDownloadStarted()
    }
}
