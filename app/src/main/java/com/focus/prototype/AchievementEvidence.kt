package com.focus.prototype

import java.time.Instant
import java.time.ZoneId

internal sealed class AchievementEvidence {
    data class TaskList(val tasks: List<EvidenceTask>, val remaining: Int) : AchievementEvidence()
    data class DateList(val dates: List<String>, val remaining: Int) : AchievementEvidence()
    data class FocusList(val entries: List<EvidenceFocus>, val remaining: Int) : AchievementEvidence()
    object Hidden : AchievementEvidence()
}

internal data class EvidenceTask(
    val id: String,
    val title: String,
    val date: String,
    val completedAt: Long,
    val elapsed: Long,
    val elapsedKnown: Boolean,
)

internal data class EvidenceFocus(
    val date: String,
    val minutes: Long,
)

internal fun nextStageOf(achievementId: String): Pair<String, String>? = when (achievementId) {
    "task.first" -> "十项达成" to "累计完成 10 项任务"
    "task.ten" -> "五十项达成" to "累计完成 50 项任务"
    "streak.three" -> "连续一周" to "连续七个自律日完成目标"
    "streak.seven" -> "连续整月" to "连续三十个自律日完成目标"
    "focus.hour" -> "专注十小时" to "累计有效专注 10 小时"
    else -> null
}

internal fun loadAchievementEvidence(db: Database, item: Achievement): AchievementEvidence {
    if (item.category == "隐藏成就" && !item.earned) {
        return AchievementEvidence.Hidden
    }
    return when (item.id.substringBefore('.')) {
        "task" -> queryTaskEvidence(db, item.target)
        "streak" -> queryStreakEvidence(db, item.target, item.earned)
        "focus" -> queryFocusEvidence(db, item.target, item.earned)
        else -> AchievementEvidence.TaskList(emptyList(), item.target)
    }
}

internal fun queryTaskEvidence(db: Database, target: Int): AchievementEvidence.TaskList {
    val tasks = mutableListOf<EvidenceTask>()
    db.readableDatabase.rawQuery(
        "SELECT id, title, day, completed_at, elapsed FROM tasks WHERE done=1 AND origin!='sleep_system' AND id NOT IN (SELECT source_id FROM task_moves) ORDER BY day ASC, rowid ASC",
        null,
    ).use { cursor ->
        while (cursor.moveToNext()) {
            tasks.add(
                EvidenceTask(
                    id = cursor.getString(0),
                    title = cursor.getString(1),
                    date = cursor.getString(2),
                    completedAt = cursor.getLong(3),
                    elapsed = if (cursor.isNull(4)) 0L else cursor.getLong(4),
                    elapsedKnown = !cursor.isNull(4),
                )
            )
        }
    }
    val contributing = tasks.take(target)
    val remaining = (target - tasks.size).coerceAtLeast(0)
    return AchievementEvidence.TaskList(contributing, remaining)
}

internal fun queryStreakEvidence(db: Database, target: Int, isEarned: Boolean = false): AchievementEvidence.DateList {
    val states = db.history().filter { it.first <= db.day() }.sortedBy { it.first }
    if (isEarned) {
        val earnedRun = mutableListOf<String>()
        for ((date, state) in states) {
            when (state) {
                "完成" -> {
                    earnedRun.add(date)
                    if (earnedRun.size == target) {
                        return AchievementEvidence.DateList(earnedRun, 0)
                    }
                }
                "未完成" -> earnedRun.clear()
                else -> Unit
            }
        }
    }
    var currentRun = mutableListOf<String>()
    var bestRun = mutableListOf<String>()
    for ((date, state) in states) {
        when (state) {
            "完成" -> {
                currentRun.add(date)
                if (currentRun.size > bestRun.size) {
                    bestRun = ArrayList(currentRun)
                }
            }
            "未完成" -> currentRun.clear()
            else -> Unit
        }
    }
    val contributing = bestRun.take(target)
    val remaining = (target - bestRun.size).coerceAtLeast(0)
    return AchievementEvidence.DateList(contributing, remaining)
}

internal fun queryFocusEvidence(db: Database, targetMinutes: Int, isEarned: Boolean = false): AchievementEvidence.FocusList {
    val focusByDay = linkedMapOf<String, Long>()
    db.readableDatabase.rawQuery(
        "SELECT day, COALESCE(SUM(elapsed),0) FROM tasks WHERE origin!='sleep_system' AND id NOT IN (SELECT source_id FROM task_moves) GROUP BY day ORDER BY day",
        null,
    ).use { cursor ->
        while (cursor.moveToNext()) {
            val day = cursor.getString(0)
            val elapsed = cursor.getLong(1)
            if (elapsed > 0) focusByDay[day] = elapsed
        }
    }
    db.runningTasks().filter { it.origin != "sleep_system" }.forEach { task ->
        val extra = (db.elapsed(task) - task.elapsed).coerceAtLeast(0)
        if (extra > 0) {
            focusByDay[task.date] = (focusByDay[task.date] ?: 0L) + extra
        }
    }
    val entries = focusByDay.map { (day, millis) ->
        EvidenceFocus(day, millis / 60000)
    }.filter { it.minutes > 0 }
    val totalMinutes = entries.sumOf { it.minutes }
    val remaining = (targetMinutes - totalMinutes).coerceAtLeast(0).toInt()
    if (isEarned) {
        var accumulated = 0L
        val contributing = mutableListOf<EvidenceFocus>()
        for (e in entries) {
            contributing.add(e)
            accumulated += e.minutes
            if (accumulated >= targetMinutes) break
        }
        return AchievementEvidence.FocusList(contributing, 0)
    }
    return AchievementEvidence.FocusList(entries, remaining)
}
