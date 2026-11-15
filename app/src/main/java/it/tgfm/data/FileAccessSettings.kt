package it.tgfm.data

import android.content.Intent
import android.net.Uri
import android.provider.Settings

object FileAccessSettings {
 /** Some Android variants expose only the general list of special file-access grants. */
 fun open(packageName: String, launch: (Intent) -> Unit): Boolean {
  val intents = listOf(
   Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")),
   Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
  )
  for (intent in intents) {
   try { launch(intent); return true }
   catch (_: android.content.ActivityNotFoundException) { }
   catch (_: SecurityException) { }
  }
  return false
 }
}
