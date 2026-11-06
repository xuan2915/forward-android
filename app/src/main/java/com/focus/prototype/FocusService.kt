package com.focus.prototype

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

// Foreground-package event handling adapted from SelfLock (MIT); see LICENSES/SelfLock.txt.
class FocusService : AccessibilityService() {
    companion object {
        @Volatile var connected = false
            private set
        @Volatile private var activeService: FocusService? = null

        fun overlayDismissed() {
            activeService?.dismissBlockActivity()
        }
    }

    private lateinit var store: Store
    private lateinit var db: Database
    private val handler = Handler(Looper.getMainLooper())
    private var current: String? = null
    private var lastTick = 0L
    private var blockActivityShowing = false
    private var beforeScreenOff: String? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                charge(screenJustOff = true)
                beforeScreenOff = current
                current = null
                blockActivityShowing = false
            } else if (intent.action == Intent.ACTION_USER_PRESENT || intent.action == Intent.ACTION_SCREEN_ON && !(getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked) {
                current = beforeScreenOff
                beforeScreenOff = null
                lastTick = SystemClock.elapsedRealtime()
                enforce()
            }
        }
    }

    private var checkpointTick = 0
    private val tick = object : Runnable {
        override fun run() {
            db.settleToday()
            charge()
            enforce()
            if (++checkpointTick % 10 == 0) db.checkpoint()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onServiceConnected() {
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
        handler.post(tick)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName && blockActivityShowing) return
        if (pkg != packageName) blockActivityShowing = false
        charge()
        current = pkg
        enforce()
    }

    private fun charge(screenJustOff: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val elapsed = (now - lastTick).coerceAtLeast(0)
        lastTick = now
        if (current in Store.videos && current in store.selected && !(current == "tv.danmaku.bili" && db.learningActive()) && !db.contract().emergency && db.balance("video") > 0 && (screenJustOff || (getSystemService(POWER_SERVICE) as PowerManager).isInteractive && !(getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked)) {
            db.consume("video", elapsed)
        }
    }

    private fun enforce() {
        val pkg = current ?: return
        val contract = db.contract()
        val noAllowance=when(pkg){in Store.videos->db.balance("video")<=0;in Store.games->db.balance("game")<=0;else->!db.eligible()}
        val blocked = !contract.emergency && store.canRestrict(pkg) && !(pkg == "tv.danmaku.bili" && db.learningActive()) && pkg in store.selected && noAllowance
        if (blocked) {
            if (!blockActivityShowing) show()
        } else {
            blockActivityShowing = false
        }
    }

    private fun show() {
        blockActivityShowing = true
        startActivity(Intent(this, BlockOverlayActivity::class.java)
            .putExtra(BlockOverlayActivity.EXTRA_BLOCKED_PACKAGE, current)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
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
        charge()
        unregisterReceiver(screenReceiver)
        db.close()
        super.onDestroy()
    }
}
