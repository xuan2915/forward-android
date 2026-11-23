package com.focus.prototype
import java.time.LocalDate
import java.time.LocalDateTime

enum class DailyGoalState { ACHIEVED, MISSED, REST, NO_REQUIRED_TASKS, UNPLANNED, IN_PROGRESS, FUTURE, UNKNOWN }

fun requiredGoalAchieved(tasks: List<Task>, restDay: Boolean = false): Boolean {
 val ordinary = tasks.filter { it.origin != "sleep_system" }
 val required = ordinary.filter { it.required }
 return !restDay && ordinary.all { it.typeKnown } && required.isNotEmpty() && required.all { it.done }
}

fun dailyGoalState(tasks: List<Task>, restDay: Boolean, date: String, today: String, requireKnownTypes: Boolean = true): DailyGoalState {
  val ordinary = tasks.filter { it.origin != "sleep_system" }
  return when {
   restDay -> DailyGoalState.REST
   date > today -> DailyGoalState.FUTURE
   ordinary.isEmpty() -> DailyGoalState.UNPLANNED
   requireKnownTypes && ordinary.any { !it.typeKnown } -> DailyGoalState.UNKNOWN
   ordinary.none { it.required } -> DailyGoalState.NO_REQUIRED_TASKS
   ordinary.filter { it.required }.all { it.done } -> DailyGoalState.ACHIEVED
   date < today -> DailyGoalState.MISSED
   else -> DailyGoalState.IN_PROGRESS
  }
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
