package it.tgfm.telegram

import it.tgfm.core.*
import org.json.JSONArray
import org.json.JSONObject

/** Reads metadata only. Does not invoke any TDLib download or thumbnail request. */
object AttachmentMapper {
 fun coverFromMessage(message: JSONObject): CoverImage? {
  val content = message.optJSONObject("content") ?: return null
  // Exclude web previews, stickers and pictures embedded in unrelated content.
  if(content.optString("@type") != "messagePhoto") return null
  val photo = content.optJSONObject("photo") ?: return null
  val miniature = photo.optJSONObject("minithumbnail")?.optString("data").orEmpty()
  val sizes = photo.optJSONArray("sizes")
  val small = if(sizes == null) null else (0 until sizes.length()).map { sizes.getJSONObject(it) }
   .filter { it.optInt("width") in 1..400 && it.optInt("height") in 1..400 }
   .maxByOrNull { it.optLong("width") * it.optLong("height") }
  val file = small?.optJSONObject("photo")
  val id = if(file != null && file.optLong("size") <= 256 * 1024 && file.optLong("expected_size") <= 256 * 1024) file.optInt("id") else 0
  return CoverImage(id, miniature, content.optJSONObject("caption")?.optString("text").orEmpty(), message.getLong("id"))
 }
 fun fromMessage(message: JSONObject): List<Attachment> {
  val result = linkedMapOf<Int, Attachment>()
  val content = message.optJSONObject("content") ?: return emptyList()
  val caption = content.optJSONObject("caption")?.optString("text") ?: content.optJSONObject("text")?.optString("text") ?: ""
  fun add(file: JSONObject?, owner: JSONObject, category: Category, extension: String, defaultMime: String) {
   if (file == null || file.optInt("id") == 0) return
   if (file.optJSONObject("local")?.let { !it.optBoolean("can_be_downloaded", true) && !it.optBoolean("is_downloading_completed") } == true) return
   val id = file.getInt("id")
   val suppliedMime = owner.optString("mime_type")
   val localExtension = if (extension == "bin") mimeByExtension.entries.firstOrNull { it.value == suppliedMime }?.key ?: extension else extension
   val name = owner.optString("file_name").ifBlank { "${category.name.lowercase(java.util.Locale.ROOT)}_${message.optLong("id")}_$id.$localExtension" }
   val mime = suppliedMime.ifBlank { mimeByExtension[name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)] ?: defaultMime }
   result[id] = Attachment(message.optLong("chat_id"), message.optLong("id"), id, name, mime, file.optLong("size").takeIf { it > 0 } ?: file.optLong("expected_size"), message.optLong("date"), caption, category)
  }
  fun visit(obj: JSONObject) {
   when (obj.optString("@type")) {
    "document" -> { add(obj.optJSONObject("document"), obj, Category.DOCUMENT, "bin", "application/octet-stream"); return }
    "audio" -> { add(obj.optJSONObject("audio"), obj, Category.AUDIO, "mp3", "audio/mpeg"); return }
    "voiceNote" -> { add(obj.optJSONObject("voice"), obj, Category.AUDIO, "ogg", "audio/ogg"); return }
    "videoNote" -> { add(obj.optJSONObject("video"), obj, Category.VIDEO, "mp4", "video/mp4"); return }
    "video" -> { add(obj.optJSONObject("video"), obj, Category.VIDEO, "mp4", "video/mp4"); return }
    "animation" -> { add(obj.optJSONObject("animation"), obj, Category.ANIMATION, "mp4", "video/mp4"); return }
    "sticker" -> {
     val (ext, mime) = when (obj.optJSONObject("format")?.optString("@type")) { "stickerFormatTgs" -> "tgs" to "application/x-tgsticker"; "stickerFormatWebm" -> "webm" to "video/webm"; else -> "webp" to "image/webp" }
     add(obj.optJSONObject("sticker"), obj, Category.STICKER, ext, mime); return
    }
    "photo" -> {
     val sizes = obj.optJSONArray("sizes") ?: return
     val best = (0 until sizes.length()).map { sizes.getJSONObject(it) }.maxByOrNull { it.optLong("width") * it.optLong("height") }
     add(best?.optJSONObject("photo"), obj, Category.IMAGE, "jpg", "image/jpeg"); return
    }
    "thumbnail", "minithumbnail", "file" -> return
   }
   // Handles albums, paid media already accessible, link previews, games and rich messages.
   obj.keys().forEach { key -> when (val child = obj.opt(key)) {
    is JSONObject -> visit(child)
    is JSONArray -> (0 until child.length()).forEach { i -> child.optJSONObject(i)?.let { visit(it) } }
   } }
  }
  visit(content)
  return result.values.toList()
 }
 private val mimeByExtension = linkedMapOf("pdf" to "application/pdf", "zip" to "application/zip", "rar" to "application/vnd.rar", "epub" to "application/epub+zip", "7z" to "application/x-7z-compressed", "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "webp" to "image/webp", "gif" to "image/gif", "mp4" to "video/mp4", "webm" to "video/webm", "mp3" to "audio/mpeg", "ogg" to "audio/ogg", "txt" to "text/plain")
}
