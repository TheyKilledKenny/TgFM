package it.tgfm
import android.app.Application
import it.tgfm.data.*
import it.tgfm.telegram.TelegramClient
class TgfmApp: Application() {
 val telegram by lazy { TelegramClient(this) }
 val preferences by lazy { Preferences(this) }
 val vault by lazy { CredentialVault(this) }
 val storage by lazy { Storage(this) }
}
