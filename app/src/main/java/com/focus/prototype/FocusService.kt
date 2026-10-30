package com.focus.prototype

import android.accessibilityservice.AccessibilityService
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
 private lateinit var store: Store
 private lateinit var wm: WindowManager
 private val handler = Handler(Looper.getMainLooper())
 private var current: String? = null
 private var lastTick = 0L
 private var overlay: View? = null
 private val screenReceiver = object : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) { if (intent.action == Intent.ACTION_SCREEN_OFF) { charge(); current = null; hide() } }
 }
 private val tick = object : Runnable { override fun run() { charge(); enforce(); handler.postDelayed(this, 1000) } }
 override fun onServiceConnected() {
  store = Store(this); wm = getSystemService(WINDOW_SERVICE) as WindowManager
  registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
  lastTick = SystemClock.elapsedRealtime(); handler.post(tick)
 }
 override fun onAccessibilityEvent(event: AccessibilityEvent?) {
  if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
  val pkg = event.packageName?.toString() ?: return
  if (pkg == packageName && overlay != null) return
  charge(); current = pkg; enforce()
 }
 private fun charge() {
  val now = SystemClock.elapsedRealtime(); val elapsed = (now - lastTick).coerceAtLeast(0); lastTick = now
  if (current in Store.videos && current in store.selected && store.locked && store.remaining > 0 && (getSystemService(POWER_SERVICE) as PowerManager).isInteractive) store.consume(elapsed)
 }
 private fun enforce() {
  val pkg = current ?: return
  val blocked = store.locked && pkg in store.selected && (pkg !in Store.videos || store.remaining <= 0)
  if (blocked && overlay == null) show() else if (!blocked) hide()
 }
 private fun show() {
  val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(48,48,48,48); setBackgroundColor(0xfff7f6f2.toInt()) }
  panel.addView(TextView(this).apply { text = "暂时不能打开这个应用"; textSize = 24f; setTextColor(0xff202020.toInt()); gravity = Gravity.CENTER })
  panel.addView(TextView(this).apply { text = "手动锁定中，或共享视频额度已用完\n剩余视频额度：${store.remaining / 60000} 分钟"; textSize = 17f; setTextColor(0xff444444.toInt()); gravity = Gravity.CENTER; setPadding(0,32,0,32) })
  panel.addView(Button(this).apply { text = "返回自律 App"; setOnClickListener { current = null; hide(); startActivity(Intent(this@FocusService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)) } })
  panel.addView(Button(this).apply { text = "紧急解锁"; setOnClickListener { store.locked = false; hide() } })
  panel.addView(Button(this).apply { text = "返回桌面"; setOnClickListener { current = null; hide(); performGlobalAction(GLOBAL_ACTION_HOME) } })
  val params = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
  overlay = panel; wm.addView(panel, params)
 }
 private fun hide() { overlay?.let { wm.removeView(it); overlay = null } }
 override fun onInterrupt() { charge(); current = null; hide() }
 override fun onDestroy() { handler.removeCallbacks(tick); charge(); hide(); unregisterReceiver(screenReceiver); super.onDestroy() }
}
