package com.focus.prototype

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class EmergencyUsageCapTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun emergencyUsageCountsTowardDailyCapWithoutDebitingRewardBalance() {
        val name = "emergency-usage-${System.nanoTime()}.db"
        val date = LocalDateTime.of(2026, 11, 24, 10, 0)
        var nowMs = date.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val db = Database(context, name, wallNow = { nowMs }, now = { date })
        try {
            val app = "com.example.video"
            db.setRewardKind(app, "video")
            db.setDailyUsageCap("video", 10 * 60_000L)
            val taskDate = db.day()
            db.addTask(taskDate, "奖励", true, 0, "行动型", "", 20, 0, "once")
            db.confirm(taskDate, false, 0, 0)
            db.complete(db.tasks(taskDate).single())
            val balanceBefore = db.balance("video")
            assertEquals(10 * 60_000L, balanceBefore)
            val ledgerTotalBefore = db.readableDatabase.rawQuery(
                "SELECT COALESCE(SUM(amount),0) FROM ledger WHERE day=? AND kind='video'",
                arrayOf(taskDate)
            ).use { it.moveToFirst(); it.getLong(0) }

            db.requestEmergencyUnlock(app, "必要操作", 10, nowMs)
            assertEquals(6 * 60_000L, db.recordEmergencyVideoUsage(app, 4 * 60_000L, nowMs))
            assertTrue(db.isEmergencyUnlocked(app, nowMs))
            assertEquals(6 * 60_000L, db.balance("video"))

            nowMs += 60_000L
            assertEquals(0L, db.recordEmergencyVideoUsage(app, 7 * 60_000L, nowMs))
            assertFalse(db.isEmergencyUnlocked(app, nowMs))
            assertNull(db.activeEmergencyUnlock(nowMs))
            assertEquals(10 * 60_000L, db.consumedOnDate(date = "2026-11-24", kind = "video"))
            assertEquals(0L, db.balance("video"))
            val ledgerTotalAfter = db.readableDatabase.rawQuery(
                "SELECT COALESCE(SUM(amount),0) FROM ledger WHERE day=? AND kind='video'",
                arrayOf(taskDate)
            ).use { it.moveToFirst(); it.getLong(0) }
            assertEquals(ledgerTotalBefore, ledgerTotalAfter)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
