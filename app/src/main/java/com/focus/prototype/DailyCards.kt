package com.focus.prototype

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal data class DailyCardRecord(
    val date: String,
    val quote: String,
    val completed: Int,
    val total: Int,
    val focusMinutes: Long,
    val taskTitles: List<String>,
    val unlockedAt: Long,
    val valid: Boolean,
)

private val dailyOriginals = listOf(
    "薄雾散得很慢，山色便多留了一刻",
    "雨停以后，路面替天空保存了一会儿",
    "夜色落在窗边，书页仍亮着一小块",
    "风穿过空庭，留下几片晴朗",
    "潮声退去后，沙上还停着月光",
    "清早的光，沿着桌沿缓缓移动",
    "河流转弯时，没有惊动岸上的草",
    "暮云放慢脚步，天色便宽了一些",
    "石阶记得每一次轻声经过",
    "远山不说话，云替它翻过一页",
    "窗外的树影，把午后分成了几行",
    "一盏灯亮起，夜路就有了边界",
)

internal fun dailyCardQualifies(tasks: List<Task>): Boolean {
    val required = tasks.filter { it.required }
    return required.isNotEmpty() && required.all { it.done }
}

internal object DailyCards {
    private const val PREFIX = "daily_card_"

    fun reconcile(db: Database, date: String, tasks: List<Task> = db.tasks(date)): DailyCardRecord? {
        val key = PREFIX + date
        val old = db.setting(key).takeIf(String::isNotBlank)?.let(::decode)
        if (!dailyCardQualifies(tasks)) {
            if (old?.valid == true) db.setSetting(key, encode(old.copy(valid = false)))
            return null
        }
        val required = tasks.filter { it.required }
        val allDone = tasks.filter { it.done }
        val quote = old?.quote ?: dailyOriginals[Math.floorMod(date.hashCode(), dailyOriginals.size)]
        val record = DailyCardRecord(
            date = date,
            quote = quote,
            completed = allDone.size,
            total = tasks.size,
            focusMinutes = allDone.sumOf { it.elapsed }.coerceAtLeast(0L) / 60_000L,
            taskTitles = allDone.map { it.title },
            unlockedAt = old?.unlockedAt ?: System.currentTimeMillis(),
            valid = required.isNotEmpty(),
        )
        if (old != record) db.setSetting(key, encode(record))
        return record.takeIf { it.valid }
    }

    fun reconcileAll(db: Database, today: String): List<DailyCardRecord> {
        val dates = linkedSetOf<String>()
        db.readableDatabase.rawQuery("SELECT key FROM settings WHERE key LIKE ?", arrayOf("${PREFIX}%")).use { cursor ->
            while (cursor.moveToNext()) dates += cursor.getString(0).removePrefix(PREFIX)
        }
        dates += today
        dates.sortedDescending().forEach { date -> reconcile(db, date) }
        return read(db)
    }

    fun read(db: Database): List<DailyCardRecord> = buildList {
        db.readableDatabase.rawQuery("SELECT value FROM settings WHERE key LIKE ?", arrayOf("${PREFIX}%")).use { cursor ->
            while (cursor.moveToNext()) decode(cursor.getString(0))?.takeIf { it.valid }?.let(::add)
        }
    }.sortedByDescending { it.date }

    private fun encode(record: DailyCardRecord): String = JSONObject()
        .put("version", 1)
        .put("date", record.date)
        .put("quote", record.quote)
        .put("completed", record.completed)
        .put("total", record.total)
        .put("focusMinutes", record.focusMinutes)
        .put("taskTitles", JSONArray(record.taskTitles))
        .put("unlockedAt", record.unlockedAt)
        .put("valid", record.valid)
        .toString()

    private fun decode(json: String): DailyCardRecord? = runCatching {
        val value = JSONObject(json)
        if (value.optInt("version") != 1) return null
        val titles = value.optJSONArray("taskTitles") ?: JSONArray()
        DailyCardRecord(
            date = value.getString("date"),
            quote = value.getString("quote"),
            completed = value.optInt("completed"),
            total = value.optInt("total"),
            focusMinutes = value.optLong("focusMinutes"),
            taskTitles = (0 until titles.length()).mapNotNull { titles.optString(it).takeIf(String::isNotBlank) },
            unlockedAt = value.optLong("unlockedAt"),
            valid = value.optBoolean("valid"),
        )
    }.getOrNull()
}

/** Paper-like daily keepsake; the image is drawn locally and saved with the system picker. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DailyCardView(record: DailyCardRecord, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val bringIntoViewRequester = remember(record.date) { BringIntoViewRequester() }
    var cardCoordinates by remember(record.date) { mutableStateOf<LayoutCoordinates?>(null) }
    var bitmapToSave by remember { mutableStateOf<Bitmap?>(null) }
    var saveStatus by remember { mutableStateOf("") }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val bitmap = bitmapToSave
        bitmapToSave = null
        if (uri != null && bitmap != null) scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    } ?: error("无法写入所选位置")
                }
            }
            saveStatus = if (result.isSuccess) "卡片已保存" else "保存失败，请重试"
            bitmap.recycle()
        } else bitmap?.recycle()
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        PaperArtwork(record, Modifier.bringIntoViewRequester(bringIntoViewRequester).onGloballyPositioned { cardCoordinates = it })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                scope.launch {
                    bringIntoViewRequester.bringIntoView()
                    withFrameNanos { }
                    val coordinates = cardCoordinates
                    val bitmap = coordinates?.let { captureCard(view, it, density.density) }
                    if (bitmap == null) saveStatus = "卡片暂时无法生成图片"
                    else {
                        bitmapToSave = bitmap
                        saveLauncher.launch("向前-每日卡片-${record.date}.png")
                    }
                }
            }) { Text("保存为图片") }
            Spacer(Modifier.weight(1f))
            Text(saveStatus, style = MaterialTheme.typography.labelSmall, color = AppColors.Muted)
        }
    }
}

private fun captureCard(view: View, coordinates: LayoutCoordinates, density: Float): Bitmap? = runCatching {
    val bounds = coordinates.boundsInRoot()
    val left = bounds.left.roundToInt().coerceAtLeast(0)
    val top = bounds.top.roundToInt().coerceAtLeast(0)
    val right = bounds.right.roundToInt().coerceAtMost(view.width)
    val bottom = bounds.bottom.roundToInt().coerceAtMost(view.height)
    require(right > left && bottom > top)
    val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    view.draw(AndroidCanvas(full))
    val cropped = Bitmap.createBitmap(full, left, top, right - left, bottom - top)
    full.recycle()
    val targetWidth = maxOf(cropped.width, (360f * density).roundToInt())
    if (targetWidth == cropped.width) cropped else {
        val targetHeight = (cropped.height.toFloat() * targetWidth / cropped.width).roundToInt()
        Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true).also { cropped.recycle() }
    }
}.getOrNull()

@Composable
private fun PaperArtwork(record: DailyCardRecord, modifier: Modifier = Modifier) {
    val paper = Color(0xFFF2EBDD)
    val ink = Color(0xFF292521)
    val softInk = Color(0xFF75685B)
    val line = Color(0xFFBDB09C)
    val accent = Color(0xFFB87958)
    val date = LocalDate.parse(record.date)
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        color = paper,
        border = androidx.compose.foundation.BorderStroke(1.dp, line.copy(alpha = .72f)),
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("DAILY NOTE  /  向前", style = MaterialTheme.typography.labelSmall, color = softInk, letterSpacing = 1.4.sp)
                    Text("向前原创", style = MaterialTheme.typography.labelSmall, color = accent)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(date.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH).uppercase(), style = MaterialTheme.typography.labelMedium, color = softInk, letterSpacing = 1.6.sp)
                    Text(date.dayOfMonth.toString().padStart(2, '0'), style = MaterialTheme.typography.displayLarge.copy(fontFamily = FontFamily.Serif, fontSize = 64.sp, fontWeight = FontWeight.Light, lineHeight = 64.sp), color = ink)
                    Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.CHINESE), style = MaterialTheme.typography.labelSmall, color = softInk)
                }
            }
            Text(
                record.quote,
                modifier = Modifier.padding(top = 1.dp, bottom = 1.dp),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, lineHeight = 31.sp),
                color = ink,
            )
            TopographicArtwork(Modifier.fillMaxWidth().height(88.dp), line, accent)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                record.taskTitles.take(3).forEach { title ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Canvas(Modifier.size(5.dp)) { drawCircle(accent) }
                        Text(title, style = MaterialTheme.typography.bodySmall, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (record.completed > 3) Text("另有 ${record.completed - 3} 项完成记录", style = MaterialTheme.typography.labelSmall, color = softInk)
            }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("今日完成", style = MaterialTheme.typography.labelSmall, color = softInk)
                    Text("${record.completed} / ${record.total} 项", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = ink)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("有效专注", style = MaterialTheme.typography.labelSmall, color = softInk)
                    Text("${record.focusMinutes} 分钟", style = MaterialTheme.typography.titleSmall, color = ink)
                }
            }
            Spacer(Modifier.height(2.dp))
            Canvas(Modifier.fillMaxWidth().height(1.dp)) { drawLine(line, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            Text("一日一页  ·  ${date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))}", style = MaterialTheme.typography.labelSmall, color = softInk)
        }
    }
}

@Composable
private fun TopographicArtwork(modifier: Modifier, line: Color, accent: Color) {
    Canvas(modifier) {
        val cx = size.width * .73f
        val cy = size.height * .45f
        drawCircle(accent.copy(alpha = .12f), radius = size.height * .36f, center = Offset(cx, cy))
        drawCircle(accent.copy(alpha = .68f), radius = size.height * .14f, center = Offset(cx, cy))
        for (index in 0..5) {
            val inset = index * size.height * .075f
            val path = Path().apply {
                moveTo(size.width * .02f, size.height * (.68f - index * .035f))
                cubicTo(size.width * .18f, size.height * .18f + inset, size.width * .36f, size.height * .92f - inset, size.width * .52f, size.height * .49f)
                cubicTo(size.width * .68f, size.height * .08f + inset, size.width * .82f, size.height * .88f - inset, size.width * .98f, size.height * .27f + inset)
            }
            drawPath(path, line.copy(alpha = .45f - index * .045f), style = Stroke(width = .8.dp.toPx()))
        }
    }
}

@Composable
internal fun DailyCardArchive(cards: List<DailyCardRecord>) {
    var selected by remember { mutableStateOf<DailyCardRecord?>(null) }
    if (cards.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading("每日卡片", trailing = { Text("${cards.size} 张", color = AppColors.Muted, style = MaterialTheme.typography.labelMedium) })
            cards.forEachIndexed { index, card ->
                if (index == 0) DailyCardView(card)
                else Surface(
                    onClick = { selected = card },
                    shape = RoundedCornerShape(AppShape.Card),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(LocalDate.parse(card.date).format(DateTimeFormatter.ofPattern("yyyy年M月d日 E", Locale.CHINA)), style = MaterialTheme.typography.titleSmall)
                            Text("${card.completed} 项完成 · 专注 ${card.focusMinutes} 分钟", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                        }
                        Text("查看", style = MaterialTheme.typography.labelMedium, color = AppColors.Accent)
                    }
                }
            }
        }
    }
    selected?.let { card ->
        Dialog(onDismissRequest = { selected = null }) {
            Surface(shape = RoundedCornerShape(AppShape.Card), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DailyCardView(card)
                    TextButton(onClick = { selected = null }, modifier = Modifier.align(Alignment.End)) { Text("关闭") }
                }
            }
        }
    }
}

@Composable
internal fun DailyCardReveal(record: DailyCardRecord?) {
    if (record != null) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(motionMillis(460))) + expandVertically(tween(motionMillis(520))) + scaleIn(initialScale = .985f, animationSpec = tween(motionMillis(520))),
        ) { DailyCardView(record) }
    }
}
