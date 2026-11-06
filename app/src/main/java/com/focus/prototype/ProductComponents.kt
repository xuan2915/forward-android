package com.focus.prototype

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

@Composable fun AppNavigation(page:Int,onPage:(Int)->Unit) {
 val tabs=listOf(0 to "今天",4 to "专注",1 to "奖励",2 to "成长")
 val haptics=LocalHapticFeedback.current
 Surface(modifier=Modifier.navigationBarsPadding().padding(horizontal=16.dp,vertical=8.dp),shape=RoundedCornerShape(25.dp),color=AppColors.Surface,border=BorderStroke(1.dp,AppColors.Border)) {
  Row(Modifier.fillMaxWidth().height(70.dp)) {
   tabs.forEachIndexed { index,(destination,title) ->
    val selected=page==destination
    val tint by animateColorAsState(if(selected)AppColors.Accent else AppColors.Quiet,label="navigation-tint")
    Column(Modifier.weight(1f).fillMaxHeight().clickable { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove);onPage(destination) }.padding(top=10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
     Canvas(Modifier.size(21.dp)) {
      val w=size.width;val h=size.height;val stroke=1.6.dp.toPx()
      when(index) {
       0 -> for(x in 0..1)for(y in 0..1)drawRoundRect(tint,Offset(x*w*.55f,y*h*.55f),Size(w*.38f,h*.38f),style=Stroke(stroke))
       1 -> {drawCircle(tint,w*.42f,style=Stroke(stroke));drawLine(tint,Offset(w*.5f,h*.12f),Offset(w*.5f,h*.5f),stroke);drawLine(tint,Offset(w*.5f,h*.5f),Offset(w*.7f,h*.65f),stroke)}
       2 -> {drawLine(tint,Offset(w*.5f,0f),Offset(w*.9f,h*.5f),stroke);drawLine(tint,Offset(w*.9f,h*.5f),Offset(w*.5f,h),stroke);drawLine(tint,Offset(w*.5f,h),Offset(w*.1f,h*.5f),stroke);drawLine(tint,Offset(w*.1f,h*.5f),Offset(w*.5f,0f),stroke)}
       3 -> {drawLine(tint,Offset(0f,h*.85f),Offset(w*.35f,h*.5f),stroke);drawLine(tint,Offset(w*.35f,h*.5f),Offset(w*.6f,h*.65f),stroke);drawLine(tint,Offset(w*.6f,h*.65f),Offset(w,h*.15f),stroke)}
      }
     }
     Text(title,fontSize=11.sp,color=tint,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal)
     Spacer(Modifier.size(3.dp))
    }
   }
  }
 }
}

// StartStats composition adapted from Grit habit/ui/component/stats/StartStats.kt.
@Composable fun GrowthHero(db:Database) {
 val motion=LocalGrowthMotion.current
 val xp=motion.xp.toInt().coerceAtLeast(0)
 val level=xp/500+1
 val levelProgress=(motion.xp%500)/500f
 val rank=listOf("启程","萌芽","探索","积累","进阶","坚韧","精进","卓越")[(level-1).coerceAtMost(7)]
 val started=db.readableDatabase.rawQuery("SELECT MIN(day) FROM tasks",null).use { if(it.moveToFirst()) it.getString(0) else null }
 val levelProgressAnimated by animateFloatAsState(levelProgress.coerceIn(0f,1f),tween(motionMillis(AppMotion.Growth),easing=AppMotion.Ease),label="level-progress")
 val today=LocalDate.parse(db.day())
 val recent=db.readableDatabase.rawQuery("SELECT COUNT(*),COALESCE(SUM(done),0) FROM tasks WHERE day>=? AND day<=?",arrayOf(today.minusDays(29).toString(),today.toString())).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) to cursor.getInt(1) }
 val consistency=if(recent.first==0) 0f else recent.second.toFloat()/recent.first
 val consistencyAnimated by animateFloatAsState(consistency.coerceIn(0f,1f),tween(motionMillis(AppMotion.Growth),easing=AppMotion.Ease),label="consistency-progress")
 Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
   Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
    Text("等级 $level · $rank",style=MaterialTheme.typography.headlineSmall.copy(fontWeight=FontWeight.ExtraBold))
    Text("累计成长",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
   }
   Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
    MotionCounter(xp,MaterialTheme.typography.headlineSmall.copy(fontWeight=FontWeight.Bold),AppMotion.Growth)
    Text("XP",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=4.dp))
   }
  }
  androidx.compose.material3.LinearProgressIndicator(
   progress={levelProgressAnimated}, modifier=Modifier.fillMaxWidth().height(7.dp),
   color=MaterialTheme.colorScheme.primary, trackColor=MaterialTheme.colorScheme.surfaceContainerHighest,
  )
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
   Text("${(levelProgressAnimated*100).toInt()}% 本级进度",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   Text("还差 ${ceil((500-levelProgressAnimated*500).coerceAtLeast(0f)).toInt()} XP 升级",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
  GrowthFeedback()
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
   ConsistencyRing(recent.first,recent.second,consistencyAnimated)
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
    GrowthStatRow("开始记录",started?.let { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("M月d日")) } ?: "今天",R.drawable.grit_flag)
    Hairline()
    GrowthStatRow("最长连续","${db.bestStreak()} 天",R.drawable.grit_heat)
    Hairline()
    GrowthStatRow("当前连续","${db.streak()} 天",R.drawable.grit_chart)
   }
  }
 }
}

@Composable private fun ConsistencyRing(total:Int,done:Int,progress:Float) {
 val trackColor=MaterialTheme.colorScheme.surfaceContainerHighest
 val progressColor=MaterialTheme.colorScheme.primary
 Box(Modifier.size(158.dp),contentAlignment=Alignment.Center) {
  Canvas(Modifier.fillMaxSize().padding(6.dp)) {
   val ring=Stroke(18.dp.toPx(),cap=StrokeCap.Round)
   drawArc(trackColor,-90f,360f,false,style=ring)
   if(total>0) drawArc(progressColor,-90f,360f*progress,false,style=ring)
  }
  Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(1.dp)) {
   Text(if(total==0) "—" else "${(progress*100).toInt()}%",style=MaterialTheme.typography.headlineMedium.copy(fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary))
   Text(if(total==0) "近30天" else "完成率",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
   if(total>0) Text("$done / $total 项",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
 }
}

@Composable private fun GrowthStatRow(label:String,value:String,icon:Int) {
 Row(Modifier.fillMaxWidth().heightIn(min=36.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
  Icon(androidx.compose.ui.res.painterResource(icon),null,tint=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.size(17.dp))
  Text(label,Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
  Text(value,style=MaterialTheme.typography.titleSmall.copy(fontWeight=FontWeight.SemiBold),color=MaterialTheme.colorScheme.onSurface,maxLines=1)
 }
}
