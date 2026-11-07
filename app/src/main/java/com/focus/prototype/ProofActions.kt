package com.focus.prototype

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable fun ProofActions(task:Task,db:Database,onChanged:()->Unit) {
 val context=LocalContext.current
 var noLocalCopy by remember(task.id){mutableStateOf(db.setting("proof_no_save_${task.id}")=="true")}
 fun fileUri(file:File)=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
 val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
  val path=db.setting("capture_${task.id}")
  if(ok) {
   if(noLocalCopy) { db.submitProof(task,"相机照片");File(path).delete() }
   else db.evidence(task,path)
   onChanged()
  } else File(path).delete()
 }
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
  if(uri!=null) {
   if(noLocalCopy) db.submitProof(task,"用户选择的附件")
   else {
    val ext=android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(context.contentResolver.getType(uri))?:"bin"
    val file=File(context.filesDir,"evidence/${UUID.randomUUID()}.$ext");file.parentFile?.mkdirs()
    context.contentResolver.openInputStream(uri)!!.use { input ->file.outputStream().use {input.copyTo(it)} }
    db.evidence(task,file.absolutePath)
   }
   onChanged()
  }
 }
 Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
  if(!task.done) {
   Row(verticalAlignment=Alignment.CenterVertically) {
    Checkbox(noLocalCopy,onCheckedChange={noLocalCopy=it;db.setSetting("proof_no_save_${task.id}",it.toString());onChanged()})
    Text("仅提交证明，不在本机保留文件",style=MaterialTheme.typography.bodySmall)
   }
   Row {
    TextButton(onClick={
     val file=File(if(noLocalCopy)context.cacheDir else context.filesDir,"${if(noLocalCopy)"proof" else "evidence"}/${UUID.randomUUID()}.jpg")
     file.parentFile?.mkdirs();db.setSetting("capture_${task.id}",file.absolutePath);camera.launch(fileUri(file))
    }) {Text("拍照证明")}
    TextButton(onClick={picker.launch(arrayOf("image/*","application/pdf","text/plain"))}) {Text("选择文件")}
   }
  }
  if(task.evidence.startsWith("proof-submitted:")) Text("证明已提交 · 文件未保存在本机",style=MaterialTheme.typography.bodySmall,color=AppColors.Muted)
  db.attachments(task).forEachIndexed { index,(id,path)->
   val file=File(path)
   val bitmap by produceState<Bitmap?>(null,path) {
    value=if(file.extension.lowercase() in setOf("jpg","jpeg","png","webp","bmp")) withContext(Dispatchers.IO) {
     val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(path,bounds)
     var sample=1;while(bounds.outWidth/sample>480||bounds.outHeight/sample>480)sample*=2
     BitmapFactory.decodeFile(path,BitmapFactory.Options().apply{inSampleSize=sample})
    } else null
   }
   Row(verticalAlignment=Alignment.CenterVertically) {
    if(bitmap!=null) Surface(shape=RoundedCornerShape(10.dp),color=MaterialTheme.colorScheme.surfaceContainer) {
     Image(bitmap!!.asImageBitmap(),contentDescription="完成证明缩略图",modifier=Modifier.size(56.dp),contentScale=ContentScale.Crop)
    }
    TextButton(modifier=Modifier.weight(1f),onClick={
     val mime=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)?:"application/octet-stream"
     val intent=Intent(Intent.ACTION_VIEW).setDataAndType(fileUri(file),mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
     context.startActivity(Intent.createChooser(intent,"查看完成证明"))
    }) {Text("证明 ${index+1} · ${file.extension.uppercase()}")}
    TextButton(onClick={db.removeAttachment(task,id);onChanged()}){Text("移除")}
   }
  }
 }
}
