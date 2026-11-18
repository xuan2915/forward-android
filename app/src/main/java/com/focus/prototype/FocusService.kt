package com.focus.prototype

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.WindowManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

// Foreground-package event handling adapted from SelfLock (MIT); see LICENSES/SelfLock.txt.
class FocusService : AccessibilityService() {
    companion object {
        @Volatile var connected = false
            private set
        @Volatile private var activeService: FocusService? = null

        fun overlayDismissed() {
            activeService?.dismissBlockActivity()
        }

        fun overlayShown() {
            activeService?.blockActivityShowing = true
            activeService?.blockLaunchDeadline = 0L
        }

        fun showBedtimeReminder() {
            activeService?.let { service -> service.handler.post { service.presentBedtimeReminder() } }
        }
    }

    private lateinit var store: Store
    private lateinit var db: Database
    private val handler = Handler(Looper.getMainLooper())
    private var current: String? = null
    private var foregroundEventAt = 0L
    private var lastDecision: String? = null
    private var lastTick = 0L
    private var blockActivityShowing = false
    private var blockLaunchDeadline = 0L
    private var bedtimeDialog: AlertDialog? = null
    private var nightAttemptRecorded = false
    private var sessionStartedAt = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                charge(screenJustOff = true)
                bedtimeDialog?.dismiss()
                current = null
                foregroundEventAt = 0L
                blockActivityShowing = false
            } else if (intent.action == Intent.ACTION_USER_PRESENT || intent.action == Intent.ACTION_SCREEN_ON && !(getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked) {
                lastTick = SystemClock.elapsedRealtime()
                syncForeground()
                enforce()
            }
        }
    }

    private var checkpointTick = 0
    private val tick = object : Runnable {
        override fun run() {
            db.settleToday()
            charge()
            syncForeground()
            enforce()
            if (++checkpointTick % 10 == 0) db.checkpoint()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onServiceConnected() {
        if (activeService === this) return
        store = Store(this)
        db = Database(this)
        activeService = this
        connected = true
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        })
        lastTick = SystemClock.elapsedRealtime()
        Log.d("ForwardControl", "accessibility connected")
        handler.post(tick)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!connected || !::db.isInitialized || !screenAvailable()) return
        if (bedtimeDialog?.isShowing == true) return
        val pkg = event?.packageName?.toString()
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            pkg != null && pkg in store.selected) {
            foregroundEventAt = SystemClock.elapsedRealtime()
            updateForeground(pkg)
        } else {
            syncForeground()
        }
        enforce()
    }

    private fun screenAvailable() =
        getSystemService(PowerManager::class.java).isInteractive &&
            !getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun syncForeground(): Boolean {
        if (!screenAvailable() || bedtimeDialog?.isShowing == true) return false
        if (foregroundEventAt != 0L && SystemClock.elapsedRealtime() - foregroundEventAt < 600L) return true
        val pkg = rootInActiveWindow?.packageName?.toString() ?: return false
        updateForeground(pkg)
        return true
    }

    private fun updateForeground(pkg: String) {
        if (pkg == packageName && blockActivityShowing) return
        if (pkg == current) return
        charge()
        blockActivityShowing = false
        blockLaunchDeadline = 0L
        nightAttemptRecorded = false
        sessionStartedAt = System.currentTimeMillis()
        current = pkg
        if (BuildConfig.DEBUG && pkg in store.selected) Log.d("ForwardControl", "foreground: $pkg")
    }

    private fun charge(screenJustOff: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val elapsed = (now - lastTick).coerceAtLeast(0)
        lastTick = now
        val pkg = current ?: return
        if (pkg in store.selected && db.rewardKind(pkg) == "video" && !blockActivityShowing &&
            !(pkg == "tv.danmaku.bili" && db.learningActive()) && !db.contract().emergency &&
            db.balance("video") > 0 && (screenJustOff || screenAvailable())) {
            db.consume("video", elapsed)
        }
    }

    private fun enforce() {
        if (!screenAvailable() || bedtimeDialog?.isShowing == true) return
        val pkg = current ?: return
        val contract = db.contract()
        if (pkg !in store.selected || !store.canRestrict(pkg)) {
            blockActivityShowing = false
            lastDecision = null
            return
        }
        val kind = db.rewardKind(pkg)
        val noAllowance = db.balance(kind) <= 0
        val learningExempt = pkg == "tv.danmaku.bili" && db.learningActive()
        val nightRestricted = db.nightEntertainmentRestricted() && !contract.emergency && !learningExempt
        val verifiableNightBlock = nightRestricted
        if (verifiableNightBlock && !nightAttemptRecorded) {
            val cycle = db.nightCycleDate()
            db.recordNightViolation("$cycle|$pkg|${sessionStartedAt.takeIf { it > 0 } ?: System.currentTimeMillis()}", pkg, System.currentTimeMillis())
            nightAttemptRecorded = true
        }
        val blocked = !contract.emergency && !learningExempt && (noAllowance || nightRestricted)
        val decision = "$pkg kind=$kind blocked=$blocked night=$nightRestricted exhausted=$noAllowance"
        if (BuildConfig.DEBUG && lastDecision != decision) Log.d("ForwardControl", decision)
        lastDecision = decision
        if (blocked) {
            if (!blockActivityShowing || blockLaunchDeadline in 1..SystemClock.elapsedRealtime()) show()
        } else {
            blockActivityShowing = false
        }
    }

    private fun show() {
        if (blockLaunchDeadline != 0L) Log.w("ForwardControl", "block activity not acknowledged; retrying")
        blockActivityShowing = true
        blockLaunchDeadline = SystemClock.elapsedRealtime() + 2500L
        runCatching { startActivity(Intent(this, BlockOverlayActivity::class.java)
            .putExtra(BlockOverlayActivity.EXTRA_BLOCKED_PACKAGE, current)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
            .onFailure { blockActivityShowing = false; Log.w("ForwardControl", "block activity launch failed", it) }
    }

    private fun presentBedtimeReminder() {
        if (!screenAvailable() || bedtimeDialog?.isShowing == true) return
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        fun background(color: Int, radius: Int) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(26), dp(24), dp(22))
            background = background(Color.rgb(31, 37, 44), 28)
        }
        panel.addView(TextView(this).apply {
            text = "夜间收尾"
            textSize = 13f
            setTextColor(Color.rgb(142, 173, 168))
        })
        panel.addView(TextView(this).apply {
            text = "准备就寝"
            textSize = 25f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.rgb(244, 241, 235))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        panel.addView(TextView(this).apply {
            text = "收好手头的事，准备休息"
            textSize = 15f
            setTextColor(Color.rgb(169, 177, 185))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setView(panel)
            .create()
        panel.addView(TextView(this).apply {
            text = "知道了"
            textSize = 16f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.rgb(19, 23, 28))
            background = background(Color.rgb(142, 173, 168), 18)
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(26) })
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        dialog.setOnDismissListener { bedtimeDialog = null }
        bedtimeDialog = dialog
        runCatching {
            dialog.show()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.window?.setLayout(minOf(resources.displayMetrics.widthPixels - dp(48), dp(400)), WindowManager.LayoutParams.WRAP_CONTENT)
        }.onFailure { bedtimeDialog = null }
    }

    private fun dismissBlockActivity() {
        blockActivityShowing = false
        current = null
    }

    override fun onInterrupt() {
        charge()
        current = null
        blockActivityShowing = false
    }

    override fun onDestroy() {
        connected = false
        activeService = null
        handler.removeCallbacks(tick)
        bedtimeDialog?.dismiss()
        if (!::db.isInitialized) {
            super.onDestroy()
            return
        }
        charge()
        unregisterReceiver(screenReceiver)
        db.close()
        super.onDestroy()
    }
}
