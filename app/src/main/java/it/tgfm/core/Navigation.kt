package it.tgfm.core

import it.tgfm.R
import it.tgfm.Words

enum class AppTab(private val labelId: Int, val symbol: String) {
 SELECTION(R.string.selection, "☑"), DOWNLOADS(R.string.downloads, "↓"), FILTERS(R.string.filters, "≡"), SETTINGS(R.string.settings, "◎");
 val label: String get() = Words.get(labelId)
}
object NavigationPolicy {
 // Transport reconnects after backgrounding must not gate access to cached selection.
 fun canSelect(auth: String, channel: Channel?): Boolean = auth == "authorizationStateReady" && channel != null
 fun requested(tab: Int, auth: String, channel: Channel?): Int =
  if (tab == AppTab.SELECTION.ordinal && !canSelect(auth, channel)) AppTab.SETTINGS.ordinal else tab
 fun prerequisitesChanged(tab: Int, auth: String, channel: Channel?): Int =
  if (canSelect(auth, channel)) tab else AppTab.SETTINGS.ordinal
}
