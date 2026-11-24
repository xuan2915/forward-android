package com.focus.prototype

import android.content.ContentValues
import android.content.Context
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MonthlyChallengeEngineTest {

    private lateinit var context: Context
    private lateinit var db: Database
    private lateinit var engine: MonthlyChallengeEngine
    private val zone = ZoneId.of("UTC")
    private val cycle = "2026-11"
    private val offeredAt = ZonedDateTime.of(2026, 11, 3, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val testXp = mapOf(
        ChallengeDifficulty.ADVANCED to 7,
        ChallengeDifficulty.BREAKTHROUGH to 11,
        ChallengeDifficulty.LIMIT to 13,
    )
    private val testCatalog by lazy {
        ChallengeCatalog.TEMPLATES.mapValues { (_, template) ->
            template.copy(
                publishedStatus = true,
                milestoneDefinitions = template.milestoneDefinitions.mapIndexed { index, milestone ->
                    milestone.copy(progressWeight = listOf(10, 20, 30, 40)[index])
                },
            )
        }
    }
    private val testPolicy by lazy {
        MonthlyChallengePolicy(
            enabledTemplateIds = testCatalog.keys,
            approvedXpByDifficulty = testXp,
            evidenceGraceMillis = MonthlyChallengePolicy.MAX_EVIDENCE_GRACE_MILLIS,
        )
    }

    @org.junit.Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        listOf("test_monthly_challenge.db", "test_monthly_challenge_restore.db", "test_monthly_challenge_legacy.db", "test_monthly_challenge_upgrade.db")
            .forEach { context.getDatabasePath(it).delete() }
        db = Database(context, "test_monthly_challenge.db")
        engine = MonthlyChallengeEngine(db, testPolicy, testCatalog)
    }

    @Test
    fun productionCatalogStaysDisabledUntilWeightsEvidenceAndXpAreApproved() {
        ChallengeCatalog.validateCatalog()
        assertEquals(4, ChallengeCatalog.TEMPLATES.size)
        ChallengeCatalog.TEMPLATES.values.forEach { template ->
            assertFalse(template.publishedStatus)
            assertTrue(template.milestoneDefinitions.all { it.progressWeight == null })
            assertTrue(template.difficultyConfigs.values.all { !it.toJson().has("suggestedXp") })
        }
        assertTrue(ChallengeCatalog.TEMPLATES.getValue(ChallengeCatalog.TEMPLATE_BEHAVIOR)
            .difficultyConfigs.values.all { it.recommendedHoursMin == null && it.recommendedHoursMax == null })

        val productionEngine = db.monthlyChallengeEngine()
        assertNull(productionEngine.getOrGenerateMonthlyOffer(cycle, offeredAt, zone))
        assertNull(productionEngine.selectRecommendation(cycle))
        assertNull(productionEngine.findInstanceByCycle(cycle))
    }

    @Test
    fun offersAreUniqueReplacementIsOneTimeAndSkipSurvivesRepeatedReads() {
        val offer = newOffer()
        assertEquals(ChallengeStatus.OFFERED, offer.status)
        assertFalse(offer.replacementUsed)
        assertEquals(offer.instanceId, newOffer(offeredAt + 1_000).instanceId)

        val replaced = engine.replaceOffer(offer.instanceId, offeredAt + 2_000)
        assertEquals(ChallengeStatus.REPLACED, replaced.status)
        assertTrue(replaced.replacementUsed)
        assertTrue(replaced.templateId != offer.templateId)
        expectFailure { engine.replaceOffer(replaced.instanceId, offeredAt + 3_000) }
        assertEquals(replaced.templateId, engine.getInstance(offer.instanceId)!!.templateId)

        val skipped = engine.skipOffer(replaced.instanceId, "本月跳过", offeredAt + 4_000)
        assertEquals(ChallengeStatus.SKIPPED, skipped.status)
        assertEquals(ChallengeStatus.SKIPPED, newOffer(offeredAt + 5_000).status)
        assertEquals(1, engine.queryEvents(offer.instanceId).count { it.eventType == ChallengeEventType.CHALLENGE_REPLACED })
    }

    @Test
    fun acceptanceBoundaryIsInclusiveAndExpiredOffersCannotBeReopened() {
        val decemberCycle = "2026-12"
        val decemberOfferAt = ZonedDateTime.of(2026, 12, 3, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val decemberOffer = engine.getOrGenerateMonthlyOffer(decemberCycle, decemberOfferAt, zone)!!
        val afterWindow = engine.computeAcceptWindowEnd(decemberCycle, zone) + 1
        expectFailure { engine.acceptChallenge(decemberOffer.instanceId, ChallengeDifficulty.ADVANCED, "", "", afterWindow) }
        expectFailure { engine.replaceOffer(decemberOffer.instanceId, afterWindow) }
        expectFailure { engine.skipOffer(decemberOffer.instanceId, nowMillis = afterWindow) }
        assertEquals(ChallengeStatus.EXPIRED, engine.getOrGenerateMonthlyOffer(decemberCycle, afterWindow, zone)!!.status)
        assertEquals(ChallengeStatus.EXPIRED, engine.getOrGenerateMonthlyOffer(decemberCycle, decemberOfferAt, zone)!!.status)

        val offer = newOffer()
        val acceptEnd = engine.computeAcceptWindowEnd(cycle, zone)
        val accepted = engine.acceptChallenge(offer.instanceId, ChallengeDifficulty.ADVANCED, "基准", "愿望", acceptEnd)
        assertEquals(acceptEnd, accepted.acceptedAt)
        expectFailure { engine.acceptChallenge(offer.instanceId, ChallengeDifficulty.LIMIT, "", "", acceptEnd) }
    }

    @Test
    fun acceptanceFreezesTemplateDifficultyRewardAndWeightedMilestones() {
        val offer = newOffer()
        val accepted = engine.acceptChallenge(offer.instanceId, ChallengeDifficulty.BREAKTHROUGH, "{\"score\":4}", "一本书", offeredAt + 1_000)
        val snapshot = JSONObject(accepted.templateSnapshotJson)
        assertEquals(offer.templateId, snapshot.getString("templateId"))
        assertEquals(ChallengeDifficulty.BREAKTHROUGH, accepted.difficulty)
        assertEquals(11, JSONObject(accepted.rewardSnapshotJson).getInt("approvedXp"))
        assertEquals(100, engine.queryMilestones(accepted.instanceId).sumOf { it.progressWeight })
        assertTrue(engine.queryEvents(accepted.instanceId).any { it.eventType == ChallengeEventType.CHALLENGE_ACCEPTED })
        assertTrue(accepted.evidenceDeadline - accepted.deadline in 0..MonthlyChallengePolicy.MAX_EVIDENCE_GRACE_MILLIS)
    }

    @Test
    fun evidenceRequiresCorrectMilestoneAndPreDeadlineActionEvenDuringGrace() {
        val active = accept()
        val (first, second) = engine.queryMilestones(active.instanceId)
        expectFailure {
            engine.verifyMilestone(active.instanceId, first.milestoneId, true, "无证据", offeredAt + 2_000)
        }
        expectFailure {
            engine.submitEvidence(
                active.instanceId, first.milestoneId, EvidenceType.TEXT, text = "截止后发生的行动",
                nowMillis = active.deadline + 2_000, actionOccurredAt = active.deadline + 1_000,
            )
        }
        expectFailure {
            engine.submitEvidence(active.instanceId, "not_belonging_milestone", EvidenceType.TEXT, text = "不存在的里程碑", nowMillis = offeredAt + 2_000)
        }

        val actionAt = active.deadline - 10_000
        val uploadedAt = active.deadline + 24 * 60 * 60 * 1000L
        val evidence = engine.submitEvidence(
            active.instanceId,
            first.milestoneId,
            EvidenceType.TEXT,
            text = "截止前完成的基准记录",
            nowMillis = uploadedAt,
            actionOccurredAt = actionAt,
        )
        assertEquals(EvidenceStatus.SUBMITTED, evidence.verificationStatus)
        db.writableDatabase.update(
            MonthlyChallengeEngine.TABLE_EVIDENCES,
            ContentValues().apply { put("text_content", "tampered after hashing") },
            "evidence_id = ?",
            arrayOf(evidence.evidenceId),
        )
        expectFailure { engine.verifyMilestone(active.instanceId, first.milestoneId, true, "不应通过", uploadedAt + 1) }
        engine.submitEvidence(
            active.instanceId,
            first.milestoneId,
            EvidenceType.TEXT,
            text = "补交了未被改动的记录",
            nowMillis = uploadedAt + 2,
            actionOccurredAt = actionAt,
        )
        expectFailure { engine.verifyMilestone(active.instanceId, first.milestoneId, true, "", uploadedAt + 1) }
        val verified = engine.verifyMilestone(active.instanceId, first.milestoneId, true, "按冻结的基准检查项复核", uploadedAt + 3)
        assertEquals(MilestoneStatus.COMPLETED, verified.status)
        assertEquals("按冻结的基准检查项复核", verified.verificationNotes)
        assertEquals(EvidenceStatus.VERIFIED, engine.queryEvidences(active.instanceId).last().verificationStatus)
        assertEquals(first.progressWeight, engine.computeProgress(active.instanceId).progressPercentage)
        expectFailure { engine.verifyMilestone(active.instanceId, first.milestoneId, true, "重复确认", uploadedAt + 4) }
        expectFailure {
            engine.submitEvidence(active.instanceId, second.milestoneId, EvidenceType.TEXT, text = "超出补证窗口", nowMillis = active.evidenceDeadline + 1, actionOccurredAt = actionAt)
        }
    }

    @Test
    fun pauseResumeAndAbandonRecordStateWithoutChangingOtherLedgers() {
        val active = accept()
        val paused = engine.pauseChallenge(active.instanceId, "休息", offeredAt + 2_000)
        assertEquals(ChallengeStatus.PAUSED, paused.status)
        assertEquals(ChallengeStatus.ACTIVE, engine.resumeChallenge(paused.instanceId, offeredAt + 3_000).status)
        assertEquals(ChallengeStatus.ABANDONED, engine.abandonChallenge(active.instanceId, "退出", offeredAt + 4_000).status)
        assertEquals(0, db.xp())
        assertEquals(0, db.readableDatabase.rawQuery("SELECT COUNT(*) FROM ledger", null).use { it.moveToFirst(); it.getInt(0) })
        assertEquals(1, engine.queryEvents(active.instanceId).count { it.eventType == ChallengeEventType.CHALLENGE_ABANDONED })
        expectFailure { engine.resumeChallenge(active.instanceId, offeredAt + 5_000) }
    }

    @Test
    fun settlementAwardsConfiguredXpOnceAndAchievementReadsTheCompletedChallenge() {
        val active = accept(ChallengeDifficulty.BREAKTHROUGH)
        completeAll(active)
        val beforeXp = db.xp()
        val settled = engine.settleMonthlyChallenge(active.instanceId, active.deadline + 60 * 60 * 1000L)
        assertEquals(ChallengeStatus.COMPLETED, settled.status)
        assertEquals(11, db.xp() - beforeXp)
        assertEquals(11, engine.queryReward(active.instanceId)!!.rewardValue)
        assertEquals(0, db.readableDatabase.rawQuery("SELECT COUNT(*) FROM ledger", null).use { it.moveToFirst(); it.getInt(0) })
        assertTrue(db.readableDatabase.rawQuery(
            "SELECT 1 FROM achievement_unlocks WHERE achievement_id = ? AND status = 'UNLOCKED'",
            arrayOf("achievement.challenge.monthly.not_just_trying"),
        ).use { it.moveToFirst() })

        assertEquals(ChallengeStatus.COMPLETED, engine.settleMonthlyChallenge(active.instanceId, active.deadline + 2 * 60 * 60 * 1000L).status)
        assertEquals(11, db.xp() - beforeXp)
        assertEquals(1, engine.queryEvents(active.instanceId).count { it.eventType == ChallengeEventType.REWARD_GRANTED })
    }

    @Test
    fun monthEndWaitsForEvidenceWindowAndForegroundAppPathSettlesDueChallenge() {
        val active = accept()
        val milestone = engine.queryMilestones(active.instanceId).first()
        val evidence = engine.submitEvidence(
            active.instanceId, milestone.milestoneId, EvidenceType.TEXT, text = "截止前完成",
            nowMillis = active.deadline + 1_000, actionOccurredAt = active.deadline - 1_000,
        )
        assertEquals(EvidenceStatus.SUBMITTED, evidence.verificationStatus)
        assertEquals(active.status, engine.settleMonthlyChallenge(active.instanceId, active.deadline + 1_000).status)

        val triggerAt = engine.nextSettlementDeadline()
        assertEquals(active.evidenceDeadline + 1, triggerAt)
        MonthlyChallengeAppLifecycle.onForeground(context, db, active.deadline + 1_000)
        assertEquals(ChallengeStatus.ACTIVE, engine.getInstance(active.instanceId)!!.status)

        MonthlyChallengeAppLifecycle.onForeground(context, db, active.evidenceDeadline + 1)
        assertEquals(ChallengeStatus.INCOMPLETE, engine.getInstance(active.instanceId)!!.status)
        assertEquals(0, db.xp())
    }

    @Test
    fun newBackupRoundTripPreservesChallengeReferencesEventsTasksSleepAndXpWithoutEconomicChanges() {
        seedLegacyData(db)
        val active = accept()
        completeAll(active)
        engine.settleMonthlyChallenge(active.instanceId, active.deadline + 1_000)
        val xpBefore = db.xp()
        val ledgerBefore = scalar("SELECT COALESCE(SUM(amount),0) FROM ledger")
        val taskCountBefore = scalar("SELECT COUNT(*) FROM tasks")
        val sleepBefore = db.setting("sleep_record_2026-11-02")
        val backup = Backup.export(context, db)

        val restored = Database(context, "test_monthly_challenge_restore.db")
        Backup.restore(context, restored, backup)
        val restoredEngine = restored.monthlyChallengeEngine()
        val restoredInstance = restoredEngine.getInstance(active.instanceId)!!
        assertEquals(ChallengeStatus.COMPLETED, restoredInstance.status)
        assertEquals(4, restoredEngine.queryMilestones(active.instanceId).size)
        assertEquals(4, restoredEngine.queryEvidences(active.instanceId).size)
        assertEquals(engine.queryEvents(active.instanceId).size, restoredEngine.queryEvents(active.instanceId).size)
        assertEquals(xpBefore, restored.xp())
        assertEquals(ledgerBefore, restored.readableDatabase.rawQuery("SELECT COALESCE(SUM(amount),0) FROM ledger", null).use { it.moveToFirst(); it.getInt(0) })
        assertEquals(taskCountBefore, restored.readableDatabase.rawQuery("SELECT COUNT(*) FROM tasks", null).use { it.moveToFirst(); it.getInt(0) })
        assertEquals(sleepBefore, restored.setting("sleep_record_2026-11-02"))
        assertEquals(xpBefore, restoredEngine.settleMonthlyChallenge(active.instanceId, active.evidenceDeadline + 1).let { restored.xp() })
        restored.close()
    }

    @Test
    fun legacyBackupWithoutChallengeTablesRestoresWithEmptyChallengeSchema() {
        seedLegacyData(db)
        val backup = Backup.export(context, db)
        val oldPayload = JSONObject(backup)
        val payload = JSONObject(oldPayload.getString("payload"))
        val data = payload.getJSONObject("data")
        listOf("challenge_instances", "challenge_milestones", "challenge_evidences", "challenge_events", "challenge_rewards").forEach { data.remove(it) }
        val encoded = payload.toString()
        oldPayload.put("payload", encoded)
        oldPayload.put("sha256", sha256(encoded.toByteArray(Charsets.UTF_8)))

        val restored = Database(context, "test_monthly_challenge_legacy.db")
        Backup.restore(context, restored, oldPayload.toString())
        val engine = restored.monthlyChallengeEngine()
        assertTrue(engine.queryHistory().isEmpty())
        assertEquals(1, restored.readableDatabase.rawQuery("SELECT COUNT(*) FROM tasks", null).use { it.moveToFirst(); it.getInt(0) })
        assertEquals("sleep-json-preserved", restored.setting("sleep_record_2026-11-02"))
        restored.close()
    }

    @Test
    fun schemaUpgradeAddsChallengeTablesAndColumnsWithoutChangingUserHistory() {
        val migrationDb = Database(context, "test_monthly_challenge_upgrade.db")
        insertTask(migrationDb)
        migrationDb.setSetting("sleep_record_2026-11-02", "sleep-json-preserved")
        XpLedger.award(migrationDb.writableDatabase, XpLedger.SOURCE_TASK, "restore_task", 100, "2026-11-02", offeredAt)
        listOf(
            MonthlyChallengeEngine.TABLE_REWARDS,
            MonthlyChallengeEngine.TABLE_EVIDENCES,
            MonthlyChallengeEngine.TABLE_EVENTS,
            MonthlyChallengeEngine.TABLE_MILESTONES,
            MonthlyChallengeEngine.TABLE_INSTANCES,
        ).forEach { migrationDb.writableDatabase.execSQL("DROP TABLE $it") }
        migrationDb.writableDatabase.version = 7
        migrationDb.close()

        val upgraded = Database(context, "test_monthly_challenge_upgrade.db")
        val tables = upgraded.readableDatabase.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertTrue(tables.containsAll(listOf(
            MonthlyChallengeEngine.TABLE_INSTANCES,
            MonthlyChallengeEngine.TABLE_MILESTONES,
            MonthlyChallengeEngine.TABLE_EVIDENCES,
            MonthlyChallengeEngine.TABLE_EVENTS,
            MonthlyChallengeEngine.TABLE_REWARDS,
        )))
        assertEquals(1, upgraded.readableDatabase.rawQuery("SELECT COUNT(*) FROM tasks", null).use { it.moveToFirst(); it.getInt(0) })
        assertEquals("sleep-json-preserved", upgraded.setting("sleep_record_2026-11-02"))
        assertEquals(100, upgraded.xp())
        assertTrue(upgraded.readableDatabase.rawQuery("PRAGMA table_info(challenge_evidences)", null).use { cursor ->
            var found = false
            while (cursor.moveToNext()) if (cursor.getString(1) == "action_occurred_at") found = true
            found
        })
        upgraded.close()
    }

    private fun newOffer(at: Long = offeredAt): ChallengeInstance =
        engine.getOrGenerateMonthlyOffer(cycle, at, zone) ?: error("Test offer was not generated")

    private fun accept(difficulty: ChallengeDifficulty = ChallengeDifficulty.ADVANCED): ChallengeInstance {
        val offer = newOffer()
        return engine.acceptChallenge(offer.instanceId, difficulty, "{\"baseline\":1}", "test wish", offeredAt + 1_000)
    }

    private fun completeAll(instance: ChallengeInstance) {
        engine.queryMilestones(instance.instanceId).forEachIndexed { index, milestone ->
            val actionAt = instance.acceptedAt + 10_000L + index * 1_000L
            val evidence = engine.submitEvidence(
                instance.instanceId,
                milestone.milestoneId,
                EvidenceType.TEXT,
                text = "milestone evidence $index",
                nowMillis = actionAt + 1_000L,
                actionOccurredAt = actionAt,
            )
            assertEquals(EvidenceStatus.SUBMITTED, evidence.verificationStatus)
            engine.verifyMilestone(
                instance.instanceId,
                milestone.milestoneId,
                approved = true,
                notes = "按冻结的验收标准复核第 $index 项",
                nowMillis = actionAt + 2_000L,
            )
        }
    }

    private fun seedLegacyData(database: Database) {
        insertTask(database)
        database.setSetting("sleep_record_2026-11-02", "sleep-json-preserved")
        database.writableDatabase.execSQL(
            "INSERT INTO ledger(id,day,task_id,kind,amount,created,task_day,expire_at,consumption_day) VALUES(?,?,?,?,?,?,?,?,?)",
            arrayOf("legacy_video_1", "2026-11-02", "legacy_task", "video", 5, offeredAt, "2026-11-02", 0, "2026-11-02"),
        )
        XpLedger.award(database.writableDatabase, XpLedger.SOURCE_TASK, "restore_task", 100, "2026-11-02", offeredAt)
    }

    private fun insertTask(database: Database) {
        val values = ContentValues().apply {
            put("id", "restore_task")
            put("day", "2026-11-02")
            put("title", "existing task")
            put("required", 1)
            put("minutes", 10)
            put("mode", "focus")
            put("checklist", "")
            put("reward_minutes", 0)
            put("reward_games", 0)
            put("done", 1)
            put("claimed", 0)
            put("completed_at", offeredAt)
        }
        database.writableDatabase.insertOrThrow("tasks", null, values)
    }

    private fun scalar(sql: String): Int = db.readableDatabase.rawQuery(sql, null).use { it.moveToFirst(); it.getInt(0) }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun expectFailure(block: () -> Unit) {
        var failed = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            failed = true
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertTrue("Expected the operation to be rejected", failed)
    }
}
