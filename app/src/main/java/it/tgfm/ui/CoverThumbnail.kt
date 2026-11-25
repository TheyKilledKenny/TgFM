package it.tgfm.ui

import it.tgfm.R
import it.tgfm.Words

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import it.tgfm.core.Channel
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import it.tgfm.core.CoverImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Only composed (visible) previews are requested; no attachment is selected or saved. */
@Composable fun CoverThumbnail(cover: CoverImage, vm: AppViewModel, modifier: Modifier = Modifier,
 description: String? = Words.get(R.string.cover_from_previous_message), scale: ContentScale = ContentScale.Fit,
 localPath: String? = null, fallback: @Composable () -> Unit = {}) = key(cover, localPath) {
 val preview by produceState<ImageBitmap?>(null, cover) {
  value = withContext(Dispatchers.Default) {
   runCatching {
    if(cover.miniature.isBlank() || cover.miniature.length > 512 * 1024) null else {
     val bytes = Base64.decode(cover.miniature, Base64.DEFAULT)
     decodePreview(bytes)
    }
   }.getOrNull()
  }
  if (localPath != null) {
   val saved = withContext(Dispatchers.IO) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(localPath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else BitmapFactory.decodeFile(localPath, BitmapFactory.Options().apply {
     inSampleSize = sampleSize(bounds)
    })?.asImageBitmap()
   }
   if (saved != null) { value = saved; return@produceState }
  }
  if(cover.fileId != 0 || cover.remoteId.isNotEmpty()) {
   val bitmap = withContext(Dispatchers.IO) {
    val file = vm.coverFile(cover)
    if(file == null) null else runCatching { decodePreview(file.readBytes()) }.getOrNull()
   }
   if(bitmap != null) value = bitmap
  }
 }
 Box(modifier, contentAlignment = Alignment.Center) {
  val bitmap = preview
  if (bitmap == null) fallback() else Image(bitmap, description, Modifier.fillMaxSize(), contentScale = scale)
 }
}

@Composable internal fun ChannelAvatar(channel: Channel, vm: AppViewModel, modifier: Modifier = Modifier.size(32.dp)) = key(channel.id) {
 val state by vm.state.collectAsState()
 val listed = remember(state.channels, channel.id) { state.channels.firstOrNull { it.id == channel.id } }
 val photo by produceState(channel.avatar, listed, state.auth, channel.avatar) {
  value = if (listed != null) listed.avatar else channel.avatar ?: vm.channelAvatar(channel.id)
 }
 val frame = modifier.clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer)
 val initial = remember(channel.title) {
  val title = channel.title.trim()
  if (title.isEmpty()) "" else title.substring(0, title.offsetByCodePoints(0, 1)).uppercase()
 }
 val placeholder: @Composable () -> Unit = { Text(initial, color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.labelMedium) }
 val avatar = photo
 if (avatar == null) Box(frame, contentAlignment = Alignment.Center) { placeholder() }
 else CoverThumbnail(avatar, vm, frame, description = null, scale = ContentScale.Crop, fallback = placeholder)
}
private fun sampleSize(bounds: BitmapFactory.Options): Int {
 var sample = 1
 while(maxOf(bounds.outWidth, bounds.outHeight) / sample > 400) sample *= 2
 return sample
}
private fun decodePreview(bytes: ByteArray): ImageBitmap? {
 val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
 BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
 if(bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
 return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds) })?.asImageBitmap()
}
