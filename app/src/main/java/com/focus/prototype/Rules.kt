package com.focus.prototype
import java.time.LocalDate
import java.time.LocalDateTime

enum class DailyGoalState { ACHIEVED, MISSED, REST, NO_REQUIRED_TASKS, UNPLANNED, IN_PROGRESS, FUTURE, UNKNOWN }

fun requiredGoalAchieved(tasks: List<Task>, restDay: Boolean = false): Boolean {
 val required = tasks.filter { it.required }
 return !restDay && tasks.all { it.typeKnown } && required.isNotEmpty() && required.all { it.done }
}

fun dailyGoalState(tasks: List<Task>, restDay: Boolean, date: String, today: String, requireKnownTypes: Boolean = true): DailyGoalState = when {
  restDay -> DailyGoalState.REST
  date > today -> DailyGoalState.FUTURE
  tasks.isEmpty() -> DailyGoalState.UNPLANNED
  requireKnownTypes && tasks.any { !it.typeKnown } -> DailyGoalState.UNKNOWN
  tasks.none { it.required } -> DailyGoalState.NO_REQUIRED_TASKS
  tasks.filter { it.required }.all { it.done } -> DailyGoalState.ACHIEVED
  date < today -> DailyGoalState.MISSED
  else -> DailyGoalState.IN_PROGRESS
 }

object Rules {
 fun day(now:LocalDateTime,hour:Int):String = now.minusHours(hour.toLong()).toLocalDate().toString()
 fun repeats(rule:String, origin:String, day:String):Boolean = when {
  rule=="daily" -> true
  rule=="weekly" -> LocalDate.parse(origin).dayOfWeek==LocalDate.parse(day).dayOfWeek
  rule.startsWith("days:") -> LocalDate.parse(day).dayOfWeek.value.toString() in rule.removePrefix("days:").split(',')
  rule.startsWith("interval:") -> {
   rule.removePrefix("interval:").toLongOrNull()?.let { interval ->
    interval>0 && (LocalDate.parse(day).toEpochDay()-LocalDate.parse(origin).toEpochDay())%interval==0L
   } ?: false
  }
  else -> false
 }
}
