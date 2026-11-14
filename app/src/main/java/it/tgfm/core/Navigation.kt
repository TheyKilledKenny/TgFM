package it.tgfm.core

enum class AppTab(val label: String, val symbol: String) {
 SELECTION("Selezione", "☑"), DOWNLOADS("Download", "↓"), FILTERS("Filtri", "≡"), SETTINGS("Configura", "◎")
}
object NavigationPolicy {
 fun canSelect(auth: String, connection: String, channel: Channel?): Boolean =
  auth == "authorizationStateReady" && connection in setOf("Connesso", "Aggiornamento") && channel != null
 fun requested(tab: Int, auth: String, connection: String, channel: Channel?): Int =
  if (tab == AppTab.SELECTION.ordinal && !canSelect(auth, connection, channel)) AppTab.SETTINGS.ordinal else tab
 fun prerequisitesChanged(tab: Int, auth: String, connection: String, channel: Channel?): Int =
  if (canSelect(auth, connection, channel)) tab else AppTab.SETTINGS.ordinal
}
