package com.focus.prototype

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

// Foreground-package event handling adapted from SelfLock (MIT); see LICENSES/SelfLock.txt.
class FocusService : AccessibilityService() {
 companion object { @Volatile var connected = false; private set }
 private lateinit var store: Store
 private lateinit var db: Database
 private lateinit var wm: WindowManager
 private val handler = Handler(Looper.getMainLooper())
 private var current: String? = null
 private var lastTick = 0L
 private var overlay: View? = null
 private var beforeScreenOff:String?=null
 private val screenReceiver = object : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
   if(intent.action==Intent.ACTION_SCREEN_OFF){charge(true);beforeScreenOff=current;current=null;hide()}
   else if(intent.action==Intent.ACTION_USER_PRESENT || intent.action==Intent.ACTION_SCREEN_ON && !(getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked){current=beforeScreenOff;beforeScreenOff=null;lastTick=SystemClock.elapsedRealtime();enforce()}
  }
 }
 private var checkpointTick=0
 private val tick = object : Runnable { override fun run() { db.settleToday();charge();enforce();if(++checkpointTick%10==0)db.checkpoint();handler.postDelayed(this, 1000) } }
 override fun onServiceConnected() {
  store = Store(this); db = Database(this); wm = getSystemService(WINDOW_SERVICE) as WindowManager
  connected = true
  registerReceiver(screenReceiver, IntentFilter().apply{addAction(Intent.ACTION_SCREEN_OFF);addAction(Intent.ACTION_SCREEN_ON);addAction(Intent.ACTION_USER_PRESENT)})
  lastTick = SystemClock.elapsedRealtime(); handler.post(tick)
 }
 override fun onAccessibilityEvent(event: AccessibilityEvent?) {
  if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
  val pkg = event.packageName?.toString() ?: return
  if (pkg == packageName && overlay != null) return
  charge(); current = pkg; enforce()
 }
 private fun charge(screenJustOff:Boolean=false) {
  val now = SystemClock.elapsedRealtime(); val elapsed = (now - lastTick).coerceAtLeast(0); lastTick = now
  if (current in Store.videos && current in store.selected && !(current == "tv.danmaku.bili" && db.learningActive()) && db.eligible() && !db.contract().emergency && db.balance("video") > 0 && (screenJustOff || (getSystemService(POWER_SERVICE) as PowerManager).isInteractive && !(getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked)) db.consume("video", elapsed)
 }
 private fun enforce() {
  val pkg = current ?: return
  val contract = db.contract()
  val blocked = !contract.emergency && store.canRestrict(pkg) && !(pkg == "tv.danmaku.bili" && db.learningActive()) && pkg in store.selected && (!db.eligible() || pkg in Store.videos && db.balance("video") <= 0)
  if (blocked && overlay == null) show() else if (!blocked) hide()
 }
 private fun show() {
  val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(48,48,48,48); setBackgroundColor(0xff090a0b.toInt()) }
  panel.addView(TextView(this).apply { text = "先完成今天的目标"; textSize = 26f; setTextColor(0xffeeeeee.toInt()); gravity = Gravity.CENTER })
  panel.addView(TextView(this).apply { text = (if(db.eligible()) "视频时间已用完，完成选做任务可领取额外时间" else "完成并确认所有必做任务后，即可解锁娱乐")+"\n剩余视频时间：${db.balance("video") / 60000} 分钟"; textSize = 17f; setTextColor(0xffaeb7bd.toInt()); gravity = Gravity.CENTER; setPadding(0,32,0,32) })
  panel.addView(Button(this).apply { text = "返回自律 App"; setOnClickListener { current = null; hide(); startActivity(Intent(this@FocusService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)) } })
  panel.addView(Button(this).apply { text = "紧急解锁（记录至今天）"; setOnClickListener { db.emergency(); hide() } })
  panel.addView(Button(this).apply { text = "返回桌面"; setOnClickListener { current = null; hide(); performGlobalAction(GLOBAL_ACTION_HOME) } })
  val params = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
  overlay = panel; wm.addView(panel, params)
 }
 private fun hide() { overlay?.let { wm.removeView(it); overlay = null } }
 override fun onInterrupt() { charge(); current = null; hide() }
 override fun onDestroy() { connected = false; handler.removeCallbacks(tick); charge(); hide(); unregisterReceiver(screenReceiver); db.close(); super.onDestroy() }
}
