package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.ceil

internal const val STREAM_PRESENTATION_CATCH_UP_MS = 100L

@Composable
internal fun rememberPresentedStreamText(
    received: String,
    isTerminal: Boolean,
    contentIdentity: Any
): String {
    var visible by remember(contentIdentity) { mutableStateOf(received) }
    if (isTerminal) return received

    LaunchedEffect(contentIdentity, received) {
        var remainingMs = STREAM_PRESENTATION_CATCH_UP_MS
        if (visible != received) {
            visible = nextPresentedText(received, visible, 16L, remainingMs)
            remainingMs -= 16L
        }
        var lastFrame = 0L
        while (visible != received) {
            val frameTime = withFrameNanos { it }
            val deltaMs = if (lastFrame == 0L) {
                16L
            } else {
                ((frameTime - lastFrame) / 1_000_000L).coerceAtLeast(1L)
            }
            lastFrame = frameTime
            visible = nextPresentedText(received, visible, deltaMs, remainingMs)
            remainingMs = (remainingMs - deltaMs).coerceAtLeast(1L)
        }
    }
    return visible
}

internal fun nextPresentedText(
    received: String,
    currentlyVisible: String,
    frameDeltaMillis: Long,
    catchUpMillis: Long = STREAM_PRESENTATION_CATCH_UP_MS,
    flush: Boolean = false
): String {
    if (flush || received == currentlyVisible) return received
    if (!received.startsWith(currentlyVisible)) return received
    if (frameDeltaMillis <= 0L) return currentlyVisible
    if (frameDeltaMillis >= catchUpMillis) return received

    val pending = received.length - currentlyVisible.length
    if (pending <= 0) return received

    val fraction = frameDeltaMillis.toDouble() / catchUpMillis.coerceAtLeast(1L)
    val graphemeBudget = ceil(pending * fraction).toInt().coerceAtLeast(1)
    return currentlyVisible + takeGraphemes(received.substring(currentlyVisible.length), graphemeBudget)
}

internal fun takeGraphemes(text: String, maxGraphemes: Int): String {
    if (maxGraphemes <= 0 || text.isEmpty()) return ""
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    iterator.setText(text)
    var end = 0
    var count = 0
    while (count < maxGraphemes) {
        val next = iterator.next()
        if (next == BreakIterator.DONE) {
            return text
        }
        end = next
        count++
    }
    return text.substring(0, end)
}
