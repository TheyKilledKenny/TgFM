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
     found[chat.getLong("id")] = Channel(chat.getLong("id"), chat.getString("title"), avatar = AttachmentMapper.chatAvatar(chat))
     if (found.size % 100 == 0) progress(sorted(found.values))
    }
   }
   progress(sorted(found.values))
  }
  return sorted(found.values)
 }
 private fun sorted(channels: Collection<Channel>) = channels.sortedBy { it.title.lowercase(Locale.ROOT) }

 /** Deliver a proven slice and its exclusive resume boundary together. */
 suspend fun readHistory(chatId: Long, span: HistoryRange, fromHead: Boolean = false, page: (HistoryRange, List<JSONObject>) -> Unit) {
  var upper = span.end
  suspend fun history(from: Long): List<JSONObject> {
   val array = send(request("getChatHistory", "chat_id" to chatId, "from_message_id" to from,
    "offset" to 0, "limit" to 100, "only_local" to false)).getJSONArray("messages")
   return (0 until array.length()).map(array::getJSONObject)
  }
  suspend fun atDate(date: Long): List<JSONObject> = try {
   listOf(send(request("getChatMessageByDate", "chat_id" to chatId, "date" to date)))
  } catch (e: TelegramException) { if (e.code == 404) emptyList() else throw e }
  while (upper > span.start) {
   currentCoroutineContext().ensureActive()
   var messages = when {
    upper.id != 0L -> history(upper.id)
    fromHead && upper == span.end -> {
     val latest = history(0)
     if (latest.isNotEmpty() && latest.last().getLong("date") >= upper.date) atDate(upper.date - 1) else latest
    }
    else -> atDate(upper.date - 1)
   }
   if (upper.id != 0L && messages.size == 1 && messages.first().getLong("id") == upper.id) {
    // TDLib may repeat just the inclusive cursor. Probe the preceding server ID;
    // an empty reply, rather than the repeated cursor, establishes end of history.
    val preceding = ((upper.id - 1) shr 20 shl 20).coerceAtLeast(1)
    messages = history(preceding)
   }
   val older = messages.filter { HistoryPoint(it.getLong("date"), it.getLong("id")) < upper }
   check(messages.isEmpty() || older.isNotEmpty()) { it.tgfm.Words.get(it.tgfm.R.string.cache_partial) }
   val oldest = older.lastOrNull()?.let { HistoryPoint(it.getLong("date"), it.getLong("id")) }
   val lower = if (oldest == null || oldest < span.start) span.start else oldest
   val examined = HistoryRange(lower, upper)
   page(examined, older.filter { HistoryPoint(it.getLong("date"), it.getLong("id")) in examined })
   upper = lower
  }
 }
 suspend fun attachments(chatId: Long, window: PostWindow, progress: (List<Attachment>) -> Unit): List<Attachment> {
  val result = linkedMapOf<String, Attachment>()
  val covers = PreviousMessageCovers()
  send(request("getChat", "chat_id" to chatId))
  val start = HistoryPoint(window.start ?: 0L)
  val end = HistoryPoint(window.endExclusive ?: (java.time.Instant.now().epochSecond + 1))
  if (start < end) readHistory(chatId, HistoryRange(start,end), fromHead = window.endExclusive == null) { _, messages ->
   for (message in messages) {
    val files = AttachmentMapper.fromMessage(message)
    covers.accept(chatId, message.getLong("id"), files, AttachmentMapper.coverFromMessage(message)).forEach {
     result[it.key] = it
    }
    files.forEach { result[it.key] = it }
   }
   progress(result.values.toList())
  }
  return result.values.toList()
 }
}
