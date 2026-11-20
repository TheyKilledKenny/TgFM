package it.tgfm.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import it.tgfm.core.CoverImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Only composed (visible) previews are requested; no attachment is selected or saved. */
@Composable fun CoverThumbnail(cover: CoverImage, vm: AppViewModel, modifier: Modifier = Modifier) {
 val preview by produceState<ImageBitmap?>(null, cover.fileId, cover.miniature) {
  value = withContext(Dispatchers.Default) {
   runCatching {
    if(cover.miniature.isBlank() || cover.miniature.length > 512 * 1024) null else {
     val bytes = Base64.decode(cover.miniature, Base64.DEFAULT)
     decodePreview(bytes)
    }
   }.getOrNull()
  }
  if(cover.fileId != 0) {
   val bitmap = withContext(Dispatchers.IO) {
    val file = vm.coverFile(cover.fileId)
    if(file == null) null else runCatching { decodePreview(file.readBytes()) }.getOrNull()
   }
   if(bitmap != null) value = bitmap
  }
 }
 if(cover.fileId != 0 || cover.miniature.isNotBlank()) {
  val bitmap = preview
  if(bitmap == null) Box(modifier) else Image(bitmap, contentDescription = "Copertina dal messaggio precedente", modifier = modifier, contentScale = ContentScale.Fit)
 }
}
private fun decodePreview(bytes: ByteArray): ImageBitmap? {
 val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
 BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
 if(bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
 var sample = 1
 while(maxOf(bounds.outWidth, bounds.outHeight) / sample > 400) sample *= 2
 return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}
