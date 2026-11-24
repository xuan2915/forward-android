package com.focus.prototype

import android.content.Context
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class GrowthFoundationIntegrationTest {
    private val zone = ZoneId.systemDefault()
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: Database
    private lateinit var dbName: String

    @Before
    fun setUp() {
        dbName = "gi-${UUID.randomUUID().toString().take(12)}.db"
        context.getDatabasePath(dbName).parentFile?.mkdirs()
        db = Database(
            context,
            dbName,
            wallNow = { LocalDate.of(2026, 11, 24).atTime(10, 0).atZone(zone).toInstant().toEpochMilli() },
            now = { LocalDateTime.of(2026, 11, 24, 10, 0) },
        )
        db.writableDatabase
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun testLegacyXpEquivalentMigrationAndDuplicateIdempotency() {
        // Pre-fill 35 completed tasks
        for (i in 1..35) {
            val id = "task-uuid-$i"
            db.writableDatabase.execSQL(
                "INSERT INTO tasks(id, day, title, required, minutes, mode, checklist, reward_minutes, reward_games, elapsed, done, completed_at) VALUES(?, '2026-11-20', ?, 1, 15, '行动型', '', 10, 0, 900000, 1, 1700000000000)",
                arrayOf(id, "任务 $i"),
            )
        }

        // Before migration, tasks count is 35
        val tasksDone = db.readableDatabase.rawQuery("SELECT COUNT(*) FROM tasks WHERE done = 1", null).use {
            it.moveToFirst()
            it.getInt(0)
        }
        assertEquals(35, tasksDone)

        // Query db.xp(), which automatically migrates to xp_ledger
        val xpVal = db.xp()
        assertEquals(3500, xpVal)

        // Verify count of active rows in xp_ledger is exactly 35
        val xpRows = db.readableDatabase.rawQuery("SELECT COUNT(*) FROM xp_ledger WHERE status = 'ACTIVE'", null).use {
            it.moveToFirst()
            it.getInt(0)
        }
        assertEquals(35, xpRows)

        // Duplicate migration trigger must not increase XP or create duplicates
        XpLedger.seedFromExistingTasks(db.writableDatabase)
        assertEquals(3500, db.xp())
        val xpRowsAfter = db.readableDatabase.rawQuery("SELECT COUNT(*) FROM xp_ledger WHERE status = 'ACTIVE'", null).use {
            it.moveToFirst()
            it.getInt(0)
        }
        assertEquals(35, xpRowsAfter)
    }

    @Test
    fun testBackupExportAndRestoreRoundTrip() {
        // Create contracts, tasks, and reward ledger
        val d = "2026-11-24"
        db.contract(d)
        db.addTask(d, "完整备份测试任务", true, 0, "行动型", "", 10, 1, "once")
        val task = db.tasks(d).single()
        db.complete(task)

        // Award XP and evaluate achievement
        assertEquals(100, db.xp())
        db.achievementEngine().evaluateAll()

        val exportJson = Backup.export(context, db)
        assertNotNull(exportJson)

        val preview = Backup.inspect(exportJson)
        assertEquals(1, preview.tasks)
        assertEquals(1, preview.completedTasks)
        assertEquals(100, preview.xp)

        // Restore into new database
        val targetName = "restore-target-${UUID.randomUUID()}.db"
        val targetDb = Database(context, targetName, now = { LocalDateTime.of(2026, 11, 24, 10, 0) })
        try {
            Backup.restore(context, targetDb, exportJson)
            assertEquals(100, targetDb.xp())
            assertEquals(1, targetDb.tasks(d).size)
            assertTrue(targetDb.tasks(d).single().done)

            // Verify achievement unlocks restored
            val restoredUnlocks = targetDb.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM achievement_unlocks WHERE status = 'UNLOCKED'",
                null,
            ).use {
                it.moveToFirst()
                it.getInt(0)
            }
            assertTrue("恢复后已解锁成效应大于等于1", restoredUnlocks >= 1)
        } finally {
            targetDb.close()
            context.deleteDatabase(targetName)
        }
    }

    @Test
    fun testConcurrentXpAwardingIdempotency() {
        val pool = Executors.newFixedThreadPool(8)
        val latch = CountDownLatch(8)
        val taskId = "concurrent-task-1"
        val day = "2026-11-24"

        for (i in 0 until 8) {
            pool.submit {
                try {
                    db.awardXp(
                        sourceType = XpLedger.SOURCE_TASK,
                        sourceId = taskId,
                        amount = 100,
                        date = day,
                        idempotencyKey = "task_$taskId",
                    )
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        pool.shutdown()

        // Exactly 100 XP, 1 transaction
        assertEquals(100, db.xp())
        val txCount = db.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM xp_ledger WHERE source_id = ?",
            arrayOf(taskId),
        ).use {
            it.moveToFirst()
            it.getInt(0)
        }
        assertEquals(1, txCount)
    }

    @Test
    fun testTransactionRollbackRecovery() {
        val sql = db.writableDatabase
        sql.beginTransaction()
        try {
            XpLedger.award(sql, "task", "failing-task", 100, "2026-11-24", System.currentTimeMillis())
            // Simulate exception without setTransactionSuccessful
            throw RuntimeException("Simulated failure")
        } catch (_: RuntimeException) {
            // expected
        } finally {
            sql.endTransaction()
        }

        // Must remain 0 XP
        assertEquals(0, db.xp())
    }

    @Test
    fun testExistingBehaviorRulesAndSleepPenaltiesUnaffected() {
        // Verify sleep level calculation and penalty rules are completely preserved
        assertEquals(0, db.sleepPenaltyMinutes(0))
        assertEquals(5, db.sleepPenaltyMinutes(1))
        assertEquals(10, db.sleepPenaltyMinutes(2))
        assertEquals(30, db.sleepPenaltyMinutes(6))
        assertEquals(30, db.sleepPenaltyMinutes(10)) // max 30 min cap

        assertEquals(0, db.sleepPenaltyGames(0))
        assertEquals(0, db.sleepPenaltyGames(1))
        assertEquals(1, db.sleepPenaltyGames(2))
        assertEquals(2, db.sleepPenaltyGames(4))
        assertEquals(2, db.sleepPenaltyGames(10)) // max 2 games cap

        // Verify emergency usage cap calculation preserved
        assertFalse(db.isDailyUsageCapExhausted("com.tencent.tmgp.sgame"))
    }

    @Test
    fun testLegacyBackupReplace() {
        val date = "2026-11-24"
        db.addTask(date, "备份来源任务", true, 0, "行动型", "", 0, 0, "once")
        db.complete(db.tasks(date).single())
        val currentBackup = Backup.export(context, db)
        val payload = JSONObject(JSONObject(currentBackup).getString("payload"))
        val legacyData = payload.getJSONObject("data")
        legacyData.remove("xp_ledger")
        legacyData.remove("achievement_unlocks")
        legacyData.remove("achievement_reconcile_logs")
        val legacyBackup = JSONObject().put("version", 4).put("data", legacyData).put("selected", payload.optJSONArray("selected")).toString()

        val targetName = "legacy-restore-${UUID.randomUUID()}.db"
        val targetDb = Database(context, targetName, now = { LocalDateTime.of(2026, 11, 24, 10, 0) })
        try {
            targetDb.addTask(date, "被替换的旧任务", true, 0, "行动型", "", 0, 0, "once")
            targetDb.complete(targetDb.tasks(date).single())
            assertEquals(100, targetDb.xp())

            Backup.restore(context, targetDb, legacyBackup)
            assertEquals(listOf("备份来源任务"), targetDb.tasks(date).map { it.title })
            assertEquals(100, targetDb.xp())
            assertEquals(1, countRows(targetDb, "xp_ledger"))

            Backup.restore(context, targetDb, legacyBackup)
            assertEquals(100, targetDb.xp())
            assertEquals(1, countRows(targetDb, "xp_ledger"))
        } finally {
            targetDb.close()
            context.deleteDatabase(targetName)
        }
    }

    @Test
    fun testRestoreFailureAfterReplacementBeginsRollsBackWholeDatabase() {
        val date = "2026-11-24"
        db.addTask(date, "来源任务", true, 0, "行动型", "", 0, 0, "once")
        db.complete(db.tasks(date).single())
        val sourceBackup = JSONObject(Backup.export(context, db))
        val payload = JSONObject(sourceBackup.getString("payload"))
        val tasks = payload.getJSONObject("data").getJSONArray("tasks")
        tasks.put(JSONObject(tasks.getJSONObject(0).toString()))
        val payloadText = payload.toString()
        val digest = MessageDigest.getInstance("SHA-256").digest(payloadText.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        sourceBackup.put("payload", payloadText).put("sha256", digest)

        val targetName = "rollback-restore-${UUID.randomUUID()}.db"
        val targetDb = Database(context, targetName, now = { LocalDateTime.of(2026, 11, 24, 10, 0) })
        try {
            targetDb.addTask(date, "保留任务", true, 0, "行动型", "", 0, 0, "once")
            targetDb.complete(targetDb.tasks(date).single())
            val beforeXp = targetDb.xp()
            val beforeTaskIds = targetDb.tasks(date).map { it.id }
            val beforeRewardRows = countRows(targetDb, "ledger")

            val failure = runCatching { Backup.restore(context, targetDb, sourceBackup.toString()) }.exceptionOrNull()
            assertNotNull("重复主键应使导入在事务中失败", failure)
            assertEquals(beforeTaskIds, targetDb.tasks(date).map { it.id })
            assertEquals(beforeXp, targetDb.xp())
            assertEquals(beforeRewardRows, countRows(targetDb, "ledger"))
        } finally {
            targetDb.close()
            context.deleteDatabase(targetName)
        }
    }

    private fun countRows(database: Database, table: String): Int = database.readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use {
        it.moveToFirst()
        it.getInt(0)
    }
}
