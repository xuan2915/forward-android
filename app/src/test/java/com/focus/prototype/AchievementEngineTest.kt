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
class AchievementEngineTest {
    private val zone = ZoneId.systemDefault()
    private var currentDateTime = LocalDateTime.of(2026, 11, 24, 10, 0)
    private lateinit var db: Database
    private lateinit var dbName: String

    @Before
    fun setUp() {
        dbName = "engine-test-${UUID.randomUUID()}.db"
        val context = RuntimeEnvironment.getApplication()
        context.getDatabasePath(dbName).parentFile?.mkdirs()
        db = Database(
            context,
            dbName,
            wallNow = { currentDateTime.atZone(zone).toInstant().toEpochMilli() },
            now = { currentDateTime },
        )
        db.writableDatabase
    }

    @After
    fun tearDown() {
        db.close()
        RuntimeEnvironment.getApplication().deleteDatabase(dbName)
    }

    @Test
    fun testMultiStageHistoricalBackfill() {
        val engine = AchievementEngine(db)

        // Simulate 35 completed historical tasks before evaluation
        val date = "2026-11-20"
        for (i in 1..35) {
            db.writableDatabase.execSQL(
                "INSERT INTO tasks(id, day, title, required, minutes, mode, checklist, reward_minutes, reward_games, elapsed, done, completed_at) VALUES(?, ?, ?, 1, 15, '行动型', '', 0, 0, 900000, 1, 1700000000000)",
                arrayOf("task-backfill-$i", date, "历史任务 $i"),
            )
        }

        val result = engine.evaluateAll()
        val unlockedIds = result.newlyUnlocked.map { it.id }

        assertTrue("Stage 1 应已解锁", "achievement.evolution.action.stage_1" in unlockedIds)
        assertTrue("Stage 2 应已解锁", "achievement.evolution.action.stage_2" in unlockedIds)
        assertFalse("Stage 3 (150项) 不应解锁", "achievement.evolution.action.stage_3" in unlockedIds)

        // Check unseen notices: should have both stages
        val notices = engine.consumePendingUnseenNotices()
        val noticeAchievementIds = notices.map { it.achievementId }
        assertTrue("achievement.evolution.action.stage_1" in noticeAchievementIds)
        assertTrue("achievement.evolution.action.stage_2" in noticeAchievementIds)

        // Consuming again should be empty (no duplicate notification)
        val secondConsume = engine.consumePendingUnseenNotices()
        assertTrue("重复消费待展示通知应为空", secondConsume.isEmpty())

        // Re-evaluating should not re-trigger unlocks or notices
        val reEvalResult = engine.evaluateAll()
        assertTrue("再次评估不应重复产出新解锁", reEvalResult.newlyUnlocked.isEmpty())
        assertTrue("再次消费通知仍应为空", engine.consumePendingUnseenNotices().isEmpty())
    }

    @Test
    fun testRevokeInvalidatesAchievementAndRedoRestoresIt() {
        val engine = AchievementEngine(db)
        val date = "2026-11-21"
        db.addTask(date, "单一任务", true, 0, "行动型", "", 0, 0, "once")
        val task = db.tasks(date).single()
        db.complete(task)
        assertEquals(100, db.xp())

        engine.evaluateAll()
        var view = engine.getPublicView(AchievementCatalog.findById("achievement.evolution.action.stage_1")!!)
        assertTrue("完成单一任务后 stage 1 应已解锁", view.isUnlocked)

        // 撤销任务
        val completedTask = db.tasks(date).single()
        db.revoke(completedTask)
        assertEquals(0, db.xp())

        view = engine.getPublicView(AchievementCatalog.findById("achievement.evolution.action.stage_1")!!)
        assertFalse("任务撤销后 stage 1 应被标记为未解锁/已失效", view.isUnlocked)

        // 重新完成任务
        val revokedTask = db.tasks(date).single()
        db.complete(revokedTask)
        assertEquals(100, db.xp())
        assertEquals(1, db.readableDatabase.rawQuery("SELECT COUNT(*) FROM xp_ledger WHERE source_type='task' AND source_id=?", arrayOf(revokedTask.id)).use { it.moveToFirst(); it.getInt(0) })

        view = engine.getPublicView(AchievementCatalog.findById("achievement.evolution.action.stage_1")!!)
        assertTrue("重新完成任务后 stage 1 应重新解锁", view.isUnlocked)
    }

    @Test
    fun testHiddenAchievementNeverLeaksLockedDetails() {
        val engine = AchievementEngine(db)
        val secretItem = AchievementCatalog.findById("achievement.secret.to_be_continued")!!

        val publicView = engine.getPublicView(secretItem)
        assertFalse("未满足条件的隐藏成效应未解锁", publicView.isUnlocked)
        assertEquals("隐藏成就", publicView.title)
        assertEquals("达成特定条件后揭晓", publicView.description)
        assertEquals("未知", publicView.condition)
        assertEquals(0L, publicView.progress)
        assertEquals(1L, publicView.target)
        assertFalse(publicView.id == secretItem.id)
        assertFalse(publicView.slotId == secretItem.slotId)
        assertFalse(publicView.isPendingEvidence)
        val lockedSlots = engine.getCollectionSlotsProgress().filter { it.slot.kind == AchievementKind.SECRET && !it.isUnlocked }
        assertTrue(lockedSlots.isNotEmpty())
        assertTrue(lockedSlots.all { it.slot.title == "隐藏成就" && it.totalMilestones == 1 })
    }

    @Test
    fun testAllClearRequiresFrozenPlanAndPostConfirmationCompletion() {
        val engine = AchievementEngine(db)
        val date = "2026-11-24"
        db.addTask(date, "冻结前完成", true, 0, "行动型", "", 0, 0, "once")
        db.complete(db.tasks(date).single())
        val clearStage = AchievementCatalog.findById("achievement.evolution.all_clear.stage_1")!!
        assertFalse("未确认冻结计划不应解锁全清", engine.getPublicView(clearStage).isUnlocked)

        db.writableDatabase.execSQL("UPDATE tasks SET completed_at=1 WHERE title='冻结前完成'")
        db.addTask(date, "冻结后完成", true, 0, "行动型", "", 0, 0, "once")
        db.confirm(date, rest = false, minutes = 30, games = 0)
        db.complete(db.tasks(date).first { it.title == "冻结后完成" })
        assertFalse("计划确认之前完成的必做项不能计入冻结后的全清", engine.getPublicView(clearStage).isUnlocked)
    }

    @Test
    fun testConfirmedFrozenPlanCanUnlockAllClear() {
        val engine = AchievementEngine(db)
        val date = "2026-11-24"
        db.addTask(date, "冻结后完成", true, 0, "行动型", "", 0, 0, "once")
        db.confirm(date, rest = false, minutes = 30, games = 0)
        db.complete(db.tasks(date).single())
        assertTrue(engine.getPublicView(AchievementCatalog.findById("achievement.evolution.all_clear.stage_1")!!).isUnlocked)
    }

    @Test
    fun testHighQualityWeeksMustBeAdjacent() {
        val engine = AchievementEngine(db)
        currentDateTime = LocalDateTime.of(2026, 12, 20, 10, 0)
        val sql = db.writableDatabase
        fun seedWeek(monday: LocalDate, prefix: String) {
            for (offset in 0..4) {
                val date = monday.plusDays(offset.toLong()).toString()
                sql.execSQL("INSERT OR REPLACE INTO contracts(day,confirmed,rest) VALUES(?,1,0)", arrayOf(date))
                sql.execSQL("INSERT OR REPLACE INTO settings(key,value) VALUES(?, '1000')", arrayOf("plan_confirmed_at_$date"))
                sql.execSQL(
                    "INSERT OR REPLACE INTO tasks(id,day,title,required,minutes,mode,checklist,reward_minutes,reward_games,elapsed,done,completed_at,type_known) VALUES(?,?,?,1,0,'行动型','',0,0,0,1,2000,1)",
                    arrayOf("$prefix-$offset", date, "全清"),
                )
            }
        }
        seedWeek(LocalDate.of(2026, 11, 2), "week-1")
        seedWeek(LocalDate.of(2026, 11, 9), "week-2")
        seedWeek(LocalDate.of(2026, 11, 23), "week-4")
        seedWeek(LocalDate.of(2026, 11, 30), "week-5")
        val item = AchievementCatalog.findById("achievement.indep.execution.follow_through")!!
        engine.evaluateAll()
        assertFalse("空缺周不能被跳过并拼成连续4周", engine.getPublicView(item).isUnlocked)

        seedWeek(LocalDate.of(2026, 12, 7), "week-6")
        seedWeek(LocalDate.of(2026, 12, 14), "week-7")
        engine.evaluateAll()
        assertTrue("补足缺口后的连续4周应解锁", engine.getPublicView(item).isUnlocked)
    }

    @Test
    fun testTwelveEffectiveWeeksUnlockBalanceMilestone() {
        val engine = AchievementEngine(db)
        currentDateTime = LocalDateTime.of(2027, 1, 25, 10, 0)
        val sql = db.writableDatabase
        val firstMonday = LocalDate.of(2026, 11, 2)
        for (week in 0 until 12) {
            for (day in 0..4) {
                val date = firstMonday.plusWeeks(week.toLong()).plusDays(day.toLong())
                sql.execSQL(
                    "INSERT INTO tasks(id,day,title,required,minutes,mode,checklist,reward_minutes,reward_games,elapsed,done,completed_at,type_known) VALUES(?,?,?,1,0,'行动型','',0,0,0,1,1,1)",
                    arrayOf("spark-$week-$day", date.toString(), "执行"),
                )
            }
        }
        engine.evaluateAll()
        val item = AchievementCatalog.findById("achievement.indep.balance.more_than_spark")!!
        assertTrue(engine.getPublicView(item).isUnlocked)
    }

    @Test
    fun testDoubleHappinessNeedsTwoDifferentHighMilestoneSeriesOnSameDate() {
        val engine = AchievementEngine(db)
        val date = "2026-11-20"
        val action = AchievementCatalog.findById("achievement.evolution.action.stage_4")!!
        val focus = AchievementCatalog.findById("achievement.evolution.focus.stage_4")!!
        fun seed(item: AchievementDefinition) {
            db.writableDatabase.execSQL(
                "INSERT INTO achievement_unlocks(id, achievement_id, slot_id, unlocked_at, unlocked_date, evidence_json, status, version, seen) VALUES(?,?,?,?,?,?, 'UNLOCKED',?,1)",
                arrayOf("seed-${item.id}", item.id, item.slotId, 1L, date, "{\"target\":${item.threshold}}", item.version),
            )
        }
        seed(action)
        engine.evaluateAll()
        val secret = AchievementCatalog.findById("achievement.secret.double_happiness")!!
        assertFalse(engine.getPublicView(secret).isUnlocked)
        seed(focus)
        engine.evaluateAll()
        assertTrue(engine.getPublicView(secret).isUnlocked)
    }

    @Test
    fun testLegacyIdsResolveToRestorableUnlockRecords() {
        val engine = AchievementEngine(db)
        db.writableDatabase.execSQL(
            "INSERT INTO achievement_unlocks(id,achievement_id,slot_id,unlocked_at,unlocked_date,evidence_json,status,version,seen) VALUES('legacy-row','legacy.task.ten','legacy.slot.task.ten',123,'2026-11-20','{}','UNLOCKED',1,1)",
        )
        assertNotNull(AchievementCatalog.findById("task.ten"))
        val oldIdQuery = engine.findUnlock("task.ten")
        val stableIdQuery = engine.findUnlock("legacy.task.ten")
        assertNotNull(oldIdQuery)
        assertEquals("legacy.task.ten", oldIdQuery!!.achievementId)
        assertEquals(123L, oldIdQuery.unlockedAt)
        assertEquals(oldIdQuery, stableIdQuery)
    }

    @Test
    fun testToBeContinuedRequiresVerifiedInterruptionAndThirtyRebuiltDays() {
        val engine = AchievementEngine(db)
        currentDateTime = LocalDateTime.of(2026, 12, 20, 10, 0)
        val sql = db.writableDatabase
        fun putDay(date: LocalDate, done: Boolean, rest: Boolean = false) {
            sql.execSQL("INSERT OR REPLACE INTO contracts(day, confirmed, rest) VALUES(?,1,?)", arrayOf(date.toString(), if (rest) 1 else 0))
            if (done) {
                sql.execSQL(
                    "INSERT OR REPLACE INTO tasks(id, day, title, required, minutes, mode, checklist, reward_minutes, reward_games, elapsed, done, completed_at, type_known) VALUES(?,?,?,1,0,'行动型','',0,0,0,1,1,1)",
                    arrayOf("task-${date}", date.toString(), "执行"),
                )
            }
        }
        val start = LocalDate.of(2026, 11, 1)
        putDay(start, done = true)
        for (offset in 1..14) putDay(start.plusDays(offset.toLong()), done = false)
        for (offset in 15..45) {
            val pause = offset == 25
            putDay(start.plusDays(offset.toLong()), done = !pause, rest = pause)
        }
        val secret = AchievementCatalog.findById("achievement.secret.to_be_continued")!!
        engine.evaluateAll()
        assertTrue("14个确认的中断日后，连续30个有效执行日应解锁", engine.getPublicView(secret).isUnlocked)
    }

    @Test
    fun testPostStageSixRequiresNinetyDaysInSameDomainAndStrictlyAfter() {
        val engine = AchievementEngine(db)
        val sql = db.writableDatabase
        val focusStageSix = AchievementCatalog.findById("achievement.evolution.focus.stage_6")!!
        fun seedStage(item: AchievementDefinition) {
            sql.execSQL(
                "INSERT OR REPLACE INTO achievement_unlocks(id, achievement_id, slot_id, unlocked_at, unlocked_date, evidence_json, status, version, seen) VALUES(?,?,?,?,?,?, 'UNLOCKED',?,1)",
                arrayOf("seed-${item.id}", item.id, item.slotId, 1L, "2026-08-01", "{\"target\":${item.threshold}}", item.version),
            )
        }
        seedStage(focusStageSix)
        val first = LocalDate.of(2026, 8, 2)
        for (i in 0 until 90) {
            val date = first.plusDays(i.toLong()).toString()
            sql.execSQL("INSERT OR REPLACE INTO contracts(day, confirmed, rest) VALUES(?,1,0)", arrayOf(date))
            sql.execSQL("INSERT OR REPLACE INTO settings(key,value) VALUES(?, '1000')", arrayOf("plan_confirmed_at_$date"))
            sql.execSQL(
                "INSERT OR REPLACE INTO tasks(id,day,title,required,minutes,mode,checklist,reward_minutes,reward_games,elapsed,done,completed_at,type_known) VALUES(?,?,?,1,0,'行动型','',0,0,0,1,2000,1)",
                arrayOf("clear-$i", date, "全清任务 $i"),
            )
        }
        val secret = AchievementCatalog.findById("achievement.secret.everything_as_usual")!!
        engine.evaluateAll()
        assertFalse("90个全清日不能替代专注系列的同领域记录", engine.getPublicView(secret).isUnlocked)

        seedStage(AchievementCatalog.findById("achievement.evolution.action.stage_6")!!)
        engine.evaluateAll()
        assertTrue("同一行动系列VI后90个实际行动日应满足条件", engine.getPublicView(secret).isUnlocked)
    }

    @Test
    fun testFirstYearSecretCountsOnlyExecutionDaysInFirstYearWindow() {
        val engine = AchievementEngine(db)
        val sql = db.writableDatabase
        fun insertDone(id: String, date: LocalDate) {
            sql.execSQL(
                "INSERT INTO tasks(id,day,title,required,minutes,mode,checklist,reward_minutes,reward_games,elapsed,done,completed_at,type_known) VALUES(?,?,?,1,0,'行动型','',0,0,0,1,1,1)",
                arrayOf(id, date.toString(), id),
            )
        }
        val first = LocalDate.of(2025, 1, 1)
        insertDone("first", first)
        for (i in 0 until 240) insertDone("late-$i", LocalDate.of(2026, 1, 2).plusDays(i.toLong()))
        engine.evaluateAll()
        val secret = AchievementCatalog.findById("achievement.secret.already_so_long")!!
        assertFalse("首个任务满一年，但240个记录都发生在首年之后，不应解锁", engine.getPublicView(secret).isUnlocked)
    }

    @Test
    fun testFirstYearSecretUnlocksAfterEnoughFirstYearExecutionDays() {
        val engine = AchievementEngine(db)
        val sql = db.writableDatabase
        val first = LocalDate.of(2025, 1, 1)
        for (i in 0..240) {
            val date = first.plusDays(i.toLong())
            sql.execSQL(
                "INSERT INTO tasks(id,day,title,required,minutes,mode,checklist,reward_minutes,reward_games,elapsed,done,completed_at,type_known) VALUES(?,?,?,1,0,'行动型','',0,0,0,1,1,1)",
                arrayOf("first-year-$i", date.toString(), "执行 $i"),
            )
        }
        engine.evaluateAll()
        assertTrue(engine.getPublicView(AchievementCatalog.findById("achievement.secret.already_so_long")!!).isUnlocked)
    }

    @Test
    fun testMissingCoverageDoesNotProveInterruption() {
        val engine = AchievementEngine(db)
        currentDateTime = LocalDateTime.of(2026, 12, 20, 10, 0)
        val sql = db.writableDatabase
        fun putExecution(date: LocalDate) {
            sql.execSQL("INSERT OR REPLACE INTO contracts(day,confirmed,rest) VALUES(?,1,0)", arrayOf(date.toString()))
            sql.execSQL(
                "INSERT OR REPLACE INTO tasks(id,day,title,required,minutes,mode,checklist,reward_minutes,reward_games,elapsed,done,completed_at,type_known) VALUES(?,?,?,1,0,'行动型','',0,0,0,1,1,1)",
                arrayOf("missing-gap-$date", date.toString(), "执行"),
            )
        }
        putExecution(LocalDate.of(2026, 11, 1))
        for (offset in 20..49) putExecution(LocalDate.of(2026, 11, 1).plusDays(offset.toLong()))
        engine.evaluateAll()
        assertFalse("无记录日不是已确认的中断证据", engine.getPublicView(AchievementCatalog.findById("achievement.secret.to_be_continued")!!).isUnlocked)
    }

    @Test
    fun testCollectionSecretNeedsSixtyDistinctSlotsAndExcludesItself() {
        val engine = AchievementEngine(db)
        val sql = db.writableDatabase
        val secret = AchievementCatalog.findById("achievement.secret.closet_of_stories")!!
        val eligibleSlots = AchievementCatalog.collectionSlots.filter { it.slotId != secret.slotId }
        fun seedSlot(slot: CollectionSlot, index: Int, status: String = "UNLOCKED") {
            val definition = AchievementCatalog.findBySlotId(slot.slotId).first()
            sql.execSQL(
                "INSERT OR REPLACE INTO achievement_unlocks(id,achievement_id,slot_id,unlocked_at,unlocked_date,evidence_json,status,version,seen) VALUES(?,?,?,?,?,'{}',?,?,1)",
                arrayOf("slot-unlock-$index", definition.id, slot.slotId, 1L, "2026-11-20", status, definition.version),
            )
        }
        eligibleSlots.take(59).forEachIndexed { index, slot -> seedSlot(slot, index) }
        seedSlot(AchievementCatalog.getSlot(secret.slotId)!!, 1000, "INVALIDATED")
        engine.evaluateAll()
        assertFalse("自身槽位不能补足第60个收藏位", engine.getPublicView(secret).isUnlocked)
        seedSlot(eligibleSlots[59], 59)
        engine.evaluateAll()
        assertTrue("60个不同的有效收藏位并满足类别门槛后解锁", engine.getPublicView(secret).isUnlocked)
    }

    @Test
    fun testAchievementUnlockDoesNotGrantEconomicRewards() {
        val engine = AchievementEngine(db)
        val initialLedgerCount = db.readableDatabase.rawQuery("SELECT COUNT(*) FROM ledger", null).use {
            it.moveToFirst()
            it.getInt(0)
        }

        // Complete 1 required task on current day so reward is granted
        val date = db.day()
        db.addTask(date, "测试经济隔离", true, 0, "行动型", "", 10, 1, "once")
        val task = db.tasks(date).single()
        db.complete(task)

        // Ledger has 2 rows from task completion (video + game)
        val afterTaskLedgerCount = db.readableDatabase.rawQuery("SELECT COUNT(*) FROM ledger", null).use {
            it.moveToFirst()
            it.getInt(0)
        }
        assertEquals(initialLedgerCount + 2, afterTaskLedgerCount)

        // Evaluate achievement
        engine.evaluateAll()
        val afterAchievementLedgerCount = db.readableDatabase.rawQuery("SELECT COUNT(*) FROM ledger", null).use {
            it.moveToFirst()
            it.getInt(0)
        }
        // Zero economic reward: ledger rows must NOT increase from achievement unlock
        assertEquals("成就解锁严禁增加娱乐额度或局数账本流水", afterTaskLedgerCount, afterAchievementLedgerCount)
    }

    @Test
    fun testDateBoundaryMidnightVsRewardDay() {
        val engine = AchievementEngine(db)
        // Set up task on day 2026-11-23
        val taskDate = "2026-11-23"
        db.contract(taskDate)
        db.addTask(taskDate, "午夜跨日任务", true, 0, "行动型", "", 0, 0, "once")
        val task = db.tasks(taskDate).single()
        db.complete(task)

        engine.evaluateAll()

        // Unlocked date must anchor to natural task date, not affected by 04:00 reward boundary
        val unlockDate = db.readableDatabase.rawQuery(
            "SELECT unlocked_date FROM achievement_unlocks WHERE achievement_id = 'achievement.evolution.action.stage_1'",
            null,
        ).use {
            it.moveToFirst()
            it.getString(0)
        }
        assertEquals("成就解锁日期必须锚定任务自然日 (2026-11-23)", taskDate, unlockDate)
    }

    @Test
    fun testCollectionSlotsSeparatedFromMilestones() {
        val engine = AchievementEngine(db)

        // 35 completed tasks
        val date = "2026-11-23"
        for (i in 1..35) {
            db.addTask(date, "任务 $i", true, 0, "行动型", "", 0, 0, "once")
            db.complete(db.tasks(date).last())
        }
        engine.evaluateAll()

        val slotsProgress = engine.getCollectionSlotsProgress()
        assertEquals("收藏位必须为 68 个", 68, slotsProgress.size)

        val actionSlot = slotsProgress.first { it.slot.slotId == "slot.evolution.01.action" }
        assertEquals("行动的故事已解锁 2 个里程碑", 2, actionSlot.unlockedMilestones)
        assertEquals(6, actionSlot.totalMilestones)
        assertTrue(actionSlot.isUnlocked)
        assertEquals("不止说说而已", actionSlot.highestUnlockedDefinition?.title)
    }
}
