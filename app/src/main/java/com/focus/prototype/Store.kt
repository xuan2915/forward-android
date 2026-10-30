package com.focus.prototype

import android.content.Context

class Store(context: Context) {
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
 companion object { val videos = setOf("com.ss.android.ugc.aweme", "tv.danmaku.bili") }
}
