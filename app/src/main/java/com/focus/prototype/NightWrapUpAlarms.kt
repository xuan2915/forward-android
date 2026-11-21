package com.focus.prototype

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

internal object NightWrapUpScheduler {
    internal const val ACTION_STAGE = "com.focus.prototype.NIGHT_STAGE"
    internal const val ACTION_EXTENSION_END = "com.focus.prototype.NIGHT_EXTENSION_END"
    internal const val ACTION_END_WORK = "com.focus.prototype.NIGHT_END_WORK"
    internal const val ACTION_EXTEND_WORK = "com.focus.prototype.NIGHT_EXTEND_WORK"
    internal const val EXTRA_CYCLE = "night_cycle"
    internal const val EXTRA_STAGE = "night_stage"
    private const val CHANNEL = "night_wrap_up"

    fun exactAlarmAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun reschedule(context: Context, db: Database) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val config = db.nightWrapUpConfig()
        val now = LocalDateTime.now()
        val dates = (0L..5L).map { now.toLocalDate().minusDays(2).plusDays(it) }
        for (date in dates) {
            NightStage.entries.take(5).forEach { stage ->
                val intent = stageIntent(context, date.toString(), stage.name)
                val pending = PendingIntent.getBroadcast(context, stage.order, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                alarm.cancel(pending)
                val trigger = config.at(date, stage).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if (trigger <= System.currentTimeMillis()) return@forEach
                scheduleAlarm(alarm, trigger, pending, context)
            }
        }
        for (date in dates) {
            val intent = extensionIntent(context, date.toString())
            val pending = PendingIntent.getBroadcast(context, 5, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarm.cancel(pending)
        }
        val cycle = config.cycleDate(now).toString()
        val extendedUntil = db.setting("night_extension_until_$cycle").toLongOrNull() ?: 0L
        if (extendedUntil > System.currentTimeMillis() && db.setting("night_work_ended_$cycle") != "true") {
            val pending = PendingIntent.getBroadcast(context, 5, extensionIntent(context, cycle), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            scheduleAlarm(alarm, extendedUntil, pending, context)
        }
    }

    private fun scheduleAlarm(alarm: AlarmManager, trigger: Long, pending: PendingIntent, context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (exactAlarmAvailable(context)) {
                runCatching { alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending) }
                    .onFailure { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending) }
            } else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        } else alarm.setExact(AlarmManager.RTC_WAKEUP, trigger, pending)
    }

    private fun stageIntent(context: Context, cycle: String, stage: String) = Intent(context, NightWrapUpAlarmReceiver::class.java)
        .setAction(ACTION_STAGE).setData(Uri.parse("xiangqian://night/$cycle/$stage"))
        .putExtra(EXTRA_CYCLE, cycle).putExtra(EXTRA_STAGE, stage)

    private fun extensionIntent(context: Context, cycle: String) = Intent(context, NightWrapUpAlarmReceiver::class.java)
        .setAction(ACTION_EXTENSION_END).setData(Uri.parse("xiangqian://night/$cycle/extension"))
        .putExtra(EXTRA_CYCLE, cycle)

    internal fun actionIntent(context: Context, action: String, cycle: String) = Intent(context, NightWrapUpAlarmReceiver::class.java)
        .setAction(action).setData(Uri.parse("xiangqian://night-action/$cycle/$action"))
        .putExtra(EXTRA_CYCLE, cycle)

    internal fun notificationPermissionGranted(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    internal fun exactAlarmSettingsIntent(context: Context): Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !exactAlarmAvailable(context)) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
    } else null

    internal fun notificationSettingsIntent(context: Context) = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    internal fun post(context: Context, id: Int, title: String, message: String, actions: List<Pair<String, Pair<String, String>>> = emptyList()) {
        if (!notificationPermissionGranted(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "夜间收尾", NotificationManager.IMPORTANCE_LOW).apply { description = "安静的夜间提醒与娱乐限制状态"; setSound(null, null); enableVibration(false); enableLights(false) })
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title).setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT)
        actions.forEach { (label, data) ->
            val intent = actionIntent(context, data.first, data.second)
            val pending = PendingIntent.getBroadcast(context, data.first.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(0, label, pending)
        }
        manager.notify(id and 0x7fffffff, builder.build())
    }

    internal fun extensionAlarm(context: Context, cycle: String, at: Long) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(context, 5, extensionIntent(context, cycle), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        scheduleAlarm(alarm, at, pending, context)
    }
}

internal class NightWrapUpAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cycle = intent.getStringExtra(NightWrapUpScheduler.EXTRA_CYCLE) ?: return
        val pending = goAsync()
        Thread {
            try {
                Database(context).use { db ->
                    when (intent.action) {
                        NightWrapUpScheduler.ACTION_END_WORK -> {
                            if (isCurrentWorkWindow(db, cycle)) {
                                db.setSetting("night_work_ended_$cycle", "true")
                                db.audit(cycle, "已确认结束新工作")
                                NightWrapUpScheduler.post(context, cycle.hashCode(), "已记录", "新工作已标记为结束")
                            }
                        }
                        NightWrapUpScheduler.ACTION_EXTEND_WORK -> {
                            if (isCurrentWorkWindow(db, cycle) && db.setting("night_work_ended_$cycle") != "true" && db.setting("night_extension_used_$cycle") != "true") {
                                val until = System.currentTimeMillis() + 30 * 60_000L
                                db.setSetting("night_extension_used_$cycle", "true")
                                db.setSetting("night_extension_until_$cycle", until.toString())
                                db.setSetting("night_work_ended_$cycle", "false")
                                db.audit(cycle, "工作收尾提醒延长 30 分钟")
                                NightWrapUpScheduler.extensionAlarm(context, cycle, until)
                                NightWrapUpScheduler.post(context, cycle.hashCode(), "已延长 30 分钟", "时间到后会再次提醒，不会结束计时或关闭应用")
                            } else {
                                NightWrapUpScheduler.post(context, cycle.hashCode(), "无法延长", "这次工作延长已使用，或当前已不在工作截止时段")
                            }
                        }
                        NightWrapUpScheduler.ACTION_EXTENSION_END -> {
                            if (db.setting("night_work_ended_$cycle") != "true") {
                                db.audit(cycle, "一次延长提醒时间已到；未据此判断是否仍在工作")
                                NightWrapUpScheduler.post(context, (cycle.hashCode() xor 0x5f24), "延长时间已到", "是否继续工作由你决定；手机不会结束任务或后台进程")
                            }
                        }
                        NightWrapUpScheduler.ACTION_STAGE -> handleStage(context, db, cycle, intent.getStringExtra(NightWrapUpScheduler.EXTRA_STAGE).orEmpty())
                    }
                    NightWrapUpScheduler.reschedule(context, db)
                }
            } finally { pending.finish() }
        }.start()
    }

    private fun isCurrentWorkWindow(db: Database, cycle: String): Boolean = runCatching {
        val anchor = LocalDate.parse(cycle)
        val now = LocalDateTime.now()
        val config = db.nightWrapUpConfig()
        config.cycleDate(now).toString() == cycle &&
            !now.isBefore(config.at(anchor, NightStage.WORK_CUTOFF)) &&
            now.isBefore(config.at(anchor, NightStage.MORNING_RELEASE))
    }.getOrDefault(false)

    private fun handleStage(context: Context, db: Database, cycle: String, stage: String) {
        val marker = "night_stage_sent_${cycle}_$stage"
        if (db.setting(marker) == "true") return
        db.setSetting(marker, "true")
        when (stage) {
            NightStage.WIND_DOWN.name -> {
                db.audit(cycle, "开始收尾提醒")
                NightWrapUpScheduler.post(context, (cycle.hashCode() xor 0x101), "开始收尾", "适合停下新开的事，整理手头进度")
            }
            NightStage.WORK_CUTOFF.name -> {
                db.audit(cycle, "工作截止提醒已发出；不据此判断是否仍在工作")
                NightWrapUpScheduler.post(context, (cycle.hashCode() xor 0x202), "到工作截止时间", "可以结束新工作，或延长一次 30 分钟", listOf(
                    "结束工作" to (NightWrapUpScheduler.ACTION_END_WORK to cycle),
                    "延长 30 分钟" to (NightWrapUpScheduler.ACTION_EXTEND_WORK to cycle),
                ))
            }
            NightStage.ENTERTAINMENT_CUTOFF.name -> {
                db.audit(cycle, "娱乐限制时段开始；只限制已选短视频和游戏应用")
                NightWrapUpScheduler.post(context, (cycle.hashCode() xor 0x303), "娱乐限制已开始", "已选的短视频和游戏应用将暂时受限；学习窗口与紧急放行仍可用")
            }
            NightStage.BEDTIME.name -> {
                NightWrapUpScheduler.post(context, (cycle.hashCode() xor 0x404), "准备就寝", "收好手头的事，准备休息")
                FocusService.showBedtimeReminder()
            }
            NightStage.MORNING_RELEASE.name -> {
                val count = db.nightViolations(cycle).size
                val pendingMinutes = db.pendingNightPenalty("video", cycle) / 60_000L
                val pendingGames = db.pendingNightPenalty("game", cycle)
                val status = if (count > 0) "已记录 $count 次拦截 · 待抵扣 $pendingMinutes 分钟、$pendingGames 局" else "没有已记录的拦截 · 记录可能不完整，不代表按时就寝"
                db.audit(cycle, "夜间收尾摘要：$status")
                NightWrapUpScheduler.post(context, (cycle.hashCode() xor 0x505), "昨夜记录", status)
            }
        }
    }
}

internal class NightWrapUpSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        runCatching { Database(context).use { NightWrapUpScheduler.reschedule(context, it) } }
    }
}
