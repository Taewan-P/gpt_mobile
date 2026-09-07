package dev.chungjungsoo.gptmobile.data.backup

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SanitizedChatBackupInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun createSanitizedCopy_preservesChatsAndClearsPlaintextTokens() {
        val source = File(context.cacheDir, "backup-source.db")
        val destination = File(context.cacheDir, "backup-destination.db")
        source.delete()
        destination.delete()
        SQLiteDatabase.openOrCreateDatabase(source, null).use { database ->
            database.execSQL("CREATE TABLE chats_v2 (chat_id INTEGER PRIMARY KEY, title TEXT NOT NULL)")
            database.execSQL("CREATE TABLE platform_v2 (platform_id INTEGER PRIMARY KEY, token TEXT, secret_ref TEXT)")
            database.execSQL("INSERT INTO chats_v2 VALUES (1, 'Keep me')")
            database.execSQL("INSERT INTO platform_v2 VALUES (1, 'plaintext-secret', NULL)")
        }

        SanitizedChatBackup.createSanitizedCopy(source, destination)

        SQLiteDatabase.openDatabase(destination.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("SELECT title FROM chats_v2", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Keep me", cursor.getString(0))
            }
            database.rawQuery("SELECT token FROM platform_v2", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
            }
        }
        source.delete()
        destination.delete()
    }

    @Test
    fun deleteStagedFile_removesPreviousCopyAndReportsFailure() {
        val staged = SanitizedChatBackup.stagedFile(context)
        staged.parentFile?.mkdirs()
        staged.writeText("stale")

        assertTrue(SanitizedChatBackup.deleteStagedFile(context))
        assertFalse(staged.exists())

        staged.mkdirs()
        File(staged, "blocked").writeText("stale")
        assertFalse(SanitizedChatBackup.deleteStagedFile(context))
        File(staged, "blocked").delete()
        staged.delete()
    }
}
