package com.focus.prototype

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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

private val Ink = Color(0xff111113)
private val Panel = Color(0xff202023)
private val Red = Color(0xffef5b55)

class MainActivity : ComponentActivity() {
 private var refresh by mutableIntStateOf(0)
 override fun onResume() { super.onResume(); refresh++ }
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
  val db = Database(this); val store = Store(this)
  val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
  val apps = packageManager.queryIntentActivities(launch,0).filter { store.canRestrict(it.activityInfo.packageName) }.distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(packageManager).toString() }
  setContent {
   var page by remember { mutableIntStateOf(0) }; var editor by remember { mutableStateOf(false) }; var selectedTask by remember { mutableStateOf<String?>(null) }
   var targetDay by remember { mutableStateOf("") }; var now by remember { mutableLongStateOf(0L) }
   LaunchedEffect(Unit) { while(true) { now++; delay(1000) } }
   val tick = now + refresh; val today = db.day(); val c = remember(today,tick) { db.contract(today) }; val tasks = remember(today,tick) { db.tasks(today) }
   val protected = remember(tick) { val id = ComponentName(this,FocusService::class.java).flattenToString(); FocusService.connected && Settings.Secure.getString(contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.split(':')?.any { it.equals(id,true) } == true }
   val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
    val task = selectedTask?.let { id -> db.tasks(targetDay).firstOrNull { it.id == id } }
    if(bitmap != null && task != null) { val f = java.io.File(filesDir,"evidence/${task.id}.jpg"); f.parentFile?.mkdirs(); f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,90,it) }; db.evidence(task,f.absolutePath); refresh++ }
   }
   MaterialTheme(colorScheme = darkColorScheme(primary=Red,background=Ink,surface=Panel,onSurface=Color.White)) {
    Scaffold(containerColor=Ink, bottomBar = { NavigationBar(containerColor=Panel) { listOf("今天","奖励","成长","复盘").forEachIndexed { index, label -> NavigationBarItem(selected=page==index,onClick={page=index; editor=false},icon={ Text(listOf("●","◆","◉","▤")[index]) },label={Text(label)}) } } }) { pad ->
     LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal=20.dp), contentPadding=PaddingValues(top=20.dp,bottom=36.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
      if(editor) {
       item { TaskEditor(targetDay, db, onCancel={ editor=false }, onSave={ editor=false; refresh++ }) }
      } else when(page) {
       0 -> {
        item { Header("TODAY / $today", "今天，向前一步") }
        item { CardBlock { Text(if(c.confirmed) "契约已生效" else "今天的契约尚未确认",fontSize=20.sp,fontWeight=FontWeight.Bold); Text("必做 ${tasks.count{it.required && it.done}} / ${tasks.count{it.required}}  ·  选做 ${tasks.count{!it.required && it.done}} / ${tasks.count{!it.required}}",color=Color.LightGray); Text(if(db.eligible()) "必做门槛已达成" else "完成全部必做任务后领取基础娱乐额度",color=Red) } }
        if(!protected) item { CardBlock { Text("保护未生效：请开启无障碍服务",color=Red); Button(onClick={ startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("前往授权") } } }
        if(!c.confirmed) item { ContractEditor(db, c) { refresh++ } }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { Text("今日行动",fontSize=22.sp,fontWeight=FontWeight.Bold); TextButton(onClick={targetDay=today; editor=true}) { Text("＋ 添加任务") } } }
        val continuing = db.runningTasks().filter { it.date != today }
        if(continuing.isNotEmpty()) item { Text("跨日进行中的行动（计入原契约日期）",color=Color.LightGray) }
        items(continuing,key={it.id}) { task -> TaskCard(task,db,onChanged={refresh++},onPhoto={selectedTask=task.id; targetDay=task.date; camera.launch(null)}) }
        if(tasks.isEmpty()) item { Text("还没有任务。添加任务，或确认计划休息日。",color=Color.LightGray) }
        items(tasks,key={it.id}) { task -> TaskCard(task,db,onChanged={refresh++},onPhoto={selectedTask=task.id; targetDay=task.date; camera.launch(null)}) }
       }
       1 -> {
        item { Header("REWARDS / TODAY", "娱乐，由行动开启") }
        item { CardBlock { Text(if(!protected) "PROTECTION OFF · 保护未生效" else if(c.emergency) "EMERGENCY · 今日紧急解锁" else if(!db.eligible()) "LOCKED · 必做未达标" else if(!c.baseClaimed) "ELIGIBLE · 可领取基础额度" else if(db.balance("video")>0) "AVAILABLE · 视频额度可用" else "EXHAUSTED · 视频额度已用完",color=Red); Text("${db.balance("video") / 60000}",fontSize=52.sp,fontWeight=FontWeight.Bold); Text("共享视频分钟  ·  剩余游戏 ${db.balance("game")} 局"); Text("抖音与 B站共用余额；游戏局数仅记录，不自动识别或拦截对局。",color=Color.LightGray); if(c.emergency) TextButton(onClick={db.resumeProtection();refresh++}) {Text("恢复保护")}; if(db.eligible() && !c.baseClaimed) Button(onClick={db.grantBase(); refresh++}) { Text("领取基础额度") }; if(db.balance("game") > 0) TextButton(onClick={db.consume("game",1); refresh++}) { Text("手动记录已使用一局") } } }
        items(tasks.filter{!it.required && it.done && !it.claimed},key={it.id}) { task -> CardBlock { Text("${task.title} · 选择一项奖励"); Row { if(task.rewardMinutes>0) TextButton(enabled=db.eligible(),onClick={db.claim(task,"video");refresh++}) {Text("${task.rewardMinutes} 分钟")}; if(task.rewardGames>0) TextButton(enabled=db.eligible(),onClick={db.claim(task,"game");refresh++}) {Text("${task.rewardGames} 局") } }; if(!db.eligible()) Text("需先完成必做契约",color=Color.LightGray) } }
        item { CardBlock { Text("B站学习窗口",fontSize=18.sp); Text("15 分钟临时放行并记录，不扣视频余额。无法判断实际观看内容。",color=Color.LightGray); Button(enabled=!db.learningActive(),onClick={db.startLearning();refresh++}) {Text(if(db.learningActive()) "学习窗口进行中" else "开启 15 分钟学习窗口")} } }
        item { Text("受限应用",fontSize=21.sp,fontWeight=FontWeight.Bold); Text("只拦截主动选择的应用；系统设置和本应用不会列入。",color=Color.LightGray) }
        items(apps,key={it.activityInfo.packageName}) { app -> val pkg=app.activityInfo.packageName; Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(app.loadLabel(packageManager).toString()); Text(pkg,fontSize=11.sp,color=Color.Gray) }; Checkbox(checked=pkg in store.selected,onCheckedChange={checked -> store.selected=if(checked) store.selected+pkg else store.selected-pkg; refresh++}) } }
       }
       2 -> {
        item { Header("GROWTH / YOUR JOURNEY", "成长看得见") }
        item { CardBlock { val xp=db.xp(); val rank=listOf("启程","萌芽","探索","积累","进阶","坚韧","精进","卓越")[(xp/500).coerceAtMost(7)]; Text("$xp",fontSize=56.sp,fontWeight=FontWeight.Bold); Text("XP  /  等级 ${xp/500+1}  /  $rank",fontSize=18.sp); Text("连续完成 ${db.streak()} 天 · 休息日保留连续记录",color=Color.LightGray); Text("经验来自已验收任务，不可兑换娱乐。",color=Color.LightGray) } }
        item { Text("行动日历",fontSize=21.sp,fontWeight=FontWeight.Bold) }
        items(db.history()) { (day,state) -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(day); Text(state,color=if(state=="休息") Color.LightGray else Red) } }
       }
       3 -> {
        item { Header("REFLECTION / $today", "为今天收个尾") }
        item { CardBlock { Text("今日真实记录",fontSize=20.sp); Text("完成 ${tasks.count{it.done}} / ${tasks.size} 项 · 专注 ${tasks.sumOf{db.elapsed(it)}/60000} 分钟"); tasks.forEach { Text("${if(it.done) "✓" else "○"} ${it.title}  ${db.elapsed(it)/60000} / ${it.minutes} 分钟",color=Color.LightGray) } } }
        item { ReviewEditor(db,today) { refresh++ } }
        item { val tomorrow=LocalDate.parse(today).plusDays(1).toString(); CardBlock { Text("明日计划 · $tomorrow",fontSize=20.sp); db.tasks(tomorrow).forEach { Text("${if(it.required) "必做" else "选做"} · ${it.title}") }; OutlinedButton(onClick={targetDay=tomorrow;editor=true}) {Text("添加明日任务")}; Text("明日契约到当天后确认。",color=Color.LightGray) } }
       }
      }
     }
    }
   }
  }
 }
}

@Composable private fun Header(label:String,title:String) { Column { Text(label,letterSpacing=3.sp,color=Red,fontSize=11.sp); Spacer(Modifier.height(8.dp)); Text(title,fontSize=28.sp,fontWeight=FontWeight.Bold) } }
@Composable private fun CardBlock(content:@Composable ColumnScope.()->Unit) { Column(Modifier.fillMaxWidth().background(Panel,RoundedCornerShape(16.dp)).padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content) }
@Composable private fun NumberField(label:String,value:String,onValue:(String)->Unit) { OutlinedTextField(value,onValueChange={onValue(it.filter(Char::isDigit).take(4))},label={Text(label)},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth()) }

@Composable private fun ContractEditor(db:Database,c:Contract,onChanged:()->Unit) {
 var minutes by remember { mutableStateOf("30") }; var games by remember { mutableStateOf("0") }; var rest by remember { mutableStateOf(false) }
 CardBlock { Text("确认每日契约",fontSize=20.sp); Text("确认后必做任务与验收条件冻结；没有必做时需明确选择休息日。",color=Color.LightGray); NumberField("基础视频分钟",minutes){minutes=it}; NumberField("基础游戏局数",games){games=it}; Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(rest,onCheckedChange={rest=it}); Text("计划休息日（不能有必做）") }; val canConfirm = if(rest) db.tasks(c.date).none{it.required} else db.tasks(c.date).any{it.required}; Button(enabled=canConfirm,onClick={db.confirm(c.date,rest,minutes.toIntOrNull()?:0,games.toIntOrNull()?:0);onChanged()}) { Text("确认并冻结契约") } }
}

@Composable private fun TaskEditor(day:String,db:Database,onCancel:()->Unit,onSave:()->Unit) {
 var title by remember { mutableStateOf("") }; var required by remember { mutableStateOf(!db.contract(day).confirmed) }; var minutes by remember { mutableStateOf("25") }; var mode by remember { mutableStateOf("行动型") }; var checklist by remember { mutableStateOf("") }; var video by remember { mutableStateOf("20") }; var games by remember { mutableStateOf("1") }; var recurrence by remember { mutableStateOf("once") }
 Header("PLAN / $day","添加行动")
 CardBlock {
  OutlinedTextField(title,onValueChange={title=it},label={Text("任务名称")},modifier=Modifier.fillMaxWidth())
  Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(required,onCheckedChange={required=it},enabled=!db.contract(day).confirmed); Text("必做") }
  NumberField("目标分钟",minutes){minutes=it}
  Text("验收类型"); Row { listOf("行动型","成果型","掌握型").forEach { TextButton(onClick={mode=it}) { Text(if(mode==it) "✓ $it" else it) } } }
  OutlinedTextField(checklist,onValueChange={checklist=it},label={Text("预先约定的自检条件")},modifier=Modifier.fillMaxWidth())
  Text("重复："); Row { listOf("once" to "一次", "daily" to "每天", "weekly" to "每周同日").forEach { (v,label) -> TextButton(onClick={recurrence=v}) { Text(if(recurrence==v) "✓$label" else label) } } }
  if(!required) { Text("二选一奖励"); NumberField("视频分钟",video){video=it}; NumberField("游戏局数",games){games=it} }
  Row { Button(enabled=title.isNotBlank() && (!db.contract(day).confirmed || !required),onClick={ db.addTask(day,title,required,minutes.toIntOrNull()?:0,mode,checklist,video.toIntOrNull()?:0,games.toIntOrNull()?:0,recurrence); onSave() }) {Text("保存")}; TextButton(onClick=onCancel){Text("取消")} }
 }
}

@Composable private fun TaskCard(task:Task,db:Database,onChanged:()->Unit,onPhoto:()->Unit) {
 var checking by remember(task.id) { mutableStateOf(false) }; var selfCheck by remember(task.id) { mutableStateOf(false) }; var revoking by remember(task.id) { mutableStateOf(false) }
 CardBlock {
  Text("${if(task.required) "必做" else "选做"}  /  ${task.mode}",color=Red,fontSize=12.sp); Text(task.title,fontSize=20.sp,fontWeight=FontWeight.Bold)
  Text("${db.elapsed(task)/60000} 分 ${db.elapsed(task)/1000%60} 秒 / 目标 ${task.minutes} 分钟   ${if(task.done) "已自检通过" else if(task.runningSince>0) "计时中" else "未完成"}")
  if(task.checklist.isNotBlank()) Text("预设条件：${task.checklist}",color=Color.LightGray)
  if(task.evidence.isNotBlank()) Text("已保存本地照片",color=Color.LightGray)
  if(task.done) { TextButton(onClick={revoking=true}) {Text("撤销误完成")}; if(revoking) { Text("撤销后将按可用余额冲正已领取奖励；已消费的额度无法追回。",color=Color.LightGray); Row { Button(onClick={db.revoke(task);revoking=false;onChanged()}) {Text("确认撤销")}; TextButton(onClick={revoking=false}) {Text("取消")} } } }
  if(!task.done) {
   Row { if(task.runningSince>0) TextButton(onClick={db.pause(task);onChanged()}) {Text("暂停 / 停止")} else TextButton(onClick={ db.runningTasks().forEach{db.pause(it)}; db.start(task);onChanged() }) {Text("开始计时")}; if(task.mode!="行动型") TextButton(onClick=onPhoto){Text("拍照证明")} }
   TextButton(onClick={checking=!checking}) {Text("提交自检")}
   if(checking) { Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(selfCheck,onCheckedChange={selfCheck=it}); Text("我已按预设条件完成${if(task.mode=="掌握型") "独立练习或解释" else ""}") }; val allowed = selfCheck && db.elapsed(task)>=task.minutes*60000L && (task.mode=="行动型" || task.evidence.isNotBlank()); Button(enabled=allowed,onClick={db.complete(task);checking=false;onChanged()}) {Text("确认自检通过")}; Text("自检与照片不代表 AI 或客观真实性核验。",fontSize=12.sp,color=Color.Gray) }
   if(!db.contract(task.date).confirmed) TextButton(onClick={db.removeDraft(task);onChanged()}) { Text("删除草稿任务") }
  }
 }
}

@Composable private fun ReviewEditor(db:Database,day:String,onSaved:()->Unit) {
 val saved = remember(day) { db.readReview(day) }; var problem by remember(day) { mutableStateOf(saved[0]) }; var praise by remember(day) { mutableStateOf(saved[1]) }; var note by remember(day) { mutableStateOf(saved[2]) }
 CardBlock { OutlinedTextField(problem,onValueChange={problem=it},label={Text("遇到的问题")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(praise,onValueChange={praise=it},label={Text("今天值得肯定的地方")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(note,onValueChange={note=it},label={Text("自由记录")},modifier=Modifier.fillMaxWidth()); Button(onClick={db.review(day,problem,praise,note);onSaved()}) {Text("保存复盘")} }
}
