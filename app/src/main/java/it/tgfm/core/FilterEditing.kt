package it.tgfm.core

/** The model concerns the name only. File types are configured separately. */
object FilterEditing {
 fun fileNameOnly(value: String): String =
  if (Regex("(?:^|[_ -])[0-9]{1,2}\\.[0-9]{1,2}\\.[0-9]{2,4}$").containsMatchIn(value)) value
  else value.substringBeforeLast('.', value)
 fun nameOnly(value: String): String {
  val suffix = value.substringAfterLast('.', "")
  // A dotted numeric date or a placeholder is part of the name, not an extension.
  return if (Regex("(?=[A-Za-z0-9+-]*[A-Za-z])[A-Za-z0-9+-]{1,16}").matches(suffix)) value.substringBeforeLast('.') else value
 }
 fun withDateRoot(currentTemplate: String, root: String): String {
  val model = nameOnly(currentTemplate)
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
