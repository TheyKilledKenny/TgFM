package it.tgfm.data

data class FtpSettings(val host: String = "", val port: Int = 21, val user: String = "", val password: String = "", val directory: String = "/", val tls: Boolean = false, val keep: Boolean = false) {
 fun validate() {
  require(host.isNotBlank() && !host.contains("://") && host.none { it.isWhitespace() || it == '/' }) { "Inserisci l'host FTP, senza ftp:// o percorsi." }
  require(port in 1..65535) { "Porta FTP non valida." }
  require(user.isNotBlank()) { "Inserisci l'utente FTP (anche anonymous, se previsto dal server)." }
  require(listOf(host, user, password, directory).none { value -> value.any { it == '\r' || it == '\n' || it == '\u0000' } }) { "La configurazione FTP contiene caratteri non validi." }
 }
}
