package it.tgfm.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import java.io.File
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
 val postPeriod: PostPeriod = PostPeriod(), val scannedPeriod: String = "", val scanComplete: Boolean = false,
 val downloads: List<Downloaded> = emptyList(), val downloadedSelection: Set<String> = emptySet(),
 val busy: Boolean = false, val progress: String = "", val fraction: Float? = null,
 val message: String = "", val failures: List<String> = emptyList(), val retry: List<Attachment> = emptyList(),
 val activeDownloads: Map<Int, DownloadProgress> = emptyMap(),
 val workPath: String = "", val fileAccess: Boolean = false, val ftp: FtpSettings = FtpSettings(),
 val archiveContents: Boolean = false, val archiveDefault: Boolean = false,
 val archiveRename: String? = null, val remoteCleanup: List<String> = emptyList(),
 val loginError: String = "",
 val shareFiles: List<Downloaded> = emptyList(), val ready: Boolean = false
) {
 val activeRules: List<Rule> get() = if (channel == null) emptyList() else rules.filter { it.channelId == channel.id }
 fun withConfiguration(config: ChannelConfiguration): UiState {
  val changedChannel = channel?.id != config.channel?.id
  val base = copy(favorites = config.favorites, channel = config.channel, rules = config.rules,
   attachments = if (changedChannel) emptyList() else attachments,
   selected = if (changedChannel) emptySet() else selected,
   scannedPeriod = if (changedChannel) "" else scannedPeriod, scanComplete = if (changedChannel) false else scanComplete)
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
 @Volatile private var foregroundRecheckPending = false
 private var renameReply: CompletableDeferred<String?>? = null
 fun answerArchiveName(name: String?) { renameReply?.complete(name) }
 fun archiveMode(contents: Boolean) { _state.update { it.copy(archiveContents = contents) } }
 fun archiveDefault(contents: Boolean) { viewModelScope.launch { app.preferences.saveArchiveContents(contents) } }
 init {
  viewModelScope.launch { app.preferences.archiveContents.collect { value -> _state.update { it.copy(archiveContents = value, archiveDefault = value) } } }
  viewModelScope.launch { app.preferences.configuration.catch { notify("Impossibile leggere canali e filtri: ${it.message}") }.collect { config ->
   applyConfiguration(config)
   routeForConfiguration()
  } }
  viewModelScope.launch { app.preferences.attachmentView.catch { notify("Impossibile leggere le preferenze: ${it.message}") }.collect { value -> _state.update { it.copy(attachmentView = value) } } }
  viewModelScope.launch { app.preferences.clearBeforeDownload.catch { notify("Impossibile leggere la gestione download: ${it.message}") }.collect { value -> _state.update { it.copy(clearBeforeDownload = value) } } }
  viewModelScope.launch { tg.auth.collect { auth ->
   _state.update { it.copy(auth = auth.optString("@type"), authInfo = authDescription(auth)) }
   routeForConfiguration()
   if (auth.optString("@type") == "authorizationStateClosed") _state.update { it.copy(channels = emptyList(), attachments = emptyList(), selected = emptySet(), scannedPeriod = "", scanComplete = false) }
  } }
  viewModelScope.launch { tg.connection.collect { connection -> _state.update { it.copy(connection = connection) } } }
  viewModelScope.launch { tg.errors.collect { error -> notify(error); if (_state.value.auth != "authorizationStateReady") _state.update { it.copy(loginError = error) } } }
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
   try {
    val credentials = savedCredentials()
    val initialConfig = app.preferences.configuration.first()
    val clearPrevious = app.preferences.clearBeforeDownload.first()
    applyConfiguration(initialConfig)
    _state.update { it.copy(clearBeforeDownload = clearPrevious) }
    updateStorageState()
    try { _state.update { it.copy(ftp = app.vault.loadFtp()) } }
    catch (_: Exception) { notify("Configurazione FTP non leggibile. Puoi configurare nuovamente il server FTP.") }
    refreshDownloads(selectAll = true)
    if (credentials != null && app.storage.folder.hasAccess()) {
     app.storage.folder.requireReady()
     tg.start(credentials)
    }
   } finally { _state.update { it.copy(ready = true) } }
  }
 }
 private fun savedCredentials(): Credentials? = try { app.vault.load() } catch (_: Exception) {
  notify("Configura API ID e API hash per iniziare un nuovo accesso Telegram.")
  null
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
 fun postPeriod(value: PostPeriod) { if (!_state.value.busy) _state.update { it.copy(postPeriod = value) } }
 private fun launchOperation(label: String, transfer: Boolean = false, body: suspend () -> Unit) {
  if (_state.value.busy) return
  _state.update { it.copy(busy = true, progress = label, fraction = null, failures = emptyList()) }
  operation = viewModelScope.launch(Dispatchers.IO) {
   try {
    if (transfer) ContextCompat.startForegroundService(app, Intent(app, TransferService::class.java))
    body()
   } catch (e: TimeoutCancellationException) { notify("Tempo di attesa scaduto. Controlla la connessione e riprova.") }
   catch (e: CancellationException) { notify("Operazione annullata. Gli allegati già letti e i download completati restano disponibili.") }
   catch (e: Exception) { notify(e.message ?: "Operazione non riuscita.") }
   catch (e: UnsatisfiedLinkError) { notify("TDLib non disponibile per questo dispositivo: ${e.message}") }
   finally {
    if (transfer) app.stopService(Intent(app, TransferService::class.java))
    _state.update { it.copy(activeDownloads = emptyMap()) }
    _state.update { it.copy(busy = false, fraction = null, progress = if (transfer) it.progress else "") }
    if (foregroundRecheckPending && _state.value.ready) {
     foregroundRecheckPending = false
     onForeground()
    }
   }
  }
 }
 fun cancel() { operation?.cancel() }
 fun connect(id: String, hash: String) = launchOperation("Avvio accesso Telegram…") {
  _state.update { it.copy(loginError = "") }
  try {
   val apiId = id.trim().toIntOrNull() ?: 0
   val apiHash = hash.trim()
   require(apiId > 0 && Regex("[a-fA-F0-9]{32}").matches(apiHash)) { "API ID o API hash non validi." }
   app.storage.folder.requireReady()
   tg.closeForStorageChange()
   // Configura accesso starts a new login, without recovering a previous database/key.
   val database = File(app.filesDir, "telegram")
   check(!database.exists() || database.deleteRecursively()) { "Impossibile iniziare una nuova sessione Telegram." }
   val credentials = app.vault.saveNew(apiId, apiHash)
   tg.start(credentials)
  } catch (e: Exception) {
   _state.update { it.copy(loginError = e.message ?: "Impossibile avviare Telegram.") }
   throw e
  }
 }
 fun reconfigure() = launchOperation("Chiusura accesso…") {
  tg.closeForStorageChange()
  _state.update { it.copy(loginError = "") }
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
  _state.update { it.copy(attachments = emptyList(), selected = emptySet(), channels = emptyList(), channel = null, retry = emptyList(), scannedPeriod = "", scanComplete = false) }
 }
 fun loadChannels() = launchOperation("Lettura canali…") {
  val channels = tg.channels { channels -> _state.update { it.copy(channels = channels, progress = "${channels.size} canali e gruppi individuati…") } }
  _state.update { it.copy(channels = channels) }; app.preferences.updateFavoriteTitles(channels)
  if (channels.isEmpty()) notify("Nessun canale o gruppo accessibile. Unisciti da Telegram, poi aggiorna.")
 }
 fun chooseChannel(channel: Channel) = launchOperation("Salvataggio canale…") {
  applyConfiguration(app.preferences.selectFavorite(channel.id)); routeForConfiguration()
 }
 fun addFavorite(channel: Channel) {
  initialRoutePending = false
  launchOperation("Aggiunta preferito…") { applyConfiguration(app.preferences.addFavorite(channel)) }
 }
 fun removeFavorite(channel: Channel) = launchOperation("Rimozione preferito…") { applyConfiguration(app.preferences.removeFavorite(channel.id)); routeForConfiguration() }
 fun onForeground() {
  tg.onForeground()
  updateStorageState()
  if (!_state.value.ready || _state.value.busy) { foregroundRecheckPending = true; return }
  if (_state.value.fileAccess && _state.value.auth == "notStarted") {
   launchOperation("Avvio Telegram…") {
    val credentials = savedCredentials() ?: return@launchOperation
    app.storage.folder.requireReady()
    refreshDownloads()
    tg.start(credentials)
   }
  }
 }
 private fun updateStorageState() { _state.update { it.copy(workPath = app.storage.folder.root.absolutePath, fileAccess = app.storage.folder.hasAccess()) } }
 fun chooseWorkFolder(uri: Uri) = launchOperation("Configurazione cartella…") {
  val folder = app.storage.folder.fromTree(uri)
  tg.closeForStorageChange()
  app.storage.folder.save(folder)
  app.storage.folder.requireReady()
  updateStorageState(); refreshDownloads(true)
  savedCredentials()?.let(tg::start)
 }
 fun clearCache() = launchOperation("Pulizia cache…") {
  app.storage.folder.requireReady()
  tg.closeForStorageChange()
  app.storage.clearCache()
  savedCredentials()?.let(tg::start)
  notify("Cache eliminata. I file salvati nella cartella di lavoro restano disponibili.")
 }
 fun saveFtp(settings: FtpSettings) = launchOperation("Salvataggio FTP…") {
  app.vault.saveFtp(settings); _state.update { it.copy(ftp = settings) }; notify("Configurazione FTP salvata.")
 }
 fun testFtp() = launchOperation("Verifica connessione FTP…") {
  FtpUploader(_state.value.ftp).test(); notify("Accesso FTP e cartella verificati. La scrittura sarà verificata durante l'invio.")
 }
 fun shared() { _state.update { it.copy(shareFiles = emptyList()) } }
 fun deleteDownloads(files: List<Downloaded> = selectedDownloads()) = launchOperation("Eliminazione file…") {
  try { app.storage.delete(files) } finally { refreshDownloads() }
 }

 fun scan() = launchOperation("Lettura metadati…") {
  val channel = requireNotNull(_state.value.channel) { "Seleziona un canale in Configurazione." }
  val period = _state.value.postPeriod
  val today = java.time.LocalDate.now()
  val window = period.window(today)
  _state.update { it.copy(attachments = emptyList(), selected = emptySet(), scannedPeriod = period.description(today), scanComplete = false) }
  val files = tg.attachments(channel.id, window) { files -> _state.update { s -> s.copy(
   attachments = ArchiveNames.group(files),
   selected = ArchiveNames.group(files).filter { FilterEngine.classify(it, s.activeRules) == Match.CERTAIN }.map { it.key }.toSet(),
   progress = "${files.size} allegati individuati…") } }
  _state.update { it.copy(scanComplete = true) }
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
 fun download(retryOnly: Boolean = false, share: Boolean = false) {
  val files = if (retryOnly) _state.value.retry else _state.value.attachments.filter { it.key in _state.value.selected }
  if (files.isEmpty()) { notify("Seleziona almeno un allegato."); return }
  if (files.any { ArchiveNames.isArchive(it.name) }) { archiveTransfer(files, emptyList(), false, share); return }
  launchOperation("Preparazione download…", transfer = true) {
   if (!retryOnly) { app.storage.prepareDownloads(_state.value.clearBeforeDownload); refreshDownloads(true) }
   _state.update { it.copy(tab = AppTab.DOWNLOADS.ordinal, retry = files) }
   val saved = ConcurrentHashMap<String, Downloaded>()
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
      val existing = app.storage.existing(a)
      if (existing != null) { saved[a.key] = existing; completed += a.key; return@withLock }
      val target = app.storage.beginDownload(a)
      refreshDownloads(true)
      _state.update { it.copy(activeDownloads = it.activeDownloads + (a.fileId to DownloadProgress(a.name, size = a.size))) }
      updateSummary()
      val source = tg.download(a)
      saved[a.key] = app.storage.finishDownload(source, target)
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
   _state.update { it.copy(progress = "${completed.size}/${files.size} download completati", fraction = null,
    shareFiles = if (share && completed.size == files.size) files.map { a -> saved.getValue(a.key) } else emptyList()) }
   if (share && completed.size != files.size) notify("Condivisione non avviata: alcuni file non sono stati salvati. Riprova oppure condividi i file disponibili da Download.")

  }
 }
 @Synchronized private fun refreshDownloads(selectAll: Boolean = false) {
  val files = app.storage.list()
  _state.update { s -> s.copy(downloads = files, downloadedSelection = if (selectAll) files.map { it.path }.toSet() else s.downloadedSelection.intersect(files.map { it.path }.toSet())) }
 }
 fun refreshLocalFiles() = launchOperation("Lettura cartella…") { refreshDownloads() }
 fun selectedDownloads() = _state.value.downloads.filter { it.path in _state.value.downloadedSelection }
 fun uploadFtp(local: Boolean = false, keep: Boolean = _state.value.ftp.keep, subdirectory: String = "") {
  val selected = _state.value.attachments.filter { it.key in _state.value.selected }
  val downloaded = selectedDownloads()
  if (if (local) downloaded.isEmpty() else selected.isEmpty()) { notify("Seleziona almeno un file."); return }
  val settings = _state.value.ftp.copy(keep = keep)
  if (subdirectory.isNotBlank() || (if(local) downloaded.any { ArchiveNames.isArchive(it.originalName) } else selected.any { ArchiveNames.isArchive(it.name) })) { archiveTransfer(selected, downloaded, local, share = false, settings = settings, subdirectory = subdirectory); return }
  launchOperation("Preparazione FTP…", transfer = true) {
   settings.validate()
   _state.update { it.copy(retry = emptyList()) }
   app.storage.prepareDownloads(false)
   val failures = java.util.Collections.synchronizedList(mutableListOf<String>())
   val completed = java.util.concurrent.atomic.AtomicInteger()
   val total = if (local) downloaded.size else selected.size
   val names = mutableSetOf<String>()
   val remoteNames = if (local) downloaded.associate { it.path to FileNames.unique(File(it.path).name) { name -> name in names }.also(names::add) }
    else selected.associate { it.key to FileNames.unique(it.name) { name -> name in names }.also(names::add) }
   // Check the total required capacity before allowing retained files to run in parallel.
   if (!local && settings.keep) requireSpace(selected.filter { app.storage.existing(it) == null }.sumOf { it.size.coerceAtLeast(0) })
   suspend fun report(name: String, task: suspend () -> Unit) {
    try { task(); completed.incrementAndGet() }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) {
     failures += "$name: ${e.message}"
     if (!settings.keep) throw e // retained partial must not accumulate with the next download
    } finally {
     refreshDownloads(true)
     _state.update { it.copy(failures = failures.toList(), progress = "FTP completati ${completed.get()}/$total") }
    }
   }
   suspend fun localFile(file: Downloaded, name: String) {
    val source = File(file.path)
    check(source.canonicalFile.parentFile == app.storage.root.canonicalFile && source.isFile) { "File esterno alla cartella di lavoro." }
    val modified = source.lastModified()
    val sent = source.inputStream().use { input -> FtpUploader(settings).upload(name, { input.read(it) }) { sent ->
     _state.update { it.copy(progress = "FTP · ${file.originalName}: ${sizeLabel(sent)} / ${sizeLabel(file.size)}") }
    } }
    check(sent == file.size && source.length() == file.size && source.lastModified() == modified) { "Il file locale è cambiato durante l'invio. Non è stato eliminato." }
    if (!settings.keep) app.storage.delete(listOf(file))
   }
   suspend fun attachment(a: Attachment) = fileLocks.getOrPut(a.fileId) { Mutex() }.withLock {
    report(a.name) {
     val existing = app.storage.existing(a)
     if (existing != null) localFile(existing, remoteNames.getValue(a.key))
     else {
      requireSpace(a.size)
      val target = if (settings.keep) app.storage.beginDownload(a) else null
      _state.update { it.copy(activeDownloads = it.activeDownloads + (a.fileId to DownloadProgress(a.name, size = a.size))) }
      var uploaded = false
      var started = false
      var transferError: Throwable? = null
      try {
       started = true
       tg.startStreaming(a)
       GrowingFileReader({ offset -> tg.available(a, offset) }).use { reader ->
        FtpUploader(settings).upload(remoteNames.getValue(a.key), reader::read) { sent ->
         _state.update { s -> val active = s.activeDownloads[a.fileId]
          s.copy(activeDownloads = if (active == null) s.activeDownloads else s.activeDownloads + (a.fileId to active.copy(uploaded = sent))) }
        }
       }
       uploaded = true
      } catch (e: Throwable) { transferError = e; throw e } finally {
       withContext(NonCancellable) {
        if (started) {
         try {
          tg.stopStreaming(a, delete = uploaded && !settings.keep)
          if (target != null) {
           val available = tg.available(a, 0)
           if (available.complete && available.file != null) app.storage.finishDownload(available.file, target)
          }
         } catch (e: Exception) {
          if (transferError != null) transferError.addSuppressed(e)
          else throw IllegalStateException("FTP inviato, ma pulizia/salvataggio locale non completato: ${e.message}", e)
         }
        }
        _state.update { it.copy(activeDownloads = it.activeDownloads - a.fileId) }
       }
      }
     }
    }
   }
   if (local) {
    if (settings.keep) ParallelDownloads.run(downloaded) { file -> report(file.originalName) { localFile(file, remoteNames.getValue(file.path)) } }
    else for (file in downloaded) report(file.originalName) { localFile(file, remoteNames.getValue(file.path)) }
   } else {
    if (settings.keep) ParallelDownloads.run(selected, ::attachment)
    else for (a in selected) attachment(a)
   }
   notify("FTP: ${completed.get()}/$total file inviati. " + if (settings.keep) "File locali conservati." else "Copie locali eliminate dopo la conferma FTP.")
  }
 }
 private fun archiveTransfer(selected: List<Attachment>, downloaded: List<Downloaded>, local: Boolean, share: Boolean,
  settings: FtpSettings? = null, subdirectory: String = "") = launchOperation("Preparazione archivi…", transfer = true) {
  val contents = _state.value.archiveContents
  settings?.validate()
  val subpath = subdirectory.trim().trim('/').let { if(it.isEmpty()) "" else ArchiveNames.safeEntry(it) }
  app.storage.prepareDownloads(settings == null && !local && _state.value.clearBeforeDownload)
  val allSaved = mutableListOf<Downloaded>()
  val remoteTouched = mutableListOf<String>()
  val uploader = settings?.let { FtpUploader(it, subpath) }
  suspend fun save(item: Attachment): List<Downloaded> {
   val family = ArchiveNames.family(ArchiveNames.members(item).first().name)
   val raw = _state.value.attachments.flatMap(ArchiveNames::members).filter { ArchiveNames.family(it.name) == family }
   require((item.parts.isEmpty() && ArchiveNames.part(item.name) == null) || raw.map { it.name.lowercase() }.distinct().size == raw.size) { "Più archivi con parti omonime: restringi il periodo prima del download." }
   var parts = ArchiveNames.members(item)
   val originals = parts
   val needsName = ArchiveNames.isArchive(item.name) && parts.any { part ->
    app.storage.existing(part) == null && File(app.storage.root, part.name).exists()
   }
   if (needsName) {
    val originalStem = item.name.substringBeforeLast('.')
    val extension = item.name.substringAfterLast('.')
    fun renamed(name: String) = originals.map { it.copy(name = name.substringBeforeLast('.') + it.name.removePrefix(originalStem)) }
    var suggestion = FileNames.unique(item.name) { candidate -> renamed(candidate).any { File(app.storage.root, it.name).exists() } }
    while(true) {
     val reply = CompletableDeferred<String?>()
     renameReply = reply
     _state.update { it.copy(archiveRename = suggestion) }
     val answer = try { reply.await() ?: throw CancellationException() } finally { renameReply = null; _state.update { it.copy(archiveRename = null) } }
     if (answer != FileNames.safe(answer) || !answer.endsWith(".$extension", true)) { notify("Mantieni l'estensione .$extension e usa un nome file valido."); continue }
     parts = renamed(answer)
     if (parts.any { File(app.storage.root, it.name).exists() }) { suggestion = answer; notify("Nome già presente. Scegli un nome diverso."); continue }
     break
    }
   }
   requireSpace(parts.filter { needsName || app.storage.existing(it) == null }.sumOf { it.size.coerceAtLeast(0) })
   val saved = ConcurrentHashMap<String, Downloaded>()
   ParallelDownloads.run(parts) { part -> fileLocks.getOrPut(part.fileId) { Mutex() }.withLock {
    val existing = if(needsName) null else app.storage.existing(part)
    if(existing != null) saved[part.key] = existing else {
     val target = app.storage.beginDownload(part)
     _state.update { it.copy(activeDownloads = it.activeDownloads + (part.fileId to DownloadProgress(part.name, size=part.size))) }
     try { saved[part.key] = app.storage.finishDownload(tg.download(part), target) }
     finally { _state.update { it.copy(activeDownloads = it.activeDownloads - part.fileId) }; refreshDownloads(true) }
    }
   } }
   return parts.map { saved.getValue(it.key) }
  }
  suspend fun send(files: List<Downloaded>) {
   if(uploader == null) return
   val source = File(files.first().path)
   val snapshots = files.map { File(it.path).let { f -> Triple(f, f.length(), f.lastModified()) } }
   if(contents && ArchiveNames.isArchive(source.name)) {
    ArchiveReader(files.map { File(it.path) }).use { reader ->
     var count = 0
     while(true) {
      currentCoroutineContext().ensureActive()
      val entry = reader.next() ?: break
      remoteTouched += entry.name
      val sent = uploader.upload(entry.name, { reader.read(it) }) { bytes -> _state.update { it.copy(progress="FTP · ${entry.name}: ${sizeLabel(bytes)}") } }
      check(entry.size < 0 || sent == entry.size) { "Dimensione estratta non corretta: ${entry.name}" }
      count++
     }
     check(count > 0) { "L'archivio non contiene file da inviare." }
    }
   } else for(file in files) {
    val name = File(file.path).name
    remoteTouched += name
    val sent = File(file.path).inputStream().use { input -> uploader.upload(name, { input.read(it) }) { bytes -> _state.update { it.copy(progress="FTP · $name: ${sizeLabel(bytes)}") } } }
    check(sent == file.size) { "Dimensione FTP non corretta: $name" }
   }
   check(snapshots.all { (f,size,time) -> f.isFile && f.length()==size && f.lastModified()==time }) { "Archivio modificato durante l'invio: conservato sul dispositivo." }
   if(!settings.keep) app.storage.delete(files)
   remoteTouched.clear()
  }
  try {
   if(local) {
    val seen = mutableSetOf<String>()
    for(file in downloaded) {
     val volumes = if(ArchiveNames.isArchive(file.originalName)) ArchiveReader.volumes(File(file.path)) else listOf(File(file.path))
     if(!seen.add(volumes.joinToString { it.path })) continue
     val files = volumes.map { f -> app.storage.list().first { it.path == f.path } }
     send(files)
    }
   } else for(item in selected) {
    val files = save(item)
    allSaved += files
    send(files)
   }
   if(share) _state.update { it.copy(shareFiles = allSaved) }
   _state.update { it.copy(tab = AppTab.DOWNLOADS.ordinal, progress = if(settings != null) "Invio FTP completato" else "Archivi e file salvati") }
  } catch(e: Exception) {
   if(settings != null && remoteTouched.isNotEmpty()) {
    pendingCleanup = Triple(settings, subpath, remoteTouched.distinct())
    _state.update { it.copy(remoteCleanup = remoteTouched.distinct()) }
   }
   throw e
  } finally { refreshDownloads(true) }
 }
 private var pendingCleanup: Triple<FtpSettings, String, List<String>>? = null
 fun remoteCleanup(confirmed: Boolean) {
  val pending = pendingCleanup ?: return
  pendingCleanup = null
  _state.update { it.copy(remoteCleanup = emptyList()) }
  if(confirmed) launchOperation("Rimozione file da FTP…", transfer = true) {
   FtpUploader(pending.first, pending.second).delete(pending.third)
   notify("File remoti rimossi. Eventuali file sovrascritti non sono recuperabili.")
  }
 }
 private fun requireSpace(size: Long) {
  val free = app.storage.root.usableSpace
  check(size.coerceAtLeast(0) <= (free - 16L * 1024 * 1024).coerceAtLeast(0)) { "Spazio insufficiente: serve spazio per l'intero file (${sizeLabel(size)}) nella cartella di lavoro." }
 }
 private fun Set<String>.toggle(key: String) = if (key in this) this - key else this + key
}
