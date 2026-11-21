package it.tgfm.telegram

import it.tgfm.core.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.util.Locale

/** Metadata queries, separated from the native transport to verify pagination and cancellation. */
class TelegramReader(private val send: suspend (JSONObject) -> JSONObject) {
 suspend fun channels(progress: (List<Channel>) -> Unit = {}): List<Channel> {
  val found = linkedMapOf<Long, Channel>()
  for (list in listOf("chatListMain", "chatListArchive")) {
   while (true) {
    currentCoroutineContext().ensureActive()
    try { send(request("loadChats", "chat_list" to request(list), "limit" to 100)) }
    catch (e: TelegramException) { if (e.code == 404) break else throw e }
   }
   // loadChats' 100 is a page size, not the total. Read all loaded IDs, including groups.
   val ids = send(request("getChats", "chat_list" to request(list), "limit" to Int.MAX_VALUE)).getJSONArray("chat_ids")
   for (i in 0 until ids.length()) {
    currentCoroutineContext().ensureActive()
    val chat = send(request("getChat", "chat_id" to ids.getLong(i)))
    if (chat.getJSONObject("type").optString("@type") in setOf("chatTypeSupergroup", "chatTypeBasicGroup")) {
     found[chat.getLong("id")] = Channel(chat.getLong("id"), chat.getString("title"))
     if (found.size % 100 == 0) progress(sorted(found.values))
    }
   }
   progress(sorted(found.values))
  }
  return sorted(found.values)
 }
 private fun sorted(channels: Collection<Channel>) = channels.sortedBy { it.title.lowercase(Locale.ROOT) }

 suspend fun attachments(chatId: Long, window: PostWindow, progress: (List<Attachment>) -> Unit): List<Attachment> {
  val result = linkedMapOf<String, Attachment>()
  var cursor = 0L
  val covers = PreviousMessageCovers()
  fun accept(message: JSONObject, include: Boolean) {
   val files = AttachmentMapper.fromMessage(message)
   covers.accept(chatId, message.getLong("id"), files, AttachmentMapper.coverFromMessage(message)).forEach { decorated ->
    if(decorated.key in result) result[decorated.key] = decorated
   }
   if(include) files.forEach { if(it.key !in result) result[it.key] = it }
  }
  send(request("getChat", "chat_id" to chatId))
  if (window.endExclusive != null) {
   // Jump on Telegram to the last post in the requested period instead of walking newer history.
   val anchor = try { send(request("getChatMessageByDate", "chat_id" to chatId, "date" to window.endExclusive - 1)) }
   catch (e: TelegramException) {
    if (e.code != 404) throw e
    progress(emptyList()); return emptyList()
   }
   if (window.isBefore(anchor.getLong("date"))) { progress(emptyList()); return emptyList() }
   cursor = anchor.getLong("id")
   if (window.contains(anchor.getLong("date"))) accept(anchor, true)
   progress(result.values.toList())
  }
  while (true) {
   currentCoroutineContext().ensureActive()
   val messages = send(request("getChatHistory", "chat_id" to chatId, "from_message_id" to cursor, "offset" to 0, "limit" to 100, "only_local" to false)).getJSONArray("messages")
   var reachedStart = false
   for (i in 0 until messages.length()) {
    val message = messages.getJSONObject(i)
    val date = message.getLong("date")
    if (window.isBefore(date)) { accept(message, false); reachedStart = true; break }
    if (window.contains(date)) accept(message, true)
   }
   // Publish each received page before another cancellable request.
   progress(result.values.toList())
   if (reachedStart || messages.length() == 0) break
   val next = messages.getJSONObject(messages.length() - 1).getLong("id")
   if (next == cursor) break
   cursor = next
  }
  return result.values.toList()
 }
}
