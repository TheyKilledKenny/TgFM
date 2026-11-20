package it.tgfm.data

import it.tgfm.core.FileNames
import kotlinx.coroutines.*
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPSClient
import org.apache.commons.net.ftp.FTPReply
import java.io.OutputStream
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

/** One binary STOR stream per file; a positive final reply is mandatory before local deletion. */
class FtpUploader(private val settings: FtpSettings, private val subdirectory: String = "") {
 suspend fun upload(name: String, read: suspend (ByteArray) -> Int, progress: (Long) -> Unit): Long = session { ftp, data ->
  require(name == it.tgfm.core.ArchiveNames.safeEntry(name)) { "Nome FTP non valido." }
  val directories = name.split('/').dropLast(1)
  var prefix = ""
  for(directory in directories) {
   prefix += directory + "/"
   // MKD may fail because the directory already exists; STOR remains authoritative.
   ftp.makeDirectory(prefix)
  }
  val output = ftp.storeFileStream(name) ?: error("Il server FTP non consente di scrivere il file (codice ${ftp.replyCode}).")
  data.set(output)
  var sent = 0L
  val lastWrite = java.util.concurrent.atomic.AtomicLong(System.nanoTime())
  val watchdog = CoroutineScope(currentCoroutineContext()).launch {
   while (isActive) {
    delay(1000)
    check((System.nanoTime() - lastWrite.get()) / 1_000_000 < 180_000) { "Trasferimento FTP fermo da tre minuti. File locale conservato." }
   }
  }
  try { output.use { out ->
   val buffer = ByteArray(256 * 1024)
   while (true) {
    currentCoroutineContext().ensureActive()
    val n = read(buffer)
    if (n < 0) break
    check(n in 1..buffer.size) { "Lettura del file non valida." }
    out.write(buffer, 0, n)
    sent += n
    lastWrite.set(System.nanoTime())
    progress(sent)
   }
  }
  } finally { watchdog.cancel() }
  data.set(null)
  currentCoroutineContext().ensureActive()
  check(ftp.completePendingCommand()) { "FTP non ha confermato il trasferimento (codice ${ftp.replyCode}). Il file locale non è stato eliminato." }
  // SIZE is optional. If supplied, reject a successful reply with an inconsistent length.
  val size = ftp.getSize(name)
  if (size != null) check(size.trim().toLongOrNull() == sent) { "La dimensione ricevuta dal server FTP non corrisponde. File locale conservato." }
  else check(ftp.replyCode in setOf(202, 500, 501, 502, 504)) { "Impossibile verificare la dimensione su FTP (codice ${ftp.replyCode}). File locale conservato." }
  sent
 }
 suspend fun delete(names: List<String>) = session { ftp, _ ->
  for(name in names) {
   require(name == it.tgfm.core.ArchiveNames.safeEntry(name))
   check(ftp.deleteFile(name)) { "Impossibile rimuovere da FTP: $name (codice ${ftp.replyCode})." }
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
    check(FTPReply.isPositiveCompletion(ftp.replyCode)) { "Connessione FTP rifiutata (codice ${ftp.replyCode})." }
    check(ftp.login(settings.user, settings.password)) { "Credenziali FTP non accettate." }
    if (ftp is FTPSClient) { ftp.execPBSZ(0); ftp.execPROT("P") }
    ftp.enterLocalPassiveMode()
    check(ftp.setFileType(FTP.BINARY_FILE_TYPE)) { "Il server non supporta il trasferimento binario." }
    check(ftp.changeWorkingDirectory(settings.directory.ifBlank { "/" })) { "Cartella FTP non accessibile: configurala prima sul server." }
    if(subdirectory.isNotEmpty()) {
     require(subdirectory == it.tgfm.core.ArchiveNames.safeEntry(subdirectory))
     for(part in subdirectory.split('/')) {
      if(!ftp.changeWorkingDirectory(part)) {
       check(ftp.makeDirectory(part) && ftp.changeWorkingDirectory(part)) { "Impossibile creare o aprire la sottocartella FTP: $part" }
      }
     }
    }
    block(ftp, data)
   } finally {
    withContext(NonCancellable) { interrupt.cancelAndJoin() }
   }
  }
 }
}
