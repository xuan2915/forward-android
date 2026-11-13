package com.focus.prototype

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.io.File
import android.graphics.BitmapFactory
import android.content.Intent
import android.net.Uri
import android.content.Context
import java.util.UUID

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
    val saved: Boolean = true,
    val revealSeen: Boolean = true,
    val selectedTaskIds: List<String> = emptyList(),
    val note: String = "",
    val visual: String = "illustration",
    val photoId: String = "",
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

internal data class DailyCardComposition(val excerpt: DailyExcerpt, val edition: CardEdition)

internal fun dailyCardComposition(date: String): DailyCardComposition {
    val dayIndex = LocalDate.parse(date).toEpochDay()
    val excerpt = DailyExcerpts.entries[Math.floorMod(dayIndex, DailyExcerpts.entries.size.toLong()).toInt()]
    val edition = when {
        excerpt.themes.any { it in setOf("自我认识", "判断", "思想", "尊严", "选择") } -> CardEdition.Paper
        excerpt.themes.any { it in setOf("时间", "接受", "积累", "日常") } -> CardEdition.Sky
        else -> if (dayIndex % 2L == 0L) CardEdition.Paper else CardEdition.Sky
    }
    return DailyCardComposition(excerpt, edition)
}

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
        val composition = dailyCardComposition(date)
        val chosenIds = old?.takeIf { it.saved }?.selectedTaskIds ?: tasks.map { it.id }
        val chosenTitles = old?.takeIf { it.saved }?.taskTitles ?: tasks.filter { it.id in chosenIds }.map { it.title }
        val record = DailyCardRecord(
            date = date,
            quote = old?.quote?.takeIf(String::isNotBlank) ?: composition.excerpt.chinese,
            completed = tasks.count { it.done },
            total = tasks.size,
            focusMinutes = tasks.sumOf { it.elapsed }.coerceAtLeast(0L) / 60_000L,
            taskTitles = chosenTitles,
            unlockedAt = old?.unlockedAt?.takeIf { it > 0L } ?: System.currentTimeMillis(),
            valid = true,
            excerptId = old?.excerptId?.takeIf(String::isNotBlank) ?: composition.excerpt.id,
            edition = old?.takeIf { it.saved }?.edition ?: composition.edition,
            saved = true,
            revealSeen = old?.revealSeen ?: false,
            selectedTaskIds = chosenIds,
            note = old?.note.orEmpty(),
            visual = old?.takeIf { it.saved }?.visual ?: "illustration",
            photoId = old?.takeIf { it.saved }?.photoId.orEmpty(),
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
            while (cursor.moveToNext()) decode(cursor.getString(0))?.takeIf { it.valid && it.saved }?.let(::add)
        }
    }.sortedByDescending { it.date }

    fun load(db: Database, date: String): DailyCardRecord? = db.setting(PREFIX + date).takeIf(String::isNotBlank)?.let(::decode)

    fun markRevealSeen(db: Database, date: String) {
        val record = load(db, date) ?: return
        if (!record.revealSeen) db.setSetting(PREFIX + date, encode(record.copy(revealSeen = true)))
    }

    fun save(db: Database, record: DailyCardRecord) {
        require(record.valid && dailyCardQualifies(db.tasks(record.date)))
        val previousPhoto = load(db, record.date)?.photoId.orEmpty()
        db.setSetting(PREFIX + record.date, encode(record.copy(saved = true, unlockedAt = record.unlockedAt.takeIf { it > 0 } ?: System.currentTimeMillis())))
        if (previousPhoto.isNotBlank() && previousPhoto != record.photoId) {
            val oldPath = photoPath(db, previousPhoto)
            db.writableDatabase.delete("attachments", "id=? AND task_id=?", arrayOf(previousPhoto, "daily-card:${record.date}"))
            oldPath?.let { File(it).delete() }
        }
    }

    fun photoPath(db: Database, photoId: String): String? = db.readableDatabase.rawQuery(
        "SELECT path FROM attachments WHERE id=?", arrayOf(photoId)
    ).use { if (it.moveToFirst()) it.getString(0).takeIf { path -> File(path).isFile } else null }

    fun snapshotPhoto(context: Context, db: Database, date: String, source: String): String {
        val original = File(source)
        require(original.isFile)
        val id = UUID.randomUUID().toString()
        val directory = File(context.filesDir, "daily-card-media").apply { mkdirs() }
        val dest = File(directory, "$id.${original.extension.ifBlank { "jpg" }}")
        original.copyTo(dest)
        try {
            db.writableDatabase.insertOrThrow("attachments", null, android.content.ContentValues().apply {
                put("id", id); put("task_id", "daily-card:$date"); put("path", dest.absolutePath); put("created", System.currentTimeMillis())
            })
        } catch (error: Exception) { dest.delete(); throw error }
        return id
    }

    private fun encode(record: DailyCardRecord): String = JSONObject()
        .put("version", 4)
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
        .put("saved", record.saved)
        .put("revealSeen", record.revealSeen)
        .put("selectedTaskIds", JSONArray(record.selectedTaskIds))
        .put("note", record.note)
        .put("visual", record.visual)
        .put("photoId", record.photoId)
        .toString()

    private fun decode(json: String): DailyCardRecord? = runCatching {
        val value = JSONObject(json)
        if (value.optInt("version") !in 1..4) return null
        val titles = value.optJSONArray("taskTitles") ?: JSONArray()
        val selected = value.optJSONArray("selectedTaskIds") ?: JSONArray()
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
            saved = value.optBoolean("saved", true),
            revealSeen = value.optBoolean("revealSeen", true),
            selectedTaskIds = (0 until selected.length()).mapNotNull { selected.optString(it).takeIf(String::isNotBlank) },
            note = value.optString("note"),
            visual = value.optString("visual", "illustration"),
            photoId = value.optString("photoId"),
        )
    }.getOrNull()
}


@Composable
internal fun DailyCardDialog(record: DailyCardRecord, db: Database, onEdit: () -> Unit, onDismiss: () -> Unit) {
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
        androidx.compose.animation.AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(340)) + scaleIn(animationSpec = tween(420), initialScale = .96f),
        ) {
          Surface(shape = MaterialTheme.shapes.medium, color = AppColors.Surface) {
            Column(Modifier.fillMaxWidth().padding(AppSpace.Small)) {
                if (!record.revealSeen) Text("今日纪念已收录", Modifier.padding(horizontal = AppSpace.Medium, vertical = AppSpace.Small), style = MaterialTheme.typography.titleMedium)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    val capture = Modifier.fillMaxWidth().drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    }
                    DailyEditorialCard(record, db, capture)
                }
                if (saveStatus.isNotEmpty()) Text(saveStatus, Modifier.padding(horizontal = AppSpace.Medium))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                    TextButton(onClick = { onDismiss(); onEdit() }) { Text("编辑") }
                    TextButton(onClick = { save.launch("向前-${record.date}.png") }) { Text("保存为图片") }
                }
            }
          }
        }
    }
}

@Composable
private fun DailyEditorialCard(record: DailyCardRecord, db: Database, modifier: Modifier = Modifier, previewPhotoPath: String? = null) {
    val date = LocalDate.parse(record.date)
    val harbor = record.edition == CardEdition.Sky
    val excerpt = DailyExcerpts.find(record.excerptId)
    val attribution = excerpt?.attribution ?: legacyExcerpt(record.quote).source
    val weekday = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.CHINA)
    val artwork = painterResource(record.edition.artwork)
    val photo = remember(record.photoId, previewPhotoPath) {
        (previewPhotoPath ?: DailyCards.photoPath(db, record.photoId))?.let { path ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val sample = (maxOf(bounds.outWidth, bounds.outHeight) / 1600).coerceAtLeast(1)
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }
    }
    val ink = AppColors.PaperInk

    BoxWithConstraints(modifier.background(AppColors.Paper)) {
        val width = maxWidth
        val inset = width * .085f
        val serif = FontFamily.Serif
        val refinedSans = FontFamily(Font(R.font.figtree))
        val daySize = (width.value * .16f).sp
        val detailSize = (width.value * .037f).sp
        val sourceSize = (width.value * .032f).sp
        val quoteSize = (width.value * if (record.quote.length > 34) .062f else .073f).sp
        val month = "${date.year} 年 ${date.monthValue} 月"

        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = inset)) {
                Spacer(Modifier.height(width * .073f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(month, color = AppColors.PaperMuted, fontSize = detailSize, fontFamily = refinedSans,
                            fontWeight = FontWeight.Medium, letterSpacing = .18.sp, lineHeight = detailSize * 1.28f)
                        Text(weekday, color = AppColors.PaperMuted, fontSize = sourceSize, fontFamily = refinedSans,
                            letterSpacing = .22.sp, lineHeight = sourceSize * 1.32f)
                    }
                    Text(date.dayOfMonth.toString().padStart(2, '0'), color = ink,
                        fontFamily = serif, fontWeight = FontWeight.Light, fontSize = daySize,
                        letterSpacing = (-daySize.value * .045f).sp, lineHeight = daySize * 1.02f)
                }
                Spacer(Modifier.height(width * .045f))
                if (record.quote.isNotBlank()) {
                    Text(record.quote, color = ink, fontFamily = serif, fontSize = quoteSize,
                        fontWeight = FontWeight.Normal, letterSpacing = .06.sp,
                        lineHeight = quoteSize * 1.39f, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(width * .044f))
                    Text(attribution, color = AppColors.PaperMuted, fontFamily = refinedSans, fontSize = sourceSize,
                        fontWeight = FontWeight.Medium, letterSpacing = .18.sp, lineHeight = sourceSize * 1.38f)
                }
                if (record.selectedTaskIds.isNotEmpty()) {
                    Spacer(Modifier.height(width * .055f))
                    record.taskTitles.take(3).forEach { Text(it, color = ink, fontFamily = refinedSans, fontSize = detailSize * 1.08f, lineHeight = detailSize * 1.48f) }
                }
                if (record.note.isNotBlank()) {
                    Spacer(Modifier.height(width * .045f))
                    Text(record.note, color = ink, fontFamily = refinedSans, fontSize = detailSize * 1.08f, lineHeight = detailSize * 1.5f)
                }
                Spacer(Modifier.height(width * .072f))
                when (record.visual) {
                    "photo" -> if (photo != null) Image(photo, null, Modifier.fillMaxWidth().aspectRatio(1.34f), contentScale = ContentScale.Crop)
                    "illustration" -> Image(artwork, null, Modifier.fillMaxWidth().aspectRatio(1.34f), contentScale = ContentScale.Crop)
                    else -> Spacer(Modifier.fillMaxWidth().height(width * .25f))
                }
                Spacer(Modifier.height(width * .072f))
                Spacer(Modifier.fillMaxWidth().height(1.dp).background(AppColors.PaperRule))
                Spacer(Modifier.height(width * .04f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom) {
                    Text("完成 ${record.completed} 项 · 专注 ${record.focusMinutes} 分钟",
                        color = AppColors.PaperMuted, fontFamily = refinedSans, fontWeight = FontWeight.Medium,
                        fontSize = sourceSize, letterSpacing = .12.sp, modifier = Modifier.weight(1f))
                    Text("向前", color = ink, fontFamily = serif, fontSize = detailSize)
                }
                Spacer(Modifier.height(width * .061f))
            }
        }
    }
}

@Composable
internal fun DailyCardEditor(db: Database, date: String, tasks: List<Task>, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val source = remember(date) { DailyCards.load(db, date) ?: DailyCards.reconcile(db, date, tasks) } ?: return
    var selectedIds by remember(date) { mutableStateOf(source.selectedTaskIds.toSet()) }
    var note by remember(date) { mutableStateOf(source.note) }
    var excerptId by remember(date) { mutableStateOf(source.excerptId.ifBlank { if (source.quote.isNotBlank()) "__legacy__" else "" }) }
    var quotesExpanded by remember(date) { mutableStateOf(false) }
    var visual by remember(date) { mutableStateOf(source.visual) }
    var photoPath by remember(date) { mutableStateOf(DailyCards.photoPath(db, source.photoId).orEmpty()) }
    var preview by remember(date) { mutableStateOf(false) }
    var error by remember(date) { mutableStateOf("") }
    val excerpt = DailyExcerpts.find(excerptId)
    val draft = source.copy(
        quote = if (excerptId == "__legacy__") source.quote else excerpt?.chinese.orEmpty(),
        excerptId = excerptId.takeUnless { it == "__legacy__" }.orEmpty(),
        selectedTaskIds = selectedIds.toList(),
        taskTitles = tasks.filter { it.id in selectedIds }.map { it.title },
        note = note.trim(), visual = visual,
    )
    val photos = remember(tasks) { tasks.flatMap { task -> db.attachments(task).mapNotNull { (_, path) ->
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        if (options.outWidth > 0 && options.outHeight > 0 && File(path).isFile) task.title to path else null
    } } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, color = AppColors.Surface) {
            Column(Modifier.fillMaxWidth().heightIn(max = 690.dp).padding(AppSpace.Medium)) {
                Text(if (source.saved) "编辑每日纪念" else "为今天留个记录", style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppSpace.Small)) {
                    if (preview) {
                        DailyEditorialCard(draft, db, Modifier.fillMaxWidth(), photoPath.takeIf { visual == "photo" && it.isNotBlank() })
                    } else {
                        Text("选择今天要留下的任务", style = MaterialTheme.typography.titleMedium)
                        tasks.forEach { task ->
                            Row(Modifier.fillMaxWidth().clickable { selectedIds = if (task.id in selectedIds) selectedIds - task.id else selectedIds + task.id }, verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(task.id in selectedIds, onCheckedChange = { checked -> selectedIds = if (checked) selectedIds + task.id else selectedIds - task.id })
                                Column { Text(task.title, style = MaterialTheme.typography.bodyMedium); Text("已完成 · 专注 ${task.elapsed / 60000} 分钟", style = MaterialTheme.typography.labelSmall, color = AppColors.Muted) }
                            }
                        }
                        OutlinedTextField(value = note, onValueChange = { note = it.take(500) }, label = { Text("今天有什么值得留下？") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                        Text("思想内容", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { excerptId = "" }) { Text(if (excerptId.isEmpty()) "✓ 不添加引文" else "不添加引文") }
                        if (excerptId == "__legacy__") Text("原有引文 · ${source.quote}")
                        if (excerpt != null) {
                            Text("${excerpt.chinese}\n${excerpt.attribution}")
                            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(excerpt.source))) }) { Text("查看原文出处") }
                        }
                        TextButton(onClick = { quotesExpanded = !quotesExpanded }) { Text(if (quotesExpanded) "收起引文" else "选择引文") }
                        if (quotesExpanded) DailyExcerpts.entries.forEach { item ->
                            TextButton(onClick = { excerptId = item.id; quotesExpanded = false }, modifier = Modifier.fillMaxWidth()) {
                                Text("${item.chinese}\n${item.attribution}", modifier = Modifier.fillMaxWidth())
                            }
                        }
                        Text("画面", style = MaterialTheme.typography.titleMedium)
                        Row { listOf("paper" to "纸张留白", "illustration" to "主题插画", "photo" to "真实照片").forEach { (key, label) ->
                            TextButton(onClick = { visual = key }) { Text("${if (visual == key) "✓ " else ""}$label") }
                        } }
                        if (visual == "photo") {
                            if (photos.isEmpty()) Text("今天没有可用的图片附件", color = AppColors.Muted)
                            photos.forEachIndexed { index, (title, path) ->
                                TextButton(onClick = { photoPath = path }) { Text("${if (photoPath == path) "✓ " else ""}$title · 图片 ${index + 1}") }
                            }
                        }
                    }
                }
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { if (preview) preview = false else onDismiss() }) { Text(if (preview) "返回编辑" else "取消") }
                    if (!preview) TextButton(onClick = { preview = true }) { Text("预览") }
                    Button(onClick = {
                        if (visual == "photo" && photoPath.isBlank()) { error = "请先选择一张真实照片"; return@Button }
                        runCatching {
                            val id = if (visual != "photo") "" else if (photoPath == DailyCards.photoPath(db, source.photoId)) source.photoId else DailyCards.snapshotPhoto(context, db, date, photoPath)
                            DailyCards.save(db, draft.copy(photoId = id))
                        }.onSuccess { onSaved() }.onFailure { error = "保存失败，请重试" }
                    }) { Text("保存卡片") }
                }
            }
        }
    }
}

@Composable
internal fun DailyCardArchive(cards: List<DailyCardRecord>, db: Database, onEdit: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpace.Small)) {
        SectionHeading("每日纪念")
        cards.forEach { card -> DailyCardReveal(card, db, onEdit) }
    }
}

@Composable
internal fun DailyCardReveal(record: DailyCardRecord, db: Database, onEdit: (String) -> Unit) {
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
            if (record.visual == "illustration") Image(painterResource(record.edition.artwork), contentDescription = null,
                modifier = Modifier.size(width = 62.dp, height = 48.dp), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpace.Tiny)) {
                Text("每日纪念", style = MaterialTheme.typography.titleMedium)
                Text(day, style = MaterialTheme.typography.labelMedium, color = AppColors.PaperMuted)
            }
            Text("查看", style = MaterialTheme.typography.labelLarge)
        }
    }
    }
    if (open) DailyCardDialog(record, db, { onEdit(record.date) }) { open = false }
}
