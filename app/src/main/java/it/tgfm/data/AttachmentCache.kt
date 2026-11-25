package it.tgfm.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import it.tgfm.core.*
import it.tgfm.telegram.AttachmentMapper
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.TreeMap

/** A message with no files still separates a photo from the following document. */
data class CachedPost(val point: HistoryPoint, val files: List<Attachment>, val photo: CoverImage?) {
 companion object {
  fun from(message: JSONObject): CachedPost {
   val point = HistoryPoint(message.getLong("date"), message.getLong("id"))
   return CachedPost(point, AttachmentMapper.fromMessage(message).mapIndexed { slot, a ->
    a.copy(fileId = 0, cachedSlot = slot, imagePreview = a.imagePreview?.copy(fileId = 0))
   }, AttachmentMapper.coverFromMessage(message)?.copy(fileId = 0))
  }
  private fun preview(obj: JSONObject?, messageId: Long) = obj?.let {
   CoverImage(miniature = it.optString("mini"), caption = it.optString("caption"),
    messageId = messageId, remoteId = it.optString("remote"))
  }
  fun decode(chat: Long, point: HistoryPoint, raw: String): CachedPost {
   if (raw.isEmpty()) return CachedPost(point, emptyList(), null)
   val obj = JSONObject(raw)
   val array = obj.getJSONArray("files")
   val files = (0 until array.length()).map { slot ->
    val a = array.getJSONObject(slot)
    Attachment(chat, point.id, 0, a.getString("name"), a.getString("mime"), a.getLong("size"),
     point.date, a.getString("caption"), Category.valueOf(a.getString("category")),
     imagePreview = preview(a.optJSONObject("preview"), point.id), remoteUnique = a.getString("unique"), cachedSlot = slot)
   }
   return CachedPost(point, files, preview(obj.optJSONObject("photo"), point.id))
  }
 }
 fun encode(): String {
  if (files.isEmpty() && photo == null) return ""
  fun CoverImage.json() = JSONObject().put("remote", remoteId).put("mini", miniature).put("caption", caption)
  return JSONObject().put("photo", photo?.json()).put("files", JSONArray(files.map { a ->
   JSONObject().put("name", a.name).put("mime", a.mime).put("size", a.size).put("caption", a.caption)
    .put("category", a.category.name).put("unique", a.remoteUnique).put("preview", a.imagePreview?.json())
  })).toString()
 }
}

class AttachmentCache(private val context: Context, account: Long, private val chat: Long) :
 SQLiteOpenHelper(context, "attachments-$account-$chat.db", null, 1) {
 init { require(account > 0 && chat != 0L) }
 private val periods = context.getSharedPreferences("attachment-periods", Context.MODE_PRIVATE)
 private val periodKey = "$account:$chat"
 fun remember(period: PostPeriod) {
  periods.edit().putString(periodKey, "${period.mode}|${period.first}|${period.last}").apply()
 }
 fun lastPeriod(): PostPeriod? = periods.getString(periodKey, null)?.split('|')?.let {
  PostPeriod(PostPeriodMode.valueOf(it[0]), LocalDate.parse(it[1]), LocalDate.parse(it[2]))
 }
 fun hasHistory() = context.getDatabasePath(databaseName).exists()
 fun hasData() = hasHistory() || periods.contains(periodKey)
 fun clear() {
  close()
  if (context.getDatabasePath(databaseName).exists()) check(context.deleteDatabase(databaseName)) { it.tgfm.Words.get(it.tgfm.R.string.cache_clear_failed) }
  periods.edit().remove(periodKey).apply()
 }
 override fun onCreate(db: SQLiteDatabase) {
  db.execSQL("CREATE TABLE posts(date INTEGER NOT NULL,id INTEGER PRIMARY KEY,metadata TEXT NOT NULL)")
  db.execSQL("CREATE INDEX post_dates ON posts(date,id)")
  db.execSQL("CREATE TABLE ranges(kind TEXT NOT NULL,lo INTEGER,lo_id INTEGER,hi INTEGER,hi_id INTEGER)")
 }
 override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = error("Unexpected cache version $old/$new")

 private fun ranges(kind: String): List<HistoryRange> = readableDatabase.rawQuery(
  "SELECT lo,lo_id,hi,hi_id FROM ranges WHERE kind=? ORDER BY lo,lo_id", arrayOf(kind)
 ).use { c -> buildList {
  while (c.moveToNext()) add(HistoryRange(HistoryPoint(c.getLong(0), c.getLong(1)), HistoryPoint(c.getLong(2), c.getLong(3))))
 } }
 private fun storeRanges(kind: String, spans: List<HistoryRange>) {
  val db = writableDatabase
  db.delete("ranges", "kind=?", arrayOf(kind))
  for (s in spans) db.execSQL("INSERT INTO ranges VALUES(?,?,?,?,?)", arrayOf<Any>(kind,s.start.date,s.start.id,s.end.date,s.end.id))
 }
 private fun transaction(body: () -> Unit) {
  val db = writableDatabase
  db.beginTransaction()
  try { body(); db.setTransactionSuccessful() } finally { db.endTransaction() }
 }
 fun covered() = ranges("read")
 fun missing(span: HistoryRange): List<HistoryRange> {
  val due = ranges("refresh")
  return span.missing(covered().flatMap { it.missing(due) })
 }
 fun requestRefresh(span: HistoryRange, openEnded: Boolean) = transaction {
  val previous = ranges("request").singleOrNull()
  val pending = ranges("refresh")
  val resume = previous != null && previous.start == span.start &&
   (previous.end == span.end || (openEnded && previous.end < span.end)) &&
   pending.any { it.start < previous.end && previous.start < it.end }
  val work = if (resume) span.minus(requireNotNull(previous)) else listOf(span)
  storeRanges("refresh", mergeHistory(pending + work))
  storeRanges("request", listOf(span))
 }
 fun finishRefresh() = transaction { storeRanges("request", emptyList()) }

 private val within = "(date,id) >= (?,?) AND (date,id) < (?,?)"
 private fun HistoryRange.args() = arrayOf(start.date,start.id,end.date,end.id).map(Long::toString).toTypedArray()
 fun load(span: HistoryRange): TreeMap<HistoryPoint,CachedPost> {
  val posts = TreeMap<HistoryPoint,CachedPost>()
  readableDatabase.rawQuery("SELECT date,id,metadata FROM posts WHERE $within ORDER BY date,id", span.args()).use { c ->
   while (c.moveToNext()) {
    val key = HistoryPoint(c.getLong(0),c.getLong(1))
    posts[key] = CachedPost.decode(chat,key,c.getString(2))
   }
  }
  return posts
 }
 /** Replace only the part actually traversed, in the same transaction as its checkpoint. */
 fun accept(span: HistoryRange, page: List<CachedPost>) = transaction {
  val db = writableDatabase
  db.delete("posts", within, span.args())
  db.compileStatement("INSERT OR REPLACE INTO posts(date,id,metadata) VALUES(?,?,?)").use { insert ->
   for (post in page) {
    require(post.point in span)
    insert.bindLong(1,post.point.date); insert.bindLong(2,post.point.id); insert.bindString(3,post.encode())
    insert.executeInsert()
   }
  }
  storeRanges("read", mergeHistory(covered() + span))
  storeRanges("refresh", ranges("refresh").flatMap { it.minus(span) })
 }
}

fun cachedAttachments(posts: TreeMap<HistoryPoint,CachedPost>, spans: List<HistoryRange>, chat: Long): List<Attachment> {
 val files = linkedMapOf<String,Attachment>()
 for (span in spans.asReversed()) {
  val covers = PreviousMessageCovers()
  for (post in posts.subMap(span.start,true,span.end,false).descendingMap().values) {
   covers.accept(chat,post.point.id,post.files,post.photo).forEach { files[it.key] = it }
   post.files.forEach { files[it.key] = it }
  }
 }
 return files.values.toList()
}
