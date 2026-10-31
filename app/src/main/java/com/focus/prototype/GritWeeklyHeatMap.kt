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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
    val startIndex = (weeks.size - 20).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(24.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { index, label ->
                    Text(
                        if (index % 2 == 0) label else "",
                        Modifier.width(16.dp).height(13.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            LazyRow(
                state = state,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(weeks) { week ->
                    Column(Modifier.width(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        val monthStart = week.dates.firstOrNull { it.dayOfMonth == 1 }
                        Text(
                            monthStart?.monthValue?.toString().orEmpty(),
                            Modifier.height(11.dp).fillMaxWidth(),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                        week.dates.forEach { date ->
                            val status = history[date.toString()]
                            val previous = history[date.minusDays(1).toString()] == "完成"
                            val next = history[date.plusDays(1).toString()] == "完成"
                            val isComplete = status == "完成"
                            val position = when {
                                previous && next -> StreakPosition.MIDDLE
                                previous -> StreakPosition.END
                                next -> StreakPosition.START
                                else -> StreakPosition.ISOLATED
                            }
                            val color = when (status) {
                                "完成" -> MaterialTheme.colorScheme.primary
                                "休息" -> MaterialTheme.colorScheme.surfaceContainerHighest
                                "未完成" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = .76f)
                                else -> if (date <= today) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .45f) else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .14f)
                            }
                            Box(
                            Modifier.size(14.dp)
                                    .clip(gritHeatMapStreakShape(position, date.dayOfWeek.value == 1, date.dayOfWeek.value == 7))
                                    .background(color)
                                    .clickable { onDateSelected(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}
