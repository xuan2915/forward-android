package com.focus.prototype

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable fun ImportEditor(db:Database, today:String, refresh:Int, onChanged:()->Unit) {
 var text by remember { mutableStateOf(db.setting("import_draft")) }
 var preview by remember { mutableStateOf(emptyList<PlanDraft>()) }
 var selected by remember { mutableStateOf(setOf<String>()) }
 var message by remember { mutableStateOf("") }
 LaunchedEffect(refresh) { val incoming=db.setting("import_draft"); if(incoming.isNotBlank() && incoming!=text) {text=incoming; preview=emptyList()} }
 Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Text("从笔记导入明日任务",style=MaterialTheme.typography.titleLarge)
  Text("从笔记分享文本到这里，或粘贴内容。只整理列表，不判断文字含义；请逐项确认。")
  OutlinedTextField(text,onValueChange={text=it;db.setSetting("import_draft",it);preview=emptyList()},label={Text("粘贴今日总结或明日计划")},modifier=Modifier.fillMaxWidth(),minLines=3)
  OutlinedButton(onClick={preview=PlanImport.parse(text);selected=preview.map{it.title}.toSet();message=if(preview.isEmpty()) "没有找到可导入的任务" else "请确认以下草稿；默认不要求计时，可在任务中继续编辑。"}) {Text("预览任务草稿")}
  if(message.isNotEmpty()) Text(message)
  preview.forEach { draft -> Row { Checkbox(draft.title in selected,onCheckedChange={selected=if(it) selected+draft.title else selected-draft.title}); Text("${if(draft.required) "必做" else "选做"} · ${draft.title}") } }
  val tomorrow=LocalDate.parse(today).plusDays(1).toString()
  if(preview.isNotEmpty()) Button(enabled=selected.isNotEmpty() && !db.contract(tomorrow).confirmed,onClick={db.importPlan(tomorrow,preview.filter{it.title in selected});text="";preview=emptyList();message="已添加到明日任务，请检查后确认计划。";onChanged()}) {Text("确认添加到明天")}
 }
}
