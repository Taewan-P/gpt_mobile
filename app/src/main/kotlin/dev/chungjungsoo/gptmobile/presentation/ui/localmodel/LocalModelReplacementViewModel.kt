package dev.chungjungsoo.gptmobile.presentation.ui.localmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceTokenStore
import dev.chungjungsoo.gptmobile.data.localmodel.GatedDownloadCoordinator
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelReplacementCoordinator
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelReplacementRequest
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntime
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import dev.chungjungsoo.gptmobile.di.DeviceSocModel
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LocalModelDownloadUiState
import dev.chungjungsoo.gptmobile.presentation.ui.setting.LocalModelsDialog
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class LocalModelReplacementUiState(
    val confirmation: LocalModelReplacementRequest? = null,
    val download: LocalModelDownloadUiState = LocalModelDownloadUiState()
)

@HiltViewModel
class LocalModelReplacementViewModel @Inject constructor(
    private val coordinator: LocalModelReplacementCoordinator,
    localModelRepository: LocalModelRepository,
    gatedDownloadCoordinator: GatedDownloadCoordinator,
    huggingFaceTokenStore: HuggingFaceTokenStore,
    downloadGuards: LocalDownloadGuards,
    huggingFaceAuthClient: HuggingFaceAuthClient,
    @param:DeviceSocModel deviceSocModel: String,
    localRuntime: LocalRuntime
) : ViewModel() {
    private val activeRequest = MutableStateFlow<LocalModelReplacementRequest?>(null)
    private val downloadActions = LocalModelDownloadActions(
        localModelRepository = localModelRepository,
        gatedDownloadCoordinator = gatedDownloadCoordinator,
        huggingFaceTokenStore = huggingFaceTokenStore,
        downloadGuards = downloadGuards,
        huggingFaceAuthClient = huggingFaceAuthClient,
        scope = viewModelScope,
        deviceSocModel = deviceSocModel,
        onDownloadStarted = {
            finishActiveRequest()
        },
        isNpuAvailable = { localRuntime.isNpuAvailable() }
    )

    val uiState: StateFlow<LocalModelReplacementUiState> = combine(
        coordinator.pending,
        downloadActions.uiState,
        activeRequest
    ) { queue, download, active ->
        val busy = active != null ||
            download.dialog !is LocalModelsDialog.Hidden ||
            download.checkingAccessEntryId != null
        LocalModelReplacementUiState(
            confirmation = queue.firstOrNull().takeUnless { busy },
            download = download
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LocalModelReplacementUiState())

    fun confirmReplacement() {
        val request = uiState.value.confirmation ?: coordinator.pending.value.firstOrNull() ?: return
        if (activeRequest.value != null) return
        activeRequest.value = request
        if (!downloadActions.requestConfirmedReplacement(request.entry, request.target)) {
            activeRequest.value = null
        }
    }

    fun dismissConfirmation() {
        downloadActions.dismissDialog()
        finishActiveRequest()
    }

    fun confirmRamWarning() {
        downloadActions.confirmRamWarning()
    }

    fun confirmMeteredDownload() {
        downloadActions.confirmMeteredDownload()
    }

    fun startHuggingFaceSignIn(): Intent? = downloadActions.startHuggingFaceSignIn()

    fun onAuthActivityResult(data: Intent?) {
        downloadActions.onAuthActivityResult(data)
    }

    fun onLicenseTabClosed() {
        downloadActions.onLicenseTabClosed()
    }

    fun retryAfterLicense() {
        downloadActions.retryAfterLicense()
    }

    fun openAccessTokenDialog() {
        downloadActions.openAccessTokenDialog()
    }

    fun saveAccessToken(token: String) {
        downloadActions.saveAccessTokenAndRetry(token)
    }

    override fun onCleared() {
        downloadActions.release()
        super.onCleared()
    }

    private fun finishActiveRequest() {
        val request = activeRequest.value ?: coordinator.pending.value.firstOrNull()
        if (request != null) coordinator.dismiss(request)
        activeRequest.value = null
    }
}
