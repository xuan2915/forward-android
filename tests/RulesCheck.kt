package com.focus.prototype
import java.time.LocalDateTime
fun main() {
 check(Rules.day(LocalDateTime.parse("2026-10-31T03:59:59"),4)=="2026-10-30")
 check(Rules.day(LocalDateTime.parse("2026-10-31T04:00:00"),4)=="2026-10-31")
 check(Rules.repeats("days:1,3,5","2026-10-30","2026-11-02"))
 check(!Rules.repeats("days:1,3,5","2026-10-30","2026-11-03"))
 check(Rules.repeats("weekly","2026-10-30","2026-11-06"))
 check(!Rules.repeats("once","2026-10-30","2026-11-06"))
 check(Rules.repeats("daily","2026-10-30","2026-11-01"))
 println("PASS: 7 date and recurrence checks")
}
