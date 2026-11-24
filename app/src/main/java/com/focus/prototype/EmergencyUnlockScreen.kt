package com.focus.prototype

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EmergencyUnlockScreen(
    db: Database,
    store: Store,
    initialPackage: String? = null,
    onBack: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Tick every second to update countdowns
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            nowMs = System.currentTimeMillis()
        }
    }

    val active = remember(nowMs) { db.activeEmergencyUnlock(nowMs) }
    val candidateApps = remember(store.selected.toSet()) {
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launchIntent, 0)
            .filter { it.activityInfo.packageName in store.selected }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString() }
    }

    var selectedPkg by remember(candidateApps, initialPackage) {
        mutableStateOf(
            if (candidateApps.any { it.activityInfo.packageName == initialPackage }) {
                initialPackage.orEmpty()
            } else {
                candidateApps.firstOrNull()?.activityInfo?.packageName.orEmpty()
            }
        )
    }

    var selectedReasonPreset by remember { mutableStateOf("临时工作需要") }
    var customReason by remember { mutableStateOf("") }
    val effectiveReason = if (selectedReasonPreset == "其他原因") customReason.trim() else selectedReasonPreset

    val stats = remember(nowMs, selectedPkg) {
        db.emergencyUnlockStats(selectedPkg.ifBlank { null }, nowMs)
    }

    // 10-second countdown before confirm button enables
    var countdown by remember { mutableIntStateOf(10) }
    LaunchedEffect(selectedPkg, active != null) {
        countdown = 10
        while (countdown > 0) {
            delay(1000L)
            countdown--
        }
    }

    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
    }
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
    }

    Column(verticalArrangement = Arrangement.spacedBy(AppSpace.Large)) {
        // 1. Active unlock status (if any)
        if (active != null) {
            QuietSurface {
                val remainingSeconds = maxOf(0L, (active.expiresAt - nowMs) / 1000L)
                val mm = remainingSeconds / 60
                val ss = remainingSeconds % 60
                val appLabel = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(active.packageName, 0)).toString()
                }.getOrDefault(active.packageName)

                Text("紧急解锁进行中", style = MaterialTheme.typography.titleMedium, color = AppColors.Accent)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(appLabel, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Normal)
                        Text("原因：${active.reason}", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "%02d:%02d".format(mm, ss),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Light,
                            color = AppColors.Reward
                        )
                        Text("剩余时间", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                    }
                }
                Hairline()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "到期：${timeFormatter.format(Instant.ofEpochMilli(active.expiresAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.Muted
                    )
                    OutlinedButton(
                        onClick = {
                            db.endEmergencyUnlock(active.id, System.currentTimeMillis())
                            onChanged()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Warning)
                    ) {
                        Text("提前结束 / 恢复保护")
                    }
                }
            }
        }

        // 2. Quota Overview
        QuietSurface {
            Text("使用额度", style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("${stats.used24h} / ${stats.limit24h} 次", style = MaterialTheme.typography.titleMedium)
                    Text("滚动 24 小时", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${stats.used168h} / ${stats.limit168h} 次", style = MaterialTheme.typography.titleMedium)
                    Text("滚动 168 小时（7天）", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                }
            }
            if (!stats.canUnlock && active == null) {
                Hairline()
                Text(
                    stats.rejectReason ?: "当前无法申请紧急解锁",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.Warning
                )
            }
        }

        // 3. New Application Form (if no active unlock)
        if (active == null) {
            QuietSurface {
                Text("申请紧急解锁", style = MaterialTheme.typography.titleMedium)

                // Select target app
                Text("目标应用（每次仅限 1 个）", style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
                if (candidateApps.isEmpty()) {
                    Text("当前没有已配置的受限应用，无需紧急解锁", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        candidateApps.forEach { app ->
                            val pkg = app.activityInfo.packageName
                            val label = app.loadLabel(pm).toString()
                            FilterChip(
                                selected = selectedPkg == pkg,
                                onClick = { selectedPkg = pkg },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                Hairline()

                // Reason preset selection
                Text("解锁原因", style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
                val reasons = listOf("临时工作需要", "紧急通信", "处理重要事务", "其他原因")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    reasons.forEach { r ->
                        FilterChip(
                            selected = selectedReasonPreset == r,
                            onClick = { selectedReasonPreset = r },
                            label = { Text(r) }
                        )
                    }
                }
                if (selectedReasonPreset == "其他原因") {
                    OutlinedTextField(
                        value = customReason,
                        onValueChange = { customReason = it },
                        label = { Text("输入具体原因") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Hairline()

                // Consequences Warning
                Text("解锁规则与后果", style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
                Text(
                    "• 单次最多 10 分钟，仅放行选中的单一应用\n" +
                    "• 滚动 24 小时限 1 次，滚动 168 小时限 3 次\n" +
                    "• 不增加 XP，不发放娱乐或游戏时长\n" +
                    "• 重启进程或切换应用不重新获得计时\n" +
                    "• 当日总使用上限已耗尽时无法使用",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.Muted,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(4.dp))

                val isReasonOk = effectiveReason.isNotBlank()
                val canSubmit = stats.canUnlock && selectedPkg.isNotBlank() && isReasonOk && candidateApps.isNotEmpty()

                Button(
                    onClick = {
                        runCatching {
                            db.requestEmergencyUnlock(selectedPkg, effectiveReason, 10, System.currentTimeMillis())
                        }.onSuccess {
                            onChanged()
                            pm.getLaunchIntentForPackage(selectedPkg)?.let { intent ->
                                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    enabled = canSubmit && countdown == 0
                ) {
                    Text(
                        if (countdown > 0 && canSubmit) "确认紧急解锁 (${countdown}s)"
                        else if (!canSubmit && stats.rejectReason != null) stats.rejectReason!!
                        else if (!canSubmit && candidateApps.isEmpty()) "暂无受限应用"
                        else if (!canSubmit && !isReasonOk) "请填写解锁原因"
                        else "确认紧急解锁（10分钟）"
                    )
                }
            }
        }

        // 4. History Log
        val history = remember(nowMs) { db.emergencyUnlockHistory(10) }
        QuietSurface {
            Text("历史记录", style = MaterialTheme.typography.titleMedium)
            if (history.isEmpty()) {
                Text("暂无紧急解锁历史", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
            } else {
                history.forEachIndexed { index, record ->
                    if (index > 0) Hairline()
                    val appLabel = runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(record.packageName, 0)).toString()
                    }.getOrDefault(record.packageName)
                    val statusLabel = when (record.status) {
                        "ACTIVE" -> if (record.expiresAt > nowMs) "进行中" else "已过期"
                        "ENDED" -> "提前结束"
                        "EXPIRED" -> "已到期"
                        else -> record.status
                    }
                    val statusColor = when (statusLabel) {
                        "进行中" -> AppColors.Accent
                        "提前结束" -> AppColors.Muted
                        else -> AppColors.Muted
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(appLabel, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${dateFormatter.format(Instant.ofEpochMilli(record.startedAt))} · 原因：${record.reason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.Muted
                            )
                        }
                        Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor)
                    }
                }
            }
        }
    }
}
