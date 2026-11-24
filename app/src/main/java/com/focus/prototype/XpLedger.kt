package com.focus.prototype

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.util.UUID

enum class XpStatus {
    ACTIVE,
    REVERSED,
}

data class XpTransaction(
    val id: String,
    val day: String,
    val sourceType: String,
    val sourceId: String,
    val amount: Int,
    val status: XpStatus,
    val createdAt: Long,
    val version: Int = 1,
    val idempotencyKey: String = "",
    val metadata: String = "",
)

object XpLedger {
    const val TABLE = "xp_ledger"

    const val SOURCE_TASK = "task"
    const val SOURCE_CHALLENGE = "challenge"

    fun initSchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE (
                id TEXT PRIMARY KEY,
                day TEXT NOT NULL,
                source_type TEXT NOT NULL,
                source_id TEXT NOT NULL,
                amount INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'ACTIVE',
                created INTEGER NOT NULL,
                version INTEGER NOT NULL DEFAULT 1,
                idempotency_key TEXT NOT NULL DEFAULT '',
                metadata TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_xp_source ON $TABLE(source_type, source_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_xp_idempotency ON $TABLE(idempotency_key)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS ux_xp_source_once ON $TABLE(source_type, source_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS ux_xp_idempotency_once ON $TABLE(idempotency_key) WHERE idempotency_key <> ''")
    }

    private inline fun <T> inTransaction(db: SQLiteDatabase, block: () -> T): T {
        val ownsTransaction = !db.inTransaction()
        if (ownsTransaction) db.beginTransaction()
        try {
            val result = block()
            if (ownsTransaction) db.setTransactionSuccessful()
            return result
        } finally {
            if (ownsTransaction) db.endTransaction()
        }
    }

    /**
     * Seed or migrate existing completed tasks into xp_ledger idempotently.
     * Prevents duplication: only tasks not already present in xp_ledger are inserted.
     */
    fun seedFromExistingTasks(db: SQLiteDatabase, taskAmount: Int = 100) {
        initSchema(db)
        inTransaction(db) {
            db.execSQL(
                """
                INSERT OR IGNORE INTO $TABLE (id, day, source_type, source_id, amount, status, created, version, idempotency_key, metadata)
                SELECT
                    'xp_task_' || id,
                    day,
                    'task',
                    id,
                    $taskAmount,
                    'ACTIVE',
                    CASE WHEN completed_at > 0 THEN completed_at ELSE 0 END,
                    1,
                    'task_' || id,
                    'migrated_task'
                FROM tasks
                WHERE done = 1
                  AND origin != 'sleep_system'
                  AND id NOT IN (SELECT source_id FROM task_moves)
                """.trimIndent()
            )
        }
    }

    /**
     * Award XP idempotently.
     * If an active entry with matching idempotencyKey or (source_type, source_id) exists, returns it without creating duplicate XP.
     * If a reversed entry exists for the same source, re-activates it and updates if needed.
     */
    @Synchronized
    fun award(
        db: SQLiteDatabase,
        sourceType: String,
        sourceId: String,
        amount: Int,
        day: String,
        wallNow: Long,
        idempotencyKey: String = "",
        version: Int = 1,
        metadata: String = "",
    ): XpTransaction {
        initSchema(db)
        require(amount > 0) { "XP amount must be positive" }
        require(version > 0) { "XP version must be positive" }
        val key = idempotencyKey.ifBlank { "${sourceType}_$sourceId" }
        return inTransaction(db) {
            val existing = db.rawQuery(
                "SELECT id, day, source_type, source_id, amount, status, created, version, idempotency_key, metadata FROM $TABLE WHERE idempotency_key = ? OR (source_type = ? AND source_id = ?) LIMIT 1",
                arrayOf(key, sourceType, sourceId),
            ).use { cursor ->
                if (!cursor.moveToFirst()) null else XpTransaction(
                    id = cursor.getString(0),
                    day = cursor.getString(1),
                    sourceType = cursor.getString(2),
                    sourceId = cursor.getString(3),
                    amount = cursor.getInt(4),
                    status = XpStatus.valueOf(cursor.getString(5)),
                    createdAt = cursor.getLong(6),
                    version = cursor.getInt(7),
                    idempotencyKey = cursor.getString(8),
                    metadata = cursor.getString(9),
                )
            }
            if (existing != null) {
                require(existing.sourceType == sourceType && existing.sourceId == sourceId) {
                    "Idempotency key is already owned by another XP source"
                }
                if (existing.status == XpStatus.ACTIVE) {
                    existing
                } else {
                    db.update(TABLE, ContentValues().apply {
                        put("status", XpStatus.ACTIVE.name)
                        put("amount", amount)
                        put("created", wallNow)
                        put("version", version)
                        put("metadata", metadata)
                    }, "id = ?", arrayOf(existing.id))
                    existing.copy(amount = amount, status = XpStatus.ACTIVE, createdAt = wallNow, version = version, metadata = metadata)
                }
            } else {
                val newId = "xp_${key}"
                val cv = ContentValues().apply {
                    put("id", newId)
                    put("day", day)
                    put("source_type", sourceType)
                    put("source_id", sourceId)
                    put("amount", amount)
                    put("status", XpStatus.ACTIVE.name)
                    put("created", wallNow)
                    put("version", version)
                    put("idempotency_key", key)
                    put("metadata", metadata)
                }
                db.insertOrThrow(TABLE, null, cv)
                XpTransaction(newId, day, sourceType, sourceId, amount, XpStatus.ACTIVE, wallNow, version, key, metadata)
            }
        }
    }

    /**
     * Reverses / voids XP from a given source.
     * Returns true if an active transaction was reversed; false if nothing was active.
     */
    fun reverse(
        db: SQLiteDatabase,
        sourceType: String,
        sourceId: String,
        reason: String = "",
    ): Boolean {
        initSchema(db)
        return inTransaction(db) {
            val row = db.rawQuery(
                "SELECT id, metadata FROM $TABLE WHERE source_type = ? AND source_id = ? AND status = 'ACTIVE' LIMIT 1",
                arrayOf(sourceType, sourceId),
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) to cursor.getString(1) else null }
            if (row == null) false else {
                val cv = ContentValues().apply {
                    put("status", XpStatus.REVERSED.name)
                    if (reason.isNotBlank()) put("metadata", listOf(row.second, "reversed:$reason").filter { it.isNotBlank() }.joinToString(";"))
                }
                db.update(TABLE, cv, "id = ?", arrayOf(row.first))
                true
            }
        }
    }

    /**
     * Query total active XP.
     */
    fun totalXp(db: SQLiteDatabase): Int {
        initSchema(db)
        return db.rawQuery(
            "SELECT COALESCE(SUM(amount), 0) FROM $TABLE WHERE status = 'ACTIVE'",
            null,
        ).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    /**
     * Query transaction by source.
     */
    fun findBySource(db: SQLiteDatabase, sourceType: String, sourceId: String): XpTransaction? {
        initSchema(db)
        return db.rawQuery(
            "SELECT id, day, source_type, source_id, amount, status, created, version, idempotency_key, metadata FROM $TABLE WHERE source_type = ? AND source_id = ? ORDER BY created DESC LIMIT 1",
            arrayOf(sourceType, sourceId),
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                XpTransaction(
                    id = cursor.getString(0),
                    day = cursor.getString(1),
                    sourceType = cursor.getString(2),
                    sourceId = cursor.getString(3),
                    amount = cursor.getInt(4),
                    status = XpStatus.valueOf(cursor.getString(5)),
                    createdAt = cursor.getLong(6),
                    version = cursor.getInt(7),
                    idempotencyKey = cursor.getString(8),
                    metadata = cursor.getString(9),
                )
            } else null
        }
    }

    /**
     * Query transaction history.
     */
    fun queryHistory(db: SQLiteDatabase, limit: Int = 100): List<XpTransaction> {
        initSchema(db)
        val list = mutableListOf<XpTransaction>()
        db.rawQuery(
            "SELECT id, day, source_type, source_id, amount, status, created, version, idempotency_key, metadata FROM $TABLE ORDER BY created DESC, rowid DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    XpTransaction(
                        id = cursor.getString(0),
                        day = cursor.getString(1),
                        sourceType = cursor.getString(2),
                        sourceId = cursor.getString(3),
                        amount = cursor.getInt(4),
                        status = XpStatus.valueOf(cursor.getString(5)),
                        createdAt = cursor.getLong(6),
                        version = cursor.getInt(7),
                        idempotencyKey = cursor.getString(8),
                        metadata = cursor.getString(9),
                    )
                )
            }
        }
        return list
    }
}
