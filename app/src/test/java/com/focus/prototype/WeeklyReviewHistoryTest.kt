package com.focus.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklyReviewHistoryTest {

    private val baseToday = LocalDate.of(2026, 11, 19) // Thursday

    @Test
    fun defaultIsCurrentWeek() {
        val state = WeeklyReviewState(baseToday)
        assertEquals(0, state.offset)
        assertTrue(state.isCurrentWeek)
        assertFalse(state.canGoNext)
        assertEquals(LocalDate.of(2026, 11, 16), state.weekStart)
        assertEquals(LocalDate.of(2026, 11, 23), state.weekEndExclusive)
        assertEquals("2026年11月16日—11月22日", state.rangeLabel)
    }

    @Test
    fun navigatePreviousAndNextWeek() {
        val state = WeeklyReviewState(baseToday)
        state.previousWeek()
        assertEquals(-1, state.offset)
        assertFalse(state.isCurrentWeek)
        assertTrue(state.canGoNext)
        assertEquals(LocalDate.of(2026, 11, 9), state.weekStart)
        assertEquals(LocalDate.of(2026, 11, 16), state.weekEndExclusive)
        assertEquals("2026年11月9日—11月15日", state.rangeLabel)

        state.nextWeek()
        assertEquals(0, state.offset)
        assertTrue(state.isCurrentWeek)
        assertFalse(state.canGoNext)
        assertEquals(LocalDate.of(2026, 11, 16), state.weekStart)
    }

    @Test
    fun cannotNavigateToFuture() {
        val state = WeeklyReviewState(baseToday)
        state.nextWeek() // At current week, should not advance
        assertEquals(0, state.offset)
        assertFalse(state.canGoNext)

        val invalidState = WeeklyReviewState(baseToday, initialOffset = 5)
        assertEquals(0, invalidState.offset)
    }

    @Test
    fun resetToCurrentWeek() {
        val state = WeeklyReviewState(baseToday)
        state.previousWeek()
        state.previousWeek()
        state.previousWeek()
        assertEquals(-3, state.offset)
        assertEquals(LocalDate.of(2026, 10, 26), state.weekStart)

        state.resetToCurrent()
        assertEquals(0, state.offset)
        assertTrue(state.isCurrentWeek)
        assertEquals(LocalDate.of(2026, 11, 16), state.weekStart)
    }

    @Test
    fun weekRangeLabelFormatSameYear() {
        val start = LocalDate.of(2026, 11, 16)
        val endExclusive = LocalDate.of(2026, 11, 23)
        assertEquals("2026年11月16日—11月22日", formatWeekRangeLabel(start, endExclusive))
    }

    @Test
    fun weekRangeLabelFormatCrossYear() {
        val start = LocalDate.of(2024, 12, 30) // Monday
        val endExclusive = LocalDate.of(2025, 1, 6) // Next Monday
        assertEquals("2024年12月30日—2025年1月5日", formatWeekRangeLabel(start, endExclusive))
    }

}
