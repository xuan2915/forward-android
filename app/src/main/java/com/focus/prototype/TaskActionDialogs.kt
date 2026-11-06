package com.focus.prototype

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.LocalDate

@Composable
fun TaskActionMenu(
    task:Task,
    onDismiss:()->Unit,
    onEdit:()->Unit,
    onMove:()->Unit,
    onDetails:()->Unit,
    onDelete:()->Unit,
) {
    Dialog(onDismissRequest=onDismiss) {
        Surface(shape=MaterialTheme.shapes.extraLarge,color=MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(task.title,style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(bottom=8.dp))
                if(!task.done) TextButton(onClick=onEdit,modifier=Modifier.fillMaxWidth()) {Text("编辑任务")}
                if(!task.done) TextButton(onClick=onMove,modifier=Modifier.fillMaxWidth()) {Text("调整日期")}
                TextButton(onClick=onDetails,modifier=Modifier.fillMaxWidth()) {Text("查看详情")}
                if(!task.done) TextButton(onClick=onDelete,modifier=Modifier.fillMaxWidth()) {Text("删除任务",color=MaterialTheme.colorScheme.error)}
                TextButton(onClick=onDismiss,modifier=Modifier.fillMaxWidth()) {Text("取消")}
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskMoveDialog(task:Task,db:Database,onDismiss:()->Unit,onMoved:(String)->Unit) {
    val today=LocalDate.parse(db.day())
    val state=rememberDatePickerState(
        initialSelectedDateMillis=today.plusDays(1).toEpochDay()*86_400_000L,
        selectableDates=object:SelectableDates {
            override fun isSelectableDate(utcTimeMillis:Long)=LocalDate.ofEpochDay(utcTimeMillis/86_400_000L)>=today
        },
    )
    val selected=state.selectedDateMillis?.let{LocalDate.ofEpochDay(it/86_400_000L)}
    val reasonRequired=db.contract(task.date).confirmed&&task.required
    var reason by remember(task.id){mutableStateOf("")}
    DatePickerDialog(
        onDismissRequest=onDismiss,
        confirmButton={TextButton(enabled=selected!=null&&selected!=LocalDate.parse(task.date)&&(!reasonRequired||reason.isNotBlank()),onClick={selected?.let{onMoved(db.moveTask(task,it.toString(),reason.trim()))}}){Text("移至此日")} },
        dismissButton={TextButton(onClick=onDismiss){Text("取消")}},
    ) {
        Column {
            Text("选择新的任务日期",modifier=Modifier.padding(start=24.dp,top=14.dp),style=MaterialTheme.typography.titleMedium)
            DatePicker(state,showModeToggle=false)
            if(reasonRequired) OutlinedTextField(reason,onValueChange={reason=it},label={Text("计划变更原因")},modifier=Modifier.fillMaxWidth().padding(horizontal=24.dp).padding(bottom=16.dp))
            Text("延期只会移动未完成任务，不会记为完成或发放奖励。",modifier=Modifier.padding(horizontal=24.dp).padding(bottom=12.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TaskDeleteDialog(task:Task,db:Database,onDismiss:()->Unit,onDeleted:()->Unit) {
    val reasonRequired=db.contract(task.date).confirmed
    var reason by remember(task.id){mutableStateOf("")}
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text("删除这项任务？")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("只删除未完成记录。${if(reasonRequired)"已确认的计划会保留变更原因。" else ""}")
            if(reasonRequired) OutlinedTextField(reason,onValueChange={reason=it},label={Text("删除原因")},modifier=Modifier.fillMaxWidth())
        }},
        confirmButton={TextButton(enabled=!reasonRequired||reason.isNotBlank(),onClick={db.deleteTask(task,reason.trim());onDeleted()}){Text("删除")}},
        dismissButton={TextButton(onClick=onDismiss){Text("保留")}},
    )
}
