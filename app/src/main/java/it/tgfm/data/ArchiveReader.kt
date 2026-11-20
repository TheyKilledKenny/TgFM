package it.tgfm.data

import it.tgfm.core.ArchiveNames
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry

/** Owns one decoder, never writes expanded data to disk. */
class ArchiveReader(private val files: List<File>) : Closeable {
 data class Entry(val index: Int, val name: String, val size: Long)
 private val zip = if(files.any { it.name.endsWith(".zip", true) || Regex("(?i).*\\.zip\\.\\d{3,}$").matches(it.name) })
  net.lingala.zip4j.ZipFile(files.firstOrNull { it.name.endsWith(".zip", true) } ?: files.first()) else null
 private val handle = if(zip == null) Archive.readNew() else 0L
 private var zipInput: java.io.InputStream? = null
 private var volumeInput: it.tgfm.core.VolumeInput? = null
 private var closed = false
 private var index = -1
 init {
  try {
   require(files.isNotEmpty() && files.all { it.isFile }) { "Parti dell'archivio non disponibili." }
   if(files.any { Regex("(?i).*\\.r\\d{2,}$").matches(it.name) }) require(files.any { it.name.endsWith(".rar", true) && ArchiveNames.part(it.name)==null }) { "Manca il volume iniziale .rar." }
   if(files.any { Regex("(?i).*\\.z\\d{2,}$").matches(it.name) }) require(files.any { it.name.endsWith(".zip", true) }) { "Manca il volume finale .zip." }
   val numbered = files.mapNotNull { ArchiveNames.part(it.name) }
   if(numbered.isNotEmpty()) {
    val numbers = numbered.map { it.number }.sorted()
    val expected = if(files.any { it.name.endsWith(".rar", true) && ArchiveNames.part(it.name)==null }) 1 else numbered.first().first
    require(numbers.first() == expected && numbers.zipWithNext().all { (a,b) -> b==a+1 }) { "Mancano parti dell'archivio. Amplia il periodo di lettura." }
   }
   if(zip == null) {
   Archive.readSupportFilterAll(handle)
   Archive.readSupportFormatAll(handle)
   if(files.first().name.matches(Regex("(?i).*\\.7z\\.\\d{3,}$"))) {
    val input = it.tgfm.core.VolumeInput(files).also { volumeInput = it }
    val buffer = ByteArray(256 * 1024)
    Archive.readSetCallbackData(handle, input)
    Archive.readSetReadCallback<it.tgfm.core.VolumeInput>(handle) { _, data ->
     val n = data.read(buffer)
     ByteBuffer.wrap(buffer, 0, n.coerceAtLeast(0))
    }
    Archive.readSetSeekCallback<it.tgfm.core.VolumeInput>(handle) { _, data, offset, whence -> data.seek(offset, whence) }
    Archive.readOpen1(handle)
   } else Archive.readOpenFileNames(handle, files.map { it.absolutePath.toByteArray() }.toTypedArray(), 256 * 1024L)
   }
  } catch (e: Throwable) { close(); throw e }
 }
 fun next(): Entry? {
  if(zip != null) {
   zipInput?.close(); zipInput = null
   val headers = zip.fileHeaders
   while(++index < headers.size) {
    val header = headers[index]
    if(header.isDirectory) continue
    require(!header.isEncrypted) { "Archivio protetto da password: invialo come archivio originale." }
    zipInput = zip.getInputStream(header)
    return Entry(index, ArchiveNames.safeEntry(header.fileName), header.uncompressedSize)
   }
   return null
  }
  while (true) {
   val entry = Archive.readNextHeader(handle)
   if (entry == 0L) return null
   index++
   require(ArchiveEntry.hardlink(entry) == null && ArchiveEntry.symlink(entry) == null) { "Collegamenti nell'archivio non supportati." }
   val type = ArchiveEntry.filetype(entry)
   if (type == 0x4000) continue
   require(type == 0x8000 || type == 0) { "Tipo di file non supportato nell'archivio." }
   val name = ArchiveEntry.pathnameUtf8(entry) ?: ArchiveEntry.pathname(entry)?.toString(Charsets.UTF_8) ?: error("Nome mancante nell'archivio.")
   return Entry(index, ArchiveNames.safeEntry(name), ArchiveEntry.size(entry))
  }
 }
 fun seekEntry(target: Int): Entry {
  while (true) { val entry = next() ?: error("File non trovato nell'archivio."); if (entry.index == target) return entry }
 }
 fun read(bytes: ByteArray, count: Int = bytes.size): Int {
  if(zip != null) return requireNotNull(zipInput).read(bytes, 0, count)
  val buffer = ByteBuffer.wrap(bytes, 0, count)
  Archive.readData(handle, buffer)
  return buffer.position().let { if (it == 0) -1 else it }
 }
 override fun close() { if(closed) return; closed = true; try { if(handle != 0L) Archive.free(handle) } finally { volumeInput?.close(); zipInput?.close(); zip?.close() } }
 companion object {
  fun entries(files: List<File>): List<Entry> = ArchiveReader(files).use { reader -> buildList { while(true) add(reader.next() ?: break) } }
  fun volumes(file: File): List<File> {
   val files = file.parentFile!!.listFiles().orEmpty().filter { it.isFile }
   val logical = ArchiveNames.family(file.name)
   val matching = files.filter { ArchiveNames.family(it.name) == logical }
   return matching.sortedBy { ArchiveNames.part(it.name)?.number ?: if(it.name.endsWith(".zip", true)) Int.MAX_VALUE else 0 }
  }
 }
}
