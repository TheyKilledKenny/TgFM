package it.tgfm.core

/** Preview metadata only: never part of the selected attachment's download/export. */
data class CoverImage(val fileId: Int = 0, val miniature: String = "", val caption: String = "", val messageId: Long? = null, val remoteId: String = "")

/** History arrives newest first, with overlapping pages and possible text-only messages. */
class PreviousMessageCovers {
 private var newest: Pair<Long, Long>? = null
 private var followingFiles = emptyList<Attachment>()
 fun accept(chatId: Long, messageId: Long, files: List<Attachment>, photo: CoverImage?): List<Attachment> {
  val previous = newest
  if(previous?.first == chatId && messageId >= previous.second) return emptyList()
  val decorated = if(previous?.first == chatId && photo != null) followingFiles.filter {
   it.category in setOf(Category.DOCUMENT, Category.VIDEO, Category.AUDIO) && !it.mime.startsWith("image/")
  }.map { it.copy(cover = photo) } else emptyList()
  newest = chatId to messageId
  followingFiles = files
  return decorated
 }
}
