package com.focus.prototype

import org.junit.After
import org.junit.Assert.assertEquals
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
class WeeklyReviewProductionQueryTest {
    private val zone = ZoneId.systemDefault()
    private val reviewDate = LocalDate.of(2026, 11, 19)
    private lateinit var db: Database
    private lateinit var dbName: String

    @Before
    fun setUp() {
        dbName = "weekly-review-${UUID.randomUUID()}.db"
        val context = RuntimeEnvironment.getApplication()
        context.getDatabasePath(dbName).parentFile?.mkdirs()
        db = Database(
            context,
            dbName,
            wallNow = { LocalDate.of(2026, 11, 16).atTime(0, 5).atZone(zone).toInstant().toEpochMilli() },
            now = { LocalDateTime.of(2026, 11, 19, 12, 0) },
        )
        db.writableDatabase
    }

    @After
    fun tearDown() {
        db.close()
        RuntimeEnvironment.getApplication().deleteDatabase(dbName)
    }

    @Test
    fun productionQueryUsesCompletionWeekAndExcludesUndoUnknownAndMovedRows() {
        val state = WeeklyReviewState(reviewDate)
        val plannedSunday = LocalDate.of(2026, 11, 15)
        db.addTask(plannedSunday.toString(), "跨周验证任务", true, 0, "行动型", "", 0, 0, "once")
        val task = db.tasks(plannedSunday.toString()).single()
        db.complete(task)

        val previousWeek = state.weekStart.minusWeeks(1)
        assertTrue(completedTasksBetween(db, previousWeek, state.weekStart).isEmpty())
        assertEquals(
            listOf("跨周验证任务"),
            completedTasksBetween(db, state.weekStart, state.weekEndExclusive).map { it.title },
        )

        db.revoke(db.task(task.id)!!)
        assertTrue(completedTasksBetween(db, state.weekStart, state.weekEndExclusive).isEmpty())

        insertCompletedRow("unknown-time", "2026-11-17", "未知完成时间", completedAt = 0L)
        assertTrue(completedTasksBetween(db, state.weekStart, state.weekEndExclusive).isEmpty())

        val movedAt = LocalDate.of(2026, 11, 18).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        insertCompletedRow("moved-source", "2026-11-18", "延期源任务", completedAt = movedAt)
        db.writableDatabase.execSQL(
            "INSERT INTO task_moves(source_id,target_id,from_day,to_day,created) VALUES(?,?,?,?,?)",
            arrayOf<Any?>("moved-source", "moved-target", "2026-11-18", "2026-11-19", movedAt),
        )
        assertTrue(completedTasksBetween(db, state.weekStart, state.weekEndExclusive).isEmpty())

        assertTrue(
            completedTasksBetween(db, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 26)).isEmpty(),
        )
    }

    @Test
    fun productionQueryUsesLocalMondayInclusiveAndNextMondayExclusiveBounds() {
        val state = WeeklyReviewState(reviewDate)
        val start = state.weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = state.weekEndExclusive.atStartOfDay(zone).toInstant().toEpochMilli()

        insertCompletedRow("before-monday", "2026-11-15", "周日前一毫秒", start - 1L)
        insertCompletedRow("monday-start", "2026-11-16", "周一开始", start)
        insertCompletedRow("sunday-end", "2026-11-22", "周日最后一毫秒", end - 1L)
        insertCompletedRow("next-monday", "2026-11-23", "下周一开始", end)

        assertEquals(
            setOf("周一开始", "周日最后一毫秒"),
            completedTasksBetween(db, state.weekStart, state.weekEndExclusive).map { it.title }.toSet(),
        )
    }

    private fun insertCompletedRow(id: String, day: String, title: String, completedAt: Long) {
        db.writableDatabase.execSQL(
            """INSERT INTO tasks(id,day,title,required,minutes,mode,checklist,reward_minutes,reward_games,elapsed,done,completed_at)
               VALUES(?,?,?,?,?,?,?,?,?,?,?,?)""",
            arrayOf<Any?>(id, day, title, 1, 0, "行动型", "", 0, 0, 0L, 1, completedAt),
        )
    }
}
