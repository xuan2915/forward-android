package com.focus.prototype

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
internal fun SleepRecordDialog(
    db: Database,
    initialDate: String = db.day(),
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var dateStr by remember { mutableStateOf(initialDate) }
    val existing = remember(dateStr) { db.sleepRecord(dateStr) }
    val targets = remember { db.sleepTargets() }

    var bedtime by remember(dateStr) { mutableStateOf(existing?.bedtime ?: targets[0]) }
    var wakeTime by remember(dateStr) { mutableStateOf(existing?.wakeTime ?: targets[1]) }
    var reason by remember(dateStr) { mutableStateOf(existing?.reason ?: "") }
    var errorMessage by remember { mutableStateOf("") }

    val bedParsed = runCatching { LocalTime.parse(bedtime) }.getOrDefault(LocalTime.of(0, 30))
    val wakeParsed = runCatching { LocalTime.parse(wakeTime) }.getOrDefault(LocalTime.of(7, 30))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更正睡眠记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dateStr,
                    onValueChange = { dateStr = it; errorMessage = "" },
                    label = { Text("日期 (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(context, { _, hour, minute ->
                                bedtime = "%02d:%02d".format(hour, minute)
                                errorMessage = ""
                            }, bedParsed.hour, bedParsed.minute, true).show()
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Column {
                            Text("入睡时间", style = MaterialTheme.typography.labelSmall)
                            Text(bedtime, color = AppColors.Accent)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(context, { _, hour, minute ->
                                wakeTime = "%02d:%02d".format(hour, minute)
                                errorMessage = ""
                            }, wakeParsed.hour, wakeParsed.minute, true).show()
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Column {
                            Text("起床时间", style = MaterialTheme.typography.labelSmall)
                            Text(wakeTime, color = AppColors.Accent)
                        }
                    }
                }

                val duration = runCatching {
                    db.calculateSleepDurationMinutes(dateStr, bedtime, wakeTime)
                }.getOrNull()
                if (duration != null) {
                    val hasAuto = db.hasTrustedAutoSleepRecord(dateStr)
                    val statusHint = if (!hasAuto) " · 待客观证据核验" else if (duration < 420L) " · 不足7小时" else " · 达标"
                    Text(
                        "时长 ${duration / 60}小时${duration % 60}分$statusHint",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!hasAuto) AppColors.Muted else if (duration < 420L) AppColors.Warning else AppColors.Accent
                    )
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it; errorMessage = "" },
                    label = { Text("更正原因（必填）") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage.isNotBlank()) {
                    Text(errorMessage, color = AppColors.Warning, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (reason.isBlank()) {
                    errorMessage = "请填写更正原因"
                    return@Button
                }
                runCatching {
                    db.recordSleepCycle(dateStr, bedtime, wakeTime, reason = reason, source = "manual")
                }.onSuccess {
                    onSaved()
                    onDismiss()
                }.onFailure {
                    errorMessage = it.message ?: "保存失败"
                }
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
internal fun SleepExemptionDialog(
    db: Database,
    initialDate: String = db.day(),
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var dateStr by remember { mutableStateOf(initialDate) }
    var reason by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("申请睡眠豁免") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dateStr,
                    onValueChange = { dateStr = it; errorMessage = "" },
                    label = { Text("日期 (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it; errorMessage = "" },
                    label = { Text("豁免原因") },
                    placeholder = { Text("如突发疾病或紧急加班") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage.isNotBlank()) {
                    Text(errorMessage, color = AppColors.Warning, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                runCatching {
                    db.pauseSleepCycle(dateStr, reason)
                }.onSuccess {
                    onSaved()
                    onDismiss()
                }.onFailure {
                    errorMessage = it.message ?: "申请失败"
                }
            }) {
                Text("确认豁免")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
