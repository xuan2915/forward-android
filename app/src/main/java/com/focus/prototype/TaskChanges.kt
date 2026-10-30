package com.focus.prototype
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun TaskChanges(task:Task,db:Database,onChanged:()->Unit) {
 var open by remember { mutableStateOf(false) }; var title by remember { mutableStateOf(task.title) }; var minutes by remember { mutableStateOf(task.minutes.toString()) }; var required by remember { mutableStateOf(task.required) }; var mode by remember { mutableStateOf(task.mode) }; var checklist by remember { mutableStateOf(task.checklist) }; var video by remember { mutableStateOf(task.rewardMinutes.toString()) }; var games by remember { mutableStateOf(task.rewardGames.toString()) }; var days by remember { mutableStateOf("1,2,3,4,5") }; var rule by remember { mutableStateOf(db.recurrence(task)) }; var reason by remember { mutableStateOf("") }; var deleting by remember { mutableStateOf(false) }
 val frozen=db.contract(task.date).confirmed
 TextButton(onClick={open=!open}) {Text(if(frozen) "变更本次计划" else "编辑任务全部要求")}
 if(open) Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Text("修改仅影响本次任务；周期设置用于之后的日期。已完成记录保留。")
  OutlinedTextField(title,onValueChange={title=it},label={Text("名称")})
  Row { Checkbox(required,onCheckedChange={required=it}); Text("必做任务") }
  OutlinedTextField(minutes,onValueChange={minutes=it.filter(Char::isDigit).take(4)},label={Text("目标分钟")})
  listOf("行动型","成果型","掌握型").forEach { m -> TextButton(onClick={mode=m}) {Text(if(mode==m) "✓ $m" else m)} }
  OutlinedTextField(checklist,onValueChange={checklist=it},label={Text("完成标准")})
  if(!required) { OutlinedTextField(video,onValueChange={video=it.filter(Char::isDigit).take(4)},label={Text("视频分钟奖励")}); OutlinedTextField(games,onValueChange={games=it.filter(Char::isDigit).take(4)},label={Text("游戏局数奖励")}) }
  Row { TextButton(onClick={rule="once"}){Text("一次")}; TextButton(onClick={rule="daily"}){Text("每天")}; TextButton(onClick={rule="days:$days"}){Text("指定星期")} }
  OutlinedTextField(days,onValueChange={days=it.filter{c->c in '1'..'7'||c==','};rule="days:$days"},label={Text("星期：1 到 7，逗号分隔")})
  Text("重复规则：$rule")
  if(frozen) OutlinedTextField(reason,onValueChange={reason=it},label={Text("变更原因（会保留原要求与时间）")})
  Row { Checkbox(deleting,onCheckedChange={deleting=it});Text("删除本次任务") }
  Button(enabled=title.isNotBlank() && (!frozen || reason.isNotBlank()),onClick={db.revise(task,title,required,minutes.toIntOrNull()?:0,mode,checklist,video.toIntOrNull()?:0,games.toIntOrNull()?:0,rule,reason,deleting);open=false;onChanged()}) {Text(if(frozen) "确认变更并记录" else "保存修改")}
 }
}

@Composable fun DaySettings(db:Database,onChanged:()->Unit) {
 var hour by remember { mutableStateOf(db.setting("boundary","4")) }
 Column { Text("一天从几点开始"); OutlinedTextField(hour,onValueChange={hour=it.filter(Char::isDigit).take(2)},label={Text("0–23 点，默认 4 点")}); Button(enabled=(hour.toIntOrNull()?:-1) in 0..23,onClick={db.scheduleBoundary(hour.toInt());onChanged()}){Text("从下一个自律日生效")}; val pending=db.setting("boundary_effective"); if(pending.isNotBlank()) Text("生效时间：$pending；历史日期与奖励不会重新结算。") }
}
