package com.focus.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Detail presentation chosen in the debug comparison toggle: "sheet" = plan A, "inline" = plan B. */
internal const val DetailModeKey = "ui_task_detail_mode"
internal fun Database.inlineTaskDetail(): Boolean = setting(DetailModeKey, "sheet") == "inline"

/**
 * Plan A: a follow-the-finger bottom sheet.
 *
 * One value, offsetPx, is the single source of truth for the sheet's vertical
 * position. Entering, dragging, flinging, springing back and closing all write
 * to it; the scrim and the handle derive from it. Any motion can therefore be
 * caught mid-flight by the finger and continued from where it is.
 */
@Composable
fun TaskDetailSheet(
    task: Task,
    db: Database,
    timer: FocusTimer,
    startEditing: Boolean = false,
    onDismiss: () -> Unit,
    onFocus: () -> Unit,
    onAnalytics: () -> Unit,
    onChanged: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    var sheetHeight by remember(task.id) { mutableFloatStateOf(0f) }
    var offsetPx by remember(task.id) { mutableFloatStateOf(Float.NaN) }
    var closing by remember(task.id) { mutableStateOf(false) }
    var dragging by remember(task.id) { mutableStateOf(false) }
    var motion by remember(task.id) { mutableStateOf<Job?>(null) }
    val velocityThreshold = with(density) { 1100.dp.toPx() }
    val maxThreshold = with(density) { 200.dp.toPx() }
    val closeThreshold = if (sheetHeight > 0f) minOf(sheetHeight * .3f, maxThreshold) else maxThreshold

    fun animateTo(target: Float, velocity: Float, settle: Boolean, after: () -> Unit = {}) {
        motion?.cancel()
        motion = scope.launch {
            val start = if (offsetPx.isNaN()) sheetHeight else offsetPx
            if (reduced) offsetPx = target else animate(
                start, target, velocity,
                // Settling keeps a hint of overshoot so a released sheet lands
                // with weight; closing is critically damped so it never bounces back.
                if (settle) spring(dampingRatio = .8f, stiffness = 380f)
                else spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
            ) { value, _ -> offsetPx = value }
            after()
        }
    }
    fun close(velocity: Float = 0f, after: () -> Unit = onDismiss) {
        if (closing) return
        closing = true
        animateTo(sheetHeight.coerceAtLeast(1f), velocity.coerceAtLeast(0f), settle = false, after = after)
    }
    fun settle(velocity: Float) {
        dragging = false
        if (closing || offsetPx.isNaN()) return
        val shouldClose = velocity > velocityThreshold || (offsetPx > closeThreshold && velocity > -velocityThreshold / 2)
        if (shouldClose) close(velocity) else animateTo(0f, velocity, settle = true)
    }
    fun dragBy(delta: Float): Float {
        if (closing || offsetPx.isNaN()) return 0f
        if (!dragging) { motion?.cancel(); dragging = true }
        val previous = offsetPx
        // Above the resting position the sheet resists (rubber band) instead of stopping dead.
        val next = offsetPx + if (offsetPx <= 0f && delta < 0f) delta * .18f else delta
        offsetPx = next.coerceIn(-with(density) { 24.dp.toPx() }, sheetHeight)
        return offsetPx - previous
    }

    // Enter from exactly the measured sheet height, so the first frame already
    // sits on the same track that dragging and closing use.
    LaunchedEffect(task.id, sheetHeight > 0f) {
        if (sheetHeight > 0f && offsetPx.isNaN()) {
            offsetPx = sheetHeight
            animateTo(0f, 0f, settle = true)
        }
    }
    BackHandler(enabled = true) { close() }

    val connection = remember(task.id) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                // Finger moving up while the sheet is lowered: raise the sheet before scrolling content.
                if (available.y < 0f && offsetPx > 0f && source == NestedScrollSource.UserInput) Offset(0f, dragBy(available.y)) else Offset.Zero
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                // Content already at its top and the finger keeps pulling: the sheet follows the finger.
                if (available.y > 0f && source == NestedScrollSource.UserInput) Offset(0f, dragBy(available.y)) else Offset.Zero
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragging || offsetPx > .5f || offsetPx < -.5f) { settle(available.y); return available }
                return Velocity.Zero
            }
        }
    }

    val height = sheetHeight.coerceAtLeast(1f)
    val progress = if (offsetPx.isNaN()) 0f else (1f - offsetPx / height).coerceIn(0f, 1f)
    val pull = if (offsetPx.isNaN()) 0f else (offsetPx / closeThreshold).coerceIn(0f, 1f)
    val armed = dragging && pull >= 1f
    LaunchedEffect(armed) { if (armed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fallbackOffset = with(density) { maxHeight.toPx() }
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = progress }
                .background(Color.Black.copy(alpha = 0.56f))
                .clickable(remember { MutableInteractionSource() }, indication = null) { close() },
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .heightIn(max = maxHeight * .91f)
                .onSizeChanged { sheetHeight = it.height.toFloat() }
                .graphicsLayer {
                    translationY = if (offsetPx.isNaN()) fallbackOffset else offsetPx
                    // Depth: the sheet sinks very slightly as it is pulled away.
                    val s = 1f - .035f * pull
                    scaleX = s
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 0f)
                }
                .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .nestedScroll(connection)
                .navigationBarsPadding().imePadding(),
        ) {
            // Grabbable header: dragging here always moves the sheet, independent of scroll.
            Box(
                Modifier.fillMaxWidth()
                    .draggable(
                        rememberDraggableState { dragBy(it) },
                        Orientation.Vertical,
                        onDragStopped = { settle(it) },
                    )
                    .padding(top = 12.dp, bottom = 8.dp).height(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                // The handle reports how close the pull is to closing: it widens
                // with the pull and turns accent once release would close.
                Surface(
                    Modifier.width((36 + 20 * pull).dp).height(4.dp), shape = RoundedCornerShape(50),
                    color = if (armed) AppColors.Accent else lerp(AppColors.Muted, AppColors.Accent, pull * .4f),
                ) {}
            }
            Column(
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppSpace.Page).padding(bottom = AppSpace.Large)
                    .animateContentSize(motionSizeSpec()),
                verticalArrangement = Arrangement.spacedBy(AppSpace.Medium),
            ) {
                TaskDetailBody(task, db, timer, startEditing, showHeader = true, onClose = { after -> close(after = after) }, onFocus = onFocus, onAnalytics = onAnalytics, onChanged = onChanged)
            }
        }
    }
}

/**
 * Detail content shared by plan A (sheet) and plan B (inline row expansion).
 * onClose closes the current presentation and then runs the given action.
 */
@Composable
fun ColumnScope.TaskDetailBody(
    task: Task,
    db: Database,
    timer: FocusTimer,
    startEditing: Boolean,
    showHeader: Boolean,
    onClose: (after: () -> Unit) -> Unit,
    onFocus: () -> Unit,
    onAnalytics: () -> Unit,
    onChanged: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var checking by remember(task.id) { mutableStateOf(false) }
    var selfCheck by remember(task.id) { mutableStateOf(false) }
    var revoking by remember(task.id) { mutableStateOf(false) }
    var resetting by remember(task.id) { mutableStateOf(false) }
    val active = timer.taskId == task.id && timer.phase == "work"
    val paused = timer.taskId == task.id && timer.phase == "paused"
    val elapsed = db.elapsed(task)
    val completionMode = if (task.mode == "timer") "计时" else task.mode
    val canComplete = selfCheck && elapsed >= task.minutes * 60_000L && (task.mode == "行动型" || task.evidence.isNotBlank())

            if (showHeader) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (!task.typeKnown) "任务类型待确认" else if (task.required) "必做任务" else "选做任务", style = MaterialTheme.typography.labelLarge, color = AppColors.Muted)
                    Text(task.title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
                }
                StatusPill(when {
                    task.done -> "已完成"
                    active -> "专注中"
                    paused -> "已暂停"
                    else -> "待开始"
                }, active = task.done || active)
            }

            if (task.historySource.isNotBlank()) Text("人工重建 · ${task.historySource} · ${task.historyConfidence}", style=MaterialTheme.typography.bodySmall, color=AppColors.Muted)
            if (!task.typeKnown) {
                Text("这项历史任务的类型未知，确认后可能改变当天统计。", style=MaterialTheme.typography.bodySmall, color=AppColors.Muted)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick={db.confirmHistoricalType(task,true);onChanged()}) { Text("确认为必做") }
                    TextButton(onClick={db.confirmHistoricalType(task,false);onChanged()}) { Text("确认为选做") }
                }
            }

            if(!task.done && task.date<db.day()) Text("历史任务 · 计划日 ${task.date} · 补记仍需满足原验收条件",style=MaterialTheme.typography.bodySmall,color=AppColors.Accent)
            if(task.done&&task.completedAt>0L) {
                val completedAt=java.time.Instant.ofEpochMilli(task.completedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                val crossDay=completedAt.toLocalDate().toString()!=task.date
                Text("${if(crossDay)"跨日补记" else "实际完成"} · ${completedAt.year}年${completedAt.monthValue}月${completedAt.dayOfMonth}日 ${completedAt.toLocalTime().toString().take(5)}",style=MaterialTheme.typography.bodySmall,color=if(crossDay)AppColors.Accent else AppColors.Muted)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (!task.elapsedKnown) "时长未知" else if (task.minutes > 0) "${focusDuration(elapsed)} / ${task.minutes} 分钟" else focusDuration(elapsed), style = MaterialTheme.typography.titleMedium)
                    Text("专注记录", style = MaterialTheme.typography.labelSmall, color = AppColors.Muted)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(completionMode, style = MaterialTheme.typography.titleMedium)
                    Text("完成方式", style = MaterialTheme.typography.labelSmall, color = AppColors.Muted)
                }
            }
            TextButton(onClick = { onClose(onAnalytics) }, modifier = Modifier.align(Alignment.End)) {
                Text("查看成长统计")
            }
            if (task.checklist.isNotBlank()) {
                QuietSurface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Text("完成标准", style = MaterialTheme.typography.labelLarge, color = AppColors.Muted)
                    Text(task.checklist, style = MaterialTheme.typography.bodyLarge)
                }
            }

            if (task.done && (task.evidence.isNotBlank() || db.attachments(task).isNotEmpty())) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("历史完成证明", style = MaterialTheme.typography.titleMedium)
                    ProofActions(task, db, onChanged)
                }
            }

            if (task.done) {
                if (task.required && (task.rewardMinutes>0 || task.rewardGames>0)) {
                    Text("本项奖励已自动到账："+buildList{if(task.rewardMinutes>0)add("${task.rewardMinutes} 分钟");if(task.rewardGames>0)add("${task.rewardGames} 局")}.joinToString(" · "),style=MaterialTheme.typography.bodyMedium,color=AppColors.Muted)
                } else if (task.typeKnown && !task.required && !task.claimed && (task.rewardMinutes > 0 || task.rewardGames > 0)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("领取一项奖励", style = MaterialTheme.typography.titleMedium)
                        Text("每项选做奖励限领一次", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (task.rewardMinutes > 0) SecondaryAction("${task.rewardMinutes} 分钟", { db.claim(task, "video"); onChanged() }, enabled = db.rewardUnavailableReason(task.date) == null)
                            if (task.rewardGames > 0) SecondaryAction("${task.rewardGames} 局", { db.claim(task, "game"); onChanged() }, enabled = db.rewardUnavailableReason(task.date) == null)
                        }
                        db.rewardUnavailableReason(task.date)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted) }
                    }
                } else if (task.claimed) {
                    Text("奖励已领取", style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted)
                }
                SecondaryAction("撤销误完成", { revoking = true }, Modifier.fillMaxWidth())
            } else {
                PrimaryAction(
                    when { active -> "暂停专注"; paused -> "继续专注"; else -> "开始专注" },
                    onClick = {
                        when {
                            active -> timer.pause()
                            paused -> timer.resume(task)
                            else -> { if (timer.phase != "ready") timer.finish(); timer.begin(task) }
                        }
                        onChanged()
                        if (!active) onClose(onFocus)
                    },
                    Modifier.fillMaxWidth(),
                    icon = { PlayPauseMorph(if (active) 1f else 0f) },
                )

                if (elapsed > 0) SecondaryAction("重新计时", { resetting = true }, Modifier.fillMaxWidth())

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("完成证明", style = MaterialTheme.typography.titleMedium)
                    Text(if(db.setting("proof_no_save_${task.id}")=="true") "仅提交证明 · 不在本机保存" else "照片和附件保存在本机，用于完成自检", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                    ProofActions(task, db, onChanged)
                }

                androidx.compose.material3.HorizontalDivider(color = AppColors.Border)
                TextButton(onClick = { checking = !checking }, modifier = Modifier.clip(RoundedCornerShape(50)).immediatePress()) { Text(if (checking) "收起完成确认" else "完成自检") }
                AnimatedVisibility(
                    checking,
                    enter = expandVertically(tween(motionMillis())) + fadeIn(tween(motionMillis())),
                    exit = shrinkVertically(tween(motionMillis())) + fadeOut(tween(motionMillis())),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth().toggleable(value = selfCheck, onValueChange = { selfCheck = it }), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(selfCheck, onCheckedChange = null)
                            Text("我已按预设条件完成${if (task.mode == "掌握型") "独立练习或解释" else ""}")
                        }
                        Button(enabled = canComplete, onClick = {
                            db.complete(task)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            checking = false
                            onChanged()
                        }, modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50)).immediatePress(canComplete)) { Text(if(task.date<db.day())"确认补记完成" else "确认完成任务") }
                        Text(
                            when {
                                elapsed < task.minutes * 60_000L -> "还需专注 ${focusDuration((task.minutes * 60_000L - elapsed).coerceAtLeast(0))}"
                                task.mode != "行动型" && task.evidence.isBlank() -> "请先添加照片或附件证明"
                                else -> "请确认已达到预设完成标准"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.Muted,
                        )
                    }
                }

                Text("计划调整", style = MaterialTheme.typography.titleMedium)
                TaskChanges(task, db, onChanged, startEditing)
            }

    if (resetting) AlertDialog(
        onDismissRequest = { resetting = false },
        title = { Text("重新开始这次专注？") },
        text = { Text("已计入的专注时长将清零 · 完成证明保留") },
        confirmButton = { TextButton(onClick = { db.restart(task); resetting = false; onChanged() }) { Text("清零计时") } },
        dismissButton = { TextButton(onClick = { resetting = false }) { Text("取消") } },
    )
    if (revoking) AlertDialog(
        onDismissRequest = { revoking = false },
        title = { Text("撤销任务完成？") },
        text = { Text("将按可用余额冲回奖励 · 已使用额度不追回") },
        confirmButton = { TextButton(onClick = { db.revoke(task); revoking = false; onChanged() }) { Text("确认撤销") } },
        dismissButton = { TextButton(onClick = { revoking = false }) { Text("取消") } },
    )
}
