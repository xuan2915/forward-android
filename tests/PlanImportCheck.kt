package com.focus.prototype
fun main() {
 check(PlanImport.parse("明日计划\n1. 阅读\n2. 可选：跑步")==listOf(PlanDraft("阅读",true),PlanDraft("跑步",false)))
 check(PlanImport.parse("\n").isEmpty())
 check(PlanImport.parse("- 阅读\n- 阅读").size==1)
 check(PlanImport.parse("今日总结：不错\n明日任务\n• 写作").single().title=="写作")
 check(PlanImport.parse("普通自由文本").single().title=="普通自由文本")
 println("PASS: 5 text import checks")
}
