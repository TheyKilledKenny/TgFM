package it.tgfm.data

import it.tgfm.R
import it.tgfm.Words

data class FtpSettings(val host: String = "", val port: Int = 21, val user: String = "", val password: String = "", val directory: String = "/", val tls: Boolean = false, val keep: Boolean = false) {
 fun validate() {
  require(host.isNotBlank() && !host.contains("://") && host.none { it.isWhitespace() || it == '/' }) { Words.get(R.string.enter_the_ftp_hostname_without_ftp_or) }
  require(port in 1..65535) { Words.get(R.string.invalid_ftp_port) }
  require(user.isNotBlank()) { Words.get(R.string.enter_the_ftp_username_or_anonymous_if) }
  require(listOf(host, user, password, directory).none { value -> value.any { it == '\r' || it == '\n' || it == '\u0000' } }) { Words.get(R.string.ftp_settings_contain_invalid_characters) }
 }
}
