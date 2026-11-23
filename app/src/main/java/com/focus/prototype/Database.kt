package com.focus.prototype

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.SystemClock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

data class Task(val id: String, val date: String, val title: String, val required: Boolean, val minutes: Int, val mode: String, val checklist: String, val rewardMinutes: Int, val rewardGames: Int, val elapsed: Long, val runningSince: Long, val bootTime: Long, val evidence: String, val done: Boolean, val claimed: Boolean, val completedAt: Long = 0L, val typeKnown: Boolean = true, val elapsedKnown: Boolean = true, val historySource: String = "", val historyConfidence: String = "", val origin: String = "")
data class Contract(val date: String, val confirmed: Boolean, val rest: Boolean, val baseMinutes: Int, val baseGames: Int, val baseClaimed: Boolean, val emergency: Boolean)
enum class SleepStatus { PASS, FAIL, PAUSED, UNVERIFIED }
data class SleepRecord(
 val date:String,
 val bedtime:String,
 val wakeTime:String,
 val bedtimePenalty:Long,
 val wakePenalty:Long,
 val excepted:Boolean,
 val reason:String,
 val source:String="manual",
 val status:SleepStatus=if(excepted) SleepStatus.PAUSED else if(bedtimePenalty==0L&&wakePenalty==0L) SleepStatus.PASS else SleepStatus.FAIL,
 val version:Int=1,
 val prepareBedtime:String?=null,
 val durationMinutes:Long=0L,
 val effectiveDate:String=date,
 val level:Int=0,
 val penaltyMinutes:Int=0,
 val penaltyGames:Int=0
)
data class RewardSnapshot(val earned:Long,val used:Long,val sleepDeducted:Long,val expired:Long,val reversed:Long,val remaining:Long,val nightDeducted:Long=0L)
data class GrowthPeriodStats(val planDays:Int,val achievedDays:Int,val weekPlanDays:Int,val weekAchievedDays:Int,val focusMillis:Long)

class Database(context: Context, name:String = "journey.db", private val wallNow:()->Long = { System.currentTimeMillis() }, private val now:()->LocalDateTime = { LocalDateTime.now() }) : SQLiteOpenHelper(context, name, null, 8), java.io.Closeable {
 override fun onCreate(db: SQLiteDatabase) {
  db.execSQL("CREATE TABLE contracts(day TEXT PRIMARY KEY, confirmed INTEGER NOT NULL DEFAULT 0, rest INTEGER NOT NULL DEFAULT 0, base_minutes INTEGER NOT NULL DEFAULT 30, base_games INTEGER NOT NULL DEFAULT 0, base_claimed INTEGER NOT NULL DEFAULT 0, emergency INTEGER NOT NULL DEFAULT 0, boundary INTEGER NOT NULL DEFAULT 4)")
  db.execSQL("CREATE TABLE tasks(id TEXT PRIMARY KEY, day TEXT NOT NULL, title TEXT NOT NULL, required INTEGER NOT NULL, minutes INTEGER NOT NULL, mode TEXT NOT NULL, checklist TEXT NOT NULL, reward_minutes INTEGER NOT NULL, reward_games INTEGER NOT NULL, elapsed INTEGER NOT NULL DEFAULT 0, running_since INTEGER NOT NULL DEFAULT 0, boot_time INTEGER NOT NULL DEFAULT 0, evidence TEXT NOT NULL DEFAULT '', done INTEGER NOT NULL DEFAULT 0, claimed INTEGER NOT NULL DEFAULT 0, recurrence TEXT NOT NULL DEFAULT 'once', origin TEXT NOT NULL DEFAULT '', completed_at INTEGER NOT NULL DEFAULT 0)")
  db.execSQL("CREATE TABLE ledger(id TEXT PRIMARY KEY, day TEXT NOT NULL, task_id TEXT NOT NULL, kind TEXT NOT NULL, amount INTEGER NOT NULL, created INTEGER NOT NULL, task_day TEXT, expire_at INTEGER NOT NULL DEFAULT 0, consumption_day TEXT)")
  db.execSQL("CREATE TABLE reviews(day TEXT PRIMARY KEY, problem TEXT NOT NULL, praise TEXT NOT NULL, note TEXT NOT NULL)")
  db.execSQL("CREATE TABLE audit(id TEXT PRIMARY KEY, day TEXT NOT NULL, detail TEXT NOT NULL, created INTEGER NOT NULL)")
  db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
  db.execSQL("CREATE TABLE task_moves(source_id TEXT PRIMARY KEY,target_id TEXT NOT NULL,from_day TEXT NOT NULL,to_day TEXT NOT NULL,created INTEGER NOT NULL)")
  db.execSQL("CREATE TABLE night_violations(id TEXT PRIMARY KEY,cycle_date TEXT NOT NULL,package_name TEXT NOT NULL,observed_at INTEGER NOT NULL,reserved_minutes INTEGER NOT NULL DEFAULT 0,reward_kind TEXT NOT NULL DEFAULT 'video',reserved_amount INTEGER NOT NULL DEFAULT 0)")
  upgradeTemplates(db)
  upgradeAttachments(db)
  upgradeHistory(db)
  upgradeLedger(db)
 }
 override fun onOpen(db: SQLiteDatabase) {
  super.onOpen(db)
  upgradeLedger(db)
 }
 override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
  if(oldVersion<2) upgradeTemplates(db)
  if(oldVersion<3) upgradeAttachments(db)
  if(oldVersion<4)db.execSQL("CREATE TABLE IF NOT EXISTS task_moves(source_id TEXT PRIMARY KEY,target_id TEXT NOT NULL,from_day TEXT NOT NULL,to_day TEXT NOT NULL,created INTEGER NOT NULL)")
  if(oldVersion<5)db.execSQL("ALTER TABLE tasks ADD COLUMN completed_at INTEGER NOT NULL DEFAULT 0")
  if(oldVersion<6) db.execSQL("CREATE TABLE IF NOT EXISTS night_violations(id TEXT PRIMARY KEY,cycle_date TEXT NOT NULL,package_name TEXT NOT NULL,observed_at INTEGER NOT NULL,reserved_minutes INTEGER NOT NULL DEFAULT 0,reward_kind TEXT NOT NULL DEFAULT 'video',reserved_amount INTEGER NOT NULL DEFAULT 0)")
  if(oldVersion in 6 until 7) {
   db.execSQL("ALTER TABLE night_violations ADD COLUMN reward_kind TEXT NOT NULL DEFAULT 'video'")
   db.execSQL("ALTER TABLE night_violations ADD COLUMN reserved_amount INTEGER NOT NULL DEFAULT 0")
   db.execSQL("UPDATE night_violations SET reserved_amount=reserved_minutes*60000")
   val legacyPending=mutableListOf<Pair<String,String>>()
   db.rawQuery("SELECT key,value FROM settings WHERE key GLOB 'night_pending_????-??-??'",null).use { c -> while(c.moveToNext())legacyPending.add(c.getString(0).removePrefix("night_pending_") to c.getString(1)) }
   legacyPending.forEach { (cycle,value) -> db.update("settings",cv("key" to "night_pending_video_$cycle","value" to value),"key=?",arrayOf("night_pending_$cycle")) }
   val legacyReserved=mutableListOf<Pair<String,String>>()
   db.rawQuery("SELECT key,value FROM settings WHERE key GLOB 'night_reserved_????-??-??'",null).use { c -> while(c.moveToNext())legacyReserved.add(c.getString(0).removePrefix("night_reserved_") to c.getString(1)) }
   legacyReserved.forEach { (cycle,value) -> db.update("settings",cv("key" to "night_reserved_video_$cycle","value" to value),"key=?",arrayOf("night_reserved_$cycle")) }
  }
  if(oldVersion<8) upgradeHistory(db)
  upgradeLedger(db)
 }
 private fun upgradeLedger(db: SQLiteDatabase) {
  val existing = mutableSetOf<String>()
  db.rawQuery("PRAGMA table_info(ledger)", null).use { while(it.moveToNext()) existing.add(it.getString(1)) }
  if ("task_day" !in existing) db.execSQL("ALTER TABLE ledger ADD COLUMN task_day TEXT")
  if ("expire_at" !in existing) db.execSQL("ALTER TABLE ledger ADD COLUMN expire_at INTEGER NOT NULL DEFAULT 0")
  if ("consumption_day" !in existing) db.execSQL("ALTER TABLE ledger ADD COLUMN consumption_day TEXT")
  db.execSQL("UPDATE ledger SET task_day=day WHERE task_day IS NULL")
 }
 private fun upgradeHistory(db: SQLiteDatabase) {
  val existing = mutableSetOf<String>()
  db.rawQuery("PRAGMA table_info(tasks)", null).use { while(it.moveToNext()) existing.add(it.getString(1)) }
  if ("type_known" in existing && "history_source" in existing && "history_confidence" in existing) return
  val original = db.rawQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name='tasks'", null).use { it.moveToFirst(); it.getString(0) }
  val replacement = original.replaceFirst(Regex("CREATE TABLE (?:`tasks`|\\\"tasks\\\"|tasks)", RegexOption.IGNORE_CASE), "CREATE TABLE tasks_history_migration")
    .replace("elapsed INTEGER NOT NULL DEFAULT 0", "elapsed INTEGER DEFAULT 0")
  require(replacement != original && !replacement.contains("elapsed INTEGER NOT NULL"))
  db.execSQL(replacement)
  val columns = mutableListOf<String>()
  db.rawQuery("PRAGMA table_info(tasks)", null).use { while(it.moveToNext()) columns.add(it.getString(1)) }
  val names = columns.joinToString(",") { "\"$it\"" }
  db.execSQL("INSERT INTO tasks_history_migration ($names) SELECT $names FROM tasks")
  db.execSQL("DROP TABLE tasks")
  db.execSQL("ALTER TABLE tasks_history_migration RENAME TO tasks")
  db.execSQL("ALTER TABLE tasks ADD COLUMN type_known INTEGER NOT NULL DEFAULT 1")
  db.execSQL("ALTER TABLE tasks ADD COLUMN history_source TEXT NOT NULL DEFAULT ''")
  db.execSQL("ALTER TABLE tasks ADD COLUMN history_confidence TEXT NOT NULL DEFAULT ''")
  db.execSQL("UPDATE tasks SET history_source=CASE WHEN checklist LIKE '%截图%' THEN '每日复盘、截图' ELSE '每日复盘' END, history_confidence='部分确认' WHERE checklist LIKE '%根据复盘%重建%'")
  db.execSQL("UPDATE tasks SET type_known=0 WHERE history_source<>'' AND required=0 AND checklist LIKE '%任务类型%未知%'")
  db.execSQL("UPDATE tasks SET elapsed=NULL WHERE history_source<>'' AND elapsed=0 AND checklist LIKE '%时长%未知%'")
 }
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
 private fun Cursor.task() = Task(s("id"),s("day"),s("title"),b("required"),i("minutes"),s("mode"),s("checklist"),i("reward_minutes"),i("reward_games"),l("elapsed"),l("running_since"),l("boot_time"),s("evidence"),b("done"),b("claimed"),l("completed_at"),b("type_known"),!isNull(getColumnIndexOrThrow("elapsed")),s("history_source"),s("history_confidence"),runCatching{s("origin")}.getOrDefault(""))
 fun confirmHistoricalType(task:Task,required:Boolean) { require(!task.typeKnown); writableDatabase.update("tasks",cv("required" to required,"type_known" to true),"id=? AND type_known=0",arrayOf(task.id)) }
 fun setting(key: String, default: String = ""): String = readableDatabase.rawQuery("SELECT value FROM settings WHERE key=?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else default }
 fun setSetting(key: String, value: String) { writableDatabase.insertWithOnConflict("settings", null, cv("key" to key,"value" to value), SQLiteDatabase.CONFLICT_REPLACE) }
 fun importPlan(target: String, drafts: List<PlanDraft>) {
  val db=writableDatabase; db.beginTransaction()
  try { require(!contract(target).confirmed); val existing=tasks(target).map { it.title }.toSet(); drafts.filter { it.title !in existing }.forEach { addTask(target,it.title,it.required,0,"行动型","按计划完成并自检",20,1,"once") }; setSetting("import_draft",""); audit(target,"从文本导入 ${drafts.size} 项待确认任务"); db.setTransactionSuccessful() } finally { db.endTransaction() }
 }
 // Task plans roll over at midnight. The entertainment pool keeps its separate
 // configurable boundary (04:00 by default).
 fun day(): String = now().toLocalDate().toString()
 fun rewardDay(): String {
  val current=now();val boundary=setting("boundary","4").toIntOrNull()?.coerceIn(0,23)?:4
  val pending=setting("boundary_effective")
  if(pending.isNotEmpty()) {
   if(current>=LocalDateTime.parse(pending)) { val next=setting("boundary_next").toInt();setSetting("boundary",next.toString());setSetting("boundary_effective","");return Rules.day(current,next) }
   return minOf(Rules.day(current,boundary),setting("boundary_previous_day",Rules.day(current,boundary)))
  }
  return Rules.day(current,boundary)
 }
 fun ensure(day: String = day()) {
  val db = writableDatabase
  if(day==day() && setting("generated_through")!=day) {
   val last=setting("generated_through",db.rawQuery("SELECT MAX(day) FROM contracts WHERE day<?",arrayOf(day)).use{it.moveToFirst();it.getString(0)?:day})
   var date=LocalDate.parse(last).plusDays(1)
   while(date.toString()<day){ensure(date.toString());date=date.plusDays(1)}
   setSetting("generated_through",day)
  }
  val rewardDate=rewardDay()
  if(day==day()) {
   settleExpiredBatches(now())
   setSetting("expiry_checked",rewardDate)
  }
  if (setting("sleep_system_tasks_enabled") == "true") {
   ensureSleepSystemTasks(day)
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
 fun goalState(date:String):DailyGoalState { val c=contract(date);return dailyGoalState(tasks(date,includeMoved=true),c.rest,date,day()) }
 // Keep the historical ledger keys so upgrades preserve balances and debits.
 fun rewardKind(pkg: String): String = setting(
  "controlled_reward_$pkg",
  if (pkg == "com.tencent.tmgp.sgame") "game" else "video"
 ).let { if (it == "game") "game" else "video" }

 fun setRewardKind(pkg: String, kind: String) {
  require(kind == "video" || kind == "game")
  setSetting("controlled_reward_$pkg", kind)
 }

 fun eligible(day: String = day()): Boolean = rewardUnavailableReason(day) == null
 fun rewardUnavailableReason(date: String): String? = when {
  date != day() -> "仅可领取当天任务的奖励"
  setting("gate_changed_$date") == "true" -> "今日必做计划已变更，无法领取选做奖励"
  !contract(date).confirmed -> "请先在设置的今日计划中确认计划"
  else -> null
 }
 fun batchExpiryMillis(taskDate: String): Long {
  val boundary = setting("boundary", "4").toIntOrNull()?.coerceIn(0, 23) ?: 4
  val expiryDateTime = LocalDate.parse(taskDate).plusDays(1).atTime(boundary, 0)
  return expiryDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
 }
 fun isBatchExpired(taskDate: String, at: LocalDateTime = now()): Boolean {
  val boundary = setting("boundary", "4").toIntOrNull()?.coerceIn(0, 23) ?: 4
  val expiryDateTime = runCatching { LocalDate.parse(taskDate).plusDays(1).atTime(boundary, 0) }.getOrNull() ?: return false
  return at >= expiryDateTime
 }
 fun dailyEarnCap(kind: String): Long = setting("reward_daily_earn_cap_$kind").toLongOrNull() ?: Long.MAX_VALUE
 fun setDailyEarnCap(kind: String, cap: Long) { setSetting("reward_daily_earn_cap_$kind", cap.toString()) }
 fun dailyUsageCap(kind: String): Long = setting("reward_daily_use_cap_$kind").toLongOrNull() ?: Long.MAX_VALUE
 fun setDailyUsageCap(kind: String, cap: Long) { setSetting("reward_daily_use_cap_$kind", cap.toString()) }
 fun consumedOnDate(db: SQLiteDatabase = readableDatabase, date: String, kind: String): Long = db.rawQuery(
  "SELECT COALESCE(-SUM(amount), 0) FROM ledger WHERE kind=? AND task_id='usage' AND (consumption_day=? OR (consumption_day IS NULL AND day=?))",
  arrayOf(kind, date, date)
 ).use { if (it.moveToFirst()) it.getLong(0) else 0L }
 fun activeBatchesSortedByExpiry(db: SQLiteDatabase = readableDatabase, kind: String): List<String> {
  val batches = mutableListOf<String>()
  db.rawQuery("SELECT day, SUM(amount) FROM ledger WHERE kind=? GROUP BY day HAVING SUM(amount)>0", arrayOf(kind)).use { c ->
   while (c.moveToNext()) batches.add(c.getString(0))
  }
  val nowTime = now()
  return batches.filter { !isBatchExpired(it, nowTime) }.sorted()
 }
 fun settleExpiredBatches(at: LocalDateTime = now()) {
  val db = writableDatabase
  val atMillis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
  val boundary = setting("boundary", "4").toIntOrNull()?.coerceIn(0, 23) ?: 4
  val batchesWithBalance = mutableListOf<Triple<String, String, Long>>()
  db.rawQuery("SELECT day, kind, SUM(amount) FROM ledger GROUP BY day, kind HAVING SUM(amount)>0", null).use { c ->
   while (c.moveToNext()) batchesWithBalance.add(Triple(c.getString(0), c.getString(1), c.getLong(2)))
  }
  for ((batchDate, kind, remaining) in batchesWithBalance) {
   val expiryTime = runCatching {
    LocalDate.parse(batchDate).plusDays(1).atTime(boundary, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
   }.getOrNull() ?: 0L
   if (atMillis >= expiryTime) {
    allocateLegacySpending(db, batchDate, kind)
    ledgerAt(db, batchDate, "expiry", kind, -remaining, atMillis, batchDate, expiryTime, null)
    allocateSpend(db, batchDate, kind, remaining, atMillis)
    audit(batchDate, "未使用奖励过期：$kind / $remaining")
   }
  }
 }
 private fun ledger(db: SQLiteDatabase, day: String, task: String, kind: String, amount: Long, taskDay: String? = null, expireAt: Long = 0L, consumptionDay: String? = null) =
  ledgerAt(db, day, task, kind, amount, System.currentTimeMillis(), taskDay, expireAt, consumptionDay)
 private fun ledgerAt(db: SQLiteDatabase, day: String, task: String, kind: String, amount: Long, created: Long, taskDay: String? = null, expireAt: Long = 0L, consumptionDay: String? = null) {
  db.insertOrThrow("ledger", null, cv("id" to UUID.randomUUID().toString(), "day" to day, "task_id" to task, "kind" to kind, "amount" to amount, "created" to created, "task_day" to (taskDay ?: day), "expire_at" to expireAt, "consumption_day" to consumptionDay))
 }
 private fun allocationKind(kind:String)="${kind}_allocation"
 private fun pendingSleepKey(date:String,source:String)="sleep_pending_${date}_$source"
 private fun grantReward(db:SQLiteDatabase,date:String,source:String,kind:String,amount:Long) {
  if(amount<=0) return
  if(isBatchExpired(date, now())) {
   audit(date, "任务已完成但奖励批次已过期，仅记录完成与XP，不发放娱乐额度：$kind / $amount")
   return
  }
  val expireAt = batchExpiryMillis(date)
  ledger(db, date, source, kind, amount, taskDay = date, expireAt = expireAt)
  var available=amount
  if(kind=="video") {
   val pending=mutableListOf<Triple<String,String,Long>>()
   db.rawQuery("SELECT key,value FROM settings WHERE key GLOB 'sleep_pending_*' AND key NOT GLOB 'sleep_pending_video_*' AND key NOT GLOB 'sleep_pending_game_*' AND CAST(value AS INTEGER)>0",null).use { c ->
    while(c.moveToNext()) {
     val key=c.getString(0);val parts=key.removePrefix("sleep_pending_").split('_')
     if(parts.size>=2)pending.add(Triple(parts.dropLast(1).joinToString("_"),parts.last(),c.getString(1).toLong()))
    }
   }
   db.rawQuery("SELECT key,value FROM settings WHERE key GLOB 'sleep_pending_video_*' AND CAST(value AS INTEGER)>0",null).use { c ->
    while(c.moveToNext()) {
     val key=c.getString(0);val parts=key.removePrefix("sleep_pending_video_").split('_')
     if(parts.size>=2) {
      val effectiveDate = parts[0]
      val cycleDate = parts[1]
      if(date >= effectiveDate) {
       pending.add(Triple(effectiveDate, "video:$cycleDate", c.getString(1).toLong()))
      }
     }
    }
   }
   pending.sortedWith(compareBy<Triple<String,String,Long>>{it.first}.thenBy{it.second}).forEach { (sleepDate,sourceType,amountDue) ->
    if(available<=0)return@forEach
    val charged=minOf(available,amountDue);val at=System.currentTimeMillis()
    val taskId = if(sourceType.startsWith("video:")) "sleep:cycle:${sourceType.removePrefix("video:")}" else "sleep:$sourceType:$sleepDate"
    appendDebit(db,date,taskId,kind,charged,at)
    appendDebit(db,date,source,allocationKind(kind),charged,at)
    val settingKey = if(sourceType.startsWith("video:")) "sleep_pending_video_${sleepDate}_${sourceType.removePrefix("video:")}" else pendingSleepKey(sleepDate,sourceType)
    setSetting(settingKey,(amountDue-charged).toString())
    available-=charged
   }
  } else if(kind=="game") {
   val pending=mutableListOf<Triple<String,String,Long>>()
   db.rawQuery("SELECT key,value FROM settings WHERE key GLOB 'sleep_pending_game_*' AND CAST(value AS INTEGER)>0",null).use { c ->
    while(c.moveToNext()) {
     val key=c.getString(0);val parts=key.removePrefix("sleep_pending_game_").split('_')
     if(parts.size>=2) {
      val effectiveDate = parts[0]
      val cycleDate = parts[1]
      if(date >= effectiveDate) {
       pending.add(Triple(effectiveDate, "game:$cycleDate", c.getString(1).toLong()))
      }
     }
    }
   }
   pending.sortedWith(compareBy<Triple<String,String,Long>>{it.first}.thenBy{it.second}).forEach { (sleepDate,sourceType,amountDue) ->
    if(available<=0)return@forEach
    val charged=minOf(available,amountDue);val at=System.currentTimeMillis()
    val taskId = if(sourceType.startsWith("game:")) "sleep:cycle:${sourceType.removePrefix("game:")}" else "sleep:$sourceType:$sleepDate"
    appendDebit(db,date,taskId,kind,charged,at)
    appendDebit(db,date,source,allocationKind(kind),charged,at)
    val settingKey = "sleep_pending_game_${sleepDate}_${sourceType.removePrefix("game:")}"
    setSetting(settingKey,(amountDue-charged).toString())
    available-=charged
   }
  }
  applyPendingNightPenalty(db,date,source,kind,available)
  val earnCap = dailyEarnCap(kind)
  if(earnCap < Long.MAX_VALUE) {
   val netSoFar = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM ledger WHERE day=? AND kind=? AND task_id NOT IN ('usage','expiry')", arrayOf(date, kind)).use { it.moveToFirst(); it.getLong(0) }
   if(netSoFar > earnCap) {
    val excess = netSoFar - earnCap
    val at = System.currentTimeMillis()
    appendDebit(db, date, "cap:daily_limit", kind, excess, at)
    allocateSpend(db, date, kind, excess, at)
    audit(date, "超出每日获取上限，扣减 $kind 额度：$excess")
   }
  }
 }
 private fun applyPendingNightPenalty(db:SQLiteDatabase,date:String,source:String,kind:String,initialAvailable:Long) {
  var available=initialAvailable
  val pending=mutableListOf<Pair<String,Long>>()
  val suffix=if(kind=="video")"video_" else "game_"
  db.rawQuery("SELECT key,value FROM settings WHERE key GLOB ? AND CAST(value AS INTEGER)>0",arrayOf("night_pending_$suffix*")).use { c -> while(c.moveToNext())pending.add(c.getString(0).removePrefix("night_pending_$suffix") to c.getString(1).toLong()) }
  pending.sortedBy{it.first}.forEach { (nightDate,due) ->
   if(available<=0)return@forEach
   val charged=minOf(available,due)
   ledger(db,date,"night:$kind:$nightDate:$source",kind,-charged)
   ledger(db,date,source,allocationKind(kind),-charged)
   setSetting("night_pending_$suffix$nightDate",(due-charged).toString())
   available-=charged
  }
 }
 private fun appendDebit(db:SQLiteDatabase,day:String,source:String,kind:String,amount:Long,at:Long,consumptionDay:String?=null) {
  val previous=db.rawQuery("SELECT id,amount,created FROM ledger WHERE day=? AND task_id=? AND kind=? ORDER BY created DESC,rowid DESC LIMIT 1",arrayOf(day,source,kind)).use{if(it.moveToFirst())Triple(it.getString(0),it.getLong(1),it.getLong(2))else null}
  if(previous!=null&&at-previous.third<60_000&&consumptionDay==null)db.update("ledger",cv("amount" to previous.second-amount,"created" to at),"id=?",arrayOf(previous.first)) else ledgerAt(db,day,source,kind,-amount,at,consumptionDay=consumptionDay)
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
 private fun batchBalance(db: SQLiteDatabase, day: String, kind: String): Long {
  if(isBatchExpired(day, now())) return 0L
  return db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM ledger WHERE day=? AND kind=?", arrayOf(day, kind)).use {
   if(it.moveToFirst()) it.getLong(0).coerceAtLeast(0L) else 0L
  }
 }
 fun balance(kind: String, day: String? = null): Long {
  val db = readableDatabase
  settleExpiredBatches(now())
  val today = now().toLocalDate().toString()
  val usageCap = dailyUsageCap(kind)
  val consumedToday = consumedOnDate(db, today, kind)
  val remainingCap = (usageCap - consumedToday).coerceAtLeast(0L)
  if(day != null) {
   val b = batchBalance(db, day, kind)
   return minOf(b, remainingCap)
  } else {
   val active = activeBatchesSortedByExpiry(db, kind)
   var total = 0L
   for(batch in active) total += batchBalance(db, batch, kind)
   return minOf(total, remainingCap)
  }
 }
 fun rewardSnapshot(kind:String,day:String=rewardDay()):RewardSnapshot {
  settleExpiredBatches(now())
  fun total(where:String,args:Array<String>)=readableDatabase.rawQuery("SELECT COALESCE(SUM(amount),0) FROM ledger WHERE kind=? AND $where",arrayOf(kind,*args)).use{it.moveToFirst();it.getLong(0)}
  val rem = balance(kind, day)
  return RewardSnapshot(
   total("day=? AND amount>0 AND task_id NOT LIKE 'sleep-refund:%'", arrayOf(day)),
   -total("day=? AND task_id='usage' AND amount<0", arrayOf(day)),
   -total("day=? AND task_id LIKE 'sleep:%' AND amount<0", arrayOf(day)),
   -total("day=? AND task_id='expiry' AND amount<0", arrayOf(day)),
   -total("day=? AND task_id LIKE 'revoke:%' AND amount<0", arrayOf(day)),
   rem,
   nightDeducted(day, kind)
  )
 }
 fun nightWrapUpConfig()=NightWrapUpConfig(
  setting("night_wind_down","22:00"),setting("night_work_cutoff","23:30"),setting("night_entertainment_cutoff","00:00"),setting("night_bedtime","00:30"),setting("night_morning_release","07:30"),setting("night_penalty_cap","10").toIntOrNull()?:10,
  setting("night_penalty_cap_games","1").toIntOrNull()?:1,
 ).validate()
 fun saveNightWrapUpConfig(config:NightWrapUpConfig) {
  config.validate()
  setSetting("night_wind_down",config.windDown);setSetting("night_work_cutoff",config.workCutoff)
  setSetting("night_entertainment_cutoff",config.entertainmentCutoff);setSetting("night_bedtime",config.bedtime)
  setSetting("night_morning_release",config.morningRelease);setSetting("night_penalty_cap",config.penaltyCapMinutes.toString())
  setSetting("night_penalty_cap_games",config.penaltyCapGames.toString())
 }
 fun nightEntertainmentRestricted(at:LocalDateTime=now())=nightWrapUpConfig().entertainmentRestricted(at)
 fun nightCycleDate(at:LocalDateTime=now())=nightWrapUpConfig().cycleDate(at).toString()
 fun recordNightViolation(eventId:String,packageName:String,observedAt:Long,perEventMinutes:Int=5):Boolean {
  require(eventId.isNotBlank()&&packageName.isNotBlank()&&perEventMinutes in 1..10)
  val config=nightWrapUpConfig();val observed=Instant.ofEpochMilli(observedAt).atZone(ZoneId.systemDefault()).toLocalDateTime()
  if(!config.entertainmentRestricted(observed))return false
  val cycle=config.cycleDate(observed).toString();val sql=writableDatabase;sql.beginTransaction()
  try {
   val kind=rewardKind(packageName)
   val values=cv("id" to eventId,"cycle_date" to cycle,"package_name" to packageName,"observed_at" to observedAt,"reserved_minutes" to 0,"reward_kind" to kind,"reserved_amount" to 0L)
   val inserted=sql.insertWithOnConflict("night_violations",null,values,SQLiteDatabase.CONFLICT_IGNORE)
   if(inserted==-1L){sql.setTransactionSuccessful();return false}
   val key="night_reserved_${kind}_$cycle";val already=setting(key,"0").toIntOrNull()?:0
   val cap=if(kind=="video")config.penaltyCapMinutes else config.penaltyCapGames
   val reserved=if(kind=="video") minOf(perEventMinutes,(cap-already).coerceAtLeast(0)) else minOf(1,(cap-already).coerceAtLeast(0))
   val reservedAmount=if(kind=="video")reserved*60_000L else reserved.toLong()
   if(reserved>0){sql.update("night_violations",cv("reserved_minutes" to if(kind=="video")reserved else 0,"reserved_amount" to reservedAmount),"id=?",arrayOf(eventId));setSetting(key,(already+reserved).toString());val pendingKey="night_pending_${kind}_$cycle";setSetting(pendingKey,((setting(pendingKey,"0").toLongOrNull()?:0L)+reservedAmount).toString())}
   val rewardLabel=if(kind=="video")"娱乐分钟" else "游戏局数"
   val amountLabel=if(kind=="video")"$reserved 分钟" else "$reserved 局"
   audit(cycle,"夜间娱乐访问已拦截：$packageName"+(if(reserved>0)"；后续${rewardLabel}最多抵扣 $amountLabel" else "；已达到本夜抵扣上限"))
   sql.setTransactionSuccessful();return true
  } finally {sql.endTransaction()}
 }
 fun nightViolations(cycleDate:String):List<NightViolation> = readableDatabase.rawQuery("SELECT id,cycle_date,package_name,observed_at,reward_kind,reserved_amount FROM night_violations WHERE cycle_date=? ORDER BY observed_at",arrayOf(cycleDate)).use{c->buildList{while(c.moveToNext())add(NightViolation(c.getString(0),c.getString(1),c.getString(2),c.getLong(3),c.getString(4),c.getLong(5)))}}
 fun pendingNightPenalty(kind:String="video",cycleDate:String?=null):Long {
  val pattern=if(cycleDate==null) "night_pending_${kind}_*" else "night_pending_${kind}_$cycleDate"
  return readableDatabase.rawQuery("SELECT COALESCE(SUM(CAST(value AS INTEGER)),0) FROM settings WHERE key GLOB ?",arrayOf(pattern)).use{it.moveToFirst();it.getLong(0).coerceAtLeast(0)}
 }
 fun nightDeducted(day:String=rewardDay(),kind:String="video"):Long = readableDatabase.rawQuery(
  "SELECT COALESCE(-SUM(amount),0) FROM ledger WHERE kind=? AND (day=? OR task_id LIKE 'night:' || ? || ':' || ? || ':%') AND task_id LIKE 'night:%' AND amount<>0",
  arrayOf(kind, day, kind, day)
 ).use{it.moveToFirst();it.getLong(0)}
 fun consume(kind: String, amount: Long, day: String? = null) {
  if(amount <= 0) return
  val db = writableDatabase; db.beginTransaction()
  try {
   settleExpiredBatches(now())
   val today = now().toLocalDate().toString()
   val at = System.currentTimeMillis()
   val usageCap = dailyUsageCap(kind)
   val consumedToday = consumedOnDate(db, today, kind)
   val remainingCap = (usageCap - consumedToday).coerceAtLeast(0L)
   var need = minOf(amount, remainingCap)
   if(need <= 0L) { db.setTransactionSuccessful(); return }
   val batches = if(day != null) {
    if(isBatchExpired(day, now())) emptyList() else listOf(day)
   } else {
    activeBatchesSortedByExpiry(db, kind)
   }
   for(b in batches) {
    if(need <= 0L) break
    allocateLegacySpending(db, b, kind)
    val availableInBatch = batchBalance(db, b, kind)
    if(availableInBatch <= 0L) continue
    val take = minOf(availableInBatch, need)
    appendDebit(db, b, "usage", kind, take, at, consumptionDay = today)
    allocateSpend(db, b, kind, take, at)
    need -= take
   }
   db.setTransactionSuccessful()
  } finally { db.endTransaction() }
 }
 fun claim(task: Task, kind: String) { val db = writableDatabase; db.beginTransaction(); try { val fresh = tasks(task.date).first { it.id == task.id }; if (eligible(task.date) && fresh.typeKnown && !fresh.required && fresh.done && !fresh.claimed && task.date == day()) { val amount = if(kind == "video") fresh.rewardMinutes * 60000L else fresh.rewardGames.toLong(); require(amount > 0); grantReward(db,task.date,task.id,kind,amount); db.update("tasks",cv("claimed" to true),"id=?",arrayOf(task.id)) }; db.setTransactionSuccessful() } finally { db.endTransaction() } }
 fun start(task: Task) { val fresh=tasks(task.date).first{it.id==task.id};if(fresh.done || fresh.runningSince != 0L) return; runningTasks().forEach{pause(it)};writableDatabase.update("tasks",cv("running_since" to SystemClock.elapsedRealtime(),"boot_time" to (System.currentTimeMillis()-SystemClock.elapsedRealtime())),"id=?",arrayOf(task.id)) }
 fun elapsed(task: Task): Long { val sameBoot = kotlin.math.abs((System.currentTimeMillis()-SystemClock.elapsedRealtime()) - task.bootTime) < 120000; return task.elapsed + if(task.runningSince > 0 && sameBoot) (SystemClock.elapsedRealtime()-task.runningSince).coerceAtLeast(0) else 0 }
 fun pause(task: Task) { val fresh=tasks(task.date).first{it.id==task.id};if(fresh.runningSince==0L)return;writableDatabase.update("tasks",cv("elapsed" to elapsed(fresh),"running_since" to 0L,"boot_time" to 0L),"id=?",arrayOf(task.id)) }
 fun restart(task:Task) {require(!tasks(task.date).first{it.id==task.id}.done);writableDatabase.update("tasks",cv("elapsed" to 0L,"running_since" to 0L,"boot_time" to 0L),"id=?",arrayOf(task.id));audit(task.date,"重新计时：${task.title}")}
 fun evidence(task: Task, path: String) { val db=writableDatabase;db.beginTransaction();try {db.insertOrThrow("attachments",null,cv("id" to UUID.randomUUID().toString(),"task_id" to task.id,"path" to path,"created" to System.currentTimeMillis()));db.update("tasks",cv("evidence" to path),"id=?",arrayOf(task.id));db.setTransactionSuccessful()}finally{db.endTransaction()} }
 fun submitProof(task:Task,label:String) { val marker="proof-submitted:${System.currentTimeMillis()}";writableDatabase.update("tasks",cv("evidence" to marker),"id=?",arrayOf(task.id));audit(task.date,"仅提交证明，未保存文件${if(label.isBlank())"" else "：$label"}") }
 fun attachments(task:Task):List<Pair<String,String>> { val result=mutableListOf<Pair<String,String>>();readableDatabase.rawQuery("SELECT id,path FROM attachments WHERE task_id=? ORDER BY created",arrayOf(task.id)).use{c->while(c.moveToNext())result.add(c.getString(0) to c.getString(1))};return result }
 fun removeAttachment(task:Task,id:String) {val db=writableDatabase;var removedPath="";db.beginTransaction();try{db.rawQuery("SELECT path FROM attachments WHERE id=? AND task_id=?",arrayOf(id,task.id)).use{if(it.moveToFirst())removedPath=it.getString(0)};db.delete("attachments","id=? AND task_id=?",arrayOf(id,task.id));val latest=attachments(task).lastOrNull()?.second.orEmpty();db.update("tasks",cv("evidence" to latest),"id=?",arrayOf(task.id));audit(task.date,"移除完成证明：${java.io.File(removedPath).name}");db.setTransactionSuccessful()}finally{db.endTransaction()};if(removedPath.isNotBlank()){val stillReferenced=readableDatabase.rawQuery("SELECT (SELECT COUNT(*) FROM attachments WHERE path=?)+(SELECT COUNT(*) FROM tasks WHERE evidence=?)",arrayOf(removedPath,removedPath)).use{it.moveToFirst();it.getInt(0)>0};if(!stillReferenced)java.io.File(removedPath).delete()} }
 fun checkpoint() {runningTasks().forEach{task->writableDatabase.update("tasks",cv("elapsed" to elapsed(task),"running_since" to SystemClock.elapsedRealtime(),"boot_time" to (System.currentTimeMillis()-SystemClock.elapsedRealtime())),"id=?",arrayOf(task.id))} }
 fun complete(task: Task) {
  pause(task)
  val db=writableDatabase;db.beginTransaction()
  try {
   val fresh=tasks(task.date).first { it.id == task.id }
   if(fresh.done) { db.setTransactionSuccessful();return }
   require(fresh.date<=day()) { "未来任务不能提前标记为完成" }
   require(fresh.elapsed >= fresh.minutes * 60000L && (fresh.mode == "行动型" || fresh.evidence.isNotBlank()))
   val completedAt=wallNow()
   db.update("tasks",cv("done" to true,"completed_at" to completedAt),"id=?",arrayOf(task.id))
   if(fresh.required) {
    listOf("video" to fresh.rewardMinutes.toLong()*60000L,"game" to fresh.rewardGames.toLong()).forEach { (kind,amount) ->
     val key="task_reward_${fresh.id}_$kind"
     if(amount>0 && setting(key)!="true") { grantReward(db,fresh.date,fresh.id,kind,amount);setSetting(key,"true") }
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
   if(frozen) {audit(task.date,"计划删除：${fresh.title}（${if(fresh.required)"必做" else "选做"}，${fresh.minutes} 分钟，完成标准：${fresh.checklist.ifBlank { "未填写" }}）；原因：${reason.trim()}");setSetting("changed_${task.date}","true");if(fresh.required)setSetting("gate_changed_${task.date}","true")}
   setSetting("deleted_${task.id}", "true")
   db.delete("attachments","task_id=?",arrayOf(task.id));db.delete("tasks","id=?",arrayOf(task.id));db.setTransactionSuccessful()
  } finally {db.endTransaction()}
 }
 fun moveTask(task:Task,targetDay:String,reason:String=""):String {
  require(targetDay>=day() && targetDay!=task.date)
  val db=writableDatabase;db.beginTransaction()
  return try {
   val fresh=tasks(task.date).first{it.id==task.id};val frozen=contract(task.date).confirmed
   require(!fresh.done && !contract(targetDay).confirmed && (!frozen || !fresh.required || reason.isNotBlank()))
   val source=db.rawQuery("SELECT template_id,recurrence,origin FROM tasks WHERE id=?",arrayOf(task.id)).use{it.moveToFirst();Triple(it.getString(0),it.getString(1),it.getString(2))}
   val duplicate=db.rawQuery("SELECT id,template_id,done,elapsed,running_since,evidence,claimed FROM tasks WHERE day=? AND id NOT IN (SELECT source_id FROM task_moves) AND (template_id=? OR lower(trim(title))=lower(trim(?))) LIMIT 1",arrayOf(targetDay,source.first,fresh.title)).use { c -> if(c.moveToFirst()) listOf(c.getString(0),c.getString(1),c.getInt(2).toString(),c.getLong(3).toString(),c.getLong(4).toString(),c.getString(5),c.getInt(6).toString()) else null }
   if(duplicate!=null) require(duplicate[1]==source.first && duplicate[2]=="0" && duplicate[3]=="0" && duplicate[4]=="0" && duplicate[5].isBlank() && duplicate[6]=="0") { "目标日期已有同一任务或同名任务，且已有进度，不能重复迁入" }
   pause(fresh)
   val reusableTarget=duplicate?.get(0)
   val targetId=reusableTarget?:UUID.randomUUID().toString()
   val targetValues=cv("title" to fresh.title,"required" to fresh.required,"minutes" to fresh.minutes,"mode" to fresh.mode,"checklist" to fresh.checklist,"reward_minutes" to fresh.rewardMinutes,"reward_games" to fresh.rewardGames,"elapsed" to fresh.elapsed,"running_since" to 0L,"boot_time" to 0L,"evidence" to fresh.evidence,"done" to false,"claimed" to false,"completed_at" to 0L,"recurrence" to source.second,"origin" to source.third)
   if(reusableTarget!=null)db.update("tasks",targetValues,"id=?",arrayOf(targetId)) else db.insertOrThrow("tasks",null,targetValues.apply{put("id",targetId);put("template_id",source.first);put("day",targetDay)})
   db.insertOrThrow("task_moves",null,cv("source_id" to task.id,"target_id" to targetId,"from_day" to task.date,"to_day" to targetDay,"created" to System.currentTimeMillis()))
   db.rawQuery("SELECT path,created FROM attachments WHERE task_id=?",arrayOf(task.id)).use { c -> while(c.moveToNext())db.insertOrThrow("attachments",null,cv("id" to UUID.randomUUID().toString(),"task_id" to targetId,"path" to c.getString(0),"created" to c.getLong(1))) }
   audit(task.date,"任务日期调整：${fresh.title}，${task.date} → $targetDay"+(if(reason.isBlank())"" else "；原因：$reason"))
   if(frozen){setSetting("changed_${task.date}","true");if(fresh.required)setSetting("gate_changed_${task.date}","true")}
   audit(targetDay,"接收延期任务：${fresh.title}（来自 ${task.date}）")
   db.setTransactionSuccessful();targetId
  } finally {db.endTransaction()}
 }
 fun scheduleBoundary(hour:Int) { require(hour in 0..23); val date=day();val old=setting("boundary","4").toInt(); val effective=LocalDate.parse(rewardDay()).plusDays(1).atStartOfDay().plusHours(maxOf(old,hour).toLong());setSetting("boundary_previous_day",rewardDay()); setSetting("boundary_next",hour.toString()); setSetting("boundary_effective",effective.toString()); audit(date,"娱乐额度重置时间将于 $effective 改为 $hour:00") }
 fun planRest(date:String,minutes:Int=30,games:Int=0) {
  require(date>day());val db=writableDatabase;db.beginTransaction()
  try { require(!contract(date).confirmed);val original=tasks(date).filter{it.required}.joinToString {it.title};db.update("tasks",cv("required" to false),"day=? AND done=0",arrayOf(date));db.update("contracts",cv("rest" to true,"confirmed" to true,"base_minutes" to minutes,"base_games" to games),"day=?",arrayOf(date));audit(date,"预设休息日；原必做改为选做：$original");db.setTransactionSuccessful() } finally {db.endTransaction()}
 }
 fun settleToday() { contract(day()) }
 fun bestStreak():Int {
  val states=history().filter{it.first<=day()}.sortedBy{it.first};var best=0;var count=0
  for((_,state) in states){when(state){"完成"->count++;"未完成"->count=0;else->Unit};best=maxOf(best,count)}
  return best
 }
 fun revise(task:Task,title:String,required:Boolean,minutes:Int,mode:String,checklist:String,video:Int,games:Int,recurrence:String,reason:String,delete:Boolean=false,applyFuture:Boolean=false) {
  val db=writableDatabase; db.beginTransaction()
  try { val frozen=contract(task.date).confirmed; require(!task.done && title.isNotBlank() && (!frozen || reason.isNotBlank())); if(frozen) { audit(task.date,"计划变更：${task.title}（${if(task.required)"必做" else "选做"}，${task.minutes} 分钟，完成标准：${task.checklist.ifBlank { "未填写" }}）→ ${if(delete)"删除" else "$title（${if(required)"必做" else "选做"}，$minutes 分钟，完成标准：${checklist.ifBlank { "未填写" }}）"}；原因：${reason.trim()}"); setSetting("changed_${task.date}","true") }
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
   val out=mutableListOf<Pair<Long,String>>()
  readableDatabase.rawQuery("SELECT l.day,l.kind,l.amount,l.task_id,l.created,t.title FROM ledger l LEFT JOIN tasks t ON t.id=l.task_id WHERE l.amount<>0 AND l.kind NOT LIKE '%_allocation' ORDER BY l.created DESC,l.rowid DESC LIMIT 100",null).use {c->while(c.moveToNext()) {
   val amount=c.getLong(2);val source=c.getString(3);val eventTime=java.time.Instant.ofEpochMilli(c.getLong(4)).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();val absolute=kotlin.math.abs(amount);val value=if(c.getString(1)=="video") {val minutes=absolute/60000;val seconds=absolute%60000/1000;when{minutes==0L->"${seconds.coerceAtLeast(1)} 秒";seconds==0L->"$minutes 分钟";else->"$minutes 分 $seconds 秒"}} else "${absolute} 局"
    val title=c.getString(5);val label=when{source=="expiry"->"奖励到期 −$value";source=="usage"->"已使用 $value";source.startsWith("cap:")->"每日获取上限扣减 −$value";source.startsWith("sleep:")->"睡眠规则抵扣 −$value";source.startsWith("sleep-refund:")->"睡眠记录修正返还 +$value";source.startsWith("night:")->if(amount<0)"夜间收尾抵扣 −$value" else "夜间抵扣冲回 +$value";source.startsWith("revoke:")||amount<0->"任务奖励冲回 −$value";source=="base"->"基础奖励 +$value";else->"${if(readableDatabase.rawQuery("SELECT required FROM tasks WHERE id=?",arrayOf(source)).use{it.moveToFirst()&&it.getInt(0)!=0})"必做奖励" else "选做奖励"} · ${title?:"历史任务"} +$value"}
    val time=eventTime.toLocalTime().toString().take(5);out.add(c.getLong(4) to "${eventTime.monthValue}月${eventTime.dayOfMonth}日 $time · $label")
  } }
   readableDatabase.rawQuery("SELECT day,detail,created FROM audit WHERE detail LIKE '紧急解锁%' OR detail='恢复保护' ORDER BY created DESC LIMIT 30",null).use { c -> while(c.moveToNext()) {
    val eventTime=java.time.Instant.ofEpochMilli(c.getLong(2)).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();val detail=c.getString(1);val why=detail.removePrefix("紧急解锁").trimStart('：');val label=if(detail.startsWith("紧急解锁"))"应用紧急解锁${if(why.isBlank())"" else "：$why"}" else "应用保护已恢复";out.add(c.getLong(2) to "${eventTime.monthValue}月${eventTime.dayOfMonth}日 ${eventTime.toLocalTime().toString().take(5)} · $label")
   } }
   return out.sortedByDescending{it.first}.take(100).map{it.second}
 }
 fun revoke(task: Task) {
  val db = writableDatabase; db.beginTransaction()
  try {
   val fresh = tasks(task.date).first { it.id == task.id }
   if(fresh.done) {
    val source = fresh.id
    for(kind in listOf("video","game")) {
     val buckets=mutableListOf<String>();db.rawQuery("SELECT DISTINCT day FROM ledger WHERE task_id=? AND kind=?",arrayOf(source,kind)).use{while(it.moveToNext())buckets.add(it.getString(0))}
     for(bucket in buckets) {
      allocateLegacySpending(db,bucket,kind)
      val nightDebits=mutableListOf<Pair<String,Long>>()
      db.rawQuery("SELECT task_id,SUM(amount) FROM ledger WHERE day=? AND kind=? AND task_id LIKE ? GROUP BY task_id HAVING SUM(amount)<0",arrayOf(bucket,kind,"night:$kind:%:$source")).use { c -> while(c.moveToNext())nightDebits.add(c.getString(0) to -c.getLong(1)) }
      nightDebits.forEach { (nightSource,amount) ->
       val cycle=nightSource.split(':').getOrNull(2).orEmpty()
       if(cycle.isNotBlank()) {
        ledger(db,bucket,nightSource,kind,amount)
        ledger(db,bucket,source,allocationKind(kind),amount)
        val key="night_pending_${kind}_$cycle"
        setSetting(key,((setting(key,"0").toLongOrNull()?:0L)+amount).toString())
       }
      }
      val granted = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM ledger WHERE day=? AND task_id=? AND kind=?",arrayOf(bucket,source,kind)).use { it.moveToFirst(); it.getLong(0) }
      val spent = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM ledger WHERE day=? AND task_id=? AND kind=?",arrayOf(bucket,source,allocationKind(kind))).use { it.moveToFirst(); it.getLong(0) }
      val reversal = minOf((granted+spent).coerceAtLeast(0),balance(kind,bucket))
      if(reversal > 0) ledger(db,bucket,"revoke:$source",kind,-reversal,taskDay=bucket)
      val capDebited = db.rawQuery("SELECT COALESCE(-SUM(amount),0) FROM ledger WHERE day=? AND kind=? AND task_id='cap:daily_limit'", arrayOf(bucket, kind)).use { it.moveToFirst(); it.getLong(0) }
      if(capDebited > 0) {
       val earnCap = dailyEarnCap(kind)
       val netAfter = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM ledger WHERE day=? AND kind=? AND task_id NOT IN ('usage','expiry')", arrayOf(bucket, kind)).use { it.moveToFirst(); it.getLong(0) }
       if(netAfter < earnCap) {
        val refund = minOf(capDebited, earnCap - netAfter)
        if(refund > 0) ledger(db, bucket, "cap-refund", kind, refund, taskDay = bucket)
       }
      }
     }
    }
    // Reopening an optional task must also reopen its one-time reward choice.
    // Its previous reward remains in the ledger (with any available amount
    // reversed above), but a fresh completion can be claimed again.
    db.update("tasks",cv("done" to false,"claimed" to false,"completed_at" to 0L),"id=?",arrayOf(task.id))
    for(kind in listOf("video","game"))setSetting("task_reward_${fresh.id}_$kind","false")
    audit(task.date,"撤销任务：${fresh.title}；已领取额度按剩余余额冲正")
   }
   db.setTransactionSuccessful()
  } finally { db.endTransaction() }
 }
 fun sleepWindowMinutes(bed: String, wake: String): Long {
  val bedTime = java.time.LocalTime.parse(bed)
  val wakeTime = java.time.LocalTime.parse(wake)
  val bedDateTime = if (bedTime.hour >= 12) java.time.LocalDate.of(2000, 1, 1).atTime(bedTime) else java.time.LocalDate.of(2000, 1, 2).atTime(bedTime)
  val wakeDateTime = java.time.LocalDate.of(2000, 1, 2).atTime(wakeTime)
  return java.time.Duration.between(bedDateTime, wakeDateTime).toMinutes()
 }
 fun isSleepTargetValid(bed: String, wake: String): Boolean = sleepWindowMinutes(bed, wake) >= 420L

 fun sleepTargets()=listOf(setting("sleep_bed_target","00:30"),setting("sleep_wake_target","07:30"),setting("sleep_bed_penalty","15"),setting("sleep_wake_penalty","15"))
 fun saveSleepTargets(bed:String,wake:String,bedPenalty:Int=15,wakePenalty:Int=15) {
  java.time.LocalTime.parse(bed);java.time.LocalTime.parse(wake);require(bedPenalty>=0&&wakePenalty>=0)
  require(isSleepTargetValid(bed, wake)) { "配置被拒绝：目标睡眠窗口不足7小时（实际为 ${sleepWindowMinutes(bed, wake)} 分钟），无法满足最低睡眠时长底线" }
  setSetting("sleep_bed_target",bed);setSetting("sleep_wake_target",wake);setSetting("sleep_bed_penalty",bedPenalty.toString());setSetting("sleep_wake_penalty",wakePenalty.toString())
 }

 fun calculateSleepDurationMinutes(cycleDate: String, bedtime: String, wakeTime: String): Long {
  val bedTime = java.time.LocalTime.parse(bedtime)
  val wake = java.time.LocalTime.parse(wakeTime)
  val date = java.time.LocalDate.parse(cycleDate)
  val bedDateTime = if (bedTime.hour >= 12) date.minusDays(1).atTime(bedTime) else date.atTime(bedTime)
  val wakeDateTime = date.atTime(wake)
  return java.time.Duration.between(bedDateTime, wakeDateTime).toMinutes()
 }

 fun sleepPenaltyMinutes(level: Int): Int = minOf(30, 5 * level.coerceAtLeast(0))
 fun sleepPenaltyGames(level: Int): Int = minOf(2, (level.coerceAtLeast(0)) / 2)

 fun sleepPenaltyEffectiveDate(cycleDate: String): String {
  val nextDay = setting("sleep_penalty_next_day", "true") == "true"
  return if (nextDay) java.time.LocalDate.parse(cycleDate).plusDays(1).toString() else cycleDate
 }

 fun sleepLevel(cycleDate: String = day()): Int {
  val saved = setting("sleep_level_$cycleDate")
  if (saved.isNotBlank()) return saved.toIntOrNull() ?: 0
  val rec = sleepRecord(cycleDate)
  if (rec != null) return rec.level
  return setting("sleep_failure_level", "0").toIntOrNull() ?: 0
 }

 fun sleepLevelBefore(cycleDate: String): Int {
  val priorDates = mutableListOf<String>()
  readableDatabase.rawQuery("SELECT key FROM settings WHERE (key GLOB 'sleep_level_*' OR key GLOB 'sleep_record_*')", null).use { c ->
   while (c.moveToNext()) {
    val k = c.getString(0)
    val d = if (k.startsWith("sleep_level_")) k.removePrefix("sleep_level_") else k.removePrefix("sleep_record_")
    if (d < cycleDate && d !in priorDates) priorDates.add(d)
   }
  }
  val latest = priorDates.maxOrNull() ?: return 0
  return sleepLevel(latest)
 }

 fun sleepStatus(cycleDate: String = day()): SleepStatus {
  val cycle = sleepRecord(cycleDate) ?: return SleepStatus.UNVERIFIED
  return cycle.status
 }

 fun isSleepUnverifiedOverdue(cycleDate: String, at: LocalDateTime = now()): Boolean {
  val rec = sleepRecord(cycleDate)
  if (rec != null && rec.status != SleepStatus.UNVERIFIED) return false
  val date = runCatching { java.time.LocalDate.parse(cycleDate) }.getOrNull() ?: return false
  val targets = sleepTargets()
  val wakeTime = runCatching { java.time.LocalTime.parse(targets[1]) }.getOrDefault(java.time.LocalTime.of(7, 30))
  val wakeDateTime = date.atTime(wakeTime)
  val overdueThreshold = wakeDateTime.plusHours(24)
  return at >= overdueThreshold
 }

 fun hasSleepBeenReminded(cycleDate: String): Boolean =
  setting("sleep_reminded_$cycleDate") == "true"

 fun markSleepReminded(cycleDate: String) {
  setSetting("sleep_reminded_$cycleDate", "true")
 }

 fun sleepRecord(date:String=day()):SleepRecord? {
  val json=setting("sleep_record_$date");if(json.isBlank())return null
  val o=org.json.JSONObject(json)
  val statusStr = o.optString("status", "")
  val status = if (statusStr.isNotBlank()) {
   try { SleepStatus.valueOf(statusStr) } catch(e: Exception) {
    if (o.optBoolean("excepted", false)) SleepStatus.PAUSED else if (o.optLong("bed_penalty", 0L) == 0L && o.optLong("wake_penalty", 0L) == 0L) SleepStatus.PASS else SleepStatus.FAIL
   }
  } else {
   if (o.optBoolean("excepted", false)) SleepStatus.PAUSED else if (o.optLong("bed_penalty", 0L) == 0L && o.optLong("wake_penalty", 0L) == 0L) SleepStatus.PASS else SleepStatus.FAIL
  }
  return SleepRecord(
   date = date,
   bedtime = o.getString("bedtime"),
   wakeTime = o.getString("wake"),
   bedtimePenalty = o.optLong("bed_penalty", 0L),
   wakePenalty = o.optLong("wake_penalty", 0L),
   excepted = o.optBoolean("excepted", false),
   reason = o.optString("reason", ""),
   source = o.optString("source","manual"),
   status = status,
   version = o.optInt("version", 1),
   prepareBedtime = o.optString("prepare_bedtime", "").ifBlank { null },
   durationMinutes = o.optLong("duration_minutes", 0L),
   effectiveDate = o.optString("effective_date", date),
   level = o.optInt("level", 0),
   penaltyMinutes = o.optInt("penalty_minutes", 0),
   penaltyGames = o.optInt("penalty_games", 0)
  )
 }

 fun pendingSleepPenalty(kind: String = "video", date: String? = null, cycleDate: String? = null): Long {
  if(kind == "video") {
   val legacy = if (cycleDate != null) 0L else readableDatabase.rawQuery("SELECT COALESCE(SUM(CAST(value AS INTEGER)),0) FROM settings WHERE key GLOB 'sleep_pending_*' AND key NOT GLOB 'sleep_pending_game_*' AND key NOT GLOB 'sleep_pending_video_*'",null).use{it.moveToFirst();it.getLong(0)}
   var v1 = 0L
   readableDatabase.rawQuery("SELECT key, value FROM settings WHERE key GLOB 'sleep_pending_video_*' AND CAST(value AS INTEGER)>0", null).use { c ->
    while (c.moveToNext()) {
     val key = c.getString(0)
     val parts = key.removePrefix("sleep_pending_video_").split('_')
     if (parts.size >= 2) {
      val effectiveDate = parts[0]
      val cDate = parts[1]
      if (cycleDate != null && cDate != cycleDate) continue
      if (date == null || date >= effectiveDate) {
       v1 += c.getString(1).toLong()
      }
     }
    }
   }
   return legacy + v1
  } else {
   var v1 = 0L
   readableDatabase.rawQuery("SELECT key, value FROM settings WHERE key GLOB 'sleep_pending_game_*' AND CAST(value AS INTEGER)>0", null).use { c ->
    while (c.moveToNext()) {
     val key = c.getString(0)
     val parts = key.removePrefix("sleep_pending_game_").split('_')
     if (parts.size >= 2) {
      val effectiveDate = parts[0]
      val cDate = parts[1]
      if (cycleDate != null && cDate != cycleDate) continue
      if (date == null || date >= effectiveDate) {
       v1 += c.getString(1).toLong()
      }
     }
    }
   }
   return v1
  }
 }

 fun hasTrustedAutoSleepRecord(cycleDate: String): Boolean {
  val rec = sleepRecord(cycleDate)
  val hasImportId = setting("sleep_import_id_$cycleDate").isNotBlank()
  return (rec != null && rec.source != "manual" && rec.source.isNotBlank()) || hasImportId
 }

 fun recordSleepCycle(
  cycleDate: String,
  bedtime: String,
  wakeTime: String,
  excepted: Boolean = false,
  reason: String = "",
  prepareBedtime: String? = null,
  source: String = "health_connect",
  forcedStatus: SleepStatus? = null,
  actualSleepMinutes: Long? = null
 ): SleepRecord = recordSleepCycleInternal(cycleDate, bedtime, wakeTime, excepted, reason, prepareBedtime, source, forcedStatus, isCascade = false, actualSleepMinutes = actualSleepMinutes)

 private fun refundCycleDebit(db: SQLiteDatabase, cycleDate: String, kind: String, refundAmount: Long) {
  if (refundAmount <= 0L) return
  var remainingRefund = refundAmount
  val debits = mutableListOf<Pair<String, Long>>()
  db.rawQuery(
   "SELECT day, -amount FROM ledger WHERE (task_id = 'sleep:cycle:' || ? OR task_id LIKE 'sleep:%:' || ?) AND kind=? AND amount<0 ORDER BY rowid DESC",
   arrayOf(cycleDate, cycleDate, kind)
  ).use { c ->
   while (c.moveToNext()) {
    debits.add(c.getString(0) to c.getLong(1))
   }
  }
  for ((debitDay, debited) in debits) {
   if (remainingRefund <= 0L) break
   val toRefund = minOf(remainingRefund, debited)
   ledger(db, debitDay, "sleep-refund:cycle:$cycleDate", kind, toRefund)
   remainingRefund -= toRefund
  }
  if (remainingRefund > 0L) {
   val targetDay = sleepPenaltyEffectiveDate(cycleDate)
   ledger(db, targetDay, "sleep-refund:cycle:$cycleDate", kind, remainingRefund)
  }
  settleExpiredBatches(now())
 }

 private fun recordSleepCycleInternal(
  cycleDate: String,
  bedtime: String,
  wakeTime: String,
  excepted: Boolean = false,
  reason: String = "",
  prepareBedtime: String? = null,
  source: String = "health_connect",
  forcedStatus: SleepStatus? = null,
  isCascade: Boolean = false,
  actualSleepMinutes: Long? = null
 ): SleepRecord {
  val date = java.time.LocalDate.parse(cycleDate)
  require(date <= java.time.LocalDate.parse(day())) { "未来睡眠周期不能提前记录" }
  if (excepted) require(reason.isNotBlank()) { "特殊情况豁免必须填写原因" }

  val old = sleepRecord(cycleDate)
  // S10: Idempotent check
  if (!isCascade && old != null && old.bedtime == bedtime && old.wakeTime == wakeTime && old.excepted == excepted && old.reason == reason.trim() && old.prepareBedtime == prepareBedtime && (forcedStatus == null || old.status == forcedStatus) && (actualSleepMinutes == null || old.durationMinutes == actualSleepMinutes)) {
   return old
  }
  if (!isCascade && old != null && (old.bedtime != bedtime || old.wakeTime != wakeTime || old.excepted != excepted)) {
   require(reason.isNotBlank()) { "修正已有睡眠记录必须填写更正原因" }
  }

  val hasAuto = hasTrustedAutoSleepRecord(cycleDate)
  val isManualWithoutAuto = (source == "manual" && !hasAuto)

  val inBedMinutes = calculateSleepDurationMinutes(cycleDate, bedtime, wakeTime)
  val durationMinutes = if (actualSleepMinutes != null && actualSleepMinutes > 0L) actualSleepMinutes else inBedMinutes
  val targets = sleepTargets()
  val targetBed = targets[0]
  val targetWake = targets[1]

  val status = forcedStatus ?: when {
   excepted -> SleepStatus.PAUSED
   isManualWithoutAuto -> SleepStatus.UNVERIFIED
   !isSleepTargetValid(targetBed, targetWake) -> SleepStatus.UNVERIFIED
   else -> {
    val targetBedTime = java.time.LocalTime.parse(targetBed)
    val targetWakeTime = java.time.LocalTime.parse(targetWake)
    val targetBedDateTime = if (targetBedTime.hour >= 12) date.minusDays(1).atTime(targetBedTime) else date.atTime(targetBedTime)
    val targetWakeDateTime = date.atTime(targetWakeTime)

    val actualBedTime = java.time.LocalTime.parse(bedtime)
    val actualWakeTime = java.time.LocalTime.parse(wakeTime)
    val actualBedDateTime = if (actualBedTime.hour >= 12) date.minusDays(1).atTime(actualBedTime) else date.atTime(actualBedTime)
    val actualWakeDateTime = date.atTime(actualWakeTime)

    val bedOnTime = !actualBedDateTime.isAfter(targetBedDateTime)
    val wakeOnTime = !actualWakeDateTime.isAfter(targetWakeDateTime)
    val durationOk = durationMinutes >= 420L

    if (bedOnTime && wakeOnTime && durationOk) SleepStatus.PASS else SleepStatus.FAIL
   }
  }

  val prevLevel = sleepLevelBefore(cycleDate)
  val level = when (status) {
   SleepStatus.PASS -> 0
   SleepStatus.FAIL -> prevLevel + 1
   SleepStatus.PAUSED, SleepStatus.UNVERIFIED -> prevLevel
  }

  val penaltyMin = if (status == SleepStatus.FAIL) sleepPenaltyMinutes(level) else 0
  val penaltyGames = if (status == SleepStatus.FAIL) sleepPenaltyGames(level) else 0
  val effectiveDate = sleepPenaltyEffectiveDate(cycleDate)
  val version = (old?.version ?: 0) + 1
  val actualReason = when {
   isManualWithoutAuto && reason.isBlank() -> "人工登记差错更正，保持待核验，不予处罚"
   status == SleepStatus.UNVERIFIED && !isSleepTargetValid(targetBed, targetWake) && reason.isBlank() -> "目标睡眠窗口不足7小时配置冲突，提示修正，避免自动判罚"
   else -> reason.trim()
  }
  val savedSource = if (old != null && old.source != "manual" && source == "manual") old.source else source

  val db = writableDatabase
  db.beginTransaction()
  try {
   setSetting("sleep_level_$cycleDate", level.toString())
   setSetting("sleep_failure_level", level.toString())

   val videoKey = "sleep_pending_video_${effectiveDate}_${cycleDate}"
   val totalRequiredVideo = penaltyMin * 60_000L
   val deductedVideo = db.rawQuery("SELECT COALESCE(-SUM(amount), 0) FROM ledger WHERE (task_id = 'sleep:cycle:' || ? OR task_id LIKE 'sleep:%:' || ?) AND kind='video' AND amount<0", arrayOf(cycleDate, cycleDate)).use {
    if (it.moveToFirst()) it.getLong(0) else 0L
   }
   val refundVideoKey = "sleep_refunded_video_$cycleDate"
   val alreadyRefundedVideo = setting(refundVideoKey, "0").toLongOrNull() ?: 0L
   val netDeductedVideo = (deductedVideo - alreadyRefundedVideo).coerceAtLeast(0L)

   val pendingVideo = (totalRequiredVideo - netDeductedVideo).coerceAtLeast(0L)
   setSetting(videoKey, pendingVideo.toString())

   if (totalRequiredVideo < netDeductedVideo) {
    val refundVideo = netDeductedVideo - totalRequiredVideo
    refundCycleDebit(db, cycleDate, "video", refundVideo)
    setSetting(refundVideoKey, (alreadyRefundedVideo + refundVideo).toString())
   }

   val gameKey = "sleep_pending_game_${effectiveDate}_${cycleDate}"
   val totalRequiredGames = penaltyGames
   val deductedGames = db.rawQuery("SELECT COALESCE(-SUM(amount), 0) FROM ledger WHERE (task_id = 'sleep:cycle:' || ? OR task_id LIKE 'sleep:%:' || ?) AND kind='game' AND amount<0", arrayOf(cycleDate, cycleDate)).use {
    if (it.moveToFirst()) it.getInt(0) else 0
   }
   val refundGameKey = "sleep_refunded_game_$cycleDate"
   val alreadyRefundedGames = setting(refundGameKey, "0").toIntOrNull() ?: 0
   val netDeductedGames = (deductedGames - alreadyRefundedGames).coerceAtLeast(0)

   val pendingGames = (totalRequiredGames - netDeductedGames).coerceAtLeast(0)
   setSetting(gameKey, pendingGames.toString())

   if (totalRequiredGames < netDeductedGames) {
    val refundGames = netDeductedGames - totalRequiredGames
    refundCycleDebit(db, cycleDate, "game", refundGames.toLong())
    setSetting(refundGameKey, (alreadyRefundedGames + refundGames).toString())
   }

   val json = org.json.JSONObject()
    .put("date", cycleDate)
    .put("bedtime", bedtime)
    .put("wake", wakeTime)
    .put("bed_penalty", if (status == SleepStatus.FAIL) penaltyMin * 60_000L else 0L)
    .put("wake_penalty", 0L)
    .put("excepted", excepted)
    .put("reason", actualReason)
    .put("source", savedSource)
    .put("status", status.name)
    .put("level", level)
    .put("penalty_minutes", penaltyMin)
    .put("penalty_games", penaltyGames)
    .put("effective_date", effectiveDate)
    .put("version", version)
    .put("duration_minutes", durationMinutes)
    .put("prepare_bedtime", prepareBedtime ?: "")

   setSetting("sleep_record_$cycleDate", json.toString())
   setSetting("sleep_cycle_$cycleDate", json.toString())

   val statusLabel = when (status) {
    SleepStatus.PASS -> "达标（等级清零）"
    SleepStatus.FAIL -> "未达标（等级升至 $level，扣减 $penaltyMin 分钟" + (if (penaltyGames > 0) "、$penaltyGames 局" else "") + "，次日 $effectiveDate 生效）"
    SleepStatus.PAUSED -> "暂停豁免（保持等级 $level，原因：$actualReason）"
    SleepStatus.UNVERIFIED -> "待核验（保持等级 $level" + (if (actualReason.isNotBlank()) "，提示：$actualReason" else "") + "）"
   }
   val auditDetail = "睡眠周期评估 [$cycleDate]: 入睡 $bedtime，起床 $wakeTime，时长 ${durationMinutes / 60}小时${durationMinutes % 60}分，判定：$statusLabel" +
    if (isCascade) "；级联重算（受历史更正影响）" else if (version > 1) "；版本 $version（更正原因：$actualReason）" else ""
   audit(cycleDate, auditDetail)

   db.setTransactionSuccessful()
  } finally {
   db.endTransaction()
  }

  if (!isCascade) {
   recalculateSubsequentCycles(cycleDate)
  }

  return SleepRecord(
   date = cycleDate,
   bedtime = bedtime,
   wakeTime = wakeTime,
   bedtimePenalty = if (status == SleepStatus.FAIL) penaltyMin * 60_000L else 0L,
   wakePenalty = 0L,
   excepted = excepted,
   reason = actualReason,
   source = savedSource,
   status = status,
   version = version,
   prepareBedtime = prepareBedtime,
   durationMinutes = durationMinutes,
   effectiveDate = effectiveDate,
   level = level,
   penaltyMinutes = penaltyMin,
   penaltyGames = penaltyGames
  )
 }

 private fun recalculateSubsequentCycles(afterCycleDate: String) {
  val subsequentDates = mutableListOf<String>()
  readableDatabase.rawQuery("SELECT key FROM settings WHERE key GLOB 'sleep_record_*'", null).use { c ->
   while (c.moveToNext()) {
    val d = c.getString(0).removePrefix("sleep_record_")
    if (d > afterCycleDate) subsequentDates.add(d)
   }
  }
  subsequentDates.sort()
  for (subDate in subsequentDates) {
   val rec = sleepRecord(subDate) ?: continue
   recordSleepCycleInternal(
    cycleDate = subDate,
    bedtime = rec.bedtime,
    wakeTime = rec.wakeTime,
    excepted = rec.excepted,
    reason = rec.reason,
    prepareBedtime = rec.prepareBedtime,
    source = rec.source,
    forcedStatus = if (rec.status == SleepStatus.UNVERIFIED) SleepStatus.UNVERIFIED else null,
    isCascade = true
   )
  }
  val allDates = mutableListOf<String>()
  readableDatabase.rawQuery("SELECT key FROM settings WHERE key GLOB 'sleep_record_*'", null).use { c ->
   while (c.moveToNext()) allDates.add(c.getString(0).removePrefix("sleep_record_"))
  }
  val latestDate = allDates.maxOrNull()
  if (latestDate != null) {
   val latestLevel = setting("sleep_level_$latestDate", "0")
   setSetting("sleep_failure_level", latestLevel)
  }
 }

 fun pauseSleepCycle(cycleDate: String, reason: String): SleepRecord {
  require(reason.isNotBlank()) { "暂停豁免必须填写原因" }
  val targets = sleepTargets()
  return recordSleepCycle(cycleDate, targets[0], targets[1], excepted = true, reason = reason)
 }

 fun enableSleepSystemTasks(enabled: Boolean = true) { setSetting("sleep_system_tasks_enabled", enabled.toString()) }
 fun ensureSleepSystemTasks(day: String = day()) {
  val db = writableDatabase
  val existing = mutableSetOf<String>()
  db.rawQuery("SELECT title FROM tasks WHERE day=?", arrayOf(day)).use {
   while(it.moveToNext()) existing.add(it.getString(0))
  }
  db.beginTransaction()
  try {
   if ("按时准备睡眠" !in existing) {
    val id = UUID.nameUUIDFromBytes(("prepare_sleep_$day").toByteArray()).toString()
    val values = cv(
     "id" to id, "template_id" to "sleep_system_prep", "day" to day,
     "title" to "按时准备睡眠", "required" to true, "minutes" to 0,
     "mode" to "行动型", "checklist" to "系统睡眠管理任务",
     "reward_minutes" to 0, "reward_games" to 0,
     "recurrence" to "daily", "origin" to "sleep_system"
    )
    db.insertWithOnConflict("tasks", null, values, SQLiteDatabase.CONFLICT_IGNORE)
   }
   if ("按时起床" !in existing) {
    val id = UUID.nameUUIDFromBytes(("wake_up_$day").toByteArray()).toString()
    val values = cv(
     "id" to id, "template_id" to "sleep_system_wake", "day" to day,
     "title" to "按时起床", "required" to true, "minutes" to 0,
     "mode" to "行动型", "checklist" to "系统睡眠管理任务",
     "reward_minutes" to 0, "reward_games" to 0,
     "recurrence" to "daily", "origin" to "sleep_system"
    )
    db.insertWithOnConflict("tasks", null, values, SQLiteDatabase.CONFLICT_IGNORE)
   }
   db.setTransactionSuccessful()
  } finally {
   db.endTransaction()
  }
 }

 fun sleepPrepareTime(cycleDate: String = day()): String? =
  setting("sleep_prepare_time_$cycleDate").ifBlank {
   setting("sleep_prepare_$cycleDate").ifBlank { null }
  }

 fun setSleepPrepareTime(cycleDate: String = day(), time: String) {
  setSetting("sleep_prepare_time_$cycleDate", time)
  setSetting("sleep_prepare_$cycleDate", time)
 }

 fun prepareSleep(time: String = now().toLocalTime().toString().take(5), cycleDate: String = day()) {
  setSleepPrepareTime(cycleDate, time)
  audit(cycleDate, "准备就寝确认：$time")
  val task = tasks(cycleDate).firstOrNull { it.title == "按时准备睡眠" }
  if (task != null && !task.done) complete(task)
 }

 fun confirmWake(wakeTime: String = now().toLocalTime().toString().take(5), actualBedtime: String? = null, cycleDate: String = day()): SleepRecord {
  val prepTime = sleepPrepareTime(cycleDate)
  val bed = actualBedtime ?: prepTime ?: sleepTargets()[0]
  val task = tasks(cycleDate).firstOrNull { it.title == "按时起床" }
  if (task != null && !task.done) complete(task)
  return recordSleepCycle(cycleDate, bed, wakeTime, prepareBedtime = prepTime, source = "manual")
 }

 fun recordSleep(date:String,bedtime:String,wake:String,excepted:Boolean=false,reason:String="",source:String="manual") {
  val bed=java.time.LocalTime.parse(bedtime);val woke=java.time.LocalTime.parse(wake);val old=sleepRecord(date)
  require(LocalDate.parse(date)<=LocalDate.parse(day()) && (!excepted||reason.isNotBlank()) && (old==null || old.bedtime==bedtime&&old.wakeTime==wake&&old.excepted==excepted || reason.isNotBlank()))
  val targets=sleepTargets();val bedTarget=java.time.LocalTime.parse(targets[0]);val wakeTarget=java.time.LocalTime.parse(targets[1])
  val bedLate=if(bedTarget.hour<6&&bed.hour>=18) false else bed.isAfter(bedTarget)
  val desiredBed=if(!excepted&&bedLate)targets[2].toLong()*60000 else 0L
  val desiredWake=if(!excepted&&woke.isAfter(wakeTarget))targets[3].toLong()*60000 else 0L
  val db=writableDatabase;db.beginTransaction()
  try {
   fun reconcile(source:String,oldAmount:Long,newAmount:Long):Long {
    val key=pendingSleepKey(date,source);val pendingBefore=setting(key,"0").toLongOrNull()?:0L
    val delta=newAmount-oldAmount
    val pendingAfter=if(delta>=0) pendingBefore+delta else (pendingBefore+delta).coerceAtLeast(0)
    setSetting(key,pendingAfter.toString())
    val reduceApplied=if(delta<0)(-delta-pendingBefore).coerceAtLeast(0) else 0
    if(reduceApplied>0) {
     ledger(db,rewardDay(),"sleep-refund:$source:$date", "video",reduceApplied)
    }
    return newAmount
   }
   val bedPenalty=reconcile("bedtime",old?.bedtimePenalty?:0,desiredBed)
   val wakePenalty=reconcile("wake",old?.wakePenalty?:0,desiredWake)
   val pending=pendingSleepPenalty()
   val durationMinutes = calculateSleepDurationMinutes(date, bedtime, wake)
   val status = if (excepted) SleepStatus.PAUSED else if (bedPenalty == 0L && wakePenalty == 0L && durationMinutes >= 420L) SleepStatus.PASS else SleepStatus.FAIL
   val record=org.json.JSONObject().put("bedtime",bedtime).put("wake",wake).put("bed_penalty",bedPenalty).put("wake_penalty",wakePenalty).put("excepted",excepted).put("reason",reason.trim()).put("source",source).put("duration_minutes",durationMinutes).put("status",status.name)
   setSetting("sleep_record_$date",record.toString())
   val detail=if(excepted) "睡眠例外：${reason.trim()}" else "手动睡眠记录：就寝 $bedtime，起床 $wake；待后续奖励发放扣减 ${(bedPenalty+wakePenalty)/60000} 分钟"+(if(old!=null)"；修正原因：${reason.trim()}" else "")
   if(pending>0)audit(date,"睡眠扣减待下次奖励抵扣：${pending/60000} 分钟")
   audit(date,if(source=="manual")detail else detail.replace("手动睡眠记录","已确认的健康数据记录")+"；来源：$source");db.setTransactionSuccessful()
  } finally {db.endTransaction()}
 }
 private fun issueCompletedRequiredRewards(db:SQLiteDatabase,date:String) {
  if(date!=day())return
  val ownsTransaction=!db.inTransaction();if(ownsTransaction)db.beginTransaction()
  try {db.rawQuery("SELECT * FROM tasks WHERE day=? AND required=1 AND done=1 AND origin!='sleep_system'",arrayOf(date)).use{c->while(c.moveToNext()){
    val task=c.task()
    listOf("video" to task.rewardMinutes.toLong()*60000L,"game" to task.rewardGames.toLong()).forEach{(kind,amount)->
     val key="task_reward_${task.id}_$kind"
     if(amount>0&&setting(key)!="true") {grantReward(db,date,task.id,kind,amount);setSetting(key,"true")}
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
 fun history(requireKnownTypes:Boolean = true): List<Pair<String,String>> {
  val days=mutableListOf<String>();readableDatabase.rawQuery("SELECT day FROM contracts ORDER BY day DESC",null).use{while(it.moveToNext())days.add(it.getString(0))}
  return days.map { date -> date to when(dailyGoalState(tasks(date,includeMoved=true),contract(date).rest,date,day(),requireKnownTypes)) {
   DailyGoalState.ACHIEVED -> "完成"
   DailyGoalState.MISSED -> "未完成"
   DailyGoalState.REST -> "休息"
   DailyGoalState.NO_REQUIRED_TASKS -> "无必做"
   DailyGoalState.UNPLANNED -> "未安排"
   DailyGoalState.IN_PROGRESS -> "进行中"
   DailyGoalState.FUTURE -> "未来"
   DailyGoalState.UNKNOWN -> "待确认"
  } }
 }
 fun growthPeriodStats(today:String=day()):GrowthPeriodStats {
  val end=LocalDate.parse(today);val from30=end.minusDays(29);val from7=end.minusDays((end.dayOfWeek.value-1).toLong())
  var plan30=0;var achieved30=0;var plan7=0;var achieved7=0
  var cursor=from30
  while(!cursor.isAfter(end)) {
   val date=cursor.toString()
   val contractRow=readableDatabase.rawQuery("SELECT confirmed,rest FROM contracts WHERE day=?",arrayOf(date)).use{if(it.moveToFirst())(it.getInt(0)!=0) to (it.getInt(1)!=0) else null}
   var required=0;var done=0
   readableDatabase.rawQuery("SELECT required,done FROM tasks WHERE day=? AND origin!='sleep_system'",arrayOf(date)).use{while(it.moveToNext()){if(it.getInt(0)!=0){required++;if(it.getInt(1)!=0)done++}}}
   val valid=contractRow?.second!=true&&required>0
   if(valid){plan30++;if(done==required)achieved30++;if(!cursor.isBefore(from7)){plan7++;if(done==required)achieved7++}}
   cursor=cursor.plusDays(1)
  }
  var focus=readableDatabase.rawQuery("SELECT COALESCE(SUM(elapsed),0) FROM tasks WHERE origin!='sleep_system' AND id NOT IN (SELECT source_id FROM task_moves) AND day<=?",arrayOf(today)).use{it.moveToFirst();it.getLong(0)}
  runningTasks().filter{it.origin!="sleep_system"}.forEach{task->focus+=(elapsed(task)-task.elapsed).coerceAtLeast(0L)}
  return GrowthPeriodStats(plan30,achieved30,plan7,achieved7,focus)
 }
 fun xp(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM tasks WHERE done=1 AND origin!='sleep_system' AND id NOT IN (SELECT source_id FROM task_moves)", null).use { it.moveToFirst(); it.getInt(0)*100 }
 fun streak(): Int {
  val states=history().toMap();var d=LocalDate.parse(day());var count=0
  if(states[d.toString()]!="完成")d=d.minusDays(1)
  repeat(36500){when(states[d.toString()]){"完成"->count++;"未完成"->return count;else->Unit};d=d.minusDays(1)}
  return count
 }
}
