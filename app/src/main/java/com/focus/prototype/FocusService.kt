package com.focus.prototype

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
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
  val density=resources.displayMetrics.density
  fun dp(value:Int)=(value*density).toInt()
  val panel = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(30),dp(42),dp(30),dp(42));setBackgroundColor(0xff0e1110.toInt()) }
  fun label(value:String,size:Float,color:Int,bold:Boolean=false)=TextView(this).apply { text=value;textSize=size;setTextColor(color);gravity=Gravity.CENTER;typeface=if(bold)Typeface.create("sans-serif-medium",Typeface.NORMAL) else Typeface.create("sans-serif",Typeface.NORMAL);setLineSpacing(dp(5).toFloat(),1f) }
  fun button(value:String,primary:Boolean,onClick:()->Unit) {
   val fill=GradientDrawable().apply{cornerRadius=dp(17).toFloat();setColor(if(primary)0xffa8bbaa.toInt() else 0xff202522.toInt())}
   val ripple=RippleDrawable(ColorStateList.valueOf(0x30ffffff),fill,null)
   val view=label(value,15f,if(primary)0xff172019.toInt() else 0xffe8ece7.toInt(),true).apply{background=ripple;isClickable=true;isFocusable=true;setPadding(dp(18),dp(17),dp(18),dp(17));setOnClickListener{onClick()}}
   panel.addView(view,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(12)})
  }
  panel.addView(label("向前    ·    娱乐时间",12f,0xff9aa39c.toInt(),true),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(28)})
  val exhausted=db.eligible()
  panel.addView(label(if(exhausted)"今日额度已用完" else "先完成今天的任务",31f,0xfff0f2ed.toInt(),true),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(12)})
  panel.addView(label(if(exhausted)"完成选做任务后，可领取额外视频时间。" else "完成并确认全部必做任务后，即可解锁娱乐时间。",16f,0xff9aa39c.toInt()),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(30)})
  panel.addView(label("${db.balance("video")/60000}",52f,0xfff0f2ed.toInt()),LinearLayout.LayoutParams(-1,-2))
  panel.addView(label("剩余共享视频分钟",13f,0xff6f7972.toInt()),LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(3);bottomMargin=dp(26)})
  button("返回自律 App",true){current=null;hide();startActivity(Intent(this@FocusService,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))}
  button("紧急解锁",false){db.emergency();hide()}
  button("返回桌面",false){current=null;hide();performGlobalAction(GLOBAL_ACTION_HOME)}
  val params = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
  overlay = panel; wm.addView(panel, params)
 }
 private fun hide() { overlay?.let { wm.removeView(it); overlay = null } }
 override fun onInterrupt() { charge(); current = null; hide() }
 override fun onDestroy() { connected = false; handler.removeCallbacks(tick); charge(); hide(); unregisterReceiver(screenReceiver); db.close(); super.onDestroy() }
}
