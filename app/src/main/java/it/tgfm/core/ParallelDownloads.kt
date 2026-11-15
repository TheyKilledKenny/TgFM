package it.tgfm.core

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

object ParallelDownloads {
 const val LIMIT = 4
 suspend fun <T> run(items: List<T>, operation: suspend (T) -> Unit) = coroutineScope {
  val permits = Semaphore(LIMIT)
  items.map { item -> async { permits.withPermit { operation(item) } } }.awaitAll()
  Unit
 }
}
data class DownloadProgress(val name: String, val bytes: Long = 0, val size: Long = 0, val uploaded: Long? = null) {
 val fraction: Float? get() = if (size > 0) (bytes.toFloat() / size).coerceIn(0f, 1f) else null
}
