package dev.chungjungsoo.gptmobile.util

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.saveable.Saver

val multiScrollStateSaver: Saver<MutableList<ScrollState>, *> = Saver(
    save = { it.map { scrollState -> scrollState.value } },
    restore = { it.map { i -> ScrollState(i) }.toMutableList() }
)
