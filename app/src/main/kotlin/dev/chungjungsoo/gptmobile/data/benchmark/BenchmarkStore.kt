package dev.chungjungsoo.gptmobile.data.benchmark

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement

@Singleton
class BenchmarkStore @Inject constructor(@param:ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("profile_benchmarks_v1", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val mutableHistory = MutableStateFlow<List<BenchmarkRun>>(emptyList())
    val history = mutableHistory.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "history" || key == null) {
            scope.launch {
                mutex.withLock {
                    loaded = false
                    loadLocked()
                }
            }
        }
    }
    private var loaded = false
    var loadWarning: String? = null
        private set

    init {
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock { loadLocked() }
    }

    private fun loadLocked() {
        if (loaded) return
        val saved = preferences.getString("history", null)
        val recovered = saved?.let { raw ->
            val entries = runCatching { json.parseToJsonElement(raw) as? JsonArray }.getOrNull()
            val valid = entries.orEmpty().mapNotNull { entry -> runCatching { json.decodeFromJsonElement<BenchmarkRun>(entry) }.getOrNull() }
            if (entries == null || valid.size != entries.size) {
                // Keep the original for recovery instead of letting a later save destroy it.
                check(preferences.edit().putString("recovery_history", raw).commit()) { "Could not preserve damaged benchmark history." }
                loadWarning = "Some saved benchmark records could not be read. Valid records were recovered; new benchmarks are available."
                dev.chungjungsoo.gptmobile.data.diagnostics.AppLogRecorder.record("Benchmark", "Recovered benchmark history with invalid records", "W")
            }
            valid
        }.orEmpty()
        mutableHistory.value = recovered.take(200)
        loaded = true
    }

    suspend fun save(run: BenchmarkRun) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked()
            persist((listOf(run) + mutableHistory.value.filterNot { it.id == run.id }).take(200))
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked()
            persist(mutableHistory.value.filterNot { it.id == id })
        }
    }

    private fun persist(runs: List<BenchmarkRun>) {
        check(preferences.edit().putString("history", json.encodeToString(runs)).commit()) { "Could not save benchmark history. Check available storage." }
        mutableHistory.value = runs
    }
}
