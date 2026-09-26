package dev.chungjungsoo.gptmobile.data.network

import java.net.URI
import java.net.URLDecoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PollinationsTransportTest {
    @Test fun `a transient error retries the same service once and returns only the answer`() = runTest {
        var calls = 0
        val result = pollinationsCompletion {
            calls++
            if (calls == 1) PollinationsResponse(503, "Unavailable") else PollinationsResponse(200, "Recovered", "text/plain")
        }
        assertEquals("Recovered", result)
        assertEquals(2, calls)
    }

    @Test fun `server storage failures identify the upstream cause after bounded retry`() = runTest {
        var calls = 0
        val error = runCatching {
            pollinationsCompletion {
                calls++
                PollinationsResponse(500, "{\"error\":\"ENOSPC: no space left on device\",\"status\":500}")
            }
        }.exceptionOrNull()
        assertEquals(2, calls)
        assertTrue(error!!.message!!.contains("server storage"))
        assertTrue(error.message!!.contains("Your phone's storage is not the cause"))
    }

    @Test fun `nontransient failures and cancellations are not retried`() = runTest {
        for (code in listOf(401, 403, 414)) {
            var calls = 0
            assertTrue(
                runCatching {
                    pollinationsCompletion {
                        calls++
                        PollinationsResponse(code, "Denied")
                    }
                }.isFailure
            )
            assertEquals(1, calls)
        }
        var calls = 0
        val error = runCatching {
            pollinationsCompletion {
                calls++
                throw CancellationException("stop")
            }
        }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertEquals(1, calls)
    }

    @Test fun `service pages and error envelopes never appear as successful answers`() = runTest {
        for (response in listOf(PollinationsResponse(200, "<html>Offline</html>", "text/html"), PollinationsResponse(200, "{\"error\":\"Unavailable\",\"status\":500}"), PollinationsResponse(200, "  "))) {
            assertTrue(runCatching { pollinationsCompletion { response } }.isFailure)
        }
        assertEquals("{\"answer\":\"fine\"}", pollinationsCompletion { PollinationsResponse(200, "{\"answer\":\"fine\"}") })
    }

    @Test fun `canonical anonymous model and encoded prompt cannot change query parameters`() {
        val prompt = "user: What is A & B? #test + café\n\nassistant:"
        val url = URI(pollinationsPromptUrl(prompt))
        assertEquals("model=openai-fast", url.query)
        assertEquals(prompt, URLDecoder.decode(url.rawPath.removePrefix("/"), "UTF-8"))
        assertThrows(IllegalArgumentException::class.java) { pollinationsPromptUrl("漢".repeat(4000)) }
    }
}
