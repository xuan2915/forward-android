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
class BehaviorRulesV1SleepTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    // --- B. 睡眠与惩罚 S01 - S12 ---

    @Test
    fun testS01_consecutiveThreeFailuresIncrementLevelSequentially() {
        val name = "s01-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-11-21T08:00:00")
        val db = Database(context, name, now = { time })
        try {
            // Set valid targets: 00:30 to 07:30 (7 hours window)
            db.saveSleepTargets("00:30", "07:30", 15, 15)

            // Cycle 1: 11-21 (late bed 01:30)
            val r1 = db.recordSleepCycle("2026-11-21", "01:30", "07:30")
            assertEquals(SleepStatus.FAIL, r1.status)
            assertEquals(1, r1.level)
            assertEquals(5, r1.penaltyMinutes)
            assertEquals(0, r1.penaltyGames)
            assertEquals("2026-11-22", r1.effectiveDate)

            // Cycle 2: 11-22 (late bed 01:30)
            time = LocalDateTime.parse("2026-11-22T08:00:00")
            val r2 = db.recordSleepCycle("2026-11-22", "01:30", "07:30")
            assertEquals(SleepStatus.FAIL, r2.status)
            assertEquals(2, r2.level)
            assertEquals(10, r2.penaltyMinutes)
            assertEquals(1, r2.penaltyGames)
            assertEquals("2026-11-23", r2.effectiveDate)

            // Cycle 3: 11-23 (late bed 01:30)
            time = LocalDateTime.parse("2026-11-23T08:00:00")
            val r3 = db.recordSleepCycle("2026-11-23", "01:30", "07:30")
            assertEquals(SleepStatus.FAIL, r3.status)
            assertEquals(3, r3.level)
            assertEquals(15, r3.penaltyMinutes)
            assertEquals(1, r3.penaltyGames)
            assertEquals("2026-11-24", r3.effectiveDate)

            // Verify global level
            assertEquals(3, db.sleepLevel("2026-11-23"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testS02_sameCycleLateBedAndLateWakeOnlyIncrementsOneLevel() {
        val name = "s02-${System.nanoTime()}.db"
        val time = LocalDateTime.parse("2026-11-21T09:00:00")
        val db = Database(context, name, now = { time })
        try {
            db.saveSleepTargets("00:30", "07:30", 15, 15)

            // Both bed late (01:30 > 00:30) and wake late (08:30 > 07:30)
            val r = db.recordSleepCycle("2026-11-21", "01:30", "08:30")
            assertEquals(SleepStatus.FAIL, r.status)
            // Must only increment by 1 level, not 2
            assertEquals(1, r.level)
            assertEquals(5, r.penaltyMinutes)
            assertEquals(0, r.penaltyGames)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testS03_completePassResetsLevelToZero() {
        val name = "s03-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-11-21T08:00:00")
        val db = Database(context, name, now = { time })
        try {
            db.saveSleepTargets("00:30", "07:30", 15, 15)

            // Fail two cycles to reach level 2
            db.recordSleepCycle("2026-11-21", "01:30", "07:30")
            time = LocalDateTime.parse("2026-11-22T08:00:00")
            db.recordSleepCycle("2026-11-22", "01:30", "07:30")
            assertEquals(2, db.sleepLevel("2026-11-22"))

            // Cycle 3: Complete PASS (bed 00:00, wake 07:15, duration 7h 15m)
            time = LocalDateTime.parse("2026-11-23T08:00:00")
            val r = db.recordSleepCycle("2026-11-23", "00:00", "07:15")
            assertEquals(SleepStatus.PASS, r.status)
            assertEquals(0, r.level)
            assertEquals(0, r.penaltyMinutes)
            assertEquals(0, r.penaltyGames)
            assertEquals(0, db.sleepLevel("2026-11-23"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testS04_sleepDurationSixHoursFiftyNineMinutesFailsSevenHourFloor() {
        val name = "s04-${System.nanoTime()}.db"
        val time = LocalDateTime.parse("2026-11-21T08:00:00")
        val db = Database(context, name, now = { time })
        try {
            db.saveSleepTargets("00:30", "07:30", 15, 15)

            // Bed 00:30, wake 07:29: exactly 6 hours 59 minutes (419 minutes)
            val r = db.recordSleepCycle("2026-11-21", "00:30", "07:29")
            assertEquals(419L, r.durationMinutes)
            assertEquals(SleepStatus.FAIL, r.status)
            assertEquals(1, r.level)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testS05_sleepSevenHoursWithLateWakeFails() {
        val name = "s05-${System.nanoTime()}.db"
        val time = LocalDateTime.parse("2026-11-21T08:30:00")
        val db = Database(context, name, now = { time })
        try {
            db.saveSleepTargets("00:30", "07:30", 15, 15)

            // Bed 01:00, wake 08:00: exactly 7 hours (420 minutes), but wake 08:00 > target 07:30
            val r = db.recordSleepCycle("2026-11-21", "01:00", "08:00")
            assertEquals(420L, r.durationMinutes)
            assertEquals(SleepStatus.FAIL, r.status)
            assertEquals(1, r.level)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testS06_targetWindowLessThanSevenHoursIsRejected() {
        val name = "s06-${System.nanoTime()}.db"
        val db = Database(context, name)
        try {
            // 01:00 to 07:30 is 6.5 hours (390 minutes < 420 minutes)
            val ex = assertThrows(IllegalArgumentException::class.java) {
                db.saveSleepTargets("01:00", "07:30", 15, 15)
            }
            assertTrue(ex.message!!.contains("配置被拒绝"))
            assertTrue(ex.message!!.contains("不足7小时"))

            // Legitimate 7-hour target is accepted
            db.saveSleepTargets("00:30", "07:30", 15, 15)
            assertEquals(listOf("00:30", "07:30", "15", "15"), db.sleepTargets())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testS07_missingRecordIsUnverifiedDoesNotAutoFail() {
        val name = "s07-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-11-21T08:00:00")
        val db = Database(context, name, now = { time })
        try {
            db.saveSleepTargets("00:30", "07:30", 15, 15)

            // Unrecorded date
            assertEquals(SleepStatus.UNVERIFIED, db.sleepStatus("2026-11-21"))
            // Level is not incremented
            assertEquals(0, db.sleepLevel("2026-11-21"))

            // Not yet 24 hours overdue
            assertFalse(db.isSleepUnverifiedOverdue("2026-11-21", at = LocalDateTime.parse("2026-11-21T20:00:00")))

            // Over 24 hours overdue: reminders only, does NOT auto-fail
            time = LocalDateTime.parse("2026-11-22T09:00:00")
            assertTrue(db.isSleepUnverifiedOverdue("2026-11-21", at = time))
            assertEquals(SleepStatus.UNVERIFIED, db.sleepStatus("2026-11-21"))
            assertEquals(0, db.sleepLevel("2026-11-21"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testS08_pausedSpecialCyclePreservesLevelWithoutClearingOrIncrementing() {
        val name = "s08-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-11-21T08:00:00")
        val db = Database(context, name, now = { time })
        try {
            db.saveSleepTargets("00:30", "07:30", 15, 15)

            // Reach level 2 via 2 failures
            db.recordSleepCycle("2026-11-21", "01:30", "07:30")
            time = LocalDateTime.parse("2026-11-22T08:00:00")
            db.recordSleepCycle("2026-11-22", "01:30", "07:30")
            assertEquals(2, db.sleepLevel("2026-11-22"))

            // Day 3: Special pause / exemption
            time = LocalDateTime.parse("2026-11-23T08:00:00")
            val r = db.pauseSleepCycle("2026-11-23", reason = "突发急性肠胃炎发烧")
            assertEquals(SleepStatus.PAUSED, r.status)
            assertTrue(r.excepted)
            assertEquals("突发急性肠胃炎发烧", r.reason)
            // Level remains 2 (not 3, not 0)
            assertEquals(2, r.level)
            assertEquals(0, r.penaltyMinutes)
            assertEquals(0, r.penaltyGames)
            assertEquals(2, db.sleepLevel("2026-11-23"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testS09_correctingFailedRecordReevaluatesWithoutDuplicatePenalties() {
        val name = "s09-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-11-21T08:00:00")
        val db = Database(context, name, now = { time })
        try {
            db.saveSleepTargets("00:30", "07:30", 15, 15)

            // Initial failure at level 1 (5 min penalty scheduled for 11-22)
            val r1 = db.recordSleepCycle("2026-11-21", "01:30", "07:30")
            assertEquals(SleepStatus.FAIL, r1.status)
            assertEquals(1, r1.level)
            assertEquals(1, r1.version)
            assertEquals(5 * 60_000L, db.pendingSleepPenalty("video", "2026-11-22"))

            // User corrects record with actual bed time 23:30 (now PASS)
            val r2 = db.recordSleepCycle("2026-11-21", "23:30", "07:00", reason = "补录修正真实入睡时间")
            assertEquals(SleepStatus.PASS, r2.status)
            assertEquals(0, r2.level)
            assertEquals(2, r2.version)
            assertEquals("补录修正真实入睡时间", r2.reason)

            // Pending penalty cleared
            assertEquals(0L, db.pendingSleepPenalty("video", "2026-11-22"))

            // Next cycle starts from level 0 without double penalty
            time = LocalDateTime.parse("2026-11-22T08:00:00")
            val r3 = db.recordSleepCycle("2026-11-22", "01:30", "07:30")
            assertEquals(SleepStatus.FAIL, r3.status)
            assertEquals(1, r3.level) // Starts fresh at level 1
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testS10_sameSleepEventSubmissionIsIdempotent() {
        val name = "s10-${System.nanoTime()}.db"
        val time = LocalDateTime.parse("2026-11-21T08:00:00")
        val db = Database(context, name, now = { time })
        try {
            db.saveSleepTargets("00:30", "07:30", 15, 15)

            val r1 = db.recordSleepCycle("2026-11-21", "00:00", "07:00")
            assertEquals(1, r1.version)
            assertEquals(SleepStatus.PASS, r1.status)

            // Re-submit identical values
            val r2 = db.recordSleepCycle("2026-11-21", "00:00", "07:00")
            assertEquals(1, r2.version)
            assertEquals(SleepStatus.PASS, r2.status)

            val auditCount = db.readableDatabase.rawQuery("SELECT COUNT(*) FROM audit WHERE day='2026-11-21' AND detail LIKE '睡眠周期评估%'", null).use {
                it.moveToFirst()
                it.getInt(0)
            }
            assertEquals(1, auditCount)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testS11_penaltyTakesEffectOnNextTaskDayWithoutRetroactivelyChasingConsumedRewards() {
        val name = "s11-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-11-21T08:00:00")
        val db = Database(context, name, now = { time })
        try {
            db.saveSleepTargets("00:30", "07:30", 15, 15)

            // Day 1 (11-21): Sleep evaluation FAIL -> Level 1, 5 min penalty effective on 11-22
            val cycle = db.recordSleepCycle("2026-11-21", "01:30", "07:30")
            assertEquals(SleepStatus.FAIL, cycle.status)
            assertEquals("2026-11-22", cycle.effectiveDate)

            // On 11-21: User completes task and earns 10 mins
            db.addTask("2026-11-21", "今日任务", true, 0, "行动型", "完成", 10, 0, "once")
            db.confirm("2026-11-21", false, 0, 0)
            db.complete(db.tasks("2026-11-21").first { it.title == "今日任务" })

            // Penalty is for 11-22, so on 11-21 balance is full 10 mins!
            assertEquals(10 * 60_000L, db.balance("video"))

            // Consume all 10 mins on 11-21
            db.consume("video", 10 * 60_000L)
            assertEquals(0L, db.balance("video"))

            // Advance to 11-22
            time = LocalDateTime.parse("2026-11-22T08:00:00")
            db.ensure("2026-11-22")

            // 11-21 consumed rewards are NOT retroactively chased (balance remains 0, not negative)
            assertEquals(0L, db.balance("video"))
            // 5 min penalty is now pending for 11-22
            assertEquals(5 * 60_000L, db.pendingSleepPenalty("video", "2026-11-22"))

            // Complete task on 11-22 for 15 mins -> 5 mins deducted, 10 mins net balance
            db.addTask("2026-11-22", "明日任务", true, 0, "行动型", "完成", 15, 0, "once")
            db.confirm("2026-11-22", false, 0, 0)
            db.complete(db.tasks("2026-11-22").first { it.title == "明日任务" })

            assertEquals(10 * 60_000L, db.balance("video"))
            assertEquals(0L, db.pendingSleepPenalty("video", "2026-11-22"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testS12_sleepSystemTasksDoNotGrantNormalEntertainmentRewardsOrBlockOthers() {
        val name = "s12-${System.nanoTime()}.db"
        val time = LocalDateTime.parse("2026-11-21T08:00:00")
        val db = Database(context, name, now = { time })
        try {
            db.saveSleepTargets("00:30", "07:30", 15, 15)
            db.enableSleepSystemTasks(true)
            db.ensure("2026-11-21")

            val prepTask = db.tasks("2026-11-21").first { it.title == "按时准备睡眠" }
            val wakeTask = db.tasks("2026-11-21").first { it.title == "按时起床" }
            assertEquals(0, prepTask.rewardMinutes)
            assertEquals(0, prepTask.rewardGames)
            assertEquals(0, wakeTask.rewardMinutes)
            assertEquals(0, wakeTask.rewardGames)

            // Add ordinary mandatory task
            db.addTask("2026-11-21", "常规任务", true, 0, "行动型", "完成", 10, 0, "once")
            db.confirm("2026-11-21", false, 0, 0)

            // Complete system tasks via user entrypoints
            db.prepareSleep("23:15", "2026-11-21")
            db.confirmWake("07:15", actualBedtime = "23:45", cycleDate = "2026-11-21")

            assertTrue(db.tasks("2026-11-21").first { it.title == "按时准备睡眠" }.done)
            assertTrue(db.tasks("2026-11-21").first { it.title == "按时起床" }.done)

            // System tasks do not grant entertainment balance
            assertEquals(0L, db.balance("video"))
            assertEquals(0L, db.balance("game"))

            // Ordinary task completed grants immediate reward (not blocked by system sleep tasks)
            db.complete(db.tasks("2026-11-21").first { it.title == "常规任务" })
            assertEquals(10 * 60_000L, db.balance("video"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testComprehensiveLifecycle_Fail_Paused_Fail_Pass_Progression() {
        // Detailed lifecycle requested in doc:
        // "附一次失败→暂停或缺失→再失败→完整达标的逐日等级、两个池的惩罚和生效日期"
        val name = "lifecycle-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-11-20T08:00:00")
        val db = Database(context, name, now = { time })
        try {
            db.saveSleepTargets("00:30", "07:30", 15, 15)

            // Day 1 (11-20): FAIL
            val d1 = db.recordSleepCycle("2026-11-20", "01:30", "07:30")
            assertEquals(SleepStatus.FAIL, d1.status)
            assertEquals(1, d1.level)
            assertEquals(5, d1.penaltyMinutes)
            assertEquals(0, d1.penaltyGames)
            assertEquals("2026-11-21", d1.effectiveDate)

            // Day 2 (11-21): PAUSED
            time = LocalDateTime.parse("2026-11-21T08:00:00")
            val d2 = db.pauseSleepCycle("2026-11-21", reason = "特殊情况豁免")
            assertEquals(SleepStatus.PAUSED, d2.status)
            assertEquals(1, d2.level) // Level remains 1
            assertEquals(0, d2.penaltyMinutes)
            assertEquals(0, d2.penaltyGames)

            // Day 3 (11-22): FAIL
            time = LocalDateTime.parse("2026-11-22T08:00:00")
            val d3 = db.recordSleepCycle("2026-11-22", "01:30", "07:30")
            assertEquals(SleepStatus.FAIL, d3.status)
            assertEquals(2, d3.level) // Increments by 1 to Level 2
            assertEquals(10, d3.penaltyMinutes)
            assertEquals(1, d3.penaltyGames)
            assertEquals("2026-11-23", d3.effectiveDate)

            // Day 4 (11-23): Complete PASS
            time = LocalDateTime.parse("2026-11-23T08:00:00")
            val d4 = db.recordSleepCycle("2026-11-23", "00:00", "07:00")
            assertEquals(SleepStatus.PASS, d4.status)
            assertEquals(0, d4.level) // Cleared to 0
            assertEquals(0, d4.penaltyMinutes)
            assertEquals(0, d4.penaltyGames)
            assertEquals(0, db.sleepLevel("2026-11-23"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testLegacySleepConfigUpgradeAndCorrectionPrompt() {
        val name = "legacy-config-${System.nanoTime()}.db"
        val time = LocalDateTime.parse("2026-11-21T08:00:00")
        val db = Database(context, name, now = { time })
        try {
            // Emulate legacy database state: default 01:00 to 07:30 (6.5 hours)
            // Existing settings from older schema
            db.writableDatabase.execSQL("INSERT OR REPLACE INTO settings(key, value) VALUES('sleep_bed_target', '01:00')")
            db.writableDatabase.execSQL("INSERT OR REPLACE INTO settings(key, value) VALUES('sleep_wake_target', '07:30')")

            val targets = db.sleepTargets()
            assertEquals("01:00", targets[0])
            assertEquals("07:30", targets[1])
            // Target window is 390 min (< 420 min), which is in conflict
            assertFalse(db.isSleepTargetValid(targets[0], targets[1]))

            // When evaluating sleep under conflicting legacy config:
            // Must NOT automatically penalize the user (avoids auto-fail).
            // Status remains UNVERIFIED, level remains 0, reason prompts correction.
            val r = db.recordSleepCycle("2026-11-21", "01:00", "07:30")
            assertEquals(SleepStatus.UNVERIFIED, r.status)
            assertEquals(0, r.level)
            assertEquals(0, r.penaltyMinutes)
            assertEquals(0, r.penaltyGames)
            assertTrue(r.reason.contains("不足7小时"))
            assertTrue(r.reason.contains("避免自动判罚"))

            // User corrects target to a valid 7-hour window (e.g. 00:30 to 07:30)
            db.saveSleepTargets("00:30", "07:30", 15, 15)
            assertTrue(db.isSleepTargetValid(db.sleepTargets()[0], db.sleepTargets()[1]))

            // Re-evaluating now works under the valid target
            val rCorrected = db.recordSleepCycle("2026-11-21", "00:30", "07:30", reason = "已修正目标配置")
            assertEquals(SleepStatus.PASS, rCorrected.status)
            assertEquals(0, rCorrected.level)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testRepeatedCorrectionWithAlreadyConsumedRewards() {
        val name = "consumed-corr-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-11-21T08:00:00")
        val db = Database(context, name, now = { time })
        try {
            db.saveSleepTargets("00:30", "07:30", 15, 15)

            // Day 1 (11-21): FAIL -> Level 1, 5 min penalty effective on 11-22
            val r1 = db.recordSleepCycle("2026-11-21", "01:30", "07:30")
            assertEquals(SleepStatus.FAIL, r1.status)
            assertEquals(1, r1.level)
            assertEquals(5, r1.penaltyMinutes)

            // Day 2 (11-22): Earn 10m -> 5m penalty deducted -> net 5m
            time = LocalDateTime.parse("2026-11-22T08:00:00")
            db.addTask("2026-11-22", "任务A", true, 0, "行动型", "完成", 10, 0, "once")
            db.confirm("2026-11-22", false, 0, 0)
            db.complete(db.tasks("2026-11-22").first { it.title == "任务A" })

            assertEquals(5 * 60_000L, db.balance("video"))

            // User consumes all 5m on 11-22 -> balance = 0
            db.consume("video", 5 * 60_000L)
            assertEquals(0L, db.balance("video"))

            // Now, user backfills / corrects 11-21's sleep record to PASS (actual sleep 00:00 - 07:00)
            val rCorrected = db.recordSleepCycle("2026-11-21", "00:00", "07:00", reason = "补录实际就寝时间")
            assertEquals(SleepStatus.PASS, rCorrected.status)
            assertEquals(0, rCorrected.level)
            assertEquals(2, rCorrected.version)

            // The 5m unjustified penalty is refunded! Net balance is now 5m
            assertEquals(5 * 60_000L, db.balance("video"))

            // Consumed history remains intact (snapshot shows 5m used, 0 remaining prior to refund)
            val snapshot = db.rewardSnapshot("video", "2026-11-22")
            assertEquals(5 * 60_000L, snapshot.used)
            assertEquals(5 * 60_000L, snapshot.remaining)

            // Re-submitting identical correction again: idempotent, refund is not duplicated!
            val rRepeat = db.recordSleepCycle("2026-11-21", "00:00", "07:00", reason = "补录实际就寝时间")
            assertEquals(2, rRepeat.version)
            assertEquals(5 * 60_000L, db.balance("video"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testCodexIssue1_correctingSameCycleStillFailKeepsLevelAndZeroNewDebit() {
        // Codex 验收点 1：已扣 5 分钟，修改入睡时间后仍为 FAIL：等级保持 1，新增扣减为 0；再次提交相同更正，结果相同。
        val name = "codex-issue1-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-11-20T08:00:00")
        val db = Database(context, name, now = { time })
        try {
            db.saveSleepTargets("00:30", "07:30", 15, 15)

            // Cycle 1 (11-20): Initial FAIL at Level 1, 5m penalty effective on 11-21
            val r1 = db.recordSleepCycle("2026-11-20", "01:30", "07:30")
            assertEquals(SleepStatus.FAIL, r1.status)
            assertEquals(1, r1.level)
            assertEquals(5, r1.penaltyMinutes)
            assertEquals("2026-11-21", r1.effectiveDate)

            // On 11-21: User earns 10m reward -> 5m deducted by pending penalty -> 5m balance
            time = LocalDateTime.parse("2026-11-21T08:00:00")
            db.addTask("2026-11-21", "任务1", true, 0, "行动型", "完成", 10, 0, "once")
            db.confirm("2026-11-21", false, 0, 0)
            db.complete(db.tasks("2026-11-21").first { it.title == "任务1" })

            assertEquals(5 * 60_000L, db.balance("video"))
            // 5m was deducted
            assertEquals(0L, db.pendingSleepPenalty("video", "2026-11-21"))

            // User consumes all 5m on 11-21
            db.consume("video", 5 * 60_000L)
            assertEquals(0L, db.balance("video"))

            // User modifies bedtime of 11-20 from 01:30 to 01:15 (still > 00:30, still FAIL!)
            val r2 = db.recordSleepCycle("2026-11-20", "01:15", "07:30", reason = "修正实际入睡时间")
            assertEquals(SleepStatus.FAIL, r2.status)
            // 等级保持 1
            assertEquals(1, r2.level)
            assertEquals(5, r2.penaltyMinutes)
            assertEquals(2, r2.version)

            // 新增扣减为 0
            assertEquals(0L, db.pendingSleepPenalty("video", "2026-11-21"))
            // Balance remains 0 (not negative, no extra deduction)
            assertEquals(0L, db.balance("video"))

            // 再次提交相同更正，结果相同
            val r3 = db.recordSleepCycle("2026-11-20", "01:15", "07:30", reason = "修正实际入睡时间")
            assertEquals(SleepStatus.FAIL, r3.status)
            assertEquals(1, r3.level)
            assertEquals(5, r3.penaltyMinutes)
            assertEquals(2, r3.version) // Idempotent
            assertEquals(0L, db.pendingSleepPenalty("video", "2026-11-21"))
            assertEquals(0L, db.balance("video"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testCodexIssue2_cascadingRecalculationReducesSubsequentFailLevelAndPenalties() {
        // Codex 验收点 2：前一晚从 FAIL 改为 PASS，后一晚仍为 FAIL：后一晚尚未生效的惩罚从 10 分钟、1 局变为 5 分钟、0 局。已消费流水保留，已过期额度不重新变成可用余额。
        val name = "codex-issue2-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-11-20T08:00:00")
        val db = Database(context, name, now = { time })
        try {
            db.saveSleepTargets("00:30", "07:30", 15, 15)

            // Night 1 (11-20): FAIL -> Level 1, 5m video, 0g games, effective 11-21
            val r1 = db.recordSleepCycle("2026-11-20", "01:30", "07:30")
            assertEquals(SleepStatus.FAIL, r1.status)
            assertEquals(1, r1.level)
            assertEquals(5, r1.penaltyMinutes)
            assertEquals(0, r1.penaltyGames)

            // Night 2 (11-21): FAIL -> Level 2, 10m video, 1g games, effective 11-22
            time = LocalDateTime.parse("2026-11-21T08:00:00")
            val r2 = db.recordSleepCycle("2026-11-21", "01:30", "07:30")
            assertEquals(SleepStatus.FAIL, r2.status)
            assertEquals(2, r2.level)
            assertEquals(10, r2.penaltyMinutes)
            assertEquals(1, r2.penaltyGames)

            // Before 11-22 penalties take effect:
            assertEquals(10 * 60_000L, db.pendingSleepPenalty("video", "2026-11-22", "2026-11-21"))
            assertEquals(1L, db.pendingSleepPenalty("game", "2026-11-22", "2026-11-21"))

            // User corrects Night 1 (11-20) from FAIL to PASS
            val r1Fixed = db.recordSleepCycle("2026-11-20", "00:00", "07:00", reason = "补录修正为按时睡觉")
            assertEquals(SleepStatus.PASS, r1Fixed.status)
            assertEquals(0, r1Fixed.level)

            // Cascading recalculation has updated Night 2 (11-21):
            val r2Updated = db.sleepRecord("2026-11-21")!!
            assertEquals(SleepStatus.FAIL, r2Updated.status)
            // 等级从 2 降为 1
            assertEquals(1, r2Updated.level)
            // 尚未生效的惩罚从 10 分钟、1 局变为 5 分钟、0 局！
            assertEquals(5, r2Updated.penaltyMinutes)
            assertEquals(0, r2Updated.penaltyGames)
            assertEquals(5 * 60_000L, db.pendingSleepPenalty("video", "2026-11-22", "2026-11-21"))
            assertEquals(0L, db.pendingSleepPenalty("game", "2026-11-22", "2026-11-21"))
            assertEquals(5 * 60_000L, db.pendingSleepPenalty("video", "2026-11-22"))
            assertEquals(0L, db.pendingSleepPenalty("game", "2026-11-22"))

            // Verify global failure level
            assertEquals(1, db.sleepLevel("2026-11-21"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testManualSleepInputWithoutAutoRecord_staysUnverified_noLevelChange_noDeductions_repeatAndRestartPreserved() {
        val name = "s13-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-11-23T08:00:00")
        var db = Database(context, name, now = { time })
        try {
            db.saveSleepTargets("00:30", "07:30", 15, 15)
            assertEquals(0, db.sleepLevel("2026-11-23"))
            assertEquals(0L, db.pendingSleepPenalty("video"))
            assertEquals(0L, db.pendingSleepPenalty("game"))

            // 1. 无自动记录 -> 人工填6小时 (01:30 - 07:30 = 360 min < 420 min)
            val r1 = db.recordSleepCycle(
                cycleDate = "2026-11-23",
                bedtime = "01:30",
                wakeTime = "07:30",
                reason = "Synthetic_test_only",
                source = "manual"
            )

            // 保持待核验，不能升级、清零或产生新增扣减
            assertEquals(SleepStatus.UNVERIFIED, r1.status)
            assertEquals(0, r1.level)
            assertEquals(0, r1.penaltyMinutes)
            assertEquals(0, r1.penaltyGames)
            assertEquals(360L, r1.durationMinutes)
            assertEquals("manual", r1.source)
            assertEquals("Synthetic_test_only", r1.reason)
            assertEquals(0, db.sleepLevel("2026-11-23"))
            assertEquals(0L, db.pendingSleepPenalty("video"))
            assertEquals(0L, db.pendingSleepPenalty("game"))

            // 2. 重复提交
            val r2 = db.recordSleepCycle(
                cycleDate = "2026-11-23",
                bedtime = "01:30",
                wakeTime = "07:30",
                reason = "Synthetic_test_only",
                source = "manual"
            )
            assertEquals(SleepStatus.UNVERIFIED, r2.status)
            assertEquals(0, r2.level)
            assertEquals(0, r2.penaltyMinutes)
            assertEquals(0, r2.penaltyGames)
            assertEquals(0, db.sleepLevel("2026-11-23"))
            assertEquals(0L, db.pendingSleepPenalty("video"))
            assertEquals(0L, db.pendingSleepPenalty("game"))

            // 3. 重启 (重新打开数据库)
            db.close()
            db = Database(context, name, now = { time })

            val reloaded = db.sleepRecord("2026-11-23")
            assertNotNull(reloaded)
            assertEquals(SleepStatus.UNVERIFIED, reloaded!!.status)
            assertEquals(0, reloaded.level)
            assertEquals(0, reloaded.penaltyMinutes)
            assertEquals(0, reloaded.penaltyGames)
            assertEquals(360L, reloaded.durationMinutes)
            assertEquals("01:30", reloaded.bedtime)
            assertEquals("07:30", reloaded.wakeTime)
            assertEquals("Synthetic_test_only", reloaded.reason)
            assertEquals("manual", reloaded.source)

            // 两个奖励池均无新增变化
            assertEquals(0, db.sleepLevel("2026-11-23"))
            assertEquals(0L, db.pendingSleepPenalty("video"))
            assertEquals(0L, db.pendingSleepPenalty("game"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testLegacySleepSystemTasks_doNotPollute_taskCount_xp_achievements_dailyCards_growthStats() {
        val name = "s14-${System.nanoTime()}.db"
        val today = "2026-11-23"
        val time = LocalDateTime.parse("${today}T12:00:00")
        val db = Database(context, name, now = { time })
        try {
            // 1. 创建普通任务
            db.addTask(today, "普通必做任务", true, 0, "行动型", "自检完成", 10, 0, "once")
            db.addTask(today, "普通选做任务", false, 0, "行动型", "自检完成", 0, 0, "once")
            val ordinaryReq = db.tasks(today).first { it.title == "普通必做任务" }
            db.complete(ordinaryReq) // 普通必做完成

            // 2. 插入旧睡眠系统任务（已完成）
            val writable = db.writableDatabase
            val nowMs = System.currentTimeMillis()
            writable.execSQL(
                """
                INSERT INTO tasks (id, day, title, required, minutes, mode, checklist, reward_minutes, reward_games, elapsed, done, claimed, completed_at, recurrence, origin)
                VALUES ('sleep_prep_legacy', '$today', '按时准备睡眠', 1, 0, '行动型', '系统睡眠管理任务', 0, 0, 0, 1, 0, $nowMs, 'daily', 'sleep_system')
                """.trimIndent()
            )
            writable.execSQL(
                """
                INSERT INTO tasks (id, day, title, required, minutes, mode, checklist, reward_minutes, reward_games, elapsed, done, claimed, completed_at, recurrence, origin)
                VALUES ('sleep_wake_legacy', '$today', '按时起床', 1, 0, '行动型', '系统睡眠管理任务', 0, 0, 0, 1, 0, $nowMs, 'daily', 'sleep_system')
                """.trimIndent()
            )

            // 3. 验证历史记录被完整保留在库中（4个任务均在）
            val allTasks = db.tasks(today)
            assertEquals(4, allTasks.size)
            assertTrue(allTasks.any { it.title == "按时准备睡眠" && it.origin == "sleep_system" && it.done })
            assertTrue(allTasks.any { it.title == "按时起床" && it.origin == "sleep_system" && it.done })

            // 4. 验证普通任务完成数不被污染：普通完成只有1个（普通必做任务）
            val ordinaryTasks = allTasks.filter { it.origin != "sleep_system" }
            assertEquals(2, ordinaryTasks.size)
            assertEquals(1, ordinaryTasks.count { it.done })

            // 5. 验证 XP 不被污染：只有普通任务完成给 100 XP，旧睡眠系统任务不给 XP
            assertEquals(100, db.xp())

            // 6. 验证成就系统不被污染：成就贡献任务列表只包含普通任务
            val achievements = achievementList(db)
            val taskFirst = achievements.first { it.id == "task.first" }
            assertTrue(taskFirst.earned)
            val taskTen = achievements.first { it.id == "task.ten" }
            assertEquals(1, taskTen.progress) // 只有普通任务1个，不是3个！

            // 7. 验证每日纪念 (DailyCards) 不被污染：
            val card = DailyCards.reconcile(db, today, allTasks)
            assertNotNull(card)
            assertEquals(1, card!!.completed) // 只有普通完成数 1，不是 3
            assertEquals(2, card.total) // 只有普通任务总数 2，不是 4
            assertFalse(card.taskTitles.contains("按时准备睡眠"))
            assertFalse(card.taskTitles.contains("按时起床"))

            // 8. 验证成长统计 (Growth stats) 不被污染：
            val stats = db.growthPeriodStats(today)
            assertEquals(1, stats.achievedDays) // 普通必做完成了，达成天数是1
            assertEquals(1, stats.planDays)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
