package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import dev.chungjungsoo.gptmobile.R
import kotlinx.coroutines.launch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatBottomAutoScrollerInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun growingContentWhileFollowing_keepsTheTrueBottomVisible() {
        lateinit var listState: LazyListState
        var additionalHeight by mutableStateOf(0.dp)

        composeRule.setContent {
            listState = rememberLazyListState()

            Box(Modifier.size(width = 320.dp, height = 280.dp)) {
                GrowingChatList(listState, additionalHeight)
                ChatBottomAutoScroller(listState, isEnabled = true)
            }
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle { assertFalse(listState.canScrollForward) }

        composeRule.runOnIdle { additionalHeight = 480.dp }
        composeRule.waitForIdle()

        composeRule.runOnIdle { assertFalse(listState.canScrollForward) }
    }

    @Test
    fun bottomButton_reenablesFollowingForLateContentGrowth() {
        lateinit var listState: LazyListState
        var additionalHeight by mutableStateOf(0.dp)
        var isFollowingBottom by mutableStateOf(false)
        val scrollToBottomDescription = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.scroll_to_bottom_icon)

        composeRule.setContent {
            listState = rememberLazyListState()
            val scope = rememberCoroutineScope()

            Box(Modifier.size(width = 320.dp, height = 280.dp)) {
                GrowingChatList(listState, additionalHeight)
                ChatBottomAutoScroller(listState, isEnabled = isFollowingBottom)
                ScrollToBottomButton {
                    scope.launch {
                        listState.animateScrollToLatestChatMessage()
                        isFollowingBottom = true
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle { assertTrue(listState.canScrollForward) }

        composeRule.onNodeWithContentDescription(scrollToBottomDescription).performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertFalse(listState.canScrollForward) }

        composeRule.runOnIdle { additionalHeight = 480.dp }
        composeRule.waitForIdle()

        composeRule.runOnIdle { assertFalse(listState.canScrollForward) }
    }
}

@androidx.compose.runtime.Composable
private fun GrowingChatList(
    listState: LazyListState,
    additionalHeight: androidx.compose.ui.unit.Dp
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState
    ) {
        item { Spacer(Modifier.height(420.dp)) }
        item { Spacer(Modifier.height(320.dp + additionalHeight)) }
        item(key = "chat-bottom-anchor") { Spacer(Modifier.height(1.dp)) }
    }
}
