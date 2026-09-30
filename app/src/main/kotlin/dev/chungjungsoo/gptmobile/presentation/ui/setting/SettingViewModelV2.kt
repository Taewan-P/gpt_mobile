package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.backup.BackupProtection
import dev.chungjungsoo.gptmobile.data.backup.BackupRestoreResult
import dev.chungjungsoo.gptmobile.data.backup.BackupStatus
import dev.chungjungsoo.gptmobile.data.backup.CompleteBackupManager
import dev.chungjungsoo.gptmobile.data.backup.CompleteBackupSection
import dev.chungjungsoo.gptmobile.data.backup.CompleteBackupSelection
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ProviderConnection
import dev.chungjungsoo.gptmobile.data.model.AppFeature
import dev.chungjungsoo.gptmobile.data.model.AppFeatureSettings
import dev.chungjungsoo.gptmobile.data.model.DebugMetric
import dev.chungjungsoo.gptmobile.data.model.LocalRuntimeBackend
import dev.chungjungsoo.gptmobile.data.model.ProfileLabel
import dev.chungjungsoo.gptmobile.data.model.encodeProfileLabels
import dev.chungjungsoo.gptmobile.data.model.parseProfileLabels
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class SettingViewModelV2 @Inject constructor(
    private val settingRepository: SettingRepository,
    private val completeBackupManager: CompleteBackupManager,
    private val localRuntime: dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntime
) : ViewModel() {

    val platformState: StateFlow<List<PlatformV2>> = settingRepository.observePlatformV2s()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val providerConnections: StateFlow<List<ProviderConnection>> =
        settingRepository.observeProviderConnections()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val localRuntimeState = localRuntime.state

    val localRuntimeBackend: StateFlow<LocalRuntimeBackend> = settingRepository.observeLocalRuntimeBackend()
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalRuntimeBackend.DEFAULT)

    val debugMode: StateFlow<Boolean> = settingRepository.observeDebugMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val featureSettings: StateFlow<AppFeatureSettings> = settingRepository.observeFeatureSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppFeatureSettings())

    private val _backupStatus = MutableStateFlow(completeBackupManager.getBackupStatus())
    val backupStatus: StateFlow<BackupStatus> = _backupStatus.asStateFlow()

    private val backupProtectionMutex = Mutex()
    private val _backupUi = MutableStateFlow(BackupUiState(selection = completeBackupManager.savedSelection()))
    val backupUi: StateFlow<BackupUiState> = _backupUi.asStateFlow()

    private val _dialogState = MutableStateFlow(DialogState())
    val dialogState: StateFlow<DialogState> = _dialogState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    init {
        fetchPlatforms()
        refreshBackupStatus()
    }

    fun refreshBackupStatus() {
        _backupStatus.value = completeBackupManager.getBackupStatus()
    }

    fun updateLocalRuntimeBackend(backend: LocalRuntimeBackend) {
        viewModelScope.launch {
            settingRepository.updateLocalRuntimeBackend(backend)
            _uiEvent.emit(UiEvent.ShowToast("Local inference engine set to ${backend.displayName}"))
        }
    }

    fun updateDebugMode(enabled: Boolean) {
        viewModelScope.launch {
            settingRepository.updateDebugMode(enabled)
            _uiEvent.emit(UiEvent.ShowToast(if (enabled) "Debug diagnostics HUD enabled" else "Debug diagnostics HUD disabled"))
        }
    }

    fun updateFeature(feature: AppFeature, enabled: Boolean) {
        viewModelScope.launch {
            val updated = featureSettings.value.withFeature(feature, enabled)
            settingRepository.updateFeatureSettings(updated)
            val state = if (enabled) "enabled" else "disabled"
            _uiEvent.emit(UiEvent.ShowToast("${feature.title} $state"))
        }
    }
    fun updateDebugMetric(metric: DebugMetric, enabled: Boolean) {
        viewModelScope.launch {
            settingRepository.updateFeatureSettings(featureSettings.value.withDebugMetric(metric, enabled))
        }
    }

    fun fetchPlatforms() {
        viewModelScope.launch {
            settingRepository.fetchPlatformV2s()
        }
    }

    fun addPlatform(platform: PlatformV2) {
        viewModelScope.launch {
            settingRepository.addPlatformV2(platform)
        }
    }

    fun addPlatform(
        platform: PlatformV2,
        newConnection: ProviderConnection?,
        credential: String?
    ) {
        viewModelScope.launch {
            val connection = newConnection?.let {
                settingRepository.addProviderConnection(it, credential)
            }
            val profileToSave = platform.copy(
                providerConnectionUid = connection?.uid ?: platform.providerConnectionUid,
                apiUrl = if (connection != null || platform.providerConnectionUid != null) "" else platform.apiUrl,
                token = if (connection != null || platform.providerConnectionUid != null) null else platform.token,
                secretRef = if (connection != null || platform.providerConnectionUid != null) null else platform.secretRef
            )
            synchronizeLinkedLabelColors(profileToSave)
            settingRepository.addPlatformV2(profileToSave)
        }
    }

    private suspend fun synchronizeLinkedLabelColors(profile: PlatformV2) {
        val desiredColors = parseProfileLabels(profile.labels)
            .associateBy(ProfileLabel::key)
        if (desiredColors.isEmpty()) return

        settingRepository.fetchPlatformV2s().forEach { existing ->
            val currentLabels = parseProfileLabels(existing.labels)
            val linked = currentLabels.map { current ->
                desiredColors[current.key]
                    ?.colorHex
                    ?.let { current.copy(colorHex = it) }
                    ?: current
            }
            val encoded = encodeProfileLabels(linked)
            if (encoded != existing.labels) {
                settingRepository.updatePlatformV2(existing.copy(labels = encoded))
            }
        }
    }

    suspend fun providerKeys(uid: String): List<String> =
        dev.chungjungsoo.gptmobile.data.network.ApiCredentialRotator.parseKeys(settingRepository.getProviderCredentials(uid))

    suspend fun saveProviderSettings(connection: ProviderConnection, keys: List<String>) {
        settingRepository.updateProviderConnection(connection, dev.chungjungsoo.gptmobile.data.network.ApiCredentialRotator.formatKeys(keys))
    }

    fun updateProviderConnection(connection: ProviderConnection, credential: String? = null) {
        viewModelScope.launch {
            settingRepository.updateProviderConnection(connection, credential)
        }
    }

    fun deleteProviderConnection(connection: ProviderConnection) {
        viewModelScope.launch {
            try {
                check(settingRepository.deleteProviderConnection(connection))
                _uiEvent.emit(UiEvent.ShowToast("Provider deleted. Conversation history was kept."))
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiEvent.emit(UiEvent.ShowToast("Could not delete the provider. Please try again."))
            }
        }
    }

    fun updatePlatform(platform: PlatformV2) {
        viewModelScope.launch {
            settingRepository.updatePlatformV2(platform)
        }
    }

    fun deletePlatform(platform: PlatformV2) {
        viewModelScope.launch {
            settingRepository.deletePlatformV2(platform)
        }
    }

    fun togglePlatformEnabled(platformId: Int) {
        val platform = platformState.value.find { it.id == platformId }
        platform?.let { target ->
            val updated = target.copy(enabled = !target.enabled)
            updatePlatform(updated)
        }
    }

    fun togglePlatformFavorite(platformId: Int) {
        val platform = platformState.value.find { it.id == platformId }
        platform?.let { target ->
            val updated = target.copy(isFavorite = !target.isFavorite)
            updatePlatform(updated)
        }
    }

    fun openThemeDialog() = _dialogState.update { it.copy(isThemeDialogOpen = true) }

    fun closeThemeDialog() = _dialogState.update { it.copy(isThemeDialogOpen = false) }

    fun openDeleteDialog(platformId: Int) = _dialogState.update {
        it.copy(
            isDeleteDialogOpen = true,
            platformToDelete = platformId
        )
    }

    fun closeDeleteDialog() = _dialogState.update {
        it.copy(
            isDeleteDialogOpen = false,
            platformToDelete = null
        )
    }

    fun confirmDelete() {
        _dialogState.value.platformToDelete?.let { platformId ->
            val platform = platformState.value.find { it.id == platformId }
            platform?.let { deletePlatform(it) }
        }
        closeDeleteDialog()
    }

    fun openBackupRestoreDialog() {
        if (_backupUi.value.isBusy) return
        refreshBackupStatus()
        _dialogState.update { it.copy(isBackupRestoreDialogOpen = true) }
        _backupUi.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                backupProtectionMutex.withLock { loadBackupProtection() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _backupUi.update { it.copy(message = "Could not load saved backup password. Enter it again.", isError = true) }
            } finally {
                _backupUi.update { it.copy(isBusy = false) }
            }
        }
    }

    private suspend fun loadBackupProtection() {
        val protection = completeBackupManager.savedProtection()
        _backupUi.update { it.copy(passwordProtectionEnabled = protection.enabled, backupPassword = protection.password) }
    }

    private suspend fun saveBackupProtection() {
        val state = _backupUi.value
        completeBackupManager.saveProtection(BackupProtection(state.passwordProtectionEnabled, state.backupPassword))
    }

    private fun persistBackupProtection() {
        viewModelScope.launch {
            try {
                backupProtectionMutex.withLock { saveBackupProtection() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _backupUi.update { it.copy(message = "Could not save backup password. Please try again.", isError = true) }
            }
        }
    }

    fun closeBackupRestoreDialog() {
        if (_backupUi.value.isBusy) return
        _backupUi.update {
            BackupUiState(selection = completeBackupManager.savedSelection(), passwordProtectionEnabled = it.passwordProtectionEnabled, backupPassword = it.backupPassword)
        }
        _dialogState.update { it.copy(isBackupRestoreDialogOpen = false) }
    }

    fun updateLegacyBackupPassword(value: String) {
        if (!_backupUi.value.isWorking) {
            _backupUi.update { it.copy(legacyPassword = value, message = null, isError = false) }
        }
    }

    fun updateBackupPasswordProtection(enabled: Boolean) {
        if (!_backupUi.value.isBusy) {
            _backupUi.update { it.copy(passwordProtectionEnabled = enabled, message = null, isError = false) }
            persistBackupProtection()
        }
    }

    fun updateBackupPassword(value: String) {
        if (!_backupUi.value.isBusy) {
            _backupUi.update { it.copy(backupPassword = value, message = null, isError = false) }
            persistBackupProtection()
        }
    }

    fun updateBackupSection(section: CompleteBackupSection, enabled: Boolean) {
        if (!_backupUi.value.isWorking) {
            val next = _backupUi.value.selection.toggled(section, enabled)
            completeBackupManager.saveSelection(next)
            _backupUi.update {
                it.copy(
                    selection = next,
                    passwordProtectionEnabled = it.passwordProtectionEnabled,
                    message = null,
                    isError = false
                )
            }
        }
    }

    fun selectAllBackupSections() {
        if (!_backupUi.value.isWorking) {
            completeBackupManager.saveSelection(CompleteBackupSelection.ALL)
            _backupUi.update { it.copy(selection = CompleteBackupSelection.ALL, message = null, isError = false) }
        }
    }

    fun clearBackupSections() {
        if (!_backupUi.value.isWorking) {
            val empty = CompleteBackupSelection(emptySet())
            completeBackupManager.saveSelection(empty)
            _backupUi.update {
                it.copy(selection = empty, message = null, isError = false)
            }
        }
    }

    fun prepareBackupPicker(restoring: Boolean): Boolean {
        val state = _backupUi.value
        if (state.isBusy || state.isWorking) return false
        if (!restoring && !state.canBackup) {
            _backupUi.update { it.copy(message = "Select backup contents and use a password with at least 8 characters when encryption is on.", isError = true) }
            return false
        }
        _backupUi.update {
            it.copy(
                isBusy = true,
                message = null,
                isError = false,
                restoreUri = null,
                backupUri = null,
                recoveryKeyUri = null,
                requiresRecoveryKey = false,
                requiresLegacyPassword = false
            )
        }
        return true
    }

    fun cancelBackupPicker() {
        _backupUi.update {
            it.copy(
                isBusy = false,
                isWorking = false,
                restoreUri = null,
                backupUri = null,
                recoveryKeyUri = null,
                requiresRecoveryKey = false,
                requiresLegacyPassword = false
            )
        }
    }

    fun backupDestinationSelected(uri: Uri?) {
        if (uri == null) {
            cancelBackupPicker()
            return
        }
        val state = _backupUi.value
        if (state.selection.sections.isEmpty()) {
            _backupUi.update { it.copy(isBusy = false, message = "Select at least one backup section.", isError = true) }
            return
        }
        if (state.passwordProtectionEnabled && state.backupPassword.length < 8) {
            _backupUi.update { it.copy(isBusy = false, message = "Use a backup password with at least 8 characters.", isError = true) }
            return
        }
        runBackupOperation {
            backupProtectionMutex.withLock { saveBackupProtection() }
            completeBackupManager.backup(
                uri,
                state.selection,
                state.backupPassword.takeIf { state.passwordProtectionEnabled },
                encrypt = state.passwordProtectionEnabled
            )
        }
    }

    fun backupRecoveryKeySelected(uri: Uri?) {
        val state = _backupUi.value
        val destination = state.backupUri ?: return
        if (uri == null) {
            cancelBackupPicker()
            _backupUi.update { it.copy(message = "Backup canceled: save a recovery key to use passwordless encryption.") }
            return
        }
        _backupUi.update { it.copy(backupUri = null) }
        runBackupOperation {
            completeBackupManager.backup(destination, state.selection, recoveryKeyUri = uri)
        }
    }

    fun restoreRecoveryKeySelected(uri: Uri?) {
        if (uri != null) _backupUi.update { it.copy(recoveryKeyUri = uri, message = null, isError = false) }
    }

    fun restoreSourceSelected(uri: Uri?) {
        if (uri == null) {
            cancelBackupPicker()
            return
        }
        viewModelScope.launch {
            try {
                val requiresPassword = completeBackupManager.requiresPassword(uri)
                val requiresKey = completeBackupManager.requiresRecoveryKey(uri)
                _backupUi.update {
                    it.copy(
                        isBusy = false,
                        restoreUri = uri,
                        requiresLegacyPassword = requiresPassword,
                        legacyPassword = if (requiresPassword) it.backupPassword else "",
                        requiresRecoveryKey = requiresKey,
                        message = null,
                        isError = false
                    )
                }
            } catch (error: Exception) {
                _backupUi.update {
                    it.copy(
                        isBusy = false,
                        restoreUri = null,
                        message = error.localizedMessage ?: "Could not inspect the backup file.",
                        isError = true
                    )
                }
            }
        }
    }

    fun confirmRestore() {
        val state = _backupUi.value
        val uri = state.restoreUri ?: return
        if (state.requiresLegacyPassword && state.legacyPassword.isBlank()) {
            _backupUi.update {
                it.copy(message = "Enter the password used by this older encrypted backup.", isError = true)
            }
            return
        }
        if (state.selection.sections.isEmpty() || (state.requiresRecoveryKey && state.recoveryKeyUri == null)) return
        _backupUi.update { it.copy(restoreUri = null, isBusy = true) }
        runBackupOperation {
            val password = state.legacyPassword.takeIf(String::isNotBlank)
            backupProtectionMutex.withLock {
                val result = completeBackupManager.restore(uri, password, state.selection, state.recoveryKeyUri)
                if (result.success) loadBackupProtection()
                result
            }
        }
    }

    private fun runBackupOperation(operation: suspend () -> BackupRestoreResult) {
        if (_backupUi.value.isWorking) return
        _backupUi.update { it.copy(isBusy = true, isWorking = true) }
        viewModelScope.launch {
            try {
                val result = operation()
                _backupUi.update {
                    it.copy(
                        message = result.message,
                        isError = !result.success,
                        legacyPassword = if (result.success) "" else it.legacyPassword
                    )
                }
                refreshBackupStatus()
                if (result.success) {
                    fetchPlatforms()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _backupUi.update { it.copy(message = error.localizedMessage ?: "Backup or restore failed.", isError = true) }
            } finally {
                cancelBackupPicker()
            }
        }
    }

    data class BackupUiState(
        val selection: CompleteBackupSelection = CompleteBackupSelection(),
        val passwordProtectionEnabled: Boolean = false,
        val backupPassword: String = "",
        val legacyPassword: String = "",
        val requiresLegacyPassword: Boolean = false,
        val isBusy: Boolean = false,
        val isWorking: Boolean = false,
        val restoreUri: Uri? = null,
        val backupUri: Uri? = null,
        val recoveryKeyUri: Uri? = null,
        val requiresRecoveryKey: Boolean = false,
        val message: String? = null,
        val isError: Boolean = false
    ) {
        val canBackup: Boolean
            get() = !isBusy &&
                !isWorking &&
                selection.sections.isNotEmpty() &&
                (!passwordProtectionEnabled || backupPassword.length >= 8)
    }

    sealed interface UiEvent {
        data class ShowToast(val message: String) : UiEvent
    }

    data class DialogState(
        val isThemeDialogOpen: Boolean = false,
        val isDeleteDialogOpen: Boolean = false,
        val platformToDelete: Int? = null,
        val isBackupRestoreDialogOpen: Boolean = false
    )
}
