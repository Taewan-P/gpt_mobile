package dev.chungjungsoo.gptmobile.presentation.common

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsComponentsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingItemIsOneClickableNodeAndChevronIsDecorative() {
        var clicked = false
        composeRule.setContent {
            GPTMobileTheme {
                SettingItem(
                    title = "Theme",
                    description = "System default",
                    onItemClick = { clicked = true },
                    showTrailingIcon = true,
                    showLeadingIcon = false
                )
            }
        }

        composeRule.onNodeWithText("Theme").assertHasClickAction().performClick()
        assertTrue(clicked)
        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = false).assertCountEquals(1)
        composeRule
            .onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
                useUnmergedTree = true
            )
            .assertCountEquals(0)
    }

    @Test
    fun errorStateExposesItsDescriptionAsAnError() {
        composeRule.setContent {
            GPTMobileTheme {
                EmptyErrorState(
                    title = "Could not load",
                    description = "Try again",
                    isError = true
                )
            }
        }

        composeRule
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Try again"))
            .assertExists()
    }
}
