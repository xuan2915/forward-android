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
import kotlinx.coroutines.launch
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
  initializeAchievementNotices(db)
  val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
  val apps = packageManager.queryIntentActivities(launch,0).filter { store.canRestrict(it.activityInfo.packageName) }.distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(packageManager).toString() }
  setContent {
    val debugPage = if (BuildConfig.DEBUG) intent.getIntExtra("debug_page",0) else 0
    var page by remember { mutableIntStateOf(if(db.setting("import_draft").isNotBlank()) 5 else debugPage.coerceIn(0,5)) }; var editor by remember { mutableStateOf(false) };var dataVersion by remember {mutableIntStateOf(0)};var rewardHistoryExpanded by remember {mutableStateOf(false)};var rewardControlsExpanded by remember {mutableStateOf(false)};var selectedRewardRecord by remember { mutableStateOf<String?>(null) };var taskFilter by remember { mutableIntStateOf(0) }
    var targetDay by remember { mutableStateOf("") }; var now by remember { mutableLongStateOf(0L) }; var taskDetailsId by remember { mutableStateOf<String?>(null) }; var editingTaskId by remember { mutableStateOf<String?>(null) };var movingTask by remember { mutableStateOf<Task?>(null) };var deletingTask by remember { mutableStateOf<Task?>(null) };var allAchievements by remember { mutableStateOf(false) }; var newAchievement by remember { mutableStateOf(nextUnseenAchievement(db)) }; var gritAnalyticsTaskId by remember { mutableStateOf<String?>(null) }; var showContractDialog by remember { mutableStateOf(false) }
    val taskActionScope = rememberCoroutineScope()
    fun dismissTaskActions() { movingTask=null; deletingTask=null }
   LaunchedEffect(shareVersion) { if(shareVersion>0) { page=5;editor=false } }
   LaunchedEffect(Unit) { while(true) { db.settleToday();focusTimer.advance();if(now%10L==0L)db.checkpoint();now++; delay(1000) } }
   val tick = now + refresh; val today = db.day(); val c = remember(today,tick) { db.contract(today) }; val tasks = remember(today,refresh) { db.tasks(today) }; val continuing = remember(today,refresh) { db.runningTasks().filter { it.date != today } }
   var dailyCardRecords by remember { mutableStateOf(emptyList<DailyCardRecord>()) }
   LaunchedEffect(today,refresh) { dailyCardRecords = DailyCards.reconcileAll(db,today) }
   val protected = remember(tick) { val id = ComponentName(this,FocusService::class.java).flattenToString(); FocusService.connected && Settings.Secure.getString(contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.split(':')?.any { it.equals(id,true) } == true }
   LaunchedEffect(refresh) { if (newAchievement == null) newAchievement = nextUnseenAchievement(db) }
   FocusTheme { MotionHost(db.xp(),showToast=page!=2 && taskDetailsId==null && newAchievement==null) {
       androidx.activity.compose.BackHandler(enabled=allAchievements || gritAnalyticsTaskId!=null || page==5 || editor || showContractDialog) {
        when { showContractDialog -> showContractDialog=false; allAchievements -> allAchievements=false; gritAnalyticsTaskId!=null -> gritAnalyticsTaskId=null; editor -> editor=false; else -> page=0 }
       }
     Scaffold(containerColor=AppColors.Background, bottomBar = { if(taskDetailsId==null && gritAnalyticsTaskId==null && !allAchievements && page!=5) AppNavigation(page){page=it;editor=false;gritAnalyticsTaskId=null;allAchievements=false;dismissTaskActions();showContractDialog=false} }) { pad ->
     Box(Modifier.fillMaxSize()) {
      AnimatedContent(targetState=page,transitionSpec={fadeIn(tween(AppMotion.Standard))+slideInHorizontally(tween(AppMotion.Standard)){it/28} togetherWith fadeOut(tween(AppMotion.Short))+slideOutHorizontally(tween(AppMotion.Short)){ -it/36 }},label="page-transition") { screen ->
      if (!editor && screen == 0) {
       GritTaskListPage(
        modifier=Modifier.fillMaxSize().padding(pad), date=today, tasks=tasks,
        dailyCard=dailyCardRecords.firstOrNull { it.date == today },
        continuing=continuing, filter=taskFilter,
        confirmed=c.confirmed,
        onFilter={taskFilter=it}, onAdd={dismissTaskActions();targetDay=today;editor=true},
        onPlan={dismissTaskActions();showContractDialog=true}, onSettings={dismissTaskActions();page=5},
        onDetails={taskDetailsId=it.id},
        onEdit={editingTaskId=it.id;taskDetailsId=it.id},
        onMove={task->taskActionScope.launch { delay(200); movingTask=task }},
        onDelete={task->taskActionScope.launch { delay(200); deletingTask=task }},
       )
      } else if (!editor && screen == 2 && allAchievements) {
       AchievementsPage(db,refresh){allAchievements=false}
      } else if (!editor && screen == 2 && gritAnalyticsTaskId != null) {
       GritHabitAnalyticsPage(db,gritAnalyticsTaskId!!,refresh, Modifier.padding(pad)) { gritAnalyticsTaskId=null }
      } else {
     val listState=remember(screen){androidx.compose.foundation.lazy.LazyListState()}
     LazyColumn(state=listState,modifier=Modifier.fillMaxSize().padding(pad).padding(horizontal=AppSpace.Page), contentPadding=PaddingValues(top=if(screen==5) AppSpace.Small else AppSpace.XLarge,bottom=AppSpace.XLarge), verticalArrangement=Arrangement.spacedBy(AppSpace.Large)) {
      if(editor) {
       item { TaskEditor(targetDay, db, onCancel={ editor=false }, onSave={ editor=false; refresh++ }) }
      } else when(screen) {
       0 -> {
         val required=tasks.filter { it.required }; val optional=tasks.filterNot { it.required }
         item { GritTodayHeader(LocalDate.parse(today).format(DateTimeFormatter.ofPattern("M月d日 · E",Locale.CHINA)),tasks.count{it.done},tasks.size){page=5} }
         item { GritTaskFilters(taskFilter){taskFilter=it} }
         item { GritEntertainmentBanner(protected,c.emergency,db.balance("video")>0,db.balance("video")){dismissTaskActions();if(!protected)startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) else page=1} }
         if(db.setting("changed_$today")=="true") item {Text("今日计划已调整 · 查看成长记录",color=AppColors.Muted)}
         if(db.setting("gate_changed_$today")=="true") item {Text("必做任务已变更 · 娱乐保护保持开启",color=AppColors.Warning)}
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
         if(tasks.isEmpty()) item { GritEmptyTasks(if(c.rest) "今天是休息日 · 记录会保留在成长日历" else "还没有任务 · 添加一项今天要完成的事") }
       }
       1 -> {
        item { Header("奖励与额度", "娱乐时间") }
        item { CardBlock {
         val video=db.rewardSnapshot("video");val games=db.rewardSnapshot("game")
         Text(if(!protected) "应用保护未开启" else if(c.emergency) "今天已紧急解锁" else if(video.remaining>0) "保护已开启 · 额度可用" else if(tasks.any{it.required && !it.done && it.rewardMinutes>0}) "完成一项有视频奖励的必做任务即可增加额度" else if(tasks.any{it.required && it.done}) "视频额度已用完" else "暂无可用视频额度",color=if(video.remaining>0 && protected && !c.emergency) AppColors.Accent else AppColors.Muted,style=MaterialTheme.typography.labelMedium)
         Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
          Column(verticalArrangement=Arrangement.spacedBy(2.dp)) { Text("${video.remaining / 60000}",fontSize=38.sp,fontWeight=FontWeight.Light,lineHeight=42.sp,color=AppColors.Reward); Text("剩余分钟",color=AppColors.Muted,style=MaterialTheme.typography.bodySmall) }
          Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(2.dp)) { Text("${games.remaining}",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Light); Text("剩余游戏局数",color=AppColors.Muted,style=MaterialTheme.typography.bodySmall) }
         }
         Hairline()
         Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text("已获得 ${video.earned/60000} 分钟",style=MaterialTheme.typography.labelMedium,color=AppColors.Muted);Text("已使用 ${video.used/60000} 分钟",style=MaterialTheme.typography.labelMedium,color=AppColors.Muted)}
         if(video.used>0 || video.sleepDeducted>0 || video.expired>0) Text("已使用 ${video.used/60000} 分钟 · 睡眠扣减 ${video.sleepDeducted/60000} 分钟 · 已过期 ${video.expired/60000} 分钟",style=MaterialTheme.typography.labelSmall,color=AppColors.Muted)
         if(video.reversed>0 || db.pendingSleepPenalty()>0) Text("任务奖励冲回 ${video.reversed/60000} 分钟 · 睡眠待抵扣 ${db.pendingSleepPenalty()/60000} 分钟",style=MaterialTheme.typography.labelSmall,color=AppColors.Muted)
         if(games.earned>0 || games.used>0) Text("游戏额度：已获得 ${games.earned} 局 · 已使用 ${games.used} 局",style=MaterialTheme.typography.labelSmall,color=AppColors.Muted)
         Text("抖音与 B 站共享视频额度 · 游戏局数手动记录",style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)
         Text("任务每天 0 点更新 · 奖励额度 ${db.setting("boundary","4").padStart(2,'0')}:00 重置",style=MaterialTheme.typography.labelSmall,color=AppColors.Quiet)
         if(games.remaining > 0) TextButton(onClick={db.consume("game",1); refresh++}) { Text("手动记录已使用一局") }
        } }
        items(tasks.filter{!it.required && it.done && !it.claimed},key={it.id}) { task -> CardBlock { Text("${task.title} · 领取一项奖励"); Row { if(task.rewardMinutes>0) TextButton(enabled=db.eligible(),onClick={db.claim(task,"video");refresh++}) {Text("${task.rewardMinutes} 分钟视频")}; if(task.rewardGames>0) TextButton(enabled=db.eligible(),onClick={db.claim(task,"game");refresh++}) {Text("${task.rewardGames} 局游戏") } }; if(!db.eligible()) Text("完成今日必做任务后可领取",color=AppColors.Muted) } }
        item { TextButton(onClick={rewardControlsExpanded=!rewardControlsExpanded},modifier=Modifier.animateContentSize()) {Text(if(rewardControlsExpanded) "收起其他操作" else "应用保护与其他操作") } }
        if(rewardControlsExpanded) item { CardBlock {
         if(!c.emergency) EmergencyUnlock(db){refresh++} else TextButton(onClick={db.resumeProtection();refresh++}){Text("恢复应用保护")}
         TextButton(onClick={db.startLearning();refresh++},enabled=!db.learningActive()) {Text(if(db.learningActive()) "B站学习窗口进行中" else "开启 B站15分钟学习窗口")}
         Text("学习窗口临时放行 · 不扣视频额度",style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)
         TextButton(onClick={page=5}){Text("受限应用与权限")}
         TextButton(onClick={page=5}){Text("睡眠记录与规则")}
        } }
        item { CardBlock { Text("奖励记录",style=MaterialTheme.typography.titleLarge); val records=db.rewardHistory(); if(records.isEmpty()) Text("奖励发放和使用记录会显示在这里",color=AppColors.Muted); (if(rewardHistoryExpanded) records else records.take(4)).forEach { record -> TextButton(onClick={selectedRewardRecord=record},contentPadding=PaddingValues(vertical=4.dp,horizontal=0.dp)) {Text(record,style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)} }; if(records.size>4) TextButton(onClick={rewardHistoryExpanded=!rewardHistoryExpanded}) { Text(if(rewardHistoryExpanded) "收起记录" else "查看全部 ${records.size} 条记录") } } }
       }
       2 -> {
        item { GrowthHero(db,refresh) }
        item { GrowthDetails(db,refresh){allAchievements=true} }
        if(dailyCardRecords.isNotEmpty()) item { DailyCardArchive(dailyCardRecords) }
       }
       4 -> { item { FocusScreen(db,focusTimer,tasks,onTasks={page=0},onChanged={refresh++}) } }
       5 -> {
        item {
         Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween) {
          Text("设置",style=MaterialTheme.typography.headlineSmall)
          TextButton(onClick={page=0}) { Text("返回今日任务") }
         }
        }
         item {CardBlock {Text(if(protected) "应用保护已开启" else "应用保护未开启");SecondaryAction("管理无障碍权限",{startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))});Text("后台受限时，请在系统设置允许向前运行",color=AppColors.Muted)}}
        item {DaySettings(db){refresh++}}
        item {SectionHeading("睡眠规则")}
        item {SleepCard(db,refresh){refresh++}}
        item {SectionHeading("计划管理")}
        item {ImportEditor(db,today,refresh){refresh++}}
        item {FuturePlan(db,onAdd={targetDay=it;editor=true}){refresh++}}
        item {BackupView(db,{dataVersion++;refresh++},{focusTimer.resetAfterRestore()})}
        item {Text("受限应用",style=MaterialTheme.typography.titleLarge);Text("已选择 ${store.selected.size} 个应用",color=AppColors.Muted)}
        items(apps,key={it.activityInfo.packageName}) {app->val pkg=app.activityInfo.packageName;Row(Modifier.fillMaxWidth().toggleable(value=pkg in store.selected,onValueChange={checked->store.selected=if(checked)store.selected+pkg else store.selected-pkg;refresh++}),verticalAlignment=Alignment.CenterVertically){Text(app.loadLabel(packageManager).toString(),modifier=Modifier.weight(1f));Checkbox(pkg in store.selected,onCheckedChange=null)}}
       }
      }
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
     selectedRewardRecord?.let { record -> AlertDialog(onDismissRequest={selectedRewardRecord=null},title={Text("奖励记录详情")},text={Text(record)},confirmButton={TextButton(onClick={selectedRewardRecord=null}){Text("完成")}}) }
     movingTask?.let { task -> TaskMoveDialog(task,db,onDismiss={movingTask=null},onMoved={if(focusTimer.taskId==task.id)focusTimer.finish();movingTask=null;refresh++}) }
     deletingTask?.let { task -> TaskDeleteDialog(task,db,onDismiss={deletingTask=null},onDeleted={deletingTask=null;refresh++}) }
     val detailTask = remember(taskDetailsId,tick) { taskDetailsId?.let(db::task) }
      LaunchedEffect(taskDetailsId,detailTask) { if(taskDetailsId!=null && detailTask==null) taskDetailsId=null }
     if(detailTask!=null) TaskDetailSheet(
      task=detailTask, db=db, timer=focusTimer,
      startEditing=editingTaskId==detailTask.id,
      onDismiss={taskDetailsId=null;editingTaskId=null}, onFocus={taskDetailsId=null;editingTaskId=null;page=4}, onChanged={refresh++},
      onAnalytics={taskDetailsId=null;editingTaskId=null;gritAnalyticsTaskId=detailTask.id;page=2},
      )
     newAchievement?.takeIf { taskDetailsId == null }?.let { achievement ->
      fun acknowledgeAchievement(openAll:Boolean) {
       db.setSetting("achievement_seen_${achievement.id}","true")
       newAchievement=null
       if(openAll) { allAchievements=true;page=2 }
       refresh++
      }
      AchievementUnlockDialog(
       achievement,
       onDismiss={acknowledgeAchievement(false)},
       onViewAll={acknowledgeAchievement(true)},
      )
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
internal fun focusDuration(milliseconds:Long):String = when {
 milliseconds < 60_000 -> "${milliseconds/1_000} 秒"
 milliseconds < 3_600_000 -> "${milliseconds/60_000} 分钟"
 else -> "${milliseconds/3_600_000} 小时 ${(milliseconds%3_600_000)/60_000} 分"
}

@Composable private fun ContractEditor(db:Database,c:Contract,onChanged:()->Unit) {
 var minutes by remember { mutableStateOf("30") }; var games by remember { mutableStateOf("0") }; var rest by remember { mutableStateOf(false) }
 CardBlock { Text("确认今日任务",fontSize=20.sp); Text("确认后任务要求将锁定 · 每项必做完成后即时发奖",color=Color.LightGray); Row(Modifier.toggleable(value=rest,onValueChange={rest=it}),verticalAlignment=Alignment.CenterVertically) { Checkbox(rest,onCheckedChange=null); Text("设为休息日（不能有必做）") }; val canConfirm = if(rest) db.tasks(c.date).none{it.required} else db.tasks(c.date).any{it.required}; Button(enabled=canConfirm,onClick={db.confirm(c.date,rest,0,0);onChanged()}) { Text("确认今日计划") } }
}

@Composable private fun TaskEditor(day:String,db:Database,onCancel:()->Unit,onSave:()->Unit) {
 var title by remember { mutableStateOf("") }; var required by remember { mutableStateOf(!db.contract(day).confirmed) }; var minutes by remember { mutableStateOf("25") }; var mode by remember { mutableStateOf("行动型") }; var checklist by remember { mutableStateOf("") }; var video by remember { mutableStateOf(if(!db.contract(day).confirmed) "10" else "20") }; var games by remember { mutableStateOf(if(!db.contract(day).confirmed) "0" else "1") }; var recurrence by remember { mutableStateOf("once") };var weekdays by remember{mutableStateOf(setOf(1,2,3,4,5))};var intervalDays by remember{mutableStateOf("3")}
 Header("PLAN / $day","添加任务")
 CardBlock {
  OutlinedTextField(title,onValueChange={title=it},label={Text("任务名称")},modifier=Modifier.fillMaxWidth())
  Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(required,onCheckedChange={required=it;if(it){video="10";games="0"}else{video="20";games="1"}},enabled=!db.contract(day).confirmed); Text("必做") }
  NumberField("目标分钟",minutes){minutes=it}
  Text("验收类型"); Row { listOf("行动型","成果型","掌握型").forEach { TextButton(onClick={mode=it}) { Text(if(mode==it) "✓ $it" else it) } } }
  OutlinedTextField(checklist,onValueChange={checklist=it},label={Text("预先约定的自检条件")},modifier=Modifier.fillMaxWidth())
  Text("重复："); Row { listOf("once" to "一次", "daily" to "每天", "weekly" to "每周同日").forEach { (v,label) -> TextButton(onClick={recurrence=v}) { Text(if(recurrence==v) "✓$label" else label) } } }
  TextButton(onClick={recurrence="days:"+weekdays.sorted().joinToString(",")}){Text(if(recurrence.startsWith("days:")) "✓ 指定星期" else "指定星期")}
  if(recurrence.startsWith("days:"))Row(Modifier.fillMaxWidth()){(1..7).forEach {dayOfWeek->FilterChip(selected=dayOfWeek in weekdays,onClick={weekdays=if(dayOfWeek in weekdays)weekdays-dayOfWeek else weekdays+dayOfWeek;recurrence="days:"+weekdays.sorted().joinToString(",")},label={Text(listOf("一","二","三","四","五","六","日")[dayOfWeek-1])},modifier=Modifier.weight(1f))}}
  TextButton(onClick={recurrence="interval:${intervalDays.toIntOrNull()?.coerceIn(1,365)?:3}"}){Text(if(recurrence.startsWith("interval:")) "✓ 自定义间隔" else "自定义间隔")}
  if(recurrence.startsWith("interval:")){NumberField("每隔几天（1–365）",intervalDays){intervalDays=it;recurrence="interval:${it.toIntOrNull()?.coerceIn(1,365)?:1}"};Text("按开始日期和间隔生成",style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)}
  Text(if(required) "完成后自动发放奖励" else "选做完成后领取一项奖励"); NumberField("视频分钟",video){video=it}; NumberField("游戏局数",games){games=it}
  Row { Button(enabled=title.isNotBlank() && recurrence!="days:" && (!recurrence.startsWith("interval:") || (intervalDays.toIntOrNull()?:0) in 1..365) && (!db.contract(day).confirmed || !required),onClick={ db.addTask(day,title,required,minutes.toIntOrNull()?:0,mode,checklist,video.toIntOrNull()?:0,games.toIntOrNull()?:0,recurrence); onSave() }) {Text("保存")}; TextButton(onClick=onCancel){Text("取消")} }
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
