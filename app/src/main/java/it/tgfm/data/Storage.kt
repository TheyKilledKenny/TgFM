package it.tgfm.data

import it.tgfm.R
import it.tgfm.Words

import android.content.Context
import android.util.AtomicFile
import it.tgfm.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Storage(private val context: Context) {
 val folder = WorkFolder(context)
 val root: File get() = folder.root
 val ready: File get() = root
 private val reservations = mutableMapOf<String, String>()
 private val index = AtomicFile(File(context.filesDir, "external-download-index.json"))
 private fun read(file: AtomicFile) = if (file.baseFile.exists()) JSONArray(String(file.readFully(), Charsets.UTF_8)) else JSONArray()
 private fun write(file: AtomicFile, array: JSONArray) {
  val out = file.startWrite()
  try { out.write(array.toString().toByteArray()); file.finishWrite(out) } catch (e: Exception) { file.failWrite(out); throw e }
 }
 @Synchronized fun list(): List<Downloaded> {
  val entries = read(index)
  val metadata = (0 until entries.length()).map { entries.getJSONObject(it) }.associateBy { it.getString("path") }
  return ready.listFiles().orEmpty().filter { it.isFile && !it.name.startsWith(".") && it.canonicalFile.parentFile == ready.canonicalFile }.sortedBy { it.name }.map { f ->
   val j = metadata[f.absolutePath]
   Downloaded(f.absolutePath, j?.optString("name") ?: f.name, j?.optString("mime") ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()).orEmpty(), f.length(), j?.optString("key").orEmpty())
  }
 }
 private fun persist(files: List<Downloaded>) {
  val old = read(index)
  val otherFolders = (0 until old.length()).map { old.getJSONObject(it) }.filter { File(it.getString("path")).parentFile?.canonicalFile != ready.canonicalFile }
  val current = files.filter { it.sourceKey.isNotBlank() }.map { JSONObject().put("path", it.path).put("name", it.originalName).put("mime", it.mime).put("key", it.sourceKey) }
  write(index, JSONArray(otherFolders + current))
 }
 @Synchronized fun clearDownloads() {
  folder.requireReady()
  val entries = read(index)
  val managed = (0 until entries.length()).map { entries.getJSONObject(it).getString("path") }.toSet()
  delete(list().filter { it.path in managed })
 }
 @Synchronized fun prepareDownloads(clearPrevious: Boolean) {
  folder.requireReady()
  reservations.clear()
  if (clearPrevious) clearDownloads()
 }
 @Synchronized fun beginArchiveDownload(a: Attachment, localName: String): DownloadTarget {
  val target = File(ready, localName)
  check(!target.exists()) { Words.get(R.string.name_already_in_use_choose_another_name) }
  reservations[a.key] = target.absolutePath
  return DownloadTarget(a, target.absolutePath)
 }
 /** Reserve a unique destination and discard its previous contents BEFORE network I/O. */
 @Synchronized fun beginDownload(a: Attachment): DownloadTarget {
  val previous = list().filter { it.originalName == a.name && it.path !in reservations.filterKeys { key -> key != a.key }.values }
   .sortedByDescending { it.sourceKey == a.key }.firstOrNull()
  val target = reservations[a.key]?.let(::File) ?: previous?.let { File(it.path) }
   ?: File(ready, FileNames.unique(a.name) { name -> File(ready, name).let { it.exists() || it.absolutePath in reservations.values } })
  check(!target.exists() || target.delete()) { Words.get(R.string.could_not_overwrite, a.name) }
  persist(list().filterNot { it.path == target.absolutePath })
  reservations[a.key] = target.absolutePath
  return DownloadTarget(a, target.absolutePath)
 }
 @Synchronized fun finishDownload(source: File, target: DownloadTarget): Downloaded {
  check(source.canonicalPath.startsWith(File(folder.cache, "tdlib").canonicalPath + File.separator)) { Words.get(R.string.unexpected_tdlib_path) }
  check(source.isFile) { Words.get(R.string.downloaded_file_unavailable) }
  check(reservations[target.attachment.key] == target.path) { Words.get(R.string.invalid_download_destination) }
  val destination = File(target.path)
  check(!destination.exists()) { Words.get(R.string.the_destination_already_exists) }
  // Native TDLib content is renamed on the same filesystem: no extra copy or backup.
  java.nio.file.Files.move(source.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE)
  val a = target.attachment
  val file = Downloaded(destination.absolutePath, a.name, a.mime, destination.length(), a.key)
  persist(list().filterNot { it.path == file.path } + file)
  return file
 }
 @Synchronized fun delete(files: List<Downloaded>) {
  files.forEach { file ->
   val f = File(file.path)
   check(f.canonicalFile.parentFile == ready.canonicalFile && !f.name.startsWith(".")) { Words.get(R.string.file_outside_the_working_folder) }
   check(!f.exists() || f.delete()) { Words.get(R.string.could_not_delete, file.originalName) }
  }
  persist(list())
 }
 @Synchronized fun existing(a: Attachment): Downloaded? = list().firstOrNull { it.sourceKey == a.key && (a.size <= 0 || it.size == a.size) }
 fun clearCache() { deleteTree(folder.cache); folder.requireReady() }
 private fun deleteTree(file: File) {
  if (!file.exists()) return
  java.nio.file.Files.walk(file.toPath()).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { java.nio.file.Files.delete(it) } }
 }
}

data class DownloadTarget(val attachment: Attachment, val path: String)
