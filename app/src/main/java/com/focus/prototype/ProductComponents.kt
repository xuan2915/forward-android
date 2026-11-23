/*
 * Copyright (C) 2026 Shubham Gorai
 * This file includes UI derived from Grit shared/ui's habit statistics.
 * The Self Discipline-specific data binding and layout adjustments are modifications.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.focus.prototype

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import java.time.LocalDate
import java.util.Locale
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

@Composable fun AppNavigation(page:Int,onPage:(Int)->Unit) {
 val tabs=listOf(0 to "今天",4 to "专注",1 to "奖励",2 to "成长")
 val selectedIndex = tabs.indexOfFirst { it.first == page }.coerceAtLeast(0)
 val haptics=LocalHapticFeedback.current

 val bounds = remember { mutableStateMapOf<Int, Rect>() }
 val targetRect = bounds[selectedIndex]
 val duration = motionMillis(AppMotion.Standard)
 val pillLeft by animateFloatAsState(
  targetValue = targetRect?.left ?: 0f,
  animationSpec = androidx.compose.animation.core.tween(duration, easing = AppMotion.Ease),
  label = "nav-pill-left",
 )
 val pillRight by animateFloatAsState(
  targetValue = targetRect?.right ?: 0f,
  animationSpec = androidx.compose.animation.core.tween(duration, easing = AppMotion.Ease),
  label = "nav-pill-right",
 )
 val pillTop = targetRect?.top ?: 0f
 val pillHeight = targetRect?.height ?: 0f

 Surface(modifier=Modifier.navigationBarsPadding().padding(horizontal=16.dp,vertical=8.dp),shape=RoundedCornerShape(25.dp),color=AppColors.Surface,border=BorderStroke(1.dp,AppColors.Border)) {
  Box(
   modifier = Modifier
    .fillMaxWidth()
    .height(68.dp)
    .drawBehind {
     if (pillRight > pillLeft && pillHeight > 0f) {
      val pillWidth = pillRight - pillLeft
      val padH = 6.dp.toPx()
      val padV = 5.dp.toPx()
      drawRoundRect(
       color = AppColors.Raised,
       topLeft = Offset(pillLeft + padH, pillTop + padV),
       size = Size((pillWidth - padH * 2).coerceAtLeast(0f), (pillHeight - padV * 2).coerceAtLeast(0f)),
       cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
      )
     }
    }
  ) {
   Row(Modifier.fillMaxSize()) {
    tabs.forEachIndexed { index,(destination,title) ->
     val isSelected=page==destination
     val tint by animateColorAsState(if(isSelected)AppColors.Accent else AppColors.Quiet,androidx.compose.animation.core.tween(duration, easing = AppMotion.Ease),label="navigation-tint")
     Column(
      Modifier
       .weight(1f)
       .fillMaxHeight()
       .onGloballyPositioned { coordinates ->
        val at = coordinates.positionInParent()
        val rect = Rect(at, Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
        if (bounds[index] != rect) bounds[index] = rect
       }
       .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
       ) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onPage(destination)
       }
       .semantics {
        this.selected = isSelected
        contentDescription = title
       }
       .padding(top=10.dp),
      horizontalAlignment=Alignment.CenterHorizontally,
      verticalArrangement=Arrangement.spacedBy(4.dp)
     ) {
      Canvas(Modifier.size(21.dp)) {
       val w=size.width;val h=size.height;val stroke=1.6.dp.toPx()
       when(index) {
        0 -> for(x in 0..1)for(y in 0..1)drawRoundRect(tint,Offset(x*w*.55f,y*h*.55f),Size(w*.38f,h*.38f),style=Stroke(stroke))
        1 -> {drawCircle(tint,w*.42f,style=Stroke(stroke));drawLine(tint,Offset(w*.5f,h*.12f),Offset(w*.5f,h*.5f),stroke);drawLine(tint,Offset(w*.5f,h*.5f),Offset(w*.7f,h*.65f),stroke)}
        2 -> {drawLine(tint,Offset(w*.5f,0f),Offset(w*.9f,h*.5f),stroke);drawLine(tint,Offset(w*.9f,h*.5f),Offset(w*.5f,h),stroke);drawLine(tint,Offset(w*.5f,h),Offset(w*.1f,h*.5f),stroke);drawLine(tint,Offset(w*.1f,h*.5f),Offset(w*.5f,0f),stroke)}
        3 -> {drawLine(tint,Offset(0f,h*.85f),Offset(w*.35f,h*.5f),stroke);drawLine(tint,Offset(w*.35f,h*.5f),Offset(w*.6f,h*.65f),stroke);drawLine(tint,Offset(w*.6f,h*.65f),Offset(w,h*.15f),stroke)}
       }
      }
      Text(title,fontSize=11.sp,color=tint,fontWeight=if(isSelected)FontWeight.SemiBold else FontWeight.Normal)
      Spacer(Modifier.size(3.dp))
     }
    }
   }
  }
 }
}

// StartStats composition adapted from Grit habit/ui/component/stats/StartStats.kt.
private data class GrowthSnapshot(val planDays: Int, val achievedDays: Int, val weekPlanDays: Int, val weekAchievedDays: Int, val focusMillis: Long, val achievements: Int)

@Composable fun GrowthHero(db:Database, refreshKey:Int=0, onAllAchievements:()->Unit={}) {
 val motion=LocalGrowthMotion.current
 val xp=motion.xp.toInt().coerceAtLeast(0)
 val level=xp/500+1
 val levelProgress=(motion.xp%500)/500f
 val rank=listOf("启程","萌芽","探索","积累","进阶","坚韧","精进","卓越")[(level-1).coerceAtMost(7)]
 val today=LocalDate.parse(db.day())
 val snapshot=remember(db,refreshKey,today) {
  val stats=db.growthPeriodStats(today.toString())
  GrowthSnapshot(stats.planDays,stats.achievedDays,stats.weekPlanDays,stats.weekAchievedDays,stats.focusMillis,achievementList(db).count{it.earned})
 }
 val levelProgressAnimated by animateFloatAsState(levelProgress.coerceIn(0f,1f),tween(motionMillis(AppMotion.Growth),easing=AppMotion.Ease),label="level-progress")
 Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
   Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
    Text("等级 $level · $rank",style=MaterialTheme.typography.headlineSmall.copy(fontWeight=FontWeight.ExtraBold))
   }
   Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
    MotionCounter(xp,MaterialTheme.typography.headlineSmall.copy(fontWeight=FontWeight.Bold),AppMotion.Growth)
    Text("XP",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=4.dp))
   }
  }
  Box(Modifier.fillMaxWidth().height(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
   if(levelProgressAnimated>0f)Box(Modifier.fillMaxWidth(levelProgressAnimated).fillMaxHeight().clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.primary))
  }
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
   Text("${(levelProgressAnimated*100).toInt()}% 本级进度",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   Text("还差 ${ceil((500-levelProgressAnimated*500).coerceAtLeast(0f)).toInt()} XP 升级",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
  GrowthFeedback()
  GritGrowthStats(snapshot,onAllAchievements)
 }
}

@Composable private fun GritGrowthStats(data:GrowthSnapshot,onAllAchievements:()->Unit) {
 val target=if(data.planDays==0) 0f else (data.achievedDays.toFloat()/data.planDays).coerceIn(0f,1f)
 val progress by animateFloatAsState(target,tween(motionMillis(AppMotion.Growth),easing=AppMotion.Ease),label="grit-overall-consistency")
 Column(Modifier.fillMaxWidth().widthIn(max=420.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)) {
  GrowthConsistencyRing(data,progress)
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   GrowthMetric("本周必做",if(data.weekPlanDays==0)"—" else "${data.weekAchievedDays}/${data.weekPlanDays}",Modifier.weight(1f))
   val focusMinutes=data.focusMillis/60000
   GrowthMetric("累计专注",when{focusMinutes<60->"${focusMinutes} 分";else->"${focusMinutes/60}时 ${focusMinutes%60}分"},Modifier.weight(1f))
   GrowthMetric("已解锁成就",data.achievements.toString(),Modifier.weight(1f),onAllAchievements)
  }
 }
}

@Composable private fun GrowthMetric(label:String,value:String,modifier:Modifier,onClick:(()->Unit)?=null) {
 Column(modifier.then(if(onClick!=null)Modifier.clickable(onClick=onClick) else Modifier).background(MaterialTheme.colorScheme.surfaceContainer,MaterialTheme.shapes.medium).padding(horizontal=10.dp,vertical=11.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
   Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
   if(onClick!=null) Text("›", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.primary)
  }
  Text(value,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=1,softWrap=false)
 }
}

@Composable private fun GrowthConsistencyRing(data:GrowthSnapshot,progress:Float) {
 val ringSize=200.dp
 val ringStroke=36.dp
 val trackColor=MaterialTheme.colorScheme.surfaceContainerHighest
 val progressColor=MaterialTheme.colorScheme.primary
 Box(Modifier.size(ringSize),contentAlignment=Alignment.Center) {
  Canvas(Modifier.fillMaxSize()) {
   val arcStroke=ringStroke.toPx()
   val arcSize=Size(size.width-arcStroke,size.height-arcStroke)
   val arcTopLeft=Offset(arcStroke/2,arcStroke/2)
   drawArc(trackColor,-90f,360f,false,topLeft=arcTopLeft,size=arcSize,style=Stroke(arcStroke,cap=StrokeCap.Butt))
   if(progress>=0.999f) {
    drawArc(progressColor,-90f,360f,false,topLeft=arcTopLeft,size=arcSize,style=Stroke(arcStroke,cap=StrokeCap.Butt))
   } else if(progress>0f) {
    drawArc(progressColor,-90f,progress*360f,false,topLeft=arcTopLeft,size=arcSize,style=Stroke(arcStroke,cap=StrokeCap.Round))
   }
  }
  Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(1.dp)) {
   Text(if(data.planDays==0) "—" else "${(progress*100).toInt()}%",style=MaterialTheme.typography.headlineSmall.copy(fontFamily=gritFontRounded(),color=MaterialTheme.colorScheme.primary))
   Text(if(data.planDays==0) "暂无计划日" else "近30天达标率",style=MaterialTheme.typography.titleSmall.copy(fontFamily=gritFontRounded()),maxLines=1)
   if(data.planDays>0) Text("${data.achievedDays} / ${data.planDays} 个计划日",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
  }
 }
}
