package com.focus.prototype
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import android.util.Base64

object Backup {
 private val tables=listOf("contracts","tasks","ledger","reviews","audit","settings")
 fun export(context:Context,db:Database):String {
  val root=JSONObject().put("version",1);val data=JSONObject();val sql=db.writableDatabase
  sql.beginTransaction()
  try { for(table in tables) { val rows=JSONArray();sql.rawQuery("SELECT * FROM $table",null).use { c->while(c.moveToNext()){val row=JSONObject();c.columnNames.forEachIndexed { i,key->row.put(key,if(c.isNull(i)) JSONObject.NULL else c.getString(i)) };rows.put(row)} };data.put(table,rows) };sql.setTransactionSuccessful() } finally {sql.endTransaction()}
  root.put("data",data);root.put("selected",JSONArray(Store(context).selected.toList()))
  val files=JSONObject();File(context.filesDir,"evidence").listFiles()?.filter{it.isFile}?.forEach{files.put(it.name,Base64.encodeToString(it.readBytes(),Base64.NO_WRAP))};root.put("files",files);return root.toString()
 }
 fun restore(context:Context,db:Database,text:String) {
  val root=JSONObject(text);require(root.getInt("version")==1);val data=root.getJSONObject("data");tables.forEach{require(data.has(it))}
  val files=root.getJSONObject("files");val restored=mutableMapOf<String,String>()
  files.keys().forEach { name->require(name==File(name).name && name!=".." && name!="."); val dest=File(context.filesDir,"evidence/restore_${java.util.UUID.randomUUID()}_${name}");dest.parentFile?.mkdirs();dest.writeBytes(Base64.decode(files.getString(name),Base64.DEFAULT));restored[name]=dest.absolutePath }
  val sql=db.writableDatabase;sql.beginTransaction()
  try { tables.forEach { table->sql.delete(table,null,null);val rows=data.getJSONArray(table); for(i in 0 until rows.length()){val row=rows.getJSONObject(i);val values=ContentValues();row.keys().forEach { key->if(row.isNull(key))values.putNull(key) else values.put(key,row.getString(key)) };if(table=="tasks"){val old=values.getAsString("evidence");if(!old.isNullOrBlank()) values.put("evidence",restored[File(old).name]?:"");values.put("running_since",0);values.put("boot_time",0)};sql.insertOrThrow(table,null,values)} };sql.setTransactionSuccessful() } finally {sql.endTransaction()}
  val selected=root.getJSONArray("selected");Store(context).selected=(0 until selected.length()).map{selected.getString(it)}.toSet()
 }
}
