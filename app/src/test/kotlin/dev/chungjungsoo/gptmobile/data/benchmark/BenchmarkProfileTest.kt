package dev.chungjungsoo.gptmobile.data.benchmark

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.openrouter.OpenRouterOptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkProfileTest {
    @Test
    fun `fixture bounds override provider settings without modifying saved profile`() {
        val original = PlatformV2(name = "Remote", compatibleType = ClientType.OPENROUTER, maxTokens = null, reasoning = true, stream = false, openRouterRouting = "{\"temperature\":1.5,\"stream\":false,\"max_tokens\":9999,\"provider\":{\"sort\":\"latency\"}}")
        val bounded = benchmarkProfile(original, true)
        val options = Json.parseToJsonElement(bounded.openRouterRouting!!).jsonObject
        assertEquals("0.0", options.getValue("temperature").jsonPrimitive.content)
        assertEquals("true", options.getValue("stream").jsonPrimitive.content)
        assertEquals("512", options.getValue("max_tokens").jsonPrimitive.content)
        assertEquals("latency", options.getValue("provider").jsonObject.getValue("sort").jsonPrimitive.content)
        assertFalse(bounded.reasoning)
        assertFalse(bounded.disableAllTools)
        assertTrue(bounded.stream)
        assertNull(original.maxTokens)
        assertTrue(original.reasoning)
    }

    @Test
    fun `legacy OpenRouter routing is preserved when adding benchmark settings`() {
        val original = PlatformV2(name = "Legacy", compatibleType = ClientType.OPENROUTER, openRouterRouting = "{\"order\":[\"provider-a\"],\"allow_fallbacks\":false}")
        val result = Json.parseToJsonElement(benchmarkProfile(original, false).openRouterRouting!!).jsonObject
        assertEquals(Json.parseToJsonElement(original.openRouterRouting!!), result["provider"])
    }

    @Test
    fun `Ollama retains hardware settings while forcing deterministic bounded generation`() {
        val original = PlatformV2(name = "Ollama", compatibleType = ClientType.OLLAMA, ollamaOptions = "{\"temperature\":1.2,\"num_thread\":6}")
        val result = Json.parseToJsonElement(benchmarkProfile(original, false).ollamaOptions!!).jsonObject
        assertEquals("0.0", result.getValue("temperature").jsonPrimitive.content)
        assertEquals("6", result.getValue("num_thread").jsonPrimitive.content)
        assertTrue(benchmarkProfile(original, false).disableAllTools)
    }

    @Test
    fun `default OpenRouter routing survives omitted default JSON fields`() {
        val profile = PlatformV2(name = "Default", compatibleType = ClientType.OPENROUTER)
        val result = Json.decodeFromString<OpenRouterOptions>(benchmarkProfile(profile, false).openRouterRouting!!)
        assertEquals(OpenRouterOptions.DEFAULT_PROVIDER, result.provider)
        assertEquals(0f, result.temperature)
    }
}
