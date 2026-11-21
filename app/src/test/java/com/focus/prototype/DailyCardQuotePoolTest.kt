package com.focus.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONArray
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class DailyCardQuotePoolTest {

    @Test
    fun selectablePoolExcludesDisabledQuotesButKeepsHistoricalLookup() {
        assertTrue(DailyExcerpts.generationPool.isNotEmpty())
        assertTrue(DailyExcerpts.generationPool.all { DailyExcerpts.isSelectable(it.id) })
        assertFalse(DailyExcerpts.isSelectable("jgb272"))
        assertFalse(DailyExcerpts.isSelectable("lacan-desire"))
        assertFalse(DailyExcerpts.isSelectable("zhuang-water"))
        assertNotNull(DailyExcerpts.find("jgb272"))
    }

    @Test
    fun saveRejectsNewDisabledQuoteAndAllowsAnUnchangedHistoricalSnapshot() {
        val db = newDatabase("daily-card-quote-save")
        try {
            val date = db.day()
            addCompletedRequiredTask(db, date)
            val generated = DailyCards.reconcile(db, date)!!
            val disabled = DailyExcerpts.entries.first { !DailyExcerpts.isSelectable(it.id) }

            assertThrows(IllegalArgumentException::class.java) {
                DailyCards.save(db, generated.copy(excerptId = disabled.id, quote = disabled.chinese))
            }
            assertEquals(generated, DailyCards.load(db, date))

            val historical = generated.copy(excerptId = disabled.id, quote = disabled.chinese)
            db.setSetting("daily_card_$date", encode(historical))
            val loaded = DailyCards.load(db, date)!!
            assertEquals(disabled.id, loaded.excerptId)
            assertEquals(disabled.chinese, loaded.quote)

            DailyCards.save(db, loaded)
            val saved = DailyCards.load(db, date)!!
            assertEquals(disabled.id, saved.excerptId)
            assertEquals(disabled.chinese, saved.quote)
        } finally {
            db.close()
        }
    }

    @Test
    fun startupReconciliationDoesNotRewriteHistoricalCardSnapshots() {
        val db = newDatabase("daily-card-history-read")
        try {
            val disabled = DailyExcerpts.find("zhuang-water")!!
            val historical = DailyCardRecord(
                date = "2026-11-19",
                quote = disabled.chinese,
                completed = 7,
                total = 9,
                focusMinutes = 42,
                taskTitles = listOf("历史快照任务"),
                unlockedAt = 1_800_000_000_000L,
                valid = true,
                excerptId = disabled.id,
                edition = CardEdition.Legacy,
                saved = true,
                revealSeen = true,
                selectedTaskIds = listOf("historical-task-id"),
                note = "保留已有记录",
            )
            db.setSetting("daily_card_${historical.date}", encode(historical))

            assertEquals(historical, DailyCards.load(db, historical.date))
            DailyCards.reconcileAll(db, "2026-11-20")

            assertEquals(historical, DailyCards.load(db, historical.date))
            assertTrue(DailyCards.read(db).contains(historical))
        } finally {
            db.close()
        }
    }

    private fun newDatabase(prefix: String): Database = Database(
        RuntimeEnvironment.getApplication(),
        "$prefix-${System.nanoTime()}.db",
    )

    private fun addCompletedRequiredTask(db: Database, date: String) {
        db.addTask(date, "模拟完成任务", true, 0, "行动型", "完成", 0, 0, "once")
        db.complete(db.tasks(date).single())
    }

    private fun encode(record: DailyCardRecord): String = JSONObject()
        .put("version", 4)
        .put("date", record.date)
        .put("quote", record.quote)
        .put("completed", record.completed)
        .put("total", record.total)
        .put("focusMinutes", record.focusMinutes)
        .put("taskTitles", JSONArray(record.taskTitles))
        .put("unlockedAt", record.unlockedAt)
        .put("valid", record.valid)
        .put("excerptId", record.excerptId)
        .put("edition", record.edition.key)
        .put("saved", record.saved)
        .put("revealSeen", record.revealSeen)
        .put("selectedTaskIds", JSONArray(record.selectedTaskIds))
        .put("note", record.note)
        .put("visual", record.visual)
        .put("photoId", record.photoId)
        .toString()
}
