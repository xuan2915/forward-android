package com.focus.prototype
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

@Composable fun ProofActions(task:Task,db:Database,onChanged:()->Unit) {
 val context=LocalContext.current
 fun fileUri(file:File)=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
 val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if(ok) {db.evidence(task,db.setting("capture_${task.id}"));onChanged()} }
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) { val ext=android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(context.contentResolver.getType(uri))?:"bin"; val file=File(context.filesDir,"evidence/${UUID.randomUUID()}.$ext");file.parentFile?.mkdirs();context.contentResolver.openInputStream(uri)!!.use { input ->file.outputStream().use {input.copyTo(it)} };db.evidence(task,file.absolutePath);onChanged()} }
 Row {
  if(!task.done) {
   TextButton(onClick={val file=File(context.filesDir,"evidence/${UUID.randomUUID()}.jpg");file.parentFile?.mkdirs();db.setSetting("capture_${task.id}",file.absolutePath);camera.launch(fileUri(file))}) {Text("拍完整照片")}
   TextButton(onClick={picker.launch(arrayOf("image/*","application/pdf","text/plain"))}) {Text("添加附件")}
  }
  if(task.evidence.isNotBlank()) TextButton(onClick={val f=File(task.evidence); val mime=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension)?:"application/octet-stream"; val intent=Intent(Intent.ACTION_VIEW).setDataAndType(fileUri(f),mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);context.startActivity(Intent.createChooser(intent,"查看完成证明"))}) {Text("查看证明")}
 }
}
