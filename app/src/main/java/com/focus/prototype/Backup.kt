package com.focus.prototype
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import android.util.Base64

object Backup {
 private val tables=listOf("contracts","tasks","ledger","reviews","audit","settings","templates","attachments")
 fun export(context:Context,db:Database):String {
  db.checkpoint();val root=JSONObject().put("version",3);val data=JSONObject();val sql=db.writableDatabase
  sql.beginTransaction()
  try { for(table in tables) { val rows=JSONArray();sql.rawQuery("SELECT * FROM $table",null).use { c->while(c.moveToNext()){val row=JSONObject();c.columnNames.forEachIndexed { i,key->row.put(key,if(c.isNull(i)) JSONObject.NULL else c.getString(i)) };rows.put(row)} };data.put(table,rows) };sql.setTransactionSuccessful() } finally {sql.endTransaction()}
  root.put("data",data);root.put("selected",JSONArray(Store(context).selected.toList()))
  val files=JSONObject();sql.rawQuery("SELECT DISTINCT path FROM attachments",null).use{c->while(c.moveToNext()){val file=File(c.getString(0));files.put(file.name,Base64.encodeToString(file.readBytes(),Base64.NO_WRAP))}};root.put("files",files);return root.toString()
 }
 fun restore(context:Context,db:Database,text:String) {
  val root=JSONObject(text);require(root.getInt("version") in 1..3);val data=root.getJSONObject("data");tables.filter{it!="templates" && it!="attachments"}.forEach{require(data.has(it))}; val selected=root.getJSONArray("selected");val appSelection=(0 until selected.length()).map{selected.getString(it)}.toSet()
  val files=root.getJSONObject("files");val restored=mutableMapOf<String,String>()
  files.keys().forEach { name->require(name==File(name).name && name!=".." && name!="."); val dest=File(context.filesDir,"evidence/${java.util.UUID.randomUUID()}.${File(name).extension}");dest.parentFile?.mkdirs();dest.writeBytes(Base64.decode(files.getString(name),Base64.DEFAULT));restored[name]=dest.absolutePath }
  val sql=db.writableDatabase;sql.beginTransaction()
  try { tables.forEach { table->sql.delete(table,null,null);val rows=data.optJSONArray(table)?:JSONArray(); for(i in 0 until rows.length()){val row=rows.getJSONObject(i);val values=ContentValues();row.keys().forEach { key->if(row.isNull(key))values.putNull(key) else values.put(key,row.getString(key)) };if(table=="tasks"){val old=values.getAsString("evidence");if(!old.isNullOrBlank()) values.put("evidence",restored[File(old).name]?:"");values.put("running_since",0);values.put("boot_time",0)};if(table=="attachments"){val old=values.getAsString("path");values.put("path",restored[File(old).name]?:error("附件缺失：$old"))};sql.insertOrThrow(table,null,values)} };if(!data.has("templates"))db.seedTemplates(sql);if(!data.has("attachments"))db.seedAttachments(sql);sql.setTransactionSuccessful() } finally {sql.endTransaction()}
  Store(context).selected=appSelection
 }
}
