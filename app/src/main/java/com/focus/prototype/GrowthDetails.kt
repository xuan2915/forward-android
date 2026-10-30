package com.focus.prototype
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

@Composable fun GrowthDetails(db:Database) {
 val today=LocalDate.parse(db.day());val history=db.history().toMap();var selected by remember {mutableStateOf(today.toString())};var month by remember {mutableStateOf(YearMonth.from(today))}
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){TextButton(onClick={month=month.minusMonths(1)}){Text("‹")};Text("${month.year} 年 ${month.monthValue} 月",style=MaterialTheme.typography.titleMedium);TextButton(onClick={month=month.plusMonths(1)}){Text("›")}}
 Row(Modifier.fillMaxWidth()){listOf("一","二","三","四","五","六","日").forEach{Box(Modifier.weight(1f),contentAlignment=Alignment.Center){Text(it,color=Color.Gray,fontSize=12.sp)}}}
 val offset=month.atDay(1).dayOfWeek.value-1;val rows=(offset+month.lengthOfMonth()+6)/7
 val counts=(1..month.lengthOfMonth()).map{history[month.atDay(it).toString()]}.groupingBy{it}.eachCount();val complete=counts["完成"]?:0;val rested=counts["休息"]?:0;val missed=counts["未完成"]?:0;val recorded=complete+rested+missed
 Row(Modifier.fillMaxWidth().padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){Box(Modifier.size(124.dp),contentAlignment=Alignment.Center){Canvas(Modifier.fillMaxSize().padding(5.dp)){val total=month.lengthOfMonth().toFloat();var start=-90f;for((count,color) in listOf(complete to Color.White,rested to Color(0xff777d83),missed to Color(0xff9a403c))){if(count>0){val sweep=360f*count/total;drawArc(color,start,sweep,false,style=Stroke(8.dp.toPx()));start+=sweep}};if(recorded==0)drawArc(Color(0xff303438),-90f,360f,false,style=Stroke(8.dp.toPx()))};Column(horizontalAlignment=Alignment.CenterHorizontally){Text("$complete",fontSize=30.sp,fontWeight=FontWeight.Bold);Text("完成日",fontSize=11.sp,color=Color.Gray)}};Spacer(Modifier.width(24.dp));Column(verticalArrangement=Arrangement.spacedBy(7.dp)){Text("${complete+rested+missed} / ${month.lengthOfMonth()} 天有记录",fontSize=14.sp);Text("完成 $complete  ·  休息 $rested",fontSize=12.sp,color=Color.LightGray);Text("未完成 $missed",fontSize=12.sp,color=Color.LightGray)}}
 repeat(rows) {week->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
  repeat(7){index->val number=week*7+index-offset+1
   if(number !in 1..month.lengthOfMonth())Spacer(Modifier.weight(1f).aspectRatio(1f))
   else {val date=month.atDay(number).toString();val state=history[date];val color=when(state){"完成"->Color(0xffeceeee);"休息"->Color(0xff585e64);"未完成"->Color(0xff592a29);else->Color(0xff191c1f)}
    Box(Modifier.weight(1f).aspectRatio(1f).background(color,RoundedCornerShape(8.dp)).clickable{selected=date},contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(number.toString(),color=if(state=="完成")Color.Black else Color.LightGray);if(selected==date)Text("•",fontSize=8.sp,color=if(state=="完成")Color.Black else Color.White)}}
   }
  }
 }}
 Text("白色 完成 · 灰色 休息 · 暗红 未完成",color=Color.Gray,fontSize=11.sp)
 Text("$selected · ${history[selected]?:"未记录"}",style=MaterialTheme.typography.titleSmall)
 if(history.containsKey(selected)) {db.tasks(selected).forEach{Text("${if(it.done) "已完成" else "未完成"} · ${it.title}",fontSize=13.sp,color=Color.LightGray)};val review=db.readReview(selected);if(review.any{it.isNotBlank()})Text("今日总结：${review.filter{it.isNotBlank()}.joinToString("\n")}",fontSize=13.sp)}
 for(days in listOf(7,30)) {
  val from=today.minusDays((days-1).toLong()).toString()
  db.readableDatabase.rawQuery("SELECT COUNT(*),COALESCE(SUM(done),0),COALESCE(SUM(elapsed),0) FROM tasks WHERE day>=? AND day<=?",arrayOf(from,today.toString())).use{c->c.moveToFirst();val total=c.getInt(0);val done=c.getInt(1);Text("近 $days 天",style=MaterialTheme.typography.titleMedium);Text("${if(total==0)0 else done*100/total}% 完成率 · $done / $total 项 · 专注 ${c.getLong(2)/60000} 分钟",fontSize=13.sp,color=Color.LightGray)}
 }
 Text("例外与计划变更",style=MaterialTheme.typography.titleMedium)
 db.readableDatabase.rawQuery("SELECT day,detail,created FROM audit ORDER BY created DESC LIMIT 30",null).use{c->while(c.moveToNext())Text("${c.getString(0)} · ${c.getString(1)}",fontSize=12.sp,color=Color.LightGray)}
}
