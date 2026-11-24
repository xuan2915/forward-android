package com.focus.prototype

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class SleepPendingCyclePreservationTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun passingLatestCycleClearsOnlyItsOwnFuturePenalty() {
        val name = "sleep-pending-cycle-${System.nanoTime()}.db"
        var now = LocalDateTime.of(2026, 11, 21, 8, 0)
        val db = Database(context, name, now = { now })
        try {
            db.saveSleepTargets("00:30", "07:30")
            val priorFailure = db.recordSleepCycle("2026-11-20", "01:30", "07:30")
            assertEquals(SleepStatus.FAIL, priorFailure.status)
            assertEquals(5 * 60_000L, db.pendingSleepPenalty("video", date = "2026-11-21"))

            now = LocalDateTime.of(2026, 11, 22, 8, 0)
            val currentFailure = db.recordSleepCycle("2026-11-21", "01:30", "07:30")
            assertEquals(SleepStatus.FAIL, currentFailure.status)
            assertEquals(10 * 60_000L, db.pendingSleepPenalty("video", date = "2026-11-22"))

            val corrected = db.recordSleepCycle("2026-11-21", "00:00", "07:30", reason = "修正睡眠记录")
            assertEquals(SleepStatus.PASS, corrected.status)
            assertEquals(5 * 60_000L, db.pendingSleepPenalty("video", date = "2026-11-21"))
            assertEquals(0L, db.pendingSleepPenalty("video", date = "2026-11-22"))
            assertEquals(0, db.sleepLevel("2026-11-21"))
            assertNotNull(db.sleepRecord("2026-11-20"))
            assertTrue(db.sleepRecord("2026-11-20")!!.status == SleepStatus.FAIL)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
