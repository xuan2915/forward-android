package com.focus.prototype

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class SleepPenaltyExpiryTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun expiredDailyPenaltiesAreNotShownOrChargedOnLaterDays() {
        val name = "sleep-penalty-expiry-${System.nanoTime()}.db"
        val today = "2026-11-20"
        val db = Database(context, name, now = { LocalDateTime.of(2026, 11, 20, 10, 0) })
        val historicalMinutes = listOf(5, 10, 15, 20, 25, 30)
        val historicalDates = listOf(
            "2026-11-14", "2026-11-15", "2026-11-16",
            "2026-11-17", "2026-11-18", "2026-11-19"
        )
        try {
            historicalDates.zip(historicalMinutes).forEach { (date, minutes) ->
                db.setSetting("sleep_pending_video_${date}_$date", (minutes * 60_000L).toString())
            }
            db.setSetting("sleep_pending_video_${today}_$today", (10 * 60_000L).toString())
            db.setSetting("sleep_pending_video_2026-11-21_2026-11-21", (5 * 60_000L).toString())

            // Only today's scheduled amount is pending for today's reward settlement.
            assertEquals(10 * 60_000L, db.pendingSleepPenalty("video"))

            db.addTask(today, "当天任务", true, 0, "行动型", "检查项", 20, 0, "once")
            db.confirm(today, false, 0, 0)
            db.complete(db.tasks(today).first { it.title == "当天任务" })

            assertEquals(10 * 60_000L, db.balance("video"))
            historicalDates.zip(historicalMinutes).forEach { (date, minutes) ->
                assertEquals(
                    (minutes * 60_000L).toString(),
                    db.setting("sleep_pending_video_${date}_$date")
                )
            }
            assertEquals("0", db.setting("sleep_pending_video_${today}_$today"))
            assertEquals("300000", db.setting("sleep_pending_video_2026-11-21_2026-11-21"))
            assertEquals(0L, db.pendingSleepPenalty("video", date = today))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
