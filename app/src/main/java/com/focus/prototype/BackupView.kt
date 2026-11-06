package com.focus.prototype
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
@Composable fun BackupView(db:Database,onChanged:()->Unit) {
 val context=LocalContext.current;var message by remember{mutableStateOf("")};var confirm by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
 val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null) {scope.launch{busy=true;runCatching{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)!!.bufferedWriter().use{it.write(Backup.export(context,db))}}}.onSuccess{message="备份已保存，包含记录和附件"}.onFailure{message="备份失败：${it.message}"};busy=false}}}
 val load=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){scope.launch{busy=true;runCatching{withContext(Dispatchers.IO){val text=context.contentResolver.openInputStream(uri)!!.bufferedReader().use{it.readText()};Backup.restore(context,db,text)}}.onSuccess{message="记录和附件已恢复";onChanged()}.onFailure{message="恢复失败：${it.message}"};busy=false}}}
 Text("本地备份",style=MaterialTheme.typography.titleLarge);TextButton(enabled=!busy,onClick={save.launch("self-discipline-backup.json")}){Text("导出记录与附件")};TextButton(enabled=!busy,onClick={confirm=true}){Text("从备份恢复")};if(busy) LinearProgressIndicator();Text(message)
 if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("用备份替换当前记录？")},text={Text("请先导出当前数据。恢复会替换任务、奖励、睡眠记录和附件，并停止正在进行的计时。")},confirmButton={TextButton(onClick={confirm=false;load.launch(arrayOf("application/json","text/plain"))}){Text("选择备份并恢复")}},dismissButton={TextButton(onClick={confirm=false}){Text("取消")}})
}
