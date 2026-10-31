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
import androidx.compose.foundation.lazy.itemsIndexed
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
  window.useDarkSystemBars()
  receiveText(intent)
  val db = Database(this); val store = Store(this)
  val focusTimer=FocusTimer(db);focusTimer.reconcileBoot()
  val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
  val apps = packageManager.queryIntentActivities(launch,0).filter { store.canRestrict(it.activityInfo.packageName) }.distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(packageManager).toString() }
  setContent {
    var page by remember { mutableIntStateOf(if(db.setting("import_draft").isNotBlank()) 3 else 0) }; var editor by remember { mutableStateOf(false) };var dataVersion by remember {mutableIntStateOf(0)};var rewardHistoryExpanded by remember {mutableStateOf(false)};var taskFilter by remember { mutableIntStateOf(0) }
    var targetDay by remember { mutableStateOf("") }; var now by remember { mutableLongStateOf(0L) }; var taskDetailsId by remember { mutableStateOf<String?>(null) }; var gritAnalyticsTaskId by remember { mutableStateOf<String?>(null) }; var showContractDialog by remember { mutableStateOf(false) }
   LaunchedEffect(shareVersion) { if(shareVersion>0) { page=3;editor=false } }
   LaunchedEffect(Unit) { while(true) { db.settleToday();focusTimer.advance();if(now%10L==0L)db.checkpoint();now++; delay(1000) } }
   val tick = now + refresh; val today = db.day(); val c = remember(today,tick) { db.contract(today) }; val tasks = remember(today,tick) { db.tasks(today) }
   val protected = remember(tick) { val id = ComponentName(this,FocusService::class.java).flattenToString(); FocusService.connected && Settings.Secure.getString(contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.split(':')?.any { it.equals(id,true) } == true }
   FocusTheme { MotionHost(db.xp(),showToast=page!=2) {
       androidx.activity.compose.BackHandler(enabled=gritAnalyticsTaskId!=null || page==5 || editor || showContractDialog) {
        when { showContractDialog -> showContractDialog=false; gritAnalyticsTaskId!=null -> gritAnalyticsTaskId=null; editor -> editor=false; else -> page=0 }
       }
     Scaffold(containerColor=AppColors.Background, bottomBar = { if(taskDetailsId==null && gritAnalyticsTaskId==null) AppNavigation(page){page=it;editor=false;gritAnalyticsTaskId=null} }) { pad ->
     Box(Modifier.fillMaxSize()) {
      AnimatedContent(targetState=page,transitionSpec={fadeIn(tween(AppMotion.Standard))+slideInHorizontally(tween(AppMotion.Standard)){it/28} togetherWith fadeOut(tween(AppMotion.Short))+slideOutHorizontally(tween(AppMotion.Short)){ -it/36 }},label="page-transition") { screen ->
      if (!editor && screen == 0) {
       GritTaskListPage(
        modifier=Modifier.fillMaxSize().padding(pad), date=today, tasks=tasks,
        continuing=db.runningTasks().filter { it.date != today }, filter=taskFilter,
        confirmed=c.confirmed,
        onFilter={taskFilter=it}, onAdd={targetDay=today;editor=true},
        onPlan={showContractDialog=true}, onSettings={page=5},
        onDetails={taskDetailsId=it.id},
       )
      } else if (!editor && screen == 2 && gritAnalyticsTaskId != null) {
       GritHabitAnalyticsPage(db,gritAnalyticsTaskId!!,refresh, Modifier.padding(pad)) { gritAnalyticsTaskId=null }
      } else {
     val listState=remember(screen){androidx.compose.foundation.lazy.LazyListState()}
     LazyColumn(state=listState,modifier=Modifier.fillMaxSize().padding(pad).padding(horizontal=AppSpace.Page), contentPadding=PaddingValues(top=AppSpace.XLarge,bottom=88.dp), verticalArrangement=Arrangement.spacedBy(AppSpace.Large)) {
      if(editor) {
       item { TaskEditor(targetDay, db, onCancel={ editor=false }, onSave={ editor=false; refresh++ }) }
      } else when(screen) {
       0 -> {
         val required=tasks.filter { it.required }; val optional=tasks.filterNot { it.required }
         item { GritTodayHeader(LocalDate.parse(today).format(DateTimeFormatter.ofPattern("M月d日 · E",Locale.CHINA)),tasks.count{it.done},tasks.size){page=5} }
         item { GritTaskFilters(taskFilter){taskFilter=it} }
         item { GritEntertainmentBanner(protected,c.emergency,db.eligible(),db.balance("video")){if(!protected)startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) else page=1} }
         if(db.setting("changed_$today")=="true") item {Text("今天的计划有调整 · 查看成长记录",color=AppColors.Muted)}
         if(db.setting("gate_changed_$today")=="true") item {Text("今天修改过必做任务，娱乐保持锁定。变更已记录。",color=AppColors.Warning)}
        if(!c.confirmed) item { ContractEditor(db, c) { refresh++ } }
        val continuing = db.runningTasks().filter { it.date != today }
        if(continuing.isNotEmpty()) item { Text("跨日专注 · 计入开始当天，过期奖励不补发",color=Color.LightGray) }
          items(continuing,key={it.id}) { task -> TaskCard(task,db,focusTimer,onFocus={page=4},onDetails={taskDetailsId=task.id},onChanged={refresh++}) }
         val sections=when(taskFilter) {
          1 -> listOf("必做任务" to required)
          2 -> listOf("选做任务" to optional)
          else -> listOf("必做任务" to required,"选做任务" to optional)
         }
         sections.forEach { (title,itemsForSection) ->
          if(itemsForSection.isNotEmpty() || taskFilter!=0) {
           item(key="grit-section-$taskFilter-$title") { GritTaskSectionHeader(title,itemsForSection.count{it.done},itemsForSection.size){targetDay=today;editor=true} }
            if(itemsForSection.isNotEmpty()) itemsIndexed(itemsForSection,key={_,task->task.id}) { index,task -> TaskCard(task,db,focusTimer,onFocus={page=4},onDetails={taskDetailsId=task.id},onChanged={refresh++},index=index,count=itemsForSection.size) }
          }
         }
         if(tasks.isEmpty()) item { GritEmptyTasks(if(c.rest) "今天是休息日。任务记录仍会保留在成长日历中。" else "还没有任务，添加一项今天要完成的事。") }
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
      if(showContractDialog) androidx.compose.ui.window.Dialog(onDismissRequest={showContractDialog=false}) {
       Surface(shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
         ContractEditor(db,c) { showContractDialog=false;refresh++ }
         TextButton(onClick={showContractDialog=false},modifier=Modifier.align(Alignment.End)) { Text("稍后确认") }
        }
       }
      }
     }
    }
     val detailTask = remember(taskDetailsId,tick) { taskDetailsId?.let(db::task) }
      LaunchedEffect(taskDetailsId,detailTask) { if(taskDetailsId!=null && detailTask==null) taskDetailsId=null }
     if(detailTask!=null) TaskDetailSheet(
      task=detailTask, db=db, timer=focusTimer,
      onDismiss={taskDetailsId=null}, onFocus={taskDetailsId=null;page=4}, onChanged={refresh++},
      onAnalytics={taskDetailsId=null;gritAnalyticsTaskId=detailTask.id;page=2},
      )
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
internal fun focusDuration(milliseconds:Long):String = when {
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

@Composable private fun TaskCard(task:Task,db:Database,timer:FocusTimer,onFocus:()->Unit,onDetails:()->Unit,onChanged:()->Unit,index:Int=0,count:Int=1) {
 val active=timer.taskId==task.id && timer.phase=="work"
 GritTaskHeader(task,focusDuration(db.elapsed(task)),active,false,gritTaskShape(index,count),onDetails) {
   when {
    timer.taskId==task.id && timer.phase=="work" -> timer.pause()
     timer.taskId==task.id && timer.phase=="paused" -> { timer.resume(task); onFocus() }
    else -> { if(timer.phase!="ready")timer.finish();timer.begin(task);onFocus() }
   }
   onChanged()
 }
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
