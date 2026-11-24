package com.focus.prototype

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

@RunWith(RobolectricTestRunner::class)
class XpLedgerAndLevelCurveTest {
    private val zone = ZoneId.systemDefault()
    private lateinit var db: Database
    private lateinit var dbName: String

    @Before
    fun setUp() {
        dbName = "xp-test-${UUID.randomUUID()}.db"
        val context = RuntimeEnvironment.getApplication()
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
        RuntimeEnvironment.getApplication().deleteDatabase(dbName)
    }

    @Test
    fun testDefaultProductionLevelCurveCalculation() {
        val curve = DefaultProductionLevelCurve()
        // Level 1: 0..499
        assertEquals(1, curve.level(0))
        assertEquals(1, curve.level(100))
        assertEquals(1, curve.level(499))
        // Level 2: 500..999
        assertEquals(2, curve.level(500))
        assertEquals(2, curve.level(999))
        // Level 8: 3500..3999
        assertEquals(8, curve.level(3500))

        val p0 = curve.progress(0)
        assertEquals(1, p0.level)
        assertEquals(0L, p0.currentLevelXp)
        assertEquals(500L, p0.xpToNextLevel)
        assertEquals(0f, p0.progressRatio, 0.001f)
        assertEquals("启程", p0.rank)

        val p250 = curve.progress(250)
        assertEquals(1, p250.level)
        assertEquals(250L, p250.currentLevelXp)
        assertEquals(250L, p250.xpToNextLevel)
        assertEquals(0.5f, p250.progressRatio, 0.001f)

        val p3500 = curve.progress(3500)
        assertEquals(8, p3500.level)
        assertEquals(0L, p3500.currentLevelXp)
        assertEquals(500L, p3500.xpToNextLevel)
        assertEquals("卓越", p3500.rank)

        // Monotonic check
        for (xp in 0L..5000L step 50L) {
            val lvl = curve.level(xp)
            val prog = curve.progress(xp)
            assertEquals(lvl, prog.level)
            assertTrue("Progress ratio must be in 0..1", prog.progressRatio in 0f..1f)
            assertEquals(500L, prog.currentLevelXp + prog.xpToNextLevel)
        }
    }

    @Test
    fun testBalancedLevelCurveMonotonicity() {
        val curve = BalancedLevelCurve()
        var prevXpReq = 0L
        for (lvl in 1..20) {
            val req = curve.xpRequiredForLevel(lvl)
            assertTrue("XP for level must be strictly monotonic", req >= prevXpReq)
            prevXpReq = req
        }

        var prevLevel = 1
        for (xp in 0L..10000L step 50L) {
            val lvl = curve.level(xp)
            assertTrue("Level must be monotonically non-decreasing", lvl >= prevLevel)
            prevLevel = lvl
            val prog = curve.progress(xp)
            assertEquals(lvl, prog.level)
            assertTrue(prog.progressRatio in 0f..1f)
        }
    }

    @Test
    fun testXpLedgerAwardRevokeRedoIdempotency() {
        val day = "2026-11-24"
        val taskId = "task-uuid-1"

        // Initial XP should be 0
        assertEquals(0, db.xp())

        // 1. Award XP
        val tx1 = db.awardXp(XpLedger.SOURCE_TASK, taskId, 100, day, idempotencyKey = "task_$taskId")
        assertEquals(100, tx1.amount)
        assertEquals(XpStatus.ACTIVE, tx1.status)
        assertEquals(100, db.xp())

        // Duplicate award with same idempotencyKey should be idempotent
        val txDuplicate = db.awardXp(XpLedger.SOURCE_TASK, taskId, 100, day, idempotencyKey = "task_$taskId")
        assertEquals(tx1.id, txDuplicate.id)
        assertEquals(100, db.xp())

        // 2. Revoke XP
        val revoked = db.reverseXp(XpLedger.SOURCE_TASK, taskId, "test_revoke")
        assertTrue(revoked)
        assertEquals(0, db.xp())

        val txAfterRevoke = db.queryXpBySource(XpLedger.SOURCE_TASK, taskId)
        assertNotNull(txAfterRevoke)
        assertEquals(XpStatus.REVERSED, txAfterRevoke!!.status)

        // Duplicate revoke returns false
        assertFalse(db.reverseXp(XpLedger.SOURCE_TASK, taskId, "repeat_revoke"))

        // 3. Redo XP
        val redone = db.redoXp(XpLedger.SOURCE_TASK, taskId)
        assertTrue(redone)
        assertEquals(100, db.xp())

        val txAfterRedo = db.queryXpBySource(XpLedger.SOURCE_TASK, taskId)
        assertEquals(XpStatus.ACTIVE, txAfterRedo!!.status)
    }

    @Test
    fun testDatabaseConstraintsPreventDuplicateSourceAndKeyOwnership() {
        db.awardXp(XpLedger.SOURCE_TASK, "unique-task", 100, "2026-11-24", idempotencyKey = "unique-key")
        val duplicateSource = runCatching {
            db.writableDatabase.execSQL(
                "INSERT INTO xp_ledger(id,day,source_type,source_id,amount,status,created,version,idempotency_key,metadata) VALUES('dup','2026-11-24','task','unique-task',100,'ACTIVE',2,1,'different-key','')",
            )
        }.exceptionOrNull()
        assertNotNull("数据库必须拒绝同一来源的第二条XP流水", duplicateSource)

        val duplicateKey = runCatching {
            db.awardXp(XpLedger.SOURCE_CHALLENGE, "other-source", 100, "2026-11-24", idempotencyKey = "unique-key")
        }.exceptionOrNull()
        assertNotNull("幂等键不能被另一个来源占用", duplicateKey)
        assertEquals(100, db.xp())
    }

    @Test
    fun testFutureChallengeXpInterface() {
        val day = "2026-11-24"
        val challengeInstanceId = "challenge-inst-202611-01"
        val frozenRewardXp = 300
        val rewardVersion = 1

        val tx = db.awardXp(
            sourceType = XpLedger.SOURCE_CHALLENGE,
            sourceId = challengeInstanceId,
            amount = frozenRewardXp,
            date = day,
            idempotencyKey = "challenge_$challengeInstanceId",
            version = rewardVersion,
            metadata = "theme:routine_master",
        )
        assertEquals(300, tx.amount)
        assertEquals(300, db.xp())
        assertEquals(XpLedger.SOURCE_CHALLENGE, tx.sourceType)
        assertEquals(rewardVersion, tx.version)

        // Challenge award does not grant entertainment minutes or game rounds
        val ledgerCount = db.readableDatabase.rawQuery("SELECT COUNT(*) FROM ledger WHERE task_id = ?", arrayOf(challengeInstanceId)).use {
            it.moveToFirst()
            it.getInt(0)
        }
        assertEquals(0, ledgerCount)
    }

    @Test
    fun testMigrationPreservesExactCompletedTaskXpWithoutDuplication() {
        // Create 5 completed tasks directly in tasks table simulating pre-migration state
        for (i in 1..5) {
            val id = "legacy-task-$i"
            db.writableDatabase.execSQL(
                "INSERT INTO tasks(id, day, title, required, minutes, mode, checklist, reward_minutes, reward_games, elapsed, done, completed_at) VALUES(?, '2026-11-20', ?, 1, 10, '行动型', '', 0, 0, 600000, 1, 1700000000000)",
                arrayOf(id, "任务$i"),
            )
        }

        // Initially, seeding from existing tasks
        XpLedger.seedFromExistingTasks(db.writableDatabase)
        assertEquals(500, db.xp())

        // Running seed again must not duplicate
        XpLedger.seedFromExistingTasks(db.writableDatabase)
        assertEquals(500, db.xp())
    }
}
