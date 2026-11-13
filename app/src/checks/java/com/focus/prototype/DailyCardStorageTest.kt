package com.focus.prototype

import android.graphics.Bitmap
import android.content.Context
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.time.LocalDateTime
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class DailyCardStorageTest {
    private val date = "2026-11-12"

    private fun database(): Database = Database(RuntimeEnvironment.getApplication(), "card-${UUID.randomUUID()}.db") {
        LocalDateTime.of(2026, 11, 12, 12, 0)
    }

    @Test fun completionAutomaticallySavesOneStableCardAndUndoInvalidatesIt() {
        database().use { db ->
            db.addTask(date, "完成报告", true, 0, "行动型", "", 0, 0, "once")
            val task = db.tasks(date).single()
            db.writableDatabase.execSQL("UPDATE tasks SET done=1,elapsed=240000 WHERE id=?", arrayOf(task.id))
            val created = DailyCards.reconcile(db, date)!!
            assertTrue(created.saved)
            assertFalse(created.revealSeen)
            assertEquals(1, DailyCards.read(db).size)
            assertEquals(listOf(task.id), created.selectedTaskIds)
            assertTrue(created.quote.isNotBlank())
            assertEquals(created, DailyCards.reconcile(db, date))
            DailyCards.markRevealSeen(db, date)
            assertEquals(1, DailyCards.read(db).size)
            assertEquals(4, DailyCards.read(db).single().focusMinutes)
            assertTrue(DailyCards.read(db).single().revealSeen)
            db.writableDatabase.execSQL("UPDATE tasks SET done=0 WHERE id=?", arrayOf(task.id))
            assertNull(DailyCards.reconcile(db, date))
            assertTrue(DailyCards.read(db).isEmpty())
            db.writableDatabase.execSQL("UPDATE tasks SET done=1 WHERE id=?", arrayOf(task.id))
            val restored = DailyCards.reconcile(db, date)!!
            assertEquals(1, DailyCards.read(db).size)
            assertEquals(created.quote, restored.quote)
            assertEquals(created.edition, restored.edition)
            assertTrue(restored.revealSeen)
        }
    }

    @Test fun photoSnapshotSurvivesSourceRemovalAndBackupRestore() {
        val context: Context = RuntimeEnvironment.getApplication()
        database().use { db ->
            db.addTask(date, "照片任务", true, 0, "行动型", "", 0, 0, "once")
            val task = db.tasks(date).single()
            db.writableDatabase.execSQL("UPDATE tasks SET done=1 WHERE id=?", arrayOf(task.id))
            val source = File(context.cacheDir, "card-source-${UUID.randomUUID()}.png")
            source.outputStream().use { Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
            val id = DailyCards.snapshotPhoto(context, db, date, source.absolutePath)
            DailyCards.save(db, DailyCards.reconcile(db, date)!!.copy(visual = "photo", photoId = id))
            source.delete()
            assertTrue(File(DailyCards.photoPath(db, id)!!).isFile)
            val backup = Backup.export(context, db)
            Backup.restore(context, db, backup)
            assertTrue(File(DailyCards.photoPath(db, DailyCards.read(db).single().photoId)!!).isFile)
        }
    }

    @Test fun olderCardIsStillSaved() {
        database().use { db ->
            db.addTask(date, "旧任务", true, 0, "行动型", "", 0, 0, "once")
            db.writableDatabase.execSQL("UPDATE tasks SET done=1")
            db.setSetting("daily_card_$date", JSONObject().put("version", 2).put("date", date)
                .put("quote", "旧引文").put("valid", true).put("completed", 1).put("total", 1).toString())
            DailyCards.reconcile(db, date)
            assertTrue(DailyCards.read(db).single().saved)
            assertEquals("旧引文", DailyCards.read(db).single().quote)
        }
    }
}
