package com.focus.prototype

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

internal data class BackupPreview(
    val formatVersion: Int,
    val createdAt: Long,
    val appVersion: String,
    val databaseVersion: Int,
    val integrity: String,
    val tasks: Int,
    val completedTasks: Int,
    val days: Int,
    val rewardRecords: Int,
    val attachments: Int,
    val focusRecords: Int,
    val sleepRecords: Int,
    val xp: Int,
)

object Backup {
    private const val FORMAT = "xiangqian-local-backup"
    private const val CURRENT_VERSION = 5
    private val tables = listOf("contracts", "tasks", "task_moves", "ledger", "reviews", "audit", "settings", "templates", "attachments", "night_violations", "emergency_unlocks", "xp_ledger", "achievement_unlocks", "achievement_reconcile_logs")
    private val requiredTables = listOf("contracts", "tasks", "ledger", "reviews", "audit", "settings")

    private data class Asset(val key: String, val name: String, val bytes: ByteArray)
    private data class Parsed(
        val version: Int,
        val createdAt: Long,
        val appVersion: String,
        val databaseVersion: Int,
        val verified: Boolean,
        val data: JSONObject,
        val selected: Set<String>,
        val assets: List<Asset>,
    )

    fun export(context: Context, db: Database): String {
        db.checkpoint()
        val sql = db.writableDatabase
        val data = JSONObject()
        sql.beginTransaction()
        try {
            for (table in tables) {
                val rows = JSONArray()
                sql.rawQuery("SELECT * FROM $table", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val row = JSONObject()
                        cursor.columnNames.forEachIndexed { index, key ->
                            row.put(key, if (cursor.isNull(index)) JSONObject.NULL else cursor.getString(index))
                        }
                        rows.put(row)
                    }
                }
                data.put(table, rows)
            }
            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }

        val assets = JSONArray()
        val paths = linkedSetOf<String>()
        data.getJSONArray("attachments").let { rows ->
            for (index in 0 until rows.length()) rows.getJSONObject(index).optString("path").takeIf(String::isNotBlank)?.let(paths::add)
        }
        data.getJSONArray("tasks").let { rows ->
            for (index in 0 until rows.length()) rows.getJSONObject(index).optString("evidence").takeIf { it.isNotBlank() && !it.startsWith("proof-submitted:") }?.let(paths::add)
        }
        for (path in paths) {
            val file = File(path)
            require(file.isFile) { "找不到附件文件：${file.name}" }
            val bytes = file.readBytes()
            assets.put(JSONObject()
                .put("key", path)
                .put("name", file.name)
                .put("length", bytes.size)
                .put("sha256", sha256(bytes))
                .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP)))
        }

        val payload = JSONObject()
            .put("data", data)
            .put("selected", JSONArray(Store(context).selected.toList()))
            .put("assets", assets)
        val payloadText = payload.toString()
        val createdAt = System.currentTimeMillis()
        val databaseVersion = sql.version
        val taskRows = data.getJSONArray("tasks")
        val completedCount = (0 until taskRows.length()).count { taskRows.getJSONObject(it).optInt("done") != 0 }
        val movedRows = data.optJSONArray("task_moves") ?: JSONArray()
        val movedIds = (0 until movedRows.length()).map { movedRows.getJSONObject(it).optString("source_id") }.toSet()
        val xpCount = (0 until taskRows.length()).count { taskRows.getJSONObject(it).optInt("done") != 0 && taskRows.getJSONObject(it).optString("id") !in movedIds }
        val counts = JSONObject().put("tasks", taskRows.length()).put("completed", completedCount)
            .put("xp", xpCount * 100).put("focusRecords", (0 until taskRows.length()).count { taskRows.getJSONObject(it).optLong("elapsed") > 0 })
            .put("rewardRecords", data.getJSONArray("ledger").length()).put("attachments", assets.length())
        return JSONObject()
            .put("format", FORMAT)
            .put("version", CURRENT_VERSION)
            .put("createdAt", createdAt)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("databaseVersion", databaseVersion)
            .put("counts", counts)
            .put("payload", payloadText)
            .put("sha256", sha256(payloadText.toByteArray(Charsets.UTF_8)))
            .toString()
    }

    internal fun inspect(text: String): BackupPreview {
        val parsed = parse(text)
        val tasks = parsed.data.optJSONArray("tasks") ?: JSONArray()
        val moved = parsed.data.optJSONArray("task_moves") ?: JSONArray()
        val movedSources = (0 until moved.length()).map { moved.getJSONObject(it).optString("source_id") }.toSet()
        val completed = (0 until tasks.length()).count { tasks.getJSONObject(it).optInt("done") != 0 }
        val xpTasks = (0 until tasks.length()).count { tasks.getJSONObject(it).optInt("done") != 0 && tasks.getJSONObject(it).optString("id") !in movedSources }
        val settings = parsed.data.optJSONArray("settings") ?: JSONArray()
        val sleepRecords = (0 until settings.length()).count { settings.getJSONObject(it).optString("key").startsWith("sleep_record_") }
        return BackupPreview(
            formatVersion = parsed.version,
            createdAt = parsed.createdAt,
            appVersion = parsed.appVersion,
            databaseVersion = parsed.databaseVersion,
            integrity = if (parsed.verified) "完整性校验通过" else "旧版备份，未包含完整性校验",
            tasks = tasks.length(),
            completedTasks = completed,
            days = parsed.data.optJSONArray("contracts")?.length() ?: 0,
            rewardRecords = parsed.data.optJSONArray("ledger")?.length() ?: 0,
            attachments = parsed.assets.size,
            focusRecords = (0 until tasks.length()).count { tasks.getJSONObject(it).optLong("elapsed") > 0 },
            sleepRecords = sleepRecords,
            xp = xpTasks * 100,
        )
    }

    /** Validates first, stages attachment files, then replaces all database tables in one transaction. */
    fun restore(context: Context, db: Database, text: String) {
        val parsed = parse(text)
        require(parsed.databaseVersion <= db.writableDatabase.version) {
            "此备份由较新版本创建，请先升级后再恢复"
        }
        validateRows(parsed.data, db.writableDatabase)
        val references = referencedPaths(parsed.data)
        val assetByKey = parsed.assets.associateBy { it.key }
        val legacyByName = if (parsed.version < CURRENT_VERSION) parsed.assets.groupBy { it.name } else emptyMap()
        val resolved = linkedMapOf<String, Asset>()
        for (path in references) {
            val asset = assetByKey[path] ?: legacyByName[File(path).name]?.singleOrNull()
                ?: error("备份缺少附件：${File(path).name}")
            resolved[path] = asset
        }

        val stagedFiles = mutableListOf<File>()
        var committed = false
        try {
            val restoredPaths = linkedMapOf<String, String>()
            for ((path, asset) in resolved) {
                val extension = asset.name.substringAfterLast('.', "bin").filter(Char::isLetterOrDigit).take(12).ifBlank { "bin" }
                val target = File(context.filesDir, "evidence/${UUID.randomUUID()}.$extension")
                target.parentFile!!.mkdirs()
                target.writeBytes(asset.bytes)
                stagedFiles.add(target)
                restoredPaths[path] = target.absolutePath
            }

            val sql = db.writableDatabase
            sql.beginTransaction()
            try {
                tables.asReversed().forEach { sql.delete(it, null, null) }
                for (table in tables) {
                    val rows = parsed.data.optJSONArray(table) ?: JSONArray()
                    for (index in 0 until rows.length()) {
                        val row = rows.getJSONObject(index)
                        val values = ContentValues()
                        val columns = tableColumns(sql, table)
                        row.keys().forEach { key ->
                            require(key in columns) { "备份包含当前版本不支持的字段：$table.$key" }
                            if (row.isNull(key)) values.putNull(key) else values.put(key, row.getString(key))
                        }
                        when (table) {
                            "tasks" -> {
                                if (!row.has("type_known")) {
                                    val note = row.optString("checklist")
                                    if (note.contains("根据复盘") && note.contains("重建")) {
                                        values.put("history_source", if(note.contains("截图")) "每日复盘、截图" else "每日复盘")
                                        values.put("history_confidence", "部分确认")
                                        if (row.optInt("required")==0 && note.contains("任务类型") && note.contains("未知")) values.put("type_known",0)
                                        if (row.optLong("elapsed")==0L && note.contains("时长") && note.contains("未知")) values.putNull("elapsed")
                                    }
                                }
                                val oldPath = values.getAsString("evidence").orEmpty()
                                values.put("evidence", if (oldPath.isBlank() || oldPath.startsWith("proof-submitted:")) oldPath else restoredPaths[oldPath] ?: error("任务证明文件缺失：${File(oldPath).name}"))
                                values.put("running_since", 0L)
                                values.put("boot_time", 0L)
                            }
                            "attachments" -> {
                                val oldPath = values.getAsString("path").orEmpty()
                                values.put("path", restoredPaths[oldPath] ?: error("附件文件缺失：${File(oldPath).name}"))
                            }
                            "night_violations" -> if (!row.has("reward_kind")) {
                                val minutes = values.getAsInteger("reserved_minutes") ?: 0
                                values.put("reward_kind", "video")
                                values.put("reserved_amount", minutes * 60_000L)
                            }
                            "settings" -> {
                                val key = values.getAsString("key").orEmpty()
                                if (key.matches(Regex("night_pending_\\d{4}-\\d{2}-\\d{2}"))) {
                                    values.put("key", key.replaceFirst("night_pending_", "night_pending_video_"))
                                } else if (key.matches(Regex("night_reserved_\\d{4}-\\d{2}-\\d{2}"))) {
                                    values.put("key", key.replaceFirst("night_reserved_", "night_reserved_video_"))
                                }
                            }
                        }
                        sql.insertOrThrow(table, null, values)
                    }
                }
                if (!parsed.data.has("templates")) db.seedTemplates(sql)
                if (!parsed.data.has("attachments")) db.seedAttachments(sql)
                if (!parsed.data.has("xp_ledger")) XpLedger.seedFromExistingTasks(sql)
                if (!parsed.data.has("achievement_unlocks")) AchievementEngine.initSchema(sql)
                sql.setTransactionSuccessful()
            } finally {
                sql.endTransaction()
            }
            Store(context).selected = parsed.selected
            committed = true
        } finally {
            if (!committed) stagedFiles.forEach(File::delete)
        }
    }

    private fun parse(text: String): Parsed {
        val root = JSONObject(text)
        val version = root.getInt("version")
        if (root.optString("format") == FORMAT) {
            require(version <= CURRENT_VERSION) { "此备份由较新版本创建，请先升级后再恢复" }
            require(version == CURRENT_VERSION) { "无法识别备份格式" }
            val payloadText = root.getString("payload")
            require(sha256(payloadText.toByteArray(Charsets.UTF_8)) == root.getString("sha256")) { "备份校验失败，文件可能已损坏或被修改" }
            val payload = JSONObject(payloadText)
            val assets = parseAssets(payload.optJSONArray("assets") ?: JSONArray(), "key")
            val data = payload.getJSONObject("data")
            validateData(data)
            validateAssetReferences(data, assets, legacy = false)
            return Parsed(
                version = version,
                createdAt = root.getLong("createdAt"),
                appVersion = root.optString("appVersion", "未知"),
                databaseVersion = root.getInt("databaseVersion"),
                verified = true,
                data = data,
                selected = readSelected(payload.optJSONArray("selected")),
                assets = assets,
            )
        }

        require(version in 1 until CURRENT_VERSION) { "不支持此备份版本" }
        val data = root.getJSONObject("data")
        validateData(data)
        val legacy = root.optJSONObject("files") ?: JSONObject()
        val assets = mutableListOf<Asset>()
        legacy.keys().forEach { name ->
            require(name == File(name).name && name != "." && name != "..") { "备份附件名称无效" }
            val bytes = Base64.decode(legacy.getString(name), Base64.DEFAULT)
            assets.add(Asset(name, name, bytes))
        }
        validateAssetReferences(data, assets, legacy = true)
        return Parsed(
            version = version,
            createdAt = 0L,
            appVersion = "旧版",
            databaseVersion = 0,
            verified = false,
            data = data,
            selected = readSelected(root.optJSONArray("selected")),
            assets = assets,
        )
    }

    private fun parseAssets(array: JSONArray, keyName: String): List<Asset> = buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val key = item.getString(keyName)
            val name = item.getString("name")
            require(key.isNotBlank() && name == File(name).name && name != "." && name != "..") { "备份附件信息无效" }
            val bytes = Base64.decode(item.getString("content"), Base64.DEFAULT)
            val digest = sha256(bytes)
            require(digest == item.getString("sha256") && bytes.size.toLong() == item.getLong("length")) { "附件校验失败：$name" }
            add(Asset(key, name, bytes))
        }
        require(map { it.key }.toSet().size == size) { "备份中有重复的附件记录" }
    }

    private fun validateData(data: JSONObject) {
        requiredTables.forEach { require(data.optJSONArray(it) != null) { "备份缺少数据表：$it" } }
        tables.forEach { table ->
            data.optJSONArray(table)?.let { rows ->
                for (index in 0 until rows.length()) require(rows.get(index) is JSONObject) { "备份数据格式无效：$table" }
            }
        }
    }

    private fun validateRows(data: JSONObject, sql: SQLiteDatabase) {
        validateData(data)
        for (table in tables) {
            val rows = data.optJSONArray(table) ?: continue
            val columns = tableColumns(sql, table)
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                row.keys().forEach { key -> require(key in columns) { "备份包含当前版本不支持的字段：$table.$key" } }
            }
        }
    }

    private fun tableColumns(sql: SQLiteDatabase, table: String): Set<String> = buildSet {
        sql.rawQuery("PRAGMA table_info($table)", null).use { while (it.moveToNext()) add(it.getString(1)) }
    }

    private fun referencedPaths(data: JSONObject): Set<String> = buildSet {
        listOf("attachments" to "path", "tasks" to "evidence").forEach { (table, column) ->
            data.optJSONArray(table)?.let { rows ->
                for (index in 0 until rows.length()) rows.getJSONObject(index).optString(column).takeIf { it.isNotBlank() && !(table=="tasks" && it.startsWith("proof-submitted:")) }?.let(::add)
            }
        }
    }

    private fun validateAssetReferences(data: JSONObject, assets: List<Asset>, legacy: Boolean) {
        val byKey = assets.mapTo(hashSetOf()) { it.key }
        val byName = assets.groupBy { it.name }
        referencedPaths(data).forEach { path ->
            val exists = path in byKey || legacy && byName[File(path).name]?.size == 1
            require(exists) { "备份缺少附件：${File(path).name}" }
        }
    }

    private fun readSelected(selected: JSONArray?): Set<String> = selected?.let { array ->
        (0 until array.length()).map(array::getString).toSet()
    } ?: emptySet()

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
