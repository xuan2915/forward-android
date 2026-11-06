package com.focus.prototype

import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager

class Store(context: Context) {
 private val appContext = context.applicationContext
 private val p = context.getSharedPreferences("focus", Context.MODE_PRIVATE)
 var locked: Boolean
  get() = p.getBoolean("locked", false)
  set(value) { p.edit().putBoolean("locked", value).apply() }
 var selected: Set<String>
  get() = p.getStringSet("selected", emptySet())!!.toSet()
  set(value) { p.edit().putStringSet("selected", value).apply() }
 var remaining: Long
  get() = p.getLong("remaining", 0)
  set(value) { p.edit().putLong("remaining", value.coerceAtLeast(0)).apply() }
 var used: Long
  get() = p.getLong("used", 0)
  set(value) { p.edit().putLong("used", value).apply() }
 fun consume(ms: Long) { val actual = minOf(ms, remaining); p.edit().putLong("remaining", remaining - actual).putLong("used", used + actual).apply() }
 fun canRestrict(pkg: String): Boolean {
  val home = appContext.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0)?.activityInfo?.packageName
  val dialer = (appContext.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage
  return pkg != appContext.packageName && pkg != "com.android.settings" && pkg != home && pkg != dialer && pkg != "com.android.phone" && pkg != "com.android.systemui"
 }
 companion object {
  val videos = setOf("com.ss.android.ugc.aweme", "tv.danmaku.bili")
  val games = setOf("com.tencent.tmgp.sgame")
 }
}
