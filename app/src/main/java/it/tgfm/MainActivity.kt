package it.tgfm

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import it.tgfm.data.ShareIntents
import it.tgfm.data.FileAccessSettings
import it.tgfm.ui.AppViewModel
import it.tgfm.ui.TgfmScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity: ComponentActivity() {
 private val vm: AppViewModel by viewModels()
 private val folder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
  if (uri != null) vm.chooseWorkFolder(uri)
 }
 private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onForeground() }
 private val specialAccess = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.onForeground() }
 private fun storageAccess() {
  try {
   if (android.os.Build.VERSION.SDK_INT >= 30) {
    if (!FileAccessSettings.open(packageName, specialAccess::launch)) vm.notify("Apri Impostazioni Android → Applicazioni → Accesso speciale → Accesso a tutti i file → TgFM.")
   }
   else storagePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
  } catch (e: ActivityNotFoundException) { vm.notify("Apri le impostazioni Android e concedi a TgFM l'accesso a tutti i file.") }
 }
 private fun transfer(action: () -> Unit) {
  if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
  action()
 }
 private fun share(files: List<it.tgfm.core.Downloaded>, open: Boolean = false) {
  try {
   val intent = ShareIntents.build(this, files, open)
   startActivity(if (open) intent else Intent.createChooser(intent, "Condividi con…"))
  } catch (e: ActivityNotFoundException) { vm.notify("Nessuna applicazione compatibile disponibile.") }
  catch (e: Exception) { vm.notify(e.message ?: "File non disponibile per la condivisione.") }
 }
 private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
 override fun onResume() { super.onResume(); vm.onForeground() }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  setContent {
   val state = vm.state.collectAsStateWithLifecycle().value
   androidx.compose.runtime.LaunchedEffect(state.shareFiles) {
    if (state.shareFiles.isNotEmpty()) { val files = state.shareFiles; vm.shared(); share(files) }
   }
   TgfmScreen(vm, onShare = { file -> share(file?.let { listOf(it) } ?: vm.selectedDownloads(), open = file != null) },
    onChooseFolder = {
     try { folder.launch(null) } catch (e: ActivityNotFoundException) { vm.notify("Selettore cartelle non disponibile.") }
    }, onStorageAccess = ::storageAccess,
    onSelectionShare = { transfer { vm.download(share = true) } },
    onFtp = { local, keep -> transfer { vm.uploadFtp(local, keep) } },
    onDownload = { retry -> transfer { vm.download(retry) } })
  }
 }
}
