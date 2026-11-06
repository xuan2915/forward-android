package com.focus.prototype

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.SystemClock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class Task(val id: String, val date: String, val title: String, val required: Boolean, val minutes: Int, val mode: String, val checklist: String, val rewardMinutes: Int, val rewardGames: Int, val elapsed: Long, val runningSince: Long, val bootTime: Long, val evidence: String, val done: Boolean, val claimed: Boolean)
data class Contract(val date: String, val confirmed: Boolean, val rest: Boolean, val baseMinutes: Int, val baseGames: Int, val baseClaimed: Boolean, val emergency: Boolean)
data class SleepRecord(val date:String,val bedtime:String,val wakeTime:String,val bedtimePenalty:Long,val wakePenalty:Long,val excepted:Boolean,val reason:String)

class Database(context: Context, name:String = "journey.db", private val now:()->LocalDateTime = { LocalDateTime.now() }) : SQLiteOpenHelper(context, name, null, 4), java.io.Closeable {
 override fun onCreate(db: SQLiteDatabase) {
  db.execSQL("CREATE TABLE contracts(day TEXT PRIMARY KEY, confirmed INTEGER NOT NULL DEFAULT 0, rest INTEGER NOT NULL DEFAULT 0, base_minutes INTEGER NOT NULL DEFAULT 30, base_games INTEGER NOT NULL DEFAULT 0, base_claimed INTEGER NOT NULL DEFAULT 0, emergency INTEGER NOT NULL DEFAULT 0, boundary INTEGER NOT NULL DEFAULT 4)")
  db.execSQL("CREATE TABLE tasks(id TEXT PRIMARY KEY, day TEXT NOT NULL, title TEXT NOT NULL, required INTEGER NOT NULL, minutes INTEGER NOT NULL, mode TEXT NOT NULL, checklist TEXT NOT NULL, reward_minutes INTEGER NOT NULL, reward_games INTEGER NOT NULL, elapsed INTEGER NOT NULL DEFAULT 0, running_since INTEGER NOT NULL DEFAULT 0, boot_time INTEGER NOT NULL DEFAULT 0, evidence TEXT NOT NULL DEFAULT '', done INTEGER NOT NULL DEFAULT 0, claimed INTEGER NOT NULL DEFAULT 0, recurrence TEXT NOT NULL DEFAULT 'once', origin TEXT NOT NULL DEFAULT '')")
  db.execSQL("CREATE TABLE ledger(id TEXT PRIMARY KEY, day TEXT NOT NULL, task_id TEXT NOT NULL, kind TEXT NOT NULL, amount INTEGER NOT NULL, created INTEGER NOT NULL)")
  db.execSQL("CREATE TABLE reviews(day TEXT PRIMARY KEY, problem TEXT NOT NULL, praise TEXT NOT NULL, note TEXT NOT NULL)")
  db.execSQL("CREATE TABLE audit(id TEXT PRIMARY KEY, day TEXT NOT NULL, detail TEXT NOT NULL, created INTEGER NOT NULL)")
  db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
  db.execSQL("CREATE TABLE task_moves(source_id TEXT PRIMARY KEY,target_id TEXT NOT NULL,from_day TEXT NOT NULL,to_day TEXT NOT NULL,created INTEGER NOT NULL)")
  upgradeTemplates(db)
  upgradeAttachments(db)
 }
 override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { if(oldVersion<2) upgradeTemplates(db);if(oldVersion<3) upgradeAttachments(db);if(oldVersion<4)db.execSQL("CREATE TABLE IF NOT EXISTS task_moves(source_id TEXT PRIMARY KEY,target_id TEXT NOT NULL,from_day TEXT NOT NULL,to_day TEXT NOT NULL,created INTEGER NOT NULL)") }
 private fun upgradeAttachments(db:SQLiteDatabase) {
  db.execSQL("CREATE TABLE attachments(id TEXT PRIMARY KEY,task_id TEXT NOT NULL,path TEXT NOT NULL,created INTEGER NOT NULL)")
  seedAttachments(db)
 }
 fun seedAttachments(db:SQLiteDatabase=writableDatabase) {
  db.execSQL("INSERT OR IGNORE INTO attachments(id,task_id,path,created) SELECT id,id,evidence,0 FROM tasks WHERE evidence<>''")
 }
 private fun upgradeTemplates(db:SQLiteDatabase) {
  db.execSQL("ALTER TABLE tasks ADD COLUMN template_id TEXT NOT NULL DEFAULT ''")
  db.execSQL("CREATE TABLE templates(id TEXT PRIMARY KEY, start_day TEXT NOT NULL, rule TEXT NOT NULL, data TEXT NOT NULL)")
  seedTemplates(db)
 }
 fun seedTemplates(db:SQLiteDatabase=writableDatabase) {
  db.rawQuery("SELECT * FROM tasks WHERE day=origin",null).use { c -> while(c.moveToNext()) {
   val id=c.s("id"); val data=org.json.JSONObject();listOf("title","required","minutes","mode","checklist","reward_minutes","reward_games").forEach {data.put(it,c.s(it))}
   val old=org.json.JSONObject(db.rawQuery("SELECT value FROM settings WHERE key=?",arrayOf("template_$id")).use {if(it.moveToFirst())it.getString(0) else "{}"})
   listOf("title","required","minutes","mode","checklist").forEach {if(old.has(it))data.put(it,old.get(it))};if(old.has("video"))data.put("reward_minutes",old.get("video"));if(old.has("games"))data.put("reward_games",old.get("games"))
   db.insertWithOnConflict("templates",null,cv("id" to id,"start_day" to c.s("day"),"rule" to old.optString("recurrence",c.s("recurrence")),"data" to data.toString()),SQLiteDatabase.CONFLICT_IGNORE)
   db.update("tasks",cv("template_id" to id),"id=?",arrayOf(id))
   db.rawQuery("SELECT DISTINCT day FROM tasks WHERE origin=? AND day<>origin",arrayOf(c.s("day"))).use { dates -> while(dates.moveToNext()) { val instance=UUID.nameUUIDFromBytes((id+dates.getString(0)).toByteArray()).toString();db.update("tasks",cv("template_id" to id),"id=?",arrayOf(instance)) } }
  } }
 }
 private fun cv(vararg fields: Pair<String, Any?>) = ContentValues().apply { fields.forEach { (k, v) -> when (v) { is String -> put(k,v); is Int -> put(k,v); is Long -> put(k,v); is Boolean -> put(k,if(v) 1 else 0); null -> putNull(k) } } }
 private fun Cursor.s(name: String) = getString(getColumnIndexOrThrow(name))
 private fun Cursor.i(name: String) = getInt(getColumnIndexOrThrow(name))
 private fun Cursor.l(name: String) = getLong(getColumnIndexOrThrow(name))
 private fun Cursor.b(name: String) = i(name) != 0
 private fun Cursor.task() = Task(s("id"),s("day"),s("title"),b("required"),i("minutes"),s("mode"),s("checklist"),i("reward_minutes"),i("reward_games"),l("elapsed"),l("running_since"),l("boot_time"),s("evidence"),b("done"),b("claimed"))
 fun setting(key: String, default: String = ""): String = readableDatabase.rawQuery("SELECT value FROM settings WHERE key=?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else default }
 fun setSetting(key: String, value: String) { writableDatabase.insertWithOnConflict("settings", null, cv("key" to key,"value" to value), SQLiteDatabase.CONFLICT_REPLACE) }
 fun importPlan(target: String, drafts: List<PlanDraft>) {
  val db=writableDatabase; db.beginTransaction()
  try { require(!contract(target).confirmed); val existing=tasks(target).map { it.title }.toSet(); drafts.filter { it.title !in existing }.forEach { addTask(target,it.title,it.required,0,"行动型","按计划完成并自检",20,1,"once") }; setSetting("import_draft",""); audit(target,"从文本导入 ${drafts.size} 项待确认任务"); db.setTransactionSuccessful() } finally { db.endTransaction() }
 }
 fun day(): String {
  val now = now(); val boundary = setting("boundary", "4").toIntOrNull()?.coerceIn(0, 23) ?: 4
  val pending=setting("boundary_effective"); if(pending.isNotEmpty()) { if(now >= LocalDateTime.parse(pending)) { val next=setting("boundary_next").toInt(); setSetting("boundary",next.toString()); setSetting("boundary_effective",""); return Rules.day(now,next) };return minOf(Rules.day(now,boundary),setting("boundary_previous_day",Rules.day(now,boundary))) }; return Rules.day(now,boundary)
 }
 fun ensure(day: String = day()) {
  val db = writableDatabase
  if(day==day() && setting("generated_through")!=day) {
   val last=setting("generated_through",db.rawQuery("SELECT MAX(day) FROM contracts WHERE day<?",arrayOf(day)).use{it.moveToFirst();it.getString(0)?:day})
   var date=LocalDate.parse(last).plusDays(1)
   while(date.toString()<day){ensure(date.toString());date=date.plusDays(1)}
   setSetting("generated_through",day)
  }
  if(day==day() && setting("expiry_checked")!=day) {
   allocateLegacySpending(db,day,"video");allocateLegacySpending(db,day,"game")
   db.beginTransaction()
   try { val expired=mutableListOf<Triple<String,String,Long>>(); db.rawQuery("SELECT day,kind,SUM(amount) FROM ledger WHERE day<? GROUP BY day,kind HAVING SUM(amount)>0",arrayOf(day)).use { c->while(c.moveToNext()) expired.add(Triple(c.getString(0),c.getString(1),c.getLong(2))) };expired.forEach { (date,kind,amount)->val created=System.currentTimeMillis();ledgerAt(db,date,"expiry",kind,-amount,created);allocateSpend(db,date,kind,amount,created);audit(date,"未使用奖励过期：$kind / $amount") };setSetting("expiry_checked",day);db.setTransactionSuccessful() } finally {db.endTransaction()}
  }
  val confirmed = db.rawQuery("SELECT confirmed FROM contracts WHERE day=?", arrayOf(day)).use { if(it.moveToFirst()) it.getInt(0) != 0 else null }
  if (confirmed == true || confirmed != null && day < day()) { issueCompletedRequiredRewards(db,day); return }
  db.beginTransaction()
  try {
   if(confirmed == null) db.insert("contracts", null, cv("day" to day, "boundary" to setting("boundary", "4").toInt()))
   if(!db.rawQuery("SELECT rest FROM contracts WHERE day=?",arrayOf(day)).use {it.moveToFirst();it.getInt(0)!=0}) {
    db.rawQuery("SELECT * FROM templates WHERE start_day<?",arrayOf(day)).use { c -> while(c.moveToNext()) {
     val id=c.s("id"); val rule=c.s("rule")
     if(Rules.repeats(rule,c.s("start_day"),day)) {
      val instanceId=UUID.nameUUIDFromBytes((id+day).toByteArray()).toString()
      if(setting("deleted_$instanceId")!="true") {
       val data=org.json.JSONObject(c.s("data"));val values=ContentValues()
       listOf("title","required","minutes","mode","checklist","reward_minutes","reward_games").forEach {values.put(it,data.getString(it))}
       values.put("id",instanceId);values.put("day",day);values.put("origin",c.s("start_day"));values.put("template_id",id);values.put("recurrence",rule)
       db.insertWithOnConflict("tasks",null,values,SQLiteDatabase.CONFLICT_IGNORE)
      }
     }
    } }
   }
   db.setTransactionSuccessful()
  } finally { db.endTransaction() }
  issueCompletedRequiredRewards(db,day)
 }
 fun contract(day: String = day()): Contract { ensure(day); return readableDatabase.rawQuery("SELECT * FROM contracts WHERE day=?", arrayOf(day)).use { c -> c.moveToFirst(); Contract(day,c.b("confirmed"),c.b("rest"),c.i("base_minutes"),c.i("base_games"),c.b("base_claimed"),c.b("emergency")) } }
 fun tasks(day: String = day(), includeMoved:Boolean=false): List<Task> { ensure(day); val list = mutableListOf<Task>(); readableDatabase.rawQuery("SELECT * FROM tasks WHERE day=? ${if(includeMoved) "" else "AND id NOT IN (SELECT source_id FROM task_moves)"} ORDER BY required DESC, rowid", arrayOf(day)).use { c -> while(c.moveToNext()) list.add(c.task()) }; return list }
 fun task(id:String):Task? = readableDatabase.rawQuery("SELECT * FROM tasks WHERE id=? AND id NOT IN (SELECT source_id FROM task_moves)",arrayOf(id)).use { if(it.moveToFirst()) it.task() else null }
 fun runningTasks(): List<Task> { val days=mutableListOf<String>(); readableDatabase.rawQuery("SELECT DISTINCT day FROM tasks WHERE running_since>0",null).use { while(it.moveToNext()) days.add(it.getString(0)) }; return days.flatMap { tasks(it).filter { t -> t.runningSince>0 } } }
 fun addTask(day: String, title: String, required: Boolean, minutes: Int, mode: String, checklist: String, rewardMinutes: Int, rewardGames: Int, recurrence: String) {
  ensure(day); val contract = contract(day); require(title.isNotBlank() && (!contract.confirmed || !required))
  val db=writableDatabase;db.beginTransaction()
  try {
   val id=UUID.randomUUID().toString()
   val data=org.json.JSONObject().put("title",title.trim()).put("required",if(required) 1 else 0).put("minutes",minutes).put("mode",mode).put("checklist",checklist).put("reward_minutes",rewardMinutes).put("reward_games",rewardGames)
   db.insertOrThrow("templates",null,cv("id" to id,"start_day" to day,"rule" to recurrence,"data" to data.toString()))
   db.insertOrThrow("tasks",null,cv("id" to id,"template_id" to id,"day" to day,"title" to title.trim(),"required" to required,"minutes" to minutes,"mode" to mode,"checklist" to checklist,"reward_minutes" to rewardMinutes,"reward_games" to rewardGames,"recurrence" to recurrence,"origin" to day))
   db.setTransactionSuccessful()
  } finally {db.endTransaction()}

 }
 fun confirm(day: String, rest: Boolean, minutes: Int, games: Int) {
  val c = contract(day); require(!c.confirmed); require(!rest || tasks(day).none { it.required })
  writableDatabase.update("contracts",cv("confirmed" to true,"rest" to rest,"base_minutes" to minutes,"base_games" to games),"day=?", arrayOf(day))
 }
 fun eligible(day: String = day()): Boolean { val c = contract(day); val required = tasks(day,includeMoved=true).filter { it.required }; return setting("gate_changed_$day")!="true" && c.confirmed && (required.isNotEmpty() && required.all { it.done } || required.isEmpty() && c.rest) }
 private fun ledger(db: SQLiteDatabase, day: String, task: String, kind: String, amount: Long) = ledgerAt(db,day,task,kind,amount,System.currentTimeMillis())
 private fun ledgerAt(db: SQLiteDatabase, day: String, task: String, kind: String, amount: Long,created:Long) { db.insertOrThrow("ledger", null, cv("id" to UUID.randomUUID().toString(),"day" to day,"task_id" to task,"kind" to kind,"amount" to amount,"created" to created)) }
 private fun allocationKind(kind:String)="${kind}_allocation"
 private fun appendDebit(db:SQLiteDatabase,day:String,source:String,kind:String,amount:Long,at:Long) {
  val previous=db.rawQuery("SELECT id,amount,created FROM ledger WHERE day=? AND task_id=? AND kind=? ORDER BY created DESC,rowid DESC LIMIT 1",arrayOf(day,source,kind)).use{if(it.moveToFirst())Triple(it.getString(0),it.getLong(1),it.getLong(2))else null}
  if(previous!=null&&at-previous.third<60_000)db.update("ledger",cv("amount" to previous.second-amount,"created" to at),"id=?",arrayOf(previous.first)) else ledgerAt(db,day,source,kind,-amount,at)
 }
 private fun allocateSpend(db:SQLiteDatabase,day:String,kind:String,amount:Long,at:Long) {
  var remaining=amount
  val sources=mutableListOf<String>()
  db.rawQuery("SELECT task_id FROM ledger WHERE day=? AND kind=? AND amount>0 AND created<=? AND task_id NOT IN ('usage','expiry') GROUP BY task_id ORDER BY MIN(created),MIN(rowid)",arrayOf(day,kind,at.toString())).use{while(it.moveToNext())sources.add(it.getString(0))}
  for(source in sources) {
   if(remaining<=0)break
   val granted=db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM ledger WHERE day=? AND kind=? AND task_id=? AND created<=?",arrayOf(day,kind,source,at.toString())).use{it.moveToFirst();it.getLong(0)}
   val spent=db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM ledger WHERE day=? AND kind=? AND task_id=? AND created<=?",arrayOf(day,allocationKind(kind),source,at.toString())).use{it.moveToFirst();it.getLong(0)}
   val available=(granted+spent).coerceAtLeast(0);val used=minOf(available,remaining)
   if(used>0){appendDebit(db,day,source,allocationKind(kind),used,at);remaining-=used}
  }
 }
 private fun allocateLegacySpending(db:SQLiteDatabase,day:String,kind:String) {
  val marker="allocation_ready_${day}_$kind";if(setting(marker)=="true")return
  val events=mutableListOf<Pair<Long,Long>>()
  db.rawQuery("SELECT created,-amount FROM ledger WHERE day=? AND kind=? AND amount<0 AND task_id IN ('usage','expiry') ORDER BY created,rowid",arrayOf(day,kind)).use{while(it.moveToNext())events.add(it.getLong(0) to it.getLong(1))}
  events.forEach{(time,amount)->allocateSpend(db,day,kind,amount,time)}
  setSetting(marker,"true")
 }
 fun balance(kind: String, day: String = day()): Long = readableDatabase.rawQuery("SELECT COALESCE(SUM(amount),0) FROM ledger WHERE day=? AND kind=?",arrayOf(day,kind)).use { it.moveToFirst(); it.getLong(0).coerceAtLeast(0) }
 fun consume(kind: String, amount: Long, day: String = day()) { val db = writableDatabase; db.beginTransaction(); try { allocateLegacySpending(db,day,kind);val actual = minOf(balance(kind,day),amount.coerceAtLeast(0)); if(actual > 0) {val created=System.currentTimeMillis();appendDebit(db,day,"usage",kind,actual,created);allocateSpend(db,day,kind,actual,created)}; db.setTransactionSuccessful() } finally { db.endTransaction() } }
 fun claim(task: Task, kind: String) { val db = writableDatabase; db.beginTransaction(); try { val fresh = tasks(task.date).first { it.id == task.id }; if (eligible(task.date) && !fresh.required && fresh.done && !fresh.claimed && task.date == day()) { val amount = if(kind == "video") fresh.rewardMinutes * 60000L else fresh.rewardGames.toLong(); require(amount > 0); ledger(db,task.date,task.id,kind,amount); db.update("tasks",cv("claimed" to true),"id=?",arrayOf(task.id)) }; db.setTransactionSuccessful() } finally { db.endTransaction() } }
 fun start(task: Task) { val fresh=tasks(task.date).first{it.id==task.id};if(fresh.done || fresh.runningSince != 0L) return; runningTasks().forEach{pause(it)};writableDatabase.update("tasks",cv("running_since" to SystemClock.elapsedRealtime(),"boot_time" to (System.currentTimeMillis()-SystemClock.elapsedRealtime())),"id=?",arrayOf(task.id)) }
 fun elapsed(task: Task): Long { val sameBoot = kotlin.math.abs((System.currentTimeMillis()-SystemClock.elapsedRealtime()) - task.bootTime) < 120000; return task.elapsed + if(task.runningSince > 0 && sameBoot) (SystemClock.elapsedRealtime()-task.runningSince).coerceAtLeast(0) else 0 }
 fun pause(task: Task) { val fresh=tasks(task.date).first{it.id==task.id};if(fresh.runningSince==0L)return;writableDatabase.update("tasks",cv("elapsed" to elapsed(fresh),"running_since" to 0L,"boot_time" to 0L),"id=?",arrayOf(task.id)) }
 fun restart(task:Task) {require(!tasks(task.date).first{it.id==task.id}.done);writableDatabase.update("tasks",cv("elapsed" to 0L,"running_since" to 0L,"boot_time" to 0L),"id=?",arrayOf(task.id));audit(task.date,"重新计时：${task.title}")}
 fun evidence(task: Task, path: String) { val db=writableDatabase;db.beginTransaction();try {db.insertOrThrow("attachments",null,cv("id" to UUID.randomUUID().toString(),"task_id" to task.id,"path" to path,"created" to System.currentTimeMillis()));db.update("tasks",cv("evidence" to path),"id=?",arrayOf(task.id));db.setTransactionSuccessful()}finally{db.endTransaction()} }
 fun attachments(task:Task):List<Pair<String,String>> { val result=mutableListOf<Pair<String,String>>();readableDatabase.rawQuery("SELECT id,path FROM attachments WHERE task_id=? ORDER BY created",arrayOf(task.id)).use{c->while(c.moveToNext())result.add(c.getString(0) to c.getString(1))};return result }
 fun removeAttachment(task:Task,id:String) {require(!task.done);val db=writableDatabase;db.beginTransaction();try{db.delete("attachments","id=? AND task_id=?",arrayOf(id,task.id));val latest=attachments(task).lastOrNull()?.second.orEmpty();db.update("tasks",cv("evidence" to latest),"id=?",arrayOf(task.id));db.setTransactionSuccessful()}finally{db.endTransaction()} }
 fun checkpoint() {runningTasks().forEach{task->writableDatabase.update("tasks",cv("elapsed" to elapsed(task),"running_since" to SystemClock.elapsedRealtime(),"boot_time" to (System.currentTimeMillis()-SystemClock.elapsedRealtime())),"id=?",arrayOf(task.id))} }
 fun complete(task: Task) {
  pause(task)
  val db=writableDatabase;db.beginTransaction()
  try {
   val fresh=tasks(task.date).first { it.id == task.id }
   if(fresh.done) { db.setTransactionSuccessful();return }
   require(fresh.elapsed >= fresh.minutes * 60000L && (fresh.mode == "行动型" || fresh.evidence.isNotBlank()))
   db.update("tasks",cv("done" to true),"id=?",arrayOf(task.id))
   if(task.date==day() && fresh.required) {
    listOf("video" to fresh.rewardMinutes.toLong()*60000L,"game" to fresh.rewardGames.toLong()).forEach { (kind,amount) ->
     val key="task_reward_${fresh.id}_$kind"
     if(amount>0 && setting(key)!="true") { ledger(db,task.date,fresh.id,kind,amount);setSetting(key,"true") }
    }
   }
   db.setTransactionSuccessful()
  } finally { db.endTransaction() }
 }
 fun removeDraft(task: Task) { require(!contract(task.date).confirmed && !task.done); deleteTask(task,"") }
 fun deleteTask(task:Task,reason:String) {
  val db=writableDatabase;db.beginTransaction()
  try {
   val fresh=tasks(task.date).first{it.id==task.id};val frozen=contract(task.date).confirmed
   require(!fresh.done && (!frozen || reason.isNotBlank()))
   if(frozen) {audit(task.date,"计划删除：${fresh.title}；原因：${reason.trim()}");setSetting("changed_${task.date}","true");if(fresh.required)setSetting("gate_changed_${task.date}","true")}
   setSetting("deleted_${task.id}", "true")
   db.delete("attachments","task_id=?",arrayOf(task.id));db.delete("tasks","id=?",arrayOf(task.id));db.setTransactionSuccessful()
  } finally {db.endTransaction()}
 }
 fun moveTask(task:Task,targetDay:String,reason:String=""):String {
  require(targetDay>=day() && targetDay!=task.date)
  pause(task)
  val db=writableDatabase;db.beginTransaction()
  return try {
   val fresh=tasks(task.date).first{it.id==task.id};val frozen=contract(task.date).confirmed
   require(!fresh.done && !contract(targetDay).confirmed && (!frozen || !fresh.required || reason.isNotBlank()))
   val source=db.rawQuery("SELECT template_id,recurrence,origin FROM tasks WHERE id=?",arrayOf(task.id)).use{it.moveToFirst();Triple(it.getString(0),it.getString(1),it.getString(2))}
   val reusableTarget=if(source.first.isBlank())null else db.rawQuery("SELECT id FROM tasks WHERE template_id=? AND day=? AND done=0 AND elapsed=0 AND running_since=0 AND evidence='' AND id NOT IN (SELECT source_id FROM task_moves) LIMIT 1",arrayOf(source.first,targetDay)).use{if(it.moveToFirst())it.getString(0)else null}
   val targetId=reusableTarget?:UUID.randomUUID().toString()
   val targetValues=cv("title" to fresh.title,"required" to fresh.required,"minutes" to fresh.minutes,"mode" to fresh.mode,"checklist" to fresh.checklist,"reward_minutes" to fresh.rewardMinutes,"reward_games" to fresh.rewardGames,"elapsed" to fresh.elapsed,"running_since" to 0L,"boot_time" to 0L,"evidence" to fresh.evidence,"done" to false,"claimed" to false,"recurrence" to source.second,"origin" to source.third)
   if(reusableTarget!=null)db.update("tasks",targetValues,"id=?",arrayOf(targetId)) else db.insertOrThrow("tasks",null,targetValues.apply{put("id",targetId);put("template_id",source.first);put("day",targetDay)})
   db.insertOrThrow("task_moves",null,cv("source_id" to task.id,"target_id" to targetId,"from_day" to task.date,"to_day" to targetDay,"created" to System.currentTimeMillis()))
   db.update("attachments",cv("task_id" to targetId),"task_id=?",arrayOf(task.id))
   if(frozen){audit(task.date,"任务延期：${fresh.title} → $targetDay"+(if(reason.isBlank())"" else "；原因：$reason"));setSetting("changed_${task.date}","true")}
   audit(targetDay,"接收延期任务：${fresh.title}（来自 ${task.date}）")
   db.setTransactionSuccessful();targetId
  } finally {db.endTransaction()}
 }
 fun scheduleBoundary(hour:Int) { require(hour in 0..23); val date=day();val old=setting("boundary","4").toInt(); val effective=LocalDate.parse(date).plusDays(1).atStartOfDay().plusHours(maxOf(old,hour).toLong());setSetting("boundary_previous_day",date); setSetting("boundary_next",hour.toString()); setSetting("boundary_effective",effective.toString()); audit(date,"日界线将于 $effective 改为 $hour:00") }
 fun planRest(date:String,minutes:Int=30,games:Int=0) {
  require(date>day());val db=writableDatabase;db.beginTransaction()
  try { require(!contract(date).confirmed);val original=tasks(date).filter{it.required}.joinToString {it.title};db.update("tasks",cv("required" to false),"day=? AND done=0",arrayOf(date));db.update("contracts",cv("rest" to true,"confirmed" to true,"base_minutes" to minutes,"base_games" to games),"day=?",arrayOf(date));audit(date,"预设休息日；原必做改为选做：$original");db.setTransactionSuccessful() } finally {db.endTransaction()}
 }
 fun settleToday() { contract(day()) }
 fun bestStreak():Int {
  val states=history().filter{it.first<=day()}.sortedBy{it.first};var best=0;var count=0;var previous:LocalDate?=null
  for((date,state) in states){val d=LocalDate.parse(date);if(previous!=null && d!=previous!!.plusDays(1))count=0;when(state){"完成"->count++;"休息"->Unit;else->count=0};best=maxOf(best,count);previous=d};return best
 }
 fun revise(task:Task,title:String,required:Boolean,minutes:Int,mode:String,checklist:String,video:Int,games:Int,recurrence:String,reason:String,delete:Boolean=false,applyFuture:Boolean=false) {
  val db=writableDatabase; db.beginTransaction()
  try { val frozen=contract(task.date).confirmed; require(!task.done && title.isNotBlank() && (!frozen || reason.isNotBlank())); if(frozen) { audit(task.date,"计划变更：${task.title} / 必做=${task.required} / ${task.minutes}分钟 / ${task.mode} / ${task.checklist} → $title / 必做=$required / $minutes 分钟 / $mode / $checklist / 删除=$delete；原因：$reason"); setSetting("changed_${task.date}","true") }
   if(frozen && task.required && (delete || !required))setSetting("gate_changed_${task.date}","true")
   val templateId=db.rawQuery("SELECT template_id FROM tasks WHERE id=?",arrayOf(task.id)).use {it.moveToFirst();it.getString(0)}
   if(applyFuture && templateId.isNotBlank()) {
    val data=org.json.JSONObject().put("title",title).put("required",if(required) 1 else 0).put("minutes",minutes).put("mode",mode).put("checklist",checklist).put("reward_minutes",video).put("reward_games",games)
    db.update("templates",cv("rule" to if(delete) "once" else recurrence,"data" to data.toString()),"id=?",arrayOf(templateId))
    db.delete("tasks","template_id=? AND day>? AND done=0 AND day IN (SELECT day FROM contracts WHERE confirmed=0)",arrayOf(templateId,task.date))
   }
   if(delete) { setSetting("deleted_${task.id}","true");db.delete("tasks","id=?",arrayOf(task.id)) }
   else db.update("tasks",cv("title" to title,"required" to required,"minutes" to minutes,"mode" to mode,"checklist" to checklist,"reward_minutes" to video,"reward_games" to games,"recurrence" to recurrence),"id=?",arrayOf(task.id))
   db.setTransactionSuccessful()
  } finally { db.endTransaction() }
 }
 fun editDraft(task: Task, title:String, minutes:Int, checklist:String) { require(!contract(task.date).confirmed && !task.done && title.isNotBlank()); writableDatabase.update("tasks",cv("title" to title.trim(),"minutes" to minutes,"checklist" to checklist),"id=?",arrayOf(task.id)) }
 fun recurrence(task:Task):String = readableDatabase.rawQuery("SELECT recurrence FROM tasks WHERE id=?",arrayOf(task.id)).use {if(it.moveToFirst())it.getString(0) else "once"}
 fun rewardHistory(): List<String> {
  val out=mutableListOf<String>()
  readableDatabase.rawQuery("SELECT l.day,l.kind,l.amount,l.task_id,l.created,t.title FROM ledger l LEFT JOIN tasks t ON t.id=l.task_id WHERE l.amount<>0 AND l.kind NOT LIKE '%_allocation' ORDER BY l.created DESC,l.rowid DESC LIMIT 100",null).use {c->while(c.moveToNext()) {
   val amount=c.getLong(2);val source=c.getString(3);val date=LocalDate.parse(c.getString(0));val absolute=kotlin.math.abs(amount);val value=if(c.getString(1)=="video") {val minutes=absolute/60000;val seconds=absolute%60000/1000;when{minutes==0L->"${seconds.coerceAtLeast(1)} 秒";seconds==0L->"$minutes 分钟";else->"$minutes 分 $seconds 秒"}} else "${absolute} 局"
   val label=when{source=="expiry"->"额度到期 −$value";source=="usage"->"已使用 $value";source.startsWith("sleep:")->"睡眠规则扣减 −$value";source.startsWith("sleep-refund:")->"睡眠修正返还 +$value";amount<0->"奖励冲回 −$value";source=="base"->"历史基础奖励 +$value";else->"${if(readableDatabase.rawQuery("SELECT required FROM tasks WHERE id=?",arrayOf(source)).use{it.moveToFirst()&&it.getInt(0)!=0})"必做奖励" else "选做奖励"} · ${c.getString(5)?:"历史任务"} +$value"}
   val time=java.time.Instant.ofEpochMilli(c.getLong(4)).atZone(java.time.ZoneId.systemDefault()).toLocalTime().toString().take(5);out.add("${date.monthValue}月${date.dayOfMonth}日 $time · $label")
  } }
  return out
 }
 fun revoke(task: Task) {
  val db = writableDatabase; db.beginTransaction()
  try {
   val fresh = tasks(task.date).first { it.id == task.id }
   if(fresh.done) {
    val source = fresh.id
    for(kind in listOf("video","game")) {
     allocateLegacySpending(db,task.date,kind)
     val granted = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM ledger WHERE day=? AND task_id=? AND kind=?",arrayOf(task.date,source,kind)).use { it.moveToFirst(); it.getLong(0) }
     val spent = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM ledger WHERE day=? AND task_id=? AND kind=?",arrayOf(task.date,source,allocationKind(kind))).use { it.moveToFirst(); it.getLong(0) }
     val reversal = minOf((granted+spent).coerceAtLeast(0),balance(kind,task.date))
     if(reversal > 0) ledger(db,task.date,"revoke:$source",kind,-reversal)
    }
    db.update("tasks",cv("done" to false),"id=?",arrayOf(task.id)); audit(task.date,"撤销任务：${fresh.title}；已领取额度按剩余余额冲正")
   }
   db.setTransactionSuccessful()
  } finally { db.endTransaction() }
 }
 fun sleepTargets()=listOf(setting("sleep_bed_target","01:00"),setting("sleep_wake_target","07:30"),setting("sleep_bed_penalty","15"),setting("sleep_wake_penalty","15"))
 fun saveSleepTargets(bed:String,wake:String,bedPenalty:Int,wakePenalty:Int) {
  java.time.LocalTime.parse(bed);java.time.LocalTime.parse(wake);require(bedPenalty>=0&&wakePenalty>=0)
  setSetting("sleep_bed_target",bed);setSetting("sleep_wake_target",wake);setSetting("sleep_bed_penalty",bedPenalty.toString());setSetting("sleep_wake_penalty",wakePenalty.toString())
 }
 fun sleepRecord(date:String=day()):SleepRecord? {
  val json=setting("sleep_record_$date");if(json.isBlank())return null
  val o=org.json.JSONObject(json);return SleepRecord(date,o.getString("bedtime"),o.getString("wake"),o.getLong("bed_penalty"),o.getLong("wake_penalty"),o.getBoolean("excepted"),o.optString("reason"))
 }
 fun recordSleep(date:String,bedtime:String,wake:String,excepted:Boolean=false,reason:String="") {
  val bed=java.time.LocalTime.parse(bedtime);val woke=java.time.LocalTime.parse(wake);val old=sleepRecord(date)
  require(date==day() && (!excepted||reason.isNotBlank()) && (old==null || old.bedtime==bedtime&&old.wakeTime==wake&&old.excepted==excepted || reason.isNotBlank()))
  val targets=sleepTargets();val bedTarget=java.time.LocalTime.parse(targets[0]);val wakeTarget=java.time.LocalTime.parse(targets[1])
  val bedLate=if(bedTarget.hour<6&&bed.hour>=18) false else bed.isAfter(bedTarget)
  val desiredBed=if(!excepted&&bedLate)targets[2].toLong()*60000 else 0L
  val desiredWake=if(!excepted&&woke.isAfter(wakeTarget))targets[3].toLong()*60000 else 0L
  val db=writableDatabase;db.beginTransaction()
  try {
   fun reconcile(kind:String,oldAmount:Long,newAmount:Long,source:String):Long {
    val delta=newAmount-oldAmount
    if(delta>0){val charged=minOf(balance(kind,date),delta);if(charged>0){val at=System.currentTimeMillis();ledgerAt(db,date,"sleep:$source:$date",kind,-charged,at);allocateSpend(db,date,kind,charged,at)};return oldAmount+charged}
    if(delta<0){val refund=minOf(oldAmount,-delta);if(refund>0)ledger(db,date,"sleep-refund:$source:$date:${System.currentTimeMillis()}",kind,refund);return oldAmount-refund}
    return oldAmount
   }
   val bedApplied=reconcile("video",old?.bedtimePenalty?:0,desiredBed,"bedtime")
   val wakeApplied=reconcile("video",old?.wakePenalty?:0,desiredWake,"wake")
   val record=org.json.JSONObject().put("bedtime",bedtime).put("wake",wake).put("bed_penalty",bedApplied).put("wake_penalty",wakeApplied).put("excepted",excepted).put("reason",reason.trim())
   setSetting("sleep_record_$date",record.toString())
   val detail=if(excepted) "睡眠例外：${reason.trim()}" else "手动睡眠记录：就寝 $bedtime，起床 $wake；扣减 ${(bedApplied+wakeApplied)/60000} 分钟"+(if(old!=null)"；修正原因：${reason.trim()}" else "")
   audit(date,detail);db.setTransactionSuccessful()
  } finally {db.endTransaction()}
 }
 private fun issueCompletedRequiredRewards(db:SQLiteDatabase,date:String) {
  if(date!=day())return
  val ownsTransaction=!db.inTransaction();if(ownsTransaction)db.beginTransaction()
  try {db.rawQuery("SELECT * FROM tasks WHERE day=? AND required=1 AND done=1",arrayOf(date)).use{c->while(c.moveToNext()){
    val task=c.task()
    listOf("video" to task.rewardMinutes.toLong()*60000L,"game" to task.rewardGames.toLong()).forEach{(kind,amount)->
     val key="task_reward_${task.id}_$kind"
     if(amount>0&&setting(key)!="true") {ledger(db,date,task.id,kind,amount);setSetting(key,"true")}
    }
   }};if(ownsTransaction)db.setTransactionSuccessful()
  } finally {if(ownsTransaction)db.endTransaction()}
 }
 fun emergency(day: String = day(),reason:String="") { ensure(day); writableDatabase.update("contracts",cv("emergency" to true),"day=?",arrayOf(day)); audit(day,"紧急解锁"+if(reason.isNotBlank()) "：$reason" else "") }
 fun resumeProtection(day: String = day()) { writableDatabase.update("contracts",cv("emergency" to false),"day=?",arrayOf(day)); audit(day,"恢复保护") }
 fun learningActive(): Boolean = System.currentTimeMillis() < (setting("learning_until", "0").toLongOrNull() ?: 0)
 fun startLearning() { setSetting("learning_until",(System.currentTimeMillis()+15*60000).toString()); audit(day(),"B站学习窗口：15 分钟") }
 fun audit(day: String, detail: String) { writableDatabase.insert("audit",null,cv("id" to UUID.randomUUID().toString(),"day" to day,"detail" to detail,"created" to System.currentTimeMillis())) }
 fun review(day: String, problem: String, praise: String, note: String) { writableDatabase.insertWithOnConflict("reviews",null,cv("day" to day,"problem" to problem,"praise" to praise,"note" to note),SQLiteDatabase.CONFLICT_REPLACE) }
 fun readReview(day: String): List<String> = readableDatabase.rawQuery("SELECT problem,praise,note FROM reviews WHERE day=?",arrayOf(day)).use { if(it.moveToFirst()) listOf(it.getString(0),it.getString(1),it.getString(2)) else listOf("","","") }
 fun history(): List<Pair<String,String>> { val days=mutableListOf<String>(); readableDatabase.rawQuery("SELECT day FROM contracts ORDER BY day DESC",null).use { while(it.moveToNext()) days.add(it.getString(0)) }; return days.map { d -> val c=contract(d); d to when { !c.confirmed -> if(d<day()) "未完成" else "待确认"; c.rest -> "休息"; eligible(d) -> "完成"; else -> "未完成" } } }
 fun xp(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM tasks WHERE done=1 AND id NOT IN (SELECT source_id FROM task_moves)", null).use { it.moveToFirst(); it.getInt(0)*100 }
 fun streak(): Int {
  val states=history().toMap(); var d=LocalDate.parse(day()); var count=0
  if(states[d.toString()] != "完成" && states[d.toString()] != "休息") d=d.minusDays(1)
  repeat(36500) { when(states[d.toString()]) { "完成" -> count++; "休息" -> Unit; else -> return count }; d=d.minusDays(1) }
  return count
 }
}
