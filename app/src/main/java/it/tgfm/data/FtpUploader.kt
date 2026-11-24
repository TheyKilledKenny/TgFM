package it.tgfm.data

import it.tgfm.R
import it.tgfm.Words

import it.tgfm.core.ArchiveNames
import kotlinx.coroutines.*
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFileFilters
import org.apache.commons.net.ftp.FTPSClient
import org.apache.commons.net.ftp.FTPReply
import java.io.OutputStream
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

/** One binary STOR stream per file; a positive final reply is mandatory before local deletion. */
class FtpUploader(private val settings: FtpSettings, private val subdirectory: String = "") {
 suspend fun upload(name: String, read: suspend (ByteArray) -> Int, confirmOverwrite: suspend (String) -> Unit,
  onStarted: () -> Unit = {}, progress: (Long) -> Unit): Long = session { ftp, data ->
  require(name == ArchiveNames.safeEntry(name)) { Words.get(R.string.invalid_ftp_filename) }
  enterDirectories(ftp, name.substringBeforeLast('/', ""))
  val filename = name.substringAfterLast('/')
  if (remoteExists(ftp, filename)) {
   val base = settings.directory.ifBlank { "/" }.trimEnd('/')
   val relative = listOf(subdirectory, name).filter { it.isNotEmpty() }.joinToString("/")
   confirmOverwrite("$base/$relative")
  }
  currentCoroutineContext().ensureActive()
  val output = ftp.storeFileStream(filename) ?: error(Words.get(R.string.the_ftp_server_refused_to_write_the, ftp.replyCode))
  data.set(output)
  var sent = 0L
  val lastWrite = java.util.concurrent.atomic.AtomicLong(System.nanoTime())
  val watchdog = CoroutineScope(currentCoroutineContext()).launch {
   while (isActive) {
    delay(1000)
    check((System.nanoTime() - lastWrite.get()) / 1_000_000 < 180_000) { Words.get(R.string.ftp_transfer_stalled_for_three_minutes_local) }
   }
  }
  try { output.use { out ->
   onStarted()
   val buffer = ByteArray(256 * 1024)
   while (true) {
    currentCoroutineContext().ensureActive()
    val n = read(buffer)
    if (n < 0) break
    check(n in 1..buffer.size) { Words.get(R.string.invalid_file_read) }
    out.write(buffer, 0, n)
    sent += n
    lastWrite.set(System.nanoTime())
    progress(sent)
   }
  }
  } finally { watchdog.cancel() }
  data.set(null)
  currentCoroutineContext().ensureActive()
  check(ftp.completePendingCommand()) { Words.get(R.string.ftp_did_not_confirm_the_transfer_code, ftp.replyCode) }
  // SIZE is optional. If supplied, reject a successful reply with an inconsistent length.
  val size = ftp.getSize(filename)
  if (size != null) check(size.trim().toLongOrNull() == sent) { Words.get(R.string.ftp_file_size_mismatch_local_file_kept) }
  else check(ftp.replyCode in setOf(202, 500, 501, 502, 504)) { Words.get(R.string.could_not_verify_file_size_on_ftp, ftp.replyCode) }
  sent
 }
 private fun remoteExists(ftp: FTPClient, filename: String): Boolean {
  if (ftp.getSize(filename) != null) return true
  if (ftp.sendCommand("MLST", filename) == 250) return true
  // A negative file query (including 550) can also mean permission denied or unsupported.
  val nlst = ftp.listNames()
  val names = if (nlst != null && FTPReply.isPositiveCompletion(ftp.replyCode)) nlst.asList() else {
   // NLST can return 550 for an empty directory. Keep unparseable LIST entries visible.
   val entries = ftp.listFiles(null, FTPFileFilters.ALL)
   val complete = ftp.replyCode == FTPReply.CLOSING_DATA_CONNECTION || ftp.replyCode == FTPReply.FILE_ACTION_OK
   check(complete && entries.all { it?.isValid == true && !it.name.isNullOrEmpty() }) {
    Words.get(R.string.ftp_existence_unknown, filename, ftp.replyCode)
   }
   entries.map { it.name }
  }
  if (names.any { it.substringAfterLast('/').equals(filename, ignoreCase = true) }) return true
  // Some servers omit dotfiles even when the listing succeeds.
  check(!filename.startsWith('.')) { Words.get(R.string.ftp_existence_unknown, filename, ftp.replyCode) }
  return false
 }
 private fun enterDirectories(ftp: FTPClient, path: String) {
  if (path.isEmpty()) return
  require(path == ArchiveNames.safeEntry(path))
  for (part in path.split('/')) {
   if (!ftp.changeWorkingDirectory(part)) {
    ftp.makeDirectory(part)
    check(ftp.changeWorkingDirectory(part)) { Words.get(R.string.could_not_create_or_open_ftp_subfolder, part) }
   }
  }
 }
 suspend fun delete(names: List<String>) = session { ftp, _ ->
  for(name in names) {
   require(name == it.tgfm.core.ArchiveNames.safeEntry(name))
   check(ftp.deleteFile(name)) { Words.get(R.string.could_not_delete_from_ftp_code, name, ftp.replyCode) }
  }
 }
 suspend fun test() = session { _, _ -> Unit }
 private suspend fun <T> session(block: suspend (FTPClient, AtomicReference<OutputStream?>) -> T): T = withContext(Dispatchers.IO) {
  settings.validate()
  coroutineScope {
   val ftp = if (settings.tls) FTPSClient(false).apply {
    isEndpointCheckingEnabled = true
    val factory = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm())
    factory.init(null as java.security.KeyStore?)
    trustManager = factory.trustManagers.filterIsInstance<javax.net.ssl.X509TrustManager>().first()
   } else FTPClient()
   val data = AtomicReference<OutputStream?>()
   ftp.controlEncoding = "UTF-8"
   ftp.connectTimeout = 20_000
   ftp.defaultTimeout = 180_000
   ftp.setDataTimeout(Duration.ofMinutes(3))
   ftp.isUseEPSVwithIPv4 = true
   // Closing sockets interrupts blocked I/O when Annulla is pressed, including data writes.
   val interrupt = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
    try { awaitCancellation() } finally {
     runCatching { data.getAndSet(null)?.close() }
     runCatching { ftp.disconnect() }
    }
   }
   try {
    ftp.connect(settings.host, settings.port)
    check(FTPReply.isPositiveCompletion(ftp.replyCode)) { Words.get(R.string.ftp_connection_refused_code, ftp.replyCode) }
    check(ftp.login(settings.user, settings.password)) { Words.get(R.string.ftp_credentials_rejected) }
    if (ftp is FTPSClient) { ftp.execPBSZ(0); ftp.execPROT("P") }
    ftp.enterLocalPassiveMode()
    check(ftp.setFileType(FTP.BINARY_FILE_TYPE)) { Words.get(R.string.the_server_does_not_support_binary_transfers) }
    check(ftp.changeWorkingDirectory(settings.directory.ifBlank { "/" })) { Words.get(R.string.ftp_folder_inaccessible_set_it_up_on) }
    enterDirectories(ftp, subdirectory)
    block(ftp, data)
   } finally {
    withContext(NonCancellable) { interrupt.cancelAndJoin() }
   }
  }
 }
}
