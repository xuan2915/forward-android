package com.focus.prototype

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyCardRulesTest {
    @Test fun emptyDayDoesNotCreateCard() {
        assertFalse(dailyCardQualifies(emptyList()))
    }

    @Test fun optionalOnlyDayDoesNotCreateCard() {
        assertFalse(dailyCardQualifies(listOf(task(required = false, done = true))))
    }

    @Test fun everyRequiredTaskMustBeComplete() {
        assertFalse(dailyCardQualifies(listOf(task(required = true, done = true), task(required = true, done = false))))
    }

    @Test fun incompleteRequiredTaskDoesNotCreateCard() {
        assertFalse(dailyCardQualifies(listOf(task(required = true, done = false))))
    }

    @Test fun optionalTasksDoNotBlockCompletedRequiredTasks() {
        assertTrue(dailyCardQualifies(listOf(task(required = true, done = true), task(required = false, done = false))))
    }

    private fun task(required: Boolean, done: Boolean) = Task(
        id = "test-${required}-${done}-${System.nanoTime()}",
        date = "2026-11-10",
        title = "任务",
        required = required,
        minutes = 25,
        mode = "行动型",
        checklist = "",
        rewardMinutes = 0,
        rewardGames = 0,
        elapsed = 0L,
        runningSince = 0L,
        bootTime = 0L,
        evidence = "",
        done = done,
        claimed = false,
    )
}
