package it.tgfm.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Credentials(val apiId: Int, val apiHash: String, val databaseKey: String)
class CredentialVault(context: Context) {
 private val prefs = context.getSharedPreferences("vault", Context.MODE_PRIVATE)
 private val alias = "tgfm.credentials.v1"
 private fun key(): SecretKey {
  val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  (store.getKey(alias, null) as? SecretKey)?.let { return it }
  return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
   init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
  }.generateKey()
 }
 fun loadFtp(): FtpSettings {
  val encrypted = prefs.getString("ftp", null) ?: return FtpSettings()
  val parts = encrypted.split(":")
  val cipher = Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
  val j = JSONObject(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8))
  return FtpSettings(j.getString("host"), j.getInt("port"), j.getString("user"), j.getString("password"), j.getString("directory"), j.getBoolean("tls"), j.getBoolean("keep"))
 }
 fun saveFtp(value: FtpSettings) {
  value.validate()
  val json = JSONObject().put("host", value.host).put("port", value.port).put("user", value.user).put("password", value.password).put("directory", value.directory).put("tls", value.tls).put("keep", value.keep).toString()
  val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
  val encrypted = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(json.toByteArray()), Base64.NO_WRAP)
  check(prefs.edit().putString("ftp", encrypted).commit()) { "Impossibile salvare la configurazione FTP." }
 }
 fun load(): Credentials? {
  val encrypted = prefs.getString("encrypted", null) ?: return null
  val parts = encrypted.split(":")
  val cipher = Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
  val json = JSONObject(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8))
  return Credentials(json.getInt("id"), json.getString("hash"), json.getString("key"))
 }
 fun save(apiId: Int, apiHash: String): Credentials {
  require(apiId > 0 && Regex("[a-fA-F0-9]{32}").matches(apiHash)) { "API ID o API hash non validi." }
  val databaseKey = load()?.databaseKey ?: Base64.encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) }, Base64.NO_WRAP)
  val json = JSONObject().put("id", apiId).put("hash", apiHash).put("key", databaseKey).toString()
  val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
  val encrypted = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(json.toByteArray()), Base64.NO_WRAP)
  check(prefs.edit().putString("encrypted", encrypted).commit()) { "Impossibile salvare le credenziali." }
  return Credentials(apiId, apiHash, databaseKey)
 }
}
