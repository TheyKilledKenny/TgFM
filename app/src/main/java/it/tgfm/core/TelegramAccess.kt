package it.tgfm.core

enum class SavedTelegramAccess { CHECKING, AVAILABLE, MISSING, UNREADABLE }

/** A client waiting for file permission is not evidence of a missing Telegram login. */
object TelegramAccess {
 fun canResume(auth: String, fileAccess: Boolean, saved: SavedTelegramAccess) = fileAccess && saved == SavedTelegramAccess.AVAILABLE &&
  auth in setOf("notStarted", "authorizationStateClosed", "authorizationStateWaitTdlibParameters")
 fun showCredentialForm(auth: String, saved: SavedTelegramAccess, editing: Boolean) =
  auth in setOf("notStarted", "authorizationStateClosed", "authorizationStateWaitTdlibParameters") &&
   (saved == SavedTelegramAccess.MISSING || (saved == SavedTelegramAccess.AVAILABLE && editing))
 fun label(auth: String, saved: SavedTelegramAccess, fileAccess: Boolean): String? {
  if (auth !in setOf("notStarted", "authorizationStateClosed", "authorizationStateWaitTdlibParameters")) return null
  return when (saved) {
   SavedTelegramAccess.CHECKING -> "Verifica dell'accesso salvato…"
   SavedTelegramAccess.AVAILABLE -> if (fileAccess) "Credenziali Telegram salvate" else "Accesso Telegram in attesa del permesso ai file"
   SavedTelegramAccess.MISSING -> "Accesso Telegram da configurare"
   SavedTelegramAccess.UNREADABLE -> "Credenziali Telegram presenti ma non leggibili"
  }
 }
}
