package dev.chungjungsoo.gptmobile.presentation.ui.chat

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ConversationEntryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun openingConversationOverridesRestoredMiddlePositionAndShowsTrueBottom() {
        lateinit var state: LazyListState
        compose.setContent {
            state = rememberLazyListState(initialFirstVisibleItemIndex = 2)
            Box(Modifier.size(320.dp, 280.dp)) {
                LazyColumn(Modifier.fillMaxSize(), state) {
                    items(8) { Spacer(Modifier.height(400.dp)) }
                    item { Spacer(Modifier.height(1.dp)) }
                }
                LaunchedEffect(Unit) { state.scrollToConversationEntry() }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(state.canScrollForward) }
    }

    @Test fun favouriteResponseAlignsAtTopEvenBelowLongPromptAndHistoryHeader() {
        lateinit var state: LazyListState
        var expectedOffset = 0
        compose.setContent {
            state = rememberLazyListState()
            expectedOffset = with(LocalDensity.current) { 420.dp.roundToPx() }
            Box(Modifier.size(320.dp, 280.dp)) {
                LazyColumn(Modifier.fillMaxSize(), state) {
                    item { Spacer(Modifier.height(48.dp)) }
                    item { Spacer(Modifier.height(700.dp)) }
                    item {
                        Column {
                            Spacer(Modifier.height(420.dp))
                            Spacer(Modifier.height(100.dp))
                        }
                    }
                    item { Spacer(Modifier.fillParentMaxHeight()) }
                }
                LaunchedEffect(Unit) { state.scrollToConversationEntry(targetItem = 2, responseOffset = expectedOffset) }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(2, state.firstVisibleItemIndex)
            assertEquals(expectedOffset, state.firstVisibleItemScrollOffset)
        }
    }
}
