package dev.chungjungsoo.gptmobile.data.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationDelegationSettingsTest {
    @Test
    fun overridesKeepGlobalBudgetsAndLeaveOtherConversationsAlone() {
        val defaults = ModelDelegationSettings(enabled = false, targetProfileUid = "default", maxOutputTokens = 1024, handoffTokens = 512)
        val chat = ChatMcpToolConfig(delegation = ConversationDelegationSettings(true, "online-helper", true))
        val effective = chat.effectiveDelegation(defaults)
        assertTrue(effective.enabled)
        assertTrue(effective.allowRemoteWorkers)
        assertEquals("online-helper", effective.targetProfileUid)
        assertEquals(1024, effective.maxOutputTokens)
        assertEquals(512, effective.handoffTokens)
        assertFalse(effective.fallbackToAnotherProfile)
        assertFalse(ChatMcpToolConfig().effectiveDelegation(defaults).enabled)
        assertEquals("default", defaults.targetProfileUid)
        assertFalse(ChatMcpToolConfig(delegation = ConversationDelegationSettings(false)).effectiveDelegation(defaults.copy(enabled = true)).enabled)
    }

    @Test
    fun persistedSettingsAndQueuedToolConfigurationRoundTripWithLegacyDefaults() {
        val override = ConversationDelegationSettings(true, "chosen-helper", true)
        val features = AppFeatureSettings(conversationDelegation = mapOf(7 to override, 9 to ConversationDelegationSettings(false)))
        val restored = Json.decodeFromString<AppFeatureSettings>(Json.encodeToString(features))
        assertEquals(override, restored.conversationDelegation[7])
        assertFalse(restored.conversationDelegation.getValue(9).enabled)
        val queued = ChatMcpToolConfig(delegation = override)
        assertEquals(queued, Json.decodeFromString<ChatMcpToolConfig>(Json.encodeToString(queued)))
        assertEquals(emptyMap<Int, ConversationDelegationSettings>(), Json.decodeFromString<AppFeatureSettings>("{}").conversationDelegation)
        assertEquals(null, Json.decodeFromString<ChatMcpToolConfig>("{}").delegation)
    }
}
