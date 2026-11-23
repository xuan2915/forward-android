package com.focus.prototype

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class BehaviorRulesV1LedgerTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    // --- A. 奖励与账本 R01 - R14 ---

    @Test
    fun testR01_mandatoryTaskCompletionGrantsRewardImmediately() {
        val name = "r01-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-10-30T10:00:00")
        val db = Database(context, name, now = { time })
        try {
            val date = db.day()
            db.addTask(date, "必做任务1", true, 0, "行动型", "检查项", 10, 0, "once")
            db.confirm(date, false, 0, 0)
            val task = db.tasks(date).first { it.title == "必做任务1" }
            assertEquals(0L, db.balance("video"))

            db.complete(task)
            assertEquals(10 * 60_000L, db.balance("video"))
            assertTrue(db.tasks(date).first { it.title == "必做任务1" }.done)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testR02_otherUnfinishedMandatoryTasksDoNotBlockGrantedRewards() {
        val name = "r02-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-10-30T10:00:00")
        val db = Database(context, name, now = { time })
        try {
            val date = db.day()
            db.addTask(date, "必做A", true, 0, "行动型", "检查项", 10, 0, "once")
            db.addTask(date, "必做B", true, 0, "行动型", "检查项", 20, 0, "once")
            db.confirm(date, false, 0, 0)

            val taskA = db.tasks(date).first { it.title == "必做A" }
            db.complete(taskA)

            assertEquals(10 * 60_000L, db.balance("video"))
            assertFalse(db.tasks(date).first { it.title == "必做B" }.done)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testR03_idempotentCompletionPreventsDuplicateRewards() {
        val name = "r03-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-10-30T10:00:00")
        val db = Database(context, name, now = { time })
        try {
            val date = db.day()
            db.addTask(date, "必做幂等", true, 0, "行动型", "检查项", 10, 0, "once")
            db.confirm(date, false, 0, 0)
            val task = db.tasks(date).first { it.title == "必做幂等" }

            db.complete(task)
            assertEquals(10 * 60_000L, db.balance("video"))

            // Repeat complete call
            db.complete(task)
            assertEquals(10 * 60_000L, db.balance("video"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testR04_optionalTaskRewardChoiceIsMutuallyExclusive() {
        val name = "r04-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-10-30T10:00:00")
        val db = Database(context, name, now = { time })
        try {
            val date = db.day()
            db.addTask(date, "选做互斥", false, 0, "行动型", "检查项", 15, 1, "once")
            db.confirm(date, false, 0, 0)
            val task = db.tasks(date).first { it.title == "选做互斥" }
            db.complete(task)

            assertEquals(0L, db.balance("video"))
            assertEquals(0L, db.balance("game"))

            // Claim video
            db.claim(task, "video")
            assertEquals(15 * 60_000L, db.balance("video"))
            assertEquals(0L, db.balance("game"))

            // Attempt to claim game afterwards - mutually exclusive
            db.claim(task, "game")
            assertEquals(15 * 60_000L, db.balance("video"))
            assertEquals(0L, db.balance("game"))

            // Repeat claim video - idempotent
            db.claim(task, "video")
            assertEquals(15 * 60_000L, db.balance("video"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testR05_dailyEarnCapEnforcedOnNetRewards() {
        val name = "r05-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-10-30T10:00:00")
        val db = Database(context, name, now = { time })
        try {
            val date = db.day()
            db.setDailyEarnCap("video", 15 * 60_000L) // 15 minutes cap

            db.addTask(date, "必做1", true, 0, "行动型", "检查项", 10, 0, "once")
            db.addTask(date, "必做2", true, 0, "行动型", "检查项", 10, 0, "once")
            db.confirm(date, false, 0, 0)

            db.complete(db.tasks(date).first { it.title == "必做1" })
            assertEquals(10 * 60_000L, db.balance("video"))

            // Second task grants 10 min, but cap is 15 min -> 5 min capped
            db.complete(db.tasks(date).first { it.title == "必做2" })
            assertEquals(15 * 60_000L, db.balance("video"))
            assertTrue(db.rewardHistory().any { it.contains("每日获取上限扣减") })
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testR06_sleepPenaltyDeductedFromNewRewardsFirst() {
        val name = "r06-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-10-30T10:00:00")
        val db = Database(context, name, now = { time })
        try {
            val date = db.day()
            // Record sleep violation: penalty 15 mins
            db.recordSleep(date, "23:30", "08:00") // 15 mins wake penalty
            assertEquals(15 * 60_000L, db.pendingSleepPenalty())

            db.addTask(date, "必做补偿", true, 0, "行动型", "检查项", 20, 0, "once")
            db.confirm(date, false, 0, 0)

            db.complete(db.tasks(date).first { it.title == "必做补偿" })
            // 20 min reward - 15 min penalty = 5 min balance
            assertEquals(5 * 60_000L, db.balance("video"))
            assertEquals(0L, db.pendingSleepPenalty())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testR07_penaltyExceedingRawRewardResultsInZeroBalanceNeverNegative() {
        val name = "r07-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-10-30T10:00:00")
        val db = Database(context, name, now = { time })
        try {
            val date = db.day()
            // Bedtime late + wake late: total 30 min penalty
            db.recordSleep(date, "01:30", "08:00")
            assertEquals(30 * 60_000L, db.pendingSleepPenalty())

            db.addTask(date, "部分抵扣", true, 0, "行动型", "检查项", 10, 0, "once")
            db.confirm(date, false, 0, 0)

            db.complete(db.tasks(date).first { it.title == "部分抵扣" })
            assertEquals(0L, db.balance("video"))
            assertEquals(20 * 60_000L, db.pendingSleepPenalty())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testR08_videoAndGamePoolsSettleIndependently() {
        val name = "r08-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-10-30T10:00:00")
        val db = Database(context, name, now = { time })
        try {
            val date = db.day()
            db.addTask(date, "时长任务", true, 0, "行动型", "检查项", 10, 0, "once")
            db.addTask(date, "对局任务", true, 0, "行动型", "检查项", 0, 2, "once")
            db.confirm(date, false, 0, 0)

            db.complete(db.tasks(date).first { it.title == "时长任务" })
            db.complete(db.tasks(date).first { it.title == "对局任务" })

            assertEquals(10 * 60_000L, db.balance("video"))
            assertEquals(2L, db.balance("game"))

            // Consume video only
            db.consume("video", 4 * 60_000L)
            assertEquals(6 * 60_000L, db.balance("video"))
            assertEquals(2L, db.balance("game"))

            // Consume game only
            db.consume("game", 1L)
            assertEquals(6 * 60_000L, db.balance("video"))
            assertEquals(1L, db.balance("game"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testR09_revokingUnconsumedRewardReversesBalance() {
        val name = "r09-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-10-30T10:00:00")
        val db = Database(context, name, now = { time })
        try {
            val date = db.day()
            db.addTask(date, "撤销任务", true, 0, "行动型", "检查项", 10, 0, "once")
            db.confirm(date, false, 0, 0)
            val task = db.tasks(date).first { it.title == "撤销任务" }

            db.complete(task)
            assertEquals(10 * 60_000L, db.balance("video"))

            db.revoke(task)
            assertEquals(0L, db.balance("video"))
            assertFalse(db.tasks(date).first { it.title == "撤销任务" }.done)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testR10_revokingPartiallyConsumedRewardPreservesUsageHistoryAndBalanceNonNegative() {
        val name = "r10-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-10-30T10:00:00")
        val db = Database(context, name, now = { time })
        try {
            val date = db.day()
            db.addTask(date, "部分消费任务", true, 0, "行动型", "检查项", 10, 0, "once")
            db.confirm(date, false, 0, 0)
            val task = db.tasks(date).first { it.title == "部分消费任务" }

            db.complete(task)
            assertEquals(10 * 60_000L, db.balance("video"))

            db.consume("video", 6 * 60_000L)
            assertEquals(4 * 60_000L, db.balance("video"))

            db.revoke(task)
            // Balance cannot drop below zero
            assertEquals(0L, db.balance("video"))

            // Usage history of 6 min is preserved
            val snapshot = db.rewardSnapshot("video")
            assertEquals(6 * 60_000L, snapshot.used)
            assertTrue(db.rewardHistory().any { it.contains("已使用 6 分钟") })
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testR11_recompletingRevokedTaskSettlesOnFreshVersionWithoutDuplicateGrants() {
        val name = "r11-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-10-30T10:00:00")
        val db = Database(context, name, now = { time })
        try {
            val date = db.day()
            db.addTask(date, "重做任务", true, 0, "行动型", "检查项", 10, 0, "once")
            db.confirm(date, false, 0, 0)
            val task = db.tasks(date).first { it.title == "重做任务" }

            db.complete(task)
            assertEquals(10 * 60_000L, db.balance("video"))

            db.revoke(task)
            assertEquals(0L, db.balance("video"))

            // Re-complete
            db.complete(task)
            assertEquals(10 * 60_000L, db.balance("video"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testR12_crashRebootReinvocationIdempotency() {
        val dbName = "crash-test-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-10-30T10:00:00")
        val db1 = Database(context, dbName, now = { time })
        val date = db1.day()
        db1.addTask(date, "崩溃测试", true, 0, "行动型", "检查项", 10, 0, "once")
        db1.confirm(date, false, 0, 0)
        db1.complete(db1.tasks(date).first { it.title == "崩溃测试" })
        assertEquals(10 * 60_000L, db1.balance("video"))
        db1.close()

        // Re-open (simulate app restart)
        val db2 = Database(context, dbName, now = { time })
        try {
            db2.ensure()
            assertEquals(10 * 60_000L, db2.balance("video"))
            val ledgerCount = db2.readableDatabase.rawQuery("SELECT COUNT(*) FROM ledger WHERE task_id NOT LIKE '%allocation%'", null).use {
                it.moveToFirst()
                it.getInt(0)
            }
            assertEquals(1, ledgerCount)
        } finally {
            db2.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun testR13_transactionAtomicityDuringConsumptionAndGrant() {
        val name = "r13-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-10-30T10:00:00")
        val db = Database(context, name, now = { time })
        try {
            val date = db.day()
            db.addTask(date, "并发任务", true, 0, "行动型", "检查项", 10, 0, "once")
            db.confirm(date, false, 0, 0)
            db.complete(db.tasks(date).first { it.title == "并发任务" })
            assertEquals(10 * 60_000L, db.balance("video"))

            // Interleaved consume
            db.consume("video", 7 * 60_000L)
            assertEquals(3 * 60_000L, db.balance("video"))

            // Consume more than available
            db.consume("video", 5 * 60_000L)
            // Available was only 3 min, so it takes 3 min, balance is 0L
            assertEquals(0L, db.balance("video"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testR14_xpIncreasesWithoutAlteringEntertainmentBalances() {
        val name = "r14-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-10-30T10:00:00")
        val db = Database(context, name, now = { time })
        try {
            val date = db.day()
            db.addTask(date, "纯经验任务", true, 0, "行动型", "检查项", 0, 0, "once")
            db.confirm(date, false, 0, 0)
            val initialXp = db.xp()
            assertEquals(0L, db.balance("video"))
            assertEquals(0L, db.balance("game"))

            db.complete(db.tasks(date).first { it.title == "纯经验任务" })
            assertEquals(initialXp + 100, db.xp())
            assertEquals(0L, db.balance("video"))
            assertEquals(0L, db.balance("game"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    // --- C. 跨日、异常与数据恢复 T01 - T06, T10 - T12 ---

    @Test
    fun testT01_taskAt2359BelongsToCurrentTaskDay() {
        val name = "t01-${System.nanoTime()}.db"
        val time = LocalDateTime.parse("2026-10-30T23:59:00")
        val db = Database(context, name, now = { time })
        try {
            assertEquals("2026-10-30", db.day())
            assertEquals("2026-10-30", db.rewardDay())

            db.addTask(db.day(), "深夜任务", true, 0, "行动型", "检查项", 10, 0, "once")
            db.confirm(db.day(), false, 0, 0)
            val task = db.tasks(db.day()).first { it.title == "深夜任务" }
            db.complete(task)

            assertEquals(10 * 60_000L, db.balance("video", "2026-10-30"))
            val expireAt = db.readableDatabase.rawQuery("SELECT expire_at, task_day FROM ledger WHERE task_id=? AND amount>0", arrayOf(task.id)).use {
                it.moveToFirst()
                Pair(it.getLong(0), it.getString(1))
            }
            assertEquals("2026-10-30", expireAt.second)
            assertTrue(expireAt.first > 0L)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testT02_taskAt0001BelongsToNewTaskDay() {
        val name = "t02-${System.nanoTime()}.db"
        val time = LocalDateTime.parse("2026-10-31T00:01:00")
        val db = Database(context, name, now = { time })
        try {
            assertEquals("2026-10-31", db.day())
            // Entertainment boundary is 04:00 by default, so rewardDay() is 2026-10-30
            assertEquals("2026-10-30", db.rewardDay())

            db.addTask(db.day(), "凌晨任务", true, 0, "行动型", "检查项", 10, 0, "once")
            db.confirm(db.day(), false, 0, 0)
            val task = db.tasks(db.day()).first { it.title == "凌晨任务" }
            db.complete(task)

            // Belongs to task day 2026-10-31
            assertEquals(10 * 60_000L, db.balance("video", "2026-10-31"))
            val row = db.readableDatabase.rawQuery("SELECT task_day FROM ledger WHERE task_id=? AND amount>0", arrayOf(task.id)).use {
                it.moveToFirst()
                it.getString(0)
            }
            assertEquals("2026-10-31", row)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testT03_dualBatchesCoexistBetween0000And0400() {
        val name = "t03-${System.nanoTime()}.db"
        var clock = LocalDateTime.parse("2026-10-30T22:00:00")
        val db = Database(context, name, now = { clock })
        try {
            val d1 = db.day()
            db.addTask(d1, "昨晚任务", true, 0, "行动型", "检查项", 10, 0, "once")
            db.confirm(d1, false, 0, 0)
            db.complete(db.tasks(d1).first { it.title == "昨晚任务" })
            assertEquals(10 * 60_000L, db.balance("video"))

            // Advance past midnight to 01:30
            clock = LocalDateTime.parse("2026-10-31T01:30:00")
            val d2 = db.day()
            assertEquals("2026-10-31", d2)

            db.addTask(d2, "凌晨新任务", true, 0, "行动型", "检查项", 5, 0, "once")
            db.confirm(d2, false, 0, 0)
            db.complete(db.tasks(d2).first { it.title == "凌晨新任务" })

            // Both batches coexist
            assertEquals(10 * 60_000L, db.balance("video", "2026-10-30"))
            assertEquals(5 * 60_000L, db.balance("video", "2026-10-31"))
            assertEquals(15 * 60_000L, db.balance("video"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testT04_boundary0400ExpiresOnlyPreviousBatch() {
        val name = "t04-${System.nanoTime()}.db"
        var clock = LocalDateTime.parse("2026-10-30T22:00:00")
        val db = Database(context, name, now = { clock })
        try {
            val d1 = db.day()
            db.addTask(d1, "前夜任务", true, 0, "行动型", "检查项", 10, 0, "once")
            db.confirm(d1, false, 0, 0)
            db.complete(db.tasks(d1).first { it.title == "前夜任务" })

            clock = LocalDateTime.parse("2026-10-31T01:00:00")
            val d2 = db.day()
            db.addTask(d2, "今日凌晨", true, 0, "行动型", "检查项", 5, 0, "once")
            db.confirm(d2, false, 0, 0)
            db.complete(db.tasks(d2).first { it.title == "今日凌晨" })
            assertEquals(15 * 60_000L, db.balance("video"))

            // Cross boundary at 04:00
            clock = LocalDateTime.parse("2026-10-31T04:00:00")
            db.ensure()

            // 10-30 batch expired, 10-31 batch remains active
            assertEquals(0L, db.balance("video", "2026-10-30"))
            assertEquals(5 * 60_000L, db.balance("video", "2026-10-31"))
            assertEquals(5 * 60_000L, db.balance("video"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testT05_crossBoundaryConsumptionUsesEarliestExpiringBatchFirst() {
        val name = "t05-${System.nanoTime()}.db"
        var clock = LocalDateTime.parse("2026-10-30T22:00:00")
        val db = Database(context, name, now = { clock })
        try {
            val d1 = db.day()
            db.addTask(d1, "早期批次", true, 0, "行动型", "检查项", 4, 0, "once")
            db.confirm(d1, false, 0, 0)
            db.complete(db.tasks(d1).first { it.title == "早期批次" })

            clock = LocalDateTime.parse("2026-10-31T02:00:00")
            val d2 = db.day()
            db.addTask(d2, "晚期批次", true, 0, "行动型", "检查项", 6, 0, "once")
            db.confirm(d2, false, 0, 0)
            db.complete(db.tasks(d2).first { it.title == "晚期批次" })

            assertEquals(10 * 60_000L, db.balance("video"))

            // Consume 6 minutes: 4 min from 10-30, 2 min from 10-31
            db.consume("video", 6 * 60_000L)

            assertEquals(0L, db.balance("video", "2026-10-30"))
            assertEquals(4 * 60_000L, db.balance("video", "2026-10-31"))
            assertEquals(4 * 60_000L, db.balance("video"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testT06_modifyingBoundaryAffectsOnlyFutureBatches() {
        val name = "t06-${System.nanoTime()}.db"
        var clock = LocalDateTime.parse("2026-10-30T10:00:00")
        val db = Database(context, name, now = { clock })
        try {
            val d1 = db.day()
            db.addTask(d1, "旧界线任务", true, 0, "行动型", "检查项", 10, 0, "once")
            db.confirm(d1, false, 0, 0)
            val task = db.tasks(d1).first { it.title == "旧界线任务" }
            db.complete(task)

            val initialExpiry = db.readableDatabase.rawQuery("SELECT expire_at FROM ledger WHERE task_id=? AND amount>0", arrayOf(task.id)).use {
                it.moveToFirst()
                it.getLong(0)
            }

            // Change boundary
            db.setSetting("boundary", "5")

            // Existing batch's stored expire_at is not modified
            val preservedExpiry = db.readableDatabase.rawQuery("SELECT expire_at FROM ledger WHERE task_id=? AND amount>0", arrayOf(task.id)).use {
                it.moveToFirst()
                it.getLong(0)
            }
            assertEquals(initialExpiry, preservedExpiry)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testT10_backupAndRestorePreservesMultiBatchLedgerAndAudits() {
        val name1 = "t10-src-${System.nanoTime()}.db"
        val name2 = "t10-dst-${System.nanoTime()}.db"
        var clock = LocalDateTime.parse("2026-10-31T01:00:00")
        val db1 = Database(context, name1, now = { clock })
        val backupJson: String
        try {
            db1.addTask("2026-10-30", "昨日本", true, 0, "行动型", "检查项", 10, 0, "once")
            db1.confirm("2026-10-30", false, 0, 0)
            db1.complete(db1.tasks("2026-10-30").first { it.title == "昨日本" })

            db1.addTask("2026-10-31", "今日凌晨本", true, 0, "行动型", "检查项", 8, 1, "once")
            db1.confirm("2026-10-31", false, 0, 0)
            db1.complete(db1.tasks("2026-10-31").first { it.title == "今日凌晨本" })

            db1.consume("video", 5 * 60_000L)
            assertEquals(13 * 60_000L, db1.balance("video"))
            assertEquals(1L, db1.balance("game"))

            backupJson = Backup.export(context, db1)
        } finally {
            db1.close()
            context.deleteDatabase(name1)
        }

        val db2 = Database(context, name2, now = { clock })
        try {
            Backup.restore(context, db2, backupJson)
            assertEquals(13 * 60_000L, db2.balance("video"))
            assertEquals(1L, db2.balance("game"))
            assertEquals(5 * 60_000L, db2.balance("video", "2026-10-30"))
            assertEquals(8 * 60_000L, db2.balance("video", "2026-10-31"))
        } finally {
            db2.close()
            context.deleteDatabase(name2)
        }
    }

    @Test
    fun testT11_legacyDatabaseUpgradePreservesBalancesWithoutDuplicateMigration() {
        val name = "legacy-upgrade-${System.nanoTime()}.db"
        val old = Database(context, name)
        old.setSetting("keep_me", "yes")
        old.writableDatabase.execSQL("INSERT INTO ledger(id, day, task_id, kind, amount, created) VALUES ('l1', '2026-10-30', 'task1', 'video', 600000, 1000)")
        old.writableDatabase.version = 7
        old.close()

        val upgraded = Database(context, name, now = { LocalDateTime.parse("2026-10-30T12:00:00") })
        try {
            val taskDay = upgraded.readableDatabase.rawQuery("SELECT task_day FROM ledger WHERE id='l1'", null).use {
                it.moveToFirst()
                it.getString(0)
            }
            assertEquals("2026-10-30", taskDay)
            assertEquals(600_000L, upgraded.balance("video"))
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testT12_transactionRollbackPreventsPartialSettlement() {
        val name = "t12-${System.nanoTime()}.db"
        var clock = LocalDateTime.parse("2026-10-30T10:00:00")
        val db = Database(context, name, now = { clock })
        try {
            val date = db.day()
            db.addTask(date, "回滚测试", true, 0, "行动型", "检查项", 10, 0, "once")
            db.confirm(date, false, 0, 0)
            db.complete(db.tasks(date).first { it.title == "回滚测试" })
            assertEquals(10 * 60_000L, db.balance("video"))

            val sql = db.writableDatabase
            sql.beginTransaction()
            try {
                sql.execSQL("INSERT INTO ledger(id, day, task_id, kind, amount, created) VALUES ('bad', '$date', 'usage', 'video', -300000, 2000)")
                // Do not call setTransactionSuccessful() -> simulate failure / abort
            } finally {
                sql.endTransaction()
            }

            // Verify bad entry was rolled back
            assertEquals(10 * 60_000L, db.balance("video"))
            val count = sql.rawQuery("SELECT COUNT(*) FROM ledger WHERE id='bad'", null).use {
                it.moveToFirst()
                it.getInt(0)
            }
            assertEquals(0, count)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
