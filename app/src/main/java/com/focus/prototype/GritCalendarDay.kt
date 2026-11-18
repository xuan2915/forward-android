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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kizitonwose.calendar.core.*
import java.time.LocalDate

@Composable
fun GritCalendarDay(
    day: CalendarDay,
    today: LocalDate,
    selected: LocalDate,
    history: Map<String, String>,
    onSelected: (LocalDate) -> Unit,
) {
    if (day.position != DayPosition.MonthDate) {
        Spacer(Modifier.height(40.dp))
        return
    }
    val state = history[day.date.toString()]
    val done = state == "完成"
    val color = when {
        done -> AppColors.Accent
        state == "休息" -> AppColors.Border
        state == "未完成" -> AppColors.Missed
        state == "无必做" -> MaterialTheme.colorScheme.surfaceContainerHigh
        state == "进行中" -> MaterialTheme.colorScheme.primary.copy(alpha = .16f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Box(
        Modifier.fillMaxWidth().height(40.dp).clickable { onSelected(day.date) },
        contentAlignment = Alignment.Center,
    ) {
        val circle = CircleShape
        Box(
            Modifier.size(36.dp).clip(circle).background(color)
                .then(if (day.date == selected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, circle) else if (state == "待确认") Modifier.border(1.dp, AppColors.Muted, circle) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    done -> AppColors.AccentInk
                    state == "未完成" -> AppColors.MissedInk
                    state == "休息" || state == "无必做" -> AppColors.Muted
                    day.date > today -> AppColors.Muted.copy(alpha = .55f)
                    else -> AppColors.Text
                },
            )
        }
    }
}
