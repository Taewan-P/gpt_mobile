package dev.chungjungsoo.gptmobile.data.localmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelDownloadPathsTest {

    @Test
    fun `partial file name appends part suffix`() {
        assertEquals(
            "Qwen2.5-1.5B-Instruct.litertlm.part",
            LocalModelDownloadPaths.partialFileName("Qwen2.5-1.5B-Instruct.litertlm")
        )
    }

    @Test
    fun `final file name strips part suffix`() {
        assertEquals(
            "Qwen2.5-1.5B-Instruct.litertlm",
            LocalModelDownloadPaths.finalFileNameFromPartial("Qwen2.5-1.5B-Instruct.litertlm.part")
        )
    }

    @Test
    fun `final file name leaves already-final names unchanged`() {
        assertEquals(
            "model.litertlm",
            LocalModelDownloadPaths.finalFileNameFromPartial("model.litertlm")
        )
    }

    @Test
    fun `relative directory is commit-hash scoped under models`() {
        assertEquals(
            "models/qwen2.5-1.5b-instruct/19edb84c69a0212f29a6ef17ba0d6f278b6a1614",
            LocalModelDownloadPaths.relativeDirectory(
                catalogEntryId = "qwen2.5-1.5b-instruct",
                commitHash = "19edb84c69a0212f29a6ef17ba0d6f278b6a1614"
            )
        )
    }

    @Test
    fun `relative file paths land in the commit-hash directory`() {
        assertEquals(
            "models/qwen2.5-1.5b-instruct/19edb84c69a0212f29a6ef17ba0d6f278b6a1614/model.litertlm",
            LocalModelDownloadPaths.relativeFilePath(
                catalogEntryId = "qwen2.5-1.5b-instruct",
                commitHash = "19edb84c69a0212f29a6ef17ba0d6f278b6a1614",
                fileName = "model.litertlm"
            )
        )
        assertEquals(
            "models/qwen2.5-1.5b-instruct/19edb84c69a0212f29a6ef17ba0d6f278b6a1614/model.litertlm.part",
            LocalModelDownloadPaths.relativePartialFilePath(
                catalogEntryId = "qwen2.5-1.5b-instruct",
                commitHash = "19edb84c69a0212f29a6ef17ba0d6f278b6a1614",
                fileName = "model.litertlm"
            )
        )
    }

    @Test
    fun `rewriteResolveUrl swaps the file name and keeps the pinned commit`() {
        val rewritten = LocalModelDownloadPaths.rewriteResolveUrl(
            baseUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/42d538a932e8d5b12e6b3b455f5572560bd60b2c/gemma3-1b-it-int4.litertlm?download=true",
            fileName = "Gemma3-1B-IT_q4_ekv1280_sm8750.litertlm"
        )

        assertEquals(
            "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/42d538a932e8d5b12e6b3b455f5572560bd60b2c/Gemma3-1B-IT_q4_ekv1280_sm8750.litertlm?download=true",
            rewritten
        )
    }

    @Test
    fun `rewriteResolveUrl can also pin a different commit hash`() {
        val rewritten = LocalModelDownloadPaths.rewriteResolveUrl(
            baseUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/42d538a932e8d5b12e6b3b455f5572560bd60b2c/gemma3-1b-it-int4.litertlm?download=true",
            fileName = "Gemma3-1B-IT_q4_ekv1280_sm8750.litertlm",
            commitHash = "6d54daa71cfbffba6b2843c08eeb1a27e7430bf0"
        )

        assertEquals(
            "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/6d54daa71cfbffba6b2843c08eeb1a27e7430bf0/Gemma3-1B-IT_q4_ekv1280_sm8750.litertlm?download=true",
            rewritten
        )
    }

    @Test
    fun `commit hash and file name are parsed from a huggingface resolve url`() {
        val url = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/19edb84c69a0212f29a6ef17ba0d6f278b6a1614/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true"

        assertEquals(
            "19edb84c69a0212f29a6ef17ba0d6f278b6a1614",
            LocalModelDownloadPaths.commitHashFromUrl(url)
        )
        assertEquals(
            "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            LocalModelDownloadPaths.fileNameFromUrl(url)
        )
    }

    @Test
    fun `zero partial length produces no resume headers`() {
        assertEquals(emptyMap<String, String>(), LocalModelDownloadPaths.resumeHeaders(0L))
        assertNull(LocalModelDownloadPaths.rangeHeaderValue(0L))
    }

    @Test
    fun `partial length produces byte-range resume headers with identity encoding`() {
        assertEquals("bytes=1048576-", LocalModelDownloadPaths.rangeHeaderValue(1_048_576L))
        assertEquals(
            mapOf(
                "Range" to "bytes=1048576-",
                "Accept-Encoding" to "identity"
            ),
            LocalModelDownloadPaths.resumeHeaders(1_048_576L)
        )
    }

    @Test
    fun `resume appends only when the server accepted the partial range`() {
        assertFalse(LocalModelDownloadPaths.shouldAppendToPartial(partialLength = 0L, contentRangeHeader = "bytes 0-99/100"))
        assertFalse(LocalModelDownloadPaths.shouldAppendToPartial(partialLength = 1024L, contentRangeHeader = null))
        assertTrue(
            LocalModelDownloadPaths.shouldAppendToPartial(
                partialLength = 1024L,
                contentRangeHeader = "bytes 1024-2047/2048"
            )
        )
        assertFalse(
            LocalModelDownloadPaths.shouldAppendToPartial(
                partialLength = 1024L,
                contentRangeHeader = "bytes 0-2047/2048"
            )
        )
    }

    @Test
    fun `completed download size must match the known total`() {
        assertTrue(LocalModelDownloadPaths.isCompleteDownload(tmpLength = 2048L, totalBytes = 2048L))
        assertTrue(LocalModelDownloadPaths.isCompleteDownload(tmpLength = 100L, totalBytes = 0L))
        assertFalse(LocalModelDownloadPaths.isCompleteDownload(tmpLength = 1024L, totalBytes = 2048L))
    }

    @Test
    fun `path segments reject separators parent traversal and empty values`() {
        assertTrue(LocalModelDownloadPaths.isValidPathSegment("qwen2.5-1.5b-instruct"))
        assertTrue(LocalModelDownloadPaths.isValidPathSegment("19edb84c69a0212f29a6ef17ba0d6f278b6a1614"))
        assertTrue(LocalModelDownloadPaths.isValidPathSegment("model.litertlm"))
        assertFalse(LocalModelDownloadPaths.isValidPathSegment(""))
        assertFalse(LocalModelDownloadPaths.isValidPathSegment(".."))
        assertFalse(LocalModelDownloadPaths.isValidPathSegment("foo/bar"))
        assertFalse(LocalModelDownloadPaths.isValidPathSegment("foo\\bar"))
    }

    @Test
    fun `relative paths reject invalid segments before construction`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            LocalModelDownloadPaths.relativeDirectory("../evil", "abc123")
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            LocalModelDownloadPaths.relativeFilePath("qwen", "abc123", "..")
        }
    }

    @Test
    fun `downloaded bytes resume at the content-range start`() {
        assertEquals(
            1024L,
            LocalModelDownloadPaths.downloadedBytesAfterConnect(
                partialLength = 1024L,
                contentRangeHeader = "bytes 1024-2047/2048"
            )
        )
        assertEquals(
            0L,
            LocalModelDownloadPaths.downloadedBytesAfterConnect(
                partialLength = 1024L,
                contentRangeHeader = null
            )
        )
    }

    @Test
    fun `isPartialFile detects the part suffix`() {
        assertTrue(LocalModelDownloadPaths.isPartialFile("model.litertlm.part"))
        assertFalse(LocalModelDownloadPaths.isPartialFile("model.litertlm"))
    }
}
