package com.focus.prototype
import androidx.compose.material3.*
import androidx.compose.runtime.*
@Composable fun EmergencyUnlock(db:Database,onChanged:()->Unit){
 var open by remember{mutableStateOf(false)};var reason by remember{mutableStateOf("")}
 TextButton(onClick={open=true}){Text("紧急解锁")}
 if(open)AlertDialog(onDismissRequest={open=false},title={Text("今天临时放行受限应用？")},text={OutlinedTextField(reason,onValueChange={reason=it},label={Text("原因（选填）")},supportingText={Text("保留使用记录 · 不算任务完成，也不发经验或奖励")})},confirmButton={TextButton(onClick={db.emergency(reason=reason);open=false;onChanged()}){Text("紧急解锁")}},dismissButton={TextButton(onClick={open=false}){Text("取消")}})
}
