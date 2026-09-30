package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.ConversationDelegationSettings
import dev.chungjungsoo.gptmobile.data.model.ModelDelegationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34], qualifiers = "w360dp-h640dp")
class DelegationSettingsContentTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun detailedControlsStayHiddenUntilAdvancedIsExpanded() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ModelDelegationSettingsContent(ModelDelegationSettings(), emptyList(), false, null, {}, {})
                }
            }
        }
        compose.onNodeWithText("Search queries: 9").assertDoesNotExist()
        compose.onNodeWithTag("delegate_model_dropdown").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("delegation_advanced").performScrollTo().performClick()
        compose.onNodeWithText("Search queries: 9").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Pages to read: 11").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun conversationSwitchRevealsDropdownAndSelectingOnlineHelperCreatesOverride() {
        var selected: ConversationDelegationSettings? = null
        val helper = PlatformV2(uid = "online", name = "Online helper", model = "model", compatibleType = ClientType.OPENROUTER, apiUrl = "https://openrouter.ai/api/v1")
        compose.setContent {
            MaterialTheme {
                var settings by remember { mutableStateOf(ModelDelegationSettings()) }
                ConversationDelegationCard(settings, listOf(helper), selected == null) { value ->
                    selected = value
                    settings = value?.applyTo(ModelDelegationSettings()) ?: ModelDelegationSettings()
                }
            }
        }
        compose.onNodeWithTag("delegate_model_dropdown").assertDoesNotExist()
        compose.onNodeWithTag("conversation_delegation_toggle").performClick()
        compose.onNodeWithTag("delegate_model_dropdown").assertIsDisplayed().performClick()
        compose.onNodeWithText("Online helper").performClick()
        assertEquals("online", selected?.targetProfileUid)
        assertTrue(selected?.enabled == true)
        assertTrue(selected?.allowRemoteWorker == true)
        compose.onNodeWithText("Use settings defaults").performClick()
        assertEquals(null, selected)
    }
}
