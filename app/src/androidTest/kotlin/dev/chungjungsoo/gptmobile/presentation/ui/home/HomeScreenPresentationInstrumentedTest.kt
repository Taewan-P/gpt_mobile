package dev.chungjungsoo.gptmobile.presentation.ui.home

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import org.junit.Rule
import org.junit.Test

class HomeScreenPresentationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun searchModeUsesBackToExitAndClearInsteadOfClose() {
        composeRule.setContent {
            GPTMobileTheme {
                HomeTopAppBar(
                    isSelectionMode = false,
                    isSearchMode = true,
                    selectedChats = 0,
                    duplicateEnabled = false,
                    scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
                    actionOnClick = {},
                    duplicateOnClick = {},
                    navigationOnClick = {},
                    onSearchQueryChanged = {},
                    searchQuery = "Tokyo"
                )
            }
        }

        composeRule.onNodeWithContentDescription("Go back").assertExists()
        composeRule.onNodeWithContentDescription("Clear").assertExists()
        composeRule.onNodeWithContentDescription("Close").assertDoesNotExist()
    }
}
