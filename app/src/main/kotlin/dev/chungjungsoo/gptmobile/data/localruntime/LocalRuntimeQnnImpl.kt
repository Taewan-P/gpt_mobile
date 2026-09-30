package dev.chungjungsoo.gptmobile.data.localruntime

import android.content.Context
import dev.chungjungsoo.gptmobile.BuildConfig
import java.io.File
import kotlinx.coroutines.flow.Flow

internal interface QnnLoadGuard {
    fun beforeLoad(spec: LocalEngineSpec)
    fun loadFinished()
}

/**
 * A native signal cannot be caught by Kotlin. Leave a marker only while QAIRT is
 * initializing so the next process can quarantine the same crashing tuple.
 */
internal class QnnInitializationCrashGuard(context: Context) : QnnLoadGuard {
    private val app = context.applicationContext
    private val marker = File(app.noBackupFilesDir, "qnn_dispatch/native-init.marker")

    override fun beforeLoad(spec: LocalEngineSpec) {
        val model = File(spec.modelPath)
        val install = app.packageManager.getPackageInfo(app.packageName, 0)
        val signature = listOf(
            install.longVersionCode,
            install.lastUpdateTime,
            BuildConfig.LITERT_LM_VERSION,
            BuildConfig.QAIRT_VERSION,
            model.canonicalPath,
            model.length(),
            model.lastModified()
        ).joinToString("|")
        check(marker.takeIf(File::isFile)?.readText() != signature) {
            "QNN was disabled for this model after its previous native initialization crashed. " +
                "Choose LiteRT-LM CPU/GPU or reinstall an NPU package compiled for QAIRT ${BuildConfig.QAIRT_VERSION}."
        }
        marker.parentFile?.mkdirs()
        marker.writeText(signature)
    }

    override fun loadFinished() {
        marker.delete()
    }
}

/** Qualcomm NPU execution through LiteRT-LM's dispatch API. No hidden CPU/GPU fallback. */
internal class LocalRuntimeQnnImpl(
    private val runtime: LocalRuntime,
    private val loadGuard: QnnLoadGuard,
    private val probeEnvironment: () -> QnnEnvironment.QnnProbeStatus
) : LocalRuntime {
    constructor(context: Context) : this(
        runtime = LocalRuntimeImpl(context),
        loadGuard = QnnInitializationCrashGuard(context),
        probeEnvironment = { QnnEnvironment.prepareForExecution(context) }
    )

    private var requestedSpec: LocalEngineSpec? = null
    private var dispatchedSpec: LocalEngineSpec? = null

    override val deviceRamGb: Long get() = runtime.deviceRamGb
    override fun getHardwareState(): DeviceHardwareState = runtime.getHardwareState()
    override fun getAdaptiveThrottlingPolicy(): AdaptiveThrottlingPolicy = runtime.getAdaptiveThrottlingPolicy()
    override fun loadedEngineSpec(): LocalEngineSpec? = runtime.loadedEngineSpec() ?: dispatchedSpec

    override suspend fun loadEngine(spec: LocalEngineSpec) {
        check(LocalAccelerators.normalize(spec.accelerator) == LocalAccelerators.NPU) {
            "QNN requires the NPU accelerator and a matching SoC model. Use LiteRT-LM for CPU/GPU."
        }
        val probe = probeEnvironment()
        check(probe.isReady) { probe.errorMessage ?: "Qualcomm NPU libraries are unavailable on this device" }
        val effectiveSpec = spec.copy(
            accelerator = LocalAccelerators.NPU,
            litertDispatchLibDir = spec.litertDispatchLibDir ?: probe.dispatchDir
        )
        requestedSpec = null
        dispatchedSpec = null
        loadGuard.beforeLoad(effectiveSpec)
        try {
            runtime.loadEngine(effectiveSpec)
        } catch (error: Throwable) {
            loadGuard.loadFinished()
            throw error
        }
        loadGuard.loadFinished()
        requestedSpec = spec
        dispatchedSpec = effectiveSpec
    }

    override suspend fun isEngineLoaded(spec: LocalEngineSpec): Boolean =
        requestedSpec == spec && dispatchedSpec?.let { runtime.isEngineLoaded(it) } == true

    override suspend fun createConversation(config: LocalConversationConfig) = runtime.createConversation(config)
    override fun sendMessage(text: String, images: List<ByteArray>): Flow<LocalRuntimeEvent> = runtime.sendMessage(text, images)
    override fun cancelActive() = runtime.cancelActive()
    override fun hasOpenConversation(): Boolean = runtime.hasOpenConversation()
    override suspend fun closeConversation() = runtime.closeConversation()

    override suspend fun unloadEngine() {
        requestedSpec = null
        dispatchedSpec = null
        runtime.unloadEngine()
    }
}
