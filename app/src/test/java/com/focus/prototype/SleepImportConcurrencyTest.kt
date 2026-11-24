package com.focus.prototype

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class SleepImportConcurrencyTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun concurrentForegroundReadsSettleTheSameSourceRevisionOnlyOnce() {
        val name = "sleep-concurrent-${System.nanoTime()}.db"
        val db = Database(context, name)
        val now = Instant.now()
        val wakeDate = LocalDate.now(ZoneId.systemDefault()).minusDays(1)
        val sleepStart = wakeDate.minusDays(1).atTime(23, 30).atZone(ZoneId.systemDefault()).toInstant()
        val sleepEnd = wakeDate.atTime(7, 30).atZone(ZoneId.systemDefault()).toInstant()
        val fetchCount = AtomicInteger()
        val source = object : SleepDataSource {
            override val id = "concurrent-health-source"
            override val name = "Concurrent source test"
            override fun getStatus(context: Context) = HealthDataSourceStatus.AVAILABLE_CONNECTED
            override fun fetchSleepSessions(context: Context, queryStart: Instant, queryEnd: Instant): List<RawSleepRecord> {
                fetchCount.incrementAndGet()
                Thread.sleep(100)
                return listOf(
                    RawSleepRecord(
                        id = "concurrent-record",
                        sourcePackage = "test.sleep.source",
                        startTime = sleepStart,
                        endTime = sleepEnd,
                        lastModifiedTime = now,
                        stages = listOf(RawSleepStage(sleepStart, sleepEnd, 4)),
                        isAsleepConfirmed = true,
                        actualSleepMinutes = 480L
                    )
                )
            }
        }
        val executor = Executors.newFixedThreadPool(2)
        try {
            db.saveSleepTargets("00:30", "07:30")
            val first = executor.submit<ImportResult> {
                SleepImportManager.syncFromAvailableSources(context, db, source)
            }
            val second = executor.submit<ImportResult> {
                SleepImportManager.syncFromAvailableSources(context, db, source)
            }
            val results = listOf(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS))

            assertEquals(2, fetchCount.get())
            assertEquals(1, results.sumOf { it.importedCount })
            assertNotNull(db.sleepRecord(wakeDate.toString()))
            assertEquals(1, db.sleepRecord(wakeDate.toString())!!.version)
        } finally {
            executor.shutdownNow()
            db.close()
            context.deleteDatabase(name)
        }
    }
}
