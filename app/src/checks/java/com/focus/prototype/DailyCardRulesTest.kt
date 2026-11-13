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
