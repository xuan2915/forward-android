package com.focus.prototype

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyCardRulesTest {
    @Test fun editionsUseStableKeysAndRouteLongQuotesToPaper() {
        assertTrue(CardEdition.fromKey("sky-meadow-v1") == CardEdition.Sky)
        assertTrue(CardEdition.fromKey("") == CardEdition.Legacy)
        assertTrue(cardEdition("长".repeat(42), 3) == CardEdition.Paper)
        assertTrue(cardEdition("短句", 3) == CardEdition.Sky)
    }

    @Test fun catalogHasDistinctIdsAndSourceMetadata() {
        val entries = DailyExcerpts.entries
        assertTrue(entries.size in 15..20)
        assertTrue(entries.map { it.id }.distinct().size == entries.size)
        assertTrue(entries.all { it.original.isNotBlank() && it.chinese.isNotBlank() &&
            it.author.isNotBlank() && it.work.isNotBlank() && it.source.startsWith("https://") &&
            it.themes.isNotEmpty() })
    }
    @Test fun emptyDayDoesNotCreateCard() {
        assertFalse(dailyCardQualifies(emptyList()))
    }

    @Test fun completedOptionalOnlyDayCreatesCard() {
        assertTrue(dailyCardQualifies(listOf(task(required = false, done = true))))
    }

    @Test fun everyRequiredTaskMustBeComplete() {
        assertFalse(dailyCardQualifies(listOf(task(required = true, done = true), task(required = true, done = false))))
    }

    @Test fun incompleteRequiredTaskDoesNotCreateCard() {
        assertFalse(dailyCardQualifies(listOf(task(required = true, done = false))))
    }

    @Test fun unfinishedOptionalTaskBlocksCard() {
        assertFalse(dailyCardQualifies(listOf(task(required = true, done = true), task(required = false, done = false))))
    }

    @Test fun singleCompletedTaskCreatesCard() {
        assertTrue(dailyCardQualifies(listOf(task(required = true, done = true))))
    }

    @Test fun mixedDayIsReevaluatedAfterUndo() {
        val required = task(required = true, done = true)
        val optional = task(required = false, done = true)
        assertTrue(dailyCardQualifies(listOf(required, optional)))
        assertFalse(dailyCardQualifies(listOf(required, optional.copy(done = false))))
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
