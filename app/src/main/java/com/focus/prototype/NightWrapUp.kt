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
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import java.time.LocalDate

@Composable
internal fun NightWrapUpCard(db: Database, refreshKey: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    var config by remember(refreshKey) { mutableStateOf(db.nightWrapUpConfig()) }
    var message by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    val notificationGranted = NightWrapUpScheduler.notificationPermissionGranted(context)
    val exactGranted = NightWrapUpScheduler.exactAlarmAvailable(context)
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        message = if (granted) "提醒通知已开启" else "通知未授权，定时提醒无法显示"
        NightWrapUpScheduler.reschedule(context, db)
    }

    LaunchedEffect(Unit) { NightWrapUpScheduler.reschedule(context, db) }

    QuietSurface {
        Text("收尾提醒", style = MaterialTheme.typography.titleMedium)
        NightTimeRow("开始收尾", config.windDown) { config = config.copy(windDown = it); message = "" }
        NightTimeRow("停止新工作", config.workCutoff) { config = config.copy(workCutoff = it); message = "" }
        NightTimeRow("准备就寝", config.bedtime) { config = config.copy(bedtime = it); message = "" }
        Text("截止时提醒；可结束或延长一次 30 分钟", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        val validationError = runCatching { config.validate() }.exceptionOrNull()?.message
        if (validationError != null && message.isBlank()) {
            Text(validationError, style = MaterialTheme.typography.bodySmall, color = AppColors.Warning)
        }
        Button(onClick = {
            runCatching {
                config.validate()
                db.saveNightWrapUpConfig(config)
                NightWrapUpScheduler.reschedule(context, db)
            }
                .onSuccess { message = "夜间时间已保存"; isError = false; onChanged() }
                .onFailure { message = it.message ?: "时间顺序不正确"; isError = true }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("保存时间") }
        if (message.isNotBlank()) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = if (isError) AppColors.Warning else AppColors.Accent)
        }
    }

    QuietSurface {
        Text("运行状态", style = MaterialTheme.typography.titleMedium)
        Text("通知 · ${if (notificationGranted) "已开启" else "未授权"}", color = if (notificationGranted) AppColors.Accent else AppColors.Warning)
        Text("精确提醒 · ${if (exactGranted) "已授权" else "未授权，将使用近似提醒"}", color = if (exactGranted) AppColors.Accent else AppColors.Warning)
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
    }
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
