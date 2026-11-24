package com.focus.prototype

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class SleepImportArrivalTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun lateArrivingSleepSessionImportsOnNextForegroundSyncForItsWakeDate() {
        val name = "sleep-arrival-${System.nanoTime()}.db"
        val db = Database(context, name)
        try {
            db.saveSleepTargets("00:30", "07:30")
            val source = MockSleepDataSource()
            assertEquals(
                ImportOutcome.SUCCESS_EMPTY,
                SleepImportManager.syncFromAvailableSources(context, db, source).outcome
            )

            val wakeDate = LocalDate.now(ZoneId.systemDefault()).minusDays(1)
            val start = wakeDate.minusDays(1).atTime(23, 30).atZone(ZoneId.systemDefault()).toInstant()
            val end = wakeDate.atTime(7, 30).atZone(ZoneId.systemDefault()).toInstant()
            source.mockRecords += RawSleepRecord(
                id = "late-session-${System.nanoTime()}",
                sourcePackage = "test.sleep.source",
                startTime = start,
                endTime = end,
                lastModifiedTime = end,
                stages = listOf(RawSleepStage(start, end, 4)),
                isAsleepConfirmed = true,
                actualSleepMinutes = 420L
            )

            val result = SleepImportManager.syncFromAvailableSources(context, db, source)

            assertEquals(ImportOutcome.SUCCESS_IMPORTED, result.outcome)
            assertEquals(1, result.importedCount)
            assertEquals(wakeDate.toString(), result.cycleDate)
            assertNotNull(db.sleepRecord(wakeDate.toString()))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
