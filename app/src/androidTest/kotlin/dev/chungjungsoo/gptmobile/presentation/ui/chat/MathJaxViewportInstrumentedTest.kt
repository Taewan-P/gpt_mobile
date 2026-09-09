package dev.chungjungsoo.gptmobile.presentation.ui.chat

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MathJaxViewportInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun offscreenFormulaKeepsDescriptionWithoutAWebViewAndRemountsOnScroll() {
        val tex = "E=mc^2"
        lateinit var host: View
        var scrollState: androidx.compose.foundation.ScrollState? = null
        var revealedScroll = 0
        var observedBounds = "none"
        var observedWindowHeight = 0

        composeRule.setContent {
            host = LocalView.current
            val windowHeight = LocalWindowInfo.current.containerSize.height
            observedWindowHeight = windowHeight
            val spacerHeight = with(LocalDensity.current) { (windowHeight * 2).toDp() }
            val state = rememberScrollState()
            scrollState = state
            GPTMobileTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(state)
                ) {
                    Spacer(Modifier.height(spacerHeight))
                    DisplayMathView(
                        tex = tex,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onLayoutRectChanged(0L, 0L) { bounds ->
                                observedBounds = bounds.boundsInWindow.toString()
                            }
                    )
                    Spacer(Modifier.height(spacerHeight))
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(tex).assertExists()
        composeRule.runOnIdle {
            assertEquals("offscreen should not keep a WebView engine", 0, host.countWebViews())
        }

        composeRule.runOnIdle {
            val state = checkNotNull(scrollState)
            runBlocking {
                revealedScroll = state.maxValue / 2
                state.scrollTo(revealedScroll)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(tex).assertExists()
        composeRule.runOnIdle {
            val state = checkNotNull(scrollState)
            assertEquals(
                "onscreen formula should mount one WebView; window=$observedWindowHeight " +
                    "scroll=${state.value}/${state.maxValue} bounds=$observedBounds",
                1,
                host.countWebViews()
            )
        }

        composeRule.runOnIdle {
            val state = checkNotNull(scrollState)
            runBlocking { state.scrollTo(state.maxValue) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(tex).assertExists()
        composeRule.runOnIdle {
            assertEquals("scrolled-away formula should destroy its WebView", 0, host.countWebViews())
        }

        composeRule.runOnIdle {
            val state = checkNotNull(scrollState)
            runBlocking { state.scrollTo(revealedScroll.coerceIn(0, state.maxValue)) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(tex).assertExists()
        composeRule.runOnIdle {
            assertEquals("returning into view remounts from cache", 1, host.countWebViews())
        }
    }

    private fun View.countWebViews(): Int {
        var count = if (this is WebView) 1 else 0
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                count += getChildAt(index).countWebViews()
            }
        }
        return count
    }
}
