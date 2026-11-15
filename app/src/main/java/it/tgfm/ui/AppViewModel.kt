package it.tgfm.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.tgfm.TgfmApp
import it.tgfm.TransferService
import it.tgfm.core.*
import it.tgfm.telegram.request
import it.tgfm.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

data class UiState(
 val tab: Int = AppTab.SETTINGS.ordinal, val auth: String = "notStarted", val authInfo: String = "", val connection: String = "Non connesso",
 val rules: List<Rule> = emptyList(), val favorites: List<Channel> = emptyList(), val channels: List<Channel> = emptyList(), val channel: Channel? = null,
 val attachments: List<Attachment> = emptyList(), val selected: Set<String> = emptySet(), val attachmentView: AttachmentView = AttachmentView.ALL, val clearBeforeDownload: Boolean = false,
 val downloads: List<Downloaded> = emptyList(), val downloadedSelection: Set<String> = emptySet(),
 val busy: Boolean = false, val progress: String = "", val fraction: Float? = null,
 val message: String = "", val failures: List<String> = emptyList(), val retry: List<Attachment> = emptyList(),
 val activeDownloads: Map<Int, DownloadProgress> = emptyMap(),
 val ready: Boolean = false, val pendingSave: List<Downloaded> = emptyList()
) {
 val activeRules: List<Rule> get() = if (channel == null) emptyList() else rules.filter { it.channelId == channel.id }
 fun withConfiguration(config: ChannelConfiguration): UiState {
  val changedChannel = channel?.id != config.channel?.id
  val base = copy(favorites = config.favorites, channel = config.channel, rules = config.rules,
   attachments = if (changedChannel) emptyList() else attachments,
   selected = if (changedChannel) emptySet() else selected)
  return if (!changedChannel && activeRules != base.activeRules) base.copy(selected = base.attachments.filter {
   FilterEngine.classify(it, base.activeRules) == Match.CERTAIN
  }.map { it.key }.toSet()) else base
 }
}
class AppViewModel(application: Application): AndroidViewModel(application) {
 private val app = application as TgfmApp
 private val tg = app.telegram
 private val _state = MutableStateFlow(UiState())
 val state = _state.asStateFlow()
 private var initialRoutePending = true
 private val fileLocks = ConcurrentHashMap<Int, Mutex>()
 private var operation: Job? = null
 init {
  viewModelScope.launch { app.preferences.configuration.catch { notify("Impossibile leggere canali e filtri: ${it.message}") }.collect { config ->
   applyConfiguration(config)
   routeForConfiguration()
  } }
  viewModelScope.launch { app.preferences.attachmentView.catch { notify("Impossibile leggere le preferenze: ${it.message}") }.collect { value -> _state.update { it.copy(attachmentView = value) } } }
  viewModelScope.launch { app.preferences.clearBeforeDownload.catch { notify("Impossibile leggere la gestione download: ${it.message}") }.collect { value -> _state.update { it.copy(clearBeforeDownload = value) } } }
  viewModelScope.launch { tg.auth.collect { auth ->
   _state.update { it.copy(auth = auth.optString("@type"), authInfo = authDescription(auth)) }
   routeForConfiguration()
   if (auth.optString("@type") == "authorizationStateClosed") _state.update { it.copy(channels = emptyList(), attachments = emptyList(), selected = emptySet()) }
  } }
  viewModelScope.launch { tg.connection.collect { connection -> _state.update { it.copy(connection = connection) } } }
  viewModelScope.launch { tg.errors.collect { notify(it) } }
  viewModelScope.launch { tg.files.collect { f ->
   _state.update { s ->
    val id = f.optInt("id"); val active = s.activeDownloads[id]
    if (active == null) s else {
     val size = f.optLong("size").takeIf { it > 0 } ?: f.optLong("expected_size")
     val downloaded = f.optJSONObject("local")?.optLong("downloaded_size") ?: 0L
     s.copy(activeDownloads = s.activeDownloads + (id to active.copy(bytes = downloaded, size = size)))
    }
   }
  } }
  launchOperation("Apertura archivio…") {
   val initialConfig = app.preferences.configuration.first()
   val clearPrevious = app.preferences.clearBeforeDownload.first()
   applyConfiguration(initialConfig)
   _state.update { it.copy(clearBeforeDownload = clearPrevious) }
   val warnings = app.storage.recoverMoves()
   refreshDownloads(selectAll = true)
   app.vault.load()?.let { tg.start(it) }
   _state.update { it.copy(ready = true, failures = warnings) }
  }
 }
 private fun authDescription(auth: JSONObject): String = when (auth.optString("@type")) {
  "authorizationStateWaitCode" -> "Inserisci il codice ricevuto da Telegram (${auth.optJSONObject("code_info")?.optJSONObject("type")?.optString("@type")?.removePrefix("authenticationCodeType") ?: "codice"})."
  "authorizationStateWaitPassword" -> "Verifica in due passaggi. Suggerimento: ${auth.optString("password_hint")}"
  "authorizationStateWaitEmailCode" -> "Inserisci il codice inviato all'indirizzo e-mail del tuo account."
  "authorizationStateWaitOtherDeviceConfirmation" -> "Conferma l'accesso da Telegram: ${auth.optString("link")}"
  "authorizationStateWaitRegistration" -> "Serve un account Telegram esistente. Completa la registrazione nell'app ufficiale."
  "authorizationStateWaitPremiumPurchase" -> "Telegram richiede un acquisto Premium per questo accesso. Completa il passaggio nell'app ufficiale."
  else -> ""
 }
 fun notify(message: String) { _state.update { it.copy(message = message) } }
 fun dismissMessage() { _state.update { it.copy(message = "") } }
 private fun applyConfiguration(config: ChannelConfiguration) { _state.update { it.withConfiguration(config) } }
 private fun routeForConfiguration() {
  val initial = initialRoutePending && NavigationPolicy.canSelect(_state.value.auth, _state.value.channel)
  if (initial) initialRoutePending = false
  _state.update { s ->
   if (initial && NavigationPolicy.canSelect(s.auth, s.channel)) s.copy(tab = AppTab.SELECTION.ordinal)
   else s.copy(tab = NavigationPolicy.prerequisitesChanged(s.tab, s.auth, s.channel))
  }
 }
 fun tab(index: Int) {
  initialRoutePending = false
  _state.update { it.copy(tab = NavigationPolicy.requested(index, it.auth, it.channel)) }
 }
 fun clearBeforeDownload(value: Boolean) {
  if (_state.value.busy) return
  viewModelScope.launch {
   try { app.preferences.saveClearBeforeDownload(value); _state.update { it.copy(clearBeforeDownload = value) } }
   catch (e: Exception) { notify("Impossibile salvare la gestione download: ${e.message}") }
  }
 }
 fun attachmentView(value: AttachmentView) {
  _state.update { it.copy(attachmentView = value) }
  viewModelScope.launch { runCatching { app.preferences.saveAttachmentView(value) }.onFailure { notify("Impossibile salvare la preferenza: ${it.message}") } }
 }
 private fun launchOperation(label: String, transfer: Boolean = false, body: suspend () -> Unit) {
  if (_state.value.busy) return
  _state.update { it.copy(busy = true, progress = label, fraction = null, failures = emptyList()) }
  operation = viewModelScope.launch(Dispatchers.IO) {
   try {
    if (transfer) ContextCompat.startForegroundService(app, Intent(app, TransferService::class.java))
    body()
   } catch (e: TimeoutCancellationException) { notify("Tempo di attesa scaduto. Controlla la connessione e riprova.") }
   catch (e: CancellationException) { notify("Operazione annullata. I file già completati restano disponibili.") }
   catch (e: Exception) { notify(e.message ?: "Operazione non riuscita.") }
   catch (e: UnsatisfiedLinkError) { notify("TDLib non disponibile per questo dispositivo: ${e.message}") }
   finally {
    if (transfer) app.stopService(Intent(app, TransferService::class.java))
    _state.update { it.copy(activeDownloads = emptyMap()) }
    _state.update { it.copy(busy = false, fraction = null, progress = if (transfer) it.progress else "") }
   }
  }
 }
 fun cancel() { operation?.cancel() }
 fun connect(id: String, hash: String) = launchOperation("Accesso a Telegram…") { tg.start(app.vault.save(id.trim().toIntOrNull() ?: 0, hash.trim())) }
 fun reconnect() = launchOperation("Riavvio sessione…") { app.vault.load()?.let(tg::start) ?: error("Inserisci API ID e API hash.") }
 fun reconfigure() = launchOperation("Chiusura accesso incompleto…") {
  tg.send(request("close"))
  withTimeout(15000) { tg.auth.first { it.optString("@type") == "authorizationStateClosed" } }
 }
 fun authenticate(value: String) = launchOperation("Verifica credenziali…") {
  val q = when (_state.value.auth) {
   "authorizationStateWaitPhoneNumber" -> request("setAuthenticationPhoneNumber", "phone_number" to value.trim(), "settings" to request("phoneNumberAuthenticationSettings", "allow_flash_call" to false, "allow_missed_call" to false))
   "authorizationStateWaitCode" -> request("checkAuthenticationCode", "code" to value.trim())
   "authorizationStateWaitPassword" -> request("checkAuthenticationPassword", "password" to value)
   "authorizationStateWaitEmailAddress" -> request("setAuthenticationEmailAddress", "email_address" to value.trim())
   "authorizationStateWaitEmailCode" -> request("checkAuthenticationEmailCode", "code" to request("emailAddressAuthenticationCode", "code" to value.trim()))
   else -> error("Attendi il prossimo passaggio di autenticazione.")
  }
  tg.send(q)
 }
 fun resendCode() = launchOperation("Richiesta nuovo codice…") { tg.send(request("resendAuthenticationCode", "reason" to null)) }
 fun logout() = launchOperation("Disconnessione…") {
  tg.send(request("logOut")); app.preferences.saveChannel(null)
  _state.update { it.copy(attachments = emptyList(), selected = emptySet(), channels = emptyList(), channel = null, retry = emptyList()) }
 }
 fun loadChannels() = launchOperation("Lettura canali…") {
  val channels = tg.channels(); _state.update { it.copy(channels = channels) }; app.preferences.updateFavoriteTitles(channels)
  if (channels.isEmpty()) notify("Nessun canale accessibile. Unisciti al canale dall'app Telegram, poi aggiorna.")
 }
 fun chooseChannel(channel: Channel) = launchOperation("Salvataggio canale…") {
  applyConfiguration(app.preferences.selectFavorite(channel.id)); routeForConfiguration()
 }
 fun addFavorite(channel: Channel) {
  initialRoutePending = false
  launchOperation("Aggiunta preferito…") { applyConfiguration(app.preferences.addFavorite(channel)) }
 }
 fun removeFavorite(channel: Channel) = launchOperation("Rimozione preferito…") { applyConfiguration(app.preferences.removeFavorite(channel.id)); routeForConfiguration() }
 fun onForeground() { tg.onForeground() }
 fun scan() = launchOperation("Lettura metadati…") {
  val channel = requireNotNull(_state.value.channel) { "Seleziona un canale in Configurazione." }
  val files = tg.attachments(channel.id) { count -> _state.update { it.copy(progress = "$count allegati individuati…") } }
  _state.update { s -> s.copy(attachments = files, selected = files.filter { FilterEngine.classify(it, s.activeRules) == Match.CERTAIN }.map { it.key }.toSet()) }
  notify(if (files.isEmpty()) "Nessun allegato disponibile." else "${files.size} allegati letti; ${_state.value.selected.size} corrispondenze certe selezionate.")
 }
 fun toggle(key: String, local: Boolean = false) { if (_state.value.busy) return; _state.update { s ->
  if (local) s.copy(downloadedSelection = s.downloadedSelection.toggle(key)) else s.copy(selected = s.selected.toggle(key))
 } }
 fun selectAll(local: Boolean, value: Boolean) { if (_state.value.busy) return; _state.update { s ->
  if (local) s.copy(downloadedSelection = if (value) s.downloads.map { it.path }.toSet() else emptySet())
  else s.copy(selected = if (value) s.attachments.map { it.key }.toSet() else emptySet())
 } }
 fun saveRule(rule: Rule) = launchOperation("Salvataggio filtro…") {
  FilterEngine.validate(rule)?.let { error(it) }
  check(_state.value.favorites.any { it.id == rule.channelId }) { "Assegna il filtro a un canale preferito." }
  val rules = _state.value.rules.filterNot { it.id == rule.id } + rule
  applyConfiguration(app.preferences.saveRules(rules))
 }
 fun saveLearnedRules(drafts: List<Rule>) = launchOperation("Salvataggio regole comuni…") {
  check(drafts.isNotEmpty()) { "Nessuna regola da salvare." }
  val rules = _state.value.rules.toMutableList()
  for (rule in drafts) {
   FilterEngine.validate(rule)?.let { error(it) }
   check(_state.value.favorites.any { it.id == rule.channelId }) { "Assegna il filtro a un canale preferito." }
   if (rules.none { FilterEngine.sameCriteria(it, rule) }) rules += rule
  }
  val added = rules.size - _state.value.rules.size
  applyConfiguration(app.preferences.saveRules(rules))
  notify("$added regole aggiunte. Le regole già presenti non sono state duplicate.")
 }
 fun deleteRule(rule: Rule) = launchOperation("Eliminazione filtro…") {
  val rules = _state.value.rules.filterNot { it.id == rule.id }; applyConfiguration(app.preferences.saveRules(rules))
 }
 fun download(retryOnly: Boolean = false) {
  val files = if (retryOnly) _state.value.retry else _state.value.attachments.filter { it.key in _state.value.selected }
  if (files.isEmpty()) { notify("Seleziona almeno un allegato."); return }
  launchOperation("Preparazione download…", transfer = true) {
   check(!app.storage.hasPendingMove()) { "Completa il recupero dello spostamento prima di pulire i download." }
   if (!retryOnly) { app.storage.prepareDownloads(_state.value.clearBeforeDownload); refreshDownloads(true) }
   _state.update { it.copy(tab = AppTab.DOWNLOADS.ordinal, retry = files) }
   val completed = ConcurrentHashMap.newKeySet<String>()
   val failures = ConcurrentHashMap<String, String>()
   fun updateSummary() {
    _state.update { s -> s.copy(retry = files.filterNot { it.key in completed },
     failures = files.mapNotNull { failures[it.key] },
     progress = "Completati ${completed.size}/${files.size} · attivi ${s.activeDownloads.size}/${ParallelDownloads.LIMIT}",
     fraction = completed.size.toFloat() / files.size) }
   }
   ParallelDownloads.run(files) { a ->
    // TDLib identifies content by fileId: do not race two messages sharing the same file.
    fileLocks.getOrPut(a.fileId) { Mutex() }.withLock {
     currentCoroutineContext().ensureActive()
     try {
      val target = app.storage.beginDownload(a)
      refreshDownloads(true)
      _state.update { it.copy(activeDownloads = it.activeDownloads + (a.fileId to DownloadProgress(a.name, size = a.size))) }
      updateSummary()
      val source = tg.download(a)
      app.storage.finishDownload(source, target)
      completed += a.key
     } catch (e: CancellationException) { throw e }
     catch (e: Exception) { failures[a.key] = "${a.name}: ${e.message}" }
     finally {
      _state.update { it.copy(activeDownloads = it.activeDownloads - a.fileId) }
      runCatching { refreshDownloads(true) }
      updateSummary()
     }
    }
   }
   refreshDownloads(true)
   _state.update { it.copy(progress = "${completed.size}/${files.size} download completati", fraction = null) }

  }
 }
 @Synchronized private fun refreshDownloads(selectAll: Boolean = false) {
  val files = app.storage.list()
  _state.update { s -> s.copy(downloads = files, downloadedSelection = if (selectAll) files.map { it.path }.toSet() else s.downloadedSelection.intersect(files.map { it.path }.toSet())) }
 }
 fun selectedDownloads() = _state.value.downloads.filter { it.path in _state.value.downloadedSelection }
 fun prepareSave(): Boolean {
  val files = selectedDownloads(); if (files.isEmpty()) { notify("Seleziona almeno un file."); return false }
  _state.update { it.copy(pendingSave = files) }; return true
 }
 fun save(uri: Uri?) {
  val files = _state.value.pendingSave
  _state.update { it.copy(pendingSave = emptyList()) }
  if (uri == null) { notify("Scelta cartella annullata. Nessun file spostato."); return }
  if (files.isEmpty()) { notify("Selezione scaduta: premi nuovamente Salva."); return }
  launchOperation("Spostamento di ${files.size} file…", transfer = true) {
   val recovery = app.storage.recoverMoves()
   check(recovery.isEmpty()) { recovery.joinToString("\n") }
   val context = currentCoroutineContext()
   try {
    val errors = app.storage.move(files, uri) { i, name, bytes, size ->
     context.ensureActive()
     _state.update { it.copy(progress = "Spostati $i/${files.size} · $name", fraction = if (size > 0) (bytes.toFloat() / size).coerceIn(0f, 1f) else null) }
    }
    val moved = files.count { !java.io.File(it.path).exists() }
    _state.update { it.copy(failures = errors, progress = "$moved/${files.size} file spostati") }
    notify("Spostati $moved/${files.size} file. Gli altri restano nella cartella privata.")
   } finally { refreshDownloads() }
  }
 }
 fun recover() = launchOperation("Recupero trasferimenti…") { val errors = app.storage.recoverMoves(); refreshDownloads(); _state.update { it.copy(failures = errors) }; if (errors.isEmpty()) notify("Recupero completato.") }
 private fun Set<String>.toggle(key: String) = if (key in this) this - key else this + key
}
