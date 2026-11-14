package it.tgfm

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import it.tgfm.data.ShareIntents
import it.tgfm.ui.AppViewModel
import it.tgfm.ui.TgfmScreen

class MainActivity: ComponentActivity() {
 private val vm: AppViewModel by viewModels()
 private val folder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
  if (uri != null) {
   try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
   catch (e: SecurityException) { vm.notify("La cartella non concede accesso persistente. Scegli una cartella della memoria interna."); return@registerForActivityResult }
  }
  vm.save(uri)
 }
 private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  setContent {
   TgfmScreen(vm, onShare = { file ->
    try {
     val intent = ShareIntents.build(this, file?.let { listOf(it) } ?: vm.selectedDownloads(), open = file != null)
     startActivity(if (file == null) Intent.createChooser(intent, "Condividi con…") else intent)
    } catch (e: ActivityNotFoundException) { vm.notify("Nessuna applicazione compatibile disponibile.") }
    catch (e: Exception) { vm.notify(e.message ?: "File non disponibile per la condivisione.") }
   }, onSave = {
    if (vm.prepareSave()) try { folder.launch(null) } catch (e: ActivityNotFoundException) { vm.save(null); vm.notify("Selettore cartelle non disponibile sul dispositivo.") }
   }, onDownload = { retry ->
    if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    vm.download(retry)
   })
  }
 }
}
