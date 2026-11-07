package com.focus.prototype

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.temporal.ChronoUnit

internal data class HealthSleepImport(
    val start: Instant,
    val end: Instant,
    val sourcePackage: String,
) {
    val date: String get() = end.atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
    val bedtime: String get() = start.atZone(java.time.ZoneId.systemDefault()).toLocalTime().toString().take(5)
    val wakeTime: String get() = end.atZone(java.time.ZoneId.systemDefault()).toLocalTime().toString().take(5)
}

internal val sleepReadPermission = HealthPermission.getReadPermission(SleepSessionRecord::class)

internal suspend fun readHealthSleepSessions(context: Context): List<HealthSleepImport> {
    val client = HealthConnectClient.getOrCreate(context)
    val now = Instant.now()
    val response = client.readRecords(
        ReadRecordsRequest(
            recordType = SleepSessionRecord::class,
            timeRangeFilter = TimeRangeFilter.between(now.minus(45, ChronoUnit.DAYS), now),
            ascendingOrder = false,
        ),
    )
    return response.records.map { HealthSleepImport(it.startTime, it.endTime, it.metadata.dataOrigin.packageName) }
}

internal fun healthSleepSourceLabel(context: Context, packageName: String): String = when (packageName) {
    "com.heytap.health" -> "OPPO 健康（国行）"
    "com.heytap.health.international" -> "OHealth"
    else -> runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)
}
