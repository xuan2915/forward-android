package com.focus.prototype

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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
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
    // Keep persisted keys so older cards retain their edition after the artwork correction.
    Legacy("legacy-v1", R.drawable.daily_blue_window),
    Sky("sky-meadow-v1", R.drawable.daily_rain_harbor),
    Paper("paper-terrace-v1", R.drawable.daily_blue_window);

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
                    stream.use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
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
                    DailyEditorialCard(record, capture)
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
    val harbor = record.edition == CardEdition.Sky
    val excerpt = DailyExcerpts.find(record.excerptId)
    val attribution = excerpt?.let {
        "${it.author} · ${it.work}" + if (it.translated) " · 向前意译" else ""
    } ?: legacyExcerpt(record.quote).source
    val weekday = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.CHINA)
    val artwork = painterResource(record.edition.artwork)
    val ink = AppColors.PaperInk

    BoxWithConstraints(modifier.background(AppColors.Paper)) {
        val width = maxWidth
        val inset = width * .085f
        val serif = FontFamily.Serif
        val daySize = (width.value * .16f).sp
        val detailSize = (width.value * .037f).sp
        val quoteSize = (width.value * if (record.quote.length > 34) .062f else .073f).sp
        val month = "${date.year} 年 ${date.monthValue} 月"

        Column(Modifier.fillMaxWidth()) {
            if (harbor) {
                Image(artwork, null, Modifier.fillMaxWidth().aspectRatio(1.50f),
                    contentScale = ContentScale.Crop)
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = inset)) {
                Spacer(Modifier.height(width * if (harbor) .047f else .073f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(month, color = AppColors.PaperMuted, fontSize = detailSize, fontFamily = serif)
                        Text(weekday, color = AppColors.PaperMuted, fontSize = detailSize, fontFamily = serif)
                    }
                    Text(date.dayOfMonth.toString().padStart(2, '0'), color = ink,
                        fontFamily = serif, fontSize = daySize, lineHeight = daySize * 1.05f)
                }
                Spacer(Modifier.height(width * .045f))
                if (!harbor) {
                    Image(artwork, null,
                        Modifier.fillMaxWidth(.89f).align(Alignment.End).aspectRatio(1.34f),
                        contentScale = ContentScale.Crop)
                    Spacer(Modifier.height(width * .072f))
                }
                Text(record.quote, color = ink, fontFamily = serif, fontSize = quoteSize,
                    lineHeight = quoteSize * 1.46f,
                    modifier = if (harbor) Modifier.fillMaxWidth(.94f) else Modifier.fillMaxWidth())
                Spacer(Modifier.height(width * .044f))
                Text(attribution, color = AppColors.PaperMuted, fontFamily = serif, fontSize = detailSize,
                    lineHeight = detailSize * 1.4f)
                Spacer(Modifier.height(width * .072f))
                Spacer(Modifier.fillMaxWidth().height(1.dp).background(AppColors.PaperRule))
                Spacer(Modifier.height(width * .04f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom) {
                    Text("完成 ${record.completed} 项 · 专注 ${record.focusMinutes} 分钟",
                        color = AppColors.PaperMuted, fontSize = detailSize, modifier = Modifier.weight(1f))
                    Text("向前", color = ink, fontFamily = serif, fontSize = detailSize)
                }
                Spacer(Modifier.height(width * .061f))
            }
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
