package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.chungjungsoo.gptmobile.data.localruntime.DiagnosticsTelemetryProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Sampling stops when the screen is hidden, paused or removed from composition. */
@Composable
internal fun rememberLiveHardware(backend: String, accelerator: String, live: Boolean = true): DiagnosticsTelemetryProvider.DiagnosticsSnapshot? {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val snapshot by produceState<DiagnosticsTelemetryProvider.DiagnosticsSnapshot?>(null, context, lifecycle, backend, accelerator, live) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            do {
                value = withContext(Dispatchers.IO) { DiagnosticsTelemetryProvider.getSnapshot(context, backend, accelerator) }
                if (live) delay(2000)
            } while (live && isActive)
        }
    }
    return snapshot
}

@Composable
internal fun rememberLiveClock(live: Boolean = true): Long {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(System.currentTimeMillis(), lifecycle, live) {
        if (live) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    value = System.currentTimeMillis()
                    delay(1000)
                }
            }
        }
    }
    return now
}
