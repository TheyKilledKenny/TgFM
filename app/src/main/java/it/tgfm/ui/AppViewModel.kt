package it.tgfm.ui

import it.tgfm.R
import it.tgfm.Words

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

class FtpOverwritePrompt(val path: String) {
 val approved = CompletableDeferred<Unit>()
}
private class FtpOverwriteDeclined : CancellationException()

data class UiState(
 val tab: Int = AppTab.SETTINGS.ordinal, val auth: String = "notStarted", val authState: JSONObject? = null, val connection: Int = R.string.offline,
 val rules: List<Rule> = emptyList(), val favorites: List<Channel> = emptyList(), val channels: List<Channel> = emptyList(), val channel: Channel? = null,
 val allAttachments: List<Attachment> = emptyList(),
 val attachments: List<Attachment> = emptyList(), val selected: Set<String> = emptySet(), val attachmentView: AttachmentView = AttachmentView.ALL, val clearBeforeDownload: Boolean = false,
 val postPeriod: PostPeriod = PostPeriod(), val scannedPeriod: Pair<PostPeriod, java.time.LocalDate>? = null, val scanComplete: Boolean = false, val cachedListing: Boolean = false,
 val previewPending: Boolean = false, val restoreReadPeriod: Boolean = false,
 val downloads: List<Downloaded> = emptyList(), val downloadedSelection: Set<String> = emptySet(),
 val busy: Boolean = false, val progress: String = "", val fraction: Float? = null,
 val message: String = "", val failures: List<String> = emptyList(), val retry: List<Attachment> = emptyList(),
 val activeDownloads: Map<Int, DownloadProgress> = emptyMap(),
 val workPath: String = "", val fileAccess: Boolean = false, val ftp: FtpSettings = FtpSettings(),
 val showCovers: Boolean = false,
 val archiveContents: Boolean = false, val archiveDefault: Boolean = false,
 val ftpOverwrite: FtpOverwritePrompt? = null, val archiveRename: String? = null, val remoteCleanup: List<String> = emptyList(),
 val loginError: String = "",
 val shareFiles: List<Downloaded> = emptyList(), val ready: Boolean = false
) {
 val activeRules: List<Rule> get() = if (channel == null) emptyList() else rules.filter { it.channelId == channel.id }
 fun archiveChoiceFor(files: List<Downloaded>): Boolean {
  val profiles = favorites.associateBy { it.id }
  val choices = files.filter { ArchiveNames.isArchive(it.originalName) }.map { file ->
   val chatId = file.sourceKey.substringBefore(':').toLongOrNull()
   profiles[chatId]?.profile?.useArchiveContents(archiveDefault) ?: archiveDefault
  }
  return choices.distinct().singleOrNull() ?: archiveDefault
 }
 fun resetListing(): UiState = copy(allAttachments = emptyList(), attachments = emptyList(), selected = emptySet(),
  scannedPeriod = null, scanComplete = false, cachedListing = false, previewPending = channel?.profile?.cacheAttachments == true, restoreReadPeriod = false)
 fun choosePeriod(value: PostPeriod): UiState {
  if (value == postPeriod) return if (restoreReadPeriod) copy(restoreReadPeriod = false) else this
  val next = copy(postPeriod = value)
  return if (channel?.profile?.cacheAttachments == true) next.resetListing() else next
 }
 fun withFiles(files: List<Attachment>, covers: Boolean, applyFilters: Boolean, keepExistingSelection: Boolean = false): UiState {
  val usedPhotos = if (covers) files.mapNotNull { file ->
   file.cover?.messageId?.let { file.chatId to it }
  }.toHashSet() else emptySet()
  val eligible = if (usedPhotos.isEmpty()) files else files.filterNot {
   it.category == Category.IMAGE && (it.chatId to it.messageId) in usedPhotos
  }
  val existing = if (keepExistingSelection) attachments.mapTo(hashSetOf()) { it.key } else emptySet()
  val selection = eligible.filter {
   if (applyFilters && it.key !in existing) FilterEngine.classify(it, activeRules) == Match.CERTAIN else it.key in selected
  }.mapTo(mutableSetOf()) { it.key }
  return copy(allAttachments = files, attachments = eligible, selected = selection, showCovers = covers)
 }
 fun withConfiguration(config: ChannelConfiguration): UiState {
  val changedChannel = channel?.id != config.channel?.id
  val profile = config.channel?.profile ?: ChannelProfile()
  val period = if (changedChannel || profile.initialPeriod != channel?.profile?.initialPeriod) PostPeriod(profile.initialPeriod) else postPeriod
  val configured = copy(favorites = config.favorites, channel = config.channel, rules = config.rules, postPeriod = period)
  val base = if (changedChannel || profile.cacheAttachments != (channel?.profile?.cacheAttachments == true) ||
   (profile.cacheAttachments && period != postPeriod)) configured.resetListing().copy(restoreReadPeriod = changedChannel && profile.cacheAttachments) else configured
  val rulesChanged = activeRules != base.activeRules
  return if (changedChannel || rulesChanged || showCovers != profile.covers)
   base.withFiles(base.allAttachments, profile.covers, applyFilters = rulesChanged) else base
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
 private val previewLock = Mutex()
 private var foregroundDay = java.time.LocalDate.now()
 private val coverSlots = kotlinx.coroutines.sync.Semaphore(2)
 fun saveProfile(id: Long, profile: ChannelProfile) = launchOperation(Words.get(R.string.saving_channel)) {
  applyConfiguration(app.preferences.saveProfile(id, profile))
 }
 suspend fun channelAvatar(id: Long): CoverImage? {
  if (_state.value.auth != "authorizationStateReady") return null
  return try {
   it.tgfm.telegram.AttachmentMapper.chatAvatar(tg.send(request("getChat", "chat_id" to id)))
  } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
 }
 suspend fun coverFile(preview: CoverImage): File? {
  coverSlots.acquire()
  try { return if(_state.value.auth == "authorizationStateReady") {
   val id = if (preview.fileId != 0) preview.fileId else tg.send(request("getRemoteFile", "remote_file_id" to preview.remoteId)).getInt("id")
   tg.cover(id)
  } else null }
  catch(e: CancellationException) { throw e }
  catch(_: Exception) { return null }
  finally { coverSlots.release() }
 }
 private val overwriteLock = Mutex()
 private suspend fun confirmFtpOverwrite(path: String) = overwriteLock.withLock {
  val prompt = FtpOverwritePrompt(path)
  _state.update { it.copy(ftpOverwrite = prompt) }
  try { prompt.approved.await() } finally { _state.update { it.copy(ftpOverwrite = null) } }
 }
 fun declineFtpOverwrite() { operation?.cancel(FtpOverwriteDeclined()) }
 private var renameReply: CompletableDeferred<String?>? = null
 fun answerArchiveName(name: String?) { renameReply?.complete(name) }
 fun archiveMode(contents: Boolean) { _state.update { it.copy(archiveContents = contents) } }
 fun archiveDefault(contents: Boolean) { viewModelScope.launch { app.preferences.saveArchiveContents(contents) } }
 init {
  viewModelScope.launch {
   state.map { it.previewRequest() }.distinctUntilChanged()
    .collectLatest { request -> if (request != null) withContext(Dispatchers.IO) { loadPreview(request) } }
  }
  viewModelScope.launch { app.preferences.archiveContents.collect { value -> _state.update { it.copy(archiveContents = value, archiveDefault = value) } } }
  viewModelScope.launch { app.preferences.configuration.catch { notify(Words.get(R.string.could_not_read_channels_and_filters, it.message)) }.collect { config ->
   applyConfiguration(config)
   routeForConfiguration()
  } }
  viewModelScope.launch { app.preferences.attachmentView.catch { notify(Words.get(R.string.could_not_read_preferences, it.message)) }.collect { value -> _state.update { it.copy(attachmentView = value) } } }
  viewModelScope.launch { app.preferences.clearBeforeDownload.catch { notify(Words.get(R.string.could_not_read_download_settings, it.message)) }.collect { value -> _state.update { it.copy(clearBeforeDownload = value) } } }
  viewModelScope.launch { tg.auth.collect { auth ->
   _state.update {
    val next = it.copy(auth = auth.optString("@type"), authState = auth)
    if (next.auth != it.auth && (next.auth == "authorizationStateReady" || it.auth == "authorizationStateReady")) next.resetListing().copy(restoreReadPeriod = next.auth == "authorizationStateReady" && next.channel?.profile?.cacheAttachments == true) else next
   }
   routeForConfiguration()
   if (auth.optString("@type") == "authorizationStateClosed") _state.update { it.copy(channels = emptyList(), allAttachments = emptyList(), attachments = emptyList(), selected = emptySet(), scannedPeriod = null, scanComplete = false) }
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
  launchOperation(Words.get(R.string.opening_archive), onFinished = { _state.update { it.copy(ready = true) } }) {
   val credentials = savedCredentials()
   val initialConfig = app.preferences.configuration.first()
   val clearPrevious = app.preferences.clearBeforeDownload.first()
   applyConfiguration(initialConfig)
   _state.update { it.copy(clearBeforeDownload = clearPrevious) }
   updateStorageState()
   try { _state.update { it.copy(ftp = app.vault.loadFtp()) } }
   catch (_: Exception) { notify(Words.get(R.string.could_not_read_ftp_settings_set_up)) }
   refreshDownloads(selectAll = true)
   if (credentials != null && app.storage.folder.hasAccess()) {
    app.storage.folder.requireReady()
    tg.start(credentials)
   }
  }
 }
 private fun savedCredentials(): Credentials? = try { app.vault.load() } catch (_: Exception) {
  notify(Words.get(R.string.enter_your_api_id_and_api_hash))
  null
 }

 fun authDescription(auth: JSONObject): String = when (auth.optString("@type")) {
  "authorizationStateWaitCode" -> Words.get(R.string.enter_the_code_from_telegram, auth.optJSONObject("code_info")?.optJSONObject("type")?.optString("@type")?.removePrefix("authenticationCodeType") ?: Words.get(R.string.code))
  "authorizationStateWaitPassword" -> Words.get(R.string.two_step_verification_hint, auth.optString("password_hint"))
  "authorizationStateWaitEmailCode" -> Words.get(R.string.enter_the_code_sent_to_your_account)
  "authorizationStateWaitOtherDeviceConfirmation" -> Words.get(R.string.confirm_sign_in_in_telegram, auth.optString("link"))
  "authorizationStateWaitRegistration" -> Words.get(R.string.an_existing_telegram_account_is_required_register)
  "authorizationStateWaitPremiumPurchase" -> Words.get(R.string.telegram_requires_a_premium_purchase_for_this)
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
   catch (e: Exception) { notify(Words.get(R.string.could_not_save_download_settings, e.message)) }
  }
 }
 fun attachmentView(value: AttachmentView) {
  _state.update { it.copy(attachmentView = value) }
  viewModelScope.launch { runCatching { app.preferences.saveAttachmentView(value) }.onFailure { notify(Words.get(R.string.could_not_save_preference, it.message)) } }
 }
 fun postPeriod(value: PostPeriod) { _state.update { if (it.busy) it else it.choosePeriod(value) } }
 private fun launchOperation(label: String, transfer: Boolean = false, cancelMessage: Int = R.string.operation_canceled_previously_read_attachments_and_completed, onFinished: () -> Unit = {}, body: suspend () -> Unit) {
  if (_state.value.busy) return
  _state.update { it.copy(busy = true, progress = label, fraction = null, failures = emptyList()) }
  val job = viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
   try {
    if (transfer) ContextCompat.startForegroundService(app, Intent(app, TransferService::class.java))
    ensureActive()
    body()
   } catch (e: TimeoutCancellationException) { notify(Words.get(R.string.timed_out_check_your_connection_and_try)) }
   catch (e: CancellationException) { throw e }
   catch (e: Exception) { notify(e.message ?: Words.get(R.string.operation_failed)) }
   catch (e: UnsatisfiedLinkError) { notify(Words.get(R.string.tdlib_unavailable_for_this_device, e.message)) }
   finally {
    if (transfer) app.stopService(Intent(app, TransferService::class.java))
   }
  }
  operation = job
  job.invokeOnCompletion { cause ->
   onFinished()
   if (cause is CancellationException) notify(Words.get(cancelMessage))
   _state.update { it.copy(busy = false, fraction = null, activeDownloads = emptyMap(), progress = if (transfer) it.progress else "") }
   if (foregroundRecheckPending && _state.value.ready) {
    foregroundRecheckPending = false
    onForeground()
   }
  }
  job.start()
 }
 fun cancel() { operation?.cancel() }
 fun connect(id: String, hash: String) = launchOperation(Words.get(R.string.starting_telegram_sign_in)) {
  _state.update { it.copy(loginError = "") }
  try {
   val apiId = id.trim().toIntOrNull() ?: 0
   val apiHash = hash.trim()
   require(apiId > 0 && Regex("[a-fA-F0-9]{32}").matches(apiHash)) { Words.get(R.string.invalid_api_id_or_api_hash) }
   app.storage.folder.requireReady()
   tg.closeForStorageChange()
   // Configura accesso starts a new login, without recovering a previous database/key.
   val database = File(app.filesDir, "telegram")
   check(!database.exists() || database.deleteRecursively()) { Words.get(R.string.could_not_start_a_new_telegram_session) }
   val credentials = app.vault.saveNew(apiId, apiHash)
   tg.start(credentials)
  } catch (e: Exception) {
   _state.update { it.copy(loginError = e.message ?: Words.get(R.string.could_not_start_telegram)) }
   throw e
  }
 }
 fun reconfigure() = launchOperation(Words.get(R.string.closing_session)) {
  tg.closeForStorageChange()
  _state.update { it.copy(loginError = "") }
 }
 fun authenticate(value: String) = launchOperation(Words.get(R.string.checking_credentials)) {
  val q = when (_state.value.auth) {
   "authorizationStateWaitPhoneNumber" -> request("setAuthenticationPhoneNumber", "phone_number" to value.trim(), "settings" to request("phoneNumberAuthenticationSettings", "allow_flash_call" to false, "allow_missed_call" to false))
   "authorizationStateWaitCode" -> request("checkAuthenticationCode", "code" to value.trim())
   "authorizationStateWaitPassword" -> request("checkAuthenticationPassword", "password" to value)
   "authorizationStateWaitEmailAddress" -> request("setAuthenticationEmailAddress", "email_address" to value.trim())
   "authorizationStateWaitEmailCode" -> request("checkAuthenticationEmailCode", "code" to request("emailAddressAuthenticationCode", "code" to value.trim()))
   else -> error(Words.get(R.string.wait_for_the_next_sign_in_step))
  }
  tg.send(q)
 }
 fun resendCode() = launchOperation(Words.get(R.string.requesting_a_new_code)) { tg.send(request("resendAuthenticationCode", "reason" to null)) }
 fun logout() = launchOperation(Words.get(R.string.signing_out)) {
  tg.send(request("logOut")); app.preferences.saveChannel(null)
  _state.update { it.copy(allAttachments = emptyList(), attachments = emptyList(), selected = emptySet(), channels = emptyList(), channel = null, retry = emptyList(), scannedPeriod = null, scanComplete = false) }
 }
 fun loadChannels() = launchOperation(Words.get(R.string.reading_channels)) {
  val channels = tg.channels { channels -> _state.update { it.copy(channels = channels, progress = Words.get(R.string.channels_and_groups_found, channels.size)) } }
  _state.update { it.copy(channels = channels) }; app.preferences.updateFavoriteTitles(channels)
  if (channels.isEmpty()) notify(Words.get(R.string.no_accessible_channels_or_groups_join_one))
 }
 fun chooseChannel(channel: Channel) = launchOperation(Words.get(R.string.saving_channel)) {
  applyConfiguration(app.preferences.selectFavorite(channel.id)); routeForConfiguration()
 }
 fun addFavorite(channel: Channel) {
  initialRoutePending = false
  launchOperation(Words.get(R.string.adding_favorite)) { applyConfiguration(app.preferences.addFavorite(channel)) }
 }
 fun removeFavorite(channel: Channel) = launchOperation(Words.get(R.string.removing_favorite)) {
  previewLock.withLock { cacheFor(channel.id).use { it.clear() } }
  applyConfiguration(app.preferences.removeFavorite(channel.id)); routeForConfiguration()
 }
 fun onForeground() {
  tg.onForeground()
  val today = java.time.LocalDate.now()
  if (today != foregroundDay && !_state.value.busy) {
   foregroundDay = today
   _state.update { if (it.channel?.profile?.cacheAttachments == true && it.postPeriod.mode in setOf(PostPeriodMode.TODAY, PostPeriodMode.WEEK)) it.resetListing() else it }
  }
  updateStorageState()
  if (!_state.value.ready || _state.value.busy) { foregroundRecheckPending = true; return }
  if (_state.value.fileAccess && _state.value.auth == "notStarted") {
   launchOperation(Words.get(R.string.starting_telegram)) {
    val credentials = savedCredentials() ?: return@launchOperation
    app.storage.folder.requireReady()
    refreshDownloads()
    tg.start(credentials)
   }
  }
 }
 private fun updateStorageState() { _state.update { it.copy(workPath = app.storage.folder.root.absolutePath, fileAccess = app.storage.folder.hasAccess()) } }
 fun chooseWorkFolder(uri: Uri) = launchOperation(Words.get(R.string.setting_up_folder)) {
  val folder = app.storage.folder.fromTree(uri)
  val changedFolder = folder != app.storage.root.canonicalFile
  tg.closeForStorageChange()
  app.storage.folder.save(folder)
  app.storage.folder.requireReady()
  updateStorageState(); refreshDownloads(selectAll = changedFolder)
  savedCredentials()?.let(tg::start)
 }
 fun clearCache() = launchOperation(Words.get(R.string.clearing_cache)) {
  app.storage.folder.requireReady()
  tg.closeForStorageChange()
  app.storage.clearCache()
  savedCredentials()?.let(tg::start)
  notify(Words.get(R.string.cache_cleared_files_saved_in_the_working))
 }
 fun saveFtp(settings: FtpSettings) = launchOperation(Words.get(R.string.saving_ftp_settings)) {
  app.vault.saveFtp(settings); _state.update { it.copy(ftp = settings) }; notify(Words.get(R.string.ftp_settings_saved))
 }
 fun testFtp() = launchOperation(Words.get(R.string.checking_ftp_connection)) {
  FtpUploader(_state.value.ftp).test(); notify(Words.get(R.string.ftp_login_and_folder_verified_write_access))
 }
 fun shared() { _state.update { it.copy(shareFiles = emptyList()) } }
 fun deleteDownloads(files: List<Downloaded> = selectedDownloads()) = launchOperation(Words.get(R.string.deleting_files)) {
  try { app.storage.delete(files) } finally { refreshDownloads() }
 }

 private data class PreviewRequest(val chat: Long, val period: PostPeriod, val restorePeriod: Boolean)
 private fun UiState.previewRequest(): PreviewRequest? {
  val active = channel ?: return null
  return if (previewPending && active.profile.cacheAttachments && auth == "authorizationStateReady" && !busy)
   PreviewRequest(active.id, postPeriod, restoreReadPeriod) else null
 }
 private suspend fun loadPreview(request: PreviewRequest) {
  val today = java.time.LocalDate.now()
  try {
   previewLock.withLock {
    cacheFor(request.chat).use { cache ->
     val period = if (request.restorePeriod && cache.hasHistory()) cache.lastPeriod() ?: request.period else request.period
     val span = try { period.window(today).historyUntil(java.time.Instant.now().epochSecond + 1) }
      catch (_: IllegalArgumentException) { null }
     val covered = if (span != null && cache.hasHistory()) cache.covered() else emptyList()
     val examined = span != null && covered.any { it.start < span.end && span.start < it.end }
     val files = if (examined && span != null)
      ArchiveNames.group(cachedAttachments(cache.load(span), covered, request.chat)) else emptyList()
     val complete = examined && span != null && cache.missing(span).isEmpty()
     currentCoroutineContext().ensureActive()
     _state.update { s ->
      if (s.previewRequest() != request) s
      else s.withFiles(files, s.showCovers, applyFilters = true).copy(postPeriod = period,
       previewPending = false, restoreReadPeriod = false, cachedListing = true,
       scannedPeriod = if (examined) period to today else null, scanComplete = complete)
     }
    }
   }
  } catch (e: CancellationException) { currentCoroutineContext().ensureActive(); previewFailed(request, e) }
  catch (e: Exception) { previewFailed(request, e) }
 }
 private fun previewFailed(request: PreviewRequest, error: Exception) {
  _state.update { if (it.previewRequest() == request) it.copy(previewPending = false, restoreReadPeriod = false, message = error.localizedMessage ?: Words.get(R.string.operation_failed)) else it }
 }
 private fun PostWindow.historyUntil(horizon: Long): HistoryRange? {
  val lo = HistoryPoint(start ?: 0L)
  val hi = HistoryPoint(minOf(endExclusive ?: horizon, horizon))
  return if (lo < hi) HistoryRange(lo, hi) else null
 }
 private suspend fun cacheFor(chat: Long) = AttachmentCache(app, tg.accountId(), chat)
 suspend fun cacheOptions(channel: Channel): Pair<Boolean, PostPeriod> = withContext(Dispatchers.IO) {
  cacheFor(channel.id).use { it.hasData() to (it.lastPeriod() ?: PostPeriod(channel.profile.initialPeriod)) }
 }
 fun clearAttachmentCache(channel: Channel) = launchOperation(Words.get(R.string.clear_attachment_cache)) {
  previewLock.withLock { cacheFor(channel.id).use { it.clear() } }
  _state.update { if (it.channel?.id == channel.id && (it.cachedListing || it.channel.profile.cacheAttachments)) it.resetListing() else it }
  notify(Words.get(R.string.attachment_cache_cleared))
 }
 fun scan() {
  val channel = _state.value.channel ?: return
  readPeriod(channel, _state.value.postPeriod, refresh = false)
 }
 fun refreshFromTelegram(channel: Channel, period: PostPeriod) {
  if (channel.profile.cacheAttachments) readPeriod(channel, period, refresh = true)
 }
 private fun readPeriod(channel: Channel, period: PostPeriod, refresh: Boolean) =
  launchOperation(Words.get(R.string.reading_metadata), cancelMessage = R.string.cache_partial) {
   val today = java.time.LocalDate.now()
   val window = period.window(today)
   val visible = _state.value.channel?.id == channel.id
   if (visible) _state.update { s ->
    val base = if (channel.profile.cacheAttachments && s.postPeriod == period && s.cachedListing) s else s.resetListing()
    base.copy(scannedPeriod = period to today, scanComplete = false, postPeriod = period, cachedListing = channel.profile.cacheAttachments, previewPending = false, restoreReadPeriod = false)
   }
   fun display(page: List<Attachment>) {
    val grouped = if (visible) ArchiveNames.group(page) else emptyList()
    _state.update { s ->
     val updated = if (visible && s.channel?.id == channel.id) s.withFiles(grouped, s.showCovers, applyFilters = true, keepExistingSelection = channel.profile.cacheAttachments) else s
     updated.copy(progress = Words.get(R.string.attachments_found, page.size))
    }
   }
   if (!channel.profile.cacheAttachments) {
    val files = tg.attachments(channel.id, window, ::display)
    cacheFor(channel.id).use { it.remember(period) }
    _state.update { it.copy(scanComplete = true) }
    notify(if (files.isEmpty()) Words.get(R.string.no_attachments_available) else Words.get(R.string.attachments_read_exact_matches_selected, files.size, _state.value.selected.size))
    return@launchOperation
   }
   val now = java.time.Instant.now().epochSecond
   val horizon = now + 1
   val span = window.historyUntil(horizon)
   cacheFor(channel.id).use { cache ->
    cache.remember(period)
    if (span != null) {
     val (start, end) = span
     val posts = cache.load(span)
     fun show() = display(cachedAttachments(posts, cache.covered(), channel.id))
     show()
     if (refresh) cache.requestRefresh(span, end.date == horizon)
     val gaps = cache.missing(span)
     val onlyTail = !refresh && end.date == horizon && gaps.size == 1 && gaps.single().let { it.end == end && it.start > start }
     fun online() = tg.connection.value == R.string.connected
     fun checkOnline() = check(online()) { Words.get(if (onlyTail) R.string.cache_offline_new else R.string.cache_partial) }
     try {
      if (gaps.isNotEmpty()) {
       if (tg.connection.value in setOf(R.string.connecting_to_telegram, R.string.updating)) {
        withTimeoutOrNull(8000) { tg.connection.first { it == R.string.connected || it == R.string.no_internet_connection } }
       }
       checkOnline()
      }
      tg.readHistory(channel.id, gaps, end.takeIf { it.date == horizon }) { examined, messages ->
       checkOnline()
       val page = messages.map(CachedPost::from)
       // A later post may have the very same Unix second as this scan.
       val checkpoint = if (examined.end == end && end.date == horizon) {
        val lastId = page.filter { it.point.date == now }.maxOfOrNull { it.point.id } ?: -1L
        minOf(end, HistoryPoint(now,lastId + 1))
       } else examined.end
       if (examined.start < checkpoint) {
        val verified = HistoryRange(examined.start, checkpoint)
        cache.accept(verified, page)
        posts.subMap(verified.start,true,verified.end,false).clear()
        page.forEach { posts[it.point] = it }
        show()
       }
      }
     } catch (e: TimeoutCancellationException) {
      currentCoroutineContext().ensureActive()
      notify(Words.get(if (!online() && onlyTail) R.string.cache_offline_new else R.string.cache_partial))
      return@launchOperation
     } catch (e: CancellationException) { throw e }
     catch (e: Exception) {
      notify(if (!online() && onlyTail) Words.get(R.string.cache_offline_new)
       else Words.get(R.string.cache_partial) + if (online()) " " + e.localizedMessage.orEmpty() else "")
      return@launchOperation
     }
    }
    if (visible) _state.update { it.copy(scanComplete = true) }
    if (refresh) { cache.finishRefresh(); notify(Words.get(R.string.attachment_cache_updated)) }
   }
  }
 fun toggle(key: String, local: Boolean = false) { if (_state.value.busy) return; _state.update { s ->
  if (local) s.copy(downloadedSelection = s.downloadedSelection.toggle(key)) else if (s.attachments.any { it.key == key }) s.copy(selected = s.selected.toggle(key)) else s
 } }
 fun selectAll(local: Boolean, value: Boolean) { if (_state.value.busy) return; _state.update { s ->
  if (local) s.copy(downloadedSelection = if (value) s.downloads.map { it.path }.toSet() else emptySet())
  else s.copy(selected = if (value) s.attachments.map { it.key }.toSet() else emptySet())
 } }
 fun saveRule(rule: Rule) = launchOperation(Words.get(R.string.saving_filter)) {
  FilterEngine.validate(rule)?.let { error(it) }
  check(_state.value.favorites.any { it.id == rule.channelId }) { Words.get(R.string.assign_the_filter_to_a_favorite_channel) }
  val rules = _state.value.rules.filterNot { it.id == rule.id } + rule
  applyConfiguration(app.preferences.saveRules(rules))
 }
 fun saveLearnedRules(drafts: List<Rule>) = launchOperation(Words.get(R.string.saving_common_rules)) {
  check(drafts.isNotEmpty()) { Words.get(R.string.no_rules_to_save) }
  val rules = _state.value.rules.toMutableList()
  for (rule in drafts) {
   FilterEngine.validate(rule)?.let { error(it) }
   check(_state.value.favorites.any { it.id == rule.channelId }) { Words.get(R.string.assign_the_filter_to_a_favorite_channel) }
   if (rules.none { FilterEngine.sameCriteria(it, rule) }) rules += rule
  }
  val added = rules.size - _state.value.rules.size
  applyConfiguration(app.preferences.saveRules(rules))
  notify(Words.get(R.string.rules_added_existing_rules_were_not_duplicated, added))
 }
 fun deleteRule(rule: Rule) = launchOperation(Words.get(R.string.deleting_filter)) {
  val rules = _state.value.rules.filterNot { it.id == rule.id }; applyConfiguration(app.preferences.saveRules(rules))
 }
 fun download(retryOnly: Boolean = false, share: Boolean = false) {
  val files = if (retryOnly) _state.value.retry else _state.value.attachments.filter { it.key in _state.value.selected }
  if (files.isEmpty()) { notify(Words.get(R.string.select_at_least_one_attachment)); return }
  if (files.any { ArchiveNames.isArchive(it.name) }) { archiveTransfer(files, emptyList(), false, share); return }
  launchOperation(Words.get(R.string.preparing_download), transfer = true) {
   if (!retryOnly) { app.storage.prepareDownloads(_state.value.clearBeforeDownload); refreshDownloads() }
   _state.update { it.copy(tab = AppTab.DOWNLOADS.ordinal, retry = files) }
   val saved = ConcurrentHashMap<String, Downloaded>()
   val completed = ConcurrentHashMap.newKeySet<String>()
   val failures = ConcurrentHashMap<String, String>()
   fun updateSummary() {
    _state.update { s -> s.copy(retry = files.filterNot { it.key in completed },
     failures = files.mapNotNull { failures[it.key] },
     progress = Words.get(R.string.completed_active, completed.size, files.size, s.activeDownloads.size, ParallelDownloads.LIMIT),
     fraction = completed.size.toFloat() / files.size) }
   }
   ParallelDownloads.run(files) { listed ->
    try {
     withAttachment(listed) { a, existing ->
      if (existing != null) saved[a.key] = existing else {
       val target = app.storage.beginDownload(a)
       refreshDownloads()
       _state.update { it.copy(activeDownloads = it.activeDownloads + (a.fileId to DownloadProgress(a.name, size = a.size))) }
       try {
        updateSummary()
        saved[a.key] = finishDownload(tg.download(a), target)
       } finally { _state.update { it.copy(activeDownloads = it.activeDownloads - a.fileId) } }
      }
      completed += a.key
     }
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { failures[listed.key] = "${listed.name}: ${e.message}" }
    finally { runCatching { refreshDownloads() }; updateSummary() }
   }
   refreshDownloads()
   _state.update { it.copy(progress = Words.get(R.string.downloads_completed, completed.size, files.size), fraction = null,
    shareFiles = if (share && completed.size == files.size) files.map { a -> saved.getValue(a.key) } else emptyList()) }
   if (share && completed.size != files.size) notify(Words.get(R.string.sharing_could_not_start_because_some_files))

  }
 }
 private suspend fun withAttachment(listed: Attachment, reuse: Boolean = true, body: suspend (Attachment, Downloaded?) -> Unit) {
  currentCoroutineContext().ensureActive()
  if (reuse && listed.cachedSlot != null) {
   app.storage.existing(listed)?.let { body(listed, it); return }
  }
  val live = tg.resolveCached(listed)
  fileLocks.getOrPut(live.fileId) { Mutex() }.withLock {
   currentCoroutineContext().ensureActive()
   body(live, if (reuse) app.storage.existing(live) else null)
  }
 }
 @Synchronized private fun finishDownload(source: File, target: DownloadTarget): Downloaded =
  app.storage.finishDownload(source, target).also { file ->
   _state.update { it.copy(downloadedSelection = it.downloadedSelection + file.path) }
  }
 @Synchronized private fun refreshDownloads(selectAll: Boolean = false) {
  val files = app.storage.list()
  val paths = files.map { it.path }.toSet()
  _state.update { s ->
   val selected = if (selectAll) paths else s.downloadedSelection.intersect(paths)
   s.copy(downloads = files, downloadedSelection = selected)
  }
 }
 fun refreshLocalFiles() = launchOperation(Words.get(R.string.reading_folder)) { refreshDownloads() }
 fun selectedDownloads() = _state.value.downloads.filter { it.path in _state.value.downloadedSelection }
 fun uploadFtp(local: Boolean = false, keep: Boolean = _state.value.ftp.keep, subdirectory: String = "") {
  val selected = _state.value.attachments.filter { it.key in _state.value.selected }
  val downloaded = selectedDownloads()
  if (if (local) downloaded.isEmpty() else selected.isEmpty()) { notify(Words.get(R.string.select_at_least_one_file)); return }
  val settings = _state.value.ftp.copy(keep = keep)
  if (subdirectory.isNotBlank() || (if(local) downloaded.any { ArchiveNames.isArchive(it.originalName) } else selected.any { ArchiveNames.isArchive(it.name) })) { archiveTransfer(selected, downloaded, local, share = false, settings = settings, subdirectory = subdirectory); return }
  launchOperation(Words.get(R.string.preparing_ftp), transfer = true) {
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
   // Servers may treat case variants as one path. Keep their existence checks and writes in order.
   val destinationLocks = remoteNames.values.associate { it.lowercase() to Mutex() }
   suspend fun report(name: String, destination: String, task: suspend () -> Unit) {
    try { destinationLocks.getValue(destination.lowercase()).withLock { task() }; completed.incrementAndGet() }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) {
     failures += "$name: ${e.message}"
     if (!settings.keep) throw e // retained partial must not accumulate with the next download
    } finally {
     refreshDownloads()
     _state.update { it.copy(failures = failures.toList(), progress = Words.get(R.string.ftp_transfers_completed, completed.get(), total)) }
    }
   }
   suspend fun localFile(file: Downloaded, name: String) {
    val source = File(file.path)
    check(source.canonicalFile.parentFile == app.storage.root.canonicalFile && source.isFile) { Words.get(R.string.file_outside_the_working_folder) }
    val modified = source.lastModified()
    val sent = source.inputStream().use { input -> FtpUploader(settings).upload(name, { input.read(it) }, ::confirmFtpOverwrite) { sent ->
     _state.update { it.copy(progress = "FTP · ${file.originalName}: ${sizeLabel(sent)} / ${sizeLabel(file.size)}") }
    } }
    check(sent == file.size && source.length() == file.size && source.lastModified() == modified) { Words.get(R.string.the_local_file_changed_during_upload_and) }
    if (!settings.keep) app.storage.delete(listOf(file))
   }
   suspend fun attachment(listed: Attachment) = report(listed.name, remoteNames.getValue(listed.key)) {
    withAttachment(listed) { a, existing ->
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
        FtpUploader(settings).upload(remoteNames.getValue(a.key), reader::read, ::confirmFtpOverwrite) { sent ->
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
           if (available.complete && available.file != null) finishDownload(available.file, target)
          }
         } catch (e: Exception) {
          if (transferError != null) transferError.addSuppressed(e)
          else throw IllegalStateException(Words.get(R.string.ftp_upload_succeeded_but_local_cleanup_or, e.message), e)
         }
        }
        _state.update { it.copy(activeDownloads = it.activeDownloads - a.fileId) }
       }
      }
     }
    }
   }
   suspend fun sendLocal(file: Downloaded) {
    val name = remoteNames.getValue(file.path)
    report(file.originalName, name) { localFile(file, name) }
   }
   if (local) {
    if (settings.keep) ParallelDownloads.run(downloaded, ::sendLocal)
    else for (file in downloaded) sendLocal(file)
   } else {
    if (settings.keep) ParallelDownloads.run(selected, ::attachment)
    else for (a in selected) attachment(a)
   }
   notify(Words.get(R.string.ftp_files_sent, completed.get(), total) + if (settings.keep) Words.get(R.string.local_files_kept) else Words.get(R.string.local_copies_deleted_after_ftp_confirmation))
  }
 }
 private fun archiveTransfer(selected: List<Attachment>, downloaded: List<Downloaded>, local: Boolean, share: Boolean,
  settings: FtpSettings? = null, subdirectory: String = "") = launchOperation(Words.get(R.string.preparing_archives), transfer = true) {
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
   require((item.parts.isEmpty() && ArchiveNames.part(item.name) == null) || raw.map { it.name.lowercase() }.distinct().size == raw.size) { Words.get(R.string.several_archives_have_identically_named_parts_narrow) }
   val parts = ArchiveNames.members(item)
   val archive = ArchiveNames.isArchive(item.name)
   val originalStem = item.name.substringBeforeLast('.')
   val extension = item.name.substringAfterLast('.')
   // Leave room for the longest volume suffix so every part retains the same local stem.
   val nameBudget = 180 - (parts.maxOf { it.name.length } - item.name.length)
   var localName = if (archive) FileNames.safe(item.name, nameBudget) else item.name
   fun volumeName(part: Attachment, logicalName: String) =
    logicalName.substringBeforeLast('.') + part.name.drop(originalStem.length)
   fun occupied(part: Attachment, logicalName: String) = File(app.storage.root, volumeName(part, logicalName)).exists()
   val needsName = archive && parts.any { app.storage.existing(it) == null && occupied(it, localName) }
   if (needsName) {
    // Allow room for the numeric suggestion as well as the volume suffix.
    var suggestion = FileNames.unique(FileNames.safe(localName, nameBudget - 13)) { candidate -> parts.any { occupied(it, candidate) } }
    while(true) {
     val reply = CompletableDeferred<String?>()
     renameReply = reply
     _state.update { it.copy(archiveRename = suggestion) }
     val answer = try { reply.await() ?: throw CancellationException() } finally { renameReply = null; _state.update { it.copy(archiveRename = null) } }
     if (answer != FileNames.safe(answer, nameBudget) || !answer.endsWith(".$extension", true)) { notify(Words.get(R.string.keep_the_extension_and_enter_a_valid, extension)); continue }
     if (parts.any { occupied(it, answer) }) { suggestion = answer; notify(Words.get(R.string.name_already_in_use_choose_another_name)); continue }
     localName = answer
     break
    }
   }
   requireSpace(parts.filter { needsName || app.storage.existing(it) == null }.sumOf { it.size.coerceAtLeast(0) })
   val saved = ConcurrentHashMap<String, Downloaded>()
   ParallelDownloads.run(parts) { listed -> withAttachment(listed, reuse = !needsName) { part, existing ->
    if(existing != null) saved[part.key] = existing else {
     val target = if (archive) {
      val filename = volumeName(part, localName)
      app.storage.beginArchiveDownload(if (needsName) part.copy(name = filename) else part, filename)
     } else app.storage.beginDownload(part)
     _state.update { it.copy(activeDownloads = it.activeDownloads + (part.fileId to DownloadProgress(part.name, size=part.size))) }
     try { saved[part.key] = finishDownload(tg.download(part), target) }
     finally { _state.update { it.copy(activeDownloads = it.activeDownloads - part.fileId) }; refreshDownloads() }
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
      val sent = uploader.upload(entry.name, { reader.read(it) }, ::confirmFtpOverwrite, { remoteTouched += entry.name }) { bytes -> _state.update { it.copy(progress="FTP · ${entry.name}: ${sizeLabel(bytes)}") } }
      check(entry.size < 0 || sent == entry.size) { Words.get(R.string.extracted_size_mismatch, entry.name) }
      count++
     }
     check(count > 0) { Words.get(R.string.the_archive_contains_no_files_to_send) }
    }
   } else for(file in files) {
    val name = File(file.path).name
    val sent = File(file.path).inputStream().use { input -> uploader.upload(name, { input.read(it) }, ::confirmFtpOverwrite, { remoteTouched += name }) { bytes -> _state.update { it.copy(progress="FTP · $name: ${sizeLabel(bytes)}") } } }
    check(sent == file.size) { Words.get(R.string.ftp_size_mismatch, name) }
   }
   check(snapshots.all { (f,size,time) -> f.isFile && f.length()==size && f.lastModified()==time }) { Words.get(R.string.the_archive_changed_during_upload_and_was) }
   if(!settings.keep) app.storage.delete(files)
   remoteTouched.clear()
  }
  try {
   if(local) {
    val seen = mutableSetOf<String>()
    for(file in downloaded) {
     if (file.path in seen) continue
     val volumes = if(ArchiveNames.isArchive(file.originalName)) ArchiveReader.volumes(File(file.path)) else listOf(File(file.path))
     check(volumes.any { it.path == file.path }) { Words.get(R.string.file_unavailable, file.originalName) }
     seen.addAll(volumes.map { it.path })
     val files = volumes.map { f -> app.storage.list().first { it.path == f.path } }
     send(files)
    }
   } else for(item in selected) {
    val files = save(item)
    allSaved += files
    send(files)
   }
   if(share) _state.update { it.copy(shareFiles = allSaved) }
   _state.update { it.copy(tab = AppTab.DOWNLOADS.ordinal, progress = if(settings != null) Words.get(R.string.ftp_upload_complete) else Words.get(R.string.archives_and_files_saved)) }
  } catch(e: Exception) {
   if(e !is FtpOverwriteDeclined && settings != null && remoteTouched.isNotEmpty()) {
    pendingCleanup = Triple(settings, subpath, remoteTouched.distinct())
    _state.update { it.copy(remoteCleanup = remoteTouched.distinct()) }
   }
   throw e
  } finally { refreshDownloads() }
 }
 private var pendingCleanup: Triple<FtpSettings, String, List<String>>? = null
 fun remoteCleanup(confirmed: Boolean) {
  val pending = pendingCleanup ?: return
  pendingCleanup = null
  _state.update { it.copy(remoteCleanup = emptyList()) }
  if(confirmed) launchOperation(Words.get(R.string.removing_files_from_ftp), transfer = true) {
   FtpUploader(pending.first, pending.second).delete(pending.third)
   notify(Words.get(R.string.remote_files_removed_any_overwritten_files_cannot))
  }
 }
 private fun requireSpace(size: Long) {
  val free = app.storage.root.usableSpace
  check(size.coerceAtLeast(0) <= (free - 16L * 1024 * 1024).coerceAtLeast(0)) { Words.get(R.string.not_enough_space_the_working_folder_needs, sizeLabel(size)) }
 }
 private fun Set<String>.toggle(key: String) = if (key in this) this - key else this + key
}
