package com.focus.prototype
import java.time.LocalDate
import java.time.LocalDateTime
object Rules {
 fun day(now:LocalDateTime,hour:Int):String = now.minusHours(hour.toLong()).toLocalDate().toString()
 fun repeats(rule:String, origin:String, day:String):Boolean = when { rule=="daily" -> true; rule=="weekly" -> LocalDate.parse(origin).dayOfWeek==LocalDate.parse(day).dayOfWeek; rule.startsWith("days:") -> LocalDate.parse(day).dayOfWeek.value.toString() in rule.removePrefix("days:").split(','); else -> false }
}
