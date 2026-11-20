package it.tgfm.data

import android.content.*
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.*
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import java.io.File

/** Read-only virtual members; URI grants cover exactly one member of one volume set. */
class ArchiveProvider : ContentProvider() {
 override fun onCreate() = true
 private fun sources(uri: Uri): List<File> {
  val root = WorkFolder(requireNotNull(context)).root.canonicalFile
  return uri.pathSegments.map { File(it).canonicalFile.also { file ->
   require(file.parentFile == root && !file.name.startsWith('.') && file.isFile) { "Archivio non disponibile nella cartella di lavoro." }
  } }.also { require(it.isNotEmpty()) }
 }
 private fun entry(uri: Uri) = ArchiveReader(sources(uri)).use { it.seekEntry(requireNotNull(uri.getQueryParameter("entry")).toInt()) }
 override fun getType(uri: Uri) = mime(entry(uri).name)
 override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
  val entry = entry(uri)
  val columns = (projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).filter { it in setOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE) }.toTypedArray()
  return MatrixCursor(columns, 1).apply { addRow(columns.map { if(it == OpenableColumns.DISPLAY_NAME) entry.name.substringAfterLast('/') else entry.size.takeIf { it >= 0 } }) }
 }
 override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
  require(mode == "r")
  val files = sources(uri)
  val index = requireNotNull(uri.getQueryParameter("entry")).toInt()
  val expected = files.map { it.length() to it.lastModified() }
  var reader = ArchiveReader(files)
  val entry = try { reader.seekEntry(index) } catch(e: Exception) { reader.close(); throw e }
  val thread = HandlerThread("TgFM archive reader").apply { start() }
  var position = 0L
  val callback = object : ProxyFileDescriptorCallback() {
   override fun onGetSize() = entry.size
   override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
    try {
     check(files.map { it.length() to it.lastModified() } == expected && files.all { it.isFile }) { "Archivio modificato o eliminato." }
     if (offset < position) { reader.close(); reader = ArchiveReader(files); reader.seekEntry(index); position = 0 }
     val skip = ByteArray(256 * 1024)
     while(position < offset) {
      val n = reader.read(skip, minOf(skip.size.toLong(), offset-position).toInt())
      if(n < 0) return 0
      position += n
     }
     val n = reader.read(data, minOf(size, data.size)).coerceAtLeast(0)
     position += n
     return n
    } catch(e: Exception) { throw ErrnoException("Lettura archivio: ${e.message}", OsConstants.EIO, e) }
   }
   override fun onRelease() { try { reader.close() } finally { thread.quitSafely() } }
  }
  return try { requireNotNull(context).getSystemService(StorageManager::class.java).openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY, callback, Handler(thread.looper)) }
  catch(e: Exception) { callback.onRelease(); throw e }
 }
 override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
 override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
 override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
 companion object {
  fun mime(name: String) = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
  fun uri(context: Context, files: List<File>, entry: ArchiveReader.Entry): Uri = Uri.Builder().scheme("content").authority(context.packageName+".archives").apply {
   files.forEach { appendPath(it.absolutePath) }; appendQueryParameter("entry", entry.index.toString())
  }.build()
 }
}
