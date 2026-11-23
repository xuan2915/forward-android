package com.focus.prototype

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.DatePicker
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import java.time.LocalDate

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
    var error by remember(task.id){mutableStateOf("")}
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.58f)).clickable(onClick=onDismiss), contentAlignment=Alignment.Center) {
        Surface(
            modifier=Modifier.fillMaxWidth().padding(horizontal=18.dp).heightIn(max=700.dp).border(.7.dp, AppColors.Border.copy(alpha=.45f), MaterialTheme.shapes.extraLarge).clickable(onClick={}),
            shape=MaterialTheme.shapes.extraLarge,
            color=MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.background(Brush.verticalGradient(listOf(Color.White.copy(alpha=.08f), Color.Transparent))).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("选择新的任务日期", style=MaterialTheme.typography.titleMedium)
                DatePicker(state, showModeToggle=false)
                if(reasonRequired) OutlinedTextField(reason,onValueChange={reason=it},label={Text("计划变更原因")},modifier=Modifier.fillMaxWidth())
                if(error.isNotBlank()) Text(error,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.End) {
                    TextButton(onClick=onDismiss) { Text("取消") }
                    TextButton(enabled=selected!=null&&selected!=LocalDate.parse(task.date)&&(!reasonRequired||reason.isNotBlank()),onClick={selected?.let{date->runCatching{db.moveTask(task,date.toString(),reason.trim())}.onSuccess(onMoved).onFailure{error=it.message?:"无法调整任务日期"}}}) { Text("移至此日") }
                }
            }
        }
    }
}

@Composable
fun TaskDeleteDialog(task:Task,db:Database,onDismiss:()->Unit,onDeleted:()->Unit) {
    val reasonRequired=db.contract(task.date).confirmed
    var reason by remember(task.id){mutableStateOf("")}
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.58f)).clickable(onClick=onDismiss), contentAlignment=Alignment.Center) {
        Surface(
            modifier=Modifier.fillMaxWidth().padding(horizontal=24.dp).border(.7.dp, AppColors.Border.copy(alpha=.45f), MaterialTheme.shapes.extraLarge).clickable(onClick={}),
            shape=MaterialTheme.shapes.extraLarge,
            color=MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.background(Brush.verticalGradient(listOf(Color.White.copy(alpha=.08f), Color.Transparent))).padding(20.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("删除这项任务？", style=MaterialTheme.typography.titleLarge)
                Text(if(reasonRequired) "删除未完成记录 · 已确认的计划会保留变更原因" else "只删除未完成记录")
                if(reasonRequired) OutlinedTextField(reason,onValueChange={reason=it},label={Text("删除原因")},modifier=Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.End) {
                    TextButton(onClick=onDismiss) { Text("保留") }
                    TextButton(enabled=!reasonRequired||reason.isNotBlank(),onClick={db.deleteTask(task,reason.trim());onDeleted()}) { Text("删除") }
                }
            }
        }
    }
}
