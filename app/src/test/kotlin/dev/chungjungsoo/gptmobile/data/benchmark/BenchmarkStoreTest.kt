package dev.chungjungsoo.gptmobile.data.benchmark

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class BenchmarkStoreTest {
    @Test
    fun `bad records do not disable benchmarks or discard recoverable history`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("profile_benchmarks_v1", Context.MODE_PRIVATE)
        val valid = BenchmarkRun("valid", "profile", "Name", "OPENAI", "model", "key", false, BenchmarkMode.QUICK, 100)
        val original = "[" + kotlinx.serialization.json.Json.encodeToString(BenchmarkRun.serializer(), valid) + ", {\"mode\":\"UNKNOWN_MODE\"}]"
        prefs.edit().clear().putString("history", original).commit()
        val store = BenchmarkStore(context)
        store.load()
        assertEquals(listOf(valid), store.history.value)
        assertTrue(store.loadWarning != null)
        assertEquals(original, prefs.getString("recovery_history", null))
        store.save(valid.copy(id = "new"))
        assertEquals(2, store.history.value.size)
        assertEquals(original, prefs.getString("recovery_history", null))
    }

    @Test
    fun `checkpoints survive recreation replace same run and delete permanently`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("profile_benchmarks_v1", Context.MODE_PRIVATE).edit().clear().commit()
        val store = BenchmarkStore(context)
        val run = BenchmarkRun("id", "profile", "Name", "OPENAI", "model", "key", false, BenchmarkMode.QUICK, 100, finished = false)
        store.save(run)
        val restored = BenchmarkStore(context)
        restored.load()
        assertEquals(listOf(run), restored.history.value)
        restored.save(run.copy(finished = true))
        assertEquals(1, restored.history.value.size)
        assertTrue(restored.history.value.single().finished)
        restored.delete(run.id)
        val deleted = BenchmarkStore(context)
        deleted.load()
        assertTrue(deleted.history.value.isEmpty())
    }
}
