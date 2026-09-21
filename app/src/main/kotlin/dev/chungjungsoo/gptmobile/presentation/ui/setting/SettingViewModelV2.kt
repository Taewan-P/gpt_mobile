package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddPlatformSaveState(
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class SettingViewModelV2 @Inject constructor(
    private val settingRepository: SettingRepository
) : ViewModel() {

    private val _platformState = MutableStateFlow(listOf<PlatformV2>())
    val platformState: StateFlow<List<PlatformV2>> = _platformState.asStateFlow()

    private val _dialogState = MutableStateFlow(DialogState())
    val dialogState: StateFlow<DialogState> = _dialogState.asStateFlow()

    private val _addPlatformSaveState = MutableStateFlow(AddPlatformSaveState())
    val addPlatformSaveState: StateFlow<AddPlatformSaveState> = _addPlatformSaveState.asStateFlow()

    init {
        fetchPlatforms()
    }

    fun fetchPlatforms() {
        viewModelScope.launch {
            val platforms = settingRepository.fetchPlatformV2s()
            _platformState.update { platforms }
        }
    }

    fun addPlatform(
        platform: PlatformV2,
        onSuccess: () -> Unit = {}
    ) {
        if (_addPlatformSaveState.value.isSaving) return
        _addPlatformSaveState.value = AddPlatformSaveState(isSaving = true)
        viewModelScope.launch {
            try {
                settingRepository.addPlatformV2(platform)
            } catch (exception: CancellationException) {
                throw exception
            } catch (throwable: Throwable) {
                _addPlatformSaveState.value = AddPlatformSaveState(
                    errorMessage = throwable.message ?: "Could not save platform"
                )
                return@launch
            }
            try {
                val platforms = settingRepository.fetchPlatformV2s()
                _platformState.update { platforms }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Throwable) {
            }
            _addPlatformSaveState.value = AddPlatformSaveState()
            onSuccess()
        }
    }

    fun updatePlatform(platform: PlatformV2) {
        viewModelScope.launch {
            settingRepository.updatePlatformV2(platform)
            fetchPlatforms()
        }
    }

    fun deletePlatform(platform: PlatformV2) {
        viewModelScope.launch {
            settingRepository.deletePlatformV2(platform)
            fetchPlatforms()
        }
    }

    fun togglePlatformEnabled(platformId: Int) {
        val platform = _platformState.value.find { it.id == platformId }
        platform?.let {
            updatePlatform(it.copy(enabled = !it.enabled))
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
            val platform = _platformState.value.find { it.id == platformId }
            platform?.let { deletePlatform(it) }
        }
        closeDeleteDialog()
    }

    data class DialogState(
        val isThemeDialogOpen: Boolean = false,
        val isDeleteDialogOpen: Boolean = false,
        val platformToDelete: Int? = null
    )
}
