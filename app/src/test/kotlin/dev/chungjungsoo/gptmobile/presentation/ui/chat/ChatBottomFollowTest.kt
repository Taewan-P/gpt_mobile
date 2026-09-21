package dev.chungjungsoo.gptmobile.presentation.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatBottomFollowTest {
    @Test
    fun contentGrowthWhileFollowing_keepsFollowEnabled() {
        assertTrue(
            nextFollowBottom(
                isFollowing = true,
                isUserScrolling = false,
                isScrollingAway = false,
                canScrollForward = true
            )
        )
    }

    @Test
    fun userDragAwayFromBottom_disablesFollowing() {
        assertFalse(
            nextFollowBottom(
                isFollowing = true,
                isUserScrolling = true,
                isScrollingAway = true,
                canScrollForward = true
            )
        )
    }

    @Test
    fun reachingBottom_reenablesFollowing() {
        assertTrue(
            nextFollowBottom(
                isFollowing = false,
                isUserScrolling = false,
                isScrollingAway = false,
                canScrollForward = false
            )
        )
    }

    @Test
    fun userDragTowardBottomBeforeReachingIt_keepsFollowingDisabled() {
        assertFalse(
            nextFollowBottom(
                isFollowing = false,
                isUserScrolling = true,
                isScrollingAway = false,
                canScrollForward = true
            )
        )
    }

    @Test
    fun backwardFlingAfterPointerUp_disablesFollowing() {
        assertFalse(
            nextFollowBottom(
                isFollowing = true,
                isUserScrolling = true,
                isScrollingAway = true,
                canScrollForward = true
            )
        )
    }

    @Test
    fun backwardFlingInProgress_blocksAutoScroll() {
        assertFalse(
            shouldAutoScrollToBottom(
                isFollowing = true,
                isUserDragging = false,
                isScrollInProgress = true,
                isScrollingAway = true
            )
        )
    }

    @Test
    fun idleContentGrowthWhileFollowing_allowsAutoScroll() {
        assertTrue(
            shouldAutoScrollToBottom(
                isFollowing = true,
                isUserDragging = false,
                isScrollInProgress = false,
                isScrollingAway = false
            )
        )
    }
}
