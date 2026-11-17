package com.focus.prototype

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RuntimeEnvironment

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [28], manifest = org.robolectric.annotation.Config.NONE)
class NightWrapUpTest {
    @Test fun overnightScheduleIsIndependentOfMidnightAndRewardBoundary() {
        val config = NightWrapUpConfig()
        val cycle = LocalDate.parse("2026-11-12")
        assertEquals(LocalDateTime.parse("2026-11-12T22:00"), config.at(cycle, NightStage.WIND_DOWN))
        assertEquals(LocalDateTime.parse("2026-11-13T00:00"), config.at(cycle, NightStage.ENTERTAINMENT_CUTOFF))
        assertEquals(LocalDateTime.parse("2026-11-13T07:30"), config.at(cycle, NightStage.MORNING_RELEASE))
        assertTrue(config.entertainmentRestricted(LocalDateTime.parse("2026-11-13T00:20")))
        assertTrue(config.entertainmentRestricted(LocalDateTime.parse("2026-11-13T04:00")))
        assertFalse(config.entertainmentRestricted(LocalDateTime.parse("2026-11-13T07:30")))
        assertEquals(cycle, config.cycleDate(LocalDateTime.parse("2026-11-13T00:20")))
        assertEquals(cycle, config.latestFinishedCycle(LocalDateTime.parse("2026-11-13T08:00")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidStageOrderCannotBeSaved() {
        NightWrapUpConfig(windDown = "23:00", workCutoff = "22:30").validate()
    }

    @Test fun interceptedEventIsIdempotentAndDeductedOnlyFromFutureVideoRewards() {
        val date = "2026-11-13"
        val time = LocalDateTime.parse("2026-11-13T00:20")
        val observedAt = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val context = RuntimeEnvironment.getApplication()
        val name = "night-wrap-${System.nanoTime()}.db"
        val db = Database(context, name, { observedAt }, { time })
        try {
            assertTrue(db.recordNightViolation("event-1", "com.ss.android.ugc.aweme", observedAt))
            assertFalse(db.recordNightViolation("event-1", "com.ss.android.ugc.aweme", observedAt))
            assertTrue(db.recordNightViolation("event-2", "com.ss.android.ugc.aweme", observedAt + 1))
            assertTrue(db.recordNightViolation("event-3", "com.ss.android.ugc.aweme", observedAt + 2))
            assertTrue(db.recordNightViolation("game-event-1", "com.tencent.tmgp.sgame", observedAt + 3))
            assertFalse(db.recordNightViolation("game-event-1", "com.tencent.tmgp.sgame", observedAt + 3))
            assertEquals(4, db.nightViolations("2026-11-12").size)
            assertEquals(10L * 60_000L, db.pendingNightPenalty())
            assertEquals(1L, db.pendingNightPenalty("game"))

            val taskDate = db.day()
            val title4 = "奖励任务 4 ${System.nanoTime()}"
            val title10 = "奖励任务 10 ${System.nanoTime()}"
            val titleGame = "游戏奖励任务 ${System.nanoTime()}"
            db.addTask(taskDate, title4, true, 0, "行动型", "完成", 4, 0, "once")
            db.addTask(taskDate, title10, true, 0, "行动型", "完成", 10, 0, "once")
            db.addTask(taskDate, titleGame, true, 0, "行动型", "完成", 0, 3, "once")
            db.confirm(taskDate, false, 0, 0)
            db.complete(db.tasks(taskDate).first { it.title == title4 })
            assertEquals(0L, db.balance("video"))
            assertEquals(6L * 60_000L, db.pendingNightPenalty())
            db.complete(db.tasks(taskDate).first { it.title == title10 })
            db.complete(db.tasks(taskDate).first { it.title == titleGame })
            assertEquals(4L * 60_000L, db.balance("video"))
            assertEquals(0L, db.pendingNightPenalty())
            assertEquals(10L * 60_000L, db.nightDeducted(db.rewardDay()))
            assertEquals(2L, db.balance("game"))
            assertEquals(1L, db.nightDeducted(db.rewardDay(), "game"))
            assertEquals(0L, db.pendingNightPenalty("game"))
            assertTrue(db.rewardHistory().any { it.contains("夜间收尾抵扣") })
            val backup = Backup.export(context, db)
            assertEquals(4, org.json.JSONObject(org.json.JSONObject(backup).getString("payload")).getJSONObject("data").getJSONArray("night_violations").length())
            db.recordNightViolation("event-after-backup", "com.ss.android.ugc.aweme", observedAt + 4)
            Backup.restore(context, db, backup)
            assertEquals(4, db.nightViolations("2026-11-12").size)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun upgradingFromVersionFivePreservesSettingsAndCreatesNightEventStorage() {
        val context = RuntimeEnvironment.getApplication()
        val name = "night-upgrade-${System.nanoTime()}.db"
        val old = Database(context, name)
        old.setSetting("keep_me", "yes")
        old.writableDatabase.version = 5
        old.close()
        val upgraded = Database(context, name)
        try {
            assertEquals("yes", upgraded.setting("keep_me"))
            assertTrue(upgraded.nightViolations("2026-11-12").isEmpty())
            assertEquals(8, upgraded.writableDatabase.version)
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun undoingTheRewardThatConsumedNightDebtRestoresTheDebtForTheNextGrant() {
        val dateTime = LocalDateTime.parse("2026-11-13T00:20")
        val observedAt = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val context = RuntimeEnvironment.getApplication()
        val name = "night-revoke-${System.nanoTime()}.db"
        val db = Database(context, name, { observedAt }, { dateTime })
        try {
            db.recordNightViolation("event-revoke", "com.ss.android.ugc.aweme", observedAt)
            val taskDate = db.day()
            val title = "夜间冲回验证 ${System.nanoTime()}"
            db.addTask(taskDate, title, true, 0, "行动型", "完成", 5, 0, "once")
            db.confirm(taskDate, false, 0, 0)
            db.complete(db.tasks(taskDate).single { it.title == title })
            assertEquals(0L, db.balance("video"))
            assertEquals(0L, db.pendingNightPenalty())

            db.revoke(db.tasks(taskDate).single { it.title == title })
            assertEquals(0L, db.nightDeducted(db.rewardDay()))
            assertEquals(5L * 60_000L, db.pendingNightPenalty())
            db.complete(db.tasks(taskDate).single { it.title == title })
            assertEquals(0L, db.balance("video"))
            assertEquals(0L, db.pendingNightPenalty())
            assertEquals(5L * 60_000L, db.nightDeducted(db.rewardDay()))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
