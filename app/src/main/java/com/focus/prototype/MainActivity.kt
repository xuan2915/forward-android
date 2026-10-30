package com.focus.prototype

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate

private val Ink = Color(0xff090a0b)
private val Panel = Color(0xff111315)
private val Red = Color(0xffef5b55)

class MainActivity : ComponentActivity() {
 private var refresh by mutableIntStateOf(0)
 private var shareVersion by mutableIntStateOf(0)
 private fun receiveText(intent: Intent) { if(intent.action == Intent.ACTION_SEND && intent.type == "text/plain") { val text=intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty(); if(text.isNotBlank()) Database(this).use { it.setSetting("import_draft",text) };intent.action=Intent.ACTION_MAIN;intent.removeExtra(Intent.EXTRA_TEXT) } }
 override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receiveText(intent); shareVersion++; refresh++ }
 override fun onResume() { super.onResume(); refresh++ }
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
  receiveText(intent)
  val db = Database(this); val store = Store(this)
  val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
  val apps = packageManager.queryIntentActivities(launch,0).filter { store.canRestrict(it.activityInfo.packageName) }.distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(packageManager).toString() }
  setContent {
   var page by remember { mutableIntStateOf(if(db.setting("import_draft").isNotBlank()) 3 else 0) }; var editor by remember { mutableStateOf(false) };var dataVersion by remember {mutableIntStateOf(0)}
   var targetDay by remember { mutableStateOf("") }; var now by remember { mutableLongStateOf(0L) }
   LaunchedEffect(shareVersion) { if(shareVersion>0) { page=3;editor=false } }
   LaunchedEffect(Unit) { while(true) { db.settleToday();if(now%10L==0L)db.checkpoint();now++; delay(1000) } }
   val tick = now + refresh; val today = db.day(); val c = remember(today,tick) { db.contract(today) }; val tasks = remember(today,tick) { db.tasks(today) }
   val protected = remember(tick) { val id = ComponentName(this,FocusService::class.java).flattenToString(); FocusService.connected && Settings.Secure.getString(contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.split(':')?.any { it.equals(id,true) } == true }
   MaterialTheme(colorScheme = darkColorScheme(primary=Color.White,onPrimary=Ink,background=Ink,surface=Panel,onSurface=Color.White,secondary=Color(0xffaeb7bd),outline=Color(0xff3a4045)),shapes=Shapes(small=RoundedCornerShape(10.dp),medium=RoundedCornerShape(16.dp),large=RoundedCornerShape(22.dp))) {
    androidx.activity.compose.BackHandler(enabled=page==4 || editor) {if(editor)editor=false else page=0}
    Scaffold(containerColor=Ink, bottomBar = { AppNavigation(page){page=it;editor=false} }) { pad ->
     LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal=20.dp), contentPadding=PaddingValues(top=20.dp,bottom=36.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
      if(editor) {
       item { TaskEditor(targetDay, db, onCancel={ editor=false }, onSave={ editor=false; refresh++ }) }
      } else when(page) {
       0 -> {
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Header(today, "今日任务");TextButton(onClick={page=4}){Text("设置")}}; if(db.setting("changed_$today")=="true") Text("今日计划已变更，可在成长记录查看原因。",color=Color.Gray) }; item { ProgressView(tasks.count{it.required && it.done},tasks.count{it.required}) }
        item { CardBlock { Text(if(c.confirmed) "今日任务已确认" else "请先确认今日任务",fontSize=20.sp,fontWeight=FontWeight.Bold); Text("必做 ${tasks.count{it.required && it.done}} / ${tasks.count{it.required}}  ·  选做 ${tasks.count{!it.required && it.done}} / ${tasks.count{!it.required}}",color=Color.LightGray); Text(if(!protected) "保护未开启 · ${db.balance("video")/60000} 分钟" else if(c.emergency) "已紧急解锁 · 恢复前不执行拦截" else if(!db.eligible()) "娱乐锁定 · 完成全部必做后解锁" else "娱乐已解锁 · 视频剩余 ${db.balance("video")/60000} 分钟",color=Red) } }
        if(db.setting("gate_changed_$today")=="true") item {Text("今天删减过必做任务，娱乐保持锁定。需要使用时可紧急解锁，记录会保留。",color=Red)}
        if(!protected) item { CardBlock { Text("保护未生效：请开启无障碍服务",color=Red); Button(onClick={ startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("前往授权") } } }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { Text("今日任务",fontSize=22.sp,fontWeight=FontWeight.Bold); TextButton(onClick={targetDay=today; editor=true}) { Text("＋ 添加任务") } } }
        if(!c.confirmed) item { ContractEditor(db, c) { refresh++ } }
        val continuing = db.runningTasks().filter { it.date != today }
        if(continuing.isNotEmpty()) item { Text("跨日专注 · 计入开始当天，过期奖励不补发",color=Color.LightGray) }
        items(continuing,key={it.id}) { task -> TaskCard(task,db,onChanged={refresh++}) }
        if(tasks.isEmpty()) item { Text("还没有任务。添加任务，或确认计划休息日。",color=Color.LightGray) }
        items(tasks,key={it.id}) { task -> TaskCard(task,db,onChanged={refresh++}) }
       }
       1 -> {
        item { Header("REWARDS / TODAY", "娱乐，由行动开启") }
        item { CardBlock { Text(if(!protected) "保护未开启" else if(c.emergency) "今日已紧急解锁" else if(!db.eligible()) "尚未解锁 · 请先完成必做任务" else if(!c.baseClaimed) "已达标 · 可领取基础时间" else if(db.balance("video")>0) "已解锁 · 可以使用视频时间" else "视频时间已用完",color=Red); Text("${db.balance("video") / 60000}",fontSize=52.sp,fontWeight=FontWeight.Bold); Text("共享视频分钟  ·  剩余游戏 ${db.balance("game")} 局"); Text("抖音与 B站共用余额；游戏局数仅记录，不自动识别或拦截对局。",color=Color.LightGray); if(c.emergency) TextButton(onClick={db.resumeProtection();refresh++}) {Text("恢复保护")}; if(db.eligible() && !c.baseClaimed) Button(onClick={db.grantBase(); refresh++}) { Text("领取基础额度") }; if(db.balance("game") > 0) TextButton(onClick={db.consume("game",1); refresh++}) { Text("手动记录已使用一局") } } }
        items(tasks.filter{!it.required && it.done && !it.claimed},key={it.id}) { task -> CardBlock { Text("${task.title} · 选择一项奖励"); Row { if(task.rewardMinutes>0) TextButton(enabled=db.eligible(),onClick={db.claim(task,"video");refresh++}) {Text("${task.rewardMinutes} 分钟")}; if(task.rewardGames>0) TextButton(enabled=db.eligible(),onClick={db.claim(task,"game");refresh++}) {Text("${task.rewardGames} 局") } }; if(!db.eligible()) Text("需先完成必做任务",color=Color.LightGray) } }
        item { CardBlock { Text("B站学习窗口",fontSize=18.sp); Text("15 分钟临时放行并记录，不扣视频余额。无法判断实际观看内容。",color=Color.LightGray); Button(enabled=!db.learningActive(),onClick={db.startLearning();refresh++}) {Text(if(db.learningActive()) "学习窗口进行中" else "开启 15 分钟学习窗口")} } }
        if(!c.emergency)item {EmergencyUnlock(db){refresh++}}
        item { OutlinedButton(onClick={page=4}){Text("管理受限应用与权限")} }
        item { CardBlock { Text("奖励记录",fontSize=20.sp); val records=db.rewardHistory(); if(records.isEmpty()) Text("领取或使用奖励后，记录会保存在这里。",color=Color.Gray); records.forEach { Text(it,fontSize=13.sp,color=Color.LightGray) } } }
       }
       2 -> {
        item { Header("GROWTH / YOUR JOURNEY", "成长看得见") }
        item { GrowthHero(db) }
        item { GrowthDetails(db) }
        items(db.history()) { (day,state) -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(day); Text(state,color=if(state=="休息") Color.LightGray else Red) } }
       }
       3 -> {
        item { Header("REFLECTION / $today", "为今天收个尾") }
        item { CardBlock { Text("今日真实记录",fontSize=20.sp); Text("完成 ${tasks.count{it.done}} / ${tasks.size} 项 · 专注 ${tasks.sumOf{db.elapsed(it)}/60000} 分钟"); tasks.forEach { Text("${if(it.done) "✓" else "○"} ${it.title}  ${db.elapsed(it)/60000} / ${it.minutes} 分钟",color=Color.LightGray) } } }
        item { ReviewEditor(db,today,dataVersion) { refresh++ } }
        item { ImportEditor(db,today,refresh) { refresh++ } }
        item { FuturePlan(db,onAdd={targetDay=it;editor=true}){refresh++} }
       }
       4 -> {
        item {TextButton(onClick={page=0}){Text("返回今日任务")};Header("本地保存 · 无需账号","设置")}
        item {CardBlock {Text(if(protected) "应用保护已开启" else "应用保护未开启");Button(onClick={startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}){Text("管理无障碍权限")};Text("可随时在系统设置中关闭。重启后请检查服务状态。",color=Color.LightGray)}}
        item {DaySettings(db){refresh++}}
        item {BackupView(db){dataVersion++;refresh++}}
        item {Text("受限应用",fontSize=22.sp);Text("已选择 ${store.selected.size} 个应用",color=Color.LightGray)}
        items(apps,key={it.activityInfo.packageName}) {app->val pkg=app.activityInfo.packageName;Row(Modifier.fillMaxWidth().toggleable(value=pkg in store.selected,onValueChange={checked->store.selected=if(checked)store.selected+pkg else store.selected-pkg;refresh++}),verticalAlignment=Alignment.CenterVertically){Text(app.loadLabel(packageManager).toString(),modifier=Modifier.weight(1f));Checkbox(pkg in store.selected,onCheckedChange=null)}}
       }
      }
     }
    }
   }
  }
 }
}

@Composable private fun Header(label:String,title:String) { Column { Text(label,letterSpacing=3.sp,color=Red,fontSize=11.sp); Spacer(Modifier.height(8.dp)); Text(title,fontSize=28.sp,fontWeight=FontWeight.Bold) } }
@Composable private fun CardBlock(content:@Composable ColumnScope.()->Unit) { Column(Modifier.fillMaxWidth().animateContentSize().background(Panel,RoundedCornerShape(16.dp)).padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content) }
@Composable private fun NumberField(label:String,value:String,onValue:(String)->Unit) { OutlinedTextField(value,onValueChange={onValue(it.filter(Char::isDigit).take(4))},label={Text(label)},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth()) }

@Composable private fun ContractEditor(db:Database,c:Contract,onChanged:()->Unit) {
 var minutes by remember { mutableStateOf("30") }; var games by remember { mutableStateOf("0") }; var rest by remember { mutableStateOf(false) }
 CardBlock { Text("确认今日任务",fontSize=20.sp); Text("确认后必做任务与验收条件冻结；没有必做时需明确选择休息日。",color=Color.LightGray); NumberField("基础视频分钟",minutes){minutes=it}; NumberField("基础游戏局数",games){games=it}; Row(Modifier.toggleable(value=rest,onValueChange={rest=it}),verticalAlignment=Alignment.CenterVertically) { Checkbox(rest,onCheckedChange=null); Text("计划休息日（不能有必做）") }; val canConfirm = if(rest) db.tasks(c.date).none{it.required} else db.tasks(c.date).any{it.required}; Button(enabled=canConfirm,onClick={db.confirm(c.date,rest,minutes.toIntOrNull()?:0,games.toIntOrNull()?:0);db.grantBase(c.date);onChanged()}) { Text("确认今日计划") } }
}

@Composable private fun TaskEditor(day:String,db:Database,onCancel:()->Unit,onSave:()->Unit) {
 var title by remember { mutableStateOf("") }; var required by remember { mutableStateOf(!db.contract(day).confirmed) }; var minutes by remember { mutableStateOf("25") }; var mode by remember { mutableStateOf("行动型") }; var checklist by remember { mutableStateOf("") }; var video by remember { mutableStateOf("20") }; var games by remember { mutableStateOf("1") }; var recurrence by remember { mutableStateOf("once") };var weekdays by remember{mutableStateOf(setOf(1,2,3,4,5))}
 Header("PLAN / $day","添加任务")
 CardBlock {
  OutlinedTextField(title,onValueChange={title=it},label={Text("任务名称")},modifier=Modifier.fillMaxWidth())
  Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(required,onCheckedChange={required=it},enabled=!db.contract(day).confirmed); Text("必做") }
  NumberField("目标分钟",minutes){minutes=it}
  Text("验收类型"); Row { listOf("行动型","成果型","掌握型").forEach { TextButton(onClick={mode=it}) { Text(if(mode==it) "✓ $it" else it) } } }
  OutlinedTextField(checklist,onValueChange={checklist=it},label={Text("预先约定的自检条件")},modifier=Modifier.fillMaxWidth())
  Text("重复："); Row { listOf("once" to "一次", "daily" to "每天", "weekly" to "每周同日").forEach { (v,label) -> TextButton(onClick={recurrence=v}) { Text(if(recurrence==v) "✓$label" else label) } } }
  TextButton(onClick={recurrence="days:"+weekdays.sorted().joinToString(",")}){Text(if(recurrence.startsWith("days:")) "✓ 指定星期" else "指定星期")}
  if(recurrence.startsWith("days:"))Row(Modifier.fillMaxWidth()){(1..7).forEach {dayOfWeek->FilterChip(selected=dayOfWeek in weekdays,onClick={weekdays=if(dayOfWeek in weekdays)weekdays-dayOfWeek else weekdays+dayOfWeek;recurrence="days:"+weekdays.sorted().joinToString(",")},label={Text(listOf("一","二","三","四","五","六","日")[dayOfWeek-1])},modifier=Modifier.weight(1f))}}
  if(!required) { Text("二选一奖励"); NumberField("视频分钟",video){video=it}; NumberField("游戏局数",games){games=it} }
  Row { Button(enabled=title.isNotBlank() && recurrence!="days:" && (!db.contract(day).confirmed || !required),onClick={ db.addTask(day,title,required,minutes.toIntOrNull()?:0,mode,checklist,video.toIntOrNull()?:0,games.toIntOrNull()?:0,recurrence); onSave() }) {Text("保存")}; TextButton(onClick=onCancel){Text("取消")} }
 }
}

@Composable private fun TaskCard(task:Task,db:Database,onChanged:()->Unit) {
 var resetting by remember(task.id){mutableStateOf(false)}
 var checking by remember(task.id) { mutableStateOf(false) }; var selfCheck by remember(task.id) { mutableStateOf(false) }; var revoking by remember(task.id) { mutableStateOf(false) }
 CardBlock {
  Text("${if(task.required) "必做" else "选做"}  /  ${task.mode}",color=Red,fontSize=12.sp); Text(task.title,fontSize=20.sp,fontWeight=FontWeight.Bold)
  Text("${db.elapsed(task)/60000} 分 ${db.elapsed(task)/1000%60} 秒 / 目标 ${task.minutes} 分钟   ${if(task.done) "已完成" else if(task.runningSince>0) "计时中" else "未完成"}")
  if(task.checklist.isNotBlank()) Text("预设条件：${task.checklist}",color=Color.LightGray)
  ProofActions(task,db,onChanged)
  if(task.done && !task.required && !task.claimed){Text("领取奖励 · 选择一项",fontSize=14.sp);Row{if(task.rewardMinutes>0)TextButton(enabled=db.eligible(),onClick={db.claim(task,"video");onChanged()}){Text("${task.rewardMinutes} 视频分钟")};if(task.rewardGames>0)TextButton(enabled=db.eligible(),onClick={db.claim(task,"game");onChanged()}){Text("${task.rewardGames} 游戏局数")}};if(!db.eligible())Text("完成全部必做后可领取",fontSize=12.sp,color=Color.Gray)}
  if(task.claimed)Text("奖励已领取",fontSize=12.sp,color=Color.Gray)
  if(task.done) { TextButton(onClick={revoking=true}) {Text("撤销误完成")}; if(revoking) { Text("撤销后将按可用余额冲正已领取奖励；已消费的额度无法追回。",color=Color.LightGray); Row { Button(onClick={db.revoke(task);revoking=false;onChanged()}) {Text("确认撤销")}; TextButton(onClick={revoking=false}) {Text("取消")} } } }
  if(!task.done) { TaskChanges(task,db,onChanged)
   Row { if(task.runningSince>0) TextButton(onClick={db.pause(task);onChanged()}) {Text("暂停 / 停止")} else TextButton(onClick={ db.runningTasks().forEach{db.pause(it)}; db.start(task);onChanged() }) {Text("开始专注")} }
   if(db.elapsed(task)>0)TextButton(onClick={resetting=true}){Text("重新计时")}
   TextButton(onClick={checking=!checking}) {Text("确认完成")}
   if(checking) { Row(Modifier.toggleable(value=selfCheck,onValueChange={selfCheck=it}),verticalAlignment=Alignment.CenterVertically) { Checkbox(selfCheck,onCheckedChange=null); Text("我已按预设条件完成${if(task.mode=="掌握型") "独立练习或解释" else ""}") }; val allowed = selfCheck && db.elapsed(task)>=task.minutes*60000L && (task.mode=="行动型" || task.evidence.isNotBlank()); Button(enabled=allowed,onClick={db.complete(task);db.grantBase(task.date);checking=false;onChanged()}) {Text("完成任务")}; Text("请如实自检，照片和附件仅保存在本机。",fontSize=12.sp,color=Color.Gray) }

  }
 }
 if(resetting)AlertDialog(onDismissRequest={resetting=false},title={Text("重新开始这次专注？")},text={Text("这项任务已计入的专注时长将清零，完成证明会保留。")},confirmButton={TextButton(onClick={db.restart(task);resetting=false;onChanged()}){Text("清零计时")}},dismissButton={TextButton(onClick={resetting=false}){Text("取消")}})
}

@Composable private fun ReviewEditor(db:Database,day:String,version:Int,onSaved:()->Unit) {
 val saved = remember(day,version) { db.readReview(day) }; var problem by remember(day,version) { mutableStateOf(saved[0]) }; var praise by remember(day,version) { mutableStateOf(saved[1]) }; var note by remember(day,version) { mutableStateOf(saved[2]) }
 CardBlock { OutlinedTextField(problem,onValueChange={problem=it},label={Text("遇到的问题")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(praise,onValueChange={praise=it},label={Text("今天值得肯定的地方")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(note,onValueChange={note=it},label={Text("自由记录")},modifier=Modifier.fillMaxWidth()); Button(onClick={db.review(day,problem,praise,note);onSaved()}) {Text("保存复盘")} }
}
