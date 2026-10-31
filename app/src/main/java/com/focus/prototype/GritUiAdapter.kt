/*
 * Copyright (C) 2026 Shubham Gorai
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This file adapts Grit's habit analytics to Self Discipline task records.
 * Copyright (C) 2026 Self Discipline contributors.
 */
package com.focus.prototype

import java.time.DayOfWeek
import java.time.LocalDate

internal data class GritHabitDay(val date: LocalDate, val done: Boolean, val elapsed: Long)

internal data class GritHabitData(
    val taskId: String,
    val title: String,
    val description: String,
    val startDate: LocalDate,
    val asOf: LocalDate,
    val consistency: Float,
    val bestStreak: Int,
    val currentStreak: Int,
    val days: List<GritHabitDay>,
) {
    val heatMap: Map<String, String> = days.associate { it.date.toString() to if (it.done) "完成" else "未完成" }
}

/** Maps a task template and its real dated instances to the fields consumed by Grit analytics. */
internal fun gritHabitData(db: Database, taskId: String): GritHabitData? {
    val task = db.task(taskId) ?: return null
    val template = db.readableDatabase.rawQuery(
        "SELECT t.template_id,t.origin,t.recurrence,x.start_day,x.rule FROM tasks t " +
            "LEFT JOIN templates x ON x.id=t.template_id WHERE t.id=?",
        arrayOf(taskId),
    ).use { cursor ->
        if (!cursor.moveToFirst()) null else Triple(
            cursor.getString(0).orEmpty().ifBlank { taskId },
            cursor.getString(3)?.takeIf(String::isNotBlank) ?: cursor.getString(1),
            cursor.getString(4)?.takeIf(String::isNotBlank) ?: cursor.getString(2),
        )
    } ?: return null

    val templateId = template.first
    val start = LocalDate.parse(template.second)
    val today = LocalDate.parse(db.day())
    val rule = template.third
    val instances = mutableMapOf<LocalDate, GritHabitDay>()
    db.readableDatabase.rawQuery(
        "SELECT day,done,elapsed FROM tasks WHERE template_id=? AND day>=? AND day<=? ORDER BY day",
        arrayOf(templateId, start.toString(), today.toString()),
    ).use { cursor ->
        while (cursor.moveToNext()) {
            val date = LocalDate.parse(cursor.getString(0))
            instances[date] = GritHabitDay(date, cursor.getInt(1) != 0, cursor.getLong(2))
        }
    }
    if (instances.isEmpty()) {
        instances[LocalDate.parse(task.date)] = GritHabitDay(LocalDate.parse(task.date), task.done, task.elapsed)
    }

    val scheduled = mutableListOf<GritHabitDay>()
    var date = start
    while (date <= today) {
        if (repeatsOn(rule, start, date)) {
            val restDay = db.readableDatabase.rawQuery(
                "SELECT rest FROM contracts WHERE day=?",
                arrayOf(date.toString()),
            ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) != 0 }
            if (!restDay) scheduled += instances[date] ?: GritHabitDay(date, false, 0L)
        }
        date = date.plusDays(1)
    }

    val completedDates = scheduled.filter(GritHabitDay::done).map(GritHabitDay::date)
    val (best, current) = countGritStreaks(completedDates, scheduled.map(GritHabitDay::date), today)
    val consistency = if (scheduled.isEmpty()) 0f else completedDates.size.toFloat() / scheduled.size
    val description = buildString {
        append(if (task.required) "必做" else "选做")
        append(" · ")
        append(if (task.minutes > 0) "${task.minutes} 分钟" else "自由专注")
        append(" · ")
        append(when {
            rule == "daily" -> "每天"
            rule == "weekly" -> "每周${start.dayOfWeek.cnName()}"
            rule.startsWith("days:") -> rule.removePrefix("days:").split(',').mapNotNull { it.toIntOrNull() }
                .sorted().joinToString("、") { "周${it.cnWeekName()}" }
            else -> "一次性"
        })
    }
    return GritHabitData(taskId, task.title, description, start, today, consistency.coerceIn(0f, 1f), best, current, scheduled)
}

private fun repeatsOn(rule: String, start: LocalDate, date: LocalDate): Boolean = when {
    rule == "daily" -> true
    rule == "weekly" -> date.dayOfWeek == start.dayOfWeek
    rule.startsWith("days:") -> date.dayOfWeek.value.toString() in rule.removePrefix("days:").split(',')
    else -> date == start
}

private fun countGritStreaks(
    completed: List<LocalDate>,
    scheduled: List<LocalDate>,
    today: LocalDate,
): Pair<Int, Int> {
    if (completed.isEmpty()) return 0 to 0
    val scheduledSet = scheduled.toSet()
    val completedSet = completed.toSet()
    var best = 0
    var run = 0
    scheduled.forEach { date ->
        if (date in completedSet) {
            run++
            best = maxOf(best, run)
        } else run = 0
    }
    var last = completed.maxOrNull() ?: return best to 0
    if (scheduledSet.any { it > last && it < today && it !in completedSet }) return best to 0
    var current = 1
    val sorted = completed.sorted()
    var index = sorted.lastIndex - 1
    while (index >= 0) {
        val previous = sorted[index]
        if (scheduledSet.any { it > previous && it < last && it !in completedSet }) break
        current++
        last = previous
        index--
    }
    return best to current
}

private fun DayOfWeek.cnName(): String = when (this) {
    DayOfWeek.MONDAY -> "一"
    DayOfWeek.TUESDAY -> "二"
    DayOfWeek.WEDNESDAY -> "三"
    DayOfWeek.THURSDAY -> "四"
    DayOfWeek.FRIDAY -> "五"
    DayOfWeek.SATURDAY -> "六"
    DayOfWeek.SUNDAY -> "日"
}

private fun Int.cnWeekName(): String = when (this) {
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    6 -> "六"
    7 -> "日"
    else -> "?"
}
