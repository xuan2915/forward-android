package com.focus.prototype

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay

private data class LabOption(val id: String, val title: String, val detail: String)
private data class LabGroup(val title: String, val options: List<LabOption>)
private fun opt(id: String, title: String, detail: String) = LabOption(id, title, detail)
private val labGroups = listOf(
    LabGroup("字号", listOf(opt("SZ16","16sp","任务标题 16sp；其余保持一致"),opt("SZ18","18sp · 对照","任务标题 18sp；其余保持一致"),opt("SZ20","20sp","任务标题 20sp；其余保持一致"))),
    LabGroup("字重", listOf(opt("W400","Regular","系统 SansSerif；任务标题 400"),opt("W600","SemiBold","系统 SansSerif；任务标题 600"),opt("W800","ExtraBold","系统 SansSerif；任务标题 800"))),
    LabGroup("层级", listOf(opt("L0","现有参考","页面 headlineLarge ExtraBold；任务 18sp Bold；辅助 labelSmall；数字 titleMedium Bold"),opt("L1","清晰层级","页面 28sp Bold；任务 18sp SemiBold；辅助 12sp；数字 22sp SemiBold"),opt("L2","轻盈层级","页面 26sp SemiBold；任务 16sp Medium；辅助 13sp；数字 20sp Medium"))),
    LabGroup("奖励信息", listOf(opt("R0","集中 · 对照","奖励与必做、时长同一辅助行"),opt("R1","独立","奖励放在独立辅助行，颜色保持一致"))),
    LabGroup("长标题", listOf(opt("N0","单行省略 · 对照","1 行；溢出省略"),opt("N1","两行","最多 2 行；溢出省略"),opt("N2","自适应高度","完整换行；高度随内容增长"))),
    LabGroup("整体材质", listOf(opt("M0","现有基线","原有页面背景及控件；任务面板 Border 34% / 0.7dp"),opt("M2A","重点使用 M2","任务与提醒弹窗：顶部白 8%；Border 45% / 0.7dp"),opt("M2B","全部使用 M2","任务、按钮、筛选、导航和弹窗：顶部白 8%；Border 45% / 0.7dp"))),
    LabGroup("按压诊断", listOf(opt("D100","无缩放","按住 1.0；即时反馈；释放弹簧 1 / 500"),opt("D975","现有幅度","按住 0.975；即时反馈；释放弹簧 1 / 500"),opt("D94","明显","按住 0.94；即时反馈；释放弹簧 1 / 500"),opt("D90","强烈","按住 0.90；即时反馈；释放弹簧 1 / 500"))),
    LabGroup("按压细节", listOf(opt("P0","现有对照","按下及释放均用弹簧 1 / 500；目标 0.975；不变色"),opt("P1","柔和回弹","按住 0.975；释放弹簧 0.72 / 350；不变色"),opt("P2","按压变暗","按住 0.975；释放弹簧 1 / 500；颜色变暗 12%"),opt("P3","图标形变","按住 0.975；释放弹簧 1 / 500；图标旋转 12°"))),
    LabGroup("完成编排", listOf(opt("C0","现有对照","勾选 260ms；划线 380ms；进度 500ms；XP 1000ms"),opt("C2","分段强调","勾选 260ms；划线延迟 120ms / 380ms；XP 延迟 220ms / 1000ms；后续反馈依次出现"))),
    LabGroup("微交互", listOf(opt("I0","基线","筛选直接变化；数字直接更新；现有主按钮"),opt("I1","筛选指示器","选中指示器水平移动；spring 1 / 500"),opt("I2","数字过渡","MotionCounter，260ms"),opt("I3","按钮图标","按住缩放 0.94；图标旋转 12°；释放弹簧 0.72 / 350")))
)
private val history = "v0.1 历史：排版 T0（用户反馈三版无可感知差异，不能视作确认）；材质 M2；按压未选（反馈没有变化）；完成 C2；转场 S0；触觉 H1。其他评价未填写。"

@Composable internal fun LabV02Screen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("design_lab_v02", Context.MODE_PRIVATE) }
    var tab by remember { mutableIntStateOf(0) }
    val selection = remember { mutableStateMapOf<String, Int>().apply { labGroups.forEach { put(it.title, 0) } } }
    val favorites = remember { mutableStateMapOf<String, String>().apply { labGroups.forEach { g -> prefs.getString("favorite_${g.title}", null)?.let { put(g.title,it) } } } }
    val notes = remember { mutableStateMapOf<String, String>().apply { labGroups.flatMap { it.options }.forEach { o -> prefs.getString("note_${o.id}", null)?.let { put(o.id,it) } } } }
    var scenario by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    val group = labGroups[tab]
    val selected = selection[group.title] ?: 0
    val current = group.options[selected]
    fun report() = buildString {
        appendLine("向前 Design Lab v0.2 · 实验评价")
        appendLine("默认转场 S0；默认触觉 H1；全部为模拟数据。")
        appendLine("完成模拟场景：${scenarioNames[scenario]}")
        labGroups.forEach { g ->
            appendLine("${g.title}：偏好 ${favorites[g.title] ?: "未选择"}")
            g.options.forEach { o -> appendLine("  ${o.id} ${o.title}｜${o.detail}\n  评价：${notes[o.id].orEmpty().ifBlank { "未填写" }}") }
        }
        appendLine(history)
    }
    Scaffold(containerColor = AppColors.Background, topBar = {
        Column(Modifier.background(AppColors.Background).statusBarsPadding().padding(horizontal = 18.dp, vertical = 6.dp)) {
            Text("设计实验室 v0.2 · DEBUG", style = MaterialTheme.typography.titleLarge, color = AppColors.Text)
            Text("模拟数据 · S0 转场 / H1 触觉 · 不影响正式数据", color = AppColors.Muted, style = MaterialTheme.typography.bodySmall)
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp, containerColor = AppColors.Background, contentColor = AppColors.Accent) {
                labGroups.forEachIndexed { i, g -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(g.title) }) }
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = AppSpace.Page, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(group.title, style = MaterialTheme.typography.headlineSmall) }
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                group.options.forEachIndexed { i, o -> FilterChip(selected == i, onClick = { selection[group.title] = i }, label = { Text("${o.id} ${o.title}") }) }
            } }
            item { Text("${current.id} · ${current.detail}", color = AppColors.Muted, style = MaterialTheme.typography.bodySmall) }
            item {
                AnimatedContent(targetState = tab, transitionSpec = {
                    (fadeIn(tween(AppMotion.Standard)) + slideInHorizontally(tween(AppMotion.Standard)) { it / 28 }) togetherWith
                        (fadeOut(tween(AppMotion.Short)) + slideOutHorizontally(tween(AppMotion.Short)) { -it / 36 })
                }, label = "lab-page-S0") { screen ->
                    val active = selection[labGroups[screen].title] ?: 0
                    when (screen) {
                        in 0..4 -> TypeDemo(screen, active)
                        5 -> MaterialOverview(active)
                        6, 7 -> PressDiagnostic(screen, active)
                        8 -> SequenceDemo(active, scenario) { scenario = it }
                        else -> MicroDemo(active)
                    }
                }
            }
            item { SecondaryAction(if(favorites[group.title] == current.id) "已选为本类偏好 · ${current.id}" else "选 ${current.id} 为本类偏好", {
                favorites[group.title] = current.id; prefs.edit().putString("favorite_${group.title}",current.id).apply(); copied = false
            }, Modifier.fillMaxWidth()) }
            item { OutlinedTextField(notes[current.id].orEmpty(), onValueChange = { notes[current.id] = it; prefs.edit().putString("note_${current.id}",it).apply(); copied = false }, label = { Text("${current.id} 独立评价") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
            item { SecondaryAction(if(copied) "已复制" else "复制 v0.2 评价与 v0.1 历史", {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("向前设计实验", report())); copied = true
            }, Modifier.fillMaxWidth()) }
            item { Text("本轮偏好和评价仅保存在本机实验室；历史反馈另存文档。", color = AppColors.Muted, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable private fun TypeDemo(group: Int, option: Int) {
    val size = if(group == 0) listOf(16,18,20)[option] else if(group == 2 && option == 2) 16 else 18
    val weight = when { group == 1 -> listOf(FontWeight.Normal,FontWeight.SemiBold,FontWeight.ExtraBold)[option]; group == 2 && option == 1 -> FontWeight.SemiBold; group == 2 && option == 2 -> FontWeight.Medium; else -> FontWeight.Bold }
    val maxLines = if(group == 4) listOf(1,2,Int.MAX_VALUE)[option] else 1
    val pageSize = if(group == 2) listOf(32,28,26)[option] else 32
    val numberSize = if(group == 2) listOf(16,22,20)[option] else 16
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("今日任务 Today", fontSize = pageSize.sp, fontWeight = if(group == 2 && option == 2) FontWeight.SemiBold else FontWeight.ExtraBold)
            Text("1 / 3", fontSize = numberSize.sp, fontWeight = weight, color = AppColors.Accent)
        }
        listOf("整理今天的计划 · Review plan", "完成本周研究论文的阅读、批注与详细总结，并整理 Action Items 发送给团队讨论").forEach { title ->
            Surface(shape = RoundedCornerShape(AppShape.Card), color = AppColors.Surface) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CompletionMark(false)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(title, fontFamily = FontFamily.SansSerif, fontSize = size.sp, fontWeight = weight, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
                        Text(if(group == 3 && option == 1) "必做 · 50 分钟 · 今日截止" else "必做 · 50 分钟 · 完成可得 30 分钟", color = AppColors.Muted, fontSize = if(group == 2 && option == 2) 13.sp else 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if(group == 3 && option == 1) Text("奖励 · 30 分钟视频", color = AppColors.Muted, fontSize = 11.sp)
                    }
                    Text("⋮", fontSize = 24.sp)
                }
            }
        }
        Text("本组统一用系统 SansSerif 比较；正式任务标题的拉丁字形采用 Google Sans Flex，中文使用设备回退，效果取决于设备。", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
    }
}
private fun Modifier.labMaterial(active: Boolean, shape: RoundedCornerShape, base: Color): Modifier = this.clip(shape).background(base).then(if(active) Modifier.background(Brush.verticalGradient(listOf(Color.White.copy(alpha=.08f),Color.Transparent))).border(.7.dp,AppColors.Border.copy(alpha=.45f),shape) else Modifier)
@Composable private fun MaterialOverview(option: Int) {
    var dialog by remember { mutableStateOf(false) }
    var filter by remember { mutableIntStateOf(0) }
    val itemM = option != 0
    val all = option == 2
    Column(Modifier.fillMaxWidth().background(AppColors.Background), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("今日任务", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("全部","必做","选做").forEachIndexed { i, text ->
            Box(Modifier.labMaterial(all, RoundedCornerShape(50), if(filter==i) AppColors.AccentWash else AppColors.Raised).clickable { filter=i }.padding(horizontal=14.dp,vertical=8.dp)) { Text(text, color = if(filter==i) AppColors.Accent else AppColors.Muted) }
        } }
        Column(Modifier.fillMaxWidth().labMaterial(itemM, RoundedCornerShape(AppShape.Card), AppColors.Surface).border(.7.dp,AppColors.Border.copy(alpha=if(itemM).45f else .34f),RoundedCornerShape(AppShape.Card)).padding(16.dp)) {
            Text("整理今日计划", style = MaterialTheme.typography.titleMedium); Text("必做 · 25 分钟 · 完成可得 10 分钟", color=AppColors.Muted,style=MaterialTheme.typography.bodySmall)
        }
        Box(Modifier.fillMaxWidth().labMaterial(all, RoundedCornerShape(AppShape.Control), AppColors.Accent).clickable { dialog=true }.padding(15.dp), contentAlignment=Alignment.Center) { Text("查看夜间提醒", color=AppColors.AccentInk) }
        Row(Modifier.fillMaxWidth().labMaterial(all, RoundedCornerShape(AppShape.Card),AppColors.Surface).padding(14.dp), horizontalArrangement=Arrangement.SpaceAround) { listOf("今天","专注","奖励","成长").forEach { Text(it, color=if(it=="今天") AppColors.Accent else AppColors.Muted, style=MaterialTheme.typography.labelMedium) } }
        if(dialog) androidx.compose.ui.window.Dialog(onDismissRequest={dialog=false}) {
            Column(Modifier.fillMaxWidth().labMaterial(itemM, RoundedCornerShape(AppShape.Panel),AppColors.Surface).padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("夜间收尾提醒",style=MaterialTheme.typography.titleLarge);Text("模拟提醒：准备结束今天的工作。",color=AppColors.Muted);TextButton(onClick={dialog=false}){Text("知道了")}
            }
        }
    }
}

@Composable private fun PressDiagnostic(group: Int, option: Int) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val target = if(group==6) listOf(1f,.975f,.94f,.90f)[option] else .975f
    val actualTarget = if(reduced) 1f else if(pressed) target else 1f
    val scale by animateFloatAsState(actualTarget, animationSpec = if(pressed && !(group==7 && option==0)) snap() else spring(dampingRatio=if(group==7 && option==1) .72f else 1f,stiffness=if(group==7 && option==1) 350f else 500f), label="press-diagnostic")
    val haptic=LocalHapticFeedback.current
    var count by remember { mutableIntStateOf(0) }
    val color=if(group==7 && option==2 && pressed) lerp(AppColors.Accent,Color.Black,.12f) else AppColors.Accent
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Button(onClick={count++;haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)}, interactionSource=source, modifier=Modifier.fillMaxWidth().height(52.dp).graphicsLayer { scaleX=scale;scaleY=scale },shape=RoundedCornerShape(AppShape.Control),colors=ButtonDefaults.buttonColors(containerColor=color,contentColor=AppColors.AccentInk)) {
            Text("✦",modifier=Modifier.rotate(if(group==7 && option==3 && pressed)12f else 0f));Spacer(Modifier.width(8.dp));Text("按住、释放、快速连点")
        }
        Text("pressed：$pressed · 实际缩放：${"%.3f".format(scale)}\n目标：${"%.3f".format(actualTarget)} · 系统动画关闭：$reduced · 点击：$count", color=AppColors.Muted,style=MaterialTheme.typography.bodyMedium)
        if(reduced) Text("系统已关闭动画；缩放保持 1.0。可先在系统设置中恢复动画后重试。",color=AppColors.Warning,style=MaterialTheme.typography.bodySmall)
        Text("诊断组按下即时到达目标；P0 保留原弹簧。松开后恢复，不阻塞点击。",color=AppColors.Muted,style=MaterialTheme.typography.bodySmall)
    }
}

private val scenarioNames=listOf("普通完成与撤销","全部必做完成","完成并获得娱乐奖励","XP 增长并升级","升级、成就与每日纪念同时达成")
@Composable private fun SequenceDemo(option:Int, scenario:Int, onScenario:(Int)->Unit) {
    var done by remember { mutableStateOf(false) }
    var stage by remember { mutableIntStateOf(0) }
    var strike by remember { mutableStateOf(false) }
    var xp by remember { mutableIntStateOf(if(scenario>=3) 400 else 200) }
    val reduced=LocalReducedMotion.current
    LaunchedEffect(done, option, scenario, reduced) {
        if(!done) { stage=0; strike=false; xp=if(scenario>=3) 400 else 200; return@LaunchedEffect }
        stage=1
        if(option==1 && !reduced) delay(120)
        strike=true
        if(option==1 && !reduced) delay(100)
        xp=if(scenario>=3) 500 else 300
        if(scenario>0) { if(option==1 && !reduced) delay(1050); stage=2 }
        if(scenario==4) { if(option==1 && !reduced) delay(1000); stage=3; if(option==1 && !reduced) delay(1000); stage=4 }
    }
    val line by animateFloatAsState(if(strike) 1f else 0f,tween(motionMillis(380),easing=AppMotion.Ease),label="sequence-line")
    val progress by animateFloatAsState(if(done) 1f else 0f,tween(motionMillis(500),easing=AppMotion.Ease),label="sequence-progress")
    val haptic=LocalHapticFeedback.current
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { scenarioNames.forEachIndexed { i, name -> FilterChip(scenario==i,onClick={done=false;onScenario(i)},label={Text(name)}) } }
        Text("${scenarioNames[scenario]} · 不产生真实奖励或 XP",style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)
        MotionHost(xp,showToast=false) {
            QuietSurface {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    CompletionMark(done)
                    Text("完成一项模拟任务",modifier=Modifier.drawWithContent { drawContent();if(line>0f) drawLine(AppColors.Muted,Offset(0f,size.height/2),Offset(size.width*line,size.height/2),1.7.dp.toPx()) },color=if(done)AppColors.Muted else AppColors.Text)
                }
                LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("模拟 XP",color=AppColors.Muted); MotionCounter(xp,MaterialTheme.typography.titleMedium,AppMotion.Growth) }
                AnimatedVisibility(stage>=2) { Text(when(scenario){1->"今日必做已全部完成";2->"娱乐奖励 +10 分钟";3->"等级提升 · 等级 2";4->"等级提升 · 等级 2";else->""},color=AppColors.Reward) }
                AnimatedVisibility(stage>=3) { Text("成就解锁 · 十项有成",color=AppColors.Accent) }
                AnimatedVisibility(stage>=4) { Text("今日纪念卡片已准备",color=AppColors.Text) }
            }
        }
        PrimaryAction(if(done) "撤销并重置" else "完成模拟任务",{ done=!done },Modifier.fillMaxWidth())
        Text(if(option==0) "C0：状态同步展示；并发反馈仅作对照。" else "C2：先任务，再 XP，之后依次呈现结果；撤销会取消未完成的延迟。",color=AppColors.Muted,style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun MicroDemo(option:Int) {
    var selected by remember { mutableIntStateOf(0) }
    var number by remember { mutableIntStateOf(4) }
    val source=remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduced=LocalReducedMotion.current
    val scale by animateFloatAsState(if(option==3 && pressed && !reduced).94f else 1f, spring(dampingRatio=.72f,stiffness=350f),label="micro-button")
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(50)).background(AppColors.Raised)) {
            val width=maxWidth/3
            val pos by animateDpAsState(width*selected, if(option==1 && !reduced) spring(dampingRatio=1f,stiffness=500f) else snap(),label="filter-position")
            Box(Modifier.offset(x=pos).width(width).fillMaxHeight().background(AppColors.AccentWash,RoundedCornerShape(50)))
            Row(Modifier.fillMaxSize()) { listOf("全部","必做","选做").forEachIndexed { i,t -> Box(Modifier.weight(1f).fillMaxHeight().clickable { selected=i },contentAlignment=Alignment.Center){Text(t,color=if(selected==i)AppColors.Accent else AppColors.Muted)} } }
        }
        Row(verticalAlignment=Alignment.CenterVertically) { if(option==2) MotionCounter(number,MaterialTheme.typography.headlineSmall) else Text("$number",style=MaterialTheme.typography.headlineSmall); Spacer(Modifier.width(12.dp)); TextButton(onClick={number++}) {Text("增加数字")} }
        Button(onClick={},interactionSource=source,modifier=Modifier.fillMaxWidth().height(52.dp).graphicsLayer { scaleX=scale;scaleY=scale },shape=RoundedCornerShape(AppShape.Control)) { Text("✦",Modifier.rotate(if(option==3 && pressed)12f else 0f));Spacer(Modifier.width(8.dp));Text("体验按钮") }
    }
}


