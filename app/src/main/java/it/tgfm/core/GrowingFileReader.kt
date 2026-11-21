package it.tgfm.core

import it.tgfm.R
import it.tgfm.Words

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

data class AvailableBytes(val file: File?, val readable: Long, val complete: Boolean, val size: Long)

/** Never interprets a temporary EOF or a sparse-file hole as downloaded content. */
class GrowingFileReader(private val snapshot: suspend (Long) -> AvailableBytes, private val stallMillis: Long = 180_000) : Closeable {
 private var input: RandomAccessFile? = null
 private var known: AvailableBytes? = null
 private var availableUntil = 0L
 var position = 0L; private set
 var finalSize: Long? = null; private set
 suspend fun read(buffer: ByteArray): Int {
  require(buffer.isNotEmpty())
  val started = System.nanoTime()
  while (true) {
   currentCoroutineContext().ensureActive()
   val previous = known
   val state = if (previous != null && (previous.complete || position < availableUntil)) previous.copy(readable = (availableUntil - position).coerceAtLeast(0))
    else snapshot(position).also { known = it; availableUntil = position + it.readable }
   require(state.readable >= 0 && state.size >= 0) { Words.get(R.string.invalid_file_availability) }
   if (state.complete && position == state.size) { finalSize = state.size; return -1 }
   check(!state.complete || position < state.size) { Words.get(R.string.file_size_changed_during_upload) }
   if (state.readable > 0 && state.file != null) {
    if (input == null) {
     try { input = RandomAccessFile(state.file, "r") }
     catch (e: java.io.FileNotFoundException) {
      // TDLib may have just renamed the partial to its completed native path.
      known = null
      check((System.nanoTime() - started) / 1_000_000 < stallMillis) { Words.get(R.string.tdlib_file_is_no_longer_available) }
      delay(150); continue
     }
    }
    val count = minOf(buffer.size.toLong(), state.readable, if (state.complete) state.size - position else Long.MAX_VALUE).toInt()
    val stream = requireNotNull(input)
    stream.seek(position)
    val read = stream.read(buffer, 0, count)
    check(read > 0) { Words.get(R.string.could_not_read_the_data_reported_as) }
    position += read
    return read
   }
   check((System.nanoTime() - started) / 1_000_000 < stallMillis) { Words.get(R.string.telegram_has_stopped_providing_new_data_retry) }
   delay(150)
  }
 }
 override fun close() { input?.close(); input = null }
}
