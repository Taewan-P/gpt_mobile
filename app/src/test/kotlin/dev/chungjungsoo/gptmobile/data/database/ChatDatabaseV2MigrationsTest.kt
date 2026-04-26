package dev.chungjungsoo.gptmobile.data.database

import dev.chungjungsoo.gptmobile.data.database.entity.AssistantRevisionListConverter
import dev.chungjungsoo.gptmobile.data.database.entity.ChatAttachmentListConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatDatabaseV2MigrationsTest {
    @Test
    fun `legacy file list migrates to attachment json`() {
        val json = ChatDatabaseV2Migrations.legacyFilesToAttachmentsJson("/tmp/first.png,/tmp/second.webp")

        val attachments = ChatAttachmentListConverter().fromString(json)

        assertEquals(2, attachments.size)
        assertEquals("/tmp/first.png", attachments[0].localFilePath)
        assertEquals("/tmp/first.png", attachments[0].preparedFilePath)
        assertEquals("first.png", attachments[0].resolvedDisplayName)
        assertTrue(attachments[0].providerRefs.isEmpty())
        assertEquals("/tmp/second.webp", attachments[1].localFilePath)
    }

    @Test
    fun `legacy revision list migrates to structured revisions`() {
        val json = ChatDatabaseV2Migrations.legacyRevisionsToStructuredJson(
            revisionsValue = "first revision,second revision",
            createdAt = 1234L
        )

        val revisions = AssistantRevisionListConverter().fromString(json)

        assertEquals(2, revisions.size)
        assertEquals("first revision", revisions[0].content)
        assertEquals("", revisions[0].thoughts)
        assertEquals(1234L, revisions[0].createdAt)
        assertEquals("second revision", revisions[1].content)
    }
}
