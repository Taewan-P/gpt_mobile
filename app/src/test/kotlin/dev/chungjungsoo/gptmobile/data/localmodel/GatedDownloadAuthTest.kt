package dev.chungjungsoo.gptmobile.data.localmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatedDownloadAuthTest {

    @Test
    fun `http 200 proceeds regardless of token or gating`() {
        assertEquals(GatedDownloadAction.PROCEED, GatedDownloadAuth.decide(200, hasToken = false, isGated = false).action)
        assertEquals(GatedDownloadAction.PROCEED, GatedDownloadAuth.decide(200, hasToken = true, isGated = true).action)
    }

    @Test
    fun `http 206 proceeds as a successful ranged probe`() {
        val decision = GatedDownloadAuth.decide(206, hasToken = true, isGated = true)
        assertEquals(GatedDownloadAction.PROCEED, decision.action)
        assertFalse(decision.isSessionExpired)
    }

    @Test
    fun `http 401 without a token requests sign-in`() {
        val decision = GatedDownloadAuth.decide(401, hasToken = false, isGated = true)
        assertEquals(GatedDownloadAction.NEEDS_SIGN_IN, decision.action)
        assertFalse(decision.isSessionExpired)
    }

    @Test
    fun `http 401 with a stored token is a session expiry that needs sign-in again`() {
        val decision = GatedDownloadAuth.decide(401, hasToken = true, isGated = true)
        assertEquals(GatedDownloadAction.NEEDS_SIGN_IN, decision.action)
        assertTrue(decision.isSessionExpired)
    }

    @Test
    fun `http 401 on a non-gated url still requests sign-in instead of failing silently`() {
        val decision = GatedDownloadAuth.decide(401, hasToken = true, isGated = false)
        assertEquals(GatedDownloadAction.NEEDS_SIGN_IN, decision.action)
        assertTrue(decision.isSessionExpired)
    }

    @Test
    fun `http 403 with a token requests license agreement`() {
        val decision = GatedDownloadAuth.decide(403, hasToken = true, isGated = true)
        assertEquals(GatedDownloadAction.NEEDS_LICENSE, decision.action)
        assertFalse(decision.isSessionExpired)
    }

    @Test
    fun `http 403 without a token on a gated entry requests sign-in first`() {
        val decision = GatedDownloadAuth.decide(403, hasToken = false, isGated = true)
        assertEquals(GatedDownloadAction.NEEDS_SIGN_IN, decision.action)
        assertFalse(decision.isSessionExpired)
    }

    @Test
    fun `http 403 without a token on a non-gated entry is an error`() {
        val decision = GatedDownloadAuth.decide(403, hasToken = false, isGated = false)
        assertEquals(GatedDownloadAction.ERROR, decision.action)
        assertFalse(decision.isSessionExpired)
    }

    @Test
    fun `gated entry with no token treats unauthorized-class failures as sign-in`() {
        assertEquals(
            GatedDownloadAction.NEEDS_SIGN_IN,
            GatedDownloadAuth.decide(401, hasToken = false, isGated = true).action
        )
    }

    @Test
    fun `http status maps to a distinct download failure kind`() {
        assertEquals(
            DownloadFailureKind.SESSION_EXPIRED,
            DownloadFailureKind.fromHttp(401, hasToken = true, isGated = true)
        )
        assertEquals(
            DownloadFailureKind.AUTH_REQUIRED,
            DownloadFailureKind.fromHttp(401, hasToken = false, isGated = true)
        )
        assertEquals(
            DownloadFailureKind.LICENSE_REQUIRED,
            DownloadFailureKind.fromHttp(403, hasToken = true, isGated = true)
        )
        assertEquals(
            DownloadFailureKind.AUTH_REQUIRED,
            DownloadFailureKind.fromHttp(403, hasToken = false, isGated = true)
        )
        assertEquals(
            DownloadFailureKind.GENERIC,
            DownloadFailureKind.fromHttp(500, hasToken = true, isGated = true)
        )
        assertEquals(
            DownloadFailureKind.SESSION_EXPIRED,
            DownloadFailureKind.fromWorkOutput("SESSION_EXPIRED")
        )
        assertEquals(DownloadFailureKind.GENERIC, DownloadFailureKind.fromWorkOutput(null))
    }

    @Test
    fun `unexpected status codes are errors`() {
        assertEquals(GatedDownloadAction.ERROR, GatedDownloadAuth.decide(404, hasToken = true, isGated = true).action)
        assertEquals(GatedDownloadAction.ERROR, GatedDownloadAuth.decide(500, hasToken = false, isGated = false).action)
        assertEquals(GatedDownloadAction.ERROR, GatedDownloadAuth.decide(-1, hasToken = false, isGated = true).action)
    }
}
