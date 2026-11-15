package it.tgfm.core

enum class AppTab(val label: String, val symbol: String) {
 SELECTION("Selezione", "☑"), DOWNLOADS("Download", "↓"), FILTERS("Filtri", "≡"), SETTINGS("Configura", "◎")
}
object NavigationPolicy {
 // Transport reconnects after backgrounding must not gate access to cached selection.
 fun canSelect(auth: String, channel: Channel?): Boolean = auth == "authorizationStateReady" && channel != null
 fun requested(tab: Int, auth: String, channel: Channel?): Int =
  if (tab == AppTab.SELECTION.ordinal && !canSelect(auth, channel)) AppTab.SETTINGS.ordinal else tab
 fun prerequisitesChanged(tab: Int, auth: String, channel: Channel?): Int =
  if (canSelect(auth, channel)) tab else AppTab.SETTINGS.ordinal
}
