package com.focus.prototype

data class PlanDraft(val title: String, val required: Boolean)

object PlanImport {
 fun parse(text: String): List<PlanDraft> {
  val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
  val marker = lines.indexOfFirst { it.matches(Regex(".*(明日计划|明天计划|明日任务|明天任务).*")) }
  val candidates = if(marker >= 0) lines.drop(marker+1) else lines
  return candidates.filterNot { it.matches(Regex("^(今日总结|今日复盘|今天.*|总结|复盘)[：:].*")) }
   .map { line -> val title=line.replace(Regex("^([•*\\-]|\\d+[.、)）])\\s*"), "").replace(Regex("^[□☐]\\s*"), ""); PlanDraft(title.replace(Regex("^[（(]?可选[）):：]?\\s*"), "").trim(), !title.contains("可选")) }
   .filter { it.title.isNotBlank() }.distinctBy { it.title }.take(50)
 }
}
