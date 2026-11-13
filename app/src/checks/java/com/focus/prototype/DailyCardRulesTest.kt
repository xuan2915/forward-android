package com.focus.prototype

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyCardRulesTest {
    @Test fun automaticCompositionIsStableAndPairsExcerptThemeWithOfflineArtwork() {
        assertTrue(CardEdition.fromKey("sky-meadow-v1") == CardEdition.Sky)
        assertTrue(CardEdition.fromKey("sky-meadow-v1").artwork == R.drawable.daily_rain_harbor)
        assertTrue(CardEdition.fromKey("paper-terrace-v1").artwork == R.drawable.daily_blue_window)
        assertTrue(CardEdition.fromKey("legacy-v1").artwork == R.drawable.daily_blue_window)
        assertTrue(CardEdition.fromKey("") == CardEdition.Legacy)
        val first = dailyCardComposition("2026-11-12")
        assertEquals(first, dailyCardComposition("2026-11-12"))
        assertTrue(first.excerpt in DailyExcerpts.entries)
        assertTrue(first.edition in listOf(CardEdition.Sky, CardEdition.Paper))
    }

    @Test fun catalogHasDistinctIdsAndSourceMetadata() {
        val entries = DailyExcerpts.entries
        assertTrue(entries.size in 15..20)
        assertTrue(entries.map { it.id }.distinct().size == entries.size)
        assertTrue(entries.all { it.original.isNotBlank() && it.chinese.isNotBlank() &&
            it.author.isNotBlank() && it.work.isNotBlank() && it.source.startsWith("https://") &&
            it.themes.isNotEmpty() })
    }

    @Test fun readerAttributionOmitsPageSectionAndTranslationLabels() {
        assertEquals("尼采 · 《快乐的科学》", DailyExcerpts.find("fw270")!!.attribution)
        assertEquals("塞涅卡 · 《论生命之短暂》", DailyExcerpts.find("brev1a")!!.attribution)
        assertEquals("陶渊明 · 《移居》", DailyExcerpts.find("tao-reading")!!.attribution)
        assertTrue(DailyExcerpts.entries.all { excerpt ->
            !excerpt.attribution.contains('§') &&
                !excerpt.attribution.contains("向前意译") &&
                !excerpt.attribution.matches(Regex(".*\\b[IVXLCDM]+\\.\\d+.*"))
        })
    }

    @Test fun emptyDayDoesNotCreateCard() {
        assertFalse(dailyCardQualifies(emptyList()))
    }

    @Test fun completedOptionalOnlyDayDoesNotCreateCardOrCountAsGoal() {
        val optional=listOf(task(required = false, done = true))
        assertFalse(dailyCardQualifies(optional))
        assertEquals(DailyGoalState.NO_REQUIRED_TASKS,dailyGoalState(optional,false,"2026-11-10","2026-11-10"))
    }

    @Test fun everyRequiredTaskMustBeComplete() {
        assertFalse(dailyCardQualifies(listOf(task(required = true, done = true), task(required = true, done = false))))
    }

    @Test fun incompleteRequiredTaskDoesNotCreateCard() {
        assertFalse(dailyCardQualifies(listOf(task(required = true, done = false))))
    }

    @Test fun unfinishedOptionalTaskDoesNotBlockDailyGoalOrCard() {
        val tasks=listOf(task(required = true, done = true), task(required = false, done = false))
        assertTrue(dailyCardQualifies(tasks))
        assertEquals(DailyGoalState.ACHIEVED,dailyGoalState(tasks,false,"2026-11-10","2026-11-10"))
    }

    @Test fun singleCompletedTaskCreatesCard() {
        assertTrue(dailyCardQualifies(listOf(task(required = true, done = true))))
    }

    @Test fun mixedDayIsReevaluatedAfterUndo() {
        val required = task(required = true, done = true)
        val optional = task(required = false, done = true)
        assertTrue(dailyCardQualifies(listOf(required, optional)))
        assertTrue(dailyCardQualifies(listOf(required, optional.copy(done = false))))
    }

    @Test fun optionalCompletionCannotReplaceIncompleteRequiredTask() {
        val tasks=listOf(task(required=true,done=false),task(required=false,done=true))
        assertFalse(dailyCardQualifies(tasks))
        assertEquals(DailyGoalState.IN_PROGRESS,dailyGoalState(tasks,false,"2026-11-10","2026-11-10"))
        assertEquals(DailyGoalState.MISSED,dailyGoalState(tasks,false,"2026-11-10","2026-11-11"))
    }

    @Test fun restAndUnplannedDaysAreNotSuccessOrFailure() {
        assertEquals(DailyGoalState.REST,dailyGoalState(emptyList(),true,"2026-11-10","2026-11-10"))
        assertEquals(DailyGoalState.UNPLANNED,dailyGoalState(emptyList(),false,"2026-11-10","2026-11-10"))
    }

    @Test fun futureDatesStayFutureEvenWhenNoRequiredTasksExist() {
        assertEquals(DailyGoalState.FUTURE,dailyGoalState(emptyList(),false,"2026-11-11","2026-11-10"))
        assertEquals(DailyGoalState.FUTURE,dailyGoalState(listOf(task(required=false,done=false)),false,"2026-11-11","2026-11-10"))
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
