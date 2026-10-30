package com.focus.prototype

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
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

@Composable fun GrowthHero(db:Database) {
 val xp=db.xp();val level=xp/500+1;val progress by animateFloatAsState((xp%500)/500f,label="level")
 val rank=listOf("启程","萌芽","探索","积累","进阶","坚韧","精进","卓越")[(xp/500).coerceAtMost(7)]
 Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
  Box(Modifier.size(108.dp),contentAlignment=Alignment.Center) {
   Canvas(Modifier.fillMaxSize().padding(7.dp)){val stroke=Stroke(4.dp.toPx(),cap=StrokeCap.Round);drawArc(AppColors.Raised,-90f,360f,false,style=stroke);drawArc(AppColors.Accent,-90f,360f*progress,false,style=stroke)}
   Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(1.dp)){Text("$xp",fontSize=29.sp,fontWeight=FontWeight.Light);Text("XP",fontSize=10.sp,color=AppColors.Muted)}
  }
  Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
   Text("等级 $level · $rank",style=MaterialTheme.typography.titleLarge)
   Text("距下一级 ${500-xp%500} XP",style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)
   LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth().height(3.dp),color=AppColors.Accent,trackColor=AppColors.Raised)
  }
 }
 Row(Modifier.fillMaxWidth().padding(top=4.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
  Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){Text("${db.streak()} 天",style=MaterialTheme.typography.titleLarge);Text("当前连续完成",style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)}
  Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){Text("${db.bestStreak()} 天",style=MaterialTheme.typography.titleLarge);Text("最长连续记录",style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)}
 }
}
