package it.tgfm.data

import it.tgfm.R
import it.tgfm.Words

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import java.io.File

/** TDLib needs a real local path, not a document-provider URI. */
class WorkFolder(private val context: Context) {
 private val prefs = context.getSharedPreferences("work-folder", Context.MODE_PRIVATE)
 val root: File get() = File(prefs.getString("path", null) ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TgFM").absolutePath)
 val cache: File get() = File(root, ".tgfm-cache")
 fun hasAccess() = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
  else ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
 private fun volumes(): List<File> = (listOf(Environment.getExternalStorageDirectory()) + context.getExternalFilesDirs(null).filterNotNull().map {
  File(it.absolutePath.substringBefore("/Android/"))
 }).map { it.canonicalFile }.distinct()
 fun validate(folder: File): File {
  val canonical = folder.canonicalFile
  val volume = volumes().firstOrNull { canonical.path.startsWith(it.path + File.separator) }
  require(volume != null) { Words.get(R.string.choose_a_subfolder_in_shared_storage_or) }
  val relative = canonical.relativeTo(volume).invariantSeparatorsPath
  require(relative != "Android" && !relative.startsWith("Android/") && !relative.split('/').contains(".tgfm-cache")) { Words.get(R.string.choose_a_folder_outside_app_data_and) }
  check(hasAccess()) { Words.get(R.string.grant_file_access_in_settings_first) }
  check(canonical.mkdirs() || canonical.isDirectory) { Words.get(R.string.folder_unavailable) }
  check(canonical.canWrite() && canonical.canRead()) { Words.get(R.string.cannot_read_or_write_this_folder) }
  return canonical
 }
 fun fromTree(uri: Uri): File {
  require(uri.authority == "com.android.externalstorage.documents") { Words.get(R.string.tdlib_requires_a_local_folder_cloud_providers) }
  val id = DocumentsContract.getTreeDocumentId(uri)
  val volumeId = id.substringBefore(':'); val relative = id.substringAfter(':', "")
  val volume = if (volumeId == "primary") Environment.getExternalStorageDirectory()
   else volumes().firstOrNull { it.name.equals(volumeId, true) } ?: error(Words.get(R.string.volume_unavailable_on_this_device))
  return validate(File(volume, relative))
 }
 fun save(folder: File) { check(prefs.edit().putString("path", validate(folder).absolutePath).commit()) { Words.get(R.string.could_not_save_folder_choice) } }
 fun requireReady() {
  validate(root)
  check(cache.mkdirs() || cache.isDirectory) { Words.get(R.string.could_not_create_the_cache_in_the) }
  check(cache.canonicalFile.parentFile == root.canonicalFile) { Words.get(R.string.the_cache_cannot_be_a_link_to) }
 }
}
