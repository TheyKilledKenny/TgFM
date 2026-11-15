package it.tgfm.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/** Shares only regular files directly in the chosen folder; never TDLib cache or app data. */
class WorkFileProvider : ContentProvider() {
 override fun onCreate() = true
 private fun file(uri: Uri): File {
  require(uri.pathSegments.size == 1) { "URI non valido." }
  return checked(requireNotNull(context), File(uri.pathSegments.single()))
 }
 override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
  require(mode == "r") { "Accesso consentito solo in lettura." }
  return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY)
 }
 override fun getType(uri: Uri) = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file(uri).extension.lowercase()) ?: "application/octet-stream"
 override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
  val f = file(uri)
  val columns = (projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).filter { it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE }.toTypedArray()
  return MatrixCursor(columns, 1).apply { addRow(columns.map { if (it == OpenableColumns.DISPLAY_NAME) f.name else f.length() }) }
 }
 override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
 override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
 override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
 companion object {
  private fun checked(context: Context, file: File): File {
   val root = WorkFolder(context).root.canonicalFile
   require(file.canonicalFile.parentFile == root && !file.name.startsWith(".") && file.isFile) { "File non disponibile nella cartella di lavoro." }
   return file
  }
  fun uri(context: Context, file: File): Uri = Uri.Builder().scheme("content").authority(context.packageName + ".files").appendPath(checked(context, file).absolutePath).build()
 }
}
