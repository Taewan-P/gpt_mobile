package dev.chungjungsoo.gptmobile.util

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.saveable.Saver

val multiScrollStateSaver: Saver<DefaultHashMap<Int, ScrollState>, *> = Saver(
    save = {
        val saver = hashMapOf<Int, Int>()
        it.forEach { i, scrollState -> saver[i] = scrollState.value }
        saver
    },
    restore = {
        val restored = DefaultHashMap<Int, ScrollState>({ ScrollState(0) })
        it.forEach { i, v -> restored[i] = ScrollState(v) }
        restored
    }
)
