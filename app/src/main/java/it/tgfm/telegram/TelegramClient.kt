package it.tgfm.telegram

import it.tgfm.R
import it.tgfm.Words

import android.content.Context
import it.tgfm.core.*
import it.tgfm.data.Credentials
import it.tgfm.data.WorkFolder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.drinkless.tdlib.JsonClient
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

fun request(type: String, vararg fields: Pair<String, Any?>): JSONObject = JSONObject().put("@type", type).also { j -> fields.forEach { j.put(it.first, it.second ?: JSONObject.NULL) } }
class TelegramException(val code: Int, detail: String): Exception(when {
 code == 429 -> Words.get(R.string.telegram_requires_a_pause_before_retrying, detail)
 code == 401 -> Words.get(R.string.invalid_session_sign_in_to_telegram_again)
 code == 403 -> Words.get(R.string.access_to_the_channel_or_file_was)
 code == 404 -> Words.get(R.string.message_or_file_no_longer_available)
 detail.contains("PHONE_CODE_INVALID") -> Words.get(R.string.invalid_telegram_code)
 detail.contains("PASSWORD_HASH_INVALID") -> Words.get(R.string.invalid_verification_password)
 detail.contains("PHONE_NUMBER_INVALID") -> Words.get(R.string.invalid_phone_number_include_the_international_dialing)
 else -> "Telegram: $detail ($code)"
})
class TelegramClient(private val context: Context) {
 private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
 private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
 private val sequence = AtomicLong()
 @Volatile private var clientId = 0
 private val _auth = MutableStateFlow(request("notStarted"))
 val auth = _auth.asStateFlow()
 @Volatile private var sessionAccount = 0L
 private val _connection = MutableStateFlow(R.string.offline)
 val connection = _connection.asStateFlow()
 private val _files = MutableSharedFlow<JSONObject>(extraBufferCapacity = 64)
 val files = _files.asSharedFlow()
 private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
 val errors = _errors.asSharedFlow()
 private val parametersLock = Mutex()
 private var configuredClientId = 0
 private var credentials: Credentials? = null
 private var receiving = false
 @Synchronized fun start(c: Credentials) {
  credentials = c
  if (clientId != 0 && _auth.value.optString("@type") != "authorizationStateClosed") {
   if (_auth.value.optString("@type") == "authorizationStateWaitTdlibParameters") {
    scope.launch { runCatching { parameters() }.onFailure { _errors.emit(it.message ?: Words.get(R.string.invalid_telegram_parameters)) } }
   }
   return
  }
  System.loadLibrary("tdjsonjava") // fail visibly if ABI/library is missing
  JsonClient.execute(request("setLogVerbosityLevel", "new_verbosity_level" to 0).toString())
  sessionAccount = 0L
  _auth.value = request("starting")
  clientId = JsonClient.createClientId()
  if (!receiving) {
   receiving = true
   scope.launch {
    while (isActive) {
     val raw = JsonClient.receive(1.0) ?: continue
     try {
      val json = JSONObject(raw)
      if (json.optInt("@client_id", clientId) != clientId) continue
      val extra = json.optString("@extra")
      if (extra.isNotBlank()) pending.remove(extra)?.complete(json)
      else when (json.optString("@type")) {
       "updateAuthorizationState" -> {
        val state = json.getJSONObject("authorization_state"); _auth.value = state
        if (state.optString("@type") == "authorizationStateWaitTdlibParameters") scope.launch { runCatching { parameters() }.onFailure { _errors.emit(it.message ?: Words.get(R.string.tdlib_error)) } }
        if (state.optString("@type") == "authorizationStateClosed") pending.values.forEach { it.completeExceptionally(TelegramException(401, Words.get(R.string.session_closed))) }
       }
       "updateOption" -> if (json.optString("name") == "my_id") {
        json.getJSONObject("value").optLong("value").takeIf { it > 0 }?.let { sessionAccount = it }
       }
       "updateConnectionState" -> _connection.value = when (json.getJSONObject("state").optString("@type")) { "connectionStateReady" -> R.string.connected; "connectionStateUpdating" -> R.string.updating; "connectionStateWaitingForNetwork" -> R.string.no_internet_connection; else -> R.string.connecting_to_telegram }
       "updateFile" -> _files.tryEmit(json.getJSONObject("file"))
      }
     } catch (e: Exception) { _errors.tryEmit(e.message ?: Words.get(R.string.invalid_telegram_response)) }
    }
   }
  }
  JsonClient.send(clientId, request("getAuthorizationState").toString())
 }
 fun onForeground() {
  if (clientId == 0) return
  scope.launch {
   runCatching {
    _auth.value = send(request("getAuthorizationState"), 10000)
    if (_auth.value.optString("@type") == "authorizationStateReady") send(request("setOption", "name" to "online", "value" to request("optionValueBoolean", "value" to true)), 10000)
   }
  }
 }
 private suspend fun parameters() = parametersLock.withLock {
  val configuringClientId = clientId
  if (configuredClientId == configuringClientId) return@withLock
  val c = requireNotNull(credentials)
  val folder = WorkFolder(context).apply { requireReady() }
  TelegramInitialization { send(it, 30_000) }.initialize(request("setTdlibParameters", "database_directory" to File(context.filesDir, "telegram").absolutePath,
   "files_directory" to File(folder.cache, "tdlib").apply { mkdirs() }.absolutePath,
   "database_encryption_key" to c.databaseKey, "use_file_database" to true, "use_chat_info_database" to true,
   "use_message_database" to true, "use_secret_chats" to false, "api_id" to c.apiId, "api_hash" to c.apiHash,
   "system_language_code" to Words.locale.language, "device_model" to android.os.Build.MODEL, "system_version" to android.os.Build.VERSION.RELEASE, "application_version" to "1.5.9"))
  configuredClientId = configuringClientId
  Unit
 }
 suspend fun send(json: JSONObject, timeoutMs: Long = 90000): JSONObject {
  check(clientId != 0) { Words.get(R.string.set_up_telegram_access_first) }
  val id = sequence.incrementAndGet().toString(); val deferred = CompletableDeferred<JSONObject>()
  pending[id] = deferred
  try {
   JsonClient.send(clientId, json.put("@extra", id).toString())
   val result = withTimeout(timeoutMs) { deferred.await() }
   if (result.optString("@type") == "error") throw TelegramException(result.optInt("code"), result.optString("message"))
   return result
  } finally { pending.remove(id) }
 }
 private val reader = TelegramReader { send(it) }
 suspend fun accountId(): Long {
  sessionAccount.takeIf { it > 0 }?.let { return it }
  val session = clientId
  val option = send(request("getOption", "name" to "my_id")).optLong("value")
  val account = if (option > 0) option else send(request("getMe")).getLong("id")
  synchronized(this) {
   check(session == clientId && account > 0) { Words.get(R.string.invalid_session_sign_in_to_telegram_again) }
   sessionAccount = account
  }
  return account
 }
 suspend fun readHistory(chat: Long, gaps: List<HistoryRange>, head: HistoryPoint?, page: (HistoryRange, List<JSONObject>) -> Unit) {
  if (gaps.isEmpty()) return
  send(request("getChat", "chat_id" to chat))
  send(request("openChat", "chat_id" to chat))
  try {
   for (gap in gaps.asReversed()) reader.readHistory(chat, gap, gap.end == head, page)
  } finally {
   withContext(NonCancellable) { runCatching { send(request("closeChat", "chat_id" to chat), 5000) } }
  }
 }

 suspend fun resolveCached(a: Attachment): Attachment {
  val slot = a.cachedSlot ?: return a
  val message = send(request("getMessage", "chat_id" to a.chatId, "message_id" to a.messageId))
  val files = AttachmentMapper.fromMessage(message)
  val current = if (a.remoteUnique.isNotEmpty()) files.firstOrNull { it.remoteUnique == a.remoteUnique }
   else files.getOrNull(slot)?.takeIf { it.category == a.category && it.size == a.size && it.name == a.name }
  check(current != null) { Words.get(R.string.the_attachment_changed_or_is_no_longer) }
  return a.copy(fileId = current.fileId)
 }
 suspend fun channels(progress: (List<Channel>) -> Unit = {}) = reader.channels(progress)
 suspend fun attachments(chatId: Long, window: PostWindow, progress: (List<Attachment>) -> Unit) = reader.attachments(chatId, window, progress)
 suspend fun closeForStorageChange() {
  if (clientId == 0 || auth.value.optString("@type") == "authorizationStateClosed") return
  send(request("close"))
  withTimeout(20_000) { auth.first { it.optString("@type") == "authorizationStateClosed" } }
 }
 private suspend fun validateAttachment(a: Attachment) {
  val current = send(request("getMessage", "chat_id" to a.chatId, "message_id" to a.messageId))
  check(AttachmentMapper.fromMessage(current).any { it.fileId == a.fileId }) { Words.get(R.string.the_attachment_changed_or_is_no_longer) }
 }
 suspend fun startStreaming(a: Attachment) {
  validateAttachment(a)
  send(request("deleteFile", "file_id" to a.fileId))
  send(request("downloadFile", "file_id" to a.fileId, "priority" to 32, "offset" to 0, "limit" to 0, "synchronous" to false))
 }
 suspend fun available(a: Attachment, offset: Long): AvailableBytes {
  val f = send(request("getFile", "file_id" to a.fileId), 15_000)
  val local = f.getJSONObject("local")
  val complete = local.optBoolean("is_downloading_completed")
  val file = local.optString("path").takeIf { it.isNotBlank() }?.let(::File)
  if (file != null) check(file.canonicalPath.startsWith(File(WorkFolder(context).cache, "tdlib").canonicalPath + File.separator)) { Words.get(R.string.unexpected_tdlib_path) }
  val size = f.optLong("size")
  val readable = if (complete) (size - offset).coerceAtLeast(0) else
   send(request("getFileDownloadedPrefixSize", "file_id" to a.fileId, "offset" to offset), 15_000).getLong("size")
  return AvailableBytes(file, readable, complete, size)
 }
 suspend fun stopStreaming(a: Attachment, delete: Boolean) {
  send(request("cancelDownloadFile", "file_id" to a.fileId, "only_if_pending" to false), 10_000)
  if (delete) send(request("deleteFile", "file_id" to a.fileId), 10_000)
 }
 /** Low priority previews remain in TDLib's existing, user-clearable cache. */
 suspend fun cover(fileId: Int): File {
  val f = send(request("downloadFile", "file_id" to fileId, "priority" to 1, "offset" to 0, "limit" to 0, "synchronous" to true), 20_000)
  val local = f.getJSONObject("local")
  check(local.optBoolean("is_downloading_completed"))
  return File(local.getString("path")).also {
   check(it.isFile && it.length() <= 256 * 1024 && it.canonicalPath.startsWith(File(WorkFolder(context).cache, "tdlib").canonicalPath + File.separator))
  }
 }
 suspend fun download(a: Attachment): File {
  // Revalidate availability because channel messages may have expired since the scan.
  validateAttachment(a)
  send(request("deleteFile", "file_id" to a.fileId))
  try {
   val f = coroutineScope {
    val transfer = async { send(request("downloadFile", "file_id" to a.fileId, "priority" to 32, "offset" to 0, "limit" to 0, "synchronous" to true), Long.MAX_VALUE) }
    var lastBytes = 0L
    var result: JSONObject?
    do {
     result = withTimeoutOrNull(180_000) { transfer.await() }
     if (result == null && !transfer.isCompleted) {
      val file = send(request("getFile", "file_id" to a.fileId), 15_000)
      val bytes = file.getJSONObject("local").getLong("downloaded_size")
      check(bytes > lastBytes || transfer.isCompleted) { Words.get(R.string.telegram_has_stopped_providing_new_data_retry) }
      lastBytes = bytes
     }
    } while (result == null)
    result
   }
   val local = f.getJSONObject("local")
   check(local.optBoolean("is_downloading_completed")) { Words.get(R.string.download_interrupted_try_again) }
   return File(local.getString("path")).also { check(it.isFile) { Words.get(R.string.downloaded_file_unavailable) } }
  } catch (e: Exception) {
   withContext(NonCancellable) {
    val cleanup = runCatching {
     send(request("cancelDownloadFile", "file_id" to a.fileId, "only_if_pending" to false), 10000)
     send(request("deleteFile", "file_id" to a.fileId), 10000)
    }
    cleanup.exceptionOrNull()?.let { _errors.tryEmit(Words.get(R.string.could_not_remove_the_partial_tdlib_file, a.name, it.message)) }
   }
   throw e
  }
 }
}
