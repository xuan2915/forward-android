package com.focus.prototype

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun ProgressView(done:Int,total:Int) {
 val progress by animateFloatAsState(if(total>0) done.toFloat()/total else 0f,label="今日进度")
 Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
  Column(verticalArrangement=Arrangement.spacedBy(2.dp)) { Text("今日进度",style=MaterialTheme.typography.labelMedium,color=AppColors.Muted); Text("$done / $total",style=MaterialTheme.typography.displaySmall,color=AppColors.Text); Text("必做任务已完成",style=MaterialTheme.typography.bodySmall,color=AppColors.Quiet) }
  Box(Modifier.size(78.dp),contentAlignment=Alignment.Center) {
   Canvas(Modifier.fillMaxSize().padding(5.dp)) { drawArc(AppColors.Raised,-90f,360f,false,style=Stroke(4.dp.toPx())); drawArc(AppColors.Accent,-90f,360f*progress,false,style=Stroke(4.dp.toPx())) }
   Text("${(progress*100).toInt()}%",style=MaterialTheme.typography.labelLarge,color=AppColors.Text)
  }
 }
}
