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
// Layout and weekday/streak treatment adapted from Grit WeeklyHeatMap.kt.
package com.focus.prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

private data class GritWeek(val dates: List<LocalDate>)

@Composable
fun GritWeeklyHeatMap(today: LocalDate, history: Map<String, String>, onDateSelected: (LocalDate) -> Unit) {
    val weeks = remember(today) {
        val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val firstMonday = thisMonday.minusDays(51L * 7)
        (0..51).map { week -> GritWeek((0..6).map { firstMonday.plusDays(week * 7L + it) }) }
    }
    val startIndex = (weeks.size - 9).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(24.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { index, label ->
                    Text(
                        label,
                        Modifier.width(26.dp).height(35.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            LazyRow(
                state = state,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                items(weeks) { week ->
                    Column(Modifier.width(36.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val monthStart = week.dates.firstOrNull { it.dayOfMonth == 1 }
                        Text(
                            monthStart?.let { "${it.monthValue}月" }.orEmpty(),
                            Modifier.height(18.dp).fillMaxWidth(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                        week.dates.forEach { date ->
                            val status = history[date.toString()]
                            val previous = history[date.minusDays(1).toString()] == "完成"
                            val next = history[date.plusDays(1).toString()] == "完成"
                            val position = when {
                                previous && next -> StreakPosition.MIDDLE
                                previous -> StreakPosition.END
                                next -> StreakPosition.START
                                else -> StreakPosition.ISOLATED
                            }
                            val color = when (status) {
                                "完成" -> AppColors.Accent
                                "休息" -> AppColors.Border
                                "未完成" -> AppColors.Missed
                                else -> MaterialTheme.colorScheme.surface
                            }
                            Box(
                                Modifier.size(35.dp)
                                    .clip(if (position == StreakPosition.ISOLATED) RoundedCornerShape(20.dp) else gritHeatMapStreakShape(position, date.dayOfWeek.value == 1, date.dayOfWeek.value == 7))
                                    .background(color)
                                    .clickable(enabled = status != null && date <= today) { onDateSelected(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = com.focus.prototype.gritFontRounded()),
                                    color = when (status) {
                                        "完成" -> AppColors.AccentInk
                                        "未完成" -> AppColors.MissedInk
                                        "休息" -> AppColors.Muted
                                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = .55f)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
