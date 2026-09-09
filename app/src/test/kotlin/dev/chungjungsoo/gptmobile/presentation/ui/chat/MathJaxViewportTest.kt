package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MathJaxViewportTest {
    private val window = IntSize(1080, 2400)
    private val overscan = 126f

    @Test
    fun onscreenFormulaIsVisible() {
        assertTrue(
            formulaVisibleInWindow(
                boundsInWindow = Rect(0f, 800f, 1080f, 1000f),
                windowSize = window,
                overscanPx = overscan
            )
        )
    }

    @Test
    fun formulaJustBelowWindowIsVisibleWithOverscan() {
        assertTrue(
            formulaVisibleInWindow(
                boundsInWindow = Rect(0f, 2450f, 1080f, 2600f),
                windowSize = window,
                overscanPx = overscan
            )
        )
    }

    @Test
    fun formulaFarBelowWindowIsNotVisible() {
        assertFalse(
            formulaVisibleInWindow(
                boundsInWindow = Rect(0f, 4000f, 1080f, 4200f),
                windowSize = window,
                overscanPx = overscan
            )
        )
    }

    @Test
    fun formulaFarAboveWindowIsNotVisible() {
        assertFalse(
            formulaVisibleInWindow(
                boundsInWindow = Rect(0f, -800f, 1080f, -400f),
                windowSize = window,
                overscanPx = overscan
            )
        )
    }

    @Test
    fun overlappingBottomEdgeIsVisible() {
        assertTrue(
            formulaVisibleInWindow(
                boundsInWindow = Rect(0f, 2300f, 1080f, 2500f),
                windowSize = window,
                overscanPx = 0f
            )
        )
    }

    @Test
    fun zeroSizedBoundsAreNotVisible() {
        assertFalse(
            formulaVisibleInWindow(
                boundsInWindow = Rect(10f, 10f, 10f, 40f),
                windowSize = window,
                overscanPx = overscan
            )
        )
    }

    @Test
    fun emptyWindowIsNotVisible() {
        assertFalse(
            formulaVisibleInWindow(
                boundsInWindow = Rect(0f, 0f, 100f, 100f),
                windowSize = IntSize.Zero,
                overscanPx = overscan
            )
        )
    }
}
