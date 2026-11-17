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
        assertEquals(ThinkingEffort.LOW, overridden.selectedEffort)
        assertTrue(overridden.adjustedToNearest)
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
    fun openModelsUseTheGenericSetAndLocalRuntimesStayHidden() {
        assertEquals(
            listOf(ThinkingEffort.LOW, ThinkingEffort.MEDIUM, ThinkingEffort.HIGH),
            automaticOfferedThinkingEfforts(ClientType.CUSTOM, "gpt-5.6")
        )
        assertEquals(
            listOf(ThinkingEffort.LOW, ThinkingEffort.MEDIUM, ThinkingEffort.HIGH),
            automaticOfferedThinkingEfforts(ClientType.GROQ, "llama-3")
        )
        assertTrue(supportsAdjustableThinkingEffort(ClientType.OLLAMA, "llama3"))
        assertTrue(supportsAdjustableThinkingEffort(ClientType.OPENROUTER, "any-model"))
        assertFalse(supportsAdjustableThinkingEffort(ClientType.LITERT_LM, "gemma-3"))
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

        assertEquals("max", groqReasoningEffort("openai/gpt-oss-20b", ThinkingEffort.MAX))
        assertEquals("max", groqReasoningEffort("muse-spark-open", ThinkingEffort.MAX))
        assertEquals("high", groqReasoningEffort("qwen/qwen3-32b", ThinkingEffort.HIGH))

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
        assertEquals("high", openRouter.reasoning?.effort)
        assertNull(openRouter.reasoningEffort)

        val ollama = openAIChatReasoningFields(
            platform(ClientType.OLLAMA, "gpt-oss", reasoning = true, thinkingEffort = "max")
        )
        assertEquals("high", ollama.reasoningEffort)
        assertNull(ollama.reasoning)

        assertEquals(
            "high",
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

    @Test
    fun pickersOfferOnlyLevelsTheSelectedModelCanSend() {
        assertEquals(
            listOf(ThinkingEffort.LOW, ThinkingEffort.MEDIUM, ThinkingEffort.HIGH, ThinkingEffort.MAX),
            offeredThinkingEfforts(platform(ClientType.OPENAI, "gpt-5.2"))
        )
        assertEquals(
            listOf(ThinkingEffort.HIGH),
            offeredThinkingEfforts(platform(ClientType.OPENAI, "gpt-5-pro"))
        )
        assertEquals(
            listOf(ThinkingEffort.LOW, ThinkingEffort.HIGH),
            offeredThinkingEfforts(platform(ClientType.GOOGLE, "gemini-3-pro"))
        )
        assertEquals(
            listOf(ThinkingEffort.LOW, ThinkingEffort.MEDIUM, ThinkingEffort.HIGH),
            offeredThinkingEfforts(platform(ClientType.GROQ, "openai/gpt-oss-20b"))
        )
        val widened = platform(ClientType.OLLAMA, "llama3").copy(supportedEffortLevels = "minimal,max")
        assertEquals(listOf(ThinkingEffort.MINIMAL, ThinkingEffort.MAX), offeredThinkingEfforts(widened))
    }

    @Test
    fun storedEffortStaysPutAndTheNearestLevelIsWhatGetsSent() {
        val profile = platform(ClientType.OPENAI, "gpt-5-pro", thinkingEffort = "medium")
        assertEquals(ThinkingEffort.HIGH, nearestThinkingEffort(ThinkingEffort.MEDIUM, offeredThinkingEfforts(profile)))
        assertEquals(ThinkingEffort.HIGH, requestThinkingEffort(profile))
        assertEquals("medium", profile.thinkingEffort)
        val remembered = profile.copy(effortUnsupportedModels = listOf("gpt-5-pro"))
        assertTrue(offeredThinkingEfforts(remembered).isEmpty())
        assertNull(requestThinkingEffort(remembered))
        val omitted = profile.copy().also { it.omitThinkingEffort = true }
        assertNull(requestThinkingEffort(omitted))
    }

    @Test
    fun effortRejectionRequiresTheParameterNameAndA400Or422() {
        assertTrue(
            thinkingEffortParameterRejected(
                "HTTP 400: Unknown parameter: 'reasoning_effort'. Additional properties are not allowed."
            )
        )
        assertTrue(thinkingEffortParameterRejected("HTTP 422: output_config.effort is not supported"))
        assertTrue(thinkingEffortParameterRejected("thinkingLevel is not supported by this model"))
        assertFalse(thinkingEffortParameterRejected("HTTP 400: Invalid API key"))
        assertFalse(thinkingEffortParameterRejected("HTTP 429: reasoning_effort is not supported right now"))
        assertFalse(thinkingEffortParameterRejected("HTTP 400: reasoning_effort must be one of low, medium, high"))
        assertFalse(thinkingEffortParameterRejected("HTTP 400: the model does not support tools"))
        assertFalse(thinkingEffortParameterRejected("HTTP 500: reasoning_effort is not supported"))
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
