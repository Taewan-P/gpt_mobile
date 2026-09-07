package dev.chungjungsoo.gptmobile.data.localmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HuggingFaceDownloadAuthTest {
    @Test
    fun `bearer token attaches only to https huggingface hosts`() {
        assertTrue(HuggingFaceDownloadAuth.shouldAttachBearerToken("https://huggingface.co/org/model/resolve/main/file"))
        assertTrue(HuggingFaceDownloadAuth.shouldAttachBearerToken("https://cas-bridge.huggingface.co/xet-bridge"))
        assertFalse(HuggingFaceDownloadAuth.shouldAttachBearerToken("http://huggingface.co/org/model"))
        assertFalse(HuggingFaceDownloadAuth.shouldAttachBearerToken("https://evil-huggingface.co/org/model"))
        assertFalse(HuggingFaceDownloadAuth.shouldAttachBearerToken("https://cdn.example.com/file"))
        assertFalse(HuggingFaceDownloadAuth.shouldAttachBearerToken("http://127.0.0.1:1/model"))
    }
}
