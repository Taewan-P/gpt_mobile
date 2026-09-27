package dev.chungjungsoo.gptmobile.data.localruntime

import android.util.Log
import dev.chungjungsoo.gptmobile.data.model.LocalRuntimeBackend
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/** Selects the runtime, owns QNN fallback, and publishes the engine that actually loaded. */
class LocalRuntimeRouter(
    private val settingRepository: SettingRepository,
    private val qnnRuntime: LocalRuntime,
    private val liteRtRuntime: LocalRuntime
) : LocalRuntime {
    override val handlesEngineFallback = true
    private val _state = MutableStateFlow(LocalRuntimeState())
    override val state = _state.asStateFlow()

    @Volatile private var activeLoadedRuntime: LocalRuntime? = null
    private var requestedSpec: LocalEngineSpec? = null
    private var delegatedSpec: LocalEngineSpec? = null
    private var preferenceAtLoad: LocalRuntimeBackend? = null
    private var tuningAtLoad: Pair<Int?, Boolean>? = null

    override val deviceRamGb: Long get() = (activeLoadedRuntime ?: liteRtRuntime).deviceRamGb
    override fun getHardwareState(): DeviceHardwareState = (activeLoadedRuntime ?: liteRtRuntime).getHardwareState()
    override fun getAdaptiveThrottlingPolicy(): AdaptiveThrottlingPolicy =
        (activeLoadedRuntime ?: liteRtRuntime).getAdaptiveThrottlingPolicy()
    override fun loadedEngineSpec(): LocalEngineSpec? = state.value.engineSpec

    override suspend fun loadEngine(requested: LocalEngineSpec) {
        if (isEngineLoaded(requested)) return
        val tuning = settingRepository.getFeatureSettings().localEngineTuning()
        val spec = requested.copy(cpuThreads = tuning.first, cacheEnabled = tuning.second)
        val preferred = settingRepository.getLocalRuntimeBackend()
        unloadEngine()
        try {
            if (preferred == LocalRuntimeBackend.QUALCOMM_QNN && LocalAccelerators.normalize(spec.accelerator) == LocalAccelerators.NPU) {
                try {
                    qnnRuntime.loadEngine(spec)
                    activate(qnnRuntime, LocalRuntimeBackend.QUALCOMM_QNN, requested, spec, preferred)
                    return
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    if (error !is Exception && error !is LinkageError) throw error
                    // Failed initialization can own native resources, even before a spec is cached.
                    qnnRuntime.unloadEngine()
                    if (!settingRepository.getFeatureSettings().qnnAutomaticFallback) {
                        throw LocalRuntimeFallbackDisabledException(error)
                    }
                    if (dev.chungjungsoo.gptmobile.data.localmodel.LocalModelPackages.isNpuFile(spec.modelPath)) {
                        throw LocalNpuPackageException(error)
                    }
                    Log.w(TAG, "QNN failed; trying LiteRT-LM", error)
                    // Retrying NPU with the same Qualcomm dispatch is not a fallback.
                    val fallback = if (LocalAccelerators.normalize(spec.accelerator) == LocalAccelerators.NPU) {
                        spec.copy(accelerator = LocalAccelerators.GPU, litertDispatchLibDir = null)
                    } else {
                        spec.copy(litertDispatchLibDir = null)
                    }
                    val actual = loadLiteRt(fallback)
                    activate(liteRtRuntime, LocalRuntimeBackend.LITERT_LM, requested, actual, preferred, error.message)
                    // Update the visible selection only after a working fallback exists.
                    try {
                        settingRepository.updateLocalRuntimeBackend(LocalRuntimeBackend.LITERT_LM)
                        preferenceAtLoad = LocalRuntimeBackend.LITERT_LM
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (persistenceError: Exception) {
                        Log.w(TAG, "Could not save the active LiteRT-LM backend", persistenceError)
                    }
                    return
                }
            }
            val actual = loadLiteRt(spec)
            activate(
                liteRtRuntime,
                LocalRuntimeBackend.LITERT_LM,
                requested,
                actual,
                preferred,
                if (actual.accelerator != spec.accelerator) "${spec.accelerator.uppercase()} initialization failed" else null
            )
        } catch (error: Throwable) {
            withContext(NonCancellable) { unloadEngine() }
            throw error
        }
    }

    private suspend fun loadLiteRt(spec: LocalEngineSpec): LocalEngineSpec {
        val accelerators = if (dev.chungjungsoo.gptmobile.data.localmodel.LocalModelPackages.isNpuFile(spec.modelPath)) {
            listOf(LocalAccelerators.NPU)
        } else {
            when (LocalAccelerators.normalize(spec.accelerator)) {
                LocalAccelerators.NPU -> listOf(LocalAccelerators.NPU, LocalAccelerators.GPU, LocalAccelerators.CPU)
                LocalAccelerators.GPU -> listOf(LocalAccelerators.GPU, LocalAccelerators.CPU)
                else -> listOf(LocalAccelerators.CPU)
            }
        }
        var lastError: Throwable? = null
        for (accelerator in accelerators) {
            val candidate = spec.copy(
                accelerator = accelerator,
                litertDispatchLibDir = spec.litertDispatchLibDir.takeIf { accelerator == LocalAccelerators.NPU }
            )
            try {
                liteRtRuntime.loadEngine(candidate)
                return candidate
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (error !is Exception && error !is LinkageError) throw error
                lastError = error
                liteRtRuntime.unloadEngine()
            }
        }
        val failure = checkNotNull(lastError)
        if (dev.chungjungsoo.gptmobile.data.localmodel.LocalModelPackages.isNpuFile(spec.modelPath)) {
            throw LocalNpuPackageException(failure)
        }
        throw failure
    }

    private fun activate(
        runtime: LocalRuntime,
        backend: LocalRuntimeBackend,
        requested: LocalEngineSpec,
        delegated: LocalEngineSpec,
        preference: LocalRuntimeBackend,
        fallbackReason: String? = null
    ) {
        activeLoadedRuntime = runtime
        requestedSpec = requested
        delegatedSpec = delegated
        preferenceAtLoad = preference
        tuningAtLoad = delegated.cpuThreads to delegated.cacheEnabled
        _state.value = LocalRuntimeState(backend, runtime.loadedEngineSpec() ?: delegated, fallbackReason)
    }

    override suspend fun isEngineLoaded(spec: LocalEngineSpec): Boolean {
        val runtime = activeLoadedRuntime ?: return false
        // Read the current preference so switching settings invalidates a warm engine.
        if (preferenceAtLoad != settingRepository.getLocalRuntimeBackend() ||
            requestedSpec != spec ||
            tuningAtLoad != settingRepository.getFeatureSettings().localEngineTuning()
        ) {
            return false
        }
        return delegatedSpec?.let { runtime.isEngineLoaded(it) } == true
    }

    override suspend fun createConversation(config: LocalConversationConfig) {
        checkNotNull(activeLoadedRuntime) { "Local engine is not loaded" }.createConversation(config)
    }

    override fun sendMessage(text: String, images: List<ByteArray>): Flow<LocalRuntimeEvent> =
        activeLoadedRuntime?.sendMessage(text, images) ?: flowOf(LocalRuntimeEvent.Error("Local engine is not loaded"))

    override fun cancelActive() {
        activeLoadedRuntime?.cancelActive()
    }

    override fun hasOpenConversation(): Boolean = activeLoadedRuntime?.hasOpenConversation() == true

    override suspend fun closeConversation() {
        activeLoadedRuntime?.closeConversation()
    }

    override suspend fun unloadEngine() {
        activeLoadedRuntime = null
        requestedSpec = null
        delegatedSpec = null
        preferenceAtLoad = null
        tuningAtLoad = null
        _state.value = LocalRuntimeState()
        try {
            qnnRuntime.unloadEngine()
        } finally {
            liteRtRuntime.unloadEngine()
        }
    }

    private companion object {
        const val TAG = "LocalRuntimeRouter"
    }
}

internal fun dev.chungjungsoo.gptmobile.data.model.AppFeatureSettings.localEngineTuning(
    availableCores: Int = Runtime.getRuntime().availableProcessors()
): Pair<Int?, Boolean> = localCpuThreads.takeIf { it > 0 }?.coerceIn(1, availableCores.coerceIn(1, 64)) to localModelCache
