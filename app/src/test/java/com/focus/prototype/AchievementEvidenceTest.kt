package com.focus.prototype

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class AchievementEvidenceTest {
    private val zone = ZoneId.systemDefault()
    private lateinit var db: Database
    private lateinit var dbName: String

    @Before
    fun setUp() {
        dbName = "achievement-test-${UUID.randomUUID()}.db"
        val context = RuntimeEnvironment.getApplication()
        context.getDatabasePath(dbName).parentFile?.mkdirs()
        db = Database(
            context,
            dbName,
            wallNow = { LocalDate.of(2026, 11, 16).atTime(10, 0).atZone(zone).toInstant().toEpochMilli() },
            now = { LocalDateTime.of(2026, 11, 16, 10, 0) },
        )
        db.writableDatabase
    }

    @After
    fun tearDown() {
        db.close()
        RuntimeEnvironment.getApplication().deleteDatabase(dbName)
    }

    @Test
    fun testTaskAccumulationEarnedAndProgress() {
        val d1 = "2026-11-10"
        val d2 = "2026-11-11"
        db.addTask(d1, "任务一", true, 0, "行动型", "", 0, 0, "once")
        db.addTask(d2, "任务二", true, 0, "行动型", "", 0, 0, "once")

        val t1 = db.tasks(d1).single()
        db.complete(t1)

        val items = achievementList(db)
        val taskFirst = items.first { it.id == "task.first" }
        assertTrue("task.first 应已达成", taskFirst.earned)
        assertEquals("task.first 获得日期应为任务日期", d1, taskFirst.earnedDate)

        val evidenceFirst = loadAchievementEvidence(db, taskFirst)
        assertTrue("证据应为 TaskList", evidenceFirst is AchievementEvidence.TaskList)
        val taskList = evidenceFirst as AchievementEvidence.TaskList
        assertEquals("贡献任务应有 1 个", 1, taskList.tasks.size)
        assertEquals(0, taskList.remaining)
        assertEquals("任务一", taskList.tasks[0].title)
        assertTrue("实际完成时间应大于 0", taskList.tasks[0].completedAt > 0L)

        // 下一阶段
        val nextFirst = nextStageOf(taskFirst.id)
        assertNotNull(nextFirst)
        assertEquals("十项达成", nextFirst!!.first)

        // 查看 task.ten 的进行中进度
        val taskTen = items.first { it.id == "task.ten" }
        assertFalse("task.ten 不应已达成", taskTen.earned)
        assertEquals(1, taskTen.progress)
        val evidenceTen = loadAchievementEvidence(db, taskTen) as AchievementEvidence.TaskList
        assertEquals(1, evidenceTen.tasks.size)
        assertEquals(9, evidenceTen.remaining)
    }

    @Test
    fun testTaskUndoResetsEvidenceAndProgress() {
        val d = "2026-11-12"
        db.addTask(d, "待撤销任务", true, 0, "行动型", "", 0, 0, "once")
        val task = db.tasks(d).single()
        db.complete(task)

        val itemsAfterComplete = achievementList(db)
        val firstEarned = itemsAfterComplete.first { it.id == "task.first" }
        assertTrue(firstEarned.earned)

        // 撤销任务
        val completedTask = db.tasks(d).single()
        db.revoke(completedTask)

        val itemsAfterUndo = achievementList(db)
        val firstAfterUndo = itemsAfterUndo.first { it.id == "task.first" }
        assertFalse("撤销后 task.first 应重置为未达成", firstAfterUndo.earned)
        assertEquals(0, firstAfterUndo.progress)

        val evidenceAfterUndo = loadAchievementEvidence(db, firstAfterUndo) as AchievementEvidence.TaskList
        assertTrue("撤销后贡献任务列表应为空", evidenceAfterUndo.tasks.isEmpty())
        assertEquals(1, evidenceAfterUndo.remaining)
    }

    @Test
    fun testLegacyTaskWithoutCompletedAtDoesNotForgeTimestamp() {
        val d = "2026-10-01"
        // 模拟升级旧数据：插入已完成但 completed_at 为 0 的任务
        db.writableDatabase.execSQL(
            "INSERT INTO tasks(id, day, title, required, minutes, mode, checklist, reward_minutes, reward_games, elapsed, done, completed_at) VALUES('legacy-1', ?, '历史迁移任务', 1, 15, '行动型', '', 0, 0, 900000, 1, 0)",
            arrayOf(d),
        )

        val items = achievementList(db)
        val taskFirst = items.first { it.id == "task.first" }
        assertTrue(taskFirst.earned)

        val evidence = loadAchievementEvidence(db, taskFirst) as AchievementEvidence.TaskList
        assertEquals(1, evidence.tasks.size)
        val legacy = evidence.tasks.first()
        assertEquals("历史迁移任务", legacy.title)
        assertEquals("completed_at 为 0 时不应伪造时间戳", 0L, legacy.completedAt)
    }

    @Test
    fun testLongTaskEvidenceIsCappedAtAchievementTargetInRuleOrder() {
        val date = "2026-11-10"
        repeat(55) { index ->
            db.addTask(date, "任务${index + 1}", false, 0, "行动型", "", 0, 0, "once")
            db.complete(db.tasks(date).last())
        }

        val fiftyTasks = achievementList(db).first { it.id == "task.fifty" }
        assertTrue(fiftyTasks.earned)
        val evidence = loadAchievementEvidence(db, fiftyTasks) as AchievementEvidence.TaskList
        assertEquals(50, evidence.tasks.size)
        assertEquals(0, evidence.remaining)
        assertEquals("任务1", evidence.tasks.first().title)
        assertEquals("任务50", evidence.tasks.last().title)
    }

    @Test
    fun testStreakEvidenceConsecutiveDates() {
        // 创建连续 3 天完成的历史契约
        listOf("2026-11-01", "2026-11-02", "2026-11-03").forEach { date ->
            db.contract(date)
            db.addTask(date, "任务 $date", true, 0, "行动型", "", 0, 0, "once")
            val t = db.tasks(date).single()
            db.complete(t)
            db.writableDatabase.execSQL("UPDATE contracts SET confirmed=1 WHERE day=?", arrayOf(date))
        }

        val items = achievementList(db)
        val streakThree = items.first { it.id == "streak.three" }
        assertTrue("连续 3 天应达成 streak.three", streakThree.earned)

        val evidence = loadAchievementEvidence(db, streakThree)
        assertTrue("证据应为 DateList", evidence is AchievementEvidence.DateList)
        val dateList = evidence as AchievementEvidence.DateList
        assertEquals(3, dateList.dates.size)
        assertEquals(listOf("2026-11-01", "2026-11-02", "2026-11-03"), dateList.dates)
        assertEquals(0, dateList.remaining)

        val nextStreak = nextStageOf(streakThree.id)
        assertNotNull(nextStreak)
        assertEquals("连续一周", nextStreak!!.first)
    }

    @Test
    fun testHiddenAchievementNeverLeaksConditionOrEvidence() {
        val hiddenItem = Achievement(
            id = "task.first",
            category = "隐藏成就",
            title = "未知奇遇",
            condition = "完成隐藏条件",
            progress = 0,
            target = 1,
            earnedDate = null,
        )

        val evidence = loadAchievementEvidence(db, hiddenItem)
        assertTrue("未解锁的隐藏成效应返回 Hidden 证据", evidence is AchievementEvidence.Hidden)
        assertEquals("隐藏成就", achievementDetailTitle(hiddenItem, evidence))
        assertNull("隐藏状态不应显示映射到 task.first 的下一阶段", achievementDetailNextStage(hiddenItem, evidence))
    }

    @Test
    fun testNextStageBoundary() {
        assertEquals("十项达成", nextStageOf("task.first")?.first)
        assertEquals("五十项达成", nextStageOf("task.ten")?.first)
        assertNull("最高级累计任务没有下一阶段", nextStageOf("task.fifty"))
        assertEquals("连续一周", nextStageOf("streak.three")?.first)
        assertEquals("连续整月", nextStageOf("streak.seven")?.first)
        assertNull("最高级连续记录没有下一阶段", nextStageOf("streak.thirty"))
        assertEquals("专注十小时", nextStageOf("focus.hour")?.first)
        assertNull("最高级专注没有下一阶段", nextStageOf("focus.ten_hours"))
    }
}
