package com.focus.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class Fwd022DurationAndNightTest {

    @Test
    fun testDurationFormattingUnified() {
        // FWD-022 specified format:
        // 221 分钟 / 120 分钟显示为「实际 3 小时 41 分」「目标 2 小时」
        // 30 分钟显示「30 分钟」，不凑零小时
        assertEquals("3\u00A0小时\u00A041\u00A0分", formatDurationMinutes(221))
        assertEquals("2\u00A0小时", formatDurationMinutes(120))
        assertEquals("30\u00A0分钟", formatDurationMinutes(30))
        assertEquals("1\u00A0小时\u00A05\u00A0分", formatDurationMinutes(65))
        assertEquals("1\u00A0小时", formatDurationMinutes(60))
        assertEquals("自由专注", formatDurationMinutes(0))

        assertEquals("3\u00A0小时\u00A041\u00A0分", formatDurationMs(221 * 60_000L))
        assertEquals("2\u00A0小时", formatDurationMs(120 * 60_000L))
        assertEquals("30\u00A0分钟", formatDurationMs(30 * 60_000L))
        assertEquals("1\u00A0小时\u00A05\u00A0分", formatDurationMs(65 * 60_000L))
        assertEquals("45\u00A0秒", formatDurationMs(45_000L))
        assertEquals("0\u00A0分钟", formatDurationMs(0L))
    }

    @Test
    fun testNightWrapUpMidnightOrderValidation() {
        // Baseline legal sequence across midnight: 22:00, 23:30, 00:00, 00:30, 07:30
        val baseline = NightWrapUpConfig(
            windDown = "22:00",
            workCutoff = "23:30",
            entertainmentCutoff = "00:00",
            bedtime = "00:30",
            morningRelease = "07:30"
        )
        baseline.validate()

        // Modify one evening value (21:30) and one midnight value (01:00)
        val modified = baseline.copy(windDown = "21:30", bedtime = "01:00")
        modified.validate()
        assertEquals("21:30", modified.windDown)
        assertEquals("23:30", modified.workCutoff)
        assertEquals("00:00", modified.entertainmentCutoff)
        assertEquals("01:00", modified.bedtime)
        assertEquals("07:30", modified.morningRelease)

        // Invalid: workCutoff before windDown (evening error)
        val invalid1 = baseline.copy(workCutoff = "21:00")
        val ex1 = assertThrows(IllegalArgumentException::class.java) { invalid1.validate() }
        assertTrue(ex1.message!!.contains("顺序"))

        // Invalid: bedtime before entertainmentCutoff across midnight (e.g. 23:50 vs 00:00)
        val invalid2 = baseline.copy(bedtime = "23:50")
        val ex2 = assertThrows(IllegalArgumentException::class.java) { invalid2.validate() }
        assertTrue(ex2.message!!.contains("顺序"))

        // Invalid: adjacent equal times (no progression)
        val invalid3 = baseline.copy(workCutoff = "22:00")
        val ex3 = assertThrows(IllegalArgumentException::class.java) { invalid3.validate() }
        assertTrue(ex3.message!!.contains("顺序"))
    }

    @Test
    fun testNightWrapUpSaveAndReadBackPersistence() {
        val context = RuntimeEnvironment.getApplication()
        val dbName = "fwd022-test-${System.nanoTime()}.db"
        val db = Database(context, dbName)

        // 1. Initial read: returns defaults
        val initial = db.nightWrapUpConfig()
        assertEquals("22:00", initial.windDown)
        assertEquals("23:30", initial.workCutoff)
        assertEquals("00:00", initial.entertainmentCutoff)
        assertEquals("00:30", initial.bedtime)
        assertEquals("07:30", initial.morningRelease)
        assertEquals(10, initial.penaltyCapMinutes)
        assertEquals(1, initial.penaltyCapGames)

        // 2. Modify one evening value (21:15) and one midnight value (01:15), and penalty cap
        val updated = initial.copy(
            windDown = "21:15",
            bedtime = "01:15",
            penaltyCapMinutes = 15,
            penaltyCapGames = 2
        )
        db.saveNightWrapUpConfig(updated)

        // 3. Read back from database
        val readBack = db.nightWrapUpConfig()
        assertEquals("21:15", readBack.windDown)
        assertEquals("23:30", readBack.workCutoff)
        assertEquals("00:00", readBack.entertainmentCutoff)
        assertEquals("01:15", readBack.bedtime)
        assertEquals("07:30", readBack.morningRelease)
        assertEquals(15, readBack.penaltyCapMinutes)
        assertEquals(2, readBack.penaltyCapGames)

        // 4. Simulate cold restart: new Database instance on same SQLite file
        val coldDb = Database(context, dbName)
        val coldReadBack = coldDb.nightWrapUpConfig()
        assertEquals("21:15", coldReadBack.windDown)
        assertEquals("23:30", coldReadBack.workCutoff)
        assertEquals("00:00", coldReadBack.entertainmentCutoff)
        assertEquals("01:15", coldReadBack.bedtime)
        assertEquals("07:30", coldReadBack.morningRelease)
        assertEquals(15, coldReadBack.penaltyCapMinutes)
        assertEquals(2, coldReadBack.penaltyCapGames)

        // 5. Unchanged items kept
        assertEquals(initial.workCutoff, coldReadBack.workCutoff)
        assertEquals(initial.entertainmentCutoff, coldReadBack.entertainmentCutoff)
        assertEquals(initial.morningRelease, coldReadBack.morningRelease)

        // 6. Reschedule does not crash and handles updated config
        NightWrapUpScheduler.reschedule(context, coldDb)
    }

    @Test
    fun testTaskDurationEchoAndDefault() {
        val context = RuntimeEnvironment.getApplication()
        val dbName = "fwd022-duration-test-${System.nanoTime()}.db"
        val db = Database(context, dbName)

        val today = db.day()
        // Add existing task with 120 minutes
        db.addTask(today, "原有任务 120 分钟", true, 120, "行动型", "完成", 10, 0, "once")
        // Add existing task with 0 minutes (free focus)
        db.addTask(today, "原有任务 自由专注", false, 0, "行动型", "完成", 0, 0, "once")

        val tasks = db.tasks(today)
        val task120 = tasks.first { it.title == "原有任务 120 分钟" }
        val task0 = tasks.first { it.title == "原有任务 自由专注" }

        assertEquals(120, task120.minutes)
        assertEquals(0, task0.minutes)

        // Revise task without changing minutes: preserves 120 minutes (not overridden by 30)
        db.revise(task120, "修改名称但保留时长", task120.required, task120.minutes, task120.mode, task120.checklist, task120.rewardMinutes, task120.rewardGames, "once", "", false, false)
        val revised120 = db.task(task120.id)!!
        assertEquals(120, revised120.minutes)

        // Revise 0-minute task: preserves 0 minutes
        db.revise(task0, "保留自由专注", task0.required, task0.minutes, task0.mode, task0.checklist, task0.rewardMinutes, task0.rewardGames, "once", "", false, false)
        val revised0 = db.task(task0.id)!!
        assertEquals(0, revised0.minutes)
    }
}
