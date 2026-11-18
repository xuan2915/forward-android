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
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
 private var refresh by mutableIntStateOf(0)
 private var shareVersion by mutableIntStateOf(0)
 private fun receiveText(intent: Intent): Boolean {
  if(intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return false
  val text=intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
  if(text.isNotBlank()) Database(this).use { it.setSetting("import_draft",text) }
  intent.action=Intent.ACTION_MAIN
  intent.removeExtra(Intent.EXTRA_TEXT)
  return text.isNotBlank()
 }
 override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); if(receiveText(intent)) shareVersion++; refresh++ }
 override fun onResume() { super.onResume(); refresh++;runCatching { Database(this).use { NightWrapUpScheduler.reschedule(this,it) } } }
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
  val initialNavigation = resolveInitialNavigation(
   action = intent.action,
   mimeType = intent.type,
   hasSharedText = !intent.getCharSequenceExtra(Intent.EXTRA_TEXT).isNullOrBlank(),
   debugPage = intent.getIntExtra("debug_page", 0).takeIf { intent.hasExtra("debug_page") },
   debugBuild = BuildConfig.DEBUG,
  )
  window.useDarkSystemBars()
  receiveText(intent)
  val db = Database(this); val store = Store(this)
  val focusTimer=FocusTimer(db);focusTimer.reconcileBoot()
  initializeAchievementNotices(db)
  val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
  val apps = packageManager.queryIntentActivities(launch,0).filter { store.canRestrict(it.activityInfo.packageName) }.distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(packageManager).toString() }
  setContent {
    // Keep navigation alive while this Activity is backgrounded; a fresh Activity follows its launch intent.
    var page by remember { mutableIntStateOf(initialNavigation.page) }; var planManagement by remember { mutableStateOf(initialNavigation.openPlanManagement) }; var planSection by remember { mutableStateOf<String?>(if(initialNavigation.openPlanManagement) "import" else null) }; var settingsSection by remember { mutableStateOf<String?>(null) }; var editor by remember { mutableStateOf(false) };var showAllRewardHistory by remember { mutableStateOf(false) };var rewardControlsExpanded by remember { mutableStateOf(false) };var selectedRewardRecord by remember { mutableStateOf<String?>(null) };var taskFilter by remember { mutableIntStateOf(0) }
    var targetDay by remember { mutableStateOf("") }; var now by remember { mutableLongStateOf(0L) }; var taskDetailsId by remember { mutableStateOf<String?>(null) }; var editingTaskId by remember { mutableStateOf<String?>(null) };var movingTask by remember { mutableStateOf<Task?>(null) };var deletingTask by remember { mutableStateOf<Task?>(null) };var allAchievements by remember { mutableStateOf(false) }; var newAchievement by remember { mutableStateOf(nextUnseenAchievement(db)) }; var gritAnalyticsTaskId by remember { mutableStateOf<String?>(null) }; var dailyCardDate by remember { mutableStateOf<String?>(null) }; var dailyCardOpenDate by remember { mutableStateOf<String?>(null) }
    var detailMode by remember { mutableStateOf(if (BuildConfig.DEBUG) db.setting(DetailModeKey,"sheet") else "sheet") }; var inlineTaskId by remember { mutableStateOf<String?>(null) }
    fun dismissTaskActions() { movingTask=null; deletingTask=null; inlineTaskId=null }
   LaunchedEffect(shareVersion) { if(shareVersion>0) { page=0;planManagement=true;planSection="import";settingsSection=null;editor=false } }
   LaunchedEffect(Unit) { withContext(Dispatchers.IO) { IndependentBackup.saveIfDue(this@MainActivity, db) } }
   LaunchedEffect(Unit) { while(true) { db.settleToday();focusTimer.advance();if(now%10L==0L)db.checkpoint();now++; delay(1000) } }
   val tick = now + refresh; val today = db.day(); val c = remember(today,tick) { db.contract(today) }; val tasks = remember(today,refresh) { db.tasks(today) }; val continuing = remember(today,refresh) { db.runningTasks().filter { it.date != today } }
   var dailyCardRecords by remember { mutableStateOf(emptyList<DailyCardRecord>()) }
   LaunchedEffect(today,refresh,taskDetailsId) {
    val reconciled = DailyCards.reconcileAll(db,today)
    dailyCardRecords = reconciled
    if (taskDetailsId == null && reconciled.any { it.date == today && !it.revealSeen }) dailyCardOpenDate = today
   }
   val protected = remember(tick) { val id = ComponentName(this,FocusService::class.java).flattenToString(); FocusService.connected && Settings.Secure.getString(contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.split(':')?.any { it.equals(id,true) } == true }
   LaunchedEffect(refresh) { if (newAchievement == null) newAchievement = nextUnseenAchievement(db) }
   FocusTheme { MotionHost(db.xp(),showToast=page!=2 && dailyCardOpenDate==null && dailyCardDate==null && (taskDetailsId!=null || newAchievement==null)) {
       val inlineDetailOpen = page == 0 && !editor && !planManagement && inlineTaskId != null
       androidx.activity.compose.BackHandler(enabled=inlineDetailOpen || allAchievements || gritAnalyticsTaskId!=null || settingsSection!=null || planManagement || page==5 || editor || dailyCardOpenDate!=null || dailyCardDate!=null) {
        when { dailyCardOpenDate!=null -> {DailyCards.markRevealSeen(db,dailyCardOpenDate!!);dailyCardOpenDate=null;refresh++}; dailyCardDate!=null -> dailyCardDate=null; allAchievements -> allAchievements=false; gritAnalyticsTaskId!=null -> gritAnalyticsTaskId=null; editor -> editor=false; settingsSection!=null -> settingsSection=null; planManagement && planSection!=null -> planSection=null; planManagement -> planManagement=false; page==5 -> page=0; inlineDetailOpen -> inlineTaskId=null; else -> page=0 }
       }
     Scaffold(containerColor=AppColors.Background, bottomBar = { if(taskDetailsId==null && gritAnalyticsTaskId==null && !allAchievements && page!=5 && !planManagement) AppNavigation(page){page=it;editor=false;planManagement=false;settingsSection=null;gritAnalyticsTaskId=null;allAchievements=false;dismissTaskActions()} }) { pad ->
     Box(Modifier.fillMaxSize()) {
      AnimatedContent(targetState=page,transitionSpec={fadeIn(tween(AppMotion.Standard))+slideInHorizontally(tween(AppMotion.Standard)){it/28} togetherWith fadeOut(tween(AppMotion.Short))+slideOutHorizontally(tween(AppMotion.Short)){ -it/36 }},label="page-transition") { screen ->
      if (!editor && screen == 0 && !planManagement) {
       GritTaskListPage(
        modifier=Modifier.fillMaxSize().padding(pad), db=db, date=today, tasks=tasks,
        dailyCard=dailyCardRecords.firstOrNull { it.date == today },
        continuing=continuing, filter=taskFilter,
        confirmed=c.confirmed,
        onFilter={next ->
         if (next != taskFilter) { inlineTaskId = null; taskFilter = next }
        }, onAdd={dismissTaskActions();targetDay=today;editor=true},
        onPlan={dismissTaskActions();planManagement=true}, onSettings={dismissTaskActions();settingsSection=null;page=5},
        onDetails={ if(detailMode=="inline") inlineTaskId=if(inlineTaskId==it.id) null else it.id else taskDetailsId=it.id },
        onEdit={inlineTaskId=null;editingTaskId=it.id;taskDetailsId=it.id},
        onMove={task->movingTask=task},
        onDelete={task->deletingTask=task},
        onEditDailyCard={date->DailyCards.markRevealSeen(db,date);dailyCardDate=date;refresh++},
        detailMode=detailMode.takeIf{BuildConfig.DEBUG},
        onDetailMode={mode->db.setSetting(DetailModeKey,mode);detailMode=mode;inlineTaskId=null},
        expandedTaskId=inlineTaskId.takeIf{detailMode=="inline"},
        inlineDetail={ rowTask ->
         val live = remember(rowTask.id,tick) { db.task(rowTask.id) } ?: rowTask
         TaskDetailBody(live,db,focusTimer,startEditing=false,showHeader=false,
          onClose={after->inlineTaskId=null;after()},
          onFocus={page=4},
          onAnalytics={gritAnalyticsTaskId=live.id;page=2},
          onChanged={refresh++})
        },
       )
      } else if (!editor && screen == 2 && allAchievements) {
       AchievementsPage(db,refresh){allAchievements=false}
      } else if (!editor && screen == 2 && gritAnalyticsTaskId != null) {
       GritHabitAnalyticsPage(db,gritAnalyticsTaskId!!,refresh, Modifier.padding(pad)) { gritAnalyticsTaskId=null }
      } else {
      val listState=remember(screen,settingsSection,planManagement,planSection){androidx.compose.foundation.lazy.LazyListState()}
     LazyColumn(state=listState,modifier=Modifier.fillMaxSize().padding(pad).padding(horizontal=AppSpace.Page), contentPadding=PaddingValues(top=if(screen==5) AppSpace.Small else AppSpace.XLarge,bottom=AppSpace.XLarge), verticalArrangement=Arrangement.spacedBy(AppSpace.Large)) {
      if(editor) {
       item { TaskEditor(targetDay, db, onCancel={ editor=false }, onSave={ editor=false; refresh++ }) }
      } else when(screen) {
        0 -> {
         if (planManagement) {
          if (planSection == null) {
           item { SettingsPageHeader("计划管理", "今天", onBack={planManagement=false}) }
           item { SettingsDestinationRow("今日计划", if(c.confirmed) "任务要求已确认" else "确认今天的必做与选做任务", if(c.confirmed) "已确认" else "待确认") { planSection="today" } }
           item { SettingsDestinationRow("从笔记导入", "预览后添加到明日任务", db.setting("import_draft").takeIf(String::isNotBlank)?.let { "有草稿" }) { planSection="import" } }
           item { SettingsDestinationRow("提前安排", "查看并编辑未来日期的任务") { planSection="future" } }
           item { SettingsDestinationRow("历史任务补记与修正", "从日历选择日期，按原验收要求补记") { planManagement=false;planSection=null;page=2 } }
          } else {
           item { SettingsPageHeader(when(planSection){"today"->"今日计划";"import"->"从笔记导入";"future"->"提前安排";else->"计划管理"}, "计划管理", onBack={planSection=null}) }
           when(planSection) {
            "today" -> item { CardBlock { Text(if(c.confirmed) "今日计划已确认" else "确认今日计划",style=MaterialTheme.typography.titleMedium);ContractEditor(db,c){refresh++} } }
            "import" -> item { ImportEditor(db,today,refresh){refresh++} }
            "future" -> item { FuturePlan(db,onAdd={targetDay=it;editor=true}){refresh++} }
           }
          }
         }
        }
       1 -> {
        item { Header("奖励与额度", "娱乐时间") }
        item { CardBlock {
         val video=db.rewardSnapshot("video");val games=db.rewardSnapshot("game")
         Text(if(!protected) "应用保护未开启" else if(c.emergency) "今天已紧急解锁" else if(video.remaining>0) "保护已开启 · 额度可用" else if(tasks.any{it.required && !it.done && it.rewardMinutes>0}) "必做任务奖励逐项到账" else if(tasks.any{it.required && it.done}) "娱乐时长已用完" else "暂无可用娱乐时长",color=if(video.remaining>0 && protected && !c.emergency) AppColors.Accent else AppColors.Muted,style=MaterialTheme.typography.labelMedium)
         Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
          Column(verticalArrangement=Arrangement.spacedBy(2.dp)) { Text("${video.remaining / 60000}",fontSize=38.sp,fontWeight=FontWeight.Light,lineHeight=42.sp,color=AppColors.Reward); Text("剩余分钟",color=AppColors.Muted,style=MaterialTheme.typography.bodySmall) }
          Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(2.dp)) { Text("${games.remaining}",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Light); Text("剩余游戏局数",color=AppColors.Muted,style=MaterialTheme.typography.bodySmall) }
         }
         Hairline()
         Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text("已获得 ${video.earned/60000} 分钟",style=MaterialTheme.typography.labelMedium,color=AppColors.Muted);Text("已使用 ${video.used/60000} 分钟",style=MaterialTheme.typography.labelMedium,color=AppColors.Muted)}
         if(video.sleepDeducted>0 || video.expired>0 || video.reversed>0 || db.pendingSleepPenalty()>0) Text("睡眠扣减 ${video.sleepDeducted/60000} · 已过期 ${video.expired/60000} · 冲回 ${video.reversed/60000} · 待抵扣 ${db.pendingSleepPenalty()/60000} 分钟",style=MaterialTheme.typography.labelSmall,color=AppColors.Muted)
         val nightPendingVideo=db.pendingNightPenalty("video");val nightPendingGames=db.pendingNightPenalty("game")
         if(video.nightDeducted>0 || games.nightDeducted>0 || nightPendingVideo>0 || nightPendingGames>0) Text("夜间抵扣 · 娱乐 ${video.nightDeducted/60000} 分钟、游戏 ${games.nightDeducted} 局 · 待抵扣 ${nightPendingVideo/60000} 分钟、$nightPendingGames 局",style=MaterialTheme.typography.labelSmall,color=AppColors.Muted)
         Text("游戏已获 ${games.earned} 局 · 已用 ${games.used} 局 · 每日 00:00 更新，额度 ${db.setting("boundary","4").padStart(2,'0')}:00 结算",style=MaterialTheme.typography.labelSmall,color=AppColors.Quiet)
         if(games.remaining > 0) TextButton(onClick={db.consume("game",1); refresh++}) { Text("手动记录已使用一局") }
        } }
        items(tasks.filter{it.typeKnown && !it.required && it.done && !it.claimed},key={it.id}) { task -> CardBlock { Text("${task.title} · 领取一项奖励"); Row { if(task.rewardMinutes>0) TextButton(enabled=db.eligible(),onClick={db.claim(task,"video");refresh++}) {Text("${task.rewardMinutes} 分钟娱乐时长")}; if(task.rewardGames>0) TextButton(enabled=db.eligible(),onClick={db.claim(task,"game");refresh++}) {Text("${task.rewardGames} 局游戏") } }; db.rewardUnavailableReason(task.date)?.let { Text(it,color=AppColors.Muted) } } }
        item { TextButton(onClick={rewardControlsExpanded=!rewardControlsExpanded},modifier=Modifier.animateContentSize()) {Text(if(rewardControlsExpanded) "收起其他操作" else "应用保护与其他操作") } }
        if(rewardControlsExpanded) item { CardBlock {
         if(!c.emergency) EmergencyUnlock(db){refresh++} else TextButton(onClick={db.resumeProtection();refresh++}){Text("恢复应用保护")}
         TextButton(onClick={db.startLearning();refresh++},enabled=!db.learningActive()) {Text(if(db.learningActive()) "B站学习窗口进行中" else "开启 B站15分钟学习窗口")}
         Text("学习窗口临时放行 · 不扣娱乐时长",style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)
         TextButton(onClick={page=5;settingsSection="protection"}){Text("受限应用与权限")}
         TextButton(onClick={page=5;settingsSection="sleep"}){Text("夜间收尾设置")}
        } }
        item { CardBlock { Text("最近记录",style=MaterialTheme.typography.titleMedium); val records=db.rewardHistory(); if(records.isEmpty()) Text("奖励发放和使用会显示在这里",color=AppColors.Muted); records.take(3).forEach { record -> TextButton(onClick={selectedRewardRecord=record},contentPadding=PaddingValues(vertical=4.dp,horizontal=0.dp)) {Text(record,style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)} }; if(records.size>3) TextButton(onClick={showAllRewardHistory=true}) { Text("查看全部 ${records.size} 条记录") } } }
       }
       2 -> {
        item { GrowthHero(db,refresh){allAchievements=true;page=2} }
        item { GrowthDetails(db,refresh,onAllAchievements={allAchievements=true},onHistoricalTask={task->taskDetailsId=task.id}) }
        if(dailyCardRecords.isNotEmpty()) item { DailyCardArchive(dailyCardRecords,db){DailyCards.markRevealSeen(db,it);dailyCardDate=it;refresh++} }
       }
       4 -> { item { FocusScreen(db,focusTimer,tasks,onTasks={page=0},onChanged={refresh++}) } }
        5 -> {
          item { SettingsPageHeader(if(settingsSection==null) "设置" else when(settingsSection){"protection"->"应用保护";"reward"->"奖励规则";"sleep"->"夜间收尾";"backup"->"数据备份";else->"关于向前"}, if(settingsSection==null) "今天" else "设置", onBack={if(settingsSection==null)page=0 else settingsSection=null}) }
         if(settingsSection==null) {
          item { SettingsDestinationRow("应用保护",if(protected) "无障碍服务已连接 · ${store.selected.size} 个受限应用" else "配置受限应用与无障碍权限",if(protected) "运行中" else "未开启"){settingsSection="protection"} }
          item { SettingsDestinationRow("奖励规则","查看额度重置时间与规则", "${db.setting("boundary","4").padStart(2,'0')}:00"){settingsSection="reward"} }
          val night=db.nightWrapUpConfig()
          item { SettingsDestinationRow("夜间收尾","提醒、工作截止与娱乐限制","${night.windDown} · ${night.entertainmentCutoff}"){settingsSection="sleep"} }
          item { SettingsDestinationRow("数据备份","导出或恢复本地数据"){settingsSection="backup"} }
          item { SettingsDestinationRow("关于向前","版本与应用信息",BuildConfig.VERSION_NAME){settingsSection="about"} }
         } else when(settingsSection) {
          "protection" -> {
           item { CardBlock { Text(if(protected) "应用保护已开启" else "应用保护未开启",style=MaterialTheme.typography.titleMedium,color=if(protected) AppColors.Accent else AppColors.Text);Text("已选择 ${store.selected.size} 个受限应用",color=AppColors.Muted);SecondaryAction("打开无障碍设置",{startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))});Text("服务连接状态会在返回后更新",style=MaterialTheme.typography.bodySmall,color=AppColors.Muted) } }
           item { Text("受限应用",style=MaterialTheme.typography.titleMedium) }
           items(apps, key = { it.activityInfo.packageName }) { app ->
            val pkg = app.activityInfo.packageName
            val selected = pkg in store.selected
            Column {
             Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).toggleable(value = selected, onValueChange = { checked ->
              if (checked && db.setting("controlled_reward_$pkg").isEmpty()) db.setRewardKind(pkg, "video")
              store.selected = if (checked) store.selected + pkg else store.selected - pkg
              refresh++
             }).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
              Text(app.loadLabel(packageManager).toString(), modifier = Modifier.weight(1f))
              Checkbox(selected, onCheckedChange = null)
             }
             if (selected) Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilterChip(selected = db.rewardKind(pkg) == "video", onClick = { db.setRewardKind(pkg, "video"); refresh++ }, label = { Text("娱乐时长") })
              FilterChip(selected = db.rewardKind(pkg) == "game", onClick = { db.setRewardKind(pkg, "game"); refresh++ }, label = { Text("游戏局数") })
             }
            }
           }
          }
          "reward" -> item { DaySettings(db){refresh++} }
          "sleep" -> item { NightWrapUpCard(db,refresh){refresh++} }
          "backup" -> item { BackupView(db,{refresh++},{focusTimer.resetAfterRestore()}) }
          else -> item { CardBlock { Text("向前",style=MaterialTheme.typography.titleLarge);Text("版本 ${BuildConfig.VERSION_NAME}",color=AppColors.Muted);Text("任务、专注与成长记录",color=AppColors.Muted); if(BuildConfig.DEBUG && runCatching { Class.forName("com.focus.prototype.DesignLabActivity") }.isSuccess) TextButton(onClick={startActivity(Intent().setClassName(this@MainActivity, "com.focus.prototype.DesignLabActivity"))}) { Text("设计实验室（Debug）") } } }
         }
        }
      }
      }
     }
    }
     selectedRewardRecord?.let { record -> AlertDialog(onDismissRequest={selectedRewardRecord=null},title={Text("奖励记录详情")},text={Text(record)},confirmButton={TextButton(onClick={selectedRewardRecord=null}){Text("完成")}}) }
     if(showAllRewardHistory) AlertDialog(onDismissRequest={showAllRewardHistory=false},title={Text("奖励记录")},text={
      val records=db.rewardHistory()
      androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max=480.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
       items(records){record->TextButton(onClick={selectedRewardRecord=record;showAllRewardHistory=false},contentPadding=PaddingValues(vertical=6.dp,horizontal=0.dp)){Text(record,style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)} }
      }
     },confirmButton={TextButton(onClick={showAllRewardHistory=false}){Text("完成")}})
     dailyCardOpenDate?.let { date ->
      val record = dailyCardRecords.firstOrNull { it.date == date } ?: DailyCards.load(db,date)
      record?.let { DailyCardDialog(it,db,onEdit={DailyCards.markRevealSeen(db,date);dailyCardOpenDate=null;dailyCardDate=date;refresh++},onDismiss={DailyCards.markRevealSeen(db,date);dailyCardOpenDate=null;refresh++}) }
     }
     dailyCardDate?.let { date -> DailyCardEditor(db,date,db.tasks(date),onDismiss={dailyCardDate=null},onSaved={dailyCardDate=null;refresh++}) }
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
     newAchievement?.takeIf { taskDetailsId == null && dailyCardOpenDate==null && dailyCardDate==null }?.let { achievement ->
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
 var title by remember { mutableStateOf("") }; var required by remember { mutableStateOf(!db.contract(day).confirmed) }; var minutes by remember { mutableStateOf("25") }; var mode by remember { mutableStateOf("行动型") }; var checklist by remember { mutableStateOf("") }; var video by remember { mutableStateOf(if(!db.contract(day).confirmed) "10" else "20") }; var games by remember { mutableStateOf(if(!db.contract(day).confirmed) "0" else "1") }; var recurrence by remember { mutableStateOf("once") };var weekdays by remember{mutableStateOf(setOf(1,2,3,4,5))};var intervalDays by remember{mutableStateOf("3")};var repeatExpanded by remember{mutableStateOf(false)};var rewardsExpanded by remember{mutableStateOf(false)}
 val repeatLabel=when {recurrence=="daily"->"每天";recurrence=="weekly"->"每周同日";recurrence.startsWith("days:")->"指定星期";recurrence.startsWith("interval:")->"每隔 ${intervalDays} 天";else->"仅此一次"}
 Header("PLAN / $day","添加任务")
 CardBlock {
  Text("基础信息",style=MaterialTheme.typography.titleMedium)
  OutlinedTextField(title,onValueChange={title=it},label={Text("任务名称")},modifier=Modifier.fillMaxWidth(),singleLine=true)
  Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   FilterChip(selected=required,onClick={if(!db.contract(day).confirmed){required=true;video="10";games="0"}},label={Text("必做")},enabled=!db.contract(day).confirmed)
   FilterChip(selected=!required,onClick={required=false;video="20";games="1"},label={Text("选做")})
  }
  NumberField("目标专注分钟",minutes){minutes=it}
  androidx.compose.material3.HorizontalDivider(color=AppColors.Border)
  Text("验收要求",style=MaterialTheme.typography.titleMedium)
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("行动型","成果型","掌握型").forEach { type -> FilterChip(selected=mode==type,onClick={mode=type},label={Text(type)},modifier=Modifier.weight(1f)) } }
  OutlinedTextField(checklist,onValueChange={checklist=it},label={Text(when(mode){"成果型"->"成果验收条件";"掌握型"->"独立掌握标准";else->"完成后自检条件"})},modifier=Modifier.fillMaxWidth(),minLines=1,maxLines=3)
  androidx.compose.material3.HorizontalDivider(color=AppColors.Border)
  TextButton(onClick={repeatExpanded=!repeatExpanded},modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).immediatePress()) { Text("重复计划 · $repeatLabel${if(repeatExpanded)"  −" else "  +"}") }
  AnimatedVisibility(repeatExpanded) {
   Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
     listOf("once" to "一次","daily" to "每天","weekly" to "每周").forEach{(value,label)->FilterChip(selected=recurrence==value,onClick={recurrence=value},label={Text(label)},modifier=Modifier.weight(1f))}
    }
    FilterChip(selected=recurrence.startsWith("days:"),onClick={recurrence="days:"+weekdays.sorted().joinToString(",")},label={Text("指定星期")})
    if(recurrence.startsWith("days:"))Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(2.dp)){(1..7).forEach {d->FilterChip(selected=d in weekdays,onClick={weekdays=if(d in weekdays)weekdays-d else weekdays+d;recurrence="days:"+weekdays.sorted().joinToString(",")},label={Text(listOf("一","二","三","四","五","六","日")[d-1])},modifier=Modifier.weight(1f))}}
    Row(verticalAlignment=Alignment.CenterVertically) { FilterChip(selected=recurrence.startsWith("interval:"),onClick={recurrence="interval:${intervalDays.toIntOrNull()?.coerceIn(1,365)?:3}"},label={Text("自定义间隔")});if(recurrence.startsWith("interval:")){Spacer(Modifier.width(8.dp));NumberField("间隔天数",intervalDays){intervalDays=it;recurrence="interval:${it.toIntOrNull()?.coerceIn(1,365)?:1}"}} }
   }
  }
  TextButton(onClick={rewardsExpanded=!rewardsExpanded},modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).immediatePress()) { Text("奖励设置 · 娱乐 $video 分钟，游戏 $games 局${if(rewardsExpanded)"  −" else "  +"}") }
  AnimatedVisibility(rewardsExpanded) {
   Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
    Text(if(required)"必做任务完成后发放" else "选做任务完成后可选领取",style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)
    NumberField("娱乐分钟",video){video=it}
    NumberField("游戏局数",games){games=it}
   }
  }
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
   val canSave=title.isNotBlank() && recurrence!="days:" && (!recurrence.startsWith("interval:") || (intervalDays.toIntOrNull()?:0) in 1..365) && (!db.contract(day).confirmed || !required)
   Button(enabled=canSave,onClick={ db.addTask(day,title,required,minutes.toIntOrNull()?:0,mode,checklist,video.toIntOrNull()?:0,games.toIntOrNull()?:0,recurrence); onSave() },modifier=Modifier.weight(1f).heightIn(min=48.dp).clip(RoundedCornerShape(50)).immediatePress(canSave)) {Text("保存任务")}
   TextButton(onClick=onCancel,modifier=Modifier.heightIn(min=48.dp)){Text("取消")}
  }
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
