package it.tgfm.core

import it.tgfm.R
import it.tgfm.Words

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

object FileNames {
 fun safe(name: String): String = safe(name, 180)
 fun safe(name: String, maxBytes: Int): String {
  val clean = name.replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]"), "_").trim().trim('.').ifBlank { "allegato" }
  val suffix = clean.substringAfterLast('.', "").takeIf { it.length in 1..12 }?.let { ".$it" }.orEmpty()
  require(maxBytes >= suffix.toByteArray(Charsets.UTF_8).size && maxBytes > 0) { Words.get(R.string.keep_the_extension_and_enter_a_valid, suffix) }
  var stem = if (suffix.isEmpty()) clean else clean.dropLast(suffix.length)
  // Keep UTF-8 filenames comfortably below filesystem limits, including collision suffixes.
  while ((stem + suffix).toByteArray(Charsets.UTF_8).size > maxBytes) stem = stem.dropLast(1)
  return (stem + suffix).ifBlank { "allegato" }
 }
 fun unique(name: String, exists: (String) -> Boolean): String {
  val safe = safe(name)
  if (!exists(safe)) return safe
  val ext = safe.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
  val base = if (ext.isEmpty()) safe else safe.dropLast(ext.length)
  var i = 1
  while (exists("$base ($i)$ext")) i++
  return "$base ($i)$ext"
 }
}
interface MoveDestination {
 fun exists(name: String): Boolean
 fun create(name: String, mime: String): String
 fun write(id: String): OutputStream
 fun read(id: String): InputStream
 fun delete(id: String): Boolean
}
/** A transfer is acknowledged only after close + read-back SHA-256 verification. */
object VerifiedMove {
 fun move(source: File, mime: String, destination: MoveDestination, progress: (Long) -> Unit = {}): String {
  require(source.isFile) { Words.get(R.string.local_file_unavailable) }
  val name = FileNames.unique(source.name, destination::exists)
  val id = destination.create(name, mime.ifBlank { "application/octet-stream" })
  try {
   val digest = MessageDigest.getInstance("SHA-256")
   var bytes = 0L
   source.inputStream().use { input -> destination.write(id).use { out ->
    val buf = ByteArray(64 * 1024)
    while (true) {
     val n = input.read(buf); if (n < 0) break
     out.write(buf, 0, n); digest.update(buf, 0, n); bytes += n; progress(bytes)
    }
    out.flush()
   } }
   val actual = MessageDigest.getInstance("SHA-256")
   var checked = 0L
   destination.read(id).use { input ->
    val buf = ByteArray(64 * 1024)
    while (true) { val n = input.read(buf); if (n < 0) break; actual.update(buf, 0, n); checked += n }
   }
   check(bytes == checked && digest.digest().contentEquals(actual.digest())) { Words.get(R.string.transfer_verification_failed_the_original_was_kept) }
   check(source.delete()) { Words.get(R.string.could_not_delete_the_original) }
   return name
  } catch (e: Exception) {
   val cleaned = runCatching { destination.delete(id) }.getOrDefault(false)
   if (!cleaned) throw java.io.IOException(Words.get(R.string.the_original_was_kept_remove_the_incomplete, name), e)
   throw e
  }
 }
}
fun commonMime(types: List<String>): String = types.map { it.ifBlank { "application/octet-stream" } }.distinct().singleOrNull() ?: "*/*"
