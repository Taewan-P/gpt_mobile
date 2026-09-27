package dev.chungjungsoo.gptmobile.data.localmodel

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelCompatibilityTest {
    @Test
    fun `unsafe Gemma 4 Qualcomm exports are blocked before native initialization`() {
        val reason = LocalModelCompatibility.unsupportedReason(
            "litert-community/gemma-4-E2B-it-litert-lm",
            "gemma-4-E2B-it_qualcomm_sm8750.litertlm"
        )

        assertTrue(reason?.contains("crash the Android process") == true)
        assertNull(
            LocalModelCompatibility.unsupportedReason(
                "litert-community/gemma-4-E2B-it-litert-lm",
                "gemma-4-E2B-it.litertlm"
            )
        )
    }
}
