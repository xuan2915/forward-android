package com.focus.prototype
import java.io.File
/** Mutates only the disposable emulator used by tools/service-check.py. */
class ServiceFixtureSetup(private val context:android.content.Context) {
 fun run(stage:String):String {
  check(android.os.Build.HARDWARE in listOf("ranchu","goldfish"))
  val db=Database(context);try{val file=File(context.cacheDir,"service-check-backup.json")
   when(stage){
    "motion-prepare"->{file.writeText(Backup.export(context,db));val sql=db.writableDatabase;sql.beginTransaction();try{listOf("attachments","templates","tasks","ledger","contracts","reviews","audit","settings").forEach{sql.delete(it,null,null)};sql.setTransactionSuccessful()}finally{sql.endTransaction()};val day=db.day();val yesterday=java.time.LocalDate.parse(day).minusDays(1).toString();repeat(4){db.addTask(yesterday,"模拟器验收 · 已完成 ${it+1}",it==0,0,"行动型","",0,0,"once");db.complete(db.tasks(yesterday).last())};db.confirm(yesterday,false,0,0);db.addTask(day,"模拟器验收 · 整理书桌",true,0,"行动型","桌面整理完成",0,0,"once");db.addTask(day,"模拟器验收 · 阅读",false,0,"行动型","",15,0,"once");db.confirm(day,false,30,0);check(db.xp()==400)}
    "prepare"->{file.writeText(Backup.export(context,db));val sql=db.writableDatabase;sql.beginTransaction();try{listOf("attachments","templates","tasks","ledger","contracts","reviews","audit","settings").forEach{sql.delete(it,null,null)};sql.setTransactionSuccessful()}finally{sql.endTransaction()};val today=db.day();db.addTask(today,"emulator meter check",true,0,"行动型","",0,0,"once");db.confirm(today,false,30,0);db.complete(db.tasks(today).single());check(db.eligible());Store(context).selected=Store.videos;context.getSharedPreferences("focus",android.content.Context.MODE_PRIVATE).edit().putStringSet("selected",Store.videos).commit();db.setSetting("learning_until","0");db.resumeProtection();db.consume("video",db.balance("video")-20000);check(db.balance("video")==20000L)}
    "restore"->{Backup.restore(context,db,file.readText());context.getSharedPreferences("focus",android.content.Context.MODE_PRIVATE).edit().putStringSet("selected",Store(context).selected).commit();file.delete()}
    "status"->{return "balance=${db.balance("video")}; eligible=${db.eligible()}; selected=${Store(context).selected.sorted().joinToString()}; service=${FocusService.connected}"}
    else->error("Unknown stage")
   }
  }finally{db.close()}
  return "PASS $stage"
 }
}
