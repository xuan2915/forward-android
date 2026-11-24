package com.focus.prototype

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime

@Composable
internal fun SleepPage(
    db: Database,
    refreshKey: Int,
    onBack: () -> Unit,
    initialShowRationale: Boolean = false,
    onDismissRationale: (() -> Unit)? = null,
    latestImportResult: ImportResult? = null,
    isSyncing: Boolean = false,
    onRequestSync: (forceAfterCurrent: Boolean) -> Unit = {},
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val targets = remember(refreshKey) { db.sleepTargets() }
    var targetBed by remember(targets) { mutableStateOf(targets[0]) }
    var targetWake by remember(targets) { mutableStateOf(targets[1]) }
    var sleepTargetMessage by remember { mutableStateOf("") }
    var sleepTargetIsError by remember { mutableStateOf(false) }
    var restrictionConfig by remember(refreshKey) { mutableStateOf(db.nightWrapUpConfig()) }
    var restrictionMessage by remember { mutableStateOf("") }
    var restrictionIsError by remember { mutableStateOf(false) }

    var showRecordDialog by remember { mutableStateOf(false) }
    var recordDialogDate by remember { mutableStateOf<String?>(null) }
    var showExemptionDialog by remember { mutableStateOf(false) }
    var showRationaleDialog by remember(initialShowRationale) { mutableStateOf(initialShowRationale) }
    var lastImportResult by remember { mutableStateOf<ImportResult?>(null) }

    LaunchedEffect(latestImportResult) {
        latestImportResult?.let {
            lastImportResult = it
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) onRequestSync(true)
        onChanged()
    }

    val requestHealthPermission = {
        runCatching {
            permissionLauncher.launch("android.permission.health.READ_SLEEP")
        }.onFailure {
            if (Build.VERSION.SDK_INT >= 34) {
                val intent = Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS")
                    .putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
                runCatching { context.startActivity(intent) }.onFailure {
                    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:${context.packageName}"))
                    context.startActivity(fallback)
                }
            }
        }
    }

    val todayCycle = db.day()
    val todayRecord = remember(refreshKey) { db.sleepRecord(todayCycle) }
    val todayStatus = remember(refreshKey) { db.sleepStatus(todayCycle) }
    val currentLevel = remember(refreshKey) { db.sleepLevel(todayCycle) }
    val todayPrepTime = remember(refreshKey) { db.sleepPrepareTime(todayCycle) }

    val yesterdayCycle = remember(todayCycle) {
        runCatching { LocalDate.parse(todayCycle).minusDays(1).toString() }.getOrDefault("")
    }
    val yesterdayRecord = remember(refreshKey, yesterdayCycle) {
        if (yesterdayCycle.isNotBlank()) db.sleepRecord(yesterdayCycle) else null
    }
    val yesterdayStatus = remember(refreshKey, yesterdayCycle) {
        if (yesterdayCycle.isNotBlank()) db.sleepStatus(yesterdayCycle) else SleepStatus.UNVERIFIED
    }
    val yesterdayOverdue = remember(refreshKey, yesterdayCycle) {
        if (yesterdayCycle.isNotBlank()) db.isSleepUnverifiedOverdue(yesterdayCycle) else false
    }

    // Health Connect status
    val dsStatus = remember(refreshKey) { SleepImportManager.activeSource.getStatus(context) }
    val lastSyncTime = remember(refreshKey) { db.setting("sleep_import_last_sync_time") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // ── 1. 当前周期状态（首屏） ──
        QuietSurface {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(todayCycle, style = MaterialTheme.typography.titleLarge)
                val (statusLabel, statusColor) = when (todayStatus) {
                    SleepStatus.PASS -> "达标" to AppColors.Accent
                    SleepStatus.FAIL -> "未达标" to AppColors.Warning
                    SleepStatus.PAUSED -> "已豁免" to AppColors.Reward
                    SleepStatus.UNVERIFIED -> "待核验" to AppColors.Muted
                }
                Text(statusLabel, style = MaterialTheme.typography.titleMedium, color = statusColor)
            }

            if (todayRecord != null) {
                Text(
                    "${todayRecord.bedtime} ~ ${todayRecord.wakeTime} · 时长 ${todayRecord.durationMinutes / 60}小时${todayRecord.durationMinutes % 60}分",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.Muted
                )
            }

            if (currentLevel > 0) {
                Text("违规等级 $currentLevel", style = MaterialTheme.typography.bodyMedium, color = AppColors.Warning)
            }

            val nextDayVideoPenalty = db.pendingSleepPenalty("video") / 60_000L
            val nextDayGamePenalty = db.pendingSleepPenalty("game")
            if (nextDayVideoPenalty > 0 || nextDayGamePenalty > 0) {
                Text(
                    "待抵扣娱乐 $nextDayVideoPenalty 分钟" +
                            (if (nextDayGamePenalty > 0) "、$nextDayGamePenalty 局" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.Warning
                )
            }
        }

        // ── 2. 待核验提醒（仅在超24小时未核验时显示） ──
        if (yesterdayRecord == null && yesterdayCycle.isNotBlank() && yesterdayOverdue) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("昨日睡眠待核验", style = MaterialTheme.typography.titleSmall, color = AppColors.Warning)
                    }
                    TextButton(onClick = {
                        db.markSleepReminded(yesterdayCycle)
                        onChanged()
                    }) {
                        Text("知道了")
                    }
                }
            }
        }

        // ── 3. 健康数据源与同步 ──
        QuietSurface {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusText = when {
                    lastImportResult?.outcome == ImportOutcome.READ_FAILED -> "● 读取失败"
                    dsStatus == HealthDataSourceStatus.AVAILABLE_CONNECTED -> "● 健康连接已连接"
                    dsStatus == HealthDataSourceStatus.AVAILABLE_NEED_PERMISSION -> "▲ 健康连接未授权"
                    dsStatus == HealthDataSourceStatus.NOT_SUPPORTED -> "○ 系统不支持"
                    dsStatus == HealthDataSourceStatus.DISABLED -> "○ 已停用"
                    else -> "○ 未知"
                }
                val statusColor = when {
                    lastImportResult?.outcome == ImportOutcome.READ_FAILED -> AppColors.Warning
                    dsStatus == HealthDataSourceStatus.AVAILABLE_CONNECTED -> AppColors.Accent
                    dsStatus == HealthDataSourceStatus.AVAILABLE_NEED_PERMISSION -> AppColors.Warning
                    else -> AppColors.Muted
                }
                Text(statusText, style = MaterialTheme.typography.titleSmall, color = statusColor)

                if (lastSyncTime.isNotBlank()) {
                    Text("上次同步 $lastSyncTime", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                }
            }

            if (dsStatus == HealthDataSourceStatus.AVAILABLE_NEED_PERMISSION) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { requestHealthPermission() },
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                    ) {
                        Text("授权健康读取")
                    }
                    OutlinedButton(
                        onClick = { showRationaleDialog = true },
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                    ) {
                        Text("权限说明")
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onRequestSync(false)
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        enabled = !isSyncing
                    ) {
                        Text(if (isSyncing) "同步中" else "立即同步")
                    }
                    OutlinedButton(
                        onClick = { showRationaleDialog = true },
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                    ) {
                        Text("权限说明")
                    }
                }
            }

            val currentOutcome = lastImportResult?.outcome
            when (currentOutcome) {
                ImportOutcome.READ_FAILED -> {
                    Text(
                        lastImportResult?.message ?: "读取失败",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.Warning
                    )
                }
                ImportOutcome.SUCCESS_EMPTY -> {
                    Text(
                        "近 7 天无新记录，可检查手表手环是否已同步",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.Muted
                    )
                }
                ImportOutcome.SUCCESS_IMPORTED, ImportOutcome.ALREADY_UP_TO_DATE -> {
                    val res = lastImportResult!!
                    val statusText = when (res.settlementStatus) {
                        SleepStatus.PASS -> "达标"
                        SleepStatus.FAIL -> "未达标"
                        SleepStatus.PAUSED -> "已豁免"
                        SleepStatus.UNVERIFIED -> "待核验"
                        null -> ""
                    }
                    val statusColor = when (res.settlementStatus) {
                        SleepStatus.PASS -> AppColors.Accent
                        SleepStatus.FAIL -> AppColors.Warning
                        SleepStatus.PAUSED -> AppColors.Reward
                        else -> AppColors.Muted
                    }
                    val title = if (res.outcome == ImportOutcome.SUCCESS_IMPORTED) "已导入" else "已是最新"
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("$title ${res.cycleDate} · 实际睡眠 ${res.durationMinutes / 60}小时${res.durationMinutes % 60}分", style = MaterialTheme.typography.bodySmall)
                        if (statusText.isNotBlank()) {
                            Text(statusText, style = MaterialTheme.typography.bodySmall, color = statusColor)
                        }
                    }
                }
                else -> {}
            }
        }

        // ── 4. 历史记录 ──
        val historyRecords = remember(refreshKey) {
            val list = mutableListOf<SleepRecord>()
            db.readableDatabase.rawQuery("SELECT key FROM settings WHERE key GLOB 'sleep_record_*'", null).use { c ->
                while (c.moveToNext()) {
                    val d = c.getString(0).removePrefix("sleep_record_")
                    val rec = db.sleepRecord(d)
                    if (rec != null) list.add(rec)
                }
            }
            list.sortedByDescending { it.date }
        }

        if (historyRecords.isNotEmpty()) {
            QuietSurface {
                Text("历史记录", style = MaterialTheme.typography.titleMedium)

                historyRecords.take(10).forEach { rec ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                recordDialogDate = rec.date
                                showRecordDialog = true
                            }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(rec.date, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.width(8.dp))
                                val sourceLabel = when {
                                    rec.status == SleepStatus.PAUSED -> "豁免"
                                    rec.version > 1 -> "更正"
                                    rec.source.startsWith("health") -> "同步"
                                    else -> "更正"
                                }
                                Text("[$sourceLabel]", style = MaterialTheme.typography.labelSmall, color = AppColors.Muted)
                            }
                            val userReason = if (rec.source == "manual" || rec.status == SleepStatus.PAUSED) rec.reason else ""
                            Text(
                                "${rec.bedtime} ~ ${rec.wakeTime} · ${rec.durationMinutes / 60}小时${rec.durationMinutes % 60}分" +
                                        (if (userReason.isNotBlank()) " · $userReason" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.Muted
                            )
                        }
                        val (badgeText, badgeColor) = when (rec.status) {
                            SleepStatus.PASS -> "达标" to AppColors.Accent
                            SleepStatus.FAIL -> "未达标" to AppColors.Warning
                            SleepStatus.PAUSED -> "豁免" to AppColors.Reward
                            SleepStatus.UNVERIFIED -> "待核验" to AppColors.Muted
                        }
                        Text(badgeText, style = MaterialTheme.typography.labelMedium, color = badgeColor)
                    }
                }
            }
        }

        // ── 6. 睡眠目标配置（低频设置，放底部） ──
        QuietSurface {
            Text("睡眠目标", style = MaterialTheme.typography.titleMedium)

            SleepTimeRow("目标就寝", targetBed) { targetBed = it; sleepTargetMessage = "" }
            SleepTimeRow("目标起床", targetWake) { targetWake = it; sleepTargetMessage = "" }

            val targetWindow = db.sleepWindowMinutes(targetBed, targetWake)
            Text(
                "目标时长 ${targetWindow / 60}小时${targetWindow % 60}分" +
                        (if (targetWindow < 420L) "（不足 7 小时）" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = if (targetWindow < 420L) AppColors.Warning else AppColors.Accent
            )

            Button(
                onClick = {
                    runCatching {
                        db.saveSleepTargets(targetBed, targetWake)
                    }.onSuccess {
                        sleepTargetMessage = "已保存"
                        sleepTargetIsError = false
                        onChanged()
                    }.onFailure {
                        sleepTargetMessage = it.message ?: "目标睡眠窗口不足 7 小时"
                        sleepTargetIsError = true
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text("保存目标")
            }

            if (sleepTargetMessage.isNotBlank()) {
                Text(
                    sleepTargetMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sleepTargetIsError) AppColors.Warning else AppColors.Accent
                )
            }
        }

        // ── 6. 就寝准备 ──
        QuietSurface {
            Text("就寝准备", style = MaterialTheme.typography.titleMedium)

            if (todayPrepTime != null) {
                Text("准备时间 $todayPrepTime", style = MaterialTheme.typography.bodyMedium, color = AppColors.Accent)
            }

            Button(
                onClick = {
                    db.prepareSleep()
                    onChanged()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
            ) {
                Text(if (todayPrepTime != null) "更新就寝准备" else "记录就寝准备")
            }
        }

        // ── 7. 人工更正与豁免 ──
        QuietSurface {
            Text("人工更正与豁免", style = MaterialTheme.typography.titleMedium)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        recordDialogDate = if (yesterdayRecord == null && yesterdayCycle.isNotBlank()) yesterdayCycle else todayCycle
                        showRecordDialog = true
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text("更正记录")
                }
                OutlinedButton(
                    onClick = { showExemptionDialog = true },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text("申请豁免")
                }
            }
        }

        QuietSurface {
            Text("受限应用时段", style = MaterialTheme.typography.titleMedium)
            SleepTimeRow("娱乐截止", restrictionConfig.entertainmentCutoff) {
                restrictionConfig = restrictionConfig.copy(entertainmentCutoff = it)
                restrictionMessage = ""
            }
            SleepTimeRow("早晨解除限制", restrictionConfig.morningRelease) {
                restrictionConfig = restrictionConfig.copy(morningRelease = it)
                restrictionMessage = ""
            }
            Button(
                onClick = {
                    runCatching {
                        restrictionConfig.validate()
                        db.saveNightWrapUpConfig(restrictionConfig)
                        NightWrapUpScheduler.reschedule(context, db)
                    }.onSuccess {
                        restrictionMessage = "已保存"
                        restrictionIsError = false
                        onChanged()
                    }.onFailure {
                        restrictionMessage = it.message ?: "保存失败"
                        restrictionIsError = true
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text("保存时段")
            }
            if (restrictionMessage.isNotBlank()) {
                Text(
                    restrictionMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (restrictionIsError) AppColors.Warning else AppColors.Accent
                )
            }
        }
    }

    if (showRecordDialog && recordDialogDate != null) {
        SleepRecordDialog(
            db = db,
            initialDate = recordDialogDate!!,
            onDismiss = { showRecordDialog = false; recordDialogDate = null },
            onSaved = { showRecordDialog = false; recordDialogDate = null; onChanged() }
        )
    }

    if (showExemptionDialog) {
        SleepExemptionDialog(
            db = db,
            initialDate = todayCycle,
            onDismiss = { showExemptionDialog = false },
            onSaved = { showExemptionDialog = false; onChanged() }
        )
    }

    if (showRationaleDialog) {
        SleepPermissionRationaleDialog(
            onDismiss = {
                showRationaleDialog = false
                onDismissRationale?.invoke()
            },
            onRequestPermission = if (dsStatus == HealthDataSourceStatus.AVAILABLE_NEED_PERMISSION) {
                {
                    showRationaleDialog = false
                    onDismissRationale?.invoke()
                    requestHealthPermission()
                }
            } else null
        )
    }
}

@Composable
internal fun SleepPermissionRationaleDialog(
    onDismiss: () -> Unit,
    onRequestPermission: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("健康连接读取权限说明", style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "用于从健康连接读取入睡、起床时间与睡眠阶段，自动核验睡眠目标",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "数据仅在本地处理，不上传任何外部服务器",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "可随时在系统设置中撤销授权，撤销后保留所有既有历史记录",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            if (onRequestPermission != null) {
                Button(onClick = {
                    onDismiss()
                    onRequestPermission()
                }) {
                    Text("前往授权")
                }
            } else {
                Button(onClick = onDismiss) {
                    Text("我知道了")
                }
            }
        },
        dismissButton = {
            if (onRequestPermission != null) {
                TextButton(onClick = onDismiss) {
                    Text("暂不授权")
                }
            }
        }
    )
}

@Composable
private fun SleepTimeRow(label: String, value: String, onValue: (String) -> Unit) {
    val context = LocalContext.current
    val parsed = runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.NOON)
    OutlinedButton(
        onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute -> onValue("%02d:%02d".format(hour, minute)) },
                parsed.hour,
                parsed.minute,
                true
            ).show()
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, color = AppColors.Accent)
    }
}
