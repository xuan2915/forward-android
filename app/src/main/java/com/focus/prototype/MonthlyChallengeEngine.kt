package com.focus.prototype

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.security.MessageDigest
import java.time.LocalDate
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class ChallengeInstance(
    val instanceId: String,
    val cycleId: String, // e.g. "2026-11"
    val templateId: String,
    val templateVersion: Int,
    val templateSnapshotJson: String,
    val difficulty: ChallengeDifficulty,
    val status: ChallengeStatus,
    val recommendedAt: Long,
    val acceptedAt: Long,
    val startedAt: Long,
    val deadline: Long,
    val evidenceDeadline: Long,
    val timezoneSnapshot: String,
    val replacementUsed: Boolean,
    val baselineDataJson: String,
    val wishData: String,
    val rewardSnapshotJson: String,
    val finalResultJson: String,
    val completedAt: Long,
) {
    val isTerminal: Boolean
        get() = status in listOf(
            ChallengeStatus.COMPLETED,
            ChallengeStatus.PARTIAL,
            ChallengeStatus.INCOMPLETE,
            ChallengeStatus.ABANDONED,
            ChallengeStatus.SKIPPED,
            ChallengeStatus.EXPIRED,
        )
}

data class ChallengeMilestoneRecord(
    val milestoneId: String,
    val instanceId: String,
    val templateMilestoneId: String,
    val title: String,
    val description: String,
    val sequence: Int,
    val progressWeight: Int,
    val required: Boolean,
    val status: MilestoneStatus,
    val completedAt: Long,
    val verificationType: VerificationType,
    val verificationNotes: String,
)

data class ChallengeEvidenceRecord(
    val evidenceId: String,
    val instanceId: String,
    val milestoneId: String,
    val evidenceType: EvidenceType,
    val localFileReference: String,
    val textContent: String,
    val externalUri: String,
    val createdAt: Long,
    val actionOccurredAt: Long,
    val checksum: String,
    val verificationStatus: EvidenceStatus,
)

data class ChallengeEventRecord(
    val eventId: String,
    val instanceId: String,
    val eventType: ChallengeEventType,
    val eventTimestamp: Long,
    val eventPayload: String,
    val idempotencyKey: String,
)

data class ChallengeRewardRecord(
    val rewardId: String,
    val instanceId: String,
    val rewardType: String,
    val rewardValue: Int,
    val ledgerReference: String,
    val grantStatus: String,
    val grantedAt: Long,
    val idempotencyKey: String,
)

data class ChallengeProgress(
    val instance: ChallengeInstance,
    val milestones: List<ChallengeMilestoneRecord>,
    val progressPercentage: Int,
    val completedMilestonesCount: Int,
    val totalMilestonesCount: Int,
    val evidences: List<ChallengeEvidenceRecord>,
)

/** Product choices remain opt-in until their values and evidence rules are approved. */
data class MonthlyChallengePolicy(
    val enabledTemplateIds: Set<String> = emptySet(),
    val approvedXpByDifficulty: Map<ChallengeDifficulty, Int> = emptyMap(),
    val evidenceGraceMillis: Long? = null,
) {
    fun isEnabled(template: ChallengeTemplate): Boolean =
        template.publishedStatus && template.templateId in enabledTemplateIds &&
            evidenceGraceMillis != null && evidenceGraceMillis in 0..MAX_EVIDENCE_GRACE_MILLIS &&
            ChallengeDifficulty.entries.all { (approvedXpByDifficulty[it] ?: 0) > 0 } &&
            template.milestoneDefinitions.all { (it.progressWeight ?: 0) > 0 } &&
            template.milestoneDefinitions.sumOf { it.progressWeight ?: 0 } == 100 &&
            template.milestoneDefinitions.all { it.verificationType == VerificationType.MANUAL }

    companion object {
        const val MAX_EVIDENCE_GRACE_MILLIS: Long = 48L * 60L * 60L * 1000L
        val DISABLED = MonthlyChallengePolicy()
    }
}

class MonthlyChallengeEngine(
    private val dbHelper: Database,
    private val policy: MonthlyChallengePolicy = MonthlyChallengePolicy.DISABLED,
    private val catalog: Map<String, ChallengeTemplate> = ChallengeCatalog.TEMPLATES,
) {

    companion object {
        const val TABLE_INSTANCES = "challenge_instances"
        const val TABLE_MILESTONES = "challenge_milestones"
        const val TABLE_EVIDENCES = "challenge_evidences"
        const val TABLE_EVENTS = "challenge_events"
        const val TABLE_REWARDS = "challenge_rewards"

        fun initSchema(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_INSTANCES (
                    instance_id TEXT PRIMARY KEY,
                    cycle_id TEXT NOT NULL,
                    template_id TEXT NOT NULL,
                    template_version INTEGER NOT NULL,
                    template_snapshot TEXT NOT NULL,
                    difficulty TEXT NOT NULL,
                    status TEXT NOT NULL,
                    recommended_at INTEGER NOT NULL,
                    accepted_at INTEGER NOT NULL DEFAULT 0,
                    started_at INTEGER NOT NULL DEFAULT 0,
                    deadline INTEGER NOT NULL,
                    evidence_deadline INTEGER NOT NULL DEFAULT 0,
                    timezone_snapshot TEXT NOT NULL,
                    replacement_used INTEGER NOT NULL DEFAULT 0,
                    baseline_data TEXT NOT NULL DEFAULT '',
                    wish_data TEXT NOT NULL DEFAULT '',
                    reward_snapshot TEXT NOT NULL DEFAULT '',
                    final_result TEXT NOT NULL DEFAULT '',
                    completed_at INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_challenge_instances_cycle ON $TABLE_INSTANCES(cycle_id)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS ux_challenge_instances_one_per_cycle ON $TABLE_INSTANCES(cycle_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_challenge_instances_status ON $TABLE_INSTANCES(status)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_MILESTONES (
                    milestone_id TEXT PRIMARY KEY,
                    instance_id TEXT NOT NULL,
                    template_milestone_id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT NOT NULL,
                    sequence INTEGER NOT NULL,
                    progress_weight INTEGER NOT NULL,
                    required INTEGER NOT NULL DEFAULT 1,
                    status TEXT NOT NULL DEFAULT 'PENDING',
                    completed_at INTEGER NOT NULL DEFAULT 0,
                    verification_type TEXT NOT NULL DEFAULT 'MANUAL',
                    verification_notes TEXT NOT NULL DEFAULT ''
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_challenge_milestones_inst ON $TABLE_MILESTONES(instance_id)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_EVIDENCES (
                    evidence_id TEXT PRIMARY KEY,
                    instance_id TEXT NOT NULL,
                    milestone_id TEXT NOT NULL,
                    evidence_type TEXT NOT NULL,
                    local_file_reference TEXT NOT NULL DEFAULT '',
                    text_content TEXT NOT NULL DEFAULT '',
                    external_uri TEXT NOT NULL DEFAULT '',
                    created_at INTEGER NOT NULL,
                    action_occurred_at INTEGER NOT NULL DEFAULT 0,
                    checksum TEXT NOT NULL DEFAULT '',
                    verification_status TEXT NOT NULL DEFAULT 'SUBMITTED'
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_challenge_evidences_inst ON $TABLE_EVIDENCES(instance_id)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_EVENTS (
                    event_id TEXT PRIMARY KEY,
                    instance_id TEXT NOT NULL,
                    event_type TEXT NOT NULL,
                    event_timestamp INTEGER NOT NULL,
                    event_payload TEXT NOT NULL DEFAULT '',
                    idempotency_key TEXT UNIQUE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_challenge_events_inst ON $TABLE_EVENTS(instance_id)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_REWARDS (
                    reward_id TEXT PRIMARY KEY,
                    instance_id TEXT NOT NULL,
                    reward_type TEXT NOT NULL,
                    reward_value INTEGER NOT NULL,
                    ledger_reference TEXT NOT NULL DEFAULT '',
                    grant_status TEXT NOT NULL DEFAULT 'GRANTED',
                    granted_at INTEGER NOT NULL,
                    idempotency_key TEXT UNIQUE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_challenge_rewards_inst ON $TABLE_REWARDS(instance_id)")

            ensureColumn(db, TABLE_INSTANCES, "evidence_deadline", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(db, TABLE_MILESTONES, "verification_notes", "TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, TABLE_EVIDENCES, "action_occurred_at", "INTEGER NOT NULL DEFAULT 0")
        }

        private fun ensureColumn(db: SQLiteDatabase, table: String, name: String, declaration: String) {
            val columns = mutableSetOf<String>()
            db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
                while (cursor.moveToNext()) columns.add(cursor.getString(1))
            }
            if (name !in columns) db.execSQL("ALTER TABLE $table ADD COLUMN $name $declaration")
        }

        private fun hashString(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    private val readableDb: SQLiteDatabase get() = dbHelper.readableDatabase
    private val writableDb: SQLiteDatabase get() = dbHelper.writableDatabase

    private fun requireWithinAcceptanceWindow(instance: ChallengeInstance, nowMillis: Long) {
        val zone = ZoneId.of(instance.timezoneSnapshot)
        require(nowMillis >= instance.recommendedAt && nowMillis <= computeAcceptWindowEnd(instance.cycleId, zone)) {
            "Challenge is outside its frozen acceptance window"
        }
    }

    private fun evidenceChecksum(evidence: ChallengeEvidenceRecord): String = hashString(
        listOf(
            evidence.instanceId,
            evidence.milestoneId,
            evidence.evidenceType.name,
            evidence.textContent,
            evidence.localFileReference,
            evidence.externalUri,
            evidence.createdAt.toString(),
            evidence.actionOccurredAt.toString(),
        ).joinToString("\u001f"),
    )

    private inline fun <T> inTransaction(db: SQLiteDatabase, block: () -> T): T {
        val ownsTransaction = !db.inTransaction()
        if (ownsTransaction) db.beginTransaction()
        try {
            val result = block()
            if (ownsTransaction) db.setTransactionSuccessful()
            return result
        } finally {
            if (ownsTransaction) db.endTransaction()
        }
    }

    /**
     * Compute default cycle ID and deadline for a given timestamp.
     */
    fun computeCycleId(
        timestampMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String = Instant.ofEpochMilli(timestampMillis).atZone(zoneId).toLocalDate()
        .format(DateTimeFormatter.ofPattern("yyyy-MM"))

    fun computeAcceptWindowEnd(cycleId: String, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val ym = YearMonth.parse(cycleId)
        val day7 = ym.atDay(7).atTime(23, 59, 59, 999_000_000)
        return day7.atZone(zoneId).toInstant().toEpochMilli()
    }

    fun computeMonthDeadline(cycleId: String, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val ym = YearMonth.parse(cycleId)
        val endOfMonth = ym.atEndOfMonth().atTime(23, 59, 59, 999_000_000)
        return endOfMonth.atZone(zoneId).toInstant().toEpochMilli()
    }

    /**
     * Get existing challenge instance for the month, or generate a new OFFERED instance.
     */
    @Synchronized
    fun getOrGenerateMonthlyOffer(
        cycleId: String = computeCycleId(),
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ChallengeInstance? {
        initSchema(writableDb)
        val existing = findInstanceByCycle(cycleId)
        if (existing != null) {
            // Check if offered/replaced and passed acceptance window
            val acceptDeadline = computeAcceptWindowEnd(cycleId, ZoneId.of(existing.timezoneSnapshot))
            if (existing.status in listOf(ChallengeStatus.OFFERED, ChallengeStatus.REPLACED) && nowMillis > acceptDeadline) {
                return expireOffer(existing.instanceId, nowMillis)
            }
            return existing
        }

        require(computeCycleId(nowMillis, zoneId) == cycleId) { "Cycle $cycleId does not match the local month at the requested time" }
        if (catalog.values.none { policy.isEnabled(it) }) return null
        if (getActiveInstance() != null) return null

        // If offer window already passed and no instance exists, return null (do not offer after 7th)
        val acceptDeadline = computeAcceptWindowEnd(cycleId, zoneId)
        if (nowMillis > acceptDeadline) {
            return null
        }

        // Select recommended template
        val recommendedTemplate = selectRecommendation(cycleId, excludeTemplateIds = emptySet())
            ?: return null

        val deadline = computeMonthDeadline(cycleId, zoneId)
        val evidenceDeadline = Math.addExact(deadline, requireNotNull(policy.evidenceGraceMillis))
        val instanceId = "ch_inst_${cycleId}_${recommendedTemplate.templateId}"
        val instance = ChallengeInstance(
            instanceId = instanceId,
            cycleId = cycleId,
            templateId = recommendedTemplate.templateId,
            templateVersion = recommendedTemplate.version,
            templateSnapshotJson = recommendedTemplate.toJson().toString(),
            difficulty = ChallengeDifficulty.ADVANCED, // Default selection
            status = ChallengeStatus.OFFERED,
            recommendedAt = nowMillis,
            acceptedAt = 0L,
            startedAt = 0L,
            deadline = deadline,
            evidenceDeadline = evidenceDeadline,
            timezoneSnapshot = zoneId.id,
            replacementUsed = false,
            baselineDataJson = "",
            wishData = "",
            rewardSnapshotJson = "",
            finalResultJson = "",
            completedAt = 0L,
        )

        val db = writableDb
        inTransaction(db) {
            if (findInstanceByCycle(cycleId) == null) {
                insertInstance(instance, db)
                logEvent(instanceId, ChallengeEventType.CHALLENGE_ISSUED, nowMillis, "Offered ${recommendedTemplate.title}", db)
            }
        }
        return findInstanceByCycle(cycleId)
    }

    /**
     * Swap the offered challenge. Strictly allowed only once per monthly cycle.
     */
    @Synchronized
    fun replaceOffer(
        instanceId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): ChallengeInstance {
        val instance = getInstance(instanceId) ?: error("Challenge instance $instanceId not found")
        requireWithinAcceptanceWindow(instance, nowMillis)
        require(!instance.replacementUsed) { "Replacement opportunity has already been used for this cycle" }
        require(instance.status == ChallengeStatus.OFFERED) { "Cannot replace challenge in status ${instance.status}" }

        val replacementTemplate = selectRecommendation(instance.cycleId, excludeTemplateIds = setOf(instance.templateId))
            ?: error("No alternative challenge template available for replacement")

        val updated = instance.copy(
            templateId = replacementTemplate.templateId,
            templateVersion = replacementTemplate.version,
            templateSnapshotJson = replacementTemplate.toJson().toString(),
            status = ChallengeStatus.REPLACED,
            replacementUsed = true,
        )

        val db = writableDb
        inTransaction(db) {
            require(updateInstance(updated, db, "instance_id = ? AND status = 'OFFERED' AND replacement_used = 0", arrayOf(instanceId)) == 1) {
                "Challenge offer changed before replacement could be saved"
            }
            logEvent(instanceId, ChallengeEventType.CHALLENGE_REPLACED, nowMillis, "Replaced with ${replacementTemplate.title}", db)
        }
        return getInstance(instanceId) ?: error("Challenge instance $instanceId disappeared")
    }

    /**
     * Skip the offered or replaced challenge. No failure recorded, no penalty.
     */
    @Synchronized
    fun skipOffer(
        instanceId: String,
        reason: String = "",
        nowMillis: Long = System.currentTimeMillis(),
    ): ChallengeInstance {
        val instance = getInstance(instanceId) ?: error("Challenge instance $instanceId not found")
        requireWithinAcceptanceWindow(instance, nowMillis)
        require(instance.status in listOf(ChallengeStatus.OFFERED, ChallengeStatus.REPLACED)) {
            "Cannot skip challenge in status ${instance.status}"
        }

        val updated = instance.copy(status = ChallengeStatus.SKIPPED)
        val db = writableDb
        inTransaction(db) {
            require(updateInstance(updated, db, "instance_id = ? AND status IN ('OFFERED','REPLACED')", arrayOf(instanceId)) == 1) {
                "Challenge offer changed before skip could be saved"
            }
            logEvent(instanceId, ChallengeEventType.CHALLENGE_SKIPPED, nowMillis, reason.ifBlank { "User skipped challenge" }, db)
        }
        return getInstance(instanceId) ?: error("Challenge instance $instanceId disappeared")
    }

    /**
     * Accept the challenge. Freezes configuration, difficulty, milestones, baseline and wish data.
     */
    @Synchronized
    fun acceptChallenge(
        instanceId: String,
        difficulty: ChallengeDifficulty,
        baselineData: String,
        wishData: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): ChallengeInstance {
        val instance = getInstance(instanceId) ?: error("Challenge instance $instanceId not found")
        requireWithinAcceptanceWindow(instance, nowMillis)
        require(instance.status in listOf(ChallengeStatus.OFFERED, ChallengeStatus.REPLACED)) {
            "Cannot accept challenge in status ${instance.status}"
        }

        val template = ChallengeTemplate.fromJson(JSONObject(instance.templateSnapshotJson))
        require(policy.isEnabled(template)) { "This challenge template is not enabled for production use" }
        require(nowMillis >= instance.recommendedAt) { "Challenge cannot be accepted before it was offered" }
        require(template.difficultyConfigs.containsKey(difficulty)) { "Difficulty $difficulty not configured for template ${template.templateId}" }
        val approvedXp = policy.approvedXpByDifficulty[difficulty]
            ?: error("Approved XP is not configured for difficulty ${difficulty.name}")
        require(template.milestoneDefinitions.isNotEmpty()) { "Challenge has no milestones" }
        require(template.milestoneDefinitions.all { (it.progressWeight ?: 0) > 0 } && template.milestoneDefinitions.sumOf { it.progressWeight ?: 0 } == 100) {
            "Challenge milestone weights are not approved"
        }

        // Build reward snapshot
        val rewardSnapshot = JSONObject().apply {
            put("approvedXp", approvedXp)
            put("rewardConfigVersion", 1)
            put("collectionAsset", template.collectionAsset)
            put("collectionTitle", template.collectionTitle)
            put("wish", wishData)
        }.toString()

        val updated = instance.copy(
            difficulty = difficulty,
            status = ChallengeStatus.ACTIVE,
            acceptedAt = nowMillis,
            startedAt = nowMillis,
            baselineDataJson = baselineData,
            wishData = wishData,
            rewardSnapshotJson = rewardSnapshot,
        )

        val db = writableDb
        inTransaction(db) {
            val activeElsewhere = db.rawQuery(
                "SELECT instance_id FROM $TABLE_INSTANCES WHERE instance_id <> ? AND status IN ('ACTIVE','PAUSED','REVIEW_PENDING') LIMIT 1",
                arrayOf(instanceId),
            ).use { it.moveToFirst() }
            require(!activeElsewhere) { "Another monthly challenge is still active" }
            require(updateInstance(updated, db, "instance_id = ? AND status IN ('OFFERED','REPLACED')", arrayOf(instanceId)) == 1) {
                "Challenge offer changed before acceptance could be saved"
            }

            // Instantiate milestones from template definitions
            template.milestoneDefinitions.forEach { def ->
                val milestone = ChallengeMilestoneRecord(
                    milestoneId = "${instanceId}_${def.id}",
                    instanceId = instanceId,
                    templateMilestoneId = def.id,
                    title = def.title,
                    description = def.description,
                    sequence = def.sequence,
                    progressWeight = requireNotNull(def.progressWeight),
                    required = def.required,
                    status = MilestoneStatus.PENDING,
                    completedAt = 0L,
                    verificationType = def.verificationType,
                    verificationNotes = "",
                )
                insertMilestone(milestone, db)
            }

            logEvent(instanceId, ChallengeEventType.CHALLENGE_ACCEPTED, nowMillis, "Accepted difficulty ${difficulty.name}", db)
        }

        MonthlyChallengeScheduler.reschedule(dbHelper.appContext, dbHelper)

        return updated
    }

    /**
     * Pause active challenge.
     */
    @Synchronized
    fun pauseChallenge(
        instanceId: String,
        reason: String = "",
        nowMillis: Long = System.currentTimeMillis(),
    ): ChallengeInstance {
        val instance = getInstance(instanceId) ?: error("Challenge instance $instanceId not found")
        require(nowMillis >= instance.acceptedAt) { "Cannot pause before challenge acceptance" }
        require(nowMillis <= instance.deadline) { "Cannot pause after the challenge action deadline" }
        require(reason.isNotBlank()) { "Pausing a challenge requires a reason" }
        require(instance.status == ChallengeStatus.ACTIVE) { "Cannot pause challenge in status ${instance.status}" }

        val updated = instance.copy(status = ChallengeStatus.PAUSED)
        val db = writableDb
        inTransaction(db) {
            require(updateInstance(updated, db, "instance_id = ? AND status = 'ACTIVE'", arrayOf(instanceId)) == 1) { "Challenge changed before pause could be saved" }
            logEvent(instanceId, ChallengeEventType.CHALLENGE_PAUSED, nowMillis, reason, db)
        }
        return getInstance(instanceId) ?: error("Challenge instance $instanceId disappeared")
    }

    /**
     * Resume paused challenge.
     */
    @Synchronized
    fun resumeChallenge(
        instanceId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): ChallengeInstance {
        val instance = getInstance(instanceId) ?: error("Challenge instance $instanceId not found")
        require(nowMillis >= instance.acceptedAt) { "Cannot resume before challenge acceptance" }
        require(nowMillis <= instance.deadline) { "Cannot resume after the challenge action deadline" }
        require(instance.status == ChallengeStatus.PAUSED) { "Cannot resume challenge in status ${instance.status}" }

        val updated = instance.copy(status = ChallengeStatus.ACTIVE)
        val db = writableDb
        inTransaction(db) {
            require(updateInstance(updated, db, "instance_id = ? AND status = 'PAUSED'", arrayOf(instanceId)) == 1) { "Challenge changed before resume could be saved" }
            logEvent(instanceId, ChallengeEventType.CHALLENGE_RESUMED, nowMillis, "Resumed", db)
        }
        return getInstance(instanceId) ?: error("Challenge instance $instanceId disappeared")
    }

    /**
     * Abandon challenge. No deduction of normal tasks, XP, or rewards.
     */
    @Synchronized
    fun abandonChallenge(
        instanceId: String,
        reason: String = "",
        nowMillis: Long = System.currentTimeMillis(),
    ): ChallengeInstance {
        val instance = getInstance(instanceId) ?: error("Challenge instance $instanceId not found")
        require(nowMillis <= instance.evidenceDeadline) { "Challenge evidence window has closed" }
        require(reason.isNotBlank()) { "Abandoning a challenge requires a reason" }
        require(instance.status in listOf(ChallengeStatus.ACTIVE, ChallengeStatus.PAUSED)) {
            "Cannot abandon challenge in status ${instance.status}"
        }

        val updated = instance.copy(status = ChallengeStatus.ABANDONED)
        val db = writableDb
        inTransaction(db) {
            require(updateInstance(updated, db, "instance_id = ? AND status IN ('ACTIVE','PAUSED')", arrayOf(instanceId)) == 1) { "Challenge changed before abandonment could be saved" }
            logEvent(instanceId, ChallengeEventType.CHALLENGE_ABANDONED, nowMillis, reason.ifBlank { "User abandoned challenge" }, db)
        }
        return getInstance(instanceId) ?: error("Challenge instance $instanceId disappeared")
    }

    /**
     * Submit evidence for a milestone.
     */
    @Synchronized
    fun submitEvidence(
        instanceId: String,
        milestoneId: String,
        evidenceType: EvidenceType,
        text: String = "",
        fileRef: String = "",
        uri: String = "",
        nowMillis: Long = System.currentTimeMillis(),
        actionOccurredAt: Long = nowMillis,
    ): ChallengeEvidenceRecord {
        val instance = getInstance(instanceId) ?: error("Challenge instance $instanceId not found")
        require(instance.status in listOf(ChallengeStatus.ACTIVE, ChallengeStatus.PAUSED)) {
            "Cannot submit evidence for challenge in status ${instance.status}"
        }
        require(actionOccurredAt >= instance.acceptedAt && actionOccurredAt <= instance.deadline) {
            "Evidence must describe an action that occurred during the challenge period"
        }
        require(nowMillis >= actionOccurredAt && nowMillis <= instance.evidenceDeadline) {
            "Evidence submission is outside the frozen evidence window"
        }
        if (instance.status == ChallengeStatus.PAUSED) {
            val latestTransition = queryEvents(instanceId).lastOrNull { it.eventType in setOf(ChallengeEventType.CHALLENGE_PAUSED, ChallengeEventType.CHALLENGE_RESUMED) }
            if (latestTransition?.eventType == ChallengeEventType.CHALLENGE_PAUSED) {
                require(actionOccurredAt <= latestTransition.eventTimestamp) { "Actions cannot occur while the challenge is paused" }
            }
        }
        require(text.isNotBlank() || fileRef.isNotBlank() || uri.isNotBlank()) { "Evidence content is required" }
        val milestone = getMilestone(milestoneId) ?: error("Milestone $milestoneId not found")
        require(milestone.instanceId == instanceId) { "Milestone does not belong to instance $instanceId" }
        require(milestone.status == MilestoneStatus.PENDING) { "Evidence can only be added to a pending milestone" }
        val evidence = ChallengeEvidenceRecord(
            evidenceId = "evi_${UUID.randomUUID().toString().replace("-", "").take(16)}",
            instanceId = instanceId,
            milestoneId = milestoneId,
            evidenceType = evidenceType,
            localFileReference = fileRef,
            textContent = text,
            externalUri = uri,
            createdAt = nowMillis,
            actionOccurredAt = actionOccurredAt,
            checksum = "",
            verificationStatus = EvidenceStatus.SUBMITTED,
        )
        val sealedEvidence = evidence.copy(checksum = evidenceChecksum(evidence))

        val db = writableDb
        inTransaction(db) {
            insertEvidence(sealedEvidence, db)
            logEvent(
                instanceId,
                ChallengeEventType.EVIDENCE_SUBMITTED,
                nowMillis,
                "Evidence for $milestoneId",
                db,
                "${instanceId}_EVIDENCE_${sealedEvidence.evidenceId}",
            )
        }
        return sealedEvidence
    }

    /**
     * Verify milestone. If approved, milestone marks COMPLETED.
     */
    @Synchronized
    fun verifyMilestone(
        instanceId: String,
        milestoneId: String,
        approved: Boolean,
        notes: String = "",
        nowMillis: Long = System.currentTimeMillis(),
    ): ChallengeMilestoneRecord {
        val milestone = getMilestone(milestoneId) ?: error("Milestone $milestoneId not found")
        require(milestone.instanceId == instanceId) { "Milestone does not belong to instance $instanceId" }
        val instance = getInstance(instanceId) ?: error("Challenge instance $instanceId not found")
        require(instance.status in listOf(ChallengeStatus.ACTIVE, ChallengeStatus.PAUSED)) {
            "Cannot verify milestone for challenge in status ${instance.status}"
        }
        require(nowMillis >= instance.acceptedAt) { "Cannot verify before challenge acceptance" }
        require(nowMillis <= instance.evidenceDeadline) { "Challenge evidence window has closed" }
        require(milestone.status == MilestoneStatus.PENDING) { "Milestone has already been verified" }
        require(milestone.verificationType == VerificationType.MANUAL) { "This verification type is not implemented" }
        require(notes.isNotBlank()) { "Manual verification requires a recorded reason" }

        val evidence = queryEvidences(instanceId).filter { it.milestoneId == milestoneId }
        val validEvidence = evidence.filter {
            it.verificationStatus == EvidenceStatus.SUBMITTED &&
                it.actionOccurredAt <= instance.deadline &&
                it.createdAt <= instance.evidenceDeadline &&
                it.checksum == evidenceChecksum(it)
        }
        if (approved) require(validEvidence.isNotEmpty()) { "A milestone cannot pass without valid submitted evidence" }

        val newStatus = if (approved) MilestoneStatus.COMPLETED else MilestoneStatus.PENDING
        val completedAt = if (approved) nowMillis else 0L

        val db = writableDb
        inTransaction(db) {
            val cv = ContentValues().apply {
                put("status", newStatus.name)
                put("completed_at", completedAt)
                put("verification_notes", notes.trim())
            }
            require(db.update(TABLE_MILESTONES, cv, "milestone_id = ? AND status = 'PENDING'", arrayOf(milestoneId)) == 1) {
                "Milestone changed before verification could be saved"
            }
            validEvidence.forEach { item ->
                db.update(TABLE_EVIDENCES, ContentValues().apply {
                    put("verification_status", if (approved) EvidenceStatus.VERIFIED.name else EvidenceStatus.REJECTED.name)
                }, "evidence_id = ? AND verification_status = 'SUBMITTED'", arrayOf(item.evidenceId))
            }
            if (approved) logEvent(instanceId, ChallengeEventType.MILESTONE_COMPLETED, nowMillis, "Milestone $milestoneId manually approved", db)
            else logEvent(instanceId, ChallengeEventType.REVIEW_SUBMITTED, nowMillis, "Milestone $milestoneId rejected: ${notes.trim()}", db)
        }

        return getMilestone(milestoneId) ?: error("Milestone $milestoneId disappeared")
    }

    /**
     * Compute current progress percentage and statistics.
     */
    fun computeProgress(instanceId: String): ChallengeProgress {
        val instance = getInstance(instanceId) ?: error("Challenge instance $instanceId not found")
        val milestones = queryMilestones(instanceId)
        val evidences = queryEvidences(instanceId)

        val completedWeight = milestones
            .filter { it.status == MilestoneStatus.COMPLETED }
            .sumOf { it.progressWeight }
            .coerceIn(0, 100)

        val completedCount = milestones.count { it.status == MilestoneStatus.COMPLETED }

        return ChallengeProgress(
            instance = instance,
            milestones = milestones,
            progressPercentage = completedWeight,
            completedMilestonesCount = completedCount,
            totalMilestonesCount = milestones.size,
            evidences = evidences,
        )
    }

    /**
     * Settle monthly challenge at month-end or on review completion.
     * Guaranteed transaction: award XP idempotently via XpLedger, trigger AchievementEngine, 0 economic reward.
     */
    @Synchronized
    fun settleMonthlyChallenge(
        instanceId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): ChallengeInstance {
        val db = writableDb
        var settled: ChallengeInstance? = null
        inTransaction(db) {
            val instance = getInstance(instanceId) ?: error("Challenge instance $instanceId not found")
            if (instance.isTerminal) {
                settled = instance
                return@inTransaction
            }
            require(instance.status in listOf(ChallengeStatus.ACTIVE, ChallengeStatus.PAUSED, ChallengeStatus.REVIEW_PENDING)) {
                "Only an accepted challenge can be settled"
            }

            val milestones = queryMilestones(instanceId)
            val required = milestones.filter { it.required }
            val allRequiredCompleted = required.isNotEmpty() && required.all {
                it.status == MilestoneStatus.COMPLETED &&
                    it.completedAt <= instance.evidenceDeadline &&
                    queryEvidences(instanceId).any { evidence ->
                        evidence.milestoneId == it.milestoneId &&
                            evidence.verificationStatus == EvidenceStatus.VERIFIED &&
                            evidence.actionOccurredAt <= instance.deadline &&
                            evidence.createdAt <= instance.evidenceDeadline &&
                            evidence.checksum == evidenceChecksum(evidence)
                    }
            }
            val anyCompleted = milestones.any { it.status == MilestoneStatus.COMPLETED }
            val targetStatus = when {
                allRequiredCompleted -> ChallengeStatus.COMPLETED
                nowMillis > instance.evidenceDeadline -> if (anyCompleted) ChallengeStatus.PARTIAL else ChallengeStatus.INCOMPLETE
                else -> instance.status
            }
            if (targetStatus == instance.status) {
                settled = instance
                return@inTransaction
            }

            val updated = instance.copy(
                status = targetStatus,
                completedAt = if (targetStatus == ChallengeStatus.COMPLETED) nowMillis else 0L,
                finalResultJson = JSONObject().apply {
                    put("settledAt", nowMillis)
                    put("finalStatus", targetStatus.name)
                    put("allRequiredCompleted", allRequiredCompleted)
                    put("actionDeadline", instance.deadline)
                    put("evidenceDeadline", instance.evidenceDeadline)
                }.toString(),
            )
            require(updateInstance(updated, db, "instance_id = ? AND status IN ('ACTIVE','PAUSED','REVIEW_PENDING')", arrayOf(instanceId)) == 1) {
                "Challenge changed before settlement could be saved"
            }
            logEvent(instanceId, ChallengeEventType.CHALLENGE_SETTLED, nowMillis, "Settled as ${targetStatus.name}", db)

            if (targetStatus == ChallengeStatus.COMPLETED) {
                val rewardJson = JSONObject(instance.rewardSnapshotJson.ifBlank { "{}" })
                val xpAmount = rewardJson.optInt("approvedXp", 0)
                require(xpAmount > 0) { "Approved challenge XP is missing; challenge remains unsettled" }
                val rewardConfigVersion = rewardJson.optInt("rewardConfigVersion", 0)
                require(rewardConfigVersion > 0) { "Approved challenge reward version is missing" }

                val xpKey = "challenge_reward_$instanceId"
                XpLedger.award(
                    db = db,
                    sourceType = XpLedger.SOURCE_CHALLENGE,
                    sourceId = instanceId,
                    amount = xpAmount,
                    day = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.of(instance.timezoneSnapshot)).toLocalDate().toString(),
                    wallNow = nowMillis,
                    idempotencyKey = xpKey,
                    version = rewardConfigVersion,
                    metadata = "difficulty:${instance.difficulty.name},template:${instance.templateId},cycle:${instance.cycleId}",
                )
                val rewardRecord = ChallengeRewardRecord(
                    rewardId = "rew_$instanceId",
                    instanceId = instanceId,
                    rewardType = "XP",
                    rewardValue = xpAmount,
                    ledgerReference = "xp_$xpKey",
                    grantStatus = "GRANTED",
                    grantedAt = nowMillis,
                    idempotencyKey = xpKey,
                )
                insertReward(rewardRecord, db)
                logEvent(instanceId, ChallengeEventType.CHALLENGE_COMPLETED, nowMillis, "Challenge completed successfully", db)
                logEvent(instanceId, ChallengeEventType.REWARD_GRANTED, nowMillis, "Awarded configured challenge XP", db)
            }
            settled = updated
        }

        val result = settled ?: getInstance(instanceId) ?: error("Challenge instance $instanceId disappeared")
        if (result.status == ChallengeStatus.COMPLETED) dbHelper.achievementEngine().evaluateAll()
        return result
    }

    /** Called by the app foreground path and the scheduled deadline receiver. */
    fun reconcileDueChallenges(nowMillis: Long = System.currentTimeMillis()): List<ChallengeInstance> {
        val due = mutableListOf<ChallengeInstance>()
        queryHistory(Int.MAX_VALUE).forEach { instance ->
            when {
                instance.status in listOf(ChallengeStatus.OFFERED, ChallengeStatus.REPLACED) &&
                    nowMillis > computeAcceptWindowEnd(instance.cycleId, ZoneId.of(instance.timezoneSnapshot)) ->
                    due += expireOffer(instance.instanceId, nowMillis)
                instance.status in listOf(ChallengeStatus.ACTIVE, ChallengeStatus.PAUSED, ChallengeStatus.REVIEW_PENDING) &&
                    nowMillis > instance.evidenceDeadline ->
                    due += settleMonthlyChallenge(instance.instanceId, nowMillis)
            }
        }
        return due
    }

    fun nextSettlementDeadline(nowMillis: Long = System.currentTimeMillis()): Long? = queryHistory(Int.MAX_VALUE)
        .asSequence()
        .filter { it.status in listOf(ChallengeStatus.ACTIVE, ChallengeStatus.PAUSED, ChallengeStatus.REVIEW_PENDING) }
        .mapNotNull { runCatching { Math.addExact(it.evidenceDeadline, 1L) }.getOrNull() }
        .filter { it > nowMillis }
        .minOrNull()

    private fun expireOffer(instanceId: String, nowMillis: Long): ChallengeInstance {
        val instance = getInstance(instanceId) ?: error("Instance $instanceId not found")
        if (instance.status !in listOf(ChallengeStatus.OFFERED, ChallengeStatus.REPLACED)) return instance
        val updated = instance.copy(status = ChallengeStatus.EXPIRED)
        val db = writableDb
        inTransaction(db) {
            if (updateInstance(updated, db, "instance_id = ? AND status IN ('OFFERED','REPLACED')", arrayOf(instanceId)) == 1) {
                logEvent(instanceId, ChallengeEventType.CHALLENGE_EXPIRED, nowMillis, "Accept window expired", db)
            }
        }
        return getInstance(instanceId) ?: updated
    }

    /**
     * Deterministic recommendation:
     * - Published templates only
     * - Exclude completed templates in past 6 cycles
     * - Exclude category completed in previous cycle
     */
    fun selectRecommendation(
        currentCycleId: String,
        excludeTemplateIds: Set<String> = emptySet(),
    ): ChallengeTemplate? {
        if (getActiveInstance() != null) return null
        val completedInstances = queryHistory(12).filter { it.status == ChallengeStatus.COMPLETED }
        val recentCompletedTemplateIds = completedInstances
            .take(6)
            .map { it.templateId }
            .toSet()

        val lastMonthCycle = runCatching {
            YearMonth.parse(currentCycleId).minusMonths(1).toString()
        }.getOrNull()

        val lastMonthCategory = completedInstances
            .firstOrNull { it.cycleId == lastMonthCycle }
            ?.let { inst -> catalog[inst.templateId]?.category }

        val candidates = catalog.values
            .filter { policy.isEnabled(it) }
            .filterNot { it.templateId in excludeTemplateIds }
            .filterNot { it.templateId in recentCompletedTemplateIds }

        // Prefer different category from last month
        val sorted = candidates.sortedWith(
            compareBy<ChallengeTemplate> { if (it.category == lastMonthCategory) 1 else 0 }
                .thenBy { it.templateId }
        )

        return sorted.firstOrNull()
    }

    // -------------------------------------------------------------
    // Database query & persistence helpers
    // -------------------------------------------------------------

    fun getInstance(instanceId: String): ChallengeInstance? {
        readableDb.rawQuery(
            "SELECT * FROM $TABLE_INSTANCES WHERE instance_id = ? LIMIT 1",
            arrayOf(instanceId)
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursorToInstance(cursor)
        }
        return null
    }

    fun findInstanceByCycle(cycleId: String): ChallengeInstance? {
        readableDb.rawQuery(
            "SELECT * FROM $TABLE_INSTANCES WHERE cycle_id = ? LIMIT 1",
            arrayOf(cycleId)
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursorToInstance(cursor)
        }
        return null
    }

    fun getActiveInstance(): ChallengeInstance? {
        readableDb.rawQuery(
            "SELECT * FROM $TABLE_INSTANCES WHERE status IN ('ACTIVE', 'PAUSED', 'REVIEW_PENDING') ORDER BY started_at DESC LIMIT 1",
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursorToInstance(cursor)
        }
        return null
    }

    fun queryHistory(limit: Int = 20): List<ChallengeInstance> {
        val list = mutableListOf<ChallengeInstance>()
        readableDb.rawQuery(
            "SELECT * FROM $TABLE_INSTANCES ORDER BY recommended_at DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToInstance(cursor))
            }
        }
        return list
    }

    fun queryMilestones(instanceId: String): List<ChallengeMilestoneRecord> {
        val list = mutableListOf<ChallengeMilestoneRecord>()
        readableDb.rawQuery(
            "SELECT * FROM $TABLE_MILESTONES WHERE instance_id = ? ORDER BY sequence ASC",
            arrayOf(instanceId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    ChallengeMilestoneRecord(
                        milestoneId = cursor.getString(cursor.getColumnIndexOrThrow("milestone_id")),
                        instanceId = cursor.getString(cursor.getColumnIndexOrThrow("instance_id")),
                        templateMilestoneId = cursor.getString(cursor.getColumnIndexOrThrow("template_milestone_id")),
                        title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                        description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
                        sequence = cursor.getInt(cursor.getColumnIndexOrThrow("sequence")),
                        progressWeight = cursor.getInt(cursor.getColumnIndexOrThrow("progress_weight")),
                        required = cursor.getInt(cursor.getColumnIndexOrThrow("required")) == 1,
                        status = MilestoneStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("status"))),
                        completedAt = cursor.getLong(cursor.getColumnIndexOrThrow("completed_at")),
                        verificationType = VerificationType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("verification_type"))),
                        verificationNotes = cursor.getString(cursor.getColumnIndexOrThrow("verification_notes")),
                    )
                )
            }
        }
        return list
    }

    fun getMilestone(milestoneId: String): ChallengeMilestoneRecord? {
        readableDb.rawQuery(
            "SELECT * FROM $TABLE_MILESTONES WHERE milestone_id = ? LIMIT 1",
            arrayOf(milestoneId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return ChallengeMilestoneRecord(
                    milestoneId = cursor.getString(cursor.getColumnIndexOrThrow("milestone_id")),
                    instanceId = cursor.getString(cursor.getColumnIndexOrThrow("instance_id")),
                    templateMilestoneId = cursor.getString(cursor.getColumnIndexOrThrow("template_milestone_id")),
                    title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
                    sequence = cursor.getInt(cursor.getColumnIndexOrThrow("sequence")),
                    progressWeight = cursor.getInt(cursor.getColumnIndexOrThrow("progress_weight")),
                    required = cursor.getInt(cursor.getColumnIndexOrThrow("required")) == 1,
                    status = MilestoneStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("status"))),
                    completedAt = cursor.getLong(cursor.getColumnIndexOrThrow("completed_at")),
                    verificationType = VerificationType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("verification_type"))),
                    verificationNotes = cursor.getString(cursor.getColumnIndexOrThrow("verification_notes")),
                )
            }
        }
        return null
    }

    fun queryEvidences(instanceId: String): List<ChallengeEvidenceRecord> {
        val list = mutableListOf<ChallengeEvidenceRecord>()
        readableDb.rawQuery(
            "SELECT * FROM $TABLE_EVIDENCES WHERE instance_id = ? ORDER BY created_at ASC",
            arrayOf(instanceId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    ChallengeEvidenceRecord(
                        evidenceId = cursor.getString(cursor.getColumnIndexOrThrow("evidence_id")),
                        instanceId = cursor.getString(cursor.getColumnIndexOrThrow("instance_id")),
                        milestoneId = cursor.getString(cursor.getColumnIndexOrThrow("milestone_id")),
                        evidenceType = EvidenceType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("evidence_type"))),
                        localFileReference = cursor.getString(cursor.getColumnIndexOrThrow("local_file_reference")),
                        textContent = cursor.getString(cursor.getColumnIndexOrThrow("text_content")),
                        externalUri = cursor.getString(cursor.getColumnIndexOrThrow("external_uri")),
                        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                        actionOccurredAt = cursor.getLong(cursor.getColumnIndexOrThrow("action_occurred_at")),
                        checksum = cursor.getString(cursor.getColumnIndexOrThrow("checksum")),
                        verificationStatus = EvidenceStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("verification_status"))),
                    )
                )
            }
        }
        return list
    }

    fun queryEvents(instanceId: String): List<ChallengeEventRecord> {
        val list = mutableListOf<ChallengeEventRecord>()
        readableDb.rawQuery(
            "SELECT * FROM $TABLE_EVENTS WHERE instance_id = ? ORDER BY event_timestamp ASC",
            arrayOf(instanceId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    ChallengeEventRecord(
                        eventId = cursor.getString(cursor.getColumnIndexOrThrow("event_id")),
                        instanceId = cursor.getString(cursor.getColumnIndexOrThrow("instance_id")),
                        eventType = ChallengeEventType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("event_type"))),
                        eventTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow("event_timestamp")),
                        eventPayload = cursor.getString(cursor.getColumnIndexOrThrow("event_payload")),
                        idempotencyKey = cursor.getString(cursor.getColumnIndexOrThrow("idempotency_key")),
                    )
                )
            }
        }
        return list
    }

    fun queryReward(instanceId: String): ChallengeRewardRecord? {
        readableDb.rawQuery(
            "SELECT * FROM $TABLE_REWARDS WHERE instance_id = ? LIMIT 1",
            arrayOf(instanceId)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return ChallengeRewardRecord(
                    rewardId = cursor.getString(cursor.getColumnIndexOrThrow("reward_id")),
                    instanceId = cursor.getString(cursor.getColumnIndexOrThrow("instance_id")),
                    rewardType = cursor.getString(cursor.getColumnIndexOrThrow("reward_type")),
                    rewardValue = cursor.getInt(cursor.getColumnIndexOrThrow("reward_value")),
                    ledgerReference = cursor.getString(cursor.getColumnIndexOrThrow("ledger_reference")),
                    grantStatus = cursor.getString(cursor.getColumnIndexOrThrow("grant_status")),
                    grantedAt = cursor.getLong(cursor.getColumnIndexOrThrow("granted_at")),
                    idempotencyKey = cursor.getString(cursor.getColumnIndexOrThrow("idempotency_key")),
                )
            }
        }
        return null
    }

    private fun insertInstance(instance: ChallengeInstance, db: SQLiteDatabase = writableDb) {
        val cv = ContentValues().apply {
            put("instance_id", instance.instanceId)
            put("cycle_id", instance.cycleId)
            put("template_id", instance.templateId)
            put("template_version", instance.templateVersion)
            put("template_snapshot", instance.templateSnapshotJson)
            put("difficulty", instance.difficulty.name)
            put("status", instance.status.name)
            put("recommended_at", instance.recommendedAt)
            put("accepted_at", instance.acceptedAt)
            put("started_at", instance.startedAt)
            put("deadline", instance.deadline)
            put("evidence_deadline", instance.evidenceDeadline)
            put("timezone_snapshot", instance.timezoneSnapshot)
            put("replacement_used", if (instance.replacementUsed) 1 else 0)
            put("baseline_data", instance.baselineDataJson)
            put("wish_data", instance.wishData)
            put("reward_snapshot", instance.rewardSnapshotJson)
            put("final_result", instance.finalResultJson)
            put("completed_at", instance.completedAt)
        }
        db.insertOrThrow(TABLE_INSTANCES, null, cv)
    }

    private fun updateInstance(
        instance: ChallengeInstance,
        db: SQLiteDatabase = writableDb,
        where: String = "instance_id = ?",
        whereArgs: Array<String> = arrayOf(instance.instanceId),
    ): Int {
        val cv = ContentValues().apply {
            put("template_id", instance.templateId)
            put("template_version", instance.templateVersion)
            put("template_snapshot", instance.templateSnapshotJson)
            put("difficulty", instance.difficulty.name)
            put("status", instance.status.name)
            put("accepted_at", instance.acceptedAt)
            put("started_at", instance.startedAt)
            put("deadline", instance.deadline)
            put("evidence_deadline", instance.evidenceDeadline)
            put("replacement_used", if (instance.replacementUsed) 1 else 0)
            put("baseline_data", instance.baselineDataJson)
            put("wish_data", instance.wishData)
            put("reward_snapshot", instance.rewardSnapshotJson)
            put("final_result", instance.finalResultJson)
            put("completed_at", instance.completedAt)
        }
        return db.update(TABLE_INSTANCES, cv, where, whereArgs)
    }

    private fun insertMilestone(milestone: ChallengeMilestoneRecord, db: SQLiteDatabase = writableDb) {
        val cv = ContentValues().apply {
            put("milestone_id", milestone.milestoneId)
            put("instance_id", milestone.instanceId)
            put("template_milestone_id", milestone.templateMilestoneId)
            put("title", milestone.title)
            put("description", milestone.description)
            put("sequence", milestone.sequence)
            put("progress_weight", milestone.progressWeight)
            put("required", if (milestone.required) 1 else 0)
            put("status", milestone.status.name)
            put("completed_at", milestone.completedAt)
            put("verification_type", milestone.verificationType.name)
            put("verification_notes", milestone.verificationNotes)
        }
        db.insertOrThrow(TABLE_MILESTONES, null, cv)
    }

    private fun insertEvidence(evidence: ChallengeEvidenceRecord, db: SQLiteDatabase = writableDb) {
        val cv = ContentValues().apply {
            put("evidence_id", evidence.evidenceId)
            put("instance_id", evidence.instanceId)
            put("milestone_id", evidence.milestoneId)
            put("evidence_type", evidence.evidenceType.name)
            put("local_file_reference", evidence.localFileReference)
            put("text_content", evidence.textContent)
            put("external_uri", evidence.externalUri)
            put("created_at", evidence.createdAt)
            put("action_occurred_at", evidence.actionOccurredAt)
            put("checksum", evidence.checksum)
            put("verification_status", evidence.verificationStatus.name)
        }
        db.insertOrThrow(TABLE_EVIDENCES, null, cv)
    }

    private fun logEvent(
        instanceId: String,
        type: ChallengeEventType,
        timestamp: Long,
        payload: String,
        db: SQLiteDatabase = writableDb,
        idempotencyKey: String = "${instanceId}_${type.name}_$timestamp",
    ) {
        val eventId = "evt_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val cv = ContentValues().apply {
            put("event_id", eventId)
            put("instance_id", instanceId)
            put("event_type", type.name)
            put("event_timestamp", timestamp)
            put("event_payload", payload)
            put("idempotency_key", idempotencyKey)
        }
        db.insertWithOnConflict(TABLE_EVENTS, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun insertReward(reward: ChallengeRewardRecord, db: SQLiteDatabase = writableDb) {
        val cv = ContentValues().apply {
            put("reward_id", reward.rewardId)
            put("instance_id", reward.instanceId)
            put("reward_type", reward.rewardType)
            put("reward_value", reward.rewardValue)
            put("ledger_reference", reward.ledgerReference)
            put("grant_status", reward.grantStatus)
            put("granted_at", reward.grantedAt)
            put("idempotency_key", reward.idempotencyKey)
        }
        db.insertOrThrow(TABLE_REWARDS, null, cv)
    }

    private fun cursorToInstance(cursor: android.database.Cursor): ChallengeInstance = ChallengeInstance(
        instanceId = cursor.getString(cursor.getColumnIndexOrThrow("instance_id")),
        cycleId = cursor.getString(cursor.getColumnIndexOrThrow("cycle_id")),
        templateId = cursor.getString(cursor.getColumnIndexOrThrow("template_id")),
        templateVersion = cursor.getInt(cursor.getColumnIndexOrThrow("template_version")),
        templateSnapshotJson = cursor.getString(cursor.getColumnIndexOrThrow("template_snapshot")),
        difficulty = ChallengeDifficulty.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("difficulty"))),
        status = ChallengeStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("status"))),
        recommendedAt = cursor.getLong(cursor.getColumnIndexOrThrow("recommended_at")),
        acceptedAt = cursor.getLong(cursor.getColumnIndexOrThrow("accepted_at")),
        startedAt = cursor.getLong(cursor.getColumnIndexOrThrow("started_at")),
        deadline = cursor.getLong(cursor.getColumnIndexOrThrow("deadline")),
        evidenceDeadline = cursor.getLong(cursor.getColumnIndexOrThrow("evidence_deadline")),
        timezoneSnapshot = cursor.getString(cursor.getColumnIndexOrThrow("timezone_snapshot")),
        replacementUsed = cursor.getInt(cursor.getColumnIndexOrThrow("replacement_used")) == 1,
        baselineDataJson = cursor.getString(cursor.getColumnIndexOrThrow("baseline_data")),
        wishData = cursor.getString(cursor.getColumnIndexOrThrow("wish_data")),
        rewardSnapshotJson = cursor.getString(cursor.getColumnIndexOrThrow("reward_snapshot")),
        finalResultJson = cursor.getString(cursor.getColumnIndexOrThrow("final_result")),
        completedAt = cursor.getLong(cursor.getColumnIndexOrThrow("completed_at")),
    )
}
