/*
 * Copyright (C) 2026  Shubham Gorai
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
// Adapted CalendarCommon.MonthlyCalendarDayContent: java.time dates and existing history.
package com.focus.prototype
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kizitonwose.calendar.core.*
import java.time.LocalDate
enum class StreakPosition { ISOLATED, START, MIDDLE, END }
@Composable fun GritCalendarDay(day:CalendarDay,today:LocalDate,selected:LocalDate,history:Map<String,String>,onSelected:(LocalDate)->Unit) {
 if(day.position!=DayPosition.MonthDate) { Spacer(Modifier.height(40.dp));return }
 val done=history[day.date.toString()]=="完成"
 val before=history[day.date.minusDays(1).toString()]=="完成";val after=history[day.date.plusDays(1).toString()]=="完成"
 val position=when {before&&after->StreakPosition.MIDDLE;before->StreakPosition.END;after->StreakPosition.START;else->StreakPosition.ISOLATED}
 val shape=calendarMapStreakShape(position,day.date.dayOfWeek.value==1,day.date.dayOfWeek.value==7,day.date.dayOfMonth==1,day.date.plusDays(1).dayOfMonth==1)
 val state=history[day.date.toString()]
 val color=when {done->MaterialTheme.colorScheme.primary;state=="休息"->MaterialTheme.colorScheme.surfaceContainerHighest;state=="未完成"->MaterialTheme.colorScheme.errorContainer;else->MaterialTheme.colorScheme.surfaceContainer}
 Box(Modifier.padding(vertical=1.dp).fillMaxWidth().height(40.dp).clip(shape).background(color).then(if(day.date==selected) Modifier.border(1.dp,MaterialTheme.colorScheme.primary,shape) else Modifier).clickable {onSelected(day.date)},contentAlignment=Alignment.Center) {
  Text(day.date.dayOfMonth.toString(),style=MaterialTheme.typography.bodyLarge,color=if(done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha=if(day.date>today) .5f else 1f))
 }
}
fun calendarMapStreakShape(
    streakPosition: StreakPosition,
    isFirstDayOfWeek: Boolean,
    isLastDayOfWeek: Boolean,
    isFirstDayOfMonth: Boolean,
    isLastDayOfMonth: Boolean,
): Shape {
    if (streakPosition == StreakPosition.ISOLATED) return CircleShape

    // Calculate left-side corners (topStart, bottomStart)
    val leftRadius =
        when {
            streakPosition == StreakPosition.START -> 20.dp
            isFirstDayOfWeek || isFirstDayOfMonth -> 4.dp
            else -> 0.dp
        }

    // Calculate right-side corners (topEnd, bottomEnd)
    val rightRadius =
        when {
            streakPosition == StreakPosition.END -> 20.dp
            isLastDayOfWeek || isLastDayOfMonth -> 4.dp
            else -> 0.dp
        }

    return RoundedCornerShape(
        topStart = leftRadius,
        bottomStart = leftRadius,
        topEnd = rightRadius,
        bottomEnd = rightRadius,
    )
}
