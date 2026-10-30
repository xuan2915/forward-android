package com.focus.prototype

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
 private var refresh by mutableIntStateOf(0)
 override fun onResume() { super.onResume(); refresh++ }
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
  val store = Store(this)
  val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
  val apps = packageManager.queryIntentActivities(launch, 0).filter { it.activityInfo.packageName != packageName && it.activityInfo.packageName != "com.android.settings" }.distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(packageManager).toString() }
  setContent { MaterialTheme {
   var locked by remember(refresh) { mutableStateOf(store.locked) }
   var selected by remember(refresh) { mutableStateOf(store.selected) }
   var quota by remember { mutableStateOf("10") }
   val enabled = remember(refresh) { val id = ComponentName(this, FocusService::class.java).flattenToString(); Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.split(':')?.any { it.equals(id, true) } == true }
   LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 20.dp)) {
    item { Text("自律 · 应用拦截", fontSize = 26.sp); Spacer(Modifier.height(8.dp)); Text("原型：选择应用，开启锁定；抖音和 B站共享视频额度。") }
    item { Spacer(Modifier.height(16.dp)); Text("无障碍服务：${if (enabled) "已开启" else "未开启"}"); Button(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("打开无障碍设置") }; Text("请允许自律原型识别前台应用。开机后由系统重新连接服务；请在系统电池设置中允许后台运行。", fontSize = 13.sp) }
    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("手动锁定", fontSize = 20.sp); Switch(checked = locked, onCheckedChange = { locked = it; store.locked = it }) }; Text("锁定时，其他选中应用直接拦截；视频应用有额度时可用。") }
    item { Spacer(Modifier.height(12.dp)); Text("共享视频剩余：${store.remaining / 60000} 分 ${store.remaining / 1000 % 60} 秒"); Text("累计已使用：${store.used / 60000} 分 ${store.used / 1000 % 60} 秒"); OutlinedTextField(value = quota, onValueChange = { quota = it.filter(Char::isDigit).take(4) }, label = { Text("添加视频分钟") }, singleLine = true); Button(onClick = { val n = quota.toLongOrNull() ?: 0; store.remaining += n * 60000; refresh++ }) { Text("添加额度") }; Text("额度仅对选中的抖音和 B站生效。", fontSize = 13.sp); Spacer(Modifier.height(16.dp)); Text("选择受限应用", fontSize = 20.sp) }
    items(apps, key = { it.activityInfo.packageName }) { app -> val pkg = app.activityInfo.packageName; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(app.loadLabel(packageManager).toString()); Text(pkg, fontSize = 11.sp) }; Checkbox(checked = pkg in selected, onCheckedChange = { checked -> selected = if (checked) selected + pkg else selected - pkg; store.selected = selected }) } }
   }
  } }
 }
}
