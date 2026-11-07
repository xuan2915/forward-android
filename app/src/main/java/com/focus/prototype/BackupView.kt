package com.focus.prototype

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BackupView(db: Database, onChanged: () -> Unit, onRestored: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var backupText by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val text = Backup.export(context, db)
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
                        ?: error("无法写入所选位置。")
                }
            }.onSuccess { message = "备份已保存，包含任务、记录和附件。" }
                .onFailure { message = "备份失败：${it.message ?: "无法读取数据"}" }
            busy = false
        }
    }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            backupText = null
            preview = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("无法读取所选文件。")
                    text to Backup.inspect(text)
                }
            }.onSuccess { (text, info) ->
                backupText = text
                preview = info
                message = "已读取备份，请核对内容后再恢复。"
            }.onFailure { message = "无法读取备份：${it.message ?: "文件格式错误"}" }
            busy = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("本地备份", style = MaterialTheme.typography.titleLarge)
        Text("导出文件保存在你选择的位置，可用于此设备上的数据恢复。", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = !busy, onClick = { save.launch("向前-本地备份.json") }) { Text("导出数据与附件") }
            TextButton(enabled = !busy, onClick = { load.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("选择备份文件") }
        }
        if (busy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        preview?.let { info ->
            QuietSurface {
                Text("备份内容", style = MaterialTheme.typography.titleMedium)
                Text("${info.createdAt.toLocalDateTimeText()} · 应用 ${info.appVersion} · 格式 ${info.formatVersion}", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                Text(info.integrity, style = MaterialTheme.typography.labelMedium, color = if (info.integrity.startsWith("完整性")) AppColors.Accent else AppColors.Warning)
                Text("任务 ${info.completedTasks}/${info.tasks} 已完成 · ${info.days} 天记录", style = MaterialTheme.typography.bodyMedium)
                Text("奖励账本 ${info.rewardRecords} 条 · 附件 ${info.attachments} 个 · 睡眠记录 ${info.sleepRecords} 天", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                Text("历史经验 ${info.xp} XP · 数据库版本 ${info.databaseVersion}", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                TextButton(enabled = !busy, onClick = { confirmRestore = true }) { Text("确认恢复这份备份") }
                TextButton(enabled = !busy, onClick = { preview = null; backupText = null; message = "已取消选择。" }) { Text("取消") }
            }
        }
    }

    if (confirmRestore && preview != null && backupText != null) {
        val info = preview!!
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("恢复这份备份？") },
            text = { Text("将用 ${info.createdAt.toLocalDateTimeText()} 的数据替换本机当前记录，包含 ${info.tasks} 项任务、${info.rewardRecords} 条奖励记录和 ${info.attachments} 个附件。恢复期间的计时会停止。") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    confirmRestore = false
                    val text = backupText!!
                    scope.launch {
                        busy = true
                        runCatching { withContext(Dispatchers.IO) { Backup.restore(context, db, text) } }
                            .onSuccess {
                                message = "数据和附件已恢复。"
                                backupText = null
                                preview = null
                                onRestored()
                                onChanged()
                            }
                            .onFailure { message = "恢复失败，原有数据保持不变：${it.message ?: "文件无法恢复"}" }
                        busy = false
                    }
                }) { Text("恢复数据") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("返回") } },
        )
    }
}

private fun Long.toLocalDateTimeText(): String {
    if (this <= 0L) return "日期未知"
    val formatter = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA)
    return Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(formatter)
}
