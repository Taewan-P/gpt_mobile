package dev.chungjungsoo.gptmobile.data.backup

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class CompleteBackupDatabaseTest {
    @Test
    fun reorderedColumnsAndAdditiveDefaultsRestoreByNameWithoutLosingTypedValues() {
        helper("CREATE TABLE records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, body TEXT NOT NULL, payload BLOB, retired TEXT)").use { source ->
            helper("CREATE TABLE records (body TEXT NOT NULL, id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1, payload BLOB, optional TEXT)").use { target ->
                source.writableDatabase.execSQL("INSERT INTO records (id, body, payload, retired) VALUES (?, ?, ?, ?)", arrayOf(41, "Saved message", byteArrayOf(1, 2, 3), "old setting"))
                CompleteBackupDatabase.restore(source.writableDatabase, target.writableDatabase)
                target.writableDatabase.query("SELECT id, body, enabled, payload, optional FROM records").use { rows ->
                    assertTrue(rows.moveToFirst())
                    assertEquals(41, rows.getInt(0))
                    assertEquals("Saved message", rows.getString(1))
                    assertEquals(1, rows.getInt(2))
                    assertArrayEquals(byteArrayOf(1, 2, 3), rows.getBlob(3))
                    assertTrue(rows.isNull(4))
                    assertFalse(rows.moveToNext())
                }
                target.writableDatabase.execSQL("INSERT INTO records(body) VALUES ('Next')")
                target.writableDatabase.query("SELECT MAX(id) FROM records").use { rows ->
                    rows.moveToFirst()
                    assertEquals(42, rows.getInt(0))
                }
            }
        }
    }

    @Test
    fun missingRequiredValuesFailBeforeDeletingCurrentData() {
        helper("CREATE TABLE records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)").use { source ->
            helper("CREATE TABLE records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, body TEXT NOT NULL)").use { target ->
                source.writableDatabase.execSQL("INSERT INTO records(id) VALUES (1)")
                target.writableDatabase.execSQL("INSERT INTO records(id, body) VALUES (2, 'Keep me')")
                val result = runCatching { CompleteBackupDatabase.restore(source.writableDatabase, target.writableDatabase) }
                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull()!!.message!!.contains("required data"))
                target.writableDatabase.query("SELECT body FROM records").use { rows ->
                    rows.moveToFirst()
                    assertEquals("Keep me", rows.getString(0))
                }
            }
        }
    }

    private fun helper(schema: String): SupportSQLiteOpenHelper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .callback(object : SupportSQLiteOpenHelper.Callback(30) {
                override fun onCreate(db: SupportSQLiteDatabase) = db.execSQL(schema)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
    )
}
