package com.focus.prototype

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalTime

@Composable
internal fun SleepCard(db:Database,refreshKey:Int,onChanged:()->Unit) {
    val targets=remember(refreshKey){db.sleepTargets()}
    val saved=remember(refreshKey){db.sleepRecord()}
    var bedTarget by remember(refreshKey){mutableStateOf(targets[0])}
    var wakeTarget by remember(refreshKey){mutableStateOf(targets[1])}
    var bedPenalty by remember(refreshKey){mutableStateOf(targets[2])}
    var wakePenalty by remember(refreshKey){mutableStateOf(targets[3])}
    var bedtime by remember(refreshKey){mutableStateOf(saved?.bedtime?:targets[0])}
    var wakeTime by remember(refreshKey){mutableStateOf(saved?.wakeTime?:targets[1])}
    var exception by remember(refreshKey){mutableStateOf(saved?.excepted?:false)}
    var reason by remember(refreshKey){mutableStateOf("")}
    var message by remember(refreshKey){mutableStateOf("")}
    QuietSurface {
        Text("睡眠记录",style=MaterialTheme.typography.titleLarge)
        Text("目标：${targets[0]} 前就寝 · ${targets[1]} 前起床。只按你确认的手动记录结算，不会把手机静置当作睡着。扣减会从之后发放的奖励中抵扣，不追回已使用额度。",style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)
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
            message=if(result.isSuccess)"睡眠目标已保存。" else "请检查时间格式（HH:mm）和扣减分钟数。"
            if(result.isSuccess)onChanged()
        }){Text("保存睡眠目标")}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TimeField("实际就寝",bedtime,{bedtime=it},Modifier.weight(1f))
            TimeField("实际起床",wakeTime,{wakeTime=it},Modifier.weight(1f))
        }
        Row(verticalAlignment=Alignment.CenterVertically) {
            Checkbox(exception,onCheckedChange={exception=it})
            Text("今天是例外日，免除睡眠扣减",style=MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(reason,{reason=it},label={Text(if(exception||saved!=null)"例外或修正原因" else "备注（可选）")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        PrimaryAction(if(saved==null)"保存手动记录" else "更新手动记录",{
            val result=runCatching{db.recordSleep(db.day(),bedtime,wakeTime,exception,reason.trim())}
            message=if(result.isSuccess) {
                "${if(exception)"例外已保存" else "记录已保存"} · 待后续奖励抵扣 ${db.pendingSleepPenalty()/60000} 分钟"
            } else "请检查时间格式；修改记录或设置例外时需要填写原因。"
            if(result.isSuccess)onChanged()
        },Modifier.fillMaxWidth())
        saved?.let { Text(if(it.excepted)"今日例外：${it.reason}" else "今日已记录 · ${it.bedtime} 就寝 / ${it.wakeTime} 起床",style=MaterialTheme.typography.bodySmall,color=AppColors.Muted) }
        if(message.isNotBlank())Text(message,style=MaterialTheme.typography.bodySmall,color=AppColors.Accent)
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
