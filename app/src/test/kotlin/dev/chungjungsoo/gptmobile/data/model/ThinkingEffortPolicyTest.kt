package dev.chungjungsoo.gptmobile.data.model

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingEffortPolicyTest {
    @Test
    fun chatOverrideReplacesProfileBaseAndInvalidOverrideIsIgnored() {
        val platform = platform(ClientType.ANTHROPIC, "claude-sonnet-5", thinkingEffort = "low")

        assertEquals("high", applyChatThinkingEffort(platform, "high").thinkingEffort)
        assertEquals("low", applyChatThinkingEffort(platform, "not-a-level").thinkingEffort)
        assertEquals("low", applyChatThinkingEffort(platform, null).thinkingEffort)
    }

    @Test
    fun controlUsesProfileBaseUntilTheChatSetsAnOverride() {
        val claude = platform(ClientType.ANTHROPIC, "claude-sonnet-5", reasoning = true, thinkingEffort = "high")
        val gpt = platform(ClientType.OPENAI, "gpt-5.6", reasoning = true, thinkingEffort = "high")

        val sharedBase = thinkingEffortControlState(listOf(claude, gpt), chatOverride = null)
        assertTrue(sharedBase.enabled)
        assertEquals(ThinkingEffort.HIGH, sharedBase.selectedEffort)
        assertTrue(sharedBase.followsProfileDefault)

        val overridden = thinkingEffortControlState(listOf(claude, gpt), chatOverride = "minimal")
        assertEquals(ThinkingEffort.MINIMAL, overridden.selectedEffort)
        assertFalse(overridden.followsProfileDefault)
    }

    @Test
    fun mixedProfileBasesStayMixedUntilTheChatChoosesOneLevel() {
        val claude = platform(ClientType.ANTHROPIC, "claude-sonnet-5", reasoning = true, thinkingEffort = "low")
        val gpt = platform(ClientType.OPENAI, "gpt-5.6", reasoning = true, thinkingEffort = "max")

        val state = thinkingEffortControlState(listOf(claude, gpt), chatOverride = null)

        assertTrue(state.enabled)
        assertNull(state.selectedEffort)
        assertTrue(state.followsProfileDefault)
    }

    @Test
    fun controlIsDisabledWhenThinkingIsOffOrTheModelCannotAcceptEffort() {
        val reasoningOff = platform(ClientType.OPENAI, "gpt-5.6", reasoning = false)
        val offState = thinkingEffortControlState(listOf(reasoningOff), chatOverride = "high")
        assertFalse(offState.enabled)
        assertEquals(ThinkingEffortControlStatus.REQUIRES_EXTENDED_THINKING, offState.status)

        val unsupported = platform(ClientType.OPENAI, "gpt-4o", reasoning = true)
        val unsupportedState = thinkingEffortControlState(listOf(unsupported), chatOverride = "high")
        assertFalse(unsupportedState.enabled)
        assertEquals(ThinkingEffortControlStatus.UNSUPPORTED, unsupportedState.status)
        assertFalse(platformThinkingEffortEditable(unsupported))
    }

    @Test
    fun customAndLocalRuntimesDoNotAdvertiseAdjustableEffort() {
        assertFalse(supportsAdjustableThinkingEffort(ClientType.CUSTOM, "gpt-5.6"))
        assertFalse(supportsAdjustableThinkingEffort(ClientType.LITERT_LM, "gemma-3"))
        assertTrue(supportsAdjustableThinkingEffort(ClientType.OLLAMA, "llama3"))
        assertTrue(supportsAdjustableThinkingEffort(ClientType.OPENROUTER, "any-model"))
    }

    @Test
    fun providerEffortValuesClampToWhatEachModelAccepts() {
        assertEquals("max", openAiReasoningEffort("gpt-5.6", ThinkingEffort.MAX))
        assertEquals("low", openAiReasoningEffort("gpt-5.6", ThinkingEffort.MINIMAL))
        assertEquals("xhigh", openAiReasoningEffort("gpt-5.2", ThinkingEffort.MAX))
        assertEquals("low", openAiReasoningEffort("gpt-5.2", ThinkingEffort.MINIMAL))
        assertEquals("medium", openAiReasoningEffort("gpt-5.2-pro", ThinkingEffort.LOW))
        assertEquals("medium", openAiReasoningEffort("gpt-5.2-pro", ThinkingEffort.MINIMAL))
        assertEquals("medium", openAiReasoningEffort("gpt-5.2-pro-2025-12-11", ThinkingEffort.LOW))
        assertEquals("medium", openAiReasoningEffort("gpt-5.2-pro-2025-12-11", ThinkingEffort.MINIMAL))
        assertEquals("xhigh", openAiReasoningEffort("gpt-5.2-pro", ThinkingEffort.MAX))
        assertEquals("low", openAiReasoningEffort("gpt-6.1-sol", ThinkingEffort.MINIMAL))
        assertEquals("minimal", openAiReasoningEffort("gpt-6", ThinkingEffort.MINIMAL))
        assertEquals("xhigh", openAiReasoningEffort("gpt-5.4-mini", ThinkingEffort.MAX))
        assertEquals("high", openAiReasoningEffort("gpt-5-pro", ThinkingEffort.MAX))
        assertEquals("low", openAiReasoningEffort("o3-mini", ThinkingEffort.MINIMAL))
        assertEquals("medium", openAiReasoningEffort("gpt-5.1", ThinkingEffort.MEDIUM))
        assertEquals(
            "xhigh",
            openAiResponsesReasoning(
                platform(ClientType.OPENAI, "gpt-5.2", reasoning = true, thinkingEffort = "max")
            )?.effort
        )
        assertEquals(
            "low",
            openAIChatReasoningFields(
                platform(ClientType.OPENAI, "gpt-5.2-2025-12-11", reasoning = true, thinkingEffort = "minimal")
            ).reasoningEffort
        )
        assertNull(requestThinkingEffort(platform(ClientType.OPENAI, "gpt-4o", reasoning = true, thinkingEffort = "max")))

        assertEquals("high", groqReasoningEffort("openai/gpt-oss-20b", ThinkingEffort.MAX))
        assertEquals("xhigh", groqReasoningEffort("muse-spark-open", ThinkingEffort.MAX))
        assertNull(groqReasoningEffort("qwen/qwen3-32b", ThinkingEffort.HIGH))

        assertEquals("low", anthropicOutputEffort("claude-opus-4-5", ThinkingEffort.MINIMAL))
        assertEquals("high", anthropicOutputEffort("claude-opus-4-5", ThinkingEffort.MAX))
        assertEquals("max", anthropicOutputEffort("claude-sonnet-4-6", ThinkingEffort.MAX))
        assertNull(anthropicOutputEffort("claude-sonnet-4-5", ThinkingEffort.HIGH))

        val gemini3 = googleThinkingConfig(
            platform(ClientType.GOOGLE, "gemini-3.7-flash", reasoning = true, thinkingEffort = "minimal")
        )
        assertEquals("LOW", gemini3?.thinkingLevel)
        assertNull(
            googleThinkingConfig(
                platform(ClientType.GOOGLE, "gemini-2.5-flash-image", reasoning = true, thinkingEffort = "high")
            )
        )
        assertFalse(supportsAdjustableThinkingEffort(ClientType.GOOGLE, "gemini-2.5-flash-image"))
        assertTrue(gemini3?.includeThoughts == true)

        val geminiPro = googleThinkingConfig(
            platform(ClientType.GOOGLE, "gemini-3-pro-preview", reasoning = true, thinkingEffort = "medium")
        )
        assertEquals("HIGH", geminiPro?.thinkingLevel)

        val gemini25 = googleThinkingConfig(
            platform(ClientType.GOOGLE, "gemini-2.5-flash", reasoning = true, thinkingEffort = "low")
        )
        assertEquals(2_048, gemini25?.thinkingBudget)
        assertNull(gemini25?.thinkingLevel)

        val openRouter = openAIChatReasoningFields(
            platform(ClientType.OPENROUTER, "openai/gpt-5.6-sol", reasoning = true, thinkingEffort = "max")
        )
        assertEquals("max", openRouter.reasoning?.effort)
        assertNull(openRouter.reasoningEffort)

        val ollama = openAIChatReasoningFields(
            platform(ClientType.OLLAMA, "gpt-oss", reasoning = true, thinkingEffort = "max")
        )
        assertEquals("high", ollama.reasoningEffort)
        assertNull(ollama.reasoning)

        assertNull(
            openAIChatReasoningFields(
                platform(ClientType.CUSTOM, "gpt-5.6", reasoning = true, thinkingEffort = "high")
            ).reasoningEffort
        )
    }

    @Test
    fun reasoningOffOmitsEveryProviderEffortField() {
        val platform = platform(ClientType.GOOGLE, "gemini-3.7-flash", reasoning = false, thinkingEffort = "max")
        assertNull(requestThinkingEffort(platform))
        assertNull(googleThinkingConfig(platform))
        assertNull(openAiResponsesReasoning(platform.copy(compatibleType = ClientType.OPENAI, model = "gpt-5.6")))
    }

    private fun platform(
        type: ClientType,
        model: String,
        reasoning: Boolean = true,
        thinkingEffort: String = ThinkingEffort.DEFAULT.storageValue
    ) = PlatformV2(
        name = "Provider",
        compatibleType = type,
        apiUrl = "https://provider.example",
        model = model,
        reasoning = reasoning,
        thinkingEffort = thinkingEffort
    )
}
