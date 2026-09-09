package dev.chungjungsoo.gptmobile.data.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteContextWindowParserTest {
    @Test
    fun `gemini uses documented inputTokenLimit`() {
        val body = """{"name":"models/gemini-2.5-flash","inputTokenLimit":1048576,"outputTokenLimit":65536}"""
        assertEquals(1048576, RemoteContextWindowParser.geminiInputTokenLimit(body))
        assertNull(RemoteContextWindowParser.geminiInputTokenLimit("""{"outputTokenLimit":65536}"""))
    }

    @Test
    fun `openrouter uses documented context_length`() {
        val body = """{"data":[{"id":"openai/gpt-5.6-sol","context_length":200000},{"id":"other","context_length":1}]}"""
        assertEquals(200000, RemoteContextWindowParser.openRouterContextLength(body, "openai/gpt-5.6-sol"))
        assertNull(RemoteContextWindowParser.openRouterContextLength("""{"data":[]}""", "missing"))
        assertNull(
            RemoteContextWindowParser.openRouterContextLength(
                """{"data":[{"id":"openai/gpt-5.6-sol","top_provider":{"max_completion_tokens":128000}}]}""",
                "openai/gpt-5.6-sol"
            )
        )
    }

    @Test
    fun `model parser ignores malformed envelopes`() {
        listOf(
            """{"data":{}}""",
            """{"data":[1]}""",
            """{"id":{}}"""
        ).forEach { body ->
            assertNull(RemoteContextWindowParser.openRouterContextLength(body, "model"))
            assertNull(RemoteContextWindowParser.groqContextWindow(body, "model"))
            assertNull(RemoteContextWindowParser.anthropicMaxInputTokens(body, "model"))
        }
    }

    @Test
    fun `groq uses documented context_window and ignores output limits`() {
        val body = """{"id":"llama-3.1-8b-instant","object":"model","context_window":131072,"max_completion_tokens":8192}"""
        assertEquals(131072, RemoteContextWindowParser.groqContextWindow(body, "llama-3.1-8b-instant"))
        val listBody = """{"data":[{"id":"llama-3.1-8b-instant","context_window":131072,"max_completion_tokens":8192},{"id":"other","context_window":1}]}"""
        assertEquals(131072, RemoteContextWindowParser.groqContextWindow(listBody, "llama-3.1-8b-instant"))
        assertNull(
            RemoteContextWindowParser.groqContextWindow(
                """{"id":"llama-3.1-8b-instant","max_completion_tokens":8192}""",
                "llama-3.1-8b-instant"
            )
        )
    }

    @Test
    fun `anthropic uses documented max_input_tokens and ignores max_tokens`() {
        val body = """{"id":"claude-opus-5","type":"model","max_input_tokens":200000,"max_tokens":32000}"""
        assertEquals(200000, RemoteContextWindowParser.anthropicMaxInputTokens(body, "claude-opus-5"))
        assertNull(
            RemoteContextWindowParser.anthropicMaxInputTokens(
                """{"id":"claude-opus-5","type":"model","max_tokens":32000}""",
                "claude-opus-5"
            )
        )
    }

    @Test
    fun `ollama uses model_info context_length and num_ctx`() {
        val modelInfo = """{"model_info":{"llama.context_length":8192},"parameters":"stop x"}"""
        assertEquals(8192, RemoteContextWindowParser.ollamaContextLength(modelInfo))
        val parameters = """{"parameters":"num_ctx 4096\nstop x"}"""
        assertEquals(4096, RemoteContextWindowParser.ollamaContextLength(parameters))
        assertNull(RemoteContextWindowParser.ollamaContextLength("""{"modelfile":"FROM x"}"""))
    }
}
