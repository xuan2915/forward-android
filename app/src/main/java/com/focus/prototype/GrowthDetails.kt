package com.focus.prototype
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable fun GrowthDetails(db:Database) {
 val today=LocalDate.parse(db.day());val history=db.history().toMap(); var selected by remember { mutableStateOf("") }
 Text("近四周 · 成长日历")
 repeat(4) { week -> Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) { repeat(7) { index -> val date=today.minusDays((27-week*7-index).toLong()).toString(); val state=history[date]; val color=when(state){"完成"->Color.White;"休息"->Color(0xff66686c);"未完成"->Color(0xff7e2927);else->Color(0xff1d2023)};Box(Modifier.size(36.dp).background(color).clickable{selected=date}.padding(7.dp)){Text(date.takeLast(2),color=if(state=="完成")Color.Black else Color.LightGray)} } } }
 if(selected.isNotEmpty()) {Text("$selected · ${history[selected]?:"未记录"}");if(history.containsKey(selected)) db.tasks(selected).forEach {Text("${if(it.done) "完成" else "未完成"} · ${it.title}")} }
 for(days in listOf(7,30)) { val from=today.minusDays((days-1).toLong()).toString();db.readableDatabase.rawQuery("SELECT COUNT(*),COALESCE(SUM(done),0),COALESCE(SUM(elapsed),0) FROM tasks WHERE day>=? AND day<=?",arrayOf(from,today.toString())).use{c->c.moveToFirst();val total=c.getInt(0);Text("近 $days 天：完成 ${c.getInt(1)} / $total 项 · 有效专注 ${c.getLong(2)/60000} 分钟")} }
 Text("例外与计划变更")
 db.readableDatabase.rawQuery("SELECT day,detail FROM audit ORDER BY created DESC LIMIT 20",null).use { c->while(c.moveToNext()) Text("${c.getString(0)} · ${c.getString(1)}") }
}
