package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class ChatBottomScrollTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun scrollToLatestMessage_reachesBottomWithOneConversation() {
        assertScrollReachesBottomAfterLatestMessageGrows(
            lastMessageIndex = 0,
            includePreviousMessage = false,
            growthDelayFrames = 1
        )
    }

    @Test
    fun scrollToLatestMessage_reachesBottomWhenOneConversationGrowsLate() {
        assertScrollReachesBottomAfterLatestMessageGrows(
            lastMessageIndex = 0,
            includePreviousMessage = false,
            growthDelayFrames = 3
        )
    }

    @Test
    fun scrollToLatestMessage_reachesBottomWhenLatestMessageGrowsAfterFirstLayout() {
        assertScrollReachesBottomAfterLatestMessageGrows(
            lastMessageIndex = 1,
            includePreviousMessage = true,
            growthDelayFrames = 1
        )
    }

    @Test
    fun scrollToLatestMessage_reachesBottomWithRealRevisionControls() {
        var listState: LazyListState? = null

        composeRule.setContent {
            MaterialTheme {
                val state = rememberLazyListState()
                val scope = rememberCoroutineScope()
                listState = state

                Box(Modifier.size(width = 320.dp, height = 280.dp)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = state
                    ) {
                        item {
                            UserChatBubble(
                                modifier = Modifier.padding(8.dp),
                                text = "Question",
                                onLongPress = {}
                            )
                        }

                        item {
                            OpponentChatBubble(
                                modifier = Modifier.padding(8.dp),
                                canRetry = true,
                                isLoading = false,
                                text = List(36) { "Long assistant answer line $it." }.joinToString("\n\n"),
                                revisionIndexLabel = "Revision 2/2",
                                canShowPreviousRevision = true,
                                canShowNextRevision = false
                            )
                        }

                        item {
                            Spacer(Modifier.height(1.dp))
                        }
                    }

                    Button(
                        modifier = Modifier.testTag("scrollButton"),
                        onClick = {
                            scope.launch {
                                state.animateScrollToLatestChatMessage(lastMessageIndex = 1)
                            }
                        }
                    ) {
                        Text("Bottom")
                    }
                }
            }
        }

        composeRule.onNodeWithTag("scrollButton").performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertFalse(listState?.canScrollForward ?: true)
        }
    }

    private fun assertScrollReachesBottomAfterLatestMessageGrows(
        lastMessageIndex: Int,
        includePreviousMessage: Boolean,
        growthDelayFrames: Int
    ) {
        var listState: LazyListState? = null

        composeRule.setContent {
            val state = rememberLazyListState()
            val scope = rememberCoroutineScope()
            listState = state

            Box(Modifier.size(width = 320.dp, height = 280.dp)) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("chatList"),
                    state = state
                ) {
                    if (includePreviousMessage) {
                        item {
                            Spacer(Modifier.height(360.dp))
                        }
                    }

                    item {
                        var showBottomControls by remember { mutableStateOf(false) }

                        LaunchedEffect(Unit) {
                            repeat(growthDelayFrames) {
                                withFrameNanos { }
                            }
                            showBottomControls = true
                        }

                        Column {
                            Text("latest message")
                            Spacer(Modifier.height(360.dp))
                            if (showBottomControls) {
                                Text("revision controls")
                                Spacer(Modifier.height(96.dp))
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(1.dp))
                    }
                }

                Button(
                    modifier = Modifier.testTag("scrollButton"),
                    onClick = {
                        scope.launch {
                            state.animateScrollToLatestChatMessage(lastMessageIndex)
                        }
                    }
                ) {
                    Text("Bottom")
                }
            }
        }

        composeRule.onNodeWithTag("scrollButton").performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertFalse(listState?.canScrollForward ?: true)
        }
    }
}
