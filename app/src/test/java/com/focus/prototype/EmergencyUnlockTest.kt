package com.focus.prototype

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class EmergencyUnlockTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun testE01_firstEmergencyUnlockApplicationSucceeds() {
        val name = "e01-${System.nanoTime()}.db"
        var wallTime = 1_700_000_000_000L
        var time = LocalDateTime.of(2026, 11, 24, 10, 0, 0)
        val db = Database(context, name, wallNow = { wallTime }, now = { time })
        try {
            val (can, reason) = db.canEmergencyUnlock("com.example.app", wallTime)
            assertTrue(can)
            assertNull(reason)

            val record = db.requestEmergencyUnlock("com.example.app", "紧急查收邮件", 10, wallTime)
            assertEquals("com.example.app", record.packageName)
            assertEquals("紧急查收邮件", record.reason)
            assertEquals(10, record.durationMinutes)
            assertEquals("ACTIVE", record.status)
            assertEquals(wallTime + 10 * 60_000L, record.expiresAt)

            assertTrue(db.isEmergencyUnlocked("com.example.app", wallTime))
            assertNotNull(db.activeEmergencyUnlock(wallTime))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testE02_reapplyingWithin24HoursIsRejected() {
        val name = "e02-${System.nanoTime()}.db"
        var wallTime = 1_700_000_000_000L
        var time = LocalDateTime.of(2026, 11, 24, 10, 0, 0)
        val db = Database(context, name, wallNow = { wallTime }, now = { time })
        try {
            db.requestEmergencyUnlock("com.example.app", "第一次申请", 10, wallTime)

            // 1 hour later
            wallTime += 3600 * 1000L
            val (can, reason) = db.canEmergencyUnlock("com.example.app", wallTime)
            assertFalse(can)
            assertNotNull(reason)
            assertTrue(reason!!.contains("24 小时"))

            try {
                db.requestEmergencyUnlock("com.example.app", "第二次申请", 10, wallTime)
                fail("Should reject re-applying within 24 hours")
            } catch (e: IllegalStateException) {
                assertTrue(e.message!!.contains("24 小时"))
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testE03_exceedingThreeUnlocksWithin168HoursIsRejected() {
        val name = "e03-${System.nanoTime()}.db"
        var wallTime = 1_700_000_000_000L
        var time = LocalDateTime.of(2026, 11, 20, 10, 0, 0)
        val db = Database(context, name, wallNow = { wallTime }, now = { time })
        try {
            // Unlock 1 at 0h
            db.requestEmergencyUnlock("com.example.app", "第1次", 10, wallTime)

            // Unlock 2 at 25h (passes 24h limit)
            wallTime += 25 * 3600 * 1000L
            time = time.plusHours(25)
            assertTrue(db.canEmergencyUnlock("com.example.app", wallTime).first)
            db.requestEmergencyUnlock("com.example.app", "第2次", 10, wallTime)

            // Unlock 3 at 50h (passes 24h limit)
            wallTime += 25 * 3600 * 1000L
            time = time.plusHours(25)
            assertTrue(db.canEmergencyUnlock("com.example.app", wallTime).first)
            db.requestEmergencyUnlock("com.example.app", "第3次", 10, wallTime)

            // Attempt Unlock 4 at 75h (within 168h from start)
            wallTime += 25 * 3600 * 1000L
            time = time.plusHours(25)
            val stats = db.emergencyUnlockStats("com.example.app", wallTime)
            assertEquals(3, stats.used168h)
            assertFalse(stats.canUnlock)
            assertTrue(stats.rejectReason!!.contains("168 小时"))

            // Advance past 168 hours from first unlock (e.g. 170 hours from start)
            wallTime += (170 - 75) * 3600 * 1000L
            time = time.plusHours(170 - 75)
            val statsAfter168h = db.emergencyUnlockStats("com.example.app", wallTime)
            assertTrue(statsAfter168h.canUnlock)
            val record4 = db.requestEmergencyUnlock("com.example.app", "第4次（旧记录已滑出168h）", 10, wallTime)
            assertEquals("ACTIVE", record4.status)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testE04_tenMinutesExpirationEndsUnlock() {
        val name = "e04-${System.nanoTime()}.db"
        var wallTime = 1_700_000_000_000L
        var time = LocalDateTime.of(2026, 11, 24, 10, 0, 0)
        val db = Database(context, name, wallNow = { wallTime }, now = { time })
        try {
            db.requestEmergencyUnlock("com.example.app", "限时放行", 10, wallTime)

            // At 9 min 59 sec
            wallTime += (10 * 60 - 1) * 1000L
            assertTrue(db.isEmergencyUnlocked("com.example.app", wallTime))

            // At 10 min 1 sec
            wallTime += 2000L
            assertFalse(db.isEmergencyUnlocked("com.example.app", wallTime))
            assertNull(db.activeEmergencyUnlock(wallTime))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testE05_reenteringAppDoesNotGrantAdditionalTime() {
        val name = "e05-${System.nanoTime()}.db"
        var wallTime = 1_700_000_000_000L
        var time = LocalDateTime.of(2026, 11, 24, 10, 0, 0)
        val db = Database(context, name, wallNow = { wallTime }, now = { time })
        try {
            val initialRecord = db.requestEmergencyUnlock("com.example.app", "放行", 10, wallTime)

            // 5 minutes later, user exits and re-enters
            wallTime += 5 * 60_000L
            val active = db.activeEmergencyUnlock(wallTime)
            assertNotNull(active)
            assertEquals(initialRecord.expiresAt, active!!.expiresAt)
            assertEquals(5 * 60_000L, active.expiresAt - wallTime)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testE06_controlledAppSwitchingDoesNotUnlockOtherApps() {
        val name = "e06-${System.nanoTime()}.db"
        var wallTime = 1_700_000_000_000L
        var time = LocalDateTime.of(2026, 11, 24, 10, 0, 0)
        val db = Database(context, name, wallNow = { wallTime }, now = { time })
        try {
            db.requestEmergencyUnlock("com.example.video", "仅解锁视频", 10, wallTime)

            assertTrue(db.isEmergencyUnlocked("com.example.video", wallTime))
            assertFalse(db.isEmergencyUnlocked("com.example.game", wallTime))
            assertFalse(db.isEmergencyUnlocked("com.other.app", wallTime))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testE07_dailyUsageCapExhaustionRejectsUnlock() {
        val name = "e07-${System.nanoTime()}.db"
        var wallTime = 1_700_000_000_000L
        var time = LocalDateTime.of(2026, 11, 24, 10, 0, 0)
        val db = Database(context, name, wallNow = { wallTime }, now = { time })
        try {
            val date = db.day()
            db.setRewardKind("com.example.video", "video")
            db.setDailyUsageCap("video", 10 * 60_000L) // 10 min cap

            // Grant 20 min reward
            db.addTask(date, "必做1", true, 0, "行动型", "检查项", 20, 0, "once")
            db.confirm(date, false, 0, 0)
            db.complete(db.tasks(date).first { it.title == "必做1" })

            // Consume full 10 min cap
            db.consume("video", 10 * 60_000L)
            assertEquals(0L, db.balance("video"))

            // Daily usage cap is exhausted
            val (can, reason) = db.canEmergencyUnlock("com.example.video", wallTime)
            assertFalse(can)
            assertNotNull(reason)
            assertTrue(reason!!.contains("上限已耗尽"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testE08_processRestartPreservesOriginalExpiration() {
        val name = "e08-${System.nanoTime()}.db"
        var wallTime = 1_700_000_000_000L
        var time = LocalDateTime.of(2026, 11, 24, 10, 0, 0)
        val db1 = Database(context, name, wallNow = { wallTime }, now = { time })
        val origRecord = db1.requestEmergencyUnlock("com.example.app", "跨进程重启", 10, wallTime)
        db1.close()

        // Restart process: open new Database instance
        wallTime += 4 * 60_000L
        val db2 = Database(context, name, wallNow = { wallTime }, now = { time })
        try {
            assertTrue(db2.isEmergencyUnlocked("com.example.app", wallTime))
            val active = db2.activeEmergencyUnlock(wallTime)
            assertNotNull(active)
            assertEquals(origRecord.expiresAt, active!!.expiresAt)

            // Past 10 min
            wallTime += 7 * 60_000L
            assertFalse(db2.isEmergencyUnlocked("com.example.app", wallTime))
        } finally {
            db2.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testE09_manualEarlyTerminationEndsUnlockAndKeepsCount() {
        val name = "e09-${System.nanoTime()}.db"
        var wallTime = 1_700_000_000_000L
        var time = LocalDateTime.of(2026, 11, 24, 10, 0, 0)
        val db = Database(context, name, wallNow = { wallTime }, now = { time })
        try {
            val record = db.requestEmergencyUnlock("com.example.app", "提前结束测试", 10, wallTime)
            assertTrue(db.isEmergencyUnlocked("com.example.app", wallTime))

            // User ends unlock early at +2m
            wallTime += 2 * 60_000L
            assertTrue(db.endEmergencyUnlock(record.id, wallTime))

            assertFalse(db.isEmergencyUnlocked("com.example.app", wallTime))
            assertNull(db.activeEmergencyUnlock(wallTime))

            // But 24h quota is still consumed!
            val stats = db.emergencyUnlockStats("com.example.app", wallTime)
            assertEquals(1, stats.used24h)
            assertFalse(stats.canUnlock)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testE10_emergencyUnlockGrantsNoXPOrRewardBalance() {
        val name = "e10-${System.nanoTime()}.db"
        var wallTime = 1_700_000_000_000L
        var time = LocalDateTime.of(2026, 11, 24, 10, 0, 0)
        val db = Database(context, name, wallNow = { wallTime }, now = { time })
        try {
            val xpBefore = db.xp()
            val videoBefore = db.balance("video")
            val gameBefore = db.balance("game")

            db.requestEmergencyUnlock("com.example.app", "不发奖测试", 10, wallTime)

            assertEquals(xpBefore, db.xp())
            assertEquals(videoBefore, db.balance("video"))
            assertEquals(gameBefore, db.balance("game"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testSleepPendingDeductionDoesNotAccumulateHistoricalBacklog() {
        val name = "sleep-pending-fix-${System.nanoTime()}.db"
        var time = LocalDateTime.of(2026, 11, 20, 10, 0, 0)
        val db = Database(context, name, now = { time })
        try {
            // Simulate 6 days of historical sleep failure records in settings (5+10+15+20+25+30 = 105 min)
            db.setSetting("sleep_pending_video_2026-11-14_2026-11-14", (5 * 60_000L).toString())
            db.setSetting("sleep_pending_video_2026-11-15_2026-11-15", (10 * 60_000L).toString())
            db.setSetting("sleep_pending_video_2026-11-16_2026-11-16", (15 * 60_000L).toString())
            db.setSetting("sleep_pending_video_2026-11-17_2026-11-17", (20 * 60_000L).toString())
            db.setSetting("sleep_pending_video_2026-11-18_2026-11-18", (25 * 60_000L).toString())
            db.setSetting("sleep_pending_video_2026-11-19_2026-11-19", (30 * 60_000L).toString())

            // On today's date (2026-11-20), there is no pending sleep penalty, so it returns 0!
            assertEquals(0L, db.pendingSleepPenalty("video", date = "2026-11-20"))

            // Querying a specific historical date returns only that date's pending penalty
            assertEquals(30 * 60_000L, db.pendingSleepPenalty("video", date = "2026-11-19"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
