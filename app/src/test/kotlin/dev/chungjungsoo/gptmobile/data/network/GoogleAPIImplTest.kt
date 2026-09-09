package dev.chungjungsoo.gptmobile.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleAPIImplTest {
    @Test
    fun `google api root accepts base and versioned urls`() {
        assertEquals("https://generativelanguage.googleapis.com", googleApiRoot("https://generativelanguage.googleapis.com/"))
        assertEquals("https://generativelanguage.googleapis.com", googleApiRoot("https://generativelanguage.googleapis.com/v1beta"))
    }
}
