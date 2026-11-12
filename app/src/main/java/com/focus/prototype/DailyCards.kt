package com.focus.prototype

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class DailyCardRecord(
    val date: String,
    val quote: String,
    val completed: Int,
    val total: Int,
    val focusMinutes: Long,
    val taskTitles: List<String>,
    val unlockedAt: Long,
    val valid: Boolean,
    val excerptId: String = "",
    val edition: CardEdition = CardEdition.Legacy,
)

internal enum class CardEdition(val key: String, val artwork: Int) {
    Legacy("legacy-v1", R.drawable.daily_landscape),
    Sky("sky-meadow-v1", R.drawable.daily_sky_meadow),
    Paper("paper-terrace-v1", R.drawable.daily_sky_terrace);

    companion object {
        fun fromKey(key: String): CardEdition = entries.firstOrNull { it.key == key } ?: Legacy
    }
}

internal fun cardEdition(quote: String, dayIndex: Long): CardEdition =
    if (quote.length > 24 || dayIndex % 2L == 0L) CardEdition.Paper else CardEdition.Sky

private data class LiteraryExcerpt(val text: String, val source: String)

private val excerpts = listOf(
    // https://zh.wikisource.org/wiki/移居
    LiteraryExcerpt("奇文共欣赏，疑义相与析", "陶渊明 ·《移居》其一"),
    // https://zh.wikisource.org/wiki/陋室銘
    LiteraryExcerpt("苔痕上阶绿，草色入帘青\n谈笑有鸿儒，往来无白丁", "刘禹锡 ·《陋室铭》"),
)

private fun legacyExcerpt(stored: String): LiteraryExcerpt =
    excerpts.firstOrNull { it.text == stored } ?: LiteraryExcerpt(stored, "向前原创")

internal fun dailyCardQualifies(tasks: List<Task>): Boolean =
    tasks.isNotEmpty() && tasks.all { it.done }

internal object DailyCards {
    private const val PREFIX = "daily_card_"

    fun reconcile(db: Database, date: String, tasks: List<Task> = db.tasks(date)): DailyCardRecord? {
        val key = PREFIX + date
        val old = db.setting(key).takeIf(String::isNotBlank)?.let(::decode)
        if (!dailyCardQualifies(tasks)) {
            if (old?.valid == true) db.setSetting(key, encode(old.copy(valid = false)))
            return null
        }
        val dayIndex = LocalDate.parse(date).toEpochDay()
        val catalog = DailyExcerpts.entries
        val excerpt = catalog[Math.floorMod(dayIndex, catalog.size.toLong()).toInt()]
        val quote = old?.quote ?: excerpt.chinese
        val record = DailyCardRecord(
            date = date,
            quote = quote,
            completed = tasks.size,
            total = tasks.size,
            focusMinutes = tasks.sumOf { it.elapsed }.coerceAtLeast(0L) / 60_000L,
            taskTitles = tasks.map { it.title },
            unlockedAt = old?.unlockedAt ?: System.currentTimeMillis(),
            valid = true,
            excerptId = old?.excerptId ?: excerpt.id,
            edition = old?.edition ?: cardEdition(quote, dayIndex),
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
        .put("version", 2)
        .put("excerptId", record.excerptId)
        .put("edition", record.edition.key)
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
        if (value.optInt("version") !in 1..2) return null
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
            excerptId = value.optString("excerptId"),
            edition = CardEdition.fromKey(value.optString("edition")),
        )
    }.getOrNull()
}


/** The preview and PNG share one layout measured independently of the screen viewport. */
private fun renderDailyCard(record: DailyCardRecord, artwork: Bitmap): Bitmap {
    val width = 960
    val inset = 62f
    val columnWidth = (width - inset * 2).toInt()
    val paper = AppColors.Paper.toArgb()
    val ink = AppColors.PaperInk.toArgb()
    val muted = AppColors.PaperMuted.toArgb()
    val date = LocalDate.parse(record.date)
    val excerpt = legacyExcerpt(record.quote)
    val serif = Typeface.create("serif", Typeface.NORMAL)
    val sans = Typeface.create("sans-serif", Typeface.NORMAL)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun paragraph(text: String, size: Float, font: Typeface, color: Int): StaticLayout {
        val style = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = font
            this.color = color
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, style, columnWidth)
            .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
            .setLineSpacing(9f, 1f)
            .build()
    }

    val quote = paragraph(excerpt.text, 62f, serif, ink)
    val attribution = paragraph(excerpt.source, 32f, sans, muted)
    val artworkTop = 285
    val artworkHeight = 500
    val quoteTop = artworkTop + artworkHeight + 54
    val attributionTop = quoteTop + quote.height + 14
    val ruleY = attributionTop + attribution.height + 55f
    val bitmap = Bitmap.createBitmap(width, (ruleY + 146).toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(paper)

    fun text(value: String, x: Float, y: Float, size: Float, font: Typeface, color: Int, end: Boolean = false) {
        paint.apply {
            textAlign = if (end) Paint.Align.RIGHT else Paint.Align.LEFT
            textSize = size
            typeface = font
            this.color = color
        }
        canvas.drawText(value, x, y, paint)
    }

    fun drawParagraph(layout: StaticLayout, y: Int) {
        canvas.save()
        canvas.translate(inset, y.toFloat())
        layout.draw(canvas)
        canvas.restore()
    }

    text(date.dayOfMonth.toString().padStart(2, '0'), inset, 223f, 216f, serif, ink)
    val right = width - inset
    text("${date.year} 年 ${date.monthValue} 月", right, 145f, 37f, sans, ink, end = true)
    text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.CHINA),
        right, 201f, 31f, sans, muted, end = true)

    val cropHeight = (artwork.width.toFloat() * artworkHeight / columnWidth).toInt()
    val cropWidth = (artwork.height.toFloat() * columnWidth / artworkHeight).toInt()
    val source = if (cropHeight <= artwork.height) {
        val top = (artwork.height - cropHeight) / 2
        Rect(0, top, artwork.width, top + cropHeight)
    } else {
        val left = (artwork.width - cropWidth) / 2
        Rect(left, 0, left + cropWidth, artwork.height)
    }
    canvas.drawBitmap(artwork, source,
        Rect(inset.toInt(), artworkTop, right.toInt(), artworkTop + artworkHeight), paint)
    drawParagraph(quote, quoteTop)
    drawParagraph(attribution, attributionTop)
    paint.color = AppColors.PaperRule.toArgb()
    paint.strokeWidth = 2f
    canvas.drawLine(inset, ruleY, right, ruleY, paint)
    text("完成 ${record.completed} 项  ·  专注 ${record.focusMinutes} 分钟", inset, ruleY + 74f, 32f, sans, ink)
    text("向前", right, ruleY + 74f, 32f, serif, muted, end = true)
    return bitmap
}

@Composable
private fun DailyCardDialog(record: DailyCardRecord, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var saveStatus by remember { mutableStateOf("") }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) scope.launch {
            val result = runCatching {
                val bitmap = layer.toImageBitmap().asAndroidBitmap()
                withContext(Dispatchers.IO) {
                    val stream = checkNotNull(context.contentResolver.openOutputStream(uri))
                    stream.use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                }
            }
            saveStatus = if (result.isSuccess) "卡片已保存" else "保存失败，请重试"
            if (result.isSuccess) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, color = AppColors.Surface) {
            Column(Modifier.fillMaxWidth().padding(AppSpace.Small)) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    val capture = Modifier.fillMaxWidth().drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    }
                    if (record.edition == CardEdition.Legacy) {
                        val bitmap = remember(record) {
                            val art = BitmapFactory.decodeResource(context.resources, record.edition.artwork)
                            try { renderDailyCard(record, art) } finally { art.recycle() }
                        }
                        Image(bitmap.asImageBitmap(), "${record.date}，${record.quote}", capture)
                    } else {
                        DailyEditorialCard(record, capture)
                    }
                }
                if (saveStatus.isNotEmpty()) Text(saveStatus, Modifier.padding(horizontal = AppSpace.Medium))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                    TextButton(onClick = { save.launch("向前-${record.date}.png") }) { Text("保存为图片") }
                }
            }
        }
    }
}

@Composable
private fun DailyEditorialCard(record: DailyCardRecord, modifier: Modifier = Modifier) {
    val date = LocalDate.parse(record.date)
    val excerpt = DailyExcerpts.find(record.excerptId)
    val sky = record.edition == CardEdition.Sky
    val artwork = painterResource(record.edition.artwork)
    val weekday = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.CHINA)
    val attribution = excerpt?.let {
        "${it.author} · ${it.work}" + if (it.translated) " · 向前意译" else ""
    }.orEmpty()
    BoxWithConstraints(modifier.background(AppColors.Paper)) {
        val width = maxWidth
        val serif = FontFamily.Serif
        val ink = AppColors.PaperInk
        Column(Modifier.fillMaxWidth().padding(horizontal = width * .074f)) {
            Spacer(Modifier.height(width * .064f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(
                    date.format(DateTimeFormatter.ofPattern(if (sky) "MM/dd" else "dd")),
                    color = ink, fontFamily = serif, fontStyle = if (sky) FontStyle.Italic else FontStyle.Normal,
                    fontSize = (width.value * if (sky) .145f else .208f).sp,
                    lineHeight = (width.value * .215f).sp,
                )
                Text(
                    if (sky) weekday else "${date.year} 年 ${date.monthValue} 月\n$weekday",
                    color = ink, fontFamily = serif, fontSize = (width.value * .038f).sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
            Spacer(Modifier.height(width * if (sky) .095f else .038f))
            Text(
                record.quote, color = ink, fontFamily = serif,
                fontSize = (width.value * if (sky) .057f else .052f).sp,
                lineHeight = (width.value * .082f).sp,
                modifier = if (sky) Modifier.fillMaxWidth(.72f) else Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(width * .027f))
            Text(attribution, color = ink, fontFamily = serif, fontSize = (width.value * .029f).sp)
            Spacer(Modifier.height(width * if (sky) .60f else .054f))
            if (!sky) {
                Image(artwork, null, Modifier.fillMaxWidth().aspectRatio(1.38f),
                    alignment = Alignment.BottomCenter, contentScale = ContentScale.Crop)
                Spacer(Modifier.height(width * .04f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom) {
                Text("完成 ${record.completed} 项 · 专注 ${record.focusMinutes} 分钟",
                    color = ink, fontFamily = serif, fontSize = (width.value * .034f).sp,
                    modifier = Modifier.weight(1f))
                Text("向前", color = ink, fontFamily = serif, fontSize = (width.value * .038f).sp)
            }
            Spacer(Modifier.height(width * .063f))
        }
        // Render the original full-bleed artwork below the measured text layers.
        if (sky) {
            Image(artwork, null, Modifier.matchParentSize().zIndex(-1f), contentScale = ContentScale.FillBounds)
        }
    }
}

@Composable
internal fun DailyCardArchive(cards: List<DailyCardRecord>) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpace.Small)) {
        SectionHeading("每日纪念")
        cards.forEach { card -> DailyCardReveal(card) }
    }
}

@Composable
internal fun DailyCardReveal(record: DailyCardRecord) {
    var open by remember(record.date) { mutableStateOf(false) }
    val day = remember(record.date) {
        LocalDate.parse(record.date).format(DateTimeFormatter.ofPattern("M月d日 · E", Locale.CHINA))
    }
    val reveal = remember(record.date) { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(visibleState = reveal, enter = fadeIn(tween(motionMillis(300)))) {
    Surface(
        onClick = { open = true },
        color = AppColors.Paper,
        contentColor = AppColors.PaperInk,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(AppSpace.Medium), horizontalArrangement = Arrangement.spacedBy(AppSpace.Medium),
            verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(record.edition.artwork), contentDescription = null,
                modifier = Modifier.size(width = 62.dp, height = 48.dp), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpace.Tiny)) {
                Text("每日纪念", style = MaterialTheme.typography.titleMedium)
                Text(day, style = MaterialTheme.typography.labelMedium, color = AppColors.PaperMuted)
            }
            Text("查看", style = MaterialTheme.typography.labelLarge)
        }
    }
    }
    if (open) DailyCardDialog(record) { open = false }
}
