package com.focus.prototype

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.OutcomeReceiver
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class RawSleepStage(
    val startTime: Instant,
    val endTime: Instant,
    val stageType: Int
)

data class RawSleepRecord(
    val id: String,
    val sourcePackage: String,
    val startTime: Instant,
    val endTime: Instant,
    val lastModifiedTime: Instant,
    val stages: List<RawSleepStage> = emptyList(),
    val isAsleepConfirmed: Boolean = false,
    val actualSleepMinutes: Long = 0L,
    val clientRecordVersion: Long = 0L,
    val notes: String = ""
)

enum class HealthDataSourceStatus {
    AVAILABLE_CONNECTED,
    AVAILABLE_NEED_PERMISSION,
    NOT_SUPPORTED,
    DISABLED
}

enum class ImportOutcome {
    UNAUTHORIZED,       // 未授权
    READ_FAILED,        // 读取失败
    SUCCESS_EMPTY,      // 读取成功但无记录
    SUCCESS_IMPORTED,   // 成功导入
    ALREADY_UP_TO_DATE  // 成功同步但数据已是最新
}

data class ImportResult(
    val status: HealthDataSourceStatus,
    val outcome: ImportOutcome,
    val importedCount: Int = 0,
    val skippedCount: Int = 0,
    val conflictCount: Int = 0,
    val sourcePackage: String = "",
    val durationMinutes: Long = 0L,
    val cycleDate: String = "",
    val settlementStatus: SleepStatus? = null,
    val message: String = ""
)

interface SleepDataSource {
    val id: String
    val name: String
    fun getStatus(context: Context): HealthDataSourceStatus
    @Throws(Exception::class)
    fun fetchSleepSessions(context: Context, start: Instant, end: Instant): List<RawSleepRecord>
}

class HealthConnectSleepSource : SleepDataSource {
    override val id: String = "healthconnect"
    override val name: String = "Android Health Connect"

    override fun getStatus(context: Context): HealthDataSourceStatus {
        if (Build.VERSION.SDK_INT < 34) {
            return HealthDataSourceStatus.NOT_SUPPORTED
        }
        val manager = runCatching {
            context.getSystemService(android.health.connect.HealthConnectManager::class.java)
        }.getOrNull() ?: return HealthDataSourceStatus.NOT_SUPPORTED

        val hasPermission = context.checkSelfPermission("android.permission.health.READ_SLEEP") == PackageManager.PERMISSION_GRANTED
        return if (hasPermission) HealthDataSourceStatus.AVAILABLE_CONNECTED else HealthDataSourceStatus.AVAILABLE_NEED_PERMISSION
    }

    override fun fetchSleepSessions(context: Context, start: Instant, end: Instant): List<RawSleepRecord> {
        if (Build.VERSION.SDK_INT < 34) {
            throw IllegalStateException("当前系统版本低于 Android 14 (API 34)，不支持系统级健康连接")
        }
        val manager = runCatching {
            context.getSystemService(android.health.connect.HealthConnectManager::class.java)
        }.getOrNull() ?: throw IllegalStateException("未找到 HealthConnectManager 服务")

        if (context.checkSelfPermission("android.permission.health.READ_SLEEP") != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("未获得健康连接睡眠读取权限（android.permission.health.READ_SLEEP）")
        }

        val results = mutableListOf<RawSleepRecord>()
        val timeFilter = android.health.connect.TimeInstantRangeFilter.Builder()
            .setStartTime(start)
            .setEndTime(end)
            .build()
        val request = android.health.connect.ReadRecordsRequestUsingFilters.Builder(
            android.health.connect.datatypes.SleepSessionRecord::class.java
        )
            .setTimeRangeFilter(timeFilter)
            .setAscending(true)
            .build()

        val future = CompletableFuture<List<android.health.connect.datatypes.SleepSessionRecord>>()
        val executor = Executors.newSingleThreadExecutor()
        try {
            manager.readRecords(
                request,
                executor,
                object : OutcomeReceiver<
                    android.health.connect.ReadRecordsResponse<android.health.connect.datatypes.SleepSessionRecord>,
                    android.health.connect.HealthConnectException
                > {
                    override fun onResult(response: android.health.connect.ReadRecordsResponse<android.health.connect.datatypes.SleepSessionRecord>) {
                        future.complete(response.records)
                    }

                    override fun onError(error: android.health.connect.HealthConnectException) {
                        future.completeExceptionally(error)
                    }
                }
            )
            val records = future.get(5, TimeUnit.SECONDS)

            for (rec in records) {
                val stages = rec.stages.map { stage ->
                    RawSleepStage(
                        startTime = stage.startTime,
                        endTime = stage.endTime,
                        stageType = stage.type
                    )
                }
                // Sleeping stage types: SLEEPING (4), DEEP (5), LIGHT (6), REM (7)
                val sleepingStages = stages.filter { it.stageType in listOf(4, 5, 6, 7) }
                val actualSleepMins = sleepingStages.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                val hasSleepEvidence = sleepingStages.isNotEmpty() && actualSleepMins > 0
                val totalMins = Duration.between(rec.startTime, rec.endTime).toMinutes()

                results.add(
                    RawSleepRecord(
                        id = rec.metadata.id ?: UUID.randomUUID().toString(),
                        sourcePackage = rec.metadata.dataOrigin?.packageName ?: "unknown",
                        startTime = rec.startTime,
                        endTime = rec.endTime,
                        lastModifiedTime = rec.metadata.lastModifiedTime ?: rec.endTime,
                        clientRecordVersion = rec.metadata.clientRecordVersion,
                        stages = stages,
                        isAsleepConfirmed = hasSleepEvidence,
                        actualSleepMinutes = if (hasSleepEvidence) actualSleepMins else totalMins,
                        notes = rec.notes?.toString() ?: ""
                    )
                )
            }
        } finally {
            executor.shutdown()
        }
        return results
    }
}

class MockSleepDataSource(
    var status: HealthDataSourceStatus = HealthDataSourceStatus.AVAILABLE_CONNECTED,
    var shouldThrowOnFetch: Boolean = false,
    var throwExceptionMessage: String = "模拟健康连接服务读取故障"
) : SleepDataSource {
    override val id: String = "mock_health"
    override val name: String = "模拟健康数据源"
    val mockRecords = mutableListOf<RawSleepRecord>()

    override fun getStatus(context: Context): HealthDataSourceStatus = status

    override fun fetchSleepSessions(context: Context, start: Instant, end: Instant): List<RawSleepRecord> {
        if (shouldThrowOnFetch) {
            throw RuntimeException(throwExceptionMessage)
        }
        return mockRecords.filter { it.startTime >= start && it.endTime <= end }
    }
}

object SleepImportManager {
    var activeSource: SleepDataSource = HealthConnectSleepSource()

    fun syncFromAvailableSources(
        context: Context,
        db: Database,
        source: SleepDataSource = activeSource,
        lookbackDays: Long = 7L
    ): ImportResult {
        val status = source.getStatus(context)
        db.setSetting("sleep_datasource_status", status.name)
        if (status != HealthDataSourceStatus.AVAILABLE_CONNECTED) {
            return ImportResult(
                status = status,
                outcome = ImportOutcome.UNAUTHORIZED,
                message = when (status) {
                    HealthDataSourceStatus.AVAILABLE_NEED_PERMISSION -> "需要授权健康连接读取睡眠数据"
                    HealthDataSourceStatus.NOT_SUPPORTED -> "当前设备未支持健康连接"
                    HealthDataSourceStatus.DISABLED -> "健康数据源已停用"
                    else -> "数据源不可用"
                }
            )
        }

        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val start = now.minus(lookbackDays, ChronoUnit.DAYS)
        val rawRecords = try {
            source.fetchSleepSessions(context, start, now)
        } catch (e: Throwable) {
            val rootMsg = e.cause?.message ?: e.message ?: "未知异常"
            // 保留既有睡眠记录和奖惩状态，绝不调用任何变更 DB 的方法
            return ImportResult(
                status = status,
                outcome = ImportOutcome.READ_FAILED,
                message = "读取失败: $rootMsg（已保留既有睡眠记录与奖惩状态）"
            )
        }

        if (rawRecords.isEmpty()) {
            val lastSyncStr = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(LocalDateTime.now())
            db.setSetting("sleep_import_last_sync_time", lastSyncStr)
            return ImportResult(
                status = status,
                outcome = ImportOutcome.SUCCESS_EMPTY,
                importedCount = 0,
                message = "近 $lookbackDays 天未找到有效睡眠记录"
            )
        }

        var imported = 0
        var skipped = 0
        var conflicts = 0
        var lastImportedPackage = ""
        var lastImportedDuration = 0L
        var lastImportedCycleDate = ""
        var lastImportedStatus: SleepStatus? = null

        // Group by wake date
        val byWakeDate = rawRecords.groupBy { rec ->
            rec.endTime.atZone(zone).toLocalDate().toString()
        }

        for ((wakeDate, recordsForDate) in byWakeDate) {
            val existing = db.sleepRecord(wakeDate)

            // 1. Preserve manual corrections and exemptions
            if (existing != null && (existing.source == "manual" || existing.status == SleepStatus.PAUSED)) {
                skipped++
                lastImportedCycleDate = wakeDate
                lastImportedDuration = existing.durationMinutes
                lastImportedStatus = existing.status
                continue
            }

            // 2. Conflict check: multiple distinct records for the same day with different times
            if (recordsForDate.size > 1) {
                val distinctTimes = recordsForDate.map {
                    val bed = it.startTime.atZone(zone).toLocalTime().toString().take(5)
                    val wake = it.endTime.atZone(zone).toLocalTime().toString().take(5)
                    bed to wake
                }.distinct()
                if (distinctTimes.size > 1) {
                    val recorded = db.recordSleepCycle(
                        cycleDate = wakeDate,
                        bedtime = distinctTimes[0].first,
                        wakeTime = distinctTimes[0].second,
                        source = source.id,
                        forcedStatus = SleepStatus.UNVERIFIED,
                        reason = "多条导入记录冲突，待人工核验"
                    )
                    conflicts++
                    lastImportedPackage = recordsForDate.first().sourcePackage
                    lastImportedDuration = recorded.durationMinutes
                    lastImportedCycleDate = wakeDate
                    lastImportedStatus = recorded.status
                    continue
                }
            }

            val primaryRecord = recordsForDate.maxByOrNull { it.lastModifiedTime } ?: continue
            val bedtimeStr = primaryRecord.startTime.atZone(zone).toLocalTime().toString().take(5)
            val wakeTimeStr = primaryRecord.endTime.atZone(zone).toLocalTime().toString().take(5)

            val lastImportedId = db.setting("sleep_import_id_$wakeDate")
            val lastImportedMod = db.setting("sleep_import_last_modified_$wakeDate")

            // 3. Deduplication: same id and same lastModified
            if (lastImportedId == primaryRecord.id && lastImportedMod == primaryRecord.lastModifiedTime.toEpochMilli().toString() && existing != null) {
                skipped++
                lastImportedPackage = primaryRecord.sourcePackage
                lastImportedDuration = existing.durationMinutes
                lastImportedCycleDate = wakeDate
                lastImportedStatus = existing.status
                continue
            }

            // 4. In-bed vs actual asleep
            val autoReason = if (existing != null) "健康连接数据源更新" else ""
            val isInBedOnly = !primaryRecord.isAsleepConfirmed

            val recorded = if (isInBedOnly) {
                // In-bed only cannot prove actual 7h sleep -> UNVERIFIED
                db.recordSleepCycle(
                    cycleDate = wakeDate,
                    bedtime = bedtimeStr,
                    wakeTime = wakeTimeStr,
                    source = source.id,
                    reason = if (autoReason.isNotBlank()) "$autoReason；仅在床区间，待核验" else "仅在床区间，待核验",
                    forcedStatus = SleepStatus.UNVERIFIED
                )
            } else {
                // Has verified actual sleep stages -> normal evaluation (PASS / FAIL)
                db.recordSleepCycle(
                    cycleDate = wakeDate,
                    bedtime = bedtimeStr,
                    wakeTime = wakeTimeStr,
                    reason = autoReason,
                    source = source.id,
                    actualSleepMinutes = primaryRecord.actualSleepMinutes
                )
            }

            db.setSetting("sleep_import_id_$wakeDate", primaryRecord.id)
            db.setSetting("sleep_import_last_modified_$wakeDate", primaryRecord.lastModifiedTime.toEpochMilli().toString())
            imported++
            lastImportedPackage = primaryRecord.sourcePackage
            lastImportedDuration = recorded.durationMinutes
            lastImportedCycleDate = wakeDate
            lastImportedStatus = recorded.status
        }

        val lastSyncStr = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(LocalDateTime.now())
        db.setSetting("sleep_import_last_sync_time", lastSyncStr)

        val outcome = if (imported > 0) ImportOutcome.SUCCESS_IMPORTED else ImportOutcome.ALREADY_UP_TO_DATE
        val statusDesc = when (lastImportedStatus) {
            SleepStatus.PASS -> "达标"
            SleepStatus.FAIL -> "未达标"
            SleepStatus.PAUSED -> "已豁免"
            SleepStatus.UNVERIFIED -> "待核验"
            null -> "未知"
        }
        val durationDesc = "${lastImportedDuration / 60}小时${lastImportedDuration % 60}分"
        val message = if (imported > 0) {
            "成功导入 $imported 条睡眠记录：周期 $lastImportedCycleDate，实际睡眠 $durationDesc，结算 $statusDesc"
        } else if (lastImportedCycleDate.isNotBlank()) {
            "数据已是最新：周期 $lastImportedCycleDate，实际睡眠 $durationDesc，结算 $statusDesc"
        } else {
            "数据已是最新"
        }

        return ImportResult(
            status = status,
            outcome = outcome,
            importedCount = imported,
            skippedCount = skipped,
            conflictCount = conflicts,
            sourcePackage = lastImportedPackage,
            durationMinutes = lastImportedDuration,
            cycleDate = lastImportedCycleDate,
            settlementStatus = lastImportedStatus,
            message = message
        )
    }
}
