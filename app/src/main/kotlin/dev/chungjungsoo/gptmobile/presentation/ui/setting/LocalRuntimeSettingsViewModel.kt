package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.localruntime.LocalAccelerators
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntime
import dev.chungjungsoo.gptmobile.data.localruntime.QnnEnvironment
import dev.chungjungsoo.gptmobile.data.model.AppFeatureSettings
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.LocalRuntimeBackend
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@HiltViewModel
class LocalRuntimeSettingsViewModel @Inject constructor(
    private val repository: SettingRepository,
    private val runtime: LocalRuntime,
    private val models: LocalModelRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    val settings = repository.observeFeatureSettings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppFeatureSettings())
    val backend = repository.observeLocalRuntimeBackend().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocalRuntimeBackend.DEFAULT)
    val profiles = repository.observePlatformV2s().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _qnnAvailable = MutableStateFlow(false)
    val qnnAvailable = _qnnAvailable.asStateFlow()
    val active = runtime.state
    val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    val ramGb = runtime.deviceRamGb
    val soc = Build.SOC_MODEL.orEmpty()
    private val updates = Mutex()
    private val _hardware = MutableStateFlow(runtime.getHardwareState())
    val hardware = _hardware.asStateFlow()
    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()
    private val _npuStatus = MutableStateFlow("Checking NPU prerequisites…")
    val npuStatus = _npuStatus.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _hardware.value = runtime.getHardwareState()
            try {
                val probe = withContext(Dispatchers.IO) { QnnEnvironment.initialize(context) }
                _qnnAvailable.value = probe.isReady
                if (!probe.isReady && repository.getLocalRuntimeBackend() == LocalRuntimeBackend.QUALCOMM_QNN) repository.updateLocalRuntimeBackend(LocalRuntimeBackend.LITERT_LM)
                _npuStatus.value = if (probe.isReady) "Qualcomm NPU prerequisites ready; use a matching model" else probe.errorMessage ?: "NPU unavailable for this device or build"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _npuStatus.value = "NPU prerequisites could not be checked; refresh to retry"
            }
        }
    }

    fun selectBackend(value: LocalRuntimeBackend) = perform {
        require(value != LocalRuntimeBackend.QUALCOMM_QNN || _qnnAvailable.value) { "NPU is unavailable on this device" }
        repository.updateLocalRuntimeBackend(value)
    }
    fun updateTuning(threads: Int? = null, cache: Boolean? = null, idle: Int? = null, fallback: Boolean? = null) = perform {
        updates.withLock {
            val current = repository.getFeatureSettings()
            repository.updateFeatureSettings(
                current.copy(
                    localCpuThreads = threads?.coerceIn(0, cores) ?: current.localCpuThreads,
                    localModelCache = cache ?: current.localModelCache,
                    localIdleMinutes = idle?.coerceIn(0, 60) ?: current.localIdleMinutes,
                    qnnAutomaticFallback = fallback ?: current.qnnAutomaticFallback
                )
            )
        }
        _status.value = "Saved. Performance changes apply to the next model load."
    }
    fun releaseIdleMemory() = perform {
        _status.value = if (runtime.unloadIfIdle(0L)) "Idle model memory released" else "No idle model to release; active responses are kept running"
    }
    fun createProfile(entry: CatalogEntry, onCreated: (String) -> Unit) = perform {
        check(models.resolveDownloadedPath(entry.id) != null) { "Download this model first" }
        val existing = repository.fetchPlatformV2s().firstOrNull { it.compatibleType == ClientType.LITERT_LM && it.model == entry.id }
        if (existing != null) {
            onCreated(existing.uid)
            return@perform
        }
        check(LocalAccelerators.selectable(entry.supportedAccelerators, entry.socToModelFiles, soc).isNotEmpty()) { "This package does not match this phone. Choose a compatible download in the marketplace." }
        val defaults = dev.chungjungsoo.gptmobile.data.localruntime.localSamplingDefaults(entry, soc, ramGb)
        val profile = PlatformV2(
            name = entry.displayName,
            compatibleType = ClientType.LITERT_LM,
            model = entry.id,
            accelerator = LocalAccelerators.defaultFrom(entry.supportedAccelerators, entry.socToModelFiles, soc),
            maxTokens = defaults.maxTokens,
            temperature = 0.7f,
            topP = 0.95f,
            topK = 40
        )
        repository.addPlatformV2(profile)
        _status.value = "AI profile created"
        onCreated(profile.uid)
    }
    private fun perform(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _status.value = error.message ?: "Could not apply this change. Please try again."
            } finally {
                _busy.value = false
            }
        }
    }
}
