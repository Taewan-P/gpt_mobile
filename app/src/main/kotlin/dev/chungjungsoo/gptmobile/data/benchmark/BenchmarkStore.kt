package dev.chungjungsoo.gptmobile.data.benchmark

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class BenchmarkStore @Inject constructor(@param:ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("profile_benchmarks_v1", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val mutableHistory = MutableStateFlow<List<BenchmarkRun>>(emptyList())
    val history = mutableHistory.asStateFlow()
    private var loaded = false

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock { loadLocked() }
    }

    private fun loadLocked() {
        if (loaded) return
        val saved = preferences.getString("history", null)
        mutableHistory.value = saved?.let { json.decodeFromString<List<BenchmarkRun>>(it) }.orEmpty()
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
