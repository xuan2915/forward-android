package com.focus.prototype
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import java.time.LocalDate
@Composable fun FuturePlan(db:Database,onAdd:(String)->Unit,onChanged:()->Unit) {
 var date by remember {mutableStateOf(LocalDate.parse(db.day()).plusDays(1).toString())};var restConfirm by remember {mutableStateOf(false)}
 var minutes by remember(date){mutableStateOf("30")};var games by remember(date){mutableStateOf("0")}
 Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
  Text("提前安排",style=MaterialTheme.typography.titleLarge)
  OutlinedTextField(date,onValueChange={date=it},label={Text("计划日期 YYYY-MM-DD")})
  val valid=runCatching {LocalDate.parse(date);date>db.day()}.getOrDefault(false)
  if(valid) { val c=db.contract(date);Text(if(c.rest) "已设为休息日" else if(c.confirmed) "计划已确认" else "计划待确认")
   db.tasks(date).forEach { task->Text("${if(task.required) "必做" else "选做"} · ${task.title}");if(!task.done)TaskChanges(task,db,onChanged) }
   OutlinedButton(onClick={onAdd(date)}){Text("添加这一天的任务")}
   if(!c.confirmed){OutlinedTextField(minutes,onValueChange={minutes=it.filter(Char::isDigit).take(4)},label={Text("基础视频分钟")});OutlinedTextField(games,onValueChange={games=it.filter(Char::isDigit).take(4)},label={Text("基础游戏局数")})}
   Button(enabled=!c.confirmed,onClick={restConfirm=true}){Text("设为休息日")}
   Button(enabled=!c.confirmed && db.tasks(date).any{it.required},onClick={db.confirm(date,false,minutes.toIntOrNull()?:0,games.toIntOrNull()?:0);onChanged()}){Text("确认这一天的任务")}
  }
 }
 if(restConfirm)AlertDialog(onDismissRequest={restConfirm=false},title={Text("把 $date 设为休息日？")},text={Text("这一天的必做任务将改为选做。休息日保留连续记录，不增加连续天数。")},confirmButton={TextButton(onClick={db.planRest(date,minutes.toIntOrNull()?:0,games.toIntOrNull()?:0);restConfirm=false;onChanged()}){Text("确认休息日")}},dismissButton={TextButton(onClick={restConfirm=false}){Text("取消")}})
}
