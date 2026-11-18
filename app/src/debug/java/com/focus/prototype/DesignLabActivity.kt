package com.focus.prototype

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DesignLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FocusTheme { MotionHost(0, showToast = false) { LabV02Screen() } } }
    }
}

private data class Choice(val id: String, val title: String, val parameters: String)
private val groups = listOf(
    "排版" to listOf(Choice("T0", "现有对照", "标题 18sp Bold；辅助 labelSmall；单行省略"), Choice("T1", "适度降低", "标题 18sp SemiBold；字形轴 600；辅助 labelSmall；单行省略"), Choice("T2", "轻盈", "标题 18sp Medium；字形轴 500；辅助 labelSmall；单行省略")),
    "材质" to listOf(Choice("M0", "纯色对照", "Surface 实底；卡片 Border 34% / 0.7dp；无额外光影"), Choice("M1", "微弱层次", "顶部白色 4% 至底部黑色 4%"), Choice("M2", "柔光描边", "顶部浅光 8%；边缘 Border 45% / 0.7dp"), Choice("M3", "明显材质", "顶部浅光 18%；边缘 Border 80% / 1dp")),
    "按压" to listOf(Choice("P0", "现有对照", "缩放 0.975；阻尼 1；刚度 500；无颜色变化"), Choice("P1", "轻微缩放", "缩放 0.99；阻尼 1；刚度 500；无颜色变化"), Choice("P2", "柔和回弹", "缩放 0.96；阻尼 0.72；刚度 350；无颜色变化"), Choice("P3", "色彩与图标", "缩放 0.975；阻尼 1；刚度 500；按下变暗 12%；图标旋转 12°")),
    "完成" to listOf(Choice("C0", "现有对照", "勾选 260ms；划线 380ms；进度 500ms；XP 1000ms"), Choice("C1", "更快", "勾选 160ms；划线 220ms；进度 300ms；XP 1000ms"), Choice("C2", "分段强调", "勾选 260ms；划线 380ms 延迟 120ms；进度 500ms；XP 延迟 220ms / 1000ms")),
    "转场" to listOf(Choice("S0", "现有页面对照", "进入淡入+水平位移 260ms；退出 160ms"), Choice("S1", "淡入淡出", "进入/退出 260ms；无位移"), Choice("S2", "弹簧面板", "底部滑入 spring 阻尼 1 / 刚度 500；滑出 260ms"), Choice("S3", "跟手关闭", "面板随手指下拖；超过 100dp 关闭，否则回弹")),
    "反馈" to listOf(Choice("H0", "无反馈", "不主动触发触觉"), Choice("H1", "现有对照", "Compose TextHandleMove"), Choice("H2", "轻触", "系统 CLOCK_TICK"), Choice("H3", "确认", "系统 CONFIRM（Android 11+；旧版回退 VIRTUAL_KEY）"), Choice("H4", "达成", "系统 LONG_PRESS"), Choice("H5", "双段", "CLOCK_TICK 两次，间隔 100ms"), Choice("H6", "组合", "CLOCK_TICK 后 100ms CONFIRM；旧版 VIRTUAL_KEY"))
)

@Composable private fun LabScreen() {
    val choices = remember { mutableStateMapOf<String, Int>().apply { groups.forEach { put(it.first, 0) } } }
    val notes = remember { mutableStateMapOf<String, String>() }
    val preferred = remember { mutableStateMapOf<String, Int>() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val name = groups[tab].first
    val options = groups[tab].second
    val selected = choices[name] ?: 0
    val choice = options[selected]
    var copied by remember { mutableStateOf(false) }
    var pressStiffness by remember { mutableFloatStateOf(350f) }
    fun parameters(item: Choice) = if (item.id == "P2") item.parameters.replace("刚度 350", "刚度 ${pressStiffness.toInt()}") else item.parameters
    fun report(): String = buildString {
        appendLine("向前 Design Lab v0.1 · 实验评价")
        groups.forEach { (group, entries) ->
            val chosen = preferred[group]?.let { entries[it] }
            appendLine("$group：偏好 ${chosen?.let { "${it.id} ${it.title}" } ?: "未选择"}")
            entries.forEach { item ->
                appendLine("  ${item.id} ${item.title}｜${parameters(item)}")
                appendLine("  评价：${notes[item.id].orEmpty().ifBlank { "未填写" }}")
            }
        }
    }
    Scaffold(containerColor = AppColors.Background, topBar = {
        Column(Modifier.fillMaxWidth().background(AppColors.Background).statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("设计实验室 · DEBUG", style = MaterialTheme.typography.titleLarge, color = AppColors.Text)
            Text("独立模拟数据 · 选择方案并记录感受", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp, containerColor = AppColors.Background, contentColor = AppColors.Accent) {
                groups.forEachIndexed { i, entry -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(entry.first) }) }
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = AppSpace.Page, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("${name}实验", style = MaterialTheme.typography.headlineSmall, color = AppColors.Text) }
            item { Text("点击编号切换方案；可反复体验。", color = AppColors.Muted, style = MaterialTheme.typography.bodySmall) }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEachIndexed { index, item ->
                        FilterChip(selected = selected == index, onClick = { choices[name] = index }, label = { Text("${item.id} ${item.title}") })
                    }
                }
            }
            item { Text("${choice.id} · ${choice.title}\n${parameters(choice)}", color = AppColors.Muted, style = MaterialTheme.typography.bodySmall) }
            item {
                when (tab) {
                    0 -> TypographyExperiment(selected)
                    1 -> MaterialExperiment(selected)
                    2 -> PressExperiment(selected, pressStiffness) { pressStiffness = it; copied = false }
                    3 -> CompletionExperiment(selected)
                    4 -> TransitionExperiment(selected)
                    else -> FeedbackExperiment(selected)
                }
            }
            item { SecondaryAction(if (preferred[name] == selected) "已选为本类偏好 · ${choice.id}" else "选择 ${choice.id} 为本类偏好", onClick = { preferred[name] = selected; copied = false }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = notes[choice.id].orEmpty(), onValueChange = { notes[choice.id] = it; copied = false }, label = { Text("${choice.id} 的独立评价") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
            item {
                SecondaryAction(if (copied) "已复制全部方案和评价" else "复制全部方案和评价", onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("向前设计实验评价", report()))
                    copied = true
                }, modifier = Modifier.fillMaxWidth())
            }
            item { Text("选择与评价仅保留在本次实验室会话中；复制后可粘贴分享。", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted) }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable private fun TypographyExperiment(option: Int) {
    val weight = listOf(FontWeight.Bold, FontWeight.SemiBold, FontWeight.Medium)[option]
    val family = if (option == 0) gritFontRounded() else FontFamily(Font(R.font.google_sans_flex, variationSettings = FontVariation.Settings(FontVariation.weight(if (option == 1) 600 else 500), FontVariation.Setting("ROND", 35f))))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("今日任务", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold))
        Text("今天 · 1 / 3 已完成", style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
        listOf("整理今天的工作计划", "完成本周研究论文的阅读、批注与详细总结并发送给团队讨论").forEach {
            Surface(shape = RoundedCornerShape(AppShape.Card), color = AppColors.Surface) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CompletionMark(false)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(it, fontFamily = family, fontWeight = weight, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("必做 · 50 分钟 · 完成可得 30 分钟 · 今日截止", style = MaterialTheme.typography.labelSmall, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("⋮", fontSize = 24.sp)
                }
            }
        }
        Text("仅调整任务标题字重；颜色、单行截断与形态保持一致。", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
    }
}

private fun materialBrush(option: Int): Brush = when (option) {
    1 -> Brush.verticalGradient(listOf(Color.White.copy(alpha = .04f), Color.Black.copy(alpha = .04f)))
    2 -> Brush.verticalGradient(listOf(Color.White.copy(alpha = .08f), Color.Transparent))
    3 -> Brush.verticalGradient(listOf(Color.White.copy(alpha = .18f), Color.Transparent))
    else -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
}
@Composable private fun MaterialSample(option: Int, color: Color = AppColors.Surface, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(AppShape.Card)
    val border = when(option) { 2 -> .45f; 3 -> .8f; else -> .34f }
    Column(Modifier.fillMaxWidth().clip(shape).background(color).background(materialBrush(option)).border(if (option == 3) 1.dp else .7.dp, AppColors.Border.copy(alpha = border), shape).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}
@Composable private fun MaterialExperiment(option: Int) {
    var dialog by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MaterialSample(option) { Text("任务项 · 整理今日计划", style = MaterialTheme.typography.titleMedium); Text("必做 · 25 分钟 · 完成可得 10 分钟", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted) }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(AppShape.Control)).background(AppColors.Accent).background(materialBrush(option)).border(if(option == 3) 1.dp else .7.dp, AppColors.Border.copy(alpha = when(option) { 2 -> .45f; 3 -> .8f; else -> 0f }), RoundedCornerShape(AppShape.Control)).clickable { dialog = true }.padding(16.dp), contentAlignment = Alignment.Center) { Text("体验按钮与弹窗", color = AppColors.AccentInk) }
        if (dialog) androidx.compose.ui.window.Dialog(onDismissRequest = { dialog = false }) {
            MaterialSample(option) { Text("弹窗材质预览", style = MaterialTheme.typography.titleLarge); Text("此处仅为独立模拟内容。", color = AppColors.Muted); TextButton(onClick = { dialog = false }) { Text("关闭") } }
        }
    }
}
@Composable private fun PressExperiment(option: Int, stiffness: Float, onStiffness: (Float) -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val target = listOf(.975f, .99f, .96f, .975f)[option]
    val scale by animateFloatAsState(if (pressed && !LocalReducedMotion.current) target else 1f, spring(dampingRatio = if(option == 2) .72f else 1f, stiffness = if(option == 2) stiffness else 500f), label = "lab-press")
    val icon by animateFloatAsState(if (pressed && option == 3) 12f else 0f, spring(dampingRatio = 1f, stiffness = 500f), label = "lab-icon")
    var count by remember { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { count++; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }, interactionSource = source, modifier = Modifier.fillMaxWidth().height(52.dp).graphicsLayer { scaleX = scale; scaleY = scale }, shape = RoundedCornerShape(AppShape.Control), colors = ButtonDefaults.buttonColors(containerColor = if (pressed && option == 3) androidx.compose.ui.graphics.lerp(AppColors.Accent, Color.Black, .12f) else AppColors.Accent, contentColor = AppColors.AccentInk)) {
            Text("✦", modifier = Modifier.rotate(icon)); Spacer(Modifier.width(8.dp)); Text("按住、释放或连续点击")
        }
        if (option == 2) { Text("P2 弹簧刚度：${stiffness.toInt()}（可调 200–700）", color = AppColors.Muted); Slider(value = stiffness, onValueChange = onStiffness, valueRange = 200f..700f, steps = 9) }
        Text("已点击 $count 次；所有方案使用当前主按钮触觉。", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
    }
}
@Composable private fun CompletionExperiment(option: Int) {
    var done by remember { mutableStateOf(false) }
    var xp by remember { mutableIntStateOf(400) }
    var strikeTarget by remember { mutableStateOf(false) }
    LaunchedEffect(done, option) {
        if (option == 2 && done) delay(120)
        strikeTarget = done
        if (option == 2 && done) delay(100)
        xp = if (done) 500 else 400
    }
    val duration = if (option == 1) 220 else 380
    val strike by animateFloatAsState(if (strikeTarget) 1f else 0f, tween(motionMillis(duration), easing = AppMotion.Ease), label = "lab-strike")
    val completion by animateFloatAsState(if(done) 1f else 0f, tween(motionMillis(if(option == 1) 160 else 260)), label = "lab-completion")
    val progress by animateFloatAsState(if(done) 1f else 0f, tween(motionMillis(if(option == 1) 300 else 500), easing = AppMotion.Ease), label = "lab-progress")
    MotionHost(xp, showToast = true) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MaterialSample(0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // C0 uses the production check. Other choices expose their own timing.
                    if (option == 0) CompletionMark(done) else Canvas(Modifier.size(22.dp)) {
                        drawCircle(AppColors.Border, style = Stroke(1.5.dp.toPx()))
                        if(completion > 0) {
                            drawCircle(AppColors.Accent.copy(alpha = completion))
                            val path = Path().apply { moveTo(size.width*.24f,size.height*.5f); lineTo(size.width*.43f,size.height*.69f); lineTo(size.width*.77f,size.height*.32f) }
                            val measure = PathMeasure().apply { setPath(path, false) }
                            val segment = Path(); measure.getSegment(0f, measure.length * completion, segment, true)
                            drawPath(segment, AppColors.AccentInk, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        Text("完成一项模拟任务", color = if(done) AppColors.Muted else AppColors.Text, modifier = Modifier.drawWithContent { drawContent(); if(strike > 0) drawLine(AppColors.Muted, Offset(0f,size.height/2), Offset(size.width*strike,size.height/2), 1.7.dp.toPx()) })
                    }
                }
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text("模拟 XP：$xp · 仅供演示", color = AppColors.Muted)
                MotionCounter(xp, MaterialTheme.typography.titleLarge, AppMotion.Growth)
            }
            PrimaryAction(if(done) "撤销完成" else "完成任务", onClick = {
                done = !done

            }, modifier = Modifier.fillMaxWidth())
        }
    }
}
@Composable private fun TransitionExperiment(option: Int) {
    var visible by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val threshold = with(density) { 100.dp.toPx() }
    val animatedDrag by animateFloatAsState(drag, spring(dampingRatio = 1f, stiffness = 500f), label = "sheet-drag")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryAction(if (visible) "关闭预览" else "打开预览", { visible = !visible; drag = 0f }, Modifier.fillMaxWidth())
        Box(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(AppShape.Card)).background(AppColors.Raised), contentAlignment = Alignment.BottomCenter) {
            val enter = when(option) { 0 -> fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 28 }; 1 -> fadeIn(tween(260)); else -> fadeIn(tween(260)) + slideInVertically(spring(dampingRatio = 1f, stiffness = 500f)) { it } }
            val exit = when(option) { 0 -> fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { -it / 36 }; 1 -> fadeOut(tween(260)); else -> fadeOut(tween(160)) + slideOutVertically(tween(260)) { it } }
            androidx.compose.animation.AnimatedVisibility(visible, enter = enter, exit = exit) {
                Column(Modifier.fillMaxWidth().offset { IntOffset(0, (if (dragging) drag else animatedDrag).toInt()) }.then(if(option == 3) Modifier.pointerInput(Unit) { detectDragGestures(onDragStart = { dragging = true }, onDragEnd = { if(drag > threshold) visible = false; dragging = false; drag = 0f }, onDragCancel = { dragging = false; drag = 0f }) { change, amount -> change.consume(); drag = (drag + amount.y).coerceAtLeast(0f) } } else Modifier).background(AppColors.Surface, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).padding(20.dp)) {
                    Text("详情面板预览", style = MaterialTheme.typography.titleLarge)
                    Text(if(option == 3) "向下拖动关闭；不足阈值回弹" else "重复打开与关闭以对比", color = AppColors.Muted)
                    TextButton(onClick = { visible = false }) { Text("关闭") }
                }
            }
        }
    }
}
@Composable private fun FeedbackExperiment(option: Int) {
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    fun confirm() = view.performHapticFeedback(if(Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryAction("体验触觉", onClick = {
            scope.launch {
                when(option) {
                    1 -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    2 -> view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    3 -> confirm()
                    4 -> view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    5 -> { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); delay(100); view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                    6 -> { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); delay(100); confirm() }
                }
            }
        }, modifier = Modifier.fillMaxWidth())
        Text("使用系统语义触觉，效果取决于设备和系统设置；无音频资源，暂不提供声音。", color = AppColors.Muted, style = MaterialTheme.typography.bodySmall)
    }
}


