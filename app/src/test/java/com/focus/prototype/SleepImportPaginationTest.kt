package com.focus.prototype

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepImportPaginationTest {
    @Test
    fun readAllSleepRecordPages_followsTokensUntilEndAndKeepsOrder() {
        val requestedTokens = mutableListOf<Long?>()

        val records = readAllSleepRecordPages { token ->
            requestedTokens += token
            when (token) {
                null -> SleepRecordsPage(listOf("first", "second"), 41L)
                41L -> SleepRecordsPage(listOf("third"), 92L)
                92L -> SleepRecordsPage(listOf("fourth"), -1L)
                else -> error("Unexpected page token: $token")
            }
        }

        assertEquals(listOf("first", "second", "third", "fourth"), records)
        assertEquals(listOf(null, 41L, 92L), requestedTokens)
    }

    @Test
    fun readAllSleepRecordPages_keepsReadingWhenAnIntermediatePageIsEmpty() {
        val requestedTokens = mutableListOf<Long?>()

        val records = readAllSleepRecordPages { token ->
            requestedTokens += token
            when (token) {
                null -> SleepRecordsPage(emptyList(), 7L)
                7L -> SleepRecordsPage(listOf("late-arriving-session"), -1L)
                else -> error("Unexpected page token: $token")
            }
        }

        assertEquals(listOf("late-arriving-session"), records)
        assertEquals(listOf(null, 7L), requestedTokens)
    }
}
