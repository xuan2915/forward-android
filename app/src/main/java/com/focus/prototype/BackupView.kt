package com.focus.prototype

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
fun BackupView(db: Database) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf(IndependentBackup.status(context)) }
    var busy by remember { mutableStateOf(false) }

    var location by remember { mutableStateOf(IndependentBackup.selected(context)?.toString()) }
    var lastSuccess by remember { mutableStateOf(IndependentBackup.lastSuccess(context)) }
    val selectDirectory = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) runCatching { IndependentBackup.select(context, uri); location = uri.toString(); message = "已选择位置，请点击立即备份" }
            .onFailure { message = "无法保存目录授权：${it.message}" }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("数据备份", style = MaterialTheme.typography.titleLarge)
        Text("最近成功：${lastSuccess.toLocalDateTimeText()}", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        Text("保存位置：${location?.let { android.net.Uri.parse(it).lastPathSegment } ?: "未选择"}", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = !busy, onClick = { selectDirectory.launch(null) }) { Text(if(location==null) "选择备份位置" else "更换位置") }
            TextButton(enabled = !busy && location != null, onClick = { scope.launch {
                busy = true
                runCatching { withContext(Dispatchers.IO) { IndependentBackup.save(context, db) } }
                    .onSuccess { lastSuccess = IndependentBackup.lastSuccess(context); message = "备份已保存并验证" }
                    .onFailure { IndependentBackup.failed(context, it); message = IndependentBackup.status(context) }
                busy = false
            } }) { Text("立即备份") }
        }
        if (busy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
    }
}

private fun Long.toLocalDateTimeText(): String {
    if (this <= 0L) return "日期未知"
    val formatter = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA)
    return Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(formatter)
}
