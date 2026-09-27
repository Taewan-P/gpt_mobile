package dev.chungjungsoo.gptmobile.data.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLogRedactionTest {
    @Test fun `headers json credentials key prefixes and URL secrets are redacted`() {
        val raw = "Authorization: Bearer dangerous-token\nCookie: session=private\nX-Subscription-Token: brave-secret\n{\"api_key\":\"secret-value\"}\nhttps://example.com/search?token=url-secret\nhf_abcdefghijklmnop\nHTTP 500 request failed"
        val safe = redactLogMessage(raw)
        listOf("dangerous-token", "session=private", "brave-secret", "secret-value", "url-secret", "hf_abcdefghijklmnop").forEach { assertFalse(it, safe.contains(it)) }
        assertTrue(safe.contains("HTTP 500 request failed"))
    }
}
