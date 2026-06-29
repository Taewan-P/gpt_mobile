package dev.chungjungsoo.gptmobile.data.dto

import dev.chungjungsoo.gptmobile.data.dto.anthropic.common.ImageSource
import dev.chungjungsoo.gptmobile.data.dto.google.common.Content
import dev.chungjungsoo.gptmobile.data.dto.google.common.Part
import dev.chungjungsoo.gptmobile.data.dto.google.request.GenerateContentRequest
import dev.chungjungsoo.gptmobile.data.dto.google.request.SafetySetting
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponseContentPart
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderAttachmentSerializationTest {
    private val json = Json {
        encodeDefaults = false
        explicitNulls = false
    }

    @Test
    fun `openai response image file serializes file_id without image_url`() {
        val payload = json.encodeToString(ResponseContentPart.imageFile("file-123"))

        assertTrue(payload.contains("\"file_id\":\"file-123\""))
        assertFalse(payload.contains("image_url"))
    }

    @Test
    fun `anthropic image file serializes file_id without base64 fields`() {
        val payload = json.encodeToString(ImageSource.file("file_123"))

        assertTrue(payload.contains("\"file_id\":\"file_123\""))
        assertFalse(payload.contains("media_type"))
        assertFalse(payload.contains("\"data\""))
    }

    @Test
    fun `google file part serializes file_uri`() {
        val payload = json.encodeToString(Part.fileData("image/png", "google-uri"))

        assertTrue(payload.contains("\"file_data\""))
        assertTrue(payload.contains("\"file_uri\":\"google-uri\""))
        assertFalse(payload.contains("inline_data"))
    }

    @Test
    fun `google generate content request serializes safety settings`() {
        val payload = json.encodeToString(
            GenerateContentRequest(
                contents = listOf(
                    Content(parts = listOf(Part.text("hello")))
                ),
                safetySettings = listOf(
                    SafetySetting(
                        category = "HARM_CATEGORY_HARASSMENT",
                        threshold = "BLOCK_NONE"
                    )
                )
            )
        )

        assertTrue(payload.contains("\"safetySettings\""))
        assertTrue(payload.contains("\"category\":\"HARM_CATEGORY_HARASSMENT\""))
        assertTrue(payload.contains("\"threshold\":\"BLOCK_NONE\""))
    }
}
