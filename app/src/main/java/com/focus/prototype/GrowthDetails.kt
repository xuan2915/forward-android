package com.focus.prototype
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){TextButton(onClick={month=month.minusMonths(1)}){Text("‹",color=AppColors.Muted)};Text("${month.year} 年 ${month.monthValue} 月",style=MaterialTheme.typography.titleLarge);TextButton(onClick={month=month.plusMonths(1)}){Text("›",color=AppColors.Muted)}}
 Row(Modifier.fillMaxWidth()){listOf("一","二","三","四","五","六","日").forEach{Box(Modifier.weight(1f),contentAlignment=Alignment.Center){Text(it,color=AppColors.Quiet,style=MaterialTheme.typography.labelSmall)}}}
 val offset=month.atDay(1).dayOfWeek.value-1;val rows=(offset+month.lengthOfMonth()+6)/7
 val counts=(1..month.lengthOfMonth()).map{history[month.atDay(it).toString()]}.groupingBy{it}.eachCount();val complete=counts["完成"]?:0;val rested=counts["休息"]?:0;val missed=counts["未完成"]?:0;val recorded=complete+rested+missed
 Row(Modifier.fillMaxWidth().padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){Box(Modifier.size(124.dp),contentAlignment=Alignment.Center){Canvas(Modifier.fillMaxSize().padding(5.dp)){val total=month.lengthOfMonth().toFloat();var start=-90f;for((count,color) in listOf(complete to AppColors.Accent,rested to AppColors.Muted,missed to Color(0xff67443e))){if(count>0){val sweep=360f*count/total;drawArc(color,start,sweep,false,style=Stroke(5.dp.toPx()));start+=sweep}};if(recorded==0)drawArc(AppColors.Raised,-90f,360f,false,style=Stroke(5.dp.toPx()))};Column(horizontalAlignment=Alignment.CenterHorizontally){Text("$complete",fontSize=30.sp,fontWeight=FontWeight.Light,color=AppColors.Text);Text("完成日",style=MaterialTheme.typography.labelSmall,color=AppColors.Muted)}};Spacer(Modifier.width(24.dp));Column(verticalArrangement=Arrangement.spacedBy(7.dp)){Text("${complete+rested+missed} / ${month.lengthOfMonth()} 天有记录",style=MaterialTheme.typography.titleSmall);Text("完成 $complete  ·  休息 $rested",style=MaterialTheme.typography.bodySmall,color=AppColors.Muted);Text("未完成 $missed",style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)}}
 repeat(rows) {week->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
  repeat(7){index->val number=week*7+index-offset+1
   if(number !in 1..month.lengthOfMonth())Spacer(Modifier.weight(1f).aspectRatio(1f))
   else {val date=month.atDay(number).toString();val state=history[date];val color=when(state){"完成"->AppColors.Accent;"休息"->AppColors.Raised;"未完成"->Color(0xff302320);else->AppColors.Surface}
    Box(Modifier.weight(1f).aspectRatio(1f).background(color,RoundedCornerShape(12.dp)).border(if(selected==date)1.dp else 0.dp,if(selected==date)AppColors.Text else Color.Transparent,RoundedCornerShape(12.dp)).clickable{selected=date},contentAlignment=Alignment.Center){Text(number.toString(),color=if(state=="完成")AppColors.AccentInk else AppColors.Muted,style=MaterialTheme.typography.bodySmall)}
   }
  }
 }}
 Text("浅青灰 完成 · 深灰 休息 · 暗色 未完成",color=AppColors.Quiet,style=MaterialTheme.typography.bodySmall)
 Text("$selected · ${history[selected]?:"未记录"}",style=MaterialTheme.typography.titleSmall)
 if(history.containsKey(selected)) {db.tasks(selected).forEach{Text("${if(it.done) "已完成" else "未完成"} · ${it.title}",fontSize=13.sp,color=Color.LightGray)};val review=db.readReview(selected);if(review.any{it.isNotBlank()})Text("今日总结：${review.filter{it.isNotBlank()}.joinToString("\n")}",fontSize=13.sp)}
 for(days in listOf(7,30)) {
  val from=today.minusDays((days-1).toLong()).toString()
  db.readableDatabase.rawQuery("SELECT COUNT(*),COALESCE(SUM(done),0),COALESCE(SUM(elapsed),0) FROM tasks WHERE day>=? AND day<=?",arrayOf(from,today.toString())).use{c->c.moveToFirst();val total=c.getInt(0);val done=c.getInt(1);Text("近 $days 天",style=MaterialTheme.typography.titleMedium);Text("${if(total==0)0 else done*100/total}% 完成率 · $done / $total 项 · 专注 ${c.getLong(2)/60000} 分钟",fontSize=13.sp,color=Color.LightGray)}
 }
 Text("例外与计划变更",style=MaterialTheme.typography.titleMedium)
 db.readableDatabase.rawQuery("SELECT day,detail,created FROM audit ORDER BY created DESC LIMIT 30",null).use{c->while(c.moveToNext())Text("${c.getString(0)} · ${c.getString(1)}",fontSize=12.sp,color=Color.LightGray)}
}
