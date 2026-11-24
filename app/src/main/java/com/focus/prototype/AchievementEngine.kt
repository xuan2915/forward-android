package com.focus.prototype

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.time.LocalDate
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.util.UUID

data class AchievementUnlockRecord(
    val id: String,
    val achievementId: String,
    val slotId: String,
    val unlockedAt: Long,
    val unlockedDate: String,
    val evidenceJson: String,
    val status: String, // "UNLOCKED", "INVALIDATED"
    val version: Int,
    val seen: Boolean,
)

data class AchievementReconcileLogRecord(
    val id: String,
    val achievementId: String,
    val action: String, // "UNLOCK", "INVALIDATE", "RECHECK", "MIGRATE"
    val reason: String,
    val createdAt: Long,
    val metadata: String,
)

data class AchievementProgressItem(
    val definition: AchievementDefinition,
    val currentProgress: Long,
    val target: Long,
    val isUnlocked: Boolean,
    val unlockedAt: Long? = null,
    val unlockedDate: String? = null,
    val isPendingEvidence: Boolean = false,
)

data class AchievementPublicView(
    val id: String,
    val slotId: String,
    val title: String,
    val description: String,
    val condition: String,
    val progress: Long,
    val target: Long,
    val isUnlocked: Boolean,
    val unlockedDate: String?,
    val rarity: AchievementRarity,
    val kind: AchievementKind,
    val isPendingEvidence: Boolean,
    val pendingReason: String,
)

data class CollectionSlotProgress(
    val slot: CollectionSlot,
    val unlockedMilestones: Int,
    val totalMilestones: Int,
    val highestUnlockedDefinition: AchievementDefinition?,
    val isUnlocked: Boolean,
)

data class AchievementEvaluationResult(
    val newlyUnlocked: List<AchievementDefinition>,
    val totalUnlocked: Int,
)

class AchievementEngine(private val db: Database) {

    companion object {
        const val TABLE_UNLOCKS = "achievement_unlocks"
        const val TABLE_RECONCILE_LOGS = "achievement_reconcile_logs"
        internal val implementedMetrics = setOf(
            "tasks_completed", "all_clear_days", "streak_days", "focus_minutes",
            "elective_tasks_completed", "consistent_weeks", "active_months",
            "quiet_week_days", "reclaimed_days", "immersion_hours",
            "consecutive_12_weeks", "rebuilt_streak_days", "double_milestones",
            "post_max_days", "first_year_days", "collection_count",
            "medium_monthly_challenges", "unique_theme_challenges",
            "consecutive_season_challenges", "annual_chapters", "explore_challenges",
        )

        internal fun supportsAvailable(item: AchievementDefinition): Boolean = when (item.metric) {
            "tasks_completed" -> item.id.startsWith("achievement.evolution.action.stage_")
            "all_clear_days" -> item.id.startsWith("achievement.evolution.all_clear.stage_")
            "streak_days" -> item.id.startsWith("achievement.evolution.streak.stage_")
            "focus_minutes" -> item.id.startsWith("achievement.evolution.focus.stage_")
            "elective_tasks_completed" -> item.id.startsWith("achievement.evolution.elective.stage_")
            "consistent_weeks" -> item.id == "achievement.indep.execution.follow_through"
            "active_months" -> item.id == "achievement.indep.execution.every_page_counts"
            "quiet_week_days" -> item.id == "achievement.indep.focus.quiet_week"
            "reclaimed_days" -> item.id == "achievement.indep.focus.reclaim_time"
            "immersion_hours" -> item.id == "achievement.indep.focus.full_immersion"
            "consecutive_12_weeks" -> item.id == "achievement.indep.balance.more_than_spark"
            "rebuilt_streak_days" -> item.id == "achievement.secret.to_be_continued"
            "double_milestones" -> item.id == "achievement.secret.double_happiness"
            "post_max_days" -> item.id == "achievement.secret.everything_as_usual"
            "first_year_days" -> item.id == "achievement.secret.already_so_long"
            "collection_count" -> item.id == "achievement.secret.closet_of_stories"
            "medium_monthly_challenges" -> item.id == "achievement.challenge.monthly.not_just_trying"
            "unique_theme_challenges" -> item.id == "achievement.challenge.monthly.not_by_luck"
            "consecutive_season_challenges" -> item.id == "achievement.challenge.monthly.whole_season"
            "annual_chapters" -> item.id == "achievement.challenge.monthly.twelve_chapters"
            "explore_challenges" -> item.id == "achievement.challenge.explore.little_adventure"
            else -> false
        }

        fun initSchema(sql: SQLiteDatabase) {
            sql.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_UNLOCKS (
                    id TEXT PRIMARY KEY,
                    achievement_id TEXT NOT NULL UNIQUE,
                    slot_id TEXT NOT NULL,
                    unlocked_at INTEGER NOT NULL,
                    unlocked_date TEXT NOT NULL,
                    evidence_json TEXT NOT NULL DEFAULT '',
                    status TEXT NOT NULL DEFAULT 'UNLOCKED',
                    version INTEGER NOT NULL DEFAULT 1,
                    seen INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            sql.execSQL("CREATE INDEX IF NOT EXISTS idx_unlock_achievement ON $TABLE_UNLOCKS(achievement_id)")
            sql.execSQL("CREATE INDEX IF NOT EXISTS idx_unlock_slot ON $TABLE_UNLOCKS(slot_id)")
            sql.execSQL("CREATE INDEX IF NOT EXISTS idx_unlock_status ON $TABLE_UNLOCKS(status)")

            sql.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_RECONCILE_LOGS (
                    id TEXT PRIMARY KEY,
                    achievement_id TEXT NOT NULL,
                    action TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    metadata TEXT NOT NULL DEFAULT ''
                )
                """.trimIndent()
            )
        }
    }

    private val maskedPublicIds = mutableMapOf<String, String>()
    private fun maskedPublicId(key: String): String = maskedPublicIds.getOrPut(key) { "masked.${UUID.randomUUID()}" }

    init {
        initSchema(db.writableDatabase)
    }

    /**
     * Evaluates all AVAILABLE achievements against domain events in the database.
     * Records new unlocks idempotently and creates reconcile logs.
     * ZERO ECONOMIC REWARD: does not grant any entertainment minutes or games.
     */
    fun evaluateAll(): AchievementEvaluationResult {
        initSchema(db.writableDatabase)
        val sql = db.writableDatabase
        val today = db.day()
        val wallNow = System.currentTimeMillis()
        val runningTasks = db.runningTasks().filter { it.origin != "sleep_system" }
        val runningExtraByDay = runningTasks.groupBy { it.date }.mapValues { (_, tasks) ->
            tasks.sumOf { (db.elapsed(it) - it.elapsed).coerceAtLeast(0L) }
        }
        sql.beginTransaction()
        try {

        // 1. Gather domain facts
        val completedTaskDates = mutableListOf<String>()
        val electiveTaskDates = mutableListOf<String>()
        sql.rawQuery(
            "SELECT day, required FROM tasks WHERE done = 1 AND origin != 'sleep_system' AND id NOT IN (SELECT source_id FROM task_moves) ORDER BY day ASC, rowid ASC",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val d = cursor.getString(0)
                val req = cursor.getInt(1) != 0
                completedTaskDates.add(d)
                if (!req) electiveTaskDates.add(d)
            }
        }

        val focusByDay = linkedMapOf<String, Long>()
        sql.rawQuery(
            "SELECT day, COALESCE(SUM(elapsed), 0) FROM tasks WHERE origin != 'sleep_system' AND id NOT IN (SELECT source_id FROM task_moves) GROUP BY day ORDER BY day ASC",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val day = cursor.getString(0)
                val elapsed = cursor.getLong(1)
                if (elapsed > 0) focusByDay[day] = elapsed
            }
        }
        runningExtraByDay.forEach { (date, extra) ->
            if (extra > 0) focusByDay[date] = (focusByDay[date] ?: 0L) + extra
        }

        val dayEvidence = buildDayEvidence(sql, today)
        val historyList = dayEvidence.timeline
        val allClearDates = dayEvidence.highQualityClearDates
        val effectiveExecutionDates = dayEvidence.effectiveExecutionDates
        var currentStreak = 0
        var bestStreak = 0
        val streakDatesAtThreshold = mutableMapOf<Int, String>()
        for ((date, state) in historyList) {
            when (state) {
                "完成" -> {
                    currentStreak++
                    if (currentStreak > bestStreak) {
                        bestStreak = currentStreak
                    }
                    if (!streakDatesAtThreshold.containsKey(currentStreak)) {
                        streakDatesAtThreshold[currentStreak] = date
                    }
                }
                "未完成" -> currentStreak = 0
                "休息" -> Unit
                else -> currentStreak = 0
            }
        }

        // Focus minutes running dates
        var focusAccum = 0L
        val focusDateAtThreshold = mutableMapOf<Long, String>()
        focusByDay.forEach { (date, millis) ->
            val mins = millis / 60000L
            focusAccum += mins
            focusDateAtThreshold.putIfAbsent(focusAccum, date)
        }
        val totalFocusMinutes = focusAccum

        // 2. Query existing unlocks
        val existingUnlocks = mutableMapOf<String, String>() // achievement_id -> status
        sql.rawQuery("SELECT achievement_id, status FROM $TABLE_UNLOCKS", null).use { cursor ->
            while (cursor.moveToNext()) {
                existingUnlocks[cursor.getString(0)] = cursor.getString(1)
            }
        }

        val newlyUnlocked = mutableListOf<AchievementDefinition>()

        // 3. Evaluate each available definition
        for (item in AchievementCatalog.all108) {
            if (item.releaseStatus != ReleaseStatus.AVAILABLE) continue
            if (item.prerequisiteIds.any { existingUnlocks[it] != "UNLOCKED" }) continue

            val (isMet, dateAchieved, evidenceJson) = evaluateCondition(
                item = item,
                sql = sql,
                completedTaskDates = completedTaskDates,
                electiveTaskDates = electiveTaskDates,
                allClearDates = allClearDates,
                effectiveExecutionDates = effectiveExecutionDates,
                bestStreak = bestStreak,
                streakDatesAtThreshold = streakDatesAtThreshold,
                totalFocusMinutes = totalFocusMinutes,
                focusByDay = focusByDay,
                historyList = historyList,
                existingUnlocks = existingUnlocks,
                today = today,
            )

            if (isMet) {
                val currentStatus = existingUnlocks[item.id]
                if (currentStatus == "UNLOCKED") {
                    // Already unlocked and active
                    continue
                }

                // Unlock milestone
                val unlockDate = dateAchieved ?: today
                val cv = ContentValues().apply {
                    put("id", "unlock_${item.id}")
                    put("achievement_id", item.id)
                    put("slot_id", item.slotId)
                    put("unlocked_at", wallNow)
                    put("unlocked_date", unlockDate)
                    put("evidence_json", evidenceJson)
                    put("status", "UNLOCKED")
                    put("version", item.version)
                    put("seen", 0)
                }
                sql.insertWithOnConflict(TABLE_UNLOCKS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)

                // Log reconcile
                val logCv = ContentValues().apply {
                    put("id", "reconcile_${UUID.randomUUID()}")
                    put("achievement_id", item.id)
                    put("action", "UNLOCK")
                    put("reason", "threshold_met")
                    put("created_at", wallNow)
                    put("metadata", evidenceJson)
                }
                sql.insertWithOnConflict(TABLE_RECONCILE_LOGS, null, logCv, SQLiteDatabase.CONFLICT_REPLACE)

                existingUnlocks[item.id] = "UNLOCKED"
                newlyUnlocked.add(item)
            }
        }

        val totalActive = sql.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_UNLOCKS WHERE status = 'UNLOCKED'",
            null,
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

        sql.setTransactionSuccessful()
        return AchievementEvaluationResult(newlyUnlocked, totalActive)
        } finally {
            sql.endTransaction()
        }
    }

    private data class DayEvidence(
        val timeline: List<Pair<String, String>>,
        val effectiveExecutionDates: List<String>,
        val highQualityClearDates: List<String>,
    )

    /** Reads the current database snapshot without calling history(), which can create plans as a side effect. */
    private fun buildDayEvidence(sql: SQLiteDatabase, today: String): DayEvidence {
        val contracts = linkedMapOf<String, Pair<Boolean, Boolean>>()
        sql.rawQuery("SELECT day, confirmed, rest FROM contracts WHERE day <= ? ORDER BY day", arrayOf(today)).use { cursor ->
            while (cursor.moveToNext()) contracts[cursor.getString(0)] = (cursor.getInt(1) != 0) to (cursor.getInt(2) != 0)
        }

        data class TaskFact(val required: Boolean, val done: Boolean, val completedAt: Long, val typeKnown: Boolean)
        val tasksByDay = linkedMapOf<String, MutableList<TaskFact>>()
        sql.rawQuery(
            "SELECT day, required, done, completed_at, type_known FROM tasks WHERE day <= ? AND origin != 'sleep_system' AND id NOT IN (SELECT source_id FROM task_moves) ORDER BY day, rowid",
            arrayOf(today),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                tasksByDay.getOrPut(cursor.getString(0)) { mutableListOf() }.add(
                    TaskFact(cursor.getInt(1) != 0, cursor.getInt(2) != 0, cursor.getLong(3), cursor.getInt(4) != 0),
                )
            }
        }

        val settings = mutableMapOf<String, String>()
        sql.rawQuery(
            "SELECT key, value FROM settings WHERE key GLOB 'plan_confirmed_at_*' OR key GLOB 'changed_????-??-??' OR key GLOB 'gate_changed_????-??-??'",
            null,
        ).use { cursor -> while (cursor.moveToNext()) settings[cursor.getString(0)] = cursor.getString(1) }
        val movedFrom = mutableSetOf<String>()
        sql.rawQuery("SELECT DISTINCT from_day FROM task_moves WHERE from_day <= ?", arrayOf(today)).use {
            while (it.moveToNext()) movedFrom.add(it.getString(0))
        }

        val effectiveDates = tasksByDay.filterValues { dayTasks -> dayTasks.any { it.done } }.keys.sorted()
        val clearDates = mutableListOf<String>()
        for ((date, contract) in contracts) {
            val (confirmed, rest) = contract
            if (!confirmed || rest || date in movedFrom) continue
            if (settings["changed_$date"] == "true" || settings["gate_changed_$date"] == "true") continue
            val confirmedAt = settings["plan_confirmed_at_$date"]?.toLongOrNull()?.takeIf { it > 0L } ?: continue
            val dayTasks = tasksByDay[date].orEmpty()
            val required = dayTasks.filter { it.required }
            if (required.isEmpty() || dayTasks.any { !it.typeKnown }) continue
            if (required.all { it.done && it.completedAt >= confirmedAt && it.completedAt > 0L }) clearDates.add(date)
        }

        val firstDate = (contracts.keys + tasksByDay.keys).minOrNull()
        val timeline = mutableListOf<Pair<String, String>>()
        if (firstDate != null) {
            var date = LocalDate.parse(firstDate)
            val end = LocalDate.parse(today)
            while (!date.isAfter(end)) {
                val key = date.toString()
                val contract = contracts[key]
                val state = when {
                    contract?.first == true && contract.second -> "休息"
                    key in effectiveDates -> "完成"
                    contract?.first == true -> "未完成"
                    else -> "待确认"
                }
                timeline.add(key to state)
                date = date.plusDays(1)
            }
        }
        return DayEvidence(timeline, effectiveDates, clearDates.sorted())
    }

    private fun consecutiveWeekAchievement(dates: List<String>, daysPerWeek: Int, requiredWeeks: Int): Pair<String, Int>? {
        if (dates.isEmpty()) return null
        val active = dates.map(LocalDate::parse).toSet()
        var week = active.minOrNull()!!.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val lastWeek = active.maxOrNull()!!.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        var run = 0
        while (!week.isAfter(lastWeek)) {
            val qualifyingDays = (0..6).map { week.plusDays(it.toLong()) }.filter { it in active }
            if (qualifyingDays.size >= daysPerWeek) {
                run++
                if (run >= requiredWeeks) return qualifyingDays[daysPerWeek - 1].toString() to run
            } else {
                run = 0
            }
            week = week.plusWeeks(1)
        }
        return null
    }

    private fun consecutiveMonthAchievement(dates: List<String>, daysPerMonth: Int, requiredMonths: Int): Pair<String, Int>? {
        if (dates.isEmpty()) return null
        val active = dates.map(LocalDate::parse).toSet()
        var month = YearMonth.from(active.minOrNull()!!)
        val lastMonth = YearMonth.from(active.maxOrNull()!!)
        var run = 0
        while (!month.isAfter(lastMonth)) {
            val qualifyingDays = active.filter { YearMonth.from(it) == month }.sorted()
            if (qualifyingDays.size >= daysPerMonth) {
                run++
                if (run >= requiredMonths) return qualifyingDays[daysPerMonth - 1].toString() to run
            } else {
                run = 0
            }
            month = month.plusMonths(1)
        }
        return null
    }

    private data class CompletedChallengeFact(
        val instanceId: String,
        val cycleId: String,
        val templateId: String,
        val category: String,
        val difficulty: String,
        val completedAt: Long,
    )

    private fun completedChallengeFacts(sql: SQLiteDatabase): List<CompletedChallengeFact> {
        val facts = mutableListOf<CompletedChallengeFact>()
        sql.rawQuery(
            """
            SELECT i.instance_id, i.cycle_id, i.template_id, i.template_snapshot, i.difficulty, i.completed_at
            FROM challenge_instances i
            JOIN challenge_rewards r ON r.instance_id = i.instance_id AND r.reward_type = 'XP' AND r.grant_status = 'GRANTED'
            JOIN xp_ledger x ON x.source_type = 'challenge' AND x.source_id = i.instance_id
                AND x.idempotency_key = r.idempotency_key AND x.status = 'ACTIVE'
            WHERE i.status = 'COMPLETED'
              AND NOT EXISTS (
                SELECT 1 FROM challenge_milestones m
                WHERE m.instance_id = i.instance_id AND m.required = 1
                  AND (m.status <> 'COMPLETED' OR TRIM(m.verification_notes) = '')
              )
              AND NOT EXISTS (
                SELECT 1 FROM challenge_milestones m
                WHERE m.instance_id = i.instance_id AND m.required = 1
                  AND NOT EXISTS (
                    SELECT 1 FROM challenge_evidences e
                    WHERE e.instance_id = i.instance_id AND e.milestone_id = m.milestone_id
                      AND e.verification_status = 'VERIFIED' AND e.action_occurred_at <= i.deadline
                  )
              )
            ORDER BY i.completed_at ASC
            """.trimIndent(),
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val snapshot = runCatching { JSONObject(cursor.getString(3)) }.getOrNull()
                facts.add(
                    CompletedChallengeFact(
                        instanceId = cursor.getString(0),
                        cycleId = cursor.getString(1),
                        templateId = cursor.getString(2),
                        category = snapshot?.optString("category").orEmpty(),
                        difficulty = cursor.getString(4),
                        completedAt = cursor.getLong(5),
                    ),
                )
            }
        }
        return facts
    }

    private fun evaluateChallengeCondition(
        item: AchievementDefinition,
        facts: List<CompletedChallengeFact>,
        today: String,
    ): Triple<Boolean, String?, String> {
        fun pass(date: String, metric: String, value: Any): Triple<Boolean, String?, String> =
            Triple(true, date, JSONObject().put(metric, value).put("achievementId", item.id).toString())

        fun dateOf(fact: CompletedChallengeFact): String =
            Instant.ofEpochMilli(fact.completedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString()

        when (item.id) {
            "achievement.challenge.monthly.not_just_trying" -> facts.firstOrNull {
                it.difficulty in setOf("BREAKTHROUGH", "LIMIT")
            }?.let { return pass(dateOf(it), "qualifiedChallenge", it.instanceId) }

            "achievement.challenge.monthly.not_by_luck" -> {
                val seen = mutableSetOf<String>()
                for (fact in facts) {
                    seen.add(fact.templateId)
                    if (seen.size >= item.threshold.toInt()) return pass(dateOf(fact), "distinctTemplates", seen.size)
                }
            }

            "achievement.challenge.monthly.whole_season" -> {
                val completedByMonth = facts.groupBy { YearMonth.parse(it.cycleId) }.toSortedMap()
                var previous: YearMonth? = null
                var run = 0
                for ((month, monthFacts) in completedByMonth) {
                    run = if (previous?.plusMonths(1) == month) run + 1 else 1
                    if (run >= item.threshold.toInt()) {
                        val fact = monthFacts.minBy { it.completedAt }
                        return pass(dateOf(fact), "consecutiveCompletedMonths", run)
                    }
                    previous = month
                }
            }

            "achievement.challenge.monthly.twelve_chapters" -> {
                val seen = mutableSetOf<String>()
                for (fact in facts) {
                    seen.add(fact.cycleId)
                    if (seen.size >= item.threshold.toInt()) return pass(dateOf(fact), "completedCycles", seen.size)
                }
            }

            "achievement.challenge.explore.little_adventure" -> facts.firstOrNull {
                it.category == ChallengeCategory.LIFE_EXPLORATION.name
            }?.let { return pass(dateOf(it), "explorationChallenge", it.instanceId) }
        }
        return Triple(false, null, JSONObject().put("challengeEvidence", "not_met").put("date", today).toString())
    }

    private fun evaluateCondition(
        item: AchievementDefinition,
        sql: SQLiteDatabase,
        completedTaskDates: List<String>,
        electiveTaskDates: List<String>,
        allClearDates: List<String>,
        effectiveExecutionDates: List<String>,
        bestStreak: Int,
        streakDatesAtThreshold: Map<Int, String>,
        totalFocusMinutes: Long,
        focusByDay: Map<String, Long>,
        historyList: List<Pair<String, String>>,
        existingUnlocks: Map<String, String>,
        today: String,
    ): Triple<Boolean, String?, String> {
        if (item.kind == AchievementKind.CHALLENGE) {
            return evaluateChallengeCondition(item, completedChallengeFacts(sql), today)
        }
        when (item.id) {
            // 01 行动的故事 (tasks completed)
            "achievement.evolution.action.stage_1",
            "achievement.evolution.action.stage_2",
            "achievement.evolution.action.stage_3",
            "achievement.evolution.action.stage_4",
            "achievement.evolution.action.stage_5",
            "achievement.evolution.action.stage_6",
            -> {
                val count = completedTaskDates.size
                if (count >= item.threshold) {
                    val date = completedTaskDates.getOrNull((item.threshold - 1).toInt()) ?: today
                    val evidence = JSONObject().put("count", count).put("target", item.threshold).put("date", date).toString()
                    return Triple(true, date, evidence)
                }
            }

            // 02 清单的终点 (all clear days)
            "achievement.evolution.all_clear.stage_1",
            "achievement.evolution.all_clear.stage_2",
            "achievement.evolution.all_clear.stage_3",
            "achievement.evolution.all_clear.stage_4",
            "achievement.evolution.all_clear.stage_5",
            "achievement.evolution.all_clear.stage_6",
            -> {
                val count = allClearDates.size
                if (count >= item.threshold) {
                    val date = allClearDates.getOrNull((item.threshold - 1).toInt()) ?: today
                    val evidence = JSONObject().put("count", count).put("target", item.threshold).put("date", date).toString()
                    return Triple(true, date, evidence)
                }
            }

            // 03 时间的朋友 (consecutive execution days / streak)
            "achievement.evolution.streak.stage_1",
            "achievement.evolution.streak.stage_2",
            "achievement.evolution.streak.stage_3",
            "achievement.evolution.streak.stage_4",
            "achievement.evolution.streak.stage_5",
            "achievement.evolution.streak.stage_6",
            -> {
                if (bestStreak >= item.threshold) {
                    val date = streakDatesAtThreshold[item.threshold.toInt()] ?: today
                    val evidence = JSONObject().put("streak", bestStreak).put("target", item.threshold).put("date", date).toString()
                    return Triple(true, date, evidence)
                }
            }

            // 04 专注的深度 (focus minutes)
            "achievement.evolution.focus.stage_1",
            "achievement.evolution.focus.stage_2",
            "achievement.evolution.focus.stage_3",
            "achievement.evolution.focus.stage_4",
            "achievement.evolution.focus.stage_5",
            "achievement.evolution.focus.stage_6",
            -> {
                if (totalFocusMinutes >= item.threshold) {
                    var sum = 0L
                    var targetDate = today
                    for ((d, m) in focusByDay) {
                        sum += m / 60000L
                        if (sum >= item.threshold) {
                            targetDate = d
                            break
                        }
                    }
                    val evidence = JSONObject().put("focusMinutes", totalFocusMinutes).put("target", item.threshold).put("date", targetDate).toString()
                    return Triple(true, targetDate, evidence)
                }
            }

            // 06 计划之外 (elective tasks completed)
            "achievement.evolution.elective.stage_1",
            "achievement.evolution.elective.stage_2",
            "achievement.evolution.elective.stage_3",
            "achievement.evolution.elective.stage_4",
            "achievement.evolution.elective.stage_5",
            "achievement.evolution.elective.stage_6",
            -> {
                val count = electiveTaskDates.size
                if (count >= item.threshold) {
                    val date = electiveTaskDates.getOrNull((item.threshold - 1).toInt()) ?: today
                    val evidence = JSONObject().put("count", count).put("target", item.threshold).put("date", date).toString()
                    return Triple(true, date, evidence)
                }
            }

            // Group 09: 有始有终 (4 consecutive weeks with >= 5 achieved days)
            "achievement.indep.execution.follow_through" -> {
                consecutiveWeekAchievement(allClearDates, 5, 4)?.let { (date, weeks) ->
                    return Triple(true, date, JSONObject().put("consecutiveWeeks", weeks).put("highQualityDaysPerWeek", 5).toString())
                }
            }

            // Group 09: 每一页都算数 (12 consecutive months with >= 20 achieved days)
            "achievement.indep.execution.every_page_counts" -> {
                consecutiveMonthAchievement(effectiveExecutionDates, 20, 12)?.let { (date, months) ->
                    return Triple(true, date, JSONObject().put("consecutiveMonths", months).put("effectiveDaysPerMonth", 20).toString())
                }
            }

            // Group 10: 安静的一周 (7 consecutive days with focus >= 60 min)
            "achievement.indep.focus.quiet_week" -> {
                val days60 = focusByDay.filter { it.value >= 60 * 60000L }.keys.sorted()
                var run = 0
                var prevDate: LocalDate? = null
                for (dStr in days60) {
                    val d = LocalDate.parse(dStr)
                    if (prevDate != null && prevDate.plusDays(1) == d) {
                        run++
                    } else {
                        run = 1
                    }
                    if (run >= 7) {
                        return Triple(true, dStr, JSONObject().put("consecutiveDays", run).toString())
                    }
                    prevDate = d
                }
            }

            // Group 10: 把时间找回来 (In 30-day window >= 25 days with focus >= 90m)
            "achievement.indep.focus.reclaim_time" -> {
                val days90 = focusByDay.filter { it.value >= 90 * 60000L }.keys.map { LocalDate.parse(it) }.toSet()
                if (days90.size >= 25) {
                    val minDate = days90.minOrNull() ?: LocalDate.parse(today)
                    val maxDate = days90.maxOrNull() ?: LocalDate.parse(today)
                    var cur = minDate
                    while (!cur.isAfter(maxDate.minusDays(29))) {
                        val windowDays = (0 until 30).count { days90.contains(cur.plusDays(it.toLong())) }
                        if (windowDays >= 25) {
                            val winEnd = cur.plusDays(29).toString()
                            return Triple(true, winEnd, JSONObject().put("windowDays", windowDays).toString())
                        }
                        cur = cur.plusDays(1)
                    }
                }
            }

            // Group 10: 全神贯注 (90-day window with total focus >= 180h (10800m) and >= 60 days with focus)
            "achievement.indep.focus.full_immersion" -> {
                val activeDays = focusByDay.filter { it.value > 0 }.mapKeys { LocalDate.parse(it.key) }
                if (activeDays.size >= 60 && totalFocusMinutes >= 10800L) {
                    val minDate = activeDays.keys.minOrNull() ?: LocalDate.parse(today)
                    val maxDate = activeDays.keys.maxOrNull() ?: LocalDate.parse(today)
                    var cur = minDate
                    while (!cur.isAfter(maxDate.minusDays(89))) {
                        var winFocusMins = 0L
                        var winCount = 0
                        for (i in 0 until 90) {
                            val millis = activeDays[cur.plusDays(i.toLong())] ?: 0L
                            if (millis > 0) {
                                winCount++
                                winFocusMins += millis / 60000L
                            }
                        }
                        if (winCount >= 60 && winFocusMins >= 10800L) {
                            return Triple(true, cur.plusDays(89).toString(), JSONObject().put("focusMinutes", winFocusMins).put("daysWithFocus", winCount).toString())
                        }
                        cur = cur.plusDays(1)
                    }
                }
            }

            // Group 13: 不止三分钟热度 (12 consecutive weeks with >= 5 achieved days)
            "achievement.indep.balance.more_than_spark" -> {
                consecutiveWeekAchievement(effectiveExecutionDates, 5, 12)?.let { (date, weeks) ->
                    return Triple(true, date, JSONObject().put("consecutiveWeeks", weeks).put("effectiveDaysPerWeek", 5).toString())
                }
            }

            // Secret: 未完待续 (Interrupted >= 14 days, then rebuilt 30 streak days)
            "achievement.secret.to_be_continued" -> {
                var had14DayBreak = false
                var streakAfterBreak = 0
                var inactiveDays = 0
                var hasPriorExecution = false
                for ((dStr, state) in historyList) {
                    if (state == "完成") {
                        if (hasPriorExecution && inactiveDays >= 14) {
                            had14DayBreak = true
                            streakAfterBreak = 1
                        } else if (had14DayBreak) {
                            streakAfterBreak = if (streakAfterBreak == 0) 1 else streakAfterBreak + 1
                        }
                        hasPriorExecution = true
                        inactiveDays = 0
                        if (streakAfterBreak >= 30) {
                            return Triple(true, dStr, JSONObject().put("interruptionDays", 14).put("rebuiltExecutionDays", streakAfterBreak).toString())
                        }
                    } else if (state == "未完成") {
                        inactiveDays++
                        if (had14DayBreak) streakAfterBreak = 0
                    } else if (state == "待确认") {
                        // Unknown coverage cannot prove either an interruption or a rebuilt run.
                        inactiveDays = 0
                        if (had14DayBreak) streakAfterBreak = 0
                    }
                }
            }

            // Secret: 好事成双 (Two different evolution lines reached stage IV+ on same day)
            "achievement.secret.double_happiness" -> {
                val highStagesByDate = mutableMapOf<String, MutableSet<String>>()
                db.readableDatabase.rawQuery(
                    "SELECT achievement_id, unlocked_date FROM $TABLE_UNLOCKS WHERE status = 'UNLOCKED'",
                    null,
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val achId = cursor.getString(0)
                        val uDate = cursor.getString(1)
                        val def = AchievementCatalog.findById(achId)
                        if (def?.kind == AchievementKind.EVOLUTION && (def.stage ?: 0) >= 4) {
                            highStagesByDate.computeIfAbsent(uDate) { mutableSetOf() }.add(def.seriesId ?: "")
                        }
                    }
                }
                for ((d, seriesSet) in highStagesByDate) {
                    if (seriesSet.size >= 2) {
                        return Triple(true, d, JSONObject().put("series", seriesSet.toList()).toString())
                    }
                }
            }

            // Secret: 一切照常 (Any evolution reached stage VI, then 90 achieved days)
            "achievement.secret.everything_as_usual" -> {
                var completedSeries: String? = null
                var stageViUnlockedDate: String? = null
                db.readableDatabase.rawQuery(
                    "SELECT achievement_id, unlocked_date FROM $TABLE_UNLOCKS WHERE status = 'UNLOCKED' AND achievement_id LIKE 'achievement.evolution.%.stage_6' ORDER BY unlocked_date ASC",
                    null,
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        val date = cursor.getString(1)
                        val series = AchievementCatalog.findById(id)?.seriesId ?: continue
                        val domainDates = when (series) {
                            "evolution.01.action" -> completedTaskDates
                            "evolution.02.all_clear" -> allClearDates
                            "evolution.03.streak" -> effectiveExecutionDates
                            "evolution.04.focus" -> focusByDay.filterValues { it > 0L }.keys.toList()
                            "evolution.06.elective" -> electiveTaskDates
                            else -> emptyList()
                        }
                        if (domainDates.distinct().count { it > date } >= 90) {
                            completedSeries = series
                            stageViUnlockedDate = date
                            break
                        }
                    }
                }
                if (stageViUnlockedDate != null) {
                    return Triple(true, today, JSONObject().put("series", completedSeries).put("stage6Date", stageViUnlockedDate).put("effectiveDomainDaysAfter", 90).toString())
                }
            }

            // Secret: 原来已经这么久 (First task date >= 365 days ago, and >= 240 active days)
            "achievement.secret.already_so_long" -> {
                val firstDate = completedTaskDates.firstOrNull()
                if (firstDate != null) {
                    val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(firstDate), LocalDate.parse(today))
                    val firstYearEndExclusive = LocalDate.parse(firstDate).plusDays(365)
                    val firstYearExecutionDays = effectiveExecutionDates.count {
                        it >= firstDate && LocalDate.parse(it).isBefore(firstYearEndExclusive)
                    }
                    if (daysBetween >= 365 && firstYearExecutionDays >= 240) {
                        return Triple(true, today, JSONObject().put("daysBetween", daysBetween).put("firstYearExecutionDays", firstYearExecutionDays).put("firstYearEndExclusive", firstYearEndExclusive.toString()).toString())
                    }
                }
            }

            // Secret: 一柜子的故事 (Unlocked collection slots >= 60, excluding this secret milestone)
            "achievement.secret.closet_of_stories" -> {
                val slotKinds = mutableMapOf<String, AchievementKind>()
                db.readableDatabase.rawQuery(
                    "SELECT DISTINCT slot_id FROM $TABLE_UNLOCKS WHERE status = 'UNLOCKED' AND achievement_id != ?",
                    arrayOf(item.id),
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val slotId = cursor.getString(0)
                        AchievementCatalog.getSlot(slotId)?.let { slotKinds[slotId] = it.kind }
                    }
                }
                val challengeOrSecretSlots = slotKinds.values.count { it == AchievementKind.CHALLENGE || it == AchievementKind.SECRET }
                if (slotKinds.size >= 60 && challengeOrSecretSlots >= 6) {
                    return Triple(true, today, JSONObject().put("unlockedSlots", slotKinds.size).put("challengeOrSecretSlots", challengeOrSecretSlots).toString())
                }
            }
        }

        return Triple(false, null, "")
    }

    /**
     * Consume unseen unlock notices (e.g. for feedback / badge popup).
     * Marks them as seen in DB and sets achievement_seen_${id} = true for compatibility.
     */
    fun consumePendingUnseenNotices(): List<AchievementUnlockRecord> {
        val list = mutableListOf<AchievementUnlockRecord>()
        val sql = db.writableDatabase
        sql.rawQuery(
            "SELECT id, achievement_id, slot_id, unlocked_at, unlocked_date, evidence_json, status, version, seen FROM $TABLE_UNLOCKS WHERE status = 'UNLOCKED' AND seen = 0 ORDER BY unlocked_at ASC",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    AchievementUnlockRecord(
                        id = cursor.getString(0),
                        achievementId = cursor.getString(1),
                        slotId = cursor.getString(2),
                        unlockedAt = cursor.getLong(3),
                        unlockedDate = cursor.getString(4),
                        evidenceJson = cursor.getString(5),
                        status = cursor.getString(6),
                        version = cursor.getInt(7),
                        seen = false,
                    )
                )
            }
        }
        if (list.isNotEmpty()) {
            sql.beginTransaction()
            try {
                for (rec in list) {
                    sql.execSQL("UPDATE $TABLE_UNLOCKS SET seen = 1 WHERE id = ?", arrayOf(rec.id))
                    db.setSetting("achievement_seen_${rec.achievementId}", "true")
                }
                sql.setTransactionSuccessful()
            } finally {
                sql.endTransaction()
            }
        }
        return list
    }

    /** Query an old public ID or its normalized replacement without discarding legacy.* rows. */
    fun findUnlock(achievementId: String): AchievementUnlockRecord? {
        val normalizedId = AchievementCatalog.mapLegacyId(achievementId)
        return db.readableDatabase.rawQuery(
            "SELECT id, achievement_id, slot_id, unlocked_at, unlocked_date, evidence_json, status, version, seen FROM $TABLE_UNLOCKS WHERE achievement_id=? OR achievement_id=? ORDER BY CASE WHEN achievement_id=? THEN 0 ELSE 1 END LIMIT 1",
            arrayOf(normalizedId, achievementId, normalizedId),
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else AchievementUnlockRecord(
                id = cursor.getString(0),
                achievementId = cursor.getString(1),
                slotId = cursor.getString(2),
                unlockedAt = cursor.getLong(3),
                unlockedDate = cursor.getString(4),
                evidenceJson = cursor.getString(5),
                status = cursor.getString(6),
                version = cursor.getInt(7),
                seen = cursor.getInt(8) != 0,
            )
        }
    }

    /**
     * Invalidate an achievement unlock (e.g. task revocation).
     * Legitimate past collections where threshold was raised are NOT revoked.
     */
    fun invalidate(achievementId: String, reason: String) {
        val sql = db.writableDatabase
        sql.beginTransaction()
        try {
            val changed = sql.update(TABLE_UNLOCKS, ContentValues().apply { put("status", "INVALIDATED") }, "achievement_id=? AND status='UNLOCKED'", arrayOf(achievementId))
            if (changed > 0) {
                val logCv = ContentValues().apply {
                    put("id", "reconcile_${UUID.randomUUID()}")
                    put("achievement_id", achievementId)
                    put("action", "INVALIDATE")
                    put("reason", reason)
                    put("created_at", System.currentTimeMillis())
                    put("metadata", "")
                }
                sql.insertOrThrow(TABLE_RECONCILE_LOGS, null, logCv)
            }
            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
    }

    /**
     * Reconcile logic on task revocation.
     */
    fun reconcileOnTaskRevoke(task: Task) {
        val sql = db.readableDatabase
        val completedCount = sql.rawQuery(
            "SELECT COUNT(*) FROM tasks WHERE done = 1 AND origin != 'sleep_system' AND id NOT IN (SELECT source_id FROM task_moves)", null,
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        val electiveCount = sql.rawQuery(
            "SELECT COUNT(*) FROM tasks WHERE done = 1 AND required = 0 AND origin != 'sleep_system' AND id NOT IN (SELECT source_id FROM task_moves)", null,
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        val evidence = buildDayEvidence(sql, db.day())
        var currentStreak = 0
        var bestStreak = 0
        for ((_, state) in evidence.timeline) {
            when (state) {
                "完成" -> { currentStreak++; bestStreak = maxOf(bestStreak, currentStreak) }
                "休息" -> Unit
                else -> currentStreak = 0
            }
        }
        val currentValues = mapOf(
            "evolution.01.action" to completedCount.toLong(),
            "evolution.02.all_clear" to evidence.highQualityClearDates.size.toLong(),
            "evolution.03.streak" to bestStreak.toLong(),
            "evolution.06.elective" to electiveCount.toLong(),
        )
        val affected = mutableListOf<Pair<String, Long>>()
        sql.rawQuery("SELECT achievement_id, evidence_json FROM $TABLE_UNLOCKS WHERE status='UNLOCKED'", null).use { cursor ->
            while (cursor.moveToNext()) {
                val item = AchievementCatalog.findById(cursor.getString(0)) ?: continue
                val current = item.seriesId?.let(currentValues::get) ?: continue
                val awardedTarget = runCatching { JSONObject(cursor.getString(1)).optLong("target", 0L) }.getOrDefault(0L)
                // Preserve a past award if a later catalog version raised its threshold.
                if (awardedTarget > 0L && awardedTarget >= item.threshold && current < awardedTarget) {
                    affected.add(item.id to current)
                }
            }
        }
        affected.forEach { (id, current) -> invalidate(id, "task_revoked:current_evidence_$current") }
    }

    /**
     * Query public view of an achievement, ensuring secret achievements are masked when locked.
     */
    fun getPublicView(item: AchievementDefinition): AchievementPublicView {
        var isUnlocked = false
        var unlockedDate: String? = null
        db.readableDatabase.rawQuery(
            "SELECT unlocked_date FROM $TABLE_UNLOCKS WHERE achievement_id = ? AND status = 'UNLOCKED'",
            arrayOf(item.id),
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                isUnlocked = true
                unlockedDate = cursor.getString(0)
            }
        }

        if (item.kind == AchievementKind.SECRET && !isUnlocked) {
            return AchievementPublicView(
                id = maskedPublicId("achievement:${item.id}"),
                slotId = maskedPublicId("slot:${item.slotId}"),
                title = "隐藏成就",
                description = "达成特定条件后揭晓",
                condition = "未知",
                progress = 0L,
                target = 1L,
                isUnlocked = false,
                unlockedDate = null,
                rarity = AchievementRarity.COMMON,
                kind = item.kind,
                isPendingEvidence = false,
                pendingReason = "",
            )
        }

        val progress = queryCurrentMetric(item)

        return AchievementPublicView(
            id = item.id,
            slotId = item.slotId,
            title = item.title,
            description = item.description,
            condition = item.conditionDescription,
            progress = progress,
            target = item.threshold,
            isUnlocked = isUnlocked,
            unlockedDate = unlockedDate,
            rarity = item.rarity,
            kind = item.kind,
            isPendingEvidence = item.releaseStatus == ReleaseStatus.PENDING_EVIDENCE,
            pendingReason = item.pendingReason,
        )
    }

    private fun queryCurrentMetric(item: AchievementDefinition): Long {
        return when (item.metric) {
            "tasks_completed" -> db.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM tasks WHERE done = 1 AND origin != 'sleep_system' AND id NOT IN (SELECT source_id FROM task_moves)",
                null,
            ).use { if (it.moveToFirst()) it.getLong(0) else 0L }

            "elective_tasks_completed" -> db.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM tasks WHERE done = 1 AND required = 0 AND origin != 'sleep_system' AND id NOT IN (SELECT source_id FROM task_moves)",
                null,
            ).use { if (it.moveToFirst()) it.getLong(0) else 0L }

            "streak_days" -> {
                var current = 0
                var best = 0
                for ((_, state) in buildDayEvidence(db.readableDatabase, db.day()).timeline) {
                    when (state) {
                        "完成" -> { current++; best = maxOf(best, current) }
                        "休息" -> Unit
                        else -> current = 0
                    }
                }
                best.toLong()
            }

            "focus_minutes" -> {
                val focusMillis = db.readableDatabase.rawQuery(
                    "SELECT COALESCE(SUM(elapsed), 0) FROM tasks WHERE origin != 'sleep_system' AND id NOT IN (SELECT source_id FROM task_moves)",
                    null,
                ).use { if (it.moveToFirst()) it.getLong(0) else 0L }
                focusMillis / 60000L
            }

            "all_clear_days" -> buildDayEvidence(db.readableDatabase, db.day()).highQualityClearDates.size.toLong()

            else -> 0L
        }
    }

    /**
     * Query all 68 collection slots progress.
     */
    fun getCollectionSlotsProgress(): List<CollectionSlotProgress> {
        val list = mutableListOf<CollectionSlotProgress>()
        for (slot in AchievementCatalog.collectionSlots) {
            val milestones = AchievementCatalog.findBySlotId(slot.slotId)
            val unlockedRecords = mutableListOf<AchievementDefinition>()
            for (m in milestones) {
                val isUnlocked = db.readableDatabase.rawQuery(
                    "SELECT COUNT(*) FROM $TABLE_UNLOCKS WHERE achievement_id = ? AND status = 'UNLOCKED'",
                    arrayOf(m.id),
                ).use { if (it.moveToFirst()) it.getInt(0) > 0 else false }
                if (isUnlocked) unlockedRecords.add(m)
            }
            val highest = unlockedRecords.maxByOrNull { it.stage ?: 1 }
            list.add(
                CollectionSlotProgress(
                    slot = if (slot.kind == AchievementKind.SECRET && unlockedRecords.isEmpty()) {
                        slot.copy(slotId = maskedPublicId("slot:${slot.slotId}"), seriesId = null, title = "隐藏成就", totalMilestones = 1)
                    } else slot,
                    unlockedMilestones = unlockedRecords.size,
                    totalMilestones = if (slot.kind == AchievementKind.SECRET && unlockedRecords.isEmpty()) 1 else milestones.size,
                    highestUnlockedDefinition = highest,
                    isUnlocked = unlockedRecords.isNotEmpty(),
                )
            )
        }
        return list
    }
}
