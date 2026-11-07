package com.focus.prototype

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SleepCard(db:Database,refreshKey:Int,onChanged:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val targets=remember(refreshKey){db.sleepTargets()}
    var selectedDate by remember { mutableStateOf(db.day()) }
    val saved=remember(refreshKey,selectedDate){db.sleepRecord(selectedDate)}
    var choosingDate by remember { mutableStateOf(false) }
    var bedTarget by remember(refreshKey){mutableStateOf(targets[0])}
    var wakeTarget by remember(refreshKey){mutableStateOf(targets[1])}
    var bedPenalty by remember(refreshKey){mutableStateOf(targets[2])}
    var wakePenalty by remember(refreshKey){mutableStateOf(targets[3])}
    var bedtime by remember(refreshKey,selectedDate){mutableStateOf(saved?.bedtime.orEmpty())}
    var wakeTime by remember(refreshKey,selectedDate){mutableStateOf(saved?.wakeTime.orEmpty())}
    var sleepSource by remember(refreshKey,selectedDate){mutableStateOf(saved?.source?:"manual")}
    var exception by remember(refreshKey,selectedDate){mutableStateOf(saved?.excepted?:false)}
    var reason by remember(refreshKey,selectedDate){mutableStateOf("")}
    var message by remember(refreshKey){mutableStateOf("")}
    var healthMessage by remember { mutableStateOf("") }
    var readingHealth by remember { mutableStateOf(false) }
    var healthSessions by remember { mutableStateOf(emptyList<HealthSleepImport>()) }
    var importedSession by remember { mutableStateOf<HealthSleepImport?>(null) }
    var sleepRulesExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(selectedDate,importedSession,refreshKey) {
        importedSession?.takeIf { it.date==selectedDate }?.let { session ->
            bedtime=session.bedtime
            wakeTime=session.wakeTime
            sleepSource="health_connect:${session.sourcePackage}"
            exception=false
        }
    }
    fun loadHealthSessions() {
        scope.launch {
            readingHealth=true
            runCatching { readHealthSleepSessions(context) }
                .onSuccess { sessions ->
                    healthSessions=sessions
                    healthMessage=if(sessions.isEmpty()) "没有可导入记录 · 也可以手动填写" else "读取到 ${sessions.size} 条记录 · 选中后核对并保存"
                }
            .onFailure { healthMessage="读取失败：${it.message?:"Health Connect 暂不可用"}" }
            readingHealth=false
        }
    }
    val sleepPermissionLauncher=rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if(sleepReadPermission in granted) loadHealthSessions() else healthMessage="未授权读取 · 可继续手动记录"
    }
    QuietSurface {
        Text("睡眠记录",style=MaterialTheme.typography.titleLarge)
        Text("${targets[0]} 前就寝 · ${targets[1]} 前起床",style=MaterialTheme.typography.titleMedium,color=AppColors.Text)
        Text("手动确认 · 未达标将扣减后续奖励",style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)
        TextButton(onClick={sleepRulesExpanded=!sleepRulesExpanded}) { Text(if(sleepRulesExpanded) "收起睡眠规则" else "查看睡眠规则") }
        androidx.compose.animation.AnimatedVisibility(sleepRulesExpanded, enter=androidx.compose.animation.expandVertically(tween(motionMillis()))+androidx.compose.animation.fadeIn(tween(motionMillis())), exit=androidx.compose.animation.shrinkVertically(tween(motionMillis()))+androidx.compose.animation.fadeOut(tween(motionMillis()))) {
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("晚睡扣减 ${bedPenalty.ifBlank { "0" }} 分钟 · 晚起扣减 ${wakePenalty.ifBlank { "0" }} 分钟",style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)
                Text("已使用的奖励额度不追回",style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)
                Text("没有记录的日期不处罚",style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)
            }
        }
        Text("Health Connect · 可选导入",style=MaterialTheme.typography.labelLarge)
        Text("读取已有时段 · 保存前需核对确认",style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)
        PrimaryAction(if(readingHealth)"正在读取" else "读取睡眠记录",{
            if(HealthConnectClient.getSdkStatus(context)!=HealthConnectClient.SDK_AVAILABLE) healthMessage="Health Connect 当前不可用或需要更新"
            else scope.launch {
                runCatching { HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions() }
                    .onSuccess { granted -> if(sleepReadPermission in granted) loadHealthSessions() else sleepPermissionLauncher.launch(setOf(sleepReadPermission)) }
                    .onFailure { healthMessage="无法连接 Health Connect：${it.message?:"请稍后重试"}" }
            }
        },Modifier.fillMaxWidth(),enabled=!readingHealth)
        if(healthMessage.isNotBlank())Text(healthMessage,style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)
        healthSessions.take(8).forEach { session ->
            TextButton(onClick={
                importedSession=session
                selectedDate=session.date
                healthMessage="已填入 ${healthSleepSourceLabel(context,session.sourcePackage)} 记录 · 核对时间后保存"
            },modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Text("${session.date} · ${session.bedtime}–${session.wakeTime}",color=AppColors.Text)
                    Text("来源：${healthSleepSourceLabel(context,session.sourcePackage)}",style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)
                }
            }
        }
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("记录日期",style=MaterialTheme.typography.labelMedium,color=AppColors.Muted)
            TextButton(onClick={choosingDate=true}){Text(selectedDate)}
            if(selectedDate!=db.day())Text("可补录或修正过去日期",style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)
        }
        Text("没有记录的日期不处罚",style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)
        if(db.pendingSleepPenalty()>0) Text("待后续奖励抵扣 ${db.pendingSleepPenalty()/60000} 分钟",style=MaterialTheme.typography.labelMedium,color=AppColors.Accent)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TimeField("就寝目标",bedTarget,{bedTarget=it},Modifier.weight(1f))
            TimeField("起床目标",wakeTarget,{wakeTarget=it},Modifier.weight(1f))
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            MinuteField("晚睡扣减（分钟）",bedPenalty,{bedPenalty=it},Modifier.weight(1f))
            MinuteField("晚起扣减（分钟）",wakePenalty,{wakePenalty=it},Modifier.weight(1f))
        }
        TextButton(onClick={
            val result=runCatching{db.saveSleepTargets(bedTarget,wakeTarget,bedPenalty.toInt(),wakePenalty.toInt())}
            message=if(result.isSuccess)"睡眠目标已保存" else "请检查时间格式和扣减分钟数"
            if(result.isSuccess)onChanged()
        }){Text("保存睡眠目标")}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TimeField("实际就寝",bedtime,{bedtime=it;if(sleepSource.startsWith("health_connect:")&&!sleepSource.endsWith(":edited"))sleepSource += ":edited"},Modifier.weight(1f))
            TimeField("实际起床",wakeTime,{wakeTime=it;if(sleepSource.startsWith("health_connect:")&&!sleepSource.endsWith(":edited"))sleepSource += ":edited"},Modifier.weight(1f))
        }
        Row(verticalAlignment=Alignment.CenterVertically) {
            Checkbox(exception,onCheckedChange={exception=it})
            Text("今天是例外日，免除睡眠扣减",style=MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(reason,{reason=it},label={Text(if(exception||saved!=null)"例外或修正原因" else "备注（可选）")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        PrimaryAction(if(sleepSource.startsWith("health_connect:"))"确认并保存健康记录" else if(saved==null)"保存手动记录" else "更新手动记录",{
            val result=runCatching{db.recordSleep(selectedDate,bedtime,wakeTime,exception,reason.trim(),sleepSource)}
            message=if(result.isSuccess) {
                "${if(exception)"例外已保存" else "记录已保存"} · 待后续奖励抵扣 ${db.pendingSleepPenalty()/60000} 分钟"
            } else "请检查时间格式；修正或设为例外时需填写原因"
            if(result.isSuccess)onChanged()
        },Modifier.fillMaxWidth())
        saved?.let { Text(if(it.excepted)"$selectedDate 例外：${it.reason}" else "$selectedDate 已记录 · ${it.bedtime} 就寝 / ${it.wakeTime} 起床 · 来源：${if(it.source.startsWith("health_connect:"))healthSleepSourceLabel(context,it.source.removePrefix("health_connect:").removeSuffix(":edited")) else "手动记录"}",style=MaterialTheme.typography.bodySmall,color=AppColors.Muted) }
        if(message.isNotBlank())Text(message,style=MaterialTheme.typography.bodySmall,color=AppColors.Accent)
    }
    if(choosingDate) {
        val today=LocalDate.parse(db.day())
        val state=rememberDatePickerState(
            initialSelectedDateMillis=LocalDate.parse(selectedDate).toEpochDay()*86_400_000L,
            selectableDates=object:SelectableDates {
                override fun isSelectableDate(utcTimeMillis:Long)=LocalDate.ofEpochDay(utcTimeMillis/86_400_000L)<=today
            },
        )
        DatePickerDialog(onDismissRequest={choosingDate=false},confirmButton={TextButton(onClick={state.selectedDateMillis?.let{selectedDate=LocalDate.ofEpochDay(it/86_400_000L).toString()};choosingDate=false}){Text("选择")}},dismissButton={TextButton(onClick={choosingDate=false}){Text("取消")}}){DatePicker(state,showModeToggle=false)}
    }
}

@Composable
private fun TimeField(label:String,value:String,onValue:(String)->Unit,modifier:Modifier) {
    OutlinedTextField(value,onValueChange={onValue(it.filter{c->c.isDigit()||c==':'}.take(5))},label={Text(label)},modifier=modifier,singleLine=true)
}

@Composable
private fun MinuteField(label:String,value:String,onValue:(String)->Unit,modifier:Modifier) {
    OutlinedTextField(value,onValueChange={onValue(it.filter(Char::isDigit).take(3))},label={Text(label)},modifier=modifier,singleLine=true)
}
