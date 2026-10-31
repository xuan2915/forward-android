package com.focus.prototype

import androidx.compose.animation.core.animateFloatAsState
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

@Composable fun AppNavigation(page:Int,onPage:(Int)->Unit) {
 val tabs=listOf(0 to "今天",4 to "专注",1 to "奖励",2 to "成长",3 to "复盘")
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
       4 -> {drawRoundRect(tint,Offset(w*.12f,0f),Size(w*.76f,h),style=Stroke(stroke));for(y in 1..3)drawLine(tint,Offset(w*.3f,h*y*.23f),Offset(w*.7f,h*y*.23f),stroke)}
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
 Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Text("成长记录",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.SpaceBetween) {
   Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
    Text("等级 $level · $rank",style=MaterialTheme.typography.headlineMedium.copy(fontWeight=FontWeight.ExtraBold))
    Text("${500-levelProgress.times(500).toInt()} XP 升级",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
   }
   Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
    MotionCounter(db.xp(),MaterialTheme.typography.titleLarge.copy(fontWeight=FontWeight.Bold),AppMotion.Growth)
    Text("XP",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=4.dp))
   }
  }
  GrowthFeedback()
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
   Box(Modifier.size(190.dp),contentAlignment=Alignment.Center) {
    CircularProgressIndicator(progress={levelProgress},strokeCap=StrokeCap.Round,strokeWidth=36.dp,trackColor=MaterialTheme.colorScheme.surfaceContainerHighest,modifier=Modifier.fillMaxSize())
    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(1.dp)) {
     Text("${(levelProgress*100).toInt()}%",style=MaterialTheme.typography.headlineSmall.copy(fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary))
     Text("升级进度",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
   }
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
    GritGrowthStat("开始记录",started?.let { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("M月d日")) } ?: "今天",R.drawable.grit_flag)
    GritGrowthStat("最长连续", "${db.bestStreak()} 天",R.drawable.grit_heat)
    GritGrowthStat("当前连续", "${db.streak()} 天",R.drawable.grit_chart)
   }
  }
 }
}

@Composable private fun GritGrowthStat(label:String,value:String,icon:Int) {
 Row(Modifier.fillMaxWidth().heightIn(min=54.dp).background(MaterialTheme.colorScheme.primaryContainer,RoundedCornerShape(20.dp)).padding(7.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
  Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.primary,modifier=Modifier.width(32.dp).height(40.dp)) {
   Icon(androidx.compose.ui.res.painterResource(icon),null,tint=MaterialTheme.colorScheme.onPrimary,modifier=Modifier.padding(8.dp))
  }
  Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(1.dp)) {
   Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onPrimaryContainer,maxLines=1)
   Text(value,style=MaterialTheme.typography.titleSmall.copy(fontWeight=FontWeight.SemiBold),color=MaterialTheme.colorScheme.onPrimaryContainer,maxLines=1)
  }
 }
}
