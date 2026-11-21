package com.focus.prototype

import org.junit.After
import org.junit.Assert.*
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
class Fwd024ContinuityAndGrowthTest {
    private val zone = ZoneId.systemDefault()
    private val testDate = LocalDate.of(2026, 11, 20)
    private lateinit var db: Database
    private lateinit var dbName: String

    @Before
    fun setUp() {
        dbName = "fwd024-test-${UUID.randomUUID()}.db"
        val context = RuntimeEnvironment.getApplication()
        context.getDatabasePath(dbName).parentFile?.mkdirs()
        db = Database(
            context,
            dbName,
            wallNow = { testDate.atTime(10, 0).atZone(zone).toInstant().toEpochMilli() },
            now = { testDate.atTime(10, 0) },
        )
        db.writableDatabase
    }

    @After
    fun tearDown() {
        db.close()
        RuntimeEnvironment.getApplication().deleteDatabase(dbName)
    }

    @Test
    fun testDurationFormatting() {
        // 5 hours 47 min = 347 minutes = 20,820,000 ms
        val actualMs = (5 * 60 + 47) * 60_000L
        val formattedActual = formatDurationMs(actualMs)
        assertEquals("5\u00A0小时\u00A047\u00A0分", formattedActual)

        // 2 hours 30 min = 150 min
        val targetMin = 150
        val formattedTarget = formatDurationMinutes(targetMin)
        assertEquals("2\u00A0小时\u00A030\u00A0分", formattedTarget)

        val combined = "实际\u00A0$formattedActual / 目标\u00A0$formattedTarget"
        assertEquals("实际\u00A05\u00A0小时\u00A047\u00A0分 / 目标\u00A02\u00A0小时\u00A030\u00A0分", combined)
    }

    @Test
    fun testDailyCardRequiresAllTasksAndRevealSeenLifecycle() {
        val todayStr = testDate.toString()
        db.addTask(todayStr, "任务一", required = true, minutes = 0, mode = "行动型", checklist = "", rewardMinutes = 0, rewardGames = 0, recurrence = "once")
        db.addTask(todayStr, "任务二", required = true, minutes = 0, mode = "行动型", checklist = "", rewardMinutes = 0, rewardGames = 0, recurrence = "once")

        val tasksBefore = db.tasks(todayStr)
        assertEquals(2, tasksBefore.size)
        assertFalse("开始时未完成任何任务，不可生成纪念卡", dailyCardQualifies(tasksBefore))
        assertNull(DailyCards.reconcile(db, todayStr, tasksBefore))

        // 完成第 1 项任务（当天仍有未完成的必做任务）
        db.complete(tasksBefore[0])
        val tasksAfterFirst = db.tasks(todayStr)
        assertFalse("仅完成第 1 项仍有未完成任务，不可生成每日卡片", dailyCardQualifies(tasksAfterFirst))
        assertNull(DailyCards.reconcile(db, todayStr, tasksAfterFirst))

        // 完成第 2 项任务（全部完成）
        db.complete(tasksAfterFirst[1])
        val tasksAfterAll = db.tasks(todayStr)
        assertTrue("全部必做任务完成后具备每日纪念资格", dailyCardQualifies(tasksAfterAll))

        val record = DailyCards.reconcile(db, todayStr, tasksAfterAll)
        assertNotNull("全部完成时生成纪念卡记录", record)
        assertFalse("首次生成时 revealSeen 必须为 false，以便触发自动入场展示", record!!.revealSeen)

        // 标记为已展示
        DailyCards.markRevealSeen(db, todayStr)
        val loaded = DailyCards.load(db, todayStr)
        assertNotNull(loaded)
        assertTrue("展示后 revealSeen 标记为 true", loaded!!.revealSeen)

        // 重新对账或重进页面，revealSeen 保持 true，不重复播放入场
        val reReconciled = DailyCards.reconcile(db, todayStr, db.tasks(todayStr))
        assertNotNull(reReconciled)
        assertTrue("再次对账保持 revealSeen=true，不重播", reReconciled!!.revealSeen)
    }

    @Test
    fun testWeeklyReviewCompletions0_5_8Items() {
        val weekState = WeeklyReviewState(testDate)
        val start = weekState.weekStart
        val end = weekState.weekEndExclusive

        // 0 项
        val list0 = completedTasksBetween(db, start, end)
        assertEquals(0, list0.size)

        // 增加 5 项完成任务
        for (i in 1..5) {
            db.addTask(start.toString(), "周任务$i", required = true, minutes = 0, mode = "行动型", checklist = "", rewardMinutes = 0, rewardGames = 0, recurrence = "once")
        }
        val tasks5 = db.tasks(start.toString())
        tasks5.forEach { db.complete(it) }

        val list5 = completedTasksBetween(db, start, end)
        assertEquals(5, list5.size)

        // 再增加 3 项（总共 8 项）
        for (i in 6..8) {
            db.addTask(start.toString(), "周任务$i", required = true, minutes = 0, mode = "行动型", checklist = "", rewardMinutes = 0, rewardGames = 0, recurrence = "once")
        }
        val remainingTasks = db.tasks(start.toString()).filter { !it.done }
        remainingTasks.forEach { db.complete(it) }

        val list8 = completedTasksBetween(db, start, end)
        assertEquals(8, list8.size)

        // 折叠状态默认显示最多 5 项
        val collapsed = list8.take(5)
        assertEquals(5, collapsed.size)
        // 展开状态显示全部 8 项
        assertEquals(8, list8.size)

        // 上周数据独立
        val prevStart = start.minusWeeks(1)
        val prevEnd = start
        val listPrev = completedTasksBetween(db, prevStart, prevEnd)
        assertEquals(0, listPrev.size)
    }

    @Test
    fun testAchievementsStructureAndIntegrity() {
        val items = achievementList(db)
        assertEquals("成就总数保持 8 枚", 8, items.size)

        val categories = items.map { it.category }.distinct()
        assertEquals(listOf("任务积累", "连续记录", "专注积累"), categories)

        val taskIds = items.filter { it.category == "任务积累" }.map { it.id }
        assertEquals(listOf("task.first", "task.ten", "task.fifty"), taskIds)

        val streakIds = items.filter { it.category == "连续记录" }.map { it.id }
        assertEquals(listOf("streak.three", "streak.seven", "streak.thirty"), streakIds)

        val focusIds = items.filter { it.category == "专注积累" }.map { it.id }
        assertEquals(listOf("focus.hour", "focus.ten_hours"), focusIds)
    }
}
