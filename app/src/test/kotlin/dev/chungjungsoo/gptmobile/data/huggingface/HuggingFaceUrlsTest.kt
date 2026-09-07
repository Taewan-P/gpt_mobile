package dev.chungjungsoo.gptmobile.data.huggingface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HuggingFaceUrlsTest {

    @Test
    fun `model page is the huggingface path before resolve`() {
        assertEquals(
            "https://huggingface.co/litert-community/Gemma3-1B-IT",
            HuggingFaceUrls.modelPageUrl(
                "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/42d538a932e8d5b12e6b3b455f5572560bd60b2c/gemma3-1b-it-int4.litertlm?download=true"
            )
        )
    }

    @Test
    fun `model id is org and repo`() {
        assertEquals(
            "litert-community/Gemma3-1B-IT",
            HuggingFaceUrls.modelId(
                "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/abc/file.litertlm"
            )
        )
    }

    @Test
    fun `license page uses the huggingface model id`() {
        assertEquals(
            "https://huggingface.co/litert-community/Gemma3-1B-IT",
            HuggingFaceUrls.licensePageUrl("litert-community/Gemma3-1B-IT")
        )
    }

    @Test
    fun `non huggingface urls have no model page`() {
        assertNull(HuggingFaceUrls.modelPageUrl("https://example.com/models/gemma.litertlm"))
        assertNull(HuggingFaceUrls.modelId("https://example.com/models/gemma.litertlm"))
    }
}
