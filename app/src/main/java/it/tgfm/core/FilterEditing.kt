package it.tgfm.core

/** The model concerns the name only. File types are configured separately. */
object FilterEditing {
 fun nameOnly(value: String, extensions: Set<String> = emptySet()): String {
  val suffix = value.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
  if (suffix.isEmpty()) return value
  val isExtension = extensions.any { it.trim().removePrefix(".").equals(suffix, ignoreCase = true) } ||
   android.webkit.MimeTypeMap.getSingleton().hasExtension(suffix)
  return if (isExtension) value.substringBeforeLast('.') else value
 }
 fun withDateRoot(currentTemplate: String, root: String, extensions: Set<String> = emptySet()): String {
  val model = nameOnly(currentTemplate, extensions)
  val suffix = if (model.contains("{data}")) model.substringAfter("{data}") else ""
  return root + "{data}" + suffix
 }
}

/** Search tokens are ANDed; spaces and filename separators are interchangeable. */
object ListSearch {
 private fun normalize(value: String) = value.lowercase(java.util.Locale.ROOT).replace(Regex("[_\\s-]+"), " ").trim()
 fun matches(text: String, query: String): Boolean {
  val normalized = normalize(text)
  return normalize(query).split(' ').filter { it.isNotEmpty() }.all { it in normalized }
 }
 fun examples(files: List<Attachment>, query: String, labelledKeys: Set<String> = emptySet(), onlyLabelled: Boolean = false): List<Attachment> =
  files.filter { matches(it.name, query) && (!onlyLabelled || it.key in labelledKeys) }
 fun channels(channels: List<Channel>, query: String) = channels.filter { matches(it.title, query) }
}
