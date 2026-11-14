package it.tgfm.data

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.documentfile.provider.DocumentFile
import it.tgfm.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

class Storage(private val context: Context) {
 val root = File(context.filesDir, "downloads").apply { mkdirs() }
 val ready = File(root, "ready").apply { mkdirs() }
 private val reservations = mutableMapOf<String, String>()
 private val index = AtomicFile(File(context.filesDir, "download-index.json"))
 private val journal = AtomicFile(File(context.filesDir, "move-journal.json"))
 private fun read(file: AtomicFile) = if (file.baseFile.exists()) JSONArray(String(file.readFully(), Charsets.UTF_8)) else JSONArray()
 private fun write(file: AtomicFile, array: JSONArray) {
  val out = file.startWrite()
  try { out.write(array.toString().toByteArray()); file.finishWrite(out) } catch (e: Exception) { file.failWrite(out); throw e }
 }
 @Synchronized fun list(): List<Downloaded> {
  val entries = read(index)
  val metadata = (0 until entries.length()).map { entries.getJSONObject(it) }.associateBy { it.getString("path") }
  return ready.listFiles().orEmpty().filter { it.isFile && !it.name.startsWith(".") }.sortedBy { it.name }.map { f ->
   val j = metadata[f.absolutePath]
   Downloaded(f.absolutePath, j?.optString("name") ?: f.name, j?.optString("mime") ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()).orEmpty(), f.length(), j?.optString("key").orEmpty())
  }
 }
 private fun persist(files: List<Downloaded>) = write(index, JSONArray(files.map { JSONObject().put("path", it.path).put("name", it.originalName).put("mime", it.mime).put("key", it.sourceKey) }))
 @Synchronized fun clearDownloads() {
  reservations.clear()
  // Never follows arbitrary caller paths; only this app's dedicated directory.
  root.listFiles().orEmpty().forEach { check(it.deleteRecursively()) { "Impossibile svuotare la cartella download." } }
  check(ready.mkdirs() || ready.isDirectory)
  File(root, "tdlib").mkdirs()
  persist(emptyList())
 }
 @Synchronized fun prepareDownloads(clearPrevious: Boolean) {
  reservations.clear()
  check(!hasPendingMove()) { "Completa il recupero dello spostamento prima di avviare nuovi download." }
  if (clearPrevious) clearDownloads()
 }
 /** Reserve a unique destination and discard its previous contents BEFORE network I/O. */
 @Synchronized fun beginDownload(a: Attachment): DownloadTarget {
  val previous = list().filter { it.originalName == a.name && it.path !in reservations.filterKeys { key -> key != a.key }.values }
   .sortedByDescending { it.sourceKey == a.key }.firstOrNull()
  val target = reservations[a.key]?.let(::File) ?: previous?.let { File(it.path) }
   ?: File(ready, FileNames.unique(a.name) { name -> File(ready, name).let { it.exists() || it.absolutePath in reservations.values } })
  check(!target.exists() || target.delete()) { "Impossibile sovrascrivere ${a.name}." }
  persist(list().filterNot { it.path == target.absolutePath })
  reservations[a.key] = target.absolutePath
  return DownloadTarget(a, target.absolutePath)
 }
 @Synchronized fun finishDownload(source: File, target: DownloadTarget): Downloaded {
  check(source.canonicalPath.startsWith(File(root, "tdlib").canonicalPath + File.separator)) { "Percorso TDLib inatteso." }
  check(source.isFile) { "File scaricato non disponibile." }
  check(reservations[target.attachment.key] == target.path) { "Destinazione download non valida." }
  val destination = File(target.path)
  check(!destination.exists()) { "La destinazione è già occupata." }
  // Native TDLib content is renamed on the same filesystem: no extra copy or backup.
  java.nio.file.Files.move(source.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE)
  val a = target.attachment
  val file = Downloaded(destination.absolutePath, a.name, a.mime, destination.length(), a.key)
  persist(list().filterNot { it.path == file.path } + file)
  return file
 }
 fun move(files: List<Downloaded>, treeUri: Uri, progress: (Int, String, Long, Long) -> Unit): List<String> {
  val tree = DocumentFile.fromTreeUri(context, treeUri) ?: throw IOException("Cartella non accessibile.")
  check(tree.canWrite()) { "La cartella non concede il permesso di scrittura." }
  val failures = mutableListOf<String>()
  files.forEachIndexed { i, file ->
   val source = File(file.path)
   check(source.canonicalFile.parentFile == ready.canonicalFile) { "File esterno alla cartella dedicata." }
   try {
    val destination = object : MoveDestination {
     override fun exists(name: String) = tree.findFile(name) != null
     override fun create(name: String, mime: String): String {
      val doc = tree.createFile(mime, name) ?: throw IOException("Impossibile creare $name.")
      val id = doc.uri.toString()
      try { write(journal, JSONArray().put(JSONObject().put("source", file.path).put("destination", id))) }
      catch (e: Exception) { doc.delete(); throw e }
      return id
     }
     override fun write(id: String) = context.contentResolver.openOutputStream(Uri.parse(id), "wt") ?: throw IOException("Destinazione non scrivibile.")
     override fun read(id: String) = context.contentResolver.openInputStream(Uri.parse(id)) ?: throw IOException("Impossibile verificare la destinazione.")
     override fun delete(id: String): Boolean {
      val deleted = DocumentFile.fromSingleUri(context, Uri.parse(id))?.delete() == true
      if (deleted) write(journal, JSONArray())
      return deleted
     }
    }
    VerifiedMove.move(source, file.mime, destination) { bytes -> progress(i, file.originalName, bytes, file.size) }
    write(journal, JSONArray())
   } catch (e: kotlinx.coroutines.CancellationException) { throw e }
   catch (e: Exception) {
    failures += "${file.originalName}: ${e.message}"
    // An unremovable destination must remain in the journal; do not overwrite it.
    if (read(journal).length() > 0) return failures + "Spostamento fermato: risolvi prima il recupero pendente."
   }
   progress(i + 1, file.originalName, 0, 0)
  }
  persist(list())
  return failures
 }
 /** Recover a process interruption: roll back the destination while the source exists. */
 fun recoverMoves(): List<String> {
  val entries = read(journal); val unresolved = JSONArray(); val errors = mutableListOf<String>()
  for (i in 0 until entries.length()) {
   val j = entries.getJSONObject(i)
   if (File(j.getString("source")).exists()) {
    val doc = DocumentFile.fromSingleUri(context, Uri.parse(j.getString("destination")))
    val removed = runCatching { doc != null && (!doc.exists() || doc.delete()) }.getOrDefault(false)
    if (!removed) { unresolved.put(j); errors += "Una copia incompleta non è accessibile. Riconcedi la cartella e riprova il recupero." }
   }
  }
  write(journal, unresolved)
  return errors
 }
 fun hasPendingMove() = read(journal).length() > 0
}

data class DownloadTarget(val attachment: Attachment, val path: String)
