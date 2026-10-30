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

class Database(context: Context) : SQLiteOpenHelper(context, "journey.db", null, 1) {
 override fun onCreate(db: SQLiteDatabase) {
  db.execSQL("CREATE TABLE contracts(day TEXT PRIMARY KEY, confirmed INTEGER NOT NULL DEFAULT 0, rest INTEGER NOT NULL DEFAULT 0, base_minutes INTEGER NOT NULL DEFAULT 30, base_games INTEGER NOT NULL DEFAULT 0, base_claimed INTEGER NOT NULL DEFAULT 0, emergency INTEGER NOT NULL DEFAULT 0, boundary INTEGER NOT NULL DEFAULT 4)")
  db.execSQL("CREATE TABLE tasks(id TEXT PRIMARY KEY, day TEXT NOT NULL, title TEXT NOT NULL, required INTEGER NOT NULL, minutes INTEGER NOT NULL, mode TEXT NOT NULL, checklist TEXT NOT NULL, reward_minutes INTEGER NOT NULL, reward_games INTEGER NOT NULL, elapsed INTEGER NOT NULL DEFAULT 0, running_since INTEGER NOT NULL DEFAULT 0, boot_time INTEGER NOT NULL DEFAULT 0, evidence TEXT NOT NULL DEFAULT '', done INTEGER NOT NULL DEFAULT 0, claimed INTEGER NOT NULL DEFAULT 0, recurrence TEXT NOT NULL DEFAULT 'once', origin TEXT NOT NULL DEFAULT '')")
  db.execSQL("CREATE TABLE ledger(id TEXT PRIMARY KEY, day TEXT NOT NULL, task_id TEXT NOT NULL, kind TEXT NOT NULL, amount INTEGER NOT NULL, created INTEGER NOT NULL)")
  db.execSQL("CREATE TABLE reviews(day TEXT PRIMARY KEY, problem TEXT NOT NULL, praise TEXT NOT NULL, note TEXT NOT NULL)")
  db.execSQL("CREATE TABLE audit(id TEXT PRIMARY KEY, day TEXT NOT NULL, detail TEXT NOT NULL, created INTEGER NOT NULL)")
  db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
 }
 override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
 private fun cv(vararg fields: Pair<String, Any?>) = ContentValues().apply { fields.forEach { (k, v) -> when (v) { is String -> put(k,v); is Int -> put(k,v); is Long -> put(k,v); is Boolean -> put(k,if(v) 1 else 0); null -> putNull(k) } } }
 private fun Cursor.s(name: String) = getString(getColumnIndexOrThrow(name))
 private fun Cursor.i(name: String) = getInt(getColumnIndexOrThrow(name))
 private fun Cursor.l(name: String) = getLong(getColumnIndexOrThrow(name))
 private fun Cursor.b(name: String) = i(name) != 0
 fun setting(key: String, default: String = ""): String = readableDatabase.rawQuery("SELECT value FROM settings WHERE key=?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else default }
 fun setSetting(key: String, value: String) { writableDatabase.insertWithOnConflict("settings", null, cv("key" to key,"value" to value), SQLiteDatabase.CONFLICT_REPLACE) }
 fun importPlan(target: String, drafts: List<PlanDraft>) {
  val db=writableDatabase; db.beginTransaction()
  try { require(!contract(target).confirmed); val existing=tasks(target).map { it.title }.toSet(); drafts.filter { it.title !in existing }.forEach { addTask(target,it.title,it.required,0,"行动型","按计划完成并自检",20,1,"once") }; setSetting("import_draft",""); audit(target,"从文本导入 ${drafts.size} 项待确认任务"); db.setTransactionSuccessful() } finally { db.endTransaction() }
 }
 fun day(): String {
  val now = LocalDateTime.now(); val boundary = setting("boundary", "4").toIntOrNull()?.coerceIn(0, 23) ?: 4
  return (if (now.hour < boundary) now.toLocalDate().minusDays(1) else now.toLocalDate()).toString()
 }
 fun ensure(day: String = day()) {
  val db = writableDatabase
  val confirmed = db.rawQuery("SELECT confirmed FROM contracts WHERE day=?", arrayOf(day)).use { if(it.moveToFirst()) it.getInt(0) != 0 else null }
  if (confirmed == true) return
  db.beginTransaction()
  try {
   if(confirmed == null) db.insert("contracts", null, cv("day" to day, "boundary" to setting("boundary", "4").toInt()))
   db.rawQuery("SELECT * FROM tasks WHERE day=origin AND origin<? AND recurrence != 'once'", arrayOf(day)).use { c ->
    while(c.moveToNext()) {
     val recurrence = c.s("recurrence")
     if (recurrence == "daily" || recurrence == "weekly" && LocalDate.parse(day).dayOfWeek == LocalDate.parse(c.s("origin")).dayOfWeek) {
      val instanceId = UUID.nameUUIDFromBytes((c.s("id") + day).toByteArray()).toString()
      db.insertWithOnConflict("tasks", null, cv("id" to instanceId, "day" to day, "title" to c.s("title"), "required" to c.i("required"), "minutes" to c.i("minutes"), "mode" to c.s("mode"), "checklist" to c.s("checklist"), "reward_minutes" to c.i("reward_minutes"), "reward_games" to c.i("reward_games"), "recurrence" to recurrence, "origin" to c.s("origin")), SQLiteDatabase.CONFLICT_IGNORE)
     }
    }
   }
   db.setTransactionSuccessful()
  } finally { db.endTransaction() }
 }
 fun contract(day: String = day()): Contract { ensure(day); return readableDatabase.rawQuery("SELECT * FROM contracts WHERE day=?", arrayOf(day)).use { c -> c.moveToFirst(); Contract(day,c.b("confirmed"),c.b("rest"),c.i("base_minutes"),c.i("base_games"),c.b("base_claimed"),c.b("emergency")) } }
 fun tasks(day: String = day()): List<Task> { ensure(day); val list = mutableListOf<Task>(); readableDatabase.rawQuery("SELECT * FROM tasks WHERE day=? ORDER BY required DESC, rowid", arrayOf(day)).use { c -> while(c.moveToNext()) list.add(Task(c.s("id"),c.s("day"),c.s("title"),c.b("required"),c.i("minutes"),c.s("mode"),c.s("checklist"),c.i("reward_minutes"),c.i("reward_games"),c.l("elapsed"),c.l("running_since"),c.l("boot_time"),c.s("evidence"),c.b("done"),c.b("claimed"))) }; return list }
 fun runningTasks(): List<Task> { val days=mutableListOf<String>(); readableDatabase.rawQuery("SELECT DISTINCT day FROM tasks WHERE running_since>0",null).use { while(it.moveToNext()) days.add(it.getString(0)) }; return days.flatMap { tasks(it).filter { t -> t.runningSince>0 } } }
 fun addTask(day: String, title: String, required: Boolean, minutes: Int, mode: String, checklist: String, rewardMinutes: Int, rewardGames: Int, recurrence: String) {
  ensure(day); val contract = contract(day); require(title.isNotBlank() && (!contract.confirmed || !required))
  writableDatabase.insertOrThrow("tasks", null, cv("id" to UUID.randomUUID().toString(),"day" to day,"title" to title.trim(),"required" to required,"minutes" to minutes,"mode" to mode,"checklist" to checklist,"reward_minutes" to rewardMinutes,"reward_games" to rewardGames,"recurrence" to recurrence,"origin" to day))
 }
 fun confirm(day: String, rest: Boolean, minutes: Int, games: Int) {
  val c = contract(day); require(!c.confirmed); require(!rest || tasks(day).none { it.required })
  writableDatabase.update("contracts",cv("confirmed" to true,"rest" to rest,"base_minutes" to minutes,"base_games" to games),"day=?", arrayOf(day))
 }
 fun eligible(day: String = day()): Boolean { val c = contract(day); val required = tasks(day).filter { it.required }; return c.confirmed && (required.isNotEmpty() && required.all { it.done } || required.isEmpty() && c.rest) }
 fun grantBase(day: String = day()) {
  val db = writableDatabase; db.beginTransaction()
  try { val c = contract(day); if (eligible(day) && !c.baseClaimed) { ledger(db,day,"base","video",c.baseMinutes * 60000L); ledger(db,day,"base","game",c.baseGames.toLong()); db.update("contracts",cv("base_claimed" to true),"day=?",arrayOf(day)) }; db.setTransactionSuccessful() } finally { db.endTransaction() }
 }
 private fun ledger(db: SQLiteDatabase, day: String, task: String, kind: String, amount: Long) { db.insertOrThrow("ledger", null, cv("id" to UUID.randomUUID().toString(),"day" to day,"task_id" to task,"kind" to kind,"amount" to amount,"created" to System.currentTimeMillis())) }
 fun balance(kind: String, day: String = day()): Long = readableDatabase.rawQuery("SELECT COALESCE(SUM(amount),0) FROM ledger WHERE day=? AND kind=?",arrayOf(day,kind)).use { it.moveToFirst(); it.getLong(0).coerceAtLeast(0) }
 fun consume(kind: String, amount: Long, day: String = day()) { val db = writableDatabase; db.beginTransaction(); try { val actual = minOf(balance(kind,day),amount.coerceAtLeast(0)); if(actual > 0) ledger(db,day,"usage",kind,-actual); db.setTransactionSuccessful() } finally { db.endTransaction() } }
 fun claim(task: Task, kind: String) { val db = writableDatabase; db.beginTransaction(); try { val fresh = tasks(task.date).first { it.id == task.id }; if (eligible(task.date) && !fresh.required && fresh.done && !fresh.claimed && task.date == day()) { val amount = if(kind == "video") fresh.rewardMinutes * 60000L else fresh.rewardGames.toLong(); require(amount > 0); ledger(db,task.date,task.id,kind,amount); db.update("tasks",cv("claimed" to true),"id=?",arrayOf(task.id)) }; db.setTransactionSuccessful() } finally { db.endTransaction() } }
 fun start(task: Task) { if(task.done || task.runningSince != 0L) return; writableDatabase.update("tasks",cv("running_since" to SystemClock.elapsedRealtime(),"boot_time" to (System.currentTimeMillis()-SystemClock.elapsedRealtime())),"id=?",arrayOf(task.id)) }
 fun elapsed(task: Task): Long { val sameBoot = kotlin.math.abs((System.currentTimeMillis()-SystemClock.elapsedRealtime()) - task.bootTime) < 120000; return task.elapsed + if(task.runningSince > 0 && sameBoot) (SystemClock.elapsedRealtime()-task.runningSince).coerceAtLeast(0) else 0 }
 fun pause(task: Task) { writableDatabase.update("tasks",cv("elapsed" to elapsed(task),"running_since" to 0L,"boot_time" to 0L),"id=?",arrayOf(task.id)) }
 fun evidence(task: Task, path: String) { writableDatabase.update("tasks",cv("evidence" to path),"id=?",arrayOf(task.id)) }
 fun complete(task: Task) { pause(task); val fresh=tasks(task.date).first { it.id == task.id }; require(fresh.elapsed >= fresh.minutes * 60000L && (fresh.mode == "行动型" || fresh.evidence.isNotBlank())); writableDatabase.update("tasks",cv("done" to true),"id=?",arrayOf(task.id)) }
 fun removeDraft(task: Task) { require(!contract(task.date).confirmed && !task.done); writableDatabase.delete("tasks","id=?",arrayOf(task.id)) }
 fun editDraft(task: Task, title:String, minutes:Int, checklist:String) { require(!contract(task.date).confirmed && !task.done && title.isNotBlank()); writableDatabase.update("tasks",cv("title" to title.trim(),"minutes" to minutes,"checklist" to checklist),"id=?",arrayOf(task.id)) }
 fun rewardHistory(): List<String> {
  val out=mutableListOf<String>()
  readableDatabase.rawQuery("SELECT day,kind,SUM(CASE WHEN amount>0 THEN amount ELSE 0 END),SUM(CASE WHEN amount<0 THEN -amount ELSE 0 END) FROM ledger GROUP BY day,kind ORDER BY day DESC LIMIT 30",null).use { c -> while(c.moveToNext()) { val video=c.getString(1)=="video"; val divisor=if(video) 60000.0 else 1.0; out.add("${c.getString(0)} · ${if(video) "视频分钟" else "游戏局数"}  获得 ${"%.1f".format(c.getLong(2)/divisor)} / 使用与冲正 ${"%.1f".format(c.getLong(3)/divisor)}") } }
  return out
 }
 fun revoke(task: Task) {
  val db = writableDatabase; db.beginTransaction()
  try {
   val fresh = tasks(task.date).first { it.id == task.id }
   if(fresh.done) {
    val source = if(fresh.required) "base" else fresh.id
    for(kind in listOf("video","game")) {
     val granted = db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM ledger WHERE day=? AND task_id=? AND kind=?",arrayOf(task.date,source,kind)).use { it.moveToFirst(); it.getLong(0).coerceAtLeast(0) }
     val reversal = minOf(granted,balance(kind,task.date))
     if(reversal > 0) ledger(db,task.date,source,kind,-reversal)
    }
    db.update("tasks",cv("done" to false),"id=?",arrayOf(task.id)); audit(task.date,"撤销任务：${fresh.title}；已领取额度按剩余余额冲正")
   }
   db.setTransactionSuccessful()
  } finally { db.endTransaction() }
 }
 fun emergency(day: String = day()) { ensure(day); writableDatabase.update("contracts",cv("emergency" to true),"day=?",arrayOf(day)); audit(day,"紧急解锁") }
 fun resumeProtection(day: String = day()) { writableDatabase.update("contracts",cv("emergency" to false),"day=?",arrayOf(day)); audit(day,"恢复保护") }
 fun learningActive(): Boolean = System.currentTimeMillis() < (setting("learning_until", "0").toLongOrNull() ?: 0)
 fun startLearning() { setSetting("learning_until",(System.currentTimeMillis()+15*60000).toString()); audit(day(),"B站学习窗口：15 分钟") }
 fun audit(day: String, detail: String) { writableDatabase.insert("audit",null,cv("id" to UUID.randomUUID().toString(),"day" to day,"detail" to detail,"created" to System.currentTimeMillis())) }
 fun review(day: String, problem: String, praise: String, note: String) { writableDatabase.insertWithOnConflict("reviews",null,cv("day" to day,"problem" to problem,"praise" to praise,"note" to note),SQLiteDatabase.CONFLICT_REPLACE) }
 fun readReview(day: String): List<String> = readableDatabase.rawQuery("SELECT problem,praise,note FROM reviews WHERE day=?",arrayOf(day)).use { if(it.moveToFirst()) listOf(it.getString(0),it.getString(1),it.getString(2)) else listOf("","","") }
 fun history(): List<Pair<String,String>> { val days=mutableListOf<String>(); readableDatabase.rawQuery("SELECT day FROM contracts ORDER BY day DESC LIMIT 90",null).use { while(it.moveToNext()) days.add(it.getString(0)) }; return days.map { d -> val c=contract(d); d to when { !c.confirmed -> "未生效"; c.rest -> "休息"; eligible(d) -> "完成"; else -> "未完成" } } }
 fun xp(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM tasks WHERE done=1", null).use { it.moveToFirst(); it.getInt(0)*100 }
 fun streak(): Int {
  val states=history().toMap(); var d=LocalDate.parse(day()); var count=0
  if(states[d.toString()] != "完成" && states[d.toString()] != "休息") d=d.minusDays(1)
  repeat(90) { when(states[d.toString()]) { "完成" -> count++; "休息" -> Unit; else -> return count }; d=d.minusDays(1) }
  return count
 }
}
