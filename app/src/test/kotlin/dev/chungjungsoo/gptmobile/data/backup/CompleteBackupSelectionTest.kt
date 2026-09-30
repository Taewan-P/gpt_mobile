package dev.chungjungsoo.gptmobile.data.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompleteBackupSelectionTest {
    @Test fun clearingSelectAllAlsoClearsDependentConversationSections() {
        var selection = CompleteBackupSelection.ALL
        CompleteBackupSection.entries.forEach { selection = selection.toggled(it, false) }
        assertTrue(selection.sections.isEmpty())
    }

    @Test fun deselectingConversationsDeselectsAttachmentsAndHistory() {
        val selection = CompleteBackupSelection.ALL.toggled(CompleteBackupSection.CONVERSATIONS, false)
        assertFalse(selection.includes(CompleteBackupSection.ATTACHMENTS))
        assertFalse(selection.includes(CompleteBackupSection.AGENT_HISTORY))
    }
}
