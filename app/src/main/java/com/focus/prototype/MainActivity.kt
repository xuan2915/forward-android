package com.focus.prototype

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
 private var refresh by mutableIntStateOf(0)
 private var shareVersion by mutableIntStateOf(0)
 private fun receiveText(intent: Intent) { if(intent.action == Intent.ACTION_SEND && intent.type == "text/plain") { val text=intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty(); if(text.isNotBlank()) Database(this).use { it.setSetting("import_draft",text) };intent.action=Intent.ACTION_MAIN;intent.removeExtra(Intent.EXTRA_TEXT) } }
 override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receiveText(intent); shareVersion++; refresh++ }
 override fun onResume() { super.onResume(); refresh++ }
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
  window.statusBarColor=android.graphics.Color.rgb(17,18,20);window.navigationBarColor=android.graphics.Color.rgb(17,18,20);window.decorView.systemUiVisibility=0
  if(Build.VERSION.SDK_INT>=29){window.isNavigationBarContrastEnforced=false;window.isStatusBarContrastEnforced=false}
  receiveText(intent)
  val db = Database(this); val store = Store(this)
  val focusTimer=FocusTimer(db);focusTimer.reconcileBoot()
  val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
  val apps = packageManager.queryIntentActivities(launch,0).filter { store.canRestrict(it.activityInfo.packageName) }.distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(packageManager).toString() }
  setContent {
   var page by remember { mutableIntStateOf(if(db.setting("import_draft").isNotBlank()) 3 else 0) }; var editor by remember { mutableStateOf(false) };var dataVersion by remember {mutableIntStateOf(0)};var rewardHistoryExpanded by remember {mutableStateOf(false)}
   var targetDay by remember { mutableStateOf("") }; var now by remember { mutableLongStateOf(0L) }
   LaunchedEffect(shareVersion) { if(shareVersion>0) { page=3;editor=false } }
   LaunchedEffect(Unit) { while(true) { db.settleToday();focusTimer.advance();if(now%10L==0L)db.checkpoint();now++; delay(1000) } }
   val tick = now + refresh; val today = db.day(); val c = remember(today,tick) { db.contract(today) }; val tasks = remember(today,tick) { db.tasks(today) }
   val protected = remember(tick) { val id = ComponentName(this,FocusService::class.java).flattenToString(); FocusService.connected && Settings.Secure.getString(contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.split(':')?.any { it.equals(id,true) } == true }
   FocusTheme { MotionHost(db.xp(),showToast=page!=2) {
    androidx.activity.compose.BackHandler(enabled=page==5 || editor) {if(editor)editor=false else page=0}
    Scaffold(containerColor=AppColors.Background, bottomBar = { AppNavigation(page){page=it;editor=false} }) { pad ->
     AnimatedContent(targetState=page,transitionSpec={fadeIn(tween(AppMotion.Standard))+slideInHorizontally(tween(AppMotion.Standard)){it/28} togetherWith fadeOut(tween(AppMotion.Short))+slideOutHorizontally(tween(AppMotion.Short)){ -it/36 }},label="page-transition") { screen ->
     LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal=AppSpace.Page), contentPadding=PaddingValues(top=AppSpace.XLarge,bottom=88.dp), verticalArrangement=Arrangement.spacedBy(AppSpace.Large)) {
      if(editor) {
       item { TaskEditor(targetDay, db, onCancel={ editor=false }, onSave={ editor=false; refresh++ }) }
      } else when(screen) {
       0 -> {
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Header(LocalDate.parse(today).format(DateTimeFormatter.ofPattern("M月d日 · E",Locale.CHINA)), "今日任务");TextButton(onClick={page=5}){Text("设置",color=AppColors.Muted)}}; if(db.setting("changed_$today")=="true") Text("今天的计划有调整 · 查看成长记录",color=AppColors.Muted) }; item { ProgressView(tasks.count{it.required && it.done},tasks.count{it.required});GrowthFeedback() }
        item { CardBlock {
         Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("必做 ${tasks.count{it.required && it.done}} / ${tasks.count{it.required}}",style=MaterialTheme.typography.titleMedium);Text("选做 ${tasks.count{!it.required && it.done}} / ${tasks.count{!it.required}}",color=AppColors.Muted) }
         Text(if(c.emergency) "今天已紧急解锁" else if(!db.eligible()) "完成必做后解锁娱乐" else "娱乐时间 · ${db.balance("video")/60000} 分钟",color=AppColors.Accent,style=MaterialTheme.typography.bodyMedium)
        } }
        if(db.setting("gate_changed_$today")=="true") item {Text("今天删减过必做任务，娱乐保持锁定。需要时可以紧急解锁，原因会留在记录中。",color=AppColors.Warning)}
        if(!protected) item { TextButton(onClick={startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}) { Text("应用保护未开启 · 前往授权",color=AppColors.Warning) } }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { Text("今日任务",fontSize=22.sp,fontWeight=FontWeight.Bold); TextButton(onClick={targetDay=today; editor=true}) { Text("＋ 添加任务") } } }
        if(!c.confirmed) item { ContractEditor(db, c) { refresh++ } }
        val continuing = db.runningTasks().filter { it.date != today }
        if(continuing.isNotEmpty()) item { Text("跨日专注 · 计入开始当天，过期奖励不补发",color=Color.LightGray) }
        items(continuing,key={it.id}) { task -> TaskCard(task,db,focusTimer,onFocus={page=4},onChanged={refresh++}) }
        if(tasks.isEmpty()) item { Text("还没有任务。添加任务，或确认计划休息日。",color=Color.LightGray) }
        items(tasks,key={it.id}) { task -> TaskCard(task,db,focusTimer,onFocus={page=4},onChanged={refresh++}) }
       }
       1 -> {
        item { Header("奖励与额度", "娱乐时间") }
        item { CardBlock {
         Text(if(!protected) "保护未开启" else if(c.emergency) "今天已紧急解锁" else if(!db.eligible()) "完成必做任务后解锁" else if(!c.baseClaimed) "今日任务已达标" else if(db.balance("video")>0) "已解锁 · 可以使用" else "今日额度已用完",color=if(db.eligible() && protected) AppColors.Accent else AppColors.Muted,style=MaterialTheme.typography.labelMedium)
         Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
          Column(verticalArrangement=Arrangement.spacedBy(2.dp)) { Text("${db.balance("video") / 60000}",fontSize=38.sp,fontWeight=FontWeight.Light,lineHeight=42.sp); Text("分钟可用",color=AppColors.Muted,style=MaterialTheme.typography.bodySmall) }
          Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(2.dp)) { Text("${db.balance("game")}",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Light); Text("游戏局数",color=AppColors.Muted,style=MaterialTheme.typography.bodySmall) }
         }
         Hairline()
         Text("抖音与 B站共用视频额度。游戏局数保留手动记录，不会把启动次数当作对局。",style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)
         if(c.emergency) TextButton(onClick={db.resumeProtection();refresh++}) {Text("恢复保护")}
         if(db.eligible() && !c.baseClaimed) PrimaryAction("领取今日娱乐时间",{db.grantBase(); refresh++},Modifier.fillMaxWidth())
         if(db.balance("game") > 0) TextButton(onClick={db.consume("game",1); refresh++}) { Text("手动记录已使用一局") }
        } }
        items(tasks.filter{!it.required && it.done && !it.claimed},key={it.id}) { task -> CardBlock { Text("${task.title} · 选择一项奖励"); Row { if(task.rewardMinutes>0) TextButton(enabled=db.eligible(),onClick={db.claim(task,"video");refresh++}) {Text("${task.rewardMinutes} 分钟")}; if(task.rewardGames>0) TextButton(enabled=db.eligible(),onClick={db.claim(task,"game");refresh++}) {Text("${task.rewardGames} 局") } }; if(!db.eligible()) Text("需先完成必做任务",color=Color.LightGray) } }
        item { CardBlock { Text("B站学习窗口",style=MaterialTheme.typography.titleMedium); Text("临时放行 15 分钟，不扣视频额度。",color=AppColors.Muted); PrimaryAction(if(db.learningActive()) "学习窗口进行中" else "开启 15 分钟学习窗口",{db.startLearning();refresh++},enabled=!db.learningActive()) } }
        if(!c.emergency)item {EmergencyUnlock(db){refresh++}}
        item { SecondaryAction("管理受限应用与权限",{page=5}) }
        item { CardBlock { Text("奖励记录",style=MaterialTheme.typography.titleLarge); val records=db.rewardHistory(); if(records.isEmpty()) Text("领取或使用奖励后，记录会保存在这里。",color=AppColors.Muted); (if(rewardHistoryExpanded) records else records.take(4)).forEach { Text(it,style=MaterialTheme.typography.bodySmall,color=AppColors.Muted) }; if(records.size>4) TextButton(onClick={rewardHistoryExpanded=!rewardHistoryExpanded}) { Text(if(rewardHistoryExpanded) "收起记录" else "查看全部 ${records.size} 条记录") } } }
       }
       2 -> {
        item { Header("成长记录", "成长看得见") }
        item { GrowthHero(db) }
        item { GrowthDetails(db) }
       }
       3 -> {
        item { Header("今日总结 · $today", "为今天收个尾") }
        item { CardBlock { Text("今日真实记录",style=MaterialTheme.typography.titleLarge); Text("完成 ${tasks.count{it.done}} / ${tasks.size} 项 · 专注 ${focusDuration(tasks.sumOf{db.elapsed(it)})}"); tasks.forEach { val elapsed=focusDuration(db.elapsed(it)); val duration=if(it.minutes>0) "$elapsed / 目标 ${it.minutes} 分钟" else "专注 $elapsed"; Text("${if(it.done) "✓" else "○"} ${it.title}  ·  $duration",color=AppColors.Muted) } } }
        item { ReviewEditor(db,today,dataVersion) { refresh++ } }
        item { ImportEditor(db,today,refresh) { refresh++ } }
        item { FuturePlan(db,onAdd={targetDay=it;editor=true}){refresh++} }
       }
       4 -> { item { FocusScreen(db,focusTimer,tasks,onTasks={page=0},onChanged={refresh++}) } }
       5 -> {
        item {TextButton(onClick={page=0}){Text("返回今日任务")};Header("本地保存 · 无需账号","设置")}
        item {CardBlock {Text(if(protected) "应用保护已开启" else "应用保护未开启");SecondaryAction("管理无障碍权限",{startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))});Text("可随时在系统设置中关闭。重启后请检查服务状态。",color=AppColors.Muted)}}
        item {DaySettings(db){refresh++}}
        item {BackupView(db){dataVersion++;refresh++}}
        item {Text("受限应用",style=MaterialTheme.typography.titleLarge);Text("已选择 ${store.selected.size} 个应用",color=AppColors.Muted)}
        items(apps,key={it.activityInfo.packageName}) {app->val pkg=app.activityInfo.packageName;Row(Modifier.fillMaxWidth().toggleable(value=pkg in store.selected,onValueChange={checked->store.selected=if(checked)store.selected+pkg else store.selected-pkg;refresh++}),verticalAlignment=Alignment.CenterVertically){Text(app.loadLabel(packageManager).toString(),modifier=Modifier.weight(1f));Checkbox(pkg in store.selected,onCheckedChange=null)}}
       }
      }
     }
    }
   }
  }
 }
}
}

}

@Composable private fun Header(label:String,title:String) { PageHeading(label,title) }
@Composable private fun CardBlock(content:@Composable ColumnScope.()->Unit) { QuietSurface(Modifier.animateContentSize(animationSpec=motionSizeSpec()),content=content) }
@Composable private fun NumberField(label:String,value:String,onValue:(String)->Unit) { OutlinedTextField(value,onValueChange={onValue(it.filter(Char::isDigit).take(4))},label={Text(label)},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth()) }
private fun focusDuration(milliseconds:Long):String = when {
 milliseconds < 60_000 -> "${milliseconds/1_000} 秒"
 milliseconds < 3_600_000 -> "${milliseconds/60_000} 分钟"
 else -> "${milliseconds/3_600_000} 小时 ${(milliseconds%3_600_000)/60_000} 分"
}

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

@Composable private fun TaskCard(task:Task,db:Database,timer:FocusTimer,onFocus:()->Unit,onChanged:()->Unit) {
 val haptic=androidx.compose.ui.platform.LocalHapticFeedback.current
 var expanded by remember(task.id){mutableStateOf(false)}
 var resetting by remember(task.id){mutableStateOf(false)}
 var checking by remember(task.id) { mutableStateOf(false) }; var selfCheck by remember(task.id) { mutableStateOf(false) }; var revoking by remember(task.id) { mutableStateOf(false) }
 Column(Modifier.fillMaxWidth().animateContentSize(animationSpec=motionSizeSpec()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  GritTaskHeader(task){expanded=!expanded}
  Text("${if(task.minutes>0) "${focusDuration(db.elapsed(task))} / 目标 ${task.minutes} 分钟" else "专注 ${focusDuration(db.elapsed(task))}"}   ·   ${if(task.done) "已完成" else if(task.runningSince>0) "计时中" else "未开始"}",color=AppColors.Muted)
  if(!task.done && !expanded) SecondaryAction("开始专注",{if(timer.phase!="ready")timer.finish();timer.begin(task);onFocus();onChanged()})
  TextButton(onClick={expanded=!expanded}) {Text(if(expanded) "收起详情" else "任务详情与证明")}
  androidx.compose.animation.AnimatedVisibility(expanded,enter=androidx.compose.animation.expandVertically(tween(motionMillis()))+fadeIn(tween(motionMillis())),exit=androidx.compose.animation.shrinkVertically(tween(motionMillis()))+fadeOut(tween(motionMillis()))) { Column {
  if(task.checklist.isNotBlank()) Text("完成标准：${task.checklist}",color=AppColors.Muted)
  ProofActions(task,db,onChanged)
  if(task.done && !task.required && !task.claimed){Text("领取奖励 · 选择一项",fontSize=14.sp);Row{if(task.rewardMinutes>0)TextButton(enabled=db.eligible(),onClick={db.claim(task,"video");onChanged()}){Text("${task.rewardMinutes} 视频分钟")};if(task.rewardGames>0)TextButton(enabled=db.eligible(),onClick={db.claim(task,"game");onChanged()}){Text("${task.rewardGames} 游戏局数")}};if(!db.eligible())Text("完成全部必做后可领取",fontSize=12.sp,color=Color.Gray)}
  if(task.claimed)Text("奖励已领取",fontSize=12.sp,color=Color.Gray)
  if(task.done) { TextButton(onClick={revoking=true}) {Text("撤销误完成")}; if(revoking) { Text("撤销后将按可用余额冲正已领取奖励；已消费的额度无法追回。",color=Color.LightGray); Row { Button(onClick={db.revoke(task);revoking=false;onChanged()}) {Text("确认撤销")}; TextButton(onClick={revoking=false}) {Text("取消")} } } }
  if(!task.done) { TaskChanges(task,db,onChanged)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) { if(task.runningSince>0) SecondaryAction("暂停",{if(timer.taskId==task.id && timer.phase=="work")timer.pause() else db.pause(task);onChanged()}) else PrimaryAction("开始专注",{if(timer.phase!="ready")timer.finish();timer.begin(task);onFocus();onChanged()}) }
   if(db.elapsed(task)>0)TextButton(onClick={resetting=true}){Text("重新计时")}
   TextButton(onClick={checking=!checking}) {Text("确认完成")}
   if(checking) { Row(Modifier.toggleable(value=selfCheck,onValueChange={selfCheck=it}),verticalAlignment=Alignment.CenterVertically) { Checkbox(selfCheck,onCheckedChange=null); Text("我已按预设条件完成${if(task.mode=="掌握型") "独立练习或解释" else ""}") }; val allowed = selfCheck && db.elapsed(task)>=task.minutes*60000L && (task.mode=="行动型" || task.evidence.isNotBlank()); Button(enabled=allowed,onClick={db.complete(task);haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress);db.grantBase(task.date);checking=false;onChanged()}) {Text("完成任务")}; Text("请如实自检，照片和附件仅保存在本机。",fontSize=12.sp,color=Color.Gray) }

  }
  } }
 }
 if(resetting)AlertDialog(onDismissRequest={resetting=false},title={Text("重新开始这次专注？")},text={Text("这项任务已计入的专注时长将清零，完成证明会保留。")},confirmButton={TextButton(onClick={db.restart(task);resetting=false;onChanged()}){Text("清零计时")}},dismissButton={TextButton(onClick={resetting=false}){Text("取消")}})
}

@Composable private fun ReviewEditor(db:Database,day:String,version:Int,onSaved:()->Unit) {
 val saved = remember(day,version) { db.readReview(day) }; var problem by remember(day,version) { mutableStateOf(saved[0]) }; var praise by remember(day,version) { mutableStateOf(saved[1]) }; var note by remember(day,version) { mutableStateOf(saved[2]) }
 CardBlock {
  Text("简单回顾，给明天留一个方向。",style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)
  OutlinedTextField(problem,onValueChange={problem=it},label={Text("今天遇到什么阻碍？")},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=3)
  OutlinedTextField(praise,onValueChange={praise=it},label={Text("哪件事做得不错？")},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=3)
  OutlinedTextField(note,onValueChange={note=it},label={Text("其他记录")},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=4)
  PrimaryAction("保存今日总结",{db.review(day,problem,praise,note);onSaved()},Modifier.fillMaxWidth())
 }
}
