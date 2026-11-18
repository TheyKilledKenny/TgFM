package it.tgfm.core

/** Naming recognition only: actual format and completeness are verified by the decoder. */
object ArchiveNames {
 data class Part(val name: String, val number: Int, val first: Int)
 fun part(name: String): Part? {
  Regex("(?i)^(.+)\\.part(\\d+)\\.rar$").matchEntire(name)?.let { return Part(it.groupValues[1]+".rar", it.groupValues[2].toInt(), 1) }
  Regex("(?i)^(.+\\.(?:7z|zip))\\.(\\d{3,})$").matchEntire(name)?.let { return Part(it.groupValues[1], it.groupValues[2].toInt(), 1) }
  Regex("(?i)^(.+)\\.r(\\d{2,})$").matchEntire(name)?.let { return Part(it.groupValues[1]+".rar", it.groupValues[2].toInt()+1, 0) }
  Regex("(?i)^(.+)\\.z(\\d{2,})$").matchEntire(name)?.let { return Part(it.groupValues[1]+".zip", it.groupValues[2].toInt(), 1) }
  return null
 }
 fun family(name: String): String {
  val kind = when {
   Regex("(?i).*\\.part\\d+\\.rar$").matches(name) -> "part-rar"
   Regex("(?i).*\\.(?:7z|zip)\\.\\d{3,}$").matches(name) -> "numbered"
   else -> "standard"
  }
  return (part(name)?.name ?: name).lowercase() + "|" + kind
 }
 fun isArchive(name: String) = part(name) != null || name.lowercase().let { n -> listOf(".zip", ".rar", ".7z", ".tar", ".tar.gz", ".tgz", ".tar.bz2", ".tar.xz").any(n::endsWith) }
 fun group(files: List<Attachment>): List<Attachment> {
  val families = files.mapNotNull { f -> part(f.name)?.let { f.chatId to family(f.name) } }.toSet()
  return files.groupBy { f -> f.chatId to family(f.name) }.flatMap { (key, members) ->
   if (key !in families) members else {
    val sorted = members.sortedBy { part(it.name)?.number ?: if(it.name.endsWith(".zip", true)) Int.MAX_VALUE else 0 }
    if(sorted.map { it.name.lowercase() }.distinct().size != sorted.size) return@flatMap members
    val first = sorted.first()
    listOf(first.copy(name = part(first.name)?.name ?: first.name, size = sorted.sumOf { it.size }, parts = sorted, mime = when((part(first.name)?.name ?: first.name).substringAfterLast('.').lowercase()) { "rar" -> "application/vnd.rar"; "zip" -> "application/zip"; "7z" -> "application/x-7z-compressed"; else -> first.mime }))
   }
  }
 }
 fun members(file: Attachment) = file.parts.ifEmpty { listOf(file) }
 fun safeEntry(name: String): String {
  val path = name.replace('\\', '/')
  require(path.isNotBlank() && !path.startsWith('/') && !Regex("^[A-Za-z]:").containsMatchIn(path) && path.split('/').none { it == ".." } && path.none { it == '\u0000' || it == '\r' || it == '\n' }) { "Percorso non valido nell'archivio: $name" }
  return path.split('/').filter { it.isNotEmpty() && it != "." }.joinToString("/").also { require(it.isNotEmpty()) }
 }
}
