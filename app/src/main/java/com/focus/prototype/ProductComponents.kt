package com.focus.prototype

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
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

@Composable fun AppNavigation(page:Int,onPage:(Int)->Unit) {
 Surface(modifier=Modifier.navigationBarsPadding().padding(horizontal=16.dp,vertical=8.dp),shape=RoundedCornerShape(26.dp),color=Color(0xff111315),border=BorderStroke(1.dp,Color(0xff24272a))) {
  Row(Modifier.fillMaxWidth().height(68.dp)) {
   listOf("今天","奖励","成长","复盘").forEachIndexed { index,title ->
    TextButton(modifier=Modifier.weight(1f).fillMaxHeight(),onClick={onPage(index)},contentPadding=PaddingValues(0.dp)) {
     val color=if(index==page)Color.White else Color(0xff787c82)
     Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
      Canvas(Modifier.size(20.dp)) {
       val w=size.width;val h=size.height;val stroke=1.7.dp.toPx()
       when(index) {
        0 -> for(x in 0..1)for(y in 0..1)drawRoundRect(color,Offset(x*w*.55f,y*h*.55f),Size(w*.38f,h*.38f),style=Stroke(stroke))
        1 -> {drawCircle(color,w*.38f,style=Stroke(stroke));drawLine(color,Offset(w*.5f,h*.25f),Offset(w*.5f,h*.75f),stroke);drawLine(color,Offset(w*.25f,h*.5f),Offset(w*.75f,h*.5f),stroke)}
        2 -> {drawLine(color,Offset(0f,h*.85f),Offset(w*.35f,h*.5f),stroke);drawLine(color,Offset(w*.35f,h*.5f),Offset(w*.6f,h*.65f),stroke);drawLine(color,Offset(w*.6f,h*.65f),Offset(w,h*.15f),stroke)}
        3 -> {drawRoundRect(color,Offset(w*.1f,0f),Size(w*.8f,h),style=Stroke(stroke));for(y in 1..3)drawLine(color,Offset(w*.3f,h*y*.23f),Offset(w*.7f,h*y*.23f),stroke)}
       }
      }
      Text(title,fontSize=11.sp,color=color,fontWeight=if(index==page)FontWeight.Bold else FontWeight.Normal)
     }
    }
   }
  }
 }
}

@Composable fun GrowthHero(db:Database) {
 val xp=db.xp();val level=xp/500+1;val progress by animateFloatAsState((xp%500)/500f,label="level")
 val rank=listOf("启程","萌芽","探索","积累","进阶","坚韧","精进","卓越")[(xp/500).coerceAtMost(7)]
 Column(Modifier.fillMaxWidth().padding(vertical=16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Box(Modifier.size(208.dp),contentAlignment=Alignment.Center) {
   Canvas(Modifier.fillMaxSize().padding(9.dp)){val stroke=Stroke(7.dp.toPx(),cap=StrokeCap.Round);drawArc(Color(0xff282c30),-90f,360f,false,style=stroke);drawArc(Color.White,-90f,360f*progress,false,style=stroke)}
   Column(horizontalAlignment=Alignment.CenterHorizontally){Text("$xp",fontSize=48.sp,fontWeight=FontWeight.Bold);Text("成长经验 XP",fontSize=12.sp,color=Color.Gray)}
  }
  Text("等级 $level · $rank",fontSize=22.sp,fontWeight=FontWeight.Bold)
  Text("距下一级还需 ${500-xp%500} XP",color=Color.Gray,fontSize=13.sp)
  HorizontalDivider(Modifier.padding(vertical=8.dp),color=Color(0xff26292c))
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
   Column(horizontalAlignment=Alignment.CenterHorizontally){Text("${db.streak()}",fontSize=30.sp,fontWeight=FontWeight.Bold);Text("连续完成 / 天",color=Color.Gray,fontSize=12.sp)}
   Column(horizontalAlignment=Alignment.CenterHorizontally){Text("${db.bestStreak()}",fontSize=30.sp,fontWeight=FontWeight.Bold);Text("最长连续 / 天",color=Color.Gray,fontSize=12.sp)}
  }
 }
}
