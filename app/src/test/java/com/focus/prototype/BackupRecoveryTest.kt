package com.focus.prototype

import android.database.sqlite.SQLiteDatabase
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class BackupRecoveryTest {
    private val context: Context get() = org.robolectric.RuntimeEnvironment.getApplication()
    private fun db(name: String) = Database(context, name, now = { LocalDateTime.of(2026,11,17,12,0) })

    @Test fun suppliedPhoneCopyMigratesAndRestoresWithoutChangingOriginal() {
        val source = System.getenv("FORWARD_RECOVERY_FIXTURE")
        org.junit.Assume.assumeTrue(!source.isNullOrBlank() && java.io.File(source).isFile)
        val input=java.io.File(source!!); val original=input.readBytes()
        val name="phone-copy-${UUID.randomUUID()}.db"
        val file=context.getDatabasePath(name);file.parentFile?.mkdirs();file.writeBytes(original)
        val migrated=db(name)
        try {
            val before=migrated.readableDatabase.rawQuery("SELECT COUNT(*),SUM(done),SUM(elapsed),SUM(CASE WHEN elapsed IS NULL THEN 1 ELSE 0 END) FROM tasks",null).use { it.moveToFirst();listOf(it.getLong(0),it.getLong(1),it.getLong(2),it.getLong(3)) }
            val xp=migrated.xp();val text=Backup.export(context,migrated)
            assertEquals(24L,before[0]);assertEquals(22L,before[1]);assertEquals(2200,xp)
            assertEquals(24,Backup.inspect(text).tasks);assertEquals(22,Backup.inspect(text).completedTasks);assertEquals(xp,Backup.inspect(text).xp)
            assertTrue(before[3]>0)
            val current=migrated.tasks("2026-11-17").filter { it.title in setOf("差分两节","DMA 学习") }
            assertEquals(2,current.size);assertTrue(current.all { !it.done && it.minutes==120 && it.elapsed==3600000L })
            val target=db("phone-target-${UUID.randomUUID()}.db")
            try {
                Backup.restore(context,target,text)
                val after=target.readableDatabase.rawQuery("SELECT COUNT(*),SUM(done),SUM(elapsed),SUM(CASE WHEN elapsed IS NULL THEN 1 ELSE 0 END) FROM tasks",null).use { it.moveToFirst();listOf(it.getLong(0),it.getLong(1),it.getLong(2),it.getLong(3)) }
                assertEquals(before,after);assertEquals(xp,target.xp());assertArrayEquals(original,input.readBytes())
                assertEquals(0,target.readableDatabase.rawQuery("SELECT COUNT(*) FROM ledger",null).use {it.moveToFirst();it.getInt(0)})
            } finally { target.close() }
        } finally { migrated.close() }
    }

    @Test fun versionSevenReconstructionMigratesUnknownsAndRoundTrips() {
        val name="recovery-${UUID.randomUUID()}.db"
        val initial=db(name)
        initial.addTask("2026-11-09", "历史任务", false, 0, "行动型", "根据复盘重建：原始完成时间、实际专注时长及任务类型未知。", 0,0,"once")
        val task=initial.tasks("2026-11-09").single()
        initial.writableDatabase.update("tasks",android.content.ContentValues().apply { put("done",1) },"id=?",arrayOf(task.id))
        val sql=initial.writableDatabase
        sql.beginTransaction()
        try {
            sql.execSQL("CREATE TABLE tasks_v7(id TEXT PRIMARY KEY, day TEXT NOT NULL, title TEXT NOT NULL, required INTEGER NOT NULL, minutes INTEGER NOT NULL, mode TEXT NOT NULL, checklist TEXT NOT NULL, reward_minutes INTEGER NOT NULL, reward_games INTEGER NOT NULL, elapsed INTEGER NOT NULL DEFAULT 0, running_since INTEGER NOT NULL DEFAULT 0, boot_time INTEGER NOT NULL DEFAULT 0, evidence TEXT NOT NULL DEFAULT '', done INTEGER NOT NULL DEFAULT 0, claimed INTEGER NOT NULL DEFAULT 0, recurrence TEXT NOT NULL DEFAULT 'once', origin TEXT NOT NULL DEFAULT '', completed_at INTEGER NOT NULL DEFAULT 0, template_id TEXT NOT NULL DEFAULT '')")
            val cols="id,day,title,required,minutes,mode,checklist,reward_minutes,reward_games,elapsed,running_since,boot_time,evidence,done,claimed,recurrence,origin,completed_at,template_id"
            sql.execSQL("INSERT INTO tasks_v7 ($cols) SELECT $cols FROM tasks")
            sql.execSQL("DROP TABLE tasks");sql.execSQL("ALTER TABLE tasks_v7 RENAME TO tasks")
            sql.version=7;sql.setTransactionSuccessful()
        } finally { sql.endTransaction();initial.close() }
        val migrated=db(name)
        try {
            val restored=migrated.tasks("2026-11-09").single()
            assertFalse(restored.typeKnown);assertFalse(restored.elapsedKnown);assertEquals("每日复盘",restored.historySource)
            assertEquals(DailyGoalState.UNKNOWN,migrated.goalState("2026-11-09"));assertEquals(100,migrated.xp())
            val backup=Backup.export(context,migrated);assertEquals(100,Backup.inspect(backup).xp)
            val target=db("target-${UUID.randomUUID()}.db")
            try {
                target.addTask("2026-11-17","替换前",true,0,"行动型","",0,0,"once")
                Backup.restore(context,target,backup)
                val copy=target.tasks("2026-11-09").single();assertFalse(copy.typeKnown);assertFalse(copy.elapsedKnown);assertEquals(100,target.xp())
                assertTrue(target.tasks("2026-11-17").isEmpty())
                target.confirmHistoricalType(copy,false);assertTrue(target.tasks("2026-11-09").single().typeKnown)
            } finally { target.close() }
        } finally { migrated.close() }
    }

    @Test fun rejectedInsertRollsBackReplacement() {
        db("rollback-source.db").use { source ->
            source.addTask("2026-11-17", "Incoming", false, 0, "行动型", "", 0, 0, "once")
            val snapshot = Backup.export(context, source)
            db("rollback-target.db").use { target ->
                target.addTask("2026-11-17", "Keep me", false, 0, "行动型", "", 0, 0, "once")
                target.setSetting("rollback_marker", "original")
                val existing = target.tasks().single()
                target.writableDatabase.execSQL(
                    "CREATE TRIGGER reject_restore BEFORE INSERT ON tasks BEGIN SELECT RAISE(ABORT, 'test rollback'); END"
                )
                val failure = runCatching { Backup.restore(context, target, snapshot) }.exceptionOrNull()
                assertNotNull(failure)
                assertEquals(existing.id, target.tasks().single().id)
                assertEquals("original", target.setting("rollback_marker"))
            }
        }
    }

    @Test fun unknownAndConfirmedZeroRemainDifferent() {
        val target=db("zero-${UUID.randomUUID()}.db")
        try {
            target.addTask("2026-11-17","明确零",false,0,"行动型","",0,0,"once")
            val row=target.tasks().single();assertTrue(row.elapsedKnown);assertEquals(0L,row.elapsed)
            target.writableDatabase.execSQL("UPDATE tasks SET elapsed=NULL, type_known=0 WHERE id=?",arrayOf(row.id))
            val unknown=target.tasks().single();assertFalse(unknown.elapsedKnown);assertFalse(unknown.typeKnown)
            val backup=Backup.export(context,target);Backup.restore(context,target,backup)
            assertFalse(target.tasks().single().elapsedKnown);assertFalse(target.tasks().single().typeKnown)
        } finally { target.close() }
    }
}
