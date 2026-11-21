package it.tgfm

import it.tgfm.R
import it.tgfm.Words

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
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
 override fun attachBaseContext(base: android.content.Context) { super.attachBaseContext(Words.contextFor(base)) }
 private val vm: AppViewModel by viewModels()
 private val folder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
  if (uri != null) vm.chooseWorkFolder(uri)
 }
 private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onForeground() }
 private val specialAccess = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.onForeground() }
 private fun storageAccess() {
  try {
   if (android.os.Build.VERSION.SDK_INT >= 30) {
    if (!FileAccessSettings.open(packageName, specialAccess::launch)) vm.notify(Words.get(R.string.open_android_settings_apps_special_app_access))
   }
   else storagePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
  } catch (e: ActivityNotFoundException) { vm.notify(Words.get(R.string.open_android_settings_and_allow_tgfm_to)) }
 }
 private fun transfer(action: () -> Unit) {
  if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
  action()
 }
 private fun share(files: List<it.tgfm.core.Downloaded>, open: Boolean = false) {
  lifecycleScope.launch {
  try {
   val intent = withContext(Dispatchers.IO) { ShareIntents.build(this@MainActivity, files, open, vm.state.value.archiveContents) }
   startActivity(if (open) intent else Intent.createChooser(intent, Words.get(R.string.share_with)))
  } catch (e: ActivityNotFoundException) { vm.notify(Words.get(R.string.no_compatible_app_is_available)) }
  catch (e: Exception) { vm.notify(e.message ?: Words.get(R.string.file_unavailable_for_sharing)) }
 }
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
   TgfmScreen(vm, language = Words.preference(this), onLanguage = { Words.choose(this, it) }, onShare = { file -> share(file?.let { listOf(it) } ?: vm.selectedDownloads(), open = file != null) },
    onChooseFolder = {
     try { folder.launch(null) } catch (e: ActivityNotFoundException) { vm.notify(Words.get(R.string.folder_picker_unavailable)) }
    }, onStorageAccess = ::storageAccess,
    onSelectionShare = { transfer { vm.download(share = true) } },
    onFtp = { local, keep, path -> transfer { vm.uploadFtp(local, keep, path) } },
    onDownload = { retry -> transfer { vm.download(retry) } })
  }
 }
}
