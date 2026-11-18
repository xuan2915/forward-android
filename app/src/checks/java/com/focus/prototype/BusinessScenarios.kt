package com.focus.prototype
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import java.io.File
import java.time.LocalDateTime
fun runBusinessChecks(targetContext:Context):List<String> {
val checks=mutableListOf<String>();var now=LocalDateTime.parse("2026-10-30T12:00:00")
fun resign(root:org.json.JSONObject,payload:org.json.JSONObject):String { val body=payload.toString();val digest=java.security.MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8)).joinToString(""){ "%02x".format(it) };root.put("payload",body).put("sha256",digest);return root.toString() }
  val context=object:ContextWrapper(targetContext){override fun getFilesDir()=File(super.getFilesDir(),"business-checks").apply{mkdirs()};override fun getApplicationContext():Context=this;override fun getSharedPreferences(name:String,mode:Int):SharedPreferences=super.getSharedPreferences("check_$name",mode)}
  val name="business-checks.db";context.deleteDatabase(name)
  var db=Database(context,name){now}
  fun test(name:String,block:()->Unit){block();checks.add(name)}
  try {
   test("recurrence, per-task mandatory reward, duplicate completion") {
    db.addTask(db.day(),"required",true,0,"行动型","done",20,1,"days:1,3,5")
    db.addTask(db.day(),"optional",false,0,"行动型","done",20,1,"once")
    db.confirm(db.day(),false,30,1);check(db.eligible());val required=db.tasks().first{it.required};db.complete(required);check(db.balance("video")==1200000L);db.complete(required);check(db.balance("video")==1200000L)
   }
   test("one mandatory reward is usable with other mandatory work unfinished") {
    val name = "d11-reward.db"
    context.deleteDatabase(name)
    Database(context, name) { now }.use { d ->
     val today = d.day()
     d.addTask(today, "reward", true, 0, "行动型", "", 2, 1, "once")
     d.addTask(today, "unfinished", true, 0, "行动型", "", 0, 0, "once")
     d.complete(d.tasks().first { it.title == "reward" })
     check(d.tasks().any { it.required && !it.done })
     val first = d.rewardKind("qa.calendar")
     val second = d.rewardKind("qa.clock")
     check(first == "video" && second == first)
     d.consume(first, 60_000)
     check(d.balance(second) == 60_000L)
     d.consume(second, 90_000)
     check(d.balance(first) == 0L && d.balance("game") == 1L)
     d.setRewardKind("qa.calendar", "game")
     check(d.rewardKind("qa.calendar") == "game" && d.rewardKind("qa.clock") == "video")
    }
   }
   test("custom interval recurrence is anchored and generated once") {
    val other="interval-recurrence.db";context.deleteDatabase(other);var clock=LocalDateTime.parse("2026-11-01T12:00:00");val d=Database(context,other){clock}
    try {val start=d.day();d.addTask(start,"every three days",true,0,"行动型","",0,0,"interval:3");check(d.tasks(start).single().title=="every three days");clock=clock.plusDays(2);check(d.tasks().isEmpty());clock=clock.plusDays(1);check(d.tasks().single().date=="2026-11-04");repeat(3){d.ensure()};check(d.tasks().size==1);clock=clock.plusDays(3);check(d.tasks().single().date=="2026-11-07")}finally{d.close()}
   }
   test("optional choice idempotency and consumption") {
    val optional=db.tasks().first{!it.required};db.complete(optional);repeat(3){db.claim(optional,"video")};db.claim(optional,"game");check(db.balance("video")==2400000L);check(db.balance("game")==1L);db.consume("video",60000);check(db.balance("video")==2340000L)
   }
   test("revoking optional completion reverses available reward and reopens claim") {
    val other="optional-revoke-reward.db";context.deleteDatabase(other);val d=Database(context,other,now={now})
    try {
     val date=d.day();d.addTask(date,"required",true,0,"行动型","",0,0,"once");d.addTask(date,"optional",false,0,"行动型","",10,1,"once");d.confirm(date,false,0,0)
     d.complete(d.tasks(date).first{it.required});val optional=d.tasks(date).first{!it.required};d.complete(optional);d.claim(optional,"video")
     check(d.balance("video")==600000L&&d.tasks(date).first{it.id==optional.id}.claimed)
     d.consume("video",300000L);d.revoke(d.tasks(date).first{it.id==optional.id})
     val reopened=d.tasks(date).first{it.id==optional.id};check(!reopened.done&&!reopened.claimed&&d.balance("video")==0L)
     d.complete(reopened);val completedAgain=d.tasks(date).first{it.id==optional.id};check(completedAgain.done&&!completedAgain.claimed)
     d.claim(completedAgain,"video");d.claim(completedAgain,"video")
     val snapshot=d.rewardSnapshot("video");check(snapshot.used==300000L&&snapshot.reversed==300000L&&snapshot.remaining==600000L)
    } finally {d.close()}
   }
   test("reopen preserves completion and balance") {
    db.close();db=Database(context,name){now};check(db.tasks().count{it.done}==2);check(db.balance("video")==2340000L);db.complete(db.tasks().first{it.required});check(db.balance("video")==2340000L)
   }
   test("tasks roll at midnight while reward balance lasts until 04") {
    val other="separate-day-boundaries.db";context.deleteDatabase(other);var clock=LocalDateTime.parse("2026-10-30T23:50:00");val d=Database(context,other){clock}
    try {
     val firstDay=d.day();d.addTask(firstDay,"before midnight",true,0,"行动型","",10,0,"once");d.confirm(firstDay,false,0,0);d.complete(d.tasks().single());check(d.balance("video")==600000L)
     clock=LocalDateTime.parse("2026-10-31T00:01:00");check(d.day()=="2026-10-31"&&d.rewardDay()=="2026-10-30");check(d.tasks().isEmpty());check(d.balance("video")==600000L)
     d.addTask(d.day(),"after midnight",true,0,"行动型","",5,0,"once");d.confirm(d.day(),false,0,0);d.complete(d.tasks().single());check(d.balance("video")==900000L)
     clock=LocalDateTime.parse("2026-10-31T04:00:00");d.ensure();check(d.rewardDay()=="2026-10-31");check(d.balance("video")==0L)
     d.addTask(d.day(),"after reset",false,0,"行动型","",7,0,"once");val afterReset=d.tasks().first{it.title=="after reset"};d.complete(afterReset);d.claim(afterReset,"video");check(d.balance("video")==420000L)
    } finally {d.close()}
   }
   test("historical task backfill keeps actual time and reconciles its reward once") {
    val other="historical-backfill.db";context.deleteDatabase(other)
    var clock=LocalDateTime.parse("2026-11-12T23:50:00")
    val actual=LocalDateTime.parse("2026-11-13T00:20:00").atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val d=Database(context,other,wallNow={actual},now={clock})
    try {
     val planned="2026-11-12";d.addTask(planned,"历史必做",true,0,"行动型","按原要求完成",10,0,"once");d.addTask(planned,"成果证明",true,0,"成果型","提交真实证明",0,0,"once");d.confirm(planned,false,0,0)
     clock=LocalDateTime.parse("2026-11-13T00:20:00")
     check(d.day()=="2026-11-13"&&d.rewardDay()=="2026-11-12")
     val required=d.tasks(planned).first{it.title=="历史必做"};d.complete(required)
     val completed=d.tasks(planned).first{it.id==required.id}
     check(completed.done&&completed.completedAt==actual&&d.goalState(planned)==DailyGoalState.MISSED)
     check(d.balance("video")==600000L);d.ensure(planned);check(d.balance("video")==600000L)
     val proof=d.tasks(planned).first{it.title=="成果证明"};check(runCatching{d.complete(proof)}.isFailure);d.submitProof(proof,"已确认本次提交");d.complete(d.tasks(planned).first{it.id==proof.id});check(d.goalState(planned)==DailyGoalState.ACHIEVED)
     d.revoke(d.tasks(planned).first{it.id==required.id});check(d.balance("video")==0L&&d.goalState(planned)==DailyGoalState.MISSED)
     d.complete(d.tasks(planned).first{it.id==required.id});check(d.balance("video")==600000L);d.complete(d.tasks(planned).first{it.id==required.id});check(d.balance("video")==600000L)
    } finally {d.close()}
   }
   test("daily tasks and reward expiry at their separate boundaries") {
    now=LocalDateTime.parse("2026-10-31T03:59:59");check(db.day()=="2026-10-31");check(db.rewardDay()=="2026-10-30");now=now.plusSeconds(1);check(db.day()=="2026-10-31");check(db.rewardDay()=="2026-10-31");db.ensure();check(db.balance("video","2026-10-30")==0L);check(db.tasks().isEmpty());check(db.tasks("2026-10-30").all{it.done})
   }
   test("weekday generates once, template edit preserves history") {
    now=LocalDateTime.parse("2026-11-02T12:00:00");check(db.tasks().single().title=="required");db.ensure();check(db.tasks().size==1);val task=db.tasks().single();db.revise(task,"updated",true,0,"行动型","new",10,1,"daily","",applyFuture=true);check(db.tasks("2026-10-30").first{it.required}.title=="required");check(db.tasks("2026-11-03").single().title=="updated")
   }
   test("frozen edit requires reason, deletion does not regenerate") {
    db.confirm(db.day(),false,30,0);val task=db.tasks().single();check(runCatching{db.revise(task,"x",true,0,"行动型","",0,0,"daily","")}.isFailure);db.revise(task,"x",true,0,"行动型","",0,0,"daily","incorrect input",delete=true);check(db.tasks().isEmpty());check(!db.eligible());check(db.setting("changed_${db.day()}")=="true")
   }
   test("planned rest preserves streak semantics and no mandatory") {
    db.planRest("2026-11-04");check(db.contract("2026-11-04").rest);check(db.tasks("2026-11-04").none{it.required})
   }
   test("boundary change is deferred") {
    val before=db.day();val rewardBefore=db.rewardDay();db.scheduleBoundary(6);check(db.day()==before&&db.rewardDay()==rewardBefore);now=LocalDateTime.parse("2026-11-03T05:59:59");check(db.rewardDay()=="2026-11-02");now=now.plusSeconds(1);check(db.rewardDay()=="2026-11-03")
   }
   test("proof, review, checksum preview and backup restore") {
    val date=db.day();db.addTask(date,"proof",false,0,"成果型","photo",5,1,"once");val task=db.tasks().first{it.title=="proof"};val file=File(context.filesDir,"evidence/proof.txt");file.parentFile!!.mkdirs();file.writeText("evidence bytes");db.evidence(task,file.absolutePath);db.review(date,"problem","praise","note");Store(context).selected=setOf("tv.danmaku.bili");val saved=Backup.export(context,db);val preview=Backup.inspect(saved);check(preview.tasks>=db.tasks().size&&preview.attachments==1&&preview.integrity=="完整性校验通过");db.setSetting("extra","x");repeat(2){Backup.restore(context,db,saved)};check(db.setting("extra")=="");check(db.readReview(date)==listOf("problem","praise","note"));val proof=db.tasks().first{it.title=="proof"};check(File(proof.evidence).readText()=="evidence bytes");check(Store(context).selected==setOf("tv.danmaku.bili"))
   }
   test("invalid restore leaves records intact") { val before=db.xp();check(runCatching{Backup.restore(context,db,"{}")}.isFailure);check(db.xp()==before) }
   test("modified backup fails checksum without changing records") { val before=db.xp();val root=org.json.JSONObject(Backup.export(context,db)).put("sha256","tampered");check(runCatching{Backup.restore(context,db,root.toString())}.isFailure);check(db.xp()==before) }
   test("multiple full attachments survive restore and removal") {
    val task=db.tasks().first{it.title=="proof"};val second=File(context.filesDir,"evidence/second.pdf");second.writeText("%PDF-test-bytes");db.evidence(task,second.absolutePath);check(db.attachments(task).size==2);val saved=Backup.export(context,db);repeat(3){Backup.restore(context,db,saved)};check(db.attachments(task).map{File(it.second).readText()}.toSet()==setOf("evidence bytes","%PDF-test-bytes"));val removed=db.attachments(task).last();db.removeAttachment(task,removed.first);check(db.attachments(task).size==1);check(!File(removed.second).exists())
   }
   test("no-save proof remains verifiable without becoming an attachment") {
    val other="no-save-proof.db";context.deleteDatabase(other);val d=Database(context,other){now}
    try {val date=d.day();d.addTask(date,"no local proof",true,0,"成果型","",4,0,"once");val task=d.tasks().single();d.confirm(date,false,0,0);d.submitProof(task,"image selection");d.complete(task);val backup=Backup.export(context,d);val preview=Backup.inspect(backup);check(preview.attachments==0&&preview.completedTasks==1);d.setSetting("discard-me","yes");Backup.restore(context,d,backup);val restored=d.tasks().single();check(restored.done&&restored.evidence.startsWith("proof-submitted:")&&d.attachments(restored).isEmpty());check(Backup.export(context,d).isNotBlank())}finally{d.close()}
   }
   test("focus pause resume checkpoint and stale events") {
    val task=db.tasks().first{it.title=="proof"};db.start(task);android.os.SystemClock.sleep(150);db.start(task);db.checkpoint();val elapsed=db.elapsed(db.tasks().first{it.id==task.id});check(elapsed>=100);db.pause(task);db.pause(task);check(db.tasks().first{it.id==task.id}.elapsed>=elapsed);db.close();db=Database(context,name){now};check(db.tasks().first{it.id==task.id}.elapsed>=elapsed)
   }
   test("focus presets, pause restore, rest countdown and free timer") {
    val other="focus-clock.db";context.deleteDatabase(other);var mono=100000L;var wall=1_800_000_000_000L;var d=Database(context,other){now};d.addTask(d.day(),"timer",true,0,"行动型","",0,0,"once");val task=d.tasks().single();d.confirm(task.date,false,0,0);var timer=FocusTimer(d,{mono},{wall})
     try{check(timer.mode==FocusMode.POMODORO);timer.begin(task);check(timer.remaining()==25*60_000L);mono+=60_000;wall+=60_000;timer.pause();check(timer.phase=="paused"&&timer.remaining()==24*60_000L);val saved=d.elapsed(d.task(task.id)!!);d.close();d=Database(context,other){now};timer=FocusTimer(d,{mono},{wall});check(timer.phase=="paused"&&timer.remaining()==24*60_000L);timer.resume(d.task(task.id)!!);mono+=30_000;wall+=30_000;timer.takeBreak();check(timer.phase=="break"&&timer.remaining()==5*60_000L);check(d.task(task.id)!!.runningSince==0L);mono+=5*60_000;wall+=5*60_000;check(timer.advance());check(timer.phase=="ready"&&timer.notice=="休息结束");timer.dismissNotice();timer.selectMode(FocusMode.DEEP);timer.begin(d.task(task.id)!!);check(timer.remaining()==50*60_000L);timer.finish();timer.selectMode(FocusMode.FREE);timer.begin(d.task(task.id)!!);mono+=90_000;wall+=90_000;check(timer.elapsed()==90_000L&&formatFocusTime(timer.elapsed())=="01:30");timer.pause();check(timer.elapsed()==90_000L);timer.finish();check(d.elapsed(d.task(task.id)!!) >= saved)}finally{d.close()}
   }
   test("past rewards cannot be resurrected") {check(db.balance("video","2026-10-30")==0L)}
   test("rest bridges streak, emergency earns nothing, missed day resets") {
    val other="streak-check.db";context.deleteDatabase(other);var clock=LocalDateTime.parse("2026-10-01T12:00:00");Database(context,other){clock}.use{d->
     d.addTask(d.day(),"one",true,0,"行动型","",0,0,"daily");d.confirm(d.day(),false,0,0);d.complete(d.tasks().single());d.planRest("2026-10-02");clock=clock.plusDays(1);d.settleToday();check(d.streak()==1);clock=clock.plusDays(1);d.confirm(d.day(),false,0,0);d.complete(d.tasks().single());check(d.streak()==2);check(d.bestStreak()==2);clock=clock.plusDays(1);d.ensure();d.emergency();check(d.xp()==200);check(d.balance("video")==0L);clock=clock.plusDays(1);check(d.streak()==0)
    }
   }
   test("restore SQL failure rolls back records and staged attachments") {val before=db.xp();val filesBefore=File(context.filesDir,"evidence").listFiles()?.size?:0;val root=org.json.JSONObject(Backup.export(context,db));val payload=org.json.JSONObject(root.getString("payload"));val rows=payload.getJSONObject("data").getJSONArray("tasks");rows.put(org.json.JSONObject(rows.getJSONObject(0).toString()));check(runCatching{Backup.restore(context,db,resign(root,payload))}.isFailure);check(db.xp()==before);check((File(context.filesDir,"evidence").listFiles()?.size?:0)==filesBefore);check(db.tasks().any{it.title=="proof"&&File(it.evidence).readText()=="evidence bytes"})}
   test("legacy backup migrates templates and attachments") {
    val exported=org.json.JSONObject(Backup.export(context,db));val payload=org.json.JSONObject(exported.getString("payload"));val data=payload.getJSONObject("data");data.remove("templates");data.remove("attachments");val rows=data.getJSONArray("tasks");for(i in 0 until rows.length())rows.getJSONObject(i).remove("template_id");val files=org.json.JSONObject();val assets=payload.getJSONArray("assets");for(i in 0 until assets.length()){val asset=assets.getJSONObject(i);files.put(asset.getString("name"),asset.getString("content"))};val legacy=org.json.JSONObject().put("version",4).put("data",data).put("selected",payload.getJSONArray("selected")).put("files",files);Backup.restore(context,db,legacy.toString());check(db.attachments(db.tasks().first{it.title=="proof"}).size==1);check(db.tasks("2026-11-06").any{it.title=="required"});check(Backup.inspect(legacy.toString()).integrity.contains("旧版"))
   }
   test("missed dates generate recurring records without completion") {
    val other="gap-check.db";context.deleteDatabase(other);var clock=LocalDateTime.parse("2026-10-01T12:00:00");val d=Database(context,other){clock}
    try{d.addTask(d.day(),"repeat",true,0,"行动型","",0,0,"daily");clock=clock.plusDays(4);d.ensure();check(d.tasks("2026-10-02").single().done==false);check(d.tasks("2026-10-04").size==1);check(d.history().count{it.second=="未完成"}==4);repeat(3){d.ensure()};check(d.tasks().size==1)}finally{d.close()}
   }
   test("schema v1 upgrade preserves task history") {
     val other="migration-check.db";context.deleteDatabase(other);val old=Database(context,other){now};old.addTask(old.day(),"legacy",true,0,"行动型","",0,0,"daily");old.confirm(old.day(),false,5,0);old.complete(old.tasks().single());val sql=old.writableDatabase;val create=sql.rawQuery("SELECT sql FROM sqlite_master WHERE name='tasks'",null).use{it.moveToFirst();it.getString(0)}.replace(Regex(",\\s*template_id TEXT NOT NULL DEFAULT ''"),"").replace(Regex(",\\s*completed_at INTEGER NOT NULL DEFAULT 0"),"");sql.execSQL("ALTER TABLE tasks RENAME TO previous_tasks");sql.execSQL(create);sql.execSQL("INSERT INTO tasks SELECT "+sql.rawQuery("PRAGMA table_info(tasks)",null).use{val cols=mutableListOf<String>();while(it.moveToNext())cols.add(it.getString(1));cols.joinToString(",")}+" FROM previous_tasks");sql.execSQL("DROP TABLE previous_tasks");sql.execSQL("DROP TABLE templates");sql.execSQL("DROP TABLE attachments");sql.version=1;old.close();val upgraded=Database(context,other){now};try{check(upgraded.tasks().single().done);check(upgraded.balance("video")==0L);check(upgraded.tasks(java.time.LocalDate.parse(upgraded.day()).plusDays(1).toString()).single().title=="legacy")}finally{upgraded.close()}
   }
   test("frozen mandatory deletion cannot unlock after others complete") {val other="change-gate.db";context.deleteDatabase(other);val d=Database(context,other){now};try{val today=d.day();d.addTask(today,"one",true,0,"行动型","",0,0,"once");d.addTask(today,"two",true,0,"行动型","",0,0,"once");d.confirm(today,false,30,0);val deleted=d.tasks().first();d.revise(deleted,deleted.title,deleted.required,0,deleted.mode,deleted.checklist,0,0,"once","mistake",delete=true);d.complete(d.tasks().first());check(!d.eligible());check(d.balance("video")==0L)}finally{d.close()}}
   test("moving a frozen task preserves an unfinished source and target instance") {
    val other="move-task.db";context.deleteDatabase(other);val d=Database(context,other){now}
     try {val sourceDay=d.day();d.addTask(sourceDay,"move me",true,0,"行动型","",10,0,"daily");d.confirm(sourceDay,false,0,0);val source=d.tasks().single();val targetDay=java.time.LocalDate.parse(sourceDay).plusDays(1).toString();val targetId=d.moveTask(source,targetDay,"今天来不及");check(d.tasks(sourceDay).isEmpty());check(!d.tasks(sourceDay,true).single().done);val target=d.tasks(targetDay).single();check(target.id==targetId&&target.title==source.title&&!target.done);check(d.balance("video",sourceDay)==0L);check(d.setting("changed_$sourceDay")=="true");check(!d.eligible(sourceDay))} finally {d.close()}
   }
   test("moving into a completed recurring instance cannot create a duplicate") {
    val other="move-duplicate.db";context.deleteDatabase(other);val d=Database(context,other){now}
     try {val date=d.day();val tomorrow=java.time.LocalDate.parse(date).plusDays(1).toString();d.addTask(date,"daily",true,0,"行动型","",0,0,"daily");d.confirm(date,false,0,0);d.addTask(tomorrow,"other",true,0,"行动型","",0,0,"once");val source=d.tasks(date).single();val target=d.tasks(tomorrow).first{it.title=="daily"};d.writableDatabase.execSQL("UPDATE tasks SET done=1 WHERE id=?",arrayOf(target.id));check(runCatching{d.moveTask(source,tomorrow,"原因")}.isFailure);check(d.tasks(tomorrow).count{it.title=="daily"}==1);check(d.tasks(date).single().id==source.id)}finally{d.close()}
   }
   test("source-aware reward reversal never takes another task reward") {
    val other="reward-reversal.db";context.deleteDatabase(other);val d=Database(context,other){now}
    try {val date=d.day();d.addTask(date,"first",true,0,"行动型","",10,0,"once");d.addTask(date,"second",true,0,"行动型","",20,0,"once");d.confirm(date,false,0,0);val tasks=d.tasks();val first=tasks.first{it.title=="first"};val second=tasks.first{it.title=="second"};d.complete(first);d.complete(second);check(d.balance("video")==1800000L);d.consume("video",1500000);check(d.balance("video")==300000L);d.revoke(first);check(d.balance("video")==300000L);d.revoke(second);check(d.balance("video")==0L);check(d.rewardHistory().any{it.contains("奖励冲回 −5 分钟")})} finally {d.close()}
   }
   test("manual sleep penalties wait for future rewards and corrections are idempotent") {
    val other="sleep-rules.db";context.deleteDatabase(other);val d=Database(context,other){now}
    try {val date=d.day();val next=java.time.LocalDate.parse(date).plusDays(1).toString();d.addTask(date,"required",true,0,"行动型","",10,0,"once");d.addTask(next,"tomorrow one",true,0,"行动型","",10,0,"once");d.addTask(next,"tomorrow two",true,0,"行动型","",10,0,"once");d.confirm(date,false,0,0);d.complete(d.tasks().single());check(d.sleepTargets().take(2)==listOf("01:00","07:30"));d.recordSleep(date,"23:30","08:00");check(d.balance("video")==600000L&&d.pendingSleepPenalty()==900000L);d.recordSleep(date,"23:30","08:00");check(d.pendingSleepPenalty()==900000L);d.recordSleep(date,"01:30","08:00",reason="修正实际就寝时间");check(d.pendingSleepPenalty()==1800000L&&d.balance("video")==600000L);d.recordSleep(date,"01:30","08:00",excepted=true,reason="生病例外");check(d.balance("video")==600000L&&d.pendingSleepPenalty()==0L);check(d.sleepRecord(date)?.excepted==true)
     d.recordSleep(date,"23:30","08:00",reason="恢复睡眠记录");now=now.plusDays(1);d.confirm(next,false,0,0);d.tasks(next).forEach{d.complete(it)};check(d.balance("video")==300000L);check(d.pendingSleepPenalty()==0L);check(d.rewardSnapshot("video").earned==1200000L);check(d.rewardSnapshot("video").sleepDeducted==900000L);check(d.rewardHistory().any{it.contains("睡眠规则抵扣 −15 分钟")})
    } finally {d.close()}
   }
   test("historical sleep can be added and corrected without repeating penalties") {
    val other="historical-sleep.db";context.deleteDatabase(other);var clock=LocalDateTime.parse("2026-11-06T12:00:00");val d=Database(context,other){clock}
    try {
     val yesterday=java.time.LocalDate.parse(d.day()).minusDays(1).toString();val missing=java.time.LocalDate.parse(yesterday).minusDays(1).toString()
     d.recordSleep(yesterday,"02:00","08:30");val first=d.pendingSleepPenalty();check(first==30*60000L);d.recordSleep(yesterday,"02:00","08:30");check(d.pendingSleepPenalty()==first)
     d.recordSleep(yesterday,"00:30","07:00",reason="修正记录");check(d.pendingSleepPenalty()==0L);check(d.sleepRecord(yesterday)?.bedtime=="00:30")
     d.recordSleep(missing,"23:30","07:30");check(d.sleepRecord(missing)!=null&&d.pendingSleepPenalty()==0L)
     check(runCatching{d.recordSleep("2026-11-07","01:00","07:30")}.isFailure)
    } finally {d.close()}
   }
   test("achievement categories and award dates come from real task history") {
    val other="achievement-categories.db";context.deleteDatabase(other);var clock=LocalDateTime.parse("2026-11-06T12:00:00");val d=Database(context,other){clock}
    try {
     val date=d.day();d.addTask(date,"first real task",true,0,"行动型","",0,0,"once");d.confirm(date,false,0,0);d.complete(d.tasks().single())
     val items=achievementList(d);val first=items.first{it.title=="第一步"};check(first.category=="任务积累"&&first.earned&&first.earnedDate==date)
     check(items.map{it.category}.toSet()==setOf("任务积累","连续记录","专注积累"));check(items.filter{!it.earned}.all{it.progress<it.target})
    } finally {d.close()}
   }
   test("new achievement notice is shown once after a real unlock") {
    val other="achievement-notices.db";context.deleteDatabase(other);val d=Database(context,other){now}
    try {
     initializeAchievementNotices(d);check(nextUnseenAchievement(d)==null)
     val date=d.day();d.addTask(date,"first task",true,0,"行动型","",0,0,"once");d.confirm(date,false,0,0);d.complete(d.tasks().single())
     val unlocked=nextUnseenAchievement(d);check(unlocked!=null&&unlocked.id=="task.first"&&unlocked.earnedDate==date)
     d.setSetting("achievement_seen_${unlocked.id}","true");check(nextUnseenAchievement(d)==null)
     initializeAchievementNotices(d);check(nextUnseenAchievement(d)==null)
    } finally {d.close()}
   }
   test("completed legacy mandatory task receives one current-day task reward") {
    val other="legacy-reward.db";context.deleteDatabase(other);val d=Database(context,other){now}
     try {val date=d.day();d.addTask(date,"legacy done",true,0,"行动型","",7,0,"once");d.confirm(date,false,0,0);d.writableDatabase.execSQL("UPDATE tasks SET done=1 WHERE title='legacy done'");d.ensure();check(d.balance("video")==420000L);d.ensure();check(d.balance("video")==420000L)} finally {d.close()}
   }
return checks
 } finally {db.close()}
}
