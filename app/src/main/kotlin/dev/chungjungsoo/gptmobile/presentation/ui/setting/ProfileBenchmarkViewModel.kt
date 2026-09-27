package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.benchmark.BenchmarkMode
import dev.chungjungsoo.gptmobile.data.benchmark.BenchmarkOutcome
import dev.chungjungsoo.gptmobile.data.benchmark.BenchmarkRun
import dev.chungjungsoo.gptmobile.data.benchmark.BenchmarkRunner
import dev.chungjungsoo.gptmobile.data.benchmark.BenchmarkSample
import dev.chungjungsoo.gptmobile.data.benchmark.BenchmarkStore
import dev.chungjungsoo.gptmobile.data.benchmark.benchmarkConfigKey
import dev.chungjungsoo.gptmobile.data.benchmark.benchmarkSuite
import dev.chungjungsoo.gptmobile.data.benchmark.runBenchmarkSuite
import dev.chungjungsoo.gptmobile.data.database.ChatDatabaseV2
import dev.chungjungsoo.gptmobile.data.database.dao.AgentPersistenceDao
import dev.chungjungsoo.gptmobile.data.database.dao.AgentRunDao
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEventStatus
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntime
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.security.DiagnosticRedactor
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BenchmarkProgress(val profileName: String, val testName: String, val completed: Int, val total: Int)

data class EverydayToolPerformance(val profileUid: String, val provider: String, val model: String, val completed: Int, val failed: Int) {
    val successPercent: Double? get() = (completed + failed).takeIf { it > 0 }?.let { 100.0 * completed / it }
}

@HiltViewModel
class ProfileBenchmarkViewModel @Inject constructor(
    settings: SettingRepository,
    private val chats: ChatRepository,
    private val store: BenchmarkStore,
    private val runtime: LocalRuntime,
    database: ChatDatabaseV2,
    runDao: AgentRunDao,
    persistenceDao: AgentPersistenceDao,
    savedState: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    val profiles = settings.observePlatformV2s().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val selectedUid = MutableStateFlow(savedState.get<String>("profileUid").orEmpty())
    val selected = combine(profiles, selectedUid) { list, uid -> list.firstOrNull { it.uid == uid } ?: list.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val history = store.history
    val localEnvironment = combine(settings.observeLocalRuntimeBackend(), settings.observeFeatureSettings()) { backend, features ->
        "$backend|${features.localCpuThreads}|${features.localModelCache}|${features.qnnAutomaticFallback}|" +
            "${features.localSpeculativeDecoding}|${features.localNativeMetrics}|${dev.chungjungsoo.gptmobile.BuildConfig.LITERT_LM_VERSION}"
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val legacyReport: String? = context.getSharedPreferences("connection_doctor", Context.MODE_PRIVATE)
        .getString("last_report", null)?.takeIf { it.startsWith("Benchmark v1") }
    private val mutableProgress = MutableStateFlow<BenchmarkProgress?>(null)
    val progress = mutableProgress.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val mutableReady = MutableStateFlow(false)
    val ready = mutableReady.asStateFlow()
    private val mutableDays = MutableStateFlow(30)
    val days = mutableDays.asStateFlow()
    private var job: Job? = null

    private val invocations = database.invocationDao().statistics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val activeRequests = invocations.map { requests -> requests.any { it.status == "RUNNING" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val everyday = combine(invocations, profiles, days) { requests, list, range ->
        val since = if (range == 0) 0L else System.currentTimeMillis() - range.toLong() * 24 * 60 * 60 * 1000
        profilePerformance(requests.filter { it.kind != "benchmark" && !it.parentRunId.startsWith("benchmark-") && it.startedAt >= since }, list.associate { it.uid to it.name })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val everydayTools = combine(runDao.observeRecent(10_000), persistenceDao.observeRecentToolEvents(10_000), days) { runs, events, range ->
        val since = if (range == 0) 0L else System.currentTimeMillis() / 1000 - range.toLong() * 24 * 60 * 60
        val byId = runs.associateBy { it.runId }
        events.filter { (it.startedAt ?: 0) >= since && it.status in setOf(ToolEventStatus.COMPLETED, ToolEventStatus.FAILED) }
            .mapNotNull { event -> byId[event.runId]?.let { Triple(it.profileUid, it.providerSnapshot, it.modelSnapshot) to event } }
            .groupBy({ it.first }, { it.second }).map { (key, tools) ->
                val failures = tools.count { it.isError || it.status == ToolEventStatus.FAILED }
                EverydayToolPerformance(key.first, key.second, key.third, tools.size - failures, failures)
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            try {
                store.load()
                mutableReady.value = true
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableError.value = "Could not load benchmark history: ${safeMessage(error)}"
            }
        }
    }

    fun select(profile: PlatformV2) {
        if (job?.isActive != true) selectedUid.value = profile.uid
    }

    fun dismissError() {
        mutableError.value = null
    }

    fun selectRange(days: Int) {
        require(days in setOf(0, 7, 30))
        mutableDays.value = days
    }

    fun cancel() {
        job?.cancel()
    }

    fun delete(id: String) {
        if (job?.isActive == true) return
        viewModelScope.launch {
            try {
                store.delete(id)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableError.value = safeMessage(error)
            }
        }
    }

    fun start(mode: BenchmarkMode) {
        val profile = selected.value ?: return
        if (job?.isActive == true || !mutableReady.value) return
        if (profile.compatibleType == ClientType.LITERT_LM && localEnvironment.value.isBlank()) return
        if (activeRequests.value) {
            mutableError.value = "Wait for active model requests to finish before benchmarking."
            return
        }
        if (profile.model.isBlank()) {
            mutableError.value = "Choose a model in this AI profile first."
            return
        }
        mutableError.value = null
        job = viewModelScope.launch {
            try {
                chats.validateBenchmarkProfile(profile)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableError.value = safeMessage(error)
                return@launch
            }
            val suite = benchmarkSuite(mode)
            var run = BenchmarkRun(
                UUID.randomUUID().toString(), profile.uid, profile.name, profile.compatibleType.name,
                profile.model, benchmarkConfigKey(profile, localEnvironment.value), profile.compatibleType == ClientType.LITERT_LM,
                mode, System.currentTimeMillis(), finished = false,
                device = "${Build.MANUFACTURER} ${Build.MODEL}", thermalBefore = thermal(), batteryBefore = battery(),
                engineWasLoaded = profile.compatibleType == ClientType.LITERT_LM && runtime.loadedEngineSpec() != null
            )
            var currentTest = suite.first()
            try {
                mutableProgress.value = BenchmarkProgress(profile.name, "Preparing tests", 0, suite.size)
                store.save(run)
                val supportsTools = chats.supportsBenchmarkTools(profile)
                val stoppedReason = runBenchmarkSuite(
                    suite = suite,
                    runCase = { index, test ->
                        currentTest = test
                        mutableProgress.value = BenchmarkProgress(profile.name, test.label, index, suite.size)
                        BenchmarkRunner(openSession = { turns, tools ->
                            chats.openBenchmarkSession(profile, turns, tools, "benchmark-${run.id}-${test.id}")
                        }).run(test, supportsTools)
                    },
                    onSample = { sample ->
                        val pss = withContext(Dispatchers.IO) { Debug.getPss() }
                        val actual = runtime.state.value.takeIf { run.local && sample.completed }
                        run = run.copy(
                            samples = run.samples + sample,
                            peakClientPssKb = maxOf(run.peakClientPssKb ?: 0, pss),
                            backend = actual?.backend?.displayName ?: run.backend,
                            accelerator = actual?.engineSpec?.accelerator ?: run.accelerator
                        )
                        store.save(run)
                    }
                )
                if (stoppedReason != null) {
                    run = run.copy(stoppedReason = stoppedReason)
                    mutableError.value = "Benchmark stopped: $stoppedReason"
                }
            } catch (_: CancellationException) {
                val canceledSample = if (run.samples.any { it.testId == currentTest.id }) emptyList() else listOf(BenchmarkSample(currentTest.id, currentTest.label, currentTest.category, BenchmarkOutcome.CANCELED))
                run = run.copy(canceled = true, samples = run.samples + canceledSample)
            } catch (error: Exception) {
                mutableError.value = safeMessage(error)
                run = run.copy(canceled = true)
            } finally {
                withContext(NonCancellable) {
                    try {
                        store.save(run.copy(finished = true, thermalAfter = thermal(), batteryAfter = battery()))
                    } catch (error: Exception) {
                        mutableError.value = "Could not save benchmark: ${safeMessage(error)}"
                    }
                    mutableProgress.value = null
                }
            }
        }
    }

    private fun thermal(): Int? = context.getSystemService(PowerManager::class.java)?.currentThermalStatus
    private fun battery(): Int? = context.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
    private fun safeMessage(error: Exception) = DiagnosticRedactor.redact(error.message ?: "Unknown error").take(500)
}
