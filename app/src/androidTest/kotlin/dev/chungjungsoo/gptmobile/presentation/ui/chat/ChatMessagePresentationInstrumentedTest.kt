package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItem
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItemType
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEvent
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEventStatus
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatMessagePresentationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun completedMessageKeepsActionsHiddenUntilRequested() {
        composeRule.setContent {
            GPTMobileTheme {
                OpponentChatBubble(
                    text = "Answer",
                    canRetry = false,
                    isLoading = false
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Message actions").assertCountEquals(1)
        composeRule.onNodeWithText("Copy Text").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Message actions").performClick()

        composeRule.onNodeWithText("Copy Text").assertExists()
        composeRule.onNodeWithText("Select Text").assertExists()
    }

    @Test
    fun streamingAssistantHidesActionsAndUsesExactlyOneSignal() {
        composeRule.setContent {
            GPTMobileTheme {
                OpponentChatBubble(
                    text = "Answer",
                    canRetry = false,
                    isLoading = true
                )
            }
        }

        composeRule.onNodeWithContentDescription("Message actions").assertDoesNotExist()
        composeRule.onNodeWithText("Answer●").assertExists()
        composeRule.onNodeWithContentDescription("Tool in progress").assertDoesNotExist()
    }

    @Test
    fun activeToolReplacesStreamingDotWithOneProgressSignal() {
        composeRule.setContent {
            GPTMobileTheme {
                OpponentChatBubble(
                    text = "Answer",
                    canRetry = false,
                    isLoading = true,
                    timeline = listOf(
                        AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "Answer"),
                        AssistantTimelineItem(AssistantTimelineItemType.TOOL, toolSequence = 0)
                    ),
                    toolEvents = listOf(toolEvent(status = ToolEventStatus.RUNNING))
                )
            }
        }

        composeRule.onNodeWithText("Answer●").assertDoesNotExist()
        composeRule.onAllNodesWithContentDescription("Tool in progress").assertCountEquals(1)
    }

    @Test
    fun expandedPostToolStreamingRetainsOneTrailingDot() {
        composeRule.setContent {
            GPTMobileTheme {
                OpponentChatBubble(
                    text = "Answer",
                    canRetry = false,
                    isLoading = true,
                    timeline = listOf(
                        AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "Answer"),
                        AssistantTimelineItem(AssistantTimelineItemType.TOOL, toolSequence = 0)
                    ),
                    toolEvents = listOf(toolEvent()),
                    attachments = listOf("cache/notes.txt"),
                    contentIdentity = "post-tool"
                )
            }
        }

        composeRule.onNodeWithText("Details").performClick()

        composeRule.onAllNodesWithText("●").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Tool in progress").assertDoesNotExist()
        val dotTop = composeRule.onNodeWithText("●").fetchSemanticsNode().boundsInRoot.top
        val attachmentTop = composeRule.onNodeWithContentDescription("notes.txt").fetchSemanticsNode().boundsInRoot.top
        assertTrue(dotTop < attachmentTop)
    }

    @Test
    fun failedRetryStaysInlineAndIsAbsentFromActionsSheet() {
        composeRule.setContent {
            GPTMobileTheme {
                OpponentChatBubble(
                    text = "Error: failed",
                    canRetry = true,
                    isLoading = false,
                    isError = true,
                    canEdit = true,
                    showInlineRetry = true
                )
            }
        }

        composeRule.onAllNodesWithText("Retry").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Message actions").performClick()
        composeRule.onAllNodesWithText("Retry").assertCountEquals(1)
    }

    @Test
    fun successfulRetryInActionsSheetShowsToolsWarningOnce() {
        composeRule.setContent {
            GPTMobileTheme {
                OpponentChatBubble(
                    text = "Answer",
                    canRetry = true,
                    isLoading = false,
                    showInlineRetry = false
                )
            }
        }

        composeRule.onNodeWithContentDescription("Message actions").performClick()
        composeRule.onAllNodesWithText("Retry").assertCountEquals(1)
        composeRule.onAllNodesWithText("Retry may run assigned tools again.").assertCountEquals(1)
    }

    @Test
    fun expandedDetailsKeepProcessesAboveSingleAnswer() {
        composeRule.setContent {
            GPTMobileTheme {
                OpponentChatBubble(
                    text = "Answer",
                    thoughts = "Thought process",
                    timeline = listOf(
                        AssistantTimelineItem(AssistantTimelineItemType.THINKING, content = "Thought process"),
                        AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "Answer"),
                        AssistantTimelineItem(AssistantTimelineItemType.TOOL, toolSequence = 0)
                    ),
                    toolEvents = listOf(toolEvent()),
                    canRetry = false,
                    isLoading = false,
                    contentIdentity = "message-1"
                )
            }
        }

        composeRule.onAllNodesWithText("Answer").assertCountEquals(1)
        composeRule.onNodeWithText("Details").performClick()
        composeRule.onAllNodesWithText("Answer").assertCountEquals(1)
        val detailsTop = composeRule.onNodeWithText("Details").fetchSemanticsNode().boundsInRoot.top
        val thinkingTop = composeRule.onNodeWithText("View thinking process").fetchSemanticsNode().boundsInRoot.top
        val toolTop = composeRule.onNodeWithContentDescription("Expand tool trace").fetchSemanticsNode().boundsInRoot.top
        val answerTop = composeRule.onNodeWithText("Answer").fetchSemanticsNode().boundsInRoot.top
        assertTrue(detailsTop < thinkingTop)
        assertTrue(thinkingTop < toolTop)
        assertTrue(toolTop < answerTop)
        composeRule.onNodeWithContentDescription("Expand tool trace").performClick()
        composeRule.onNodeWithText("Arguments:").assertExists()
        composeRule.onNodeWithText("View thinking process").performClick()
        composeRule.onNodeWithText("Thought process").assertExists()
    }

    @Test
    fun missingToolReferenceIsVisibleInDetails() {
        composeRule.setContent {
            GPTMobileTheme {
                OpponentChatBubble(
                    text = "Answer",
                    timeline = listOf(
                        AssistantTimelineItem(AssistantTimelineItemType.TEXT, content = "Answer"),
                        AssistantTimelineItem(AssistantTimelineItemType.TOOL, toolSequence = 9)
                    ),
                    canRetry = false,
                    isLoading = false,
                    contentIdentity = "message-2"
                )
            }
        }

        composeRule.onNodeWithText("Details").performClick()
        composeRule.onNodeWithText("Details unavailable").assertExists()
    }

    @Test
    fun truncatedToolValueCanOpenItsFullOriginalText() {
        val fullValue = "a".repeat(1025)
        var selected = ""
        composeRule.setContent {
            GPTMobileTheme {
                ToolTraceBlock(
                    events = listOf(toolEvent().copy(arguments = fullValue, result = null)),
                    onViewFull = { selected = it }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Expand tool trace").performClick()
        composeRule.onNodeWithText("View full").performClick()

        assertEquals(fullValue, selected)
    }

    @Test
    fun composerKeepsAttachAndSendInOneSurface() {
        composeRule.setContent {
            GPTMobileTheme {
                ChatInputBox(inputState = rememberTextFieldState("Hello"))
            }
        }

        composeRule.onNodeWithContentDescription("Attach File").assertExists()
        composeRule.onNodeWithContentDescription("Send").assertExists()
    }

    @Test
    fun runningComposerReplacesSendWithStop() {
        composeRule.setContent {
            GPTMobileTheme {
                ChatInputBox(isRunning = true)
            }
        }

        composeRule.onNodeWithContentDescription("Attach File").assertExists()
        composeRule.onNodeWithContentDescription("Cancel active runs").assertExists()
        composeRule.onNodeWithContentDescription("Send").assertDoesNotExist()
    }

    private fun toolEvent(status: String = ToolEventStatus.COMPLETED) = ToolEvent(
        eventId = "event-0",
        runId = "run-0",
        sequence = 0,
        callId = "call-0",
        connectionUidSnapshot = "web",
        connectionNameSnapshot = "Web",
        toolName = "search",
        modelToolName = "web_search",
        arguments = "{}",
        result = "result",
        resultType = null,
        status = status,
        isError = false,
        startedAt = 1L,
        completedAt = if (status == ToolEventStatus.COMPLETED) 2L else null,
        error = null
    )
}
