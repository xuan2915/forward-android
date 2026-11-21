package com.focus.prototype

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * WeeklyReviewState manages the week offset for reviewing past completed tasks.
 *
 * Invariant rules:
 * 1. Default to current week (offset = 0).
 * 2. Offset can only be <= 0. You cannot navigate into the future (offset > 0).
 * 3. Step backward by 1 week with previousWeek().
 * 4. Step forward by 1 week with nextWeek() until offset reaches 0.
 * 5. Return to current week instantly with resetToCurrent().
 */
class WeeklyReviewState(
    val today: LocalDate,
    initialOffset: Int = 0,
) {
    var offset by mutableIntStateOf(initialOffset.coerceAtMost(0))
        private set

    val isCurrentWeek: Boolean get() = offset == 0
    val canGoNext: Boolean get() = offset < 0
    val weekStart: LocalDate get() = calculateWeekStart(today, offset)
    val weekEndExclusive: LocalDate get() = calculateWeekEndExclusive(weekStart)
    val rangeLabel: String get() = formatWeekRangeLabel(weekStart, weekEndExclusive)

    fun previousWeek() {
        offset--
    }

    fun nextWeek() {
        if (canGoNext) {
            offset++
        }
    }

    fun resetToCurrent() {
        offset = 0
    }
}

@Composable
fun rememberWeeklyReviewState(today: LocalDate, initialOffset: Int = 0): WeeklyReviewState {
    return remember(today) { WeeklyReviewState(today, initialOffset) }
}

fun calculateWeekStart(today: LocalDate, weekOffset: Int = 0): LocalDate {
    return today.with(DayOfWeek.MONDAY).plusWeeks(weekOffset.coerceAtMost(0).toLong())
}

fun calculateWeekEndExclusive(weekStart: LocalDate): LocalDate {
    return weekStart.plusDays(7)
}

fun formatWeekRangeLabel(start: LocalDate, endExclusive: LocalDate): String {
    val end = endExclusive.minusDays(1)
    return if (start.year == end.year) {
        "${start.year}年${start.monthValue}月${start.dayOfMonth}日—${end.monthValue}月${end.dayOfMonth}日"
    } else {
        "${start.year}年${start.monthValue}月${start.dayOfMonth}日—${end.year}年${end.monthValue}月${end.dayOfMonth}日"
    }
}

@Composable
fun WeeklyReviewNavRow(
    state: WeeklyReviewState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(state.rangeLabel, style = MaterialTheme.typography.labelSmall, color = AppColors.Muted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (!state.isCurrentWeek) {
                TextButton(
                    onClick = { state.resetToCurrent() },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp).semantics { contentDescription = "回到本周" },
                ) {
                    Text("本周", style = MaterialTheme.typography.labelSmall, color = AppColors.Accent)
                }
            }
            TextButton(
                onClick = { state.previousWeek() },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp).semantics { contentDescription = "上一周" },
            ) {
                Text("‹", fontSize = 20.sp, color = AppColors.Muted)
            }
            TextButton(
                onClick = { state.nextWeek() },
                enabled = state.canGoNext,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp).semantics { contentDescription = "下一周" },
            ) {
                Text("›", fontSize = 20.sp, color = if (state.canGoNext) AppColors.Muted else AppColors.Quiet)
            }
        }
    }
}
