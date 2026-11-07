package com.focus.prototype

import android.os.SystemClock

enum class FocusMode(val key: String, val title: String, val workMillis: Long, val breakMillis: Long) {
    POMODORO("pomodoro", "25 / 5", 25 * 60_000L, 5 * 60_000L),
    DEEP("deep", "50 / 10", 50 * 60_000L, 10 * 60_000L),
    FREE("free", "自由", 0L, 5 * 60_000L);

    companion object {
        fun from(key: String) = entries.firstOrNull { it.key == key } ?: POMODORO
    }
}

class FocusTimer(
    private val db: Database,
    private val elapsedNow: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallNow: () -> Long = { System.currentTimeMillis() },
) {
    val mode: FocusMode get() = FocusMode.from(db.setting("focus_mode", FocusMode.POMODORO.key))
    val phase: String get() = db.setting("focus_phase", "ready")
    val taskId: String get() = db.setting("focus_task")
    val notice: String get() = db.setting("focus_notice")

    fun selectMode(mode: FocusMode) {
        require(phase == "ready")
        db.setSetting("focus_mode", mode.key)
        db.setSetting("focus_notice", "")
    }

    fun selectTask(task: Task) {
        require(phase == "ready" || phase == "paused")
        db.setSetting("focus_task", task.id)
    }

    fun begin(task: Task) {
        if (phase == "paused") {
            resume(task)
            return
        }
        if (phase != "ready" || task.done) return
        db.runningTasks().filter { it.id != task.id }.forEach(db::pause)
        db.start(task)
        db.setSetting("focus_task", task.id)
        db.setSetting("focus_phase", "work")
        db.setSetting("focus_segment_started", elapsedNow().toString())
        db.setSetting("focus_segment_elapsed", "0")
        db.setSetting("focus_remaining", mode.workMillis.toString())
        db.setSetting("focus_deadline", if (mode.workMillis == 0L) "0" else (elapsedNow() + mode.workMillis).toString())
        db.setSetting("focus_wall_deadline", if (mode.workMillis == 0L) "0" else (wallNow() + mode.workMillis).toString())
        rememberBoot()
        db.setSetting("focus_notice", "")
    }

    fun remaining(): Long {
        val saved = db.setting("focus_remaining", "0").toLongOrNull() ?: 0L
        return when (phase) {
            "work", "break" -> {
                val deadline = db.setting("focus_deadline", "0").toLongOrNull() ?: 0L
                if (deadline == 0L) saved else (deadline - elapsedNow()).coerceAtLeast(0L)
            }
            "paused" -> saved
            else -> 0L
        }
    }

    fun elapsed(): Long {
        val accumulated = db.setting("focus_segment_elapsed", "0").toLongOrNull() ?: 0L
        if (phase != "work") return if (phase == "paused" && db.setting("focus_paused_phase") == "work") {
            if (mode.workMillis == 0L) accumulated else (mode.workMillis - remaining()).coerceAtLeast(0L)
        } else accumulated
        if (mode.workMillis > 0L) return (mode.workMillis - remaining()).coerceAtLeast(0L)
        val started = db.setting("focus_segment_started", "0").toLongOrNull() ?: 0L
        return accumulated + (elapsedNow() - started).coerceAtLeast(0L)
    }

    fun pause() {
        val old = phase
        if (old != "work" && old != "break") return
        val now = elapsedNow()
        val left = remaining()
        if (old == "work") {
            db.task(taskId)?.let(db::pause)
            val elapsed = if (mode.workMillis == 0L) elapsed() else (mode.workMillis - left).coerceAtLeast(0L)
            db.setSetting("focus_segment_elapsed", elapsed.toString())
        }
        db.setSetting("focus_paused_phase", old)
        db.setSetting("focus_remaining", left.toString())
        db.setSetting("focus_phase", "paused")
        db.setSetting("focus_segment_started", "0")
        db.setSetting("focus_deadline", "0")
        db.setSetting("focus_wall_deadline", "0")
        rememberBoot()
    }

    fun resume(task: Task) {
        if (phase != "paused" || task.done) return
        val previous = db.setting("focus_paused_phase", "work")
        if (previous == "work") db.start(task)
        val left = remaining()
        db.setSetting("focus_phase", previous)
        db.setSetting("focus_segment_started", if (previous == "work" && mode.workMillis == 0L) elapsedNow().toString() else "0")
        db.setSetting("focus_deadline", if (left == 0L) "0" else (elapsedNow() + left).toString())
        db.setSetting("focus_wall_deadline", if (left == 0L) "0" else (wallNow() + left).toString())
        rememberBoot()
    }

    fun resumeBreak() {
        if (phase != "paused" || db.setting("focus_paused_phase") != "break") return
        val left = remaining()
        db.setSetting("focus_phase", "break")
        db.setSetting("focus_deadline", if (left == 0L) "0" else (elapsedNow() + left).toString())
        db.setSetting("focus_wall_deadline", if (left == 0L) "0" else (wallNow() + left).toString())
        rememberBoot()
    }

    fun takeBreak() {
        if (phase == "work") pause()
        if (phase != "paused" && phase != "ready") return
        val breakMillis = mode.breakMillis
        db.setSetting("focus_phase", "break")
        db.setSetting("focus_remaining", breakMillis.toString())
        db.setSetting("focus_segment_started", "0")
        db.setSetting("focus_deadline", (elapsedNow() + breakMillis).toString())
        db.setSetting("focus_wall_deadline", (wallNow() + breakMillis).toString())
        rememberBoot()
        db.setSetting("focus_notice", "休息一下，回来继续。")
    }

    fun finish() {
        if (phase == "work") db.task(taskId)?.let(db::pause)
        db.setSetting("focus_phase", "ready")
        db.setSetting("focus_remaining", "0")
        db.setSetting("focus_deadline", "0")
        db.setSetting("focus_wall_deadline", "0")
        db.setSetting("focus_segment_started", "0")
        db.setSetting("focus_segment_elapsed", "0")
        db.setSetting("focus_paused_phase", "")
        db.setSetting("focus_notice", "")
        rememberBoot()
    }

    fun dismissNotice() = db.setSetting("focus_notice", "")

    /** Imported task history is paused; do not resume a timer from the other installation. */
    fun resetAfterRestore() {
        db.setSetting("focus_task", "")
        db.setSetting("focus_phase", "ready")
        db.setSetting("focus_paused_phase", "")
        db.setSetting("focus_remaining", "0")
        db.setSetting("focus_segment_started", "0")
        db.setSetting("focus_segment_elapsed", "0")
        db.setSetting("focus_deadline", "0")
        db.setSetting("focus_wall_deadline", "0")
        db.setSetting("focus_notice", "")
        rememberBoot()
    }

    /** Returns true once when a countdown changes phase. */
    fun advance(): Boolean {
        reconcileBoot()
        if (phase != "work" && phase != "break") return false
        if (phase == "work" && mode.workMillis == 0L || remaining() > 0L) return false
        if (phase == "break") {
            db.setSetting("focus_phase", "ready")
            db.setSetting("focus_remaining", "0")
            db.setSetting("focus_deadline", "0")
            db.setSetting("focus_wall_deadline", "0")
            db.setSetting("focus_notice", "休息结束，回来继续。")
        } else {
            db.task(taskId)?.let(db::pause)
            db.setSetting("focus_segment_elapsed", "0")
            db.setSetting("focus_segment_started", "0")
            if (mode.breakMillis > 0L) {
                db.setSetting("focus_phase", "break")
                db.setSetting("focus_remaining", mode.breakMillis.toString())
                db.setSetting("focus_deadline", (elapsedNow() + mode.breakMillis).toString())
                db.setSetting("focus_wall_deadline", (wallNow() + mode.breakMillis).toString())
                db.setSetting("focus_notice", "这一段，完成了。")
            } else {
                db.setSetting("focus_phase", "ready")
                db.setSetting("focus_remaining", "0")
                db.setSetting("focus_deadline", "0")
                db.setSetting("focus_wall_deadline", "0")
                db.setSetting("focus_notice", "这一段，完成了。")
            }
        }
        rememberBoot()
        return true
    }

    fun reconcileBoot() {
        val oldBoot = db.setting("focus_boot_marker").toLongOrNull() ?: return
        val bootNow = bootMarker()
        if (kotlin.math.abs(oldBoot - bootNow) <= 120_000L) return
        if (phase == "work") {
            db.task(taskId)?.let(db::pause)
            val wallDeadline = db.setting("focus_wall_deadline", "0").toLongOrNull() ?: 0L
            val left = if (wallDeadline == 0L) 0L else (wallDeadline - wallNow()).coerceAtLeast(0L)
            db.setSetting("focus_remaining", left.toString())
            db.setSetting("focus_paused_phase", "work")
            db.setSetting("focus_phase", if (left == 0L && mode.workMillis > 0L) "ready" else "paused")
        } else if (phase == "break") {
            db.setSetting("focus_phase", "ready")
            db.setSetting("focus_remaining", "0")
        }
        db.setSetting("focus_deadline", "0")
        db.setSetting("focus_wall_deadline", "0")
        db.setSetting("focus_segment_started", "0")
        db.setSetting("focus_boot_marker", bootNow.toString())
    }

    private fun rememberBoot() = db.setSetting("focus_boot_marker", bootMarker().toString())
    private fun bootMarker() = wallNow() - elapsedNow()
}

fun formatFocusTime(millis: Long): String {
    val seconds = millis.coerceAtLeast(0L) / 1000L
    return "%02d:%02d".format(seconds / 60L, seconds % 60L)
}
