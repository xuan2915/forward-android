package com.focus.prototype

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

internal object MonthlyChallengeScheduler {
    private const val ACTION_SETTLE = "com.focus.prototype.MONTHLY_CHALLENGE_SETTLE"

    fun reschedule(context: Context, db: Database, nowMillis: Long = System.currentTimeMillis()) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            7391,
            Intent(context, MonthlyChallengeSettlementReceiver::class.java)
                .setAction(ACTION_SETTLE)
                .setData(Uri.parse("xiangqian://monthly-challenge/settlement")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarm.cancel(pending)
        val triggerAt = db.monthlyChallengeEngine().nextSettlementDeadline(nowMillis) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (NightWrapUpScheduler.exactAlarmAvailable(context)) {
                runCatching { alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending) }
                    .onFailure { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending) }
            } else {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } else {
            alarm.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    internal fun isSettlementAction(action: String?): Boolean = action == ACTION_SETTLE
}

internal object MonthlyChallengeAppLifecycle {
    fun onForeground(context: Context, db: Database, nowMillis: Long = System.currentTimeMillis()) {
        db.monthlyChallengeEngine().reconcileDueChallenges(nowMillis)
        MonthlyChallengeScheduler.reschedule(context, db, nowMillis)
    }
}

internal class MonthlyChallengeSettlementReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!MonthlyChallengeScheduler.isSettlementAction(intent.action)) return
        val pending = goAsync()
        Thread {
            try {
                Database(context).use { db ->
                    MonthlyChallengeAppLifecycle.onForeground(context, db)
                }
            } catch (_: Exception) {
                // The foreground reconciliation retries without changing an unsettled record.
            } finally {
                pending.finish()
            }
        }.start()
    }
}

internal class MonthlyChallengeSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        runCatching {
            Database(context).use { db ->
                MonthlyChallengeAppLifecycle.onForeground(context, db)
            }
        }
    }
}
