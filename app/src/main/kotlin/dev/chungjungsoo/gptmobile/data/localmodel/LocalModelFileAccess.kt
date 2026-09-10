package dev.chungjungsoo.gptmobile.data.localmodel

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object LocalModelFileAccess {
    // ponytail: serialize model downloads and cleanup; use per-model locks if concurrent downloads are needed.
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

    suspend fun whenIdle(block: suspend () -> Unit) {
        if (!mutex.tryLock()) return
        try {
            block()
        } finally {
            mutex.unlock()
        }
    }
}
