package dev.chungjungsoo.gptmobile.data.diagnostics

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Process
import dev.chungjungsoo.gptmobile.data.security.DiagnosticRedactor
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AppLogEntry(val time: Long, val level: String, val tag: String, val message: String) {
    fun line() = "${Instant.ofEpochMilli(time)} $level/$tag: $message"
}

/** Opt-in, app-process-only diagnostics. No network upload and no HTTP bodies. */
object AppLogRecorder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<AppLogEntry>(512, BufferOverflow.DROP_OLDEST)
    private val mutableEnabled = MutableStateFlow(false)
    private val mutableEntries = MutableStateFlow<List<AppLogEntry>>(emptyList())
    private val mutableError = MutableStateFlow<String?>(null)
    val enabled = mutableEnabled.asStateFlow()
    val entries = mutableEntries.asStateFlow()
    val error = mutableError.asStateFlow()

    @Volatile private var app: Context? = null
    private var reader: Job? = null

    @Volatile private var process: java.lang.Process? = null
    private val fileLock = Any()
    private val recent = ArrayDeque<AppLogEntry>()

    @Synchronized fun initialize(application: Application) {
        if (app != null) return
        app = application.applicationContext
        scope.launch {
            for (entry in queue) {
                synchronized(fileLock) {
                    if (!mutableEnabled.value) return@synchronized
                    recent.addLast(entry)
                    while (recent.size > 500) recent.removeFirst()
                    mutableEntries.value = recent.toList()
                    runCatching { append(entry.line()) }.onFailure { mutableError.value = "Unable to save logs. Check available storage." }
                }
            }
        }
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = event(activity, "created")
            override fun onActivityStarted(activity: Activity) = event(activity, "started")
            override fun onActivityResumed(activity: Activity) = event(activity, "resumed")
            override fun onActivityPaused(activity: Activity) = event(activity, "paused")
            override fun onActivityStopped(activity: Activity) = event(activity, "stopped")
            override fun onActivityDestroyed(activity: Activity) = event(activity, "destroyed")
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            private fun event(activity: Activity, state: String) = record("Lifecycle", "${activity.javaClass.simpleName} $state")
        })
        setEnabled(application.getSharedPreferences("app_diagnostics", Context.MODE_PRIVATE).getBoolean("tracking", false))
        record("Application", "Started process ${Process.myPid()}")
    }

    @Synchronized fun setEnabled(value: Boolean) {
        mutableEnabled.value = value
        app?.getSharedPreferences("app_diagnostics", Context.MODE_PRIVATE)?.edit()?.putBoolean("tracking", value)?.apply()
        if (!value) {
            process?.destroy()
            process = null
            reader?.cancel()
            reader = null
            return
        }
        if (reader?.isActive == true || app == null) return
        mutableError.value = null
        reader = scope.launch {
            var running: java.lang.Process? = null
            try {
                running = ProcessBuilder("logcat", "--pid=${Process.myPid()}", "-v", "brief", "-T", "1").redirectErrorStream(true).start()
                process = running
                if (!mutableEnabled.value) {
                    running.destroy()
                    return@launch
                }
                running.inputStream.bufferedReader().use { stream ->
                    while (isActive && mutableEnabled.value) {
                        val line = stream.readLine() ?: break
                        val level = line.firstOrNull()?.toString()?.takeIf { it in setOf("V", "D", "I", "W", "E", "F") } ?: "I"
                        record("Android", line, level)
                    }
                }
                if (mutableEnabled.value) mutableError.value = "Android log stream ended. App event tracking continues; toggle tracking to reconnect."
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (mutableEnabled.value) mutableError.value = "Android log stream unavailable on this device. App event tracking continues."
            } finally {
                running?.destroy()
                if (process === running) process = null
            }
        }
        record("Diagnostics", "Log tracking enabled")
    }

    fun record(tag: String, message: String, level: String = "I") {
        if (!mutableEnabled.value) return
        queue.trySend(AppLogEntry(System.currentTimeMillis(), level, tag.take(64), redactLogMessage(message).take(8000)))
    }

    fun clear() {
        scope.launch {
            synchronized(fileLock) {
                while (queue.tryReceive().isSuccess) Unit
                recent.clear()
                mutableEntries.value = emptyList()
                runCatching {
                    directory()?.listFiles()?.forEach { it.delete() }
                    app?.let { File(it.cacheDir, "diagnostics/app-diagnostics.log").delete() }
                }
            }
        }
    }

    /** Call from an IO dispatcher. The exported snapshot is already redacted. */
    fun export(): File? = synchronized(fileLock) {
        val context = app ?: return@synchronized null
        val folder = directory() ?: return@synchronized null
        val target = File(context.cacheDir, "diagnostics/app-diagnostics.log").also { it.parentFile?.mkdirs() }
        target.bufferedWriter().use { output ->
            listOf("previous.log", "current.log").map { File(folder, it) }.filter { it.exists() }.forEach { file ->
                file.bufferedReader().useLines { lines -> lines.forEach { output.appendLine(it) } }
            }
        }
        target
    }

    private fun directory(): File? = app?.let { File(it.filesDir, "diagnostics").also { folder -> folder.mkdirs() } }
    private fun append(line: String) {
        val directory = directory() ?: return
        val current = File(directory, "current.log")
        if (current.length() > 1_000_000) {
            val previous = File(directory, "previous.log")
            previous.delete()
            check(current.renameTo(previous))
        }
        current.appendText(line + "\n")
    }
}

internal fun redactLogMessage(message: String): String {
    var text = DiagnosticRedactor.redact(message)
    text = text.replace(Regex("(?i)(authorization|proxy-authorization|cookie|set-cookie|x-api-key|x-goog-api-key|x-subscription-token|mcp-session-id)\\s*[:=]\\s*[^\\r\\n]+"), "$1: [redacted]")
    text = text.replace(Regex("(?i)(bearer|basic)\\s+[a-z0-9._~+/=-]+"), "$1 [redacted]")
    text = text.replace(Regex("(?i)([\\\"]?(?:api_?key|access_?token|refresh_?token|password|client_?secret)[\\\"]?\\s*[:=]\\s*[\\\"]?)[^\\\"\\s,}]+"), "$1[redacted]")
    text = text.replace(Regex("\\b(?:sk-|hf_|ghp_|github_pat_)[A-Za-z0-9_-]{8,}"), "[redacted]")
    return text
}
