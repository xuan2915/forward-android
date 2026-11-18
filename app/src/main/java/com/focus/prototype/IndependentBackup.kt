package com.focus.prototype

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Independent SAF documents; backup content and database transaction remain owned by Backup. */
internal object IndependentBackup {
    private const val PREFS = "independent_backup"
    private const val URI = "tree"
    private const val LAST = "last_success"
    private const val DAY = 24 * 60 * 60 * 1000L
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun selected(context: Context): Uri? = prefs(context).getString(URI, null)?.let(Uri::parse)
    fun lastSuccess(context: Context): Long = prefs(context).getLong(LAST, 0L)
    fun status(context: Context): String = prefs(context).getString("status", "尚未备份").orEmpty()
    fun failed(context: Context, error: Throwable) { prefs(context).edit().putString("status", "备份失败：${error.message ?: "无法访问目录"}").apply() }
    @Synchronized
    fun saveIfDue(context: Context, db: Database) {
        if (selected(context) == null || System.currentTimeMillis() - lastSuccess(context) < DAY) return
        runCatching { save(context, db) }.onFailure { failed(context, it) }
    }
    fun select(context: Context, tree: Uri) {
        context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val old = selected(context)
        prefs(context).edit().putString(URI, tree.toString()).apply {
            if (old != tree) { putLong(LAST, 0L); putString("status", "已选择位置，尚未备份") }
        }.apply()
    }
    private fun directory(context: Context, tree: Uri): Uri {
        val resolver = context.contentResolver
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(root))
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) if (cursor.getString(1) == "向前备份" && cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR)
                return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
        }
        return DocumentsContract.createDocument(resolver, root, DocumentsContract.Document.MIME_TYPE_DIR, "向前备份") ?: error("无法创建备份文件夹")
    }
    @Synchronized
    fun save(context: Context, db: Database, beforeRestore: Boolean = false): Uri {
        val tree = selected(context) ?: error("请先选择备份位置")
        val text = Backup.export(context, db)
        Backup.inspect(text)
        val compressed = ByteArrayOutputStream().also { buffer -> GZIPOutputStream(buffer).use { it.write(text.toByteArray(Charsets.UTF_8)) } }.toByteArray()
        val dir = directory(context, tree)
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS", Locale.ROOT).withZone(ZoneId.systemDefault()).format(Instant.now())
        val name = "Forward_Backup_${stamp}${if (beforeRestore) "_before_restore" else ""}.forwardbackup"
        val uri = DocumentsContract.createDocument(context.contentResolver, dir, "application/octet-stream", name) ?: error("无法创建备份文件")
        try {
            context.contentResolver.openOutputStream(uri, "w")?.use { it.write(compressed) } ?: error("无法写入备份文件")
            val saved = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("无法读取备份文件")
            require(read(saved) == text) { "备份回读不一致" }
            Backup.inspect(read(saved))
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            throw e
        }
        prefs(context).edit().putLong(LAST, System.currentTimeMillis()).putString("status", "备份已保存并验证").apply()
        // Retention failure must not turn a verified backup into a reported failure.
        runCatching { prune(context, tree, dir) }
        return uri
    }
    fun read(bytes: ByteArray): String = if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte())
        GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }
    else bytes.toString(Charsets.UTF_8) // Existing JSON backups remain readable.
    private fun prune(context: Context, tree: Uri, dir: Uri) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(dir))
        val files = mutableListOf<Pair<String, String>>()
        context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1) ?: continue
                if (name.startsWith("Forward_Backup_") && name.endsWith(".forwardbackup")) files.add(cursor.getString(0) to name)
            }
        }
        files.sortedByDescending { it.second }.drop(7).forEach { (id, _) -> DocumentsContract.deleteDocument(context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
    }
}
