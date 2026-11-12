package com.focus.prototype
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun TaskChanges(task:Task,db:Database,onChanged:()->Unit,initialOpen:Boolean=false) {
 var open by remember(task.id) { mutableStateOf(initialOpen) }; var title by remember { mutableStateOf(task.title) }; var minutes by remember { mutableStateOf(task.minutes.toString()) }; var required by remember { mutableStateOf(task.required) }; var mode by remember { mutableStateOf(task.mode) }; var checklist by remember { mutableStateOf(task.checklist) }; var video by remember { mutableStateOf(task.rewardMinutes.toString()) }; var games by remember { mutableStateOf(task.rewardGames.toString()) }; var days by remember { mutableStateOf(db.recurrence(task).removePrefix("days:")) }; var intervalDays by remember { mutableStateOf(db.recurrence(task).removePrefix("interval:").toIntOrNull()?.toString() ?: "3") }; var rule by remember { mutableStateOf(db.recurrence(task)) }; var reason by remember { mutableStateOf("") }; var deleting by remember { mutableStateOf(false) }
 val frozen=db.contract(task.date).confirmed
 var applyFuture by remember { mutableStateOf(false) }
 var confirming by remember {mutableStateOf(false)}
 fun save(){db.revise(task,title,required,minutes.toIntOrNull()?:0,mode,checklist,video.toIntOrNull()?:0,games.toIntOrNull()?:0,rule,reason,deleting,applyFuture);open=false;confirming=false;onChanged()}
 TextButton(onClick={open=!open}) {Text(if(frozen) "变更本次计划" else "编辑任务全部要求")}
 if(open) Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Text("默认修改本次任务 · 可同步更新未确认的未来任务 · 已完成记录保留")
  Row { Checkbox(applyFuture,onCheckedChange={applyFuture=it});Text("同时修改未来周期任务") }
  OutlinedTextField(title,onValueChange={title=it},label={Text("名称")})
  Row { Checkbox(required,onCheckedChange={required=it}); Text("必做任务") }
  OutlinedTextField(minutes,onValueChange={minutes=it.filter(Char::isDigit).take(4)},label={Text("目标分钟")})
  listOf("行动型","成果型","掌握型").forEach { m -> TextButton(onClick={mode=m}) {Text(if(mode==m) "✓ $m" else m)} }
  OutlinedTextField(checklist,onValueChange={checklist=it},label={Text("完成标准")})
  OutlinedTextField(video,onValueChange={video=it.filter(Char::isDigit).take(4)},label={Text(if(required) "完成后视频奖励（分钟）" else "选做视频奖励（分钟）")}); OutlinedTextField(games,onValueChange={games=it.filter(Char::isDigit).take(4)},label={Text(if(required) "完成后游戏奖励（局）" else "选做游戏奖励（局）")})
  Row { TextButton(onClick={rule="once"}){Text("一次")}; TextButton(onClick={rule="daily"}){Text("每天")}; TextButton(onClick={rule="weekly"}){Text("每周同日")} }
  Row { TextButton(onClick={rule="days:$days"}){Text("指定星期")}; TextButton(onClick={rule="interval:${intervalDays.toIntOrNull()?.coerceIn(1,365)?:3}"}){Text(if(rule.startsWith("interval:"))"✓ 自定义间隔" else "自定义间隔")} }
  if(rule.startsWith("days:"))OutlinedTextField(days,onValueChange={days=it.filter{c->c in '1'..'7'||c==','};rule="days:$days"},label={Text("星期：1 到 7，逗号分隔")})
  if(rule.startsWith("interval:"))OutlinedTextField(intervalDays,onValueChange={intervalDays=it.filter(Char::isDigit).take(3);rule="interval:${intervalDays.toIntOrNull()?.coerceIn(1,365)?:1}"},label={Text("每隔几天（1–365）")},singleLine=true)
  val recurrenceLabel=when { rule=="once"->"仅此一次";rule=="daily"->"每天";rule=="weekly"->"每周同日";rule.startsWith("days:")->"指定星期 $days";rule.startsWith("interval:")->"每隔 ${intervalDays} 天";else->"仅此一次" }
  Text("重复 · $recurrenceLabel")
  if(frozen) OutlinedTextField(reason,onValueChange={reason=it},label={Text("变更原因（会保留原要求与时间）")})
  Row { Checkbox(deleting,onCheckedChange={deleting=it});Text("删除本次任务") }
  if(frozen && task.required && (deleting || !required))Text("已完成任务的奖励保留 · 未完成任务变更后不发奖")
  Button(enabled=title.isNotBlank() && (!rule.startsWith("interval:") || (intervalDays.toIntOrNull()?:0) in 1..365) && (!frozen || reason.isNotBlank()),onClick={if(frozen)confirming=true else save()}) {Text(if(frozen) "确认变更并记录" else "保存修改")}
 }
 if(confirming)AlertDialog(onDismissRequest={confirming=false},title={Text("确认修改今天的计划？")},text={Text("原要求、新要求和原因都会保留 · ${if(applyFuture) "同步更新未确认的未来周期任务" else "仅修改本次任务"}")},confirmButton={TextButton(onClick={save()}){Text("确认修改")}},dismissButton={TextButton(onClick={confirming=false}){Text("返回检查")}})
}

@Composable fun DaySettings(db:Database,onChanged:()->Unit) {
    var hour by remember { mutableStateOf(db.setting("boundary", "4")) }
    val nextHour = hour.toIntOrNull()?.takeIf { it in 0..23 }
    QuietSurface {
        Text("奖励额度重置", style = MaterialTheme.typography.titleMedium)
        Text("任务每天 00:00 更新 · 奖励默认 04:00 重置",
            color = AppColors.Muted, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            hour, onValueChange = { hour = it.filter { ch -> ch in '0'..'9' }.take(2) },
            label = { Text("重置时间（0–23 点）") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        SecondaryAction("保存重置时间", {
            nextHour?.let { db.scheduleBoundary(it); onChanged() }
        }, enabled = nextHour != null)
        val pending = db.setting("boundary_effective")
        if (pending.isNotBlank()) {
            Text("生效时间 · $pending", color = AppColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
