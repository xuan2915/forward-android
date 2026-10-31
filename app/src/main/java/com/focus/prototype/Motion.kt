// Adapted from compose-animations by 2026 skydoves (Jaewoong Eum).
// Apache-2.0; see LICENSES/Compose-Animations.txt. Modified for this app's controls.
package com.focus.prototype
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalContext
import android.provider.Settings
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper

val LocalReducedMotion = staticCompositionLocalOf { false }
val LocalGrowthMotion = staticCompositionLocalOf { GrowthMotion(0f, "") }
data class GrowthMotion(val xp:Float, val feedback:String)
@Composable fun motionMillis(duration:Int = AppMotion.Standard) = if(LocalReducedMotion.current) 0 else duration
@Composable fun MotionHost(xp:Int, showToast:Boolean = true, content:@Composable ()->Unit) {
 val context=LocalContext.current
 var reduced by remember { mutableStateOf(!android.animation.ValueAnimator.areAnimatorsEnabled()) }
 DisposableEffect(context) {
  val observer=object:ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange:Boolean) { reduced=!android.animation.ValueAnimator.areAnimatorsEnabled() } }
  context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
  onDispose { context.contentResolver.unregisterContentObserver(observer) }
 }
 val value=remember { Animatable(xp.toFloat()) }; var previous by remember { mutableIntStateOf(xp) }; var feedback by remember { mutableStateOf("") }
 LaunchedEffect(xp,reduced) {
  val old=previous;previous=xp
  if(xp!=old) feedback=if(xp>old) { if(xp/500>old/500) "已升级 · 等级 ${xp/500+1}" else "+${xp-old} XP · 已记录" } else "完成状态已更新"
  if(reduced) value.snapTo(xp.toFloat()) else value.animateTo(xp.toFloat(),tween(AppMotion.Growth,easing=AppMotion.Ease))
  if(feedback.isNotEmpty()) { kotlinx.coroutines.delay(2200);feedback="" }
 }
 CompositionLocalProvider(LocalReducedMotion provides reduced,LocalGrowthMotion provides GrowthMotion(value.value,feedback)) {
  Box(Modifier.fillMaxSize()) {
   content()
   if(showToast) AnimatedVisibility(feedback.isNotEmpty(),modifier=Modifier.align(Alignment.BottomCenter).padding(start=22.dp,end=22.dp,bottom=116.dp),enter=fadeIn(tween(motionMillis()))+slideInVertically(tween(motionMillis())){it/6},exit=fadeOut(tween(motionMillis()))) {
    Surface(shape=detachedItemShape(),color=MaterialTheme.colorScheme.surfaceContainerHighest,shadowElevation=2.dp) {
     Row(Modifier.padding(horizontal=16.dp,vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
      CircularProgressIndicator(progress={(value.value%500)/500f},modifier=Modifier.size(32.dp),strokeWidth=3.dp,trackColor=MaterialTheme.colorScheme.outlineVariant)
      Column { Text("${value.value.toInt()} XP",style=MaterialTheme.typography.titleMedium);Text(feedback,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
     }
    }
   }
  }
 }
}
@Composable fun GrowthFeedback() {
 val state=LocalGrowthMotion.current
 AnimatedVisibility(state.feedback.isNotEmpty(),enter=expandVertically(tween(motionMillis()))+fadeIn(tween(motionMillis())),exit=shrinkVertically(tween(motionMillis()))+fadeOut(tween(motionMillis()))) {
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
   Text(state.feedback,color=AppColors.Accent,style=MaterialTheme.typography.bodySmall)
   Text("${state.xp.toInt()} XP",style=MaterialTheme.typography.bodySmall)
  }
 }
}
// Counter transition adapted from AnimationExample5; no sample/demo state is retained.
@Composable fun MotionCounter(value:Int,style:TextStyle) {
 val duration=motionMillis()
 AnimatedContent(value,transitionSpec={
  val direction=if(targetState>initialState) 1 else -1
  (slideInVertically(tween(duration, easing=AppMotion.Ease)){it/4*direction}+fadeIn(tween(duration))) togetherWith
  (slideOutVertically(tween(duration,easing=AppMotion.Ease)){-it/4*direction}+fadeOut(tween(duration)))
 },label="counter") { Text("$it",style=style) }
}
@Composable fun CompletionMark(done:Boolean) {
 val amount by animateFloatAsState(if(done) 1f else 0f,tween(motionMillis()),label="task-check")
 Canvas(Modifier.size(22.dp)) {
  drawCircle(AppColors.Border,style=androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
  if(amount>0f) {
   drawCircle(AppColors.Accent.copy(alpha=amount))
   val path=Path().apply {moveTo(size.width*.24f,size.height*.5f);lineTo(size.width*.43f,size.height*.69f);lineTo(size.width*.77f,size.height*.32f)}
   val measured=androidx.compose.ui.graphics.PathMeasure().apply {setPath(path,false)}
   val segment=Path();measured.getSegment(0f,measured.length*amount,segment,true)
   drawPath(segment,AppColors.AccentInk,style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(),cap=StrokeCap.Round))
  }
 }
}
@Composable
fun PlayPauseMorph(progress: Float, iconBoxDp: Int = 48) {
  Canvas(modifier = Modifier.size((iconBoxDp / 2).dp)) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val triHalf = w * 0.32f
    val barHalfW = w * 0.12f
    val barGap = w * 0.18f

    // Triangle (PLAY) corners.
    val triTopX = cx - triHalf * 0.65f
    val triTopY = cy - triHalf
    val triBotX = cx - triHalf * 0.65f
    val triBotY = cy + triHalf
    val triRightX = cx + triHalf
    val triRightY = cy

    // Pause bars target corners (left bar + right bar collapsed into one quad each).
    val leftBarL = cx - barGap - barHalfW
    val leftBarR = cx - barGap + barHalfW
    val rightBarL = cx + barGap - barHalfW
    val rightBarR = cx + barGap + barHalfW
    val barTop = cy - triHalf
    val barBot = cy + triHalf

    // Left shape morphs triangle's top+bottom+right into the LEFT pause bar.
    val leftPath = Path().apply {
      moveTo(lerp(triTopX, leftBarL, progress), lerp(triTopY, barTop, progress))
      lineTo(lerp(triRightX, leftBarR, progress), lerp(triRightY, barTop, progress))
      lineTo(lerp(triRightX, leftBarR, progress), lerp(triRightY, barBot, progress))
      lineTo(lerp(triBotX, leftBarL, progress), lerp(triBotY, barBot, progress))
      close()
    }
    // Right shape only appears as we cross 50% (it grows from the right edge of triangle).
    val rightPath = Path().apply {
      moveTo(lerp(triRightX, rightBarL, progress), lerp(triRightY, barTop, progress))
      lineTo(lerp(triRightX, rightBarR, progress), lerp(triRightY, barTop, progress))
      lineTo(lerp(triRightX, rightBarR, progress), lerp(triRightY, barBot, progress))
      lineTo(lerp(triRightX, rightBarL, progress), lerp(triRightY, barBot, progress))
      close()
    }
    drawPath(path = leftPath, color = AppColors.AccentInk)
    drawPath(path = rightPath, color = AppColors.AccentInk)
    // Anchor a tiny offset so Canvas always remeasures cleanly on reload.
    drawCircle(color = Color.Transparent, radius = 0f, center = Offset(cx, cy))
  }
}

private fun lerp(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction

// Content-size spring adapted from compose-lab TypewriterDemo BubbleGrow.
// Copyright (c) 2026 Dimitris Vlachos, MIT; see LICENSES/Compose-Lab.txt.
@Composable fun motionSizeSpec():FiniteAnimationSpec<IntSize> = if(LocalReducedMotion.current) tween(0) else spring(dampingRatio=1f,stiffness=Spring.StiffnessMediumLow,visibilityThreshold=IntSize.VisibilityThreshold)
