package it.tgfm.data

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
  require(volume != null) { "Scegli una sottocartella della memoria condivisa o della scheda SD." }
  val relative = canonical.relativeTo(volume).invariantSeparatorsPath
  require(relative != "Android" && !relative.startsWith("Android/") && !relative.split('/').contains(".tgfm-cache")) { "Scegli una cartella esterna ai dati delle app e alla cache TgFM." }
  check(hasAccess()) { "Concedi prima l'accesso ai file in Configura." }
  check(canonical.mkdirs() || canonical.isDirectory) { "Cartella non disponibile." }
  check(canonical.canWrite() && canonical.canRead()) { "Cartella non leggibile o scrivibile." }
  return canonical
 }
 fun fromTree(uri: Uri): File {
  require(uri.authority == "com.android.externalstorage.documents") { "TDLib richiede una cartella locale: i fornitori cloud non sono supportati." }
  val id = DocumentsContract.getTreeDocumentId(uri)
  val volumeId = id.substringBefore(':'); val relative = id.substringAfter(':', "")
  val volume = if (volumeId == "primary") Environment.getExternalStorageDirectory()
   else volumes().firstOrNull { it.name.equals(volumeId, true) } ?: error("Volume non disponibile sul dispositivo.")
  return validate(File(volume, relative))
 }
 fun save(folder: File) { check(prefs.edit().putString("path", validate(folder).absolutePath).commit()) { "Impossibile salvare la cartella." } }
 fun requireReady() {
  validate(root)
  check(cache.mkdirs() || cache.isDirectory) { "Impossibile creare la cache nella cartella scelta." }
  check(cache.canonicalFile.parentFile == root.canonicalFile) { "La cache non deve essere un collegamento a un'altra cartella." }
 }
}
