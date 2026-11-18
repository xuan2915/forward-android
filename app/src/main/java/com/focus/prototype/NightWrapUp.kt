package com.focus.prototype

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
internal fun NightWrapUpCard(db: Database, refreshKey: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    var config by remember(refreshKey) { mutableStateOf(db.nightWrapUpConfig()) }
    var message by remember { mutableStateOf("") }
    val notificationGranted = NightWrapUpScheduler.notificationPermissionGranted(context)
    val exactGranted = NightWrapUpScheduler.exactAlarmAvailable(context)
    val focusServiceEnabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?.split(':')?.any { it.equals(android.content.ComponentName(context, FocusService::class.java).flattenToString(), true) } == true
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        message = if (granted) "提醒通知已开启" else "通知未授权，定时提醒无法显示"
        NightWrapUpScheduler.reschedule(context, db)
    }

    LaunchedEffect(Unit) { NightWrapUpScheduler.reschedule(context, db) }

    QuietSurface {
        Text("夜间收尾", style = MaterialTheme.typography.titleLarge)
        Text("按计划提醒与限制娱乐应用，不判断真实睡眠", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
    }

    QuietSurface {
        Text("夜间时间", style = MaterialTheme.typography.titleMedium)
        NightTimeRow("开始收尾", config.windDown) { config = config.copy(windDown = it) }
        NightTimeRow("停止新工作", config.workCutoff) { config = config.copy(workCutoff = it) }
        NightTimeRow("娱乐截止", config.entertainmentCutoff) { config = config.copy(entertainmentCutoff = it) }
        NightTimeRow("准备就寝", config.bedtime) { config = config.copy(bedtime = it) }
        NightTimeRow("早晨解除限制", config.morningRelease) { config = config.copy(morningRelease = it) }
        Text("工作截止时可结束，或延长一次 30 分钟。到时只提醒，不会结束计时或关闭后台应用", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        Text("00:00 任务日期切换与 04:00 娱乐额度重置保持独立", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        Button(onClick = {
            runCatching { db.saveNightWrapUpConfig(config); NightWrapUpScheduler.reschedule(context, db) }
                .onSuccess { message = "夜间时间已保存"; onChanged() }
                .onFailure { message = it.message ?: "时间顺序不正确" }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("保存时间") }
    }

    QuietSurface {
        Text("娱乐限制", style = MaterialTheme.typography.titleMedium)
        Text("截止后拦截已选的抖音、B 站和游戏应用", style = MaterialTheme.typography.bodyMedium)
        Text("B 站学习窗口、紧急放行仍有效；通讯和学习工具不纳入夜间拦截", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        Text("有明确拦截记录才抵扣后续额度，已使用奖励不追回", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        Text("娱乐抵扣上限", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 5, 10, 15, 20).forEach { cap ->
                FilterChip(selected = config.penaltyCapMinutes == cap, onClick = { config = config.copy(penaltyCapMinutes = cap) }, label = { Text("$cap 分") })
            }
        }
        Text("游戏抵扣上限", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 1, 2, 3).forEach { cap ->
                FilterChip(selected = config.penaltyCapGames == cap, onClick = { config = config.copy(penaltyCapGames = cap) }, label = { Text("$cap 局") })
            }
        }
        Text("实际抵扣会在后续奖励记录中列出；权限中断或没有拦截证据时不处罚", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
    }

    QuietSurface {
        Text("运行状态", style = MaterialTheme.typography.titleMedium)
        Text("通知 · ${if (notificationGranted) "已开启" else "未授权"}", color = if (notificationGranted) AppColors.Accent else AppColors.Warning)
        Text("精确提醒 · ${if (exactGranted) "已授权" else "未授权，将使用近似提醒"}", color = if (exactGranted) AppColors.Accent else AppColors.Warning)
        Text("应用保护 · ${if (FocusService.connected && focusServiceEnabled) "服务已连接" else if (focusServiceEnabled) "系统已启用，当前未连接" else "无障碍服务未启用"}", color = if (FocusService.connected && focusServiceEnabled) AppColors.Accent else AppColors.Warning)
        Text("ColorOS 可能限制后台唤醒；服务中断时不会补记为违规", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                when {
                    notificationGranted -> runCatching { context.startActivity(NightWrapUpScheduler.notificationSettingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    Build.VERSION.SDK_INT >= 33 -> notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> message = "请在系统设置中允许向前发送通知"
                }
            }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (notificationGranted) "通知设置" else "开启通知") }
            OutlinedButton(onClick = {
                val intent = NightWrapUpScheduler.exactAlarmSettingsIntent(context)
                if (intent != null) runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                else message = "精确提醒已可用"
            }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (exactGranted) "精确提醒已开" else "授权精确提醒") }
        }
        OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(if (focusServiceEnabled) "检查应用保护服务" else "开启应用保护服务")
        }
    }

    val completedCycle = db.nightWrapUpConfig().latestFinishedCycle(LocalDateTime.now()).toString()
    val recorded = db.nightViolations(completedCycle)
    val pendingMinutes = db.pendingNightPenalty("video", completedCycle) / 60_000L
    val pendingGames = db.pendingNightPenalty("game", completedCycle)
    val videoEvents = recorded.count { it.rewardKind == "video" }
    val gameEvents = recorded.count { it.rewardKind == "game" }
    QuietSurface {
        Text("最近一夜", style = MaterialTheme.typography.titleMedium)
        Text(completedCycle, style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
        Text(if (recorded.isEmpty()) "没有已记录的娱乐拦截" else "娱乐拦截 $videoEvents 次 · 游戏拦截 $gameEvents 次", style = MaterialTheme.typography.bodyLarge)
        if (pendingMinutes > 0 || pendingGames > 0) Text("后续奖励待抵扣 $pendingMinutes 分钟 · $pendingGames 局", color = AppColors.Warning)
        Text("这只是夜间行为记录，不是睡眠时间或入睡证明", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
    }
    if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, color = AppColors.Accent)
}

@Composable
private fun NightTimeRow(label: String, value: String, onValue: (String) -> Unit) {
    val context = LocalContext.current
    val parsed = runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.NOON)
    OutlinedButton(onClick = {
        TimePickerDialog(context, { _, hour, minute -> onValue("%02d:%02d".format(hour, minute)) }, parsed.hour, parsed.minute, true).show()
    }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, color = AppColors.Accent)
    }
}
