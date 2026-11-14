package it.tgfm.telegram

import android.content.Context
import it.tgfm.core.*
import it.tgfm.data.Credentials
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.drinkless.tdlib.JsonClient
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

fun request(type: String, vararg fields: Pair<String, Any?>): JSONObject = JSONObject().put("@type", type).also { j -> fields.forEach { j.put(it.first, it.second ?: JSONObject.NULL) } }
class TelegramException(val code: Int, detail: String): Exception(when {
 code == 429 -> "Telegram richiede una pausa prima di riprovare ($detail)."
 code == 401 -> "Sessione non valida. Accedi nuovamente a Telegram."
 code == 403 -> "Accesso negato al canale o al file."
 code == 404 -> "Messaggio o file non più disponibile."
 detail.contains("PHONE_CODE_INVALID") -> "Codice Telegram non valido."
 detail.contains("PASSWORD_HASH_INVALID") -> "Password di verifica non valida."
 detail.contains("PHONE_NUMBER_INVALID") -> "Numero telefonico non valido: includi il prefisso internazionale."
 else -> "Telegram: $detail ($code)"
})
class TelegramClient(private val context: Context) {
 private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
 private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
 private val sequence = AtomicLong()
 @Volatile private var clientId = 0
 private val _auth = MutableStateFlow(request("notStarted"))
 val auth = _auth.asStateFlow()
 private val _connection = MutableStateFlow("Non connesso")
 val connection = _connection.asStateFlow()
 private val _files = MutableSharedFlow<JSONObject>(extraBufferCapacity = 64)
 val files = _files.asSharedFlow()
 private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
 val errors = _errors.asSharedFlow()
 private var credentials: Credentials? = null
 private var receiving = false
 @Synchronized fun start(c: Credentials) {
  credentials = c
  if (clientId != 0 && _auth.value.optString("@type") != "authorizationStateClosed") {
   if (_auth.value.optString("@type") == "authorizationStateWaitTdlibParameters") {
    scope.launch { runCatching { parameters() }.onFailure { _errors.emit(it.message ?: "Parametri Telegram non validi") } }
   }
   return
  }
  System.loadLibrary("tdjsonjava") // fail visibly if ABI/library is missing
  JsonClient.execute(request("setLogVerbosityLevel", "new_verbosity_level" to 0).toString())
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
        if (state.optString("@type") == "authorizationStateWaitTdlibParameters") scope.launch { runCatching { parameters() }.onFailure { _errors.emit(it.message ?: "Errore TDLib") } }
        if (state.optString("@type") == "authorizationStateClosed") pending.values.forEach { it.completeExceptionally(TelegramException(401, "Sessione chiusa")) }
       }
       "updateConnectionState" -> _connection.value = when (json.getJSONObject("state").optString("@type")) { "connectionStateReady" -> "Connesso"; "connectionStateUpdating" -> "Aggiornamento"; "connectionStateWaitingForNetwork" -> "Connessione Internet assente"; else -> "Connessione a Telegram…" }
       "updateFile" -> _files.tryEmit(json.getJSONObject("file"))
      }
     } catch (e: Exception) { _errors.tryEmit(e.message ?: "Risposta Telegram non valida") }
    }
   }
  }
  JsonClient.send(clientId, request("getAuthorizationState").toString())
 }
 private suspend fun parameters() {
  val c = requireNotNull(credentials)
  send(request("setTdlibParameters", "database_directory" to File(context.filesDir, "telegram").absolutePath,
   "files_directory" to File(context.filesDir, "downloads/tdlib").apply { mkdirs() }.absolutePath,
   "database_encryption_key" to c.databaseKey, "use_file_database" to true, "use_chat_info_database" to true,
   "use_message_database" to true, "use_secret_chats" to false, "api_id" to c.apiId, "api_hash" to c.apiHash,
   "system_language_code" to "it", "device_model" to android.os.Build.MODEL, "system_version" to android.os.Build.VERSION.RELEASE, "application_version" to "1.2.0"))
  send(request("setOption", "name" to "ignore_file_names", "value" to request("optionValueBoolean", "value" to false)))
 }
 suspend fun send(json: JSONObject, timeoutMs: Long = 90000): JSONObject {
  check(clientId != 0) { "Configura prima l'accesso Telegram." }
  val id = sequence.incrementAndGet().toString(); val deferred = CompletableDeferred<JSONObject>()
  pending[id] = deferred
  try {
   JsonClient.send(clientId, json.put("@extra", id).toString())
   val result = withTimeout(timeoutMs) { deferred.await() }
   if (result.optString("@type") == "error") throw TelegramException(result.optInt("code"), result.optString("message"))
   return result
  } finally { pending.remove(id) }
 }
 suspend fun channels(): List<Channel> {
  val found = linkedMapOf<Long, Channel>()
  for (list in listOf("chatListMain", "chatListArchive")) {
   while (true) {
    currentCoroutineContext().ensureActive()
    try { send(request("loadChats", "chat_list" to request(list), "limit" to 200)) }
    catch (e: TelegramException) { if (e.code == 404) break else throw e }
   }
   val ids = send(request("getChats", "chat_list" to request(list), "limit" to 100000)).getJSONArray("chat_ids")
   for (i in 0 until ids.length()) {
    val chat = send(request("getChat", "chat_id" to ids.getLong(i)))
    if (chat.getJSONObject("type").optBoolean("is_channel")) found[chat.getLong("id")] = Channel(chat.getLong("id"), chat.getString("title"))
   }
  }
  return found.values.sortedBy { it.title.lowercase() }
 }
 suspend fun attachments(chatId: Long, progress: (Int) -> Unit): List<Attachment> {
  val result = linkedMapOf<String, Attachment>(); var cursor = 0L
  send(request("getChat", "chat_id" to chatId))
  while (true) {
   currentCoroutineContext().ensureActive()
   val messages = send(request("getChatHistory", "chat_id" to chatId, "from_message_id" to cursor, "offset" to 0, "limit" to 100, "only_local" to false)).getJSONArray("messages")
   if (messages.length() == 0) break
   for (i in 0 until messages.length()) AttachmentMapper.fromMessage(messages.getJSONObject(i)).forEach { result[it.key] = it }
   progress(result.size)
   val next = messages.getJSONObject(messages.length() - 1).getLong("id")
   if (next == cursor) break
   cursor = next
  }
  return result.values.toList()
 }
 suspend fun download(a: Attachment): File {
  // Revalidate availability because channel messages may have expired since the scan.
  val current = send(request("getMessage", "chat_id" to a.chatId, "message_id" to a.messageId))
  check(AttachmentMapper.fromMessage(current).any { it.fileId == a.fileId }) { "L'allegato è cambiato o non è più disponibile. Leggi nuovamente il canale." }
  send(request("deleteFile", "file_id" to a.fileId))
  try {
   val f = send(request("downloadFile", "file_id" to a.fileId, "priority" to 32, "offset" to 0, "limit" to 0, "synchronous" to true), 30 * 60 * 1000L)
   val local = f.getJSONObject("local")
   check(local.optBoolean("is_downloading_completed")) { "Download interrotto. Riprova." }
   return File(local.getString("path")).also { check(it.isFile) { "File scaricato non disponibile." } }
  } catch (e: Exception) {
   withContext(NonCancellable) {
    val cleanup = runCatching {
     send(request("cancelDownloadFile", "file_id" to a.fileId, "only_if_pending" to false), 10000)
     send(request("deleteFile", "file_id" to a.fileId), 10000)
    }
    cleanup.exceptionOrNull()?.let { _errors.tryEmit("Impossibile rimuovere il parziale TDLib di ${a.name}: ${it.message}") }
   }
   throw e
  }
 }
}
