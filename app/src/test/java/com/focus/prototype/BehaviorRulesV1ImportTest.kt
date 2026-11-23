package com.focus.prototype

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class BehaviorRulesV1ImportTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun test01_health_connect_valid_7h_asleep_session_evaluates_pass() {
        val name = "import-t01-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-11-21T09:00:00")
        val db = Database(context, name, now = { time })
        try {
            db.saveSleepTargets("00:30", "07:30")

            val mockSource = MockSleepDataSource()
            val zone = ZoneId.systemDefault()
            val startTime = LocalDate.parse("2026-11-21").minusDays(1).atTime(23, 30).atZone(zone).toInstant()
            val endTime = LocalDate.parse("2026-11-21").atTime(7, 30).atZone(zone).toInstant()

            // 8 hours = 480 min sleeping
            val stage = RawSleepStage(startTime, endTime, 4) // STAGE_TYPE_SLEEPING
            mockSource.mockRecords.add(
                RawSleepRecord(
                    id = "hc-record-001",
                    sourcePackage = "com.google.android.apps.fitness",
                    startTime = startTime,
                    endTime = endTime,
                    lastModifiedTime = endTime,
                    stages = listOf(stage),
                    isAsleepConfirmed = true,
                    actualSleepMinutes = 480L
                )
            )

            val result = SleepImportManager.syncFromAvailableSources(context, db, source = mockSource)
            assertEquals(HealthDataSourceStatus.AVAILABLE_CONNECTED, result.status)
            assertEquals(1, result.importedCount)

            val record = db.sleepRecord("2026-11-21")
            assertNotNull(record)
            assertEquals(SleepStatus.PASS, record!!.status)
            assertEquals("23:30", record.bedtime)
            assertEquals("07:30", record.wakeTime)
            assertEquals(480L, record.durationMinutes)
            assertEquals(0, record.level)
            assertEquals(mockSource.id, record.source)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun test02_in_bed_interval_without_actual_sleep_proof_evaluates_unverified() {
        val name = "import-t02-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-11-21T09:00:00")
        val db = Database(context, name, now = { time })
        try {
            db.saveSleepTargets("00:30", "07:30")

            val mockSource = MockSleepDataSource()
            val zone = ZoneId.systemDefault()
            val startTime = LocalDate.parse("2026-11-21").minusDays(1).atTime(23, 30).atZone(zone).toInstant()
            val endTime = LocalDate.parse("2026-11-21").atTime(7, 30).atZone(zone).toInstant()

            // Only in-bed stage (stage 2 = STAGE_TYPE_AWAKE_IN_BED), no confirmed sleeping stages
            val stage = RawSleepStage(startTime, endTime, 2)
            mockSource.mockRecords.add(
                RawSleepRecord(
                    id = "hc-record-inbed-only",
                    sourcePackage = "com.heytap.health",
                    startTime = startTime,
                    endTime = endTime,
                    lastModifiedTime = endTime,
                    stages = listOf(stage),
                    isAsleepConfirmed = false, // Not confirmed actual asleep!
                    actualSleepMinutes = 0L
                )
            )

            val result = SleepImportManager.syncFromAvailableSources(context, db, source = mockSource)
            assertEquals(1, result.importedCount)

            val record = db.sleepRecord("2026-11-21")
            assertNotNull(record)
            // System in-bed interval cannot prove actual sleep >= 7h -> must evaluate to UNVERIFIED!
            assertEquals(SleepStatus.UNVERIFIED, record!!.status)
            assertTrue(record.reason.contains("缺乏实际睡眠阶段证据") || record.reason.contains("待核验"))
            // Level is not incremented, no penalty
            assertEquals(0, record.level)
            assertEquals(0, record.penaltyMinutes)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun test03_resync_idempotency_no_duplicate_ledger_or_version() {
        val name = "import-t03-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-11-21T09:00:00")
        val db = Database(context, name, now = { time })
        try {
            db.saveSleepTargets("00:30", "07:30")

            val mockSource = MockSleepDataSource()
            val zone = ZoneId.systemDefault()
            val startTime = LocalDate.parse("2026-11-21").minusDays(1).atTime(0, 30).atZone(zone).toInstant()
            val endTime = LocalDate.parse("2026-11-21").atTime(7, 30).atZone(zone).toInstant()

            mockSource.mockRecords.add(
                RawSleepRecord(
                    id = "hc-record-dedup",
                    sourcePackage = "com.google.android.apps.fitness",
                    startTime = startTime,
                    endTime = endTime,
                    lastModifiedTime = endTime,
                    stages = listOf(RawSleepStage(startTime, endTime, 4)),
                    isAsleepConfirmed = true,
                    actualSleepMinutes = 420L
                )
            )

            // First sync
            val res1 = SleepImportManager.syncFromAvailableSources(context, db, source = mockSource)
            assertEquals(1, res1.importedCount)
            val r1 = db.sleepRecord("2026-11-21")!!
            assertEquals(1, r1.version)

            // Second sync (same record, unchanged)
            val res2 = SleepImportManager.syncFromAvailableSources(context, db, source = mockSource)
            assertEquals(0, res2.importedCount)
            assertEquals(1, res2.skippedCount)

            val r2 = db.sleepRecord("2026-11-21")!!
            assertEquals(1, r2.version) // Version not incremented
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun test04_source_record_update_reevaluates_cycle_and_cascades() {
        val name = "import-t04-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-11-21T09:00:00")
        val db = Database(context, name, now = { time })
        try {
            db.saveSleepTargets("00:30", "07:30")

            val mockSource = MockSleepDataSource()
            val zone = ZoneId.systemDefault()
            val startTime = LocalDate.parse("2026-11-21").minusDays(1).atTime(0, 30).atZone(zone).toInstant()
            val endTime = LocalDate.parse("2026-11-21").atTime(7, 30).atZone(zone).toInstant()

            mockSource.mockRecords.add(
                RawSleepRecord(
                    id = "hc-record-update",
                    sourcePackage = "com.google.android.apps.fitness",
                    startTime = startTime,
                    endTime = endTime,
                    lastModifiedTime = endTime,
                    stages = listOf(RawSleepStage(startTime, endTime, 4)),
                    isAsleepConfirmed = true,
                    actualSleepMinutes = 420L
                )
            )

            // Initial sync: PASS
            SleepImportManager.syncFromAvailableSources(context, db, source = mockSource)
            assertEquals(SleepStatus.PASS, db.sleepRecord("2026-11-21")!!.status)

            // Source app updates the record later: actual sleep was late at 02:00 (FAIL)
            val updatedStart = LocalDate.parse("2026-11-21").atTime(2, 0).atZone(zone).toInstant()
            val updatedMod = endTime.plusSeconds(3600)
            mockSource.mockRecords.clear()
            mockSource.mockRecords.add(
                RawSleepRecord(
                    id = "hc-record-update",
                    sourcePackage = "com.google.android.apps.fitness",
                    startTime = updatedStart,
                    endTime = endTime,
                    lastModifiedTime = updatedMod,
                    stages = listOf(RawSleepStage(updatedStart, endTime, 4)),
                    isAsleepConfirmed = true,
                    actualSleepMinutes = 330L
                )
            )

            // Sync update
            val res2 = SleepImportManager.syncFromAvailableSources(context, db, source = mockSource)
            assertEquals(1, res2.importedCount)

            val updatedRec = db.sleepRecord("2026-11-21")!!
            assertEquals(SleepStatus.FAIL, updatedRec.status)
            assertEquals(1, updatedRec.level)
            assertEquals(5, updatedRec.penaltyMinutes)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun test05_manual_correction_preserved_against_silent_overwrite() {
        val name = "import-t05-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-11-21T09:00:00")
        val db = Database(context, name, now = { time })
        try {
            db.saveSleepTargets("00:30", "07:30")

            // User already has a manual record
            db.recordSleepCycle("2026-11-21", "00:25", "07:35", source = "manual")
            val manualRec = db.sleepRecord("2026-11-21")!!
            assertEquals("manual", manualRec.source)
            assertEquals("00:25", manualRec.bedtime)

            // External source brings in different data
            val mockSource = MockSleepDataSource()
            val zone = ZoneId.systemDefault()
            val startTime = LocalDate.parse("2026-11-21").minusDays(1).atTime(23, 0).atZone(zone).toInstant()
            val endTime = LocalDate.parse("2026-11-21").atTime(7, 0).atZone(zone).toInstant()
            mockSource.mockRecords.add(
                RawSleepRecord(
                    id = "hc-record-external",
                    sourcePackage = "com.google.android.apps.fitness",
                    startTime = startTime,
                    endTime = endTime,
                    lastModifiedTime = endTime,
                    stages = listOf(RawSleepStage(startTime, endTime, 4)),
                    isAsleepConfirmed = true,
                    actualSleepMinutes = 480L
                )
            )

            val res = SleepImportManager.syncFromAvailableSources(context, db, source = mockSource)
            assertEquals(0, res.importedCount)
            assertEquals(1, res.skippedCount)

            // Manual record is untouched!
            val preserved = db.sleepRecord("2026-11-21")!!
            assertEquals("manual", preserved.source)
            assertEquals("00:25", preserved.bedtime)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun test06_conflicting_multiple_source_records_flag_unverified() {
        val name = "import-t06-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-11-21T09:00:00")
        val db = Database(context, name, now = { time })
        try {
            db.saveSleepTargets("00:30", "07:30")

            val mockSource = MockSleepDataSource()
            val zone = ZoneId.systemDefault()
            val s1 = LocalDate.parse("2026-11-21").minusDays(1).atTime(23, 0).atZone(zone).toInstant()
            val e1 = LocalDate.parse("2026-11-21").atTime(6, 0).atZone(zone).toInstant()

            val s2 = LocalDate.parse("2026-11-21").atTime(1, 0).atZone(zone).toInstant()
            val e2 = LocalDate.parse("2026-11-21").atTime(8, 0).atZone(zone).toInstant()

            mockSource.mockRecords.add(
                RawSleepRecord("hc-conflict-1", "pkg1", s1, e1, e1, emptyList(), true, 420L)
            )
            mockSource.mockRecords.add(
                RawSleepRecord("hc-conflict-2", "pkg2", s2, e2, e2, emptyList(), true, 420L)
            )

            val res = SleepImportManager.syncFromAvailableSources(context, db, source = mockSource)
            assertEquals(1, res.conflictCount)

            val record = db.sleepRecord("2026-11-21")
            assertNotNull(record)
            assertEquals(SleepStatus.UNVERIFIED, record!!.status)
            assertTrue(record.reason.contains("冲突"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun test07_permission_revocation_or_missing_source_handled_gracefully() {
        val name = "import-t07-${System.nanoTime()}.db"
        val time = LocalDateTime.parse("2026-11-21T09:00:00")
        val db = Database(context, name, now = { time })
        try {
            val mockSource = MockSleepDataSource(status = HealthDataSourceStatus.AVAILABLE_NEED_PERMISSION)
            val res = SleepImportManager.syncFromAvailableSources(context, db, source = mockSource)
            assertEquals(HealthDataSourceStatus.AVAILABLE_NEED_PERMISSION, res.status)
            assertEquals(0, res.importedCount)
            assertTrue(res.message.contains("需要授权"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun test08_fix2_expiry_refund_attribution_does_not_resurrect_expired_balance() {
        val name = "import-t08-${System.nanoTime()}.db"
        var time = LocalDateTime.parse("2026-11-20T10:00:00")
        val db = Database(context, name, now = { time })
        try {
            db.saveSleepTargets("00:30", "07:30")

            // Day 1: 2026-11-20, user earns 30 min video
            db.addTask("2026-11-20", "任务1", true, 0, "行动型", "检查项", 30, 0, "once")
            db.confirm("2026-11-20", false, 0, 0)
            val t1 = db.tasks("2026-11-20").first { it.title == "任务1" }
            db.complete(t1)
            assertEquals(1_800_000L, db.balance("video"))

            // Cycle 2026-11-20 was FAIL -> level 1 -> 5 min (300_000L) penalty effective today
            db.recordSleepCycle("2026-11-20", "01:30", "07:30") // FAIL
            // User earns another 10 min, penalty is deducted from today's batch
            db.addTask("2026-11-20", "任务2", false, 0, "行动型", "检查项", 10, 0, "once")
            val t2 = db.tasks("2026-11-20").first { it.title == "任务2" }
            db.complete(t2)

            // Advance clock to Day 3 (2026-11-22 10:00:00)
            // Day 1 batch (2026-11-20) expired at 2026-11-21 04:00!
            time = LocalDateTime.parse("2026-11-22T10:00:00")
            assertTrue(db.isBatchExpired("2026-11-20", time))
            db.settleExpiredBatches(time)

            // Balance on Day 3 before correction is 0
            assertEquals(0L, db.balance("video"))

            // Now user corrects Day 1 (2026-11-20) from FAIL to PASS
            db.recordSleepCycle("2026-11-20", "00:30", "07:30", reason = "录入错误更正为准时")

            val updated = db.sleepRecord("2026-11-20")!!
            assertEquals(SleepStatus.PASS, updated.status)

            // CRITICAL CHECK:
            // Refund is attributed to 2026-11-20, which is ALREADY EXPIRED!
            // It MUST NOT resurrect expired quota into today's usable balance!
            assertEquals(0L, db.balance("video"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun test09_fix2_24h_boundary_and_reminder_dedup() {
        val name = "import-t09-${System.nanoTime()}.db"
        val db = Database(context, name)
        try {
            db.saveSleepTargets("00:30", "07:30")
            val cycleDate = "2026-11-21"

            // Target wake is 2026-11-21 07:30.
            // 23 hours 59 minutes after target wake: 2026-11-22 07:29
            val tBefore24h = LocalDateTime.parse("2026-11-22T07:29:00")
            assertFalse(db.isSleepUnverifiedOverdue(cycleDate, tBefore24h))

            // Exactly 24 hours after target wake: 2026-11-22 07:30
            val tAt24h = LocalDateTime.parse("2026-11-22T07:30:00")
            assertTrue(db.isSleepUnverifiedOverdue(cycleDate, tAt24h))

            // Reminder deduplication
            assertFalse(db.hasSleepBeenReminded(cycleDate))
            db.markSleepReminded(cycleDate)
            assertTrue(db.hasSleepBeenReminded(cycleDate))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun test10_fix2_preparation_time_consistency() {
        val name = "import-t10-${System.nanoTime()}.db"
        val db = Database(context, name)
        try {
            db.saveSleepTargets("00:30", "07:30")
            val cycleDate = "2026-11-21"

            assertNull(db.sleepPrepareTime(cycleDate))
            db.setSleepPrepareTime(cycleDate, "23:15")
            assertEquals("23:15", db.sleepPrepareTime(cycleDate))

            // confirmWake should pick up preparation time
            val rec = db.confirmWake(wakeTime = "07:30", cycleDate = cycleDate)
            assertEquals("23:15", rec.prepareBedtime)
            assertEquals("23:15", rec.bedtime)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun test11_no_mandatory_sleep_tasks_on_today_page() {
        val name = "import-t11-${System.nanoTime()}.db"
        val db = Database(context, name)
        try {
            val today = db.day()
            // Ensure no sleep system tasks by default
            assertFalse(db.setting("sleep_system_tasks_enabled") == "true")

            val tasks = db.tasks(today)
            // System sleep tasks should NOT be generated by default
            assertNull(tasks.firstOrNull { it.title == "按时准备睡眠" })
            assertNull(tasks.firstOrNull { it.title == "按时起床" })
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun test12_read_failure_preserves_existing_sleep_record_and_penalties() {
        val name = "import-t12-${System.nanoTime()}.db"
        val db = Database(context, name)
        try {
            db.saveSleepTargets("00:30", "07:30")
            // Establish an existing record and penalty on 2026-11-21
            db.recordSleepCycle("2026-11-21", "01:30", "07:30") // FAIL, penalty 5 min
            val originalRec = db.sleepRecord("2026-11-21")!!
            assertEquals(SleepStatus.FAIL, originalRec.status)
            assertEquals(1, originalRec.level)
            assertEquals(5, originalRec.penaltyMinutes)
            val originalPendingPenalty = db.pendingSleepPenalty("video")
            assertTrue(originalPendingPenalty > 0)

            // External data source throws failure during fetch
            val mockSource = MockSleepDataSource(
                status = HealthDataSourceStatus.AVAILABLE_CONNECTED,
                shouldThrowOnFetch = true,
                throwExceptionMessage = "底层系统 HealthConnectService 连接超时异常"
            )

            val result = SleepImportManager.syncFromAvailableSources(context, db, source = mockSource)
            assertEquals(ImportOutcome.READ_FAILED, result.outcome)
            assertTrue(result.message.contains("读取失败"))
            assertTrue(result.message.contains("已保留既有睡眠记录与奖惩状态"))

            // Verify: existing sleep record and penalties remain untouched!
            val preservedRec = db.sleepRecord("2026-11-21")!!
            assertEquals(originalRec.status, preservedRec.status)
            assertEquals(originalRec.level, preservedRec.level)
            assertEquals(originalRec.penaltyMinutes, preservedRec.penaltyMinutes)
            assertEquals(originalRec.bedtime, preservedRec.bedtime)
            assertEquals(originalRec.wakeTime, preservedRec.wakeTime)
            assertEquals(originalRec.version, preservedRec.version)
            assertEquals(originalPendingPenalty, db.pendingSleepPenalty("video"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun test13_read_success_empty_outcome() {
        val name = "import-t13-${System.nanoTime()}.db"
        val db = Database(context, name)
        try {
            val mockSource = MockSleepDataSource(status = HealthDataSourceStatus.AVAILABLE_CONNECTED)
            mockSource.mockRecords.clear() // No records

            val result = SleepImportManager.syncFromAvailableSources(context, db, source = mockSource)
            assertEquals(ImportOutcome.SUCCESS_EMPTY, result.outcome)
            assertEquals(0, result.importedCount)
            assertTrue(result.message.contains("未找到有效睡眠记录"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun test14_read_unauthorized_outcome() {
        val name = "import-t14-${System.nanoTime()}.db"
        val db = Database(context, name)
        try {
            val mockSource = MockSleepDataSource(status = HealthDataSourceStatus.AVAILABLE_NEED_PERMISSION)
            val result = SleepImportManager.syncFromAvailableSources(context, db, source = mockSource)
            assertEquals(ImportOutcome.UNAUTHORIZED, result.outcome)
            assertEquals(0, result.importedCount)
            assertTrue(result.message.contains("需要授权"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun test15_read_success_imported_outcome() {
        val name = "import-t15-${System.nanoTime()}.db"
        val db = Database(context, name)
        try {
            db.saveSleepTargets("00:30", "07:30")
            val mockSource = MockSleepDataSource()
            val zone = ZoneId.systemDefault()
            val startTime = LocalDate.parse("2026-11-22").minusDays(1).atTime(23, 30).atZone(zone).toInstant()
            val endTime = LocalDate.parse("2026-11-22").atTime(7, 30).atZone(zone).toInstant()

            mockSource.mockRecords.add(
                RawSleepRecord(
                    id = "hc-record-success-import",
                    sourcePackage = "com.example.healthconnect.testwriter",
                    startTime = startTime,
                    endTime = endTime,
                    lastModifiedTime = endTime,
                    stages = listOf(RawSleepStage(startTime, endTime, 4)),
                    isAsleepConfirmed = true,
                    actualSleepMinutes = 480L
                )
            )

            val result = SleepImportManager.syncFromAvailableSources(context, db, source = mockSource)
            assertEquals(ImportOutcome.SUCCESS_IMPORTED, result.outcome)
            assertEquals(1, result.importedCount)
            assertEquals("com.example.healthconnect.testwriter", result.sourcePackage)
            assertEquals("2026-11-22", result.cycleDate)
            assertEquals(480L, result.durationMinutes)
            assertEquals(SleepStatus.PASS, result.settlementStatus)
            assertTrue(result.message.contains("成功导入 1 条睡眠记录"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun test16_p0_awake_stages_400min_sleep_under_420min_evaluates_fail() {
        val name = "import-t16-${System.nanoTime()}.db"
        val db = Database(context, name)
        try {
            db.saveSleepTargets("00:30", "07:30")
            val mockSource = MockSleepDataSource()
            val zone = ZoneId.systemDefault()
            val startTime = LocalDate.parse("2026-11-22").minusDays(1).atTime(23, 30).atZone(zone).toInstant()
            val endTime = LocalDate.parse("2026-11-22").atTime(7, 30).atZone(zone).toInstant()

            // 480 minutes in-bed, but 80 minutes awake -> only 400 minutes actual sleep
            val sleepingStage1 = RawSleepStage(startTime, startTime.plusSeconds(180 * 60), 4) // 180 min
            val awakeStage = RawSleepStage(startTime.plusSeconds(180 * 60), startTime.plusSeconds(260 * 60), 1) // 80 min awake
            val sleepingStage2 = RawSleepStage(startTime.plusSeconds(260 * 60), endTime, 4) // 220 min
            mockSource.mockRecords.add(
                RawSleepRecord(
                    id = "hc-record-awake-stages",
                    sourcePackage = "com.example.healthconnect.testwriter",
                    startTime = startTime,
                    endTime = endTime,
                    lastModifiedTime = endTime,
                    stages = listOf(sleepingStage1, awakeStage, sleepingStage2),
                    isAsleepConfirmed = true,
                    actualSleepMinutes = 400L
                )
            )

            val result = SleepImportManager.syncFromAvailableSources(context, db, source = mockSource)
            assertEquals(ImportOutcome.SUCCESS_IMPORTED, result.outcome)
            assertEquals(1, result.importedCount)
            assertEquals(400L, result.durationMinutes)
            // MUST evaluate to FAIL because 400 < 420 minutes!
            assertEquals(SleepStatus.FAIL, result.settlementStatus)

            val rec = db.sleepRecord("2026-11-22")
            assertNotNull(rec)
            assertEquals(SleepStatus.FAIL, rec!!.status)
            assertEquals(400L, rec.durationMinutes)
            assertEquals(1, rec.level)
            assertEquals(5, rec.penaltyMinutes)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun test17_p1_source_update_multiple_versions_not_blocked_by_version_greater_than_1() {
        val name = "import-t17-${System.nanoTime()}.db"
        val db = Database(context, name)
        try {
            db.saveSleepTargets("00:30", "07:30")
            val mockSource = MockSleepDataSource()
            val zone = ZoneId.systemDefault()
            val startTime = LocalDate.parse("2026-11-22").minusDays(1).atTime(23, 30).atZone(zone).toInstant()
            val endTime = LocalDate.parse("2026-11-22").atTime(7, 30).atZone(zone).toInstant()

            // 1st sync: 480 min -> PASS, version 1
            mockSource.mockRecords.add(
                RawSleepRecord(
                    id = "hc-record-multi-ver",
                    sourcePackage = "com.example.healthconnect.testwriter",
                    startTime = startTime,
                    endTime = endTime,
                    lastModifiedTime = endTime,
                    stages = listOf(RawSleepStage(startTime, endTime, 4)),
                    isAsleepConfirmed = true,
                    actualSleepMinutes = 480L
                )
            )
            val res1 = SleepImportManager.syncFromAvailableSources(context, db, source = mockSource)
            assertEquals(1, res1.importedCount)
            val rec1 = db.sleepRecord("2026-11-22")!!
            assertEquals(1, rec1.version)
            assertEquals(SleepStatus.PASS, rec1.status)

            // 2nd sync: modified record -> FAIL, version 2
            val modTime2 = endTime.plusSeconds(300)
            mockSource.mockRecords.clear()
            mockSource.mockRecords.add(
                RawSleepRecord(
                    id = "hc-record-multi-ver",
                    sourcePackage = "com.example.healthconnect.testwriter",
                    startTime = startTime,
                    endTime = endTime,
                    lastModifiedTime = modTime2,
                    stages = listOf(RawSleepStage(startTime, endTime, 4)),
                    isAsleepConfirmed = true,
                    actualSleepMinutes = 400L
                )
            )
            val res2 = SleepImportManager.syncFromAvailableSources(context, db, source = mockSource)
            assertEquals(1, res2.importedCount)
            val rec2 = db.sleepRecord("2026-11-22")!!
            assertEquals(2, rec2.version)
            assertEquals(SleepStatus.FAIL, rec2.status)

            // 3rd sync: source updates AGAIN (version 2 -> 3). Must NOT be skipped as manual correction!
            val modTime3 = endTime.plusSeconds(600)
            mockSource.mockRecords.clear()
            mockSource.mockRecords.add(
                RawSleepRecord(
                    id = "hc-record-multi-ver",
                    sourcePackage = "com.example.healthconnect.testwriter",
                    startTime = startTime,
                    endTime = endTime,
                    lastModifiedTime = modTime3,
                    stages = listOf(RawSleepStage(startTime, endTime, 4)),
                    isAsleepConfirmed = true,
                    actualSleepMinutes = 450L
                )
            )
            val res3 = SleepImportManager.syncFromAvailableSources(context, db, source = mockSource)
            assertEquals(1, res3.importedCount)
            val rec3 = db.sleepRecord("2026-11-22")!!
            assertEquals(3, rec3.version)
            assertEquals(SleepStatus.PASS, rec3.status)
            assertEquals(450L, rec3.durationMinutes)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun test18_p2_repeat_sync_already_up_to_date_populates_cycle_duration_and_status() {
        val name = "import-t18-${System.nanoTime()}.db"
        val db = Database(context, name)
        try {
            db.saveSleepTargets("00:30", "07:30")
            val mockSource = MockSleepDataSource()
            val zone = ZoneId.systemDefault()
            val startTime = LocalDate.parse("2026-11-22").minusDays(1).atTime(23, 30).atZone(zone).toInstant()
            val endTime = LocalDate.parse("2026-11-22").atTime(7, 30).atZone(zone).toInstant()

            mockSource.mockRecords.add(
                RawSleepRecord(
                    id = "hc-record-repeat-test",
                    sourcePackage = "com.example.healthconnect.testwriter",
                    startTime = startTime,
                    endTime = endTime,
                    lastModifiedTime = endTime,
                    stages = listOf(RawSleepStage(startTime, endTime, 4)),
                    isAsleepConfirmed = true,
                    actualSleepMinutes = 480L
                )
            )
            // 1st sync
            val res1 = SleepImportManager.syncFromAvailableSources(context, db, source = mockSource)
            assertEquals(ImportOutcome.SUCCESS_IMPORTED, res1.outcome)

            // 2nd sync (repeat sync)
            val res2 = SleepImportManager.syncFromAvailableSources(context, db, source = mockSource)
            assertEquals(ImportOutcome.ALREADY_UP_TO_DATE, res2.outcome)
            assertEquals(0, res2.importedCount)
            assertEquals(1, res2.skippedCount)
            // MUST populate cycleDate, duration, and status!
            assertEquals("2026-11-22", res2.cycleDate)
            assertEquals(480L, res2.durationMinutes)
            assertEquals(SleepStatus.PASS, res2.settlementStatus)
            assertTrue(res2.message.contains("数据已是最新"))
            assertTrue(res2.message.contains("2026-11-22"))
            assertTrue(res2.message.contains("8小时0分"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
