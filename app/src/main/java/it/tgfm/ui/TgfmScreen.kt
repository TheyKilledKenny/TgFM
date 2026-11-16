package it.tgfm.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.tgfm.core.*
import it.tgfm.data.RuleJson
import it.tgfm.data.FtpSettings
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TgfmScreen(vm: AppViewModel, onShare: (Downloaded?) -> Unit, onChooseFolder: () -> Unit, onStorageAccess: () -> Unit, onSelectionShare: () -> Unit, onFtp: (Boolean) -> Unit, onDownload: (Boolean) -> Unit) {
 val s by vm.state.collectAsStateWithLifecycle()
 val listState = remember(s.tab, if (s.tab == AppTab.SELECTION.ordinal) s.channel?.id else null) { LazyListState() }
 var permissionPromptHandled by rememberSaveable { mutableStateOf(false) }
 var selectedChannelMenu by remember { mutableStateOf(false) }
 var selectionQuery by rememberSaveable(s.channel?.id) { mutableStateOf("") }
 var removeFavoriteId by rememberSaveable { mutableStateOf<Long?>(null) }
 var viewMenuExpanded by remember { mutableStateOf(false) }
 val selectionFiles = remember(s.attachments, s.selected, s.activeRules, s.attachmentView, selectionQuery) {
  AttachmentViews.visible(s.attachments, s.selected, s.activeRules, s.attachmentView).filter { ListSearch.matches(it.name, selectionQuery) }
 }
 var editJson by rememberSaveable { mutableStateOf<String?>(null) }
 var confirmJson by rememberSaveable { mutableStateOf<String?>(null) }
 var deleteFiles by remember { mutableStateOf<List<Downloaded>?>(null) }
 var editFtp by remember { mutableStateOf(false) }
 var confirmAction by rememberSaveable { mutableStateOf("") }
 var desiredKeys by rememberSaveable { mutableStateOf<List<String>>(arrayListOf()) }
 var unwantedKeys by rememberSaveable { mutableStateOf<List<String>>(arrayListOf()) }
 var exampleQuery by rememberSaveable { mutableStateOf("") }
 var onlyLabelledExamples by rememberSaveable { mutableStateOf(false) }
 var channelQuery by rememberSaveable { mutableStateOf("") }
 val visibleExamples = remember(s.attachments, exampleQuery, desiredKeys, unwantedKeys, onlyLabelledExamples) {
  ListSearch.examples(s.attachments, exampleQuery, (desiredKeys + unwantedKeys).toSet(), onlyLabelledExamples)
 }
 val visibleChannels = remember(s.channels, s.favorites, channelQuery) { ListSearch.channels((s.channels + s.favorites).distinctBy { it.id }, channelQuery) }
 var reviewExamples by rememberSaveable { mutableStateOf(false) }
 val desired = s.attachments.filter { it.key in desiredKeys }
 val unwanted = s.attachments.filter { it.key in unwantedKeys }
 val learning = remember(desired, unwanted, s.channel?.id) { FilterEngine.learn(desired, unwanted).let { result -> result.copy(rules = result.rules.map { it.copy(channelId = s.channel?.id) }) } }
 var apiId by rememberSaveable { mutableStateOf("") }
 var apiHash by remember { mutableStateOf("") }
 var authInput by remember(s.auth) { mutableStateOf("") }
 val snackbar = remember { SnackbarHostState() }
 LaunchedEffect(s.message) { if (s.message.isNotEmpty()) { snackbar.showSnackbar(s.message); vm.dismissMessage() } }
 MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF006C67), secondary = Color(0xFF486462), background = Color(0xFFF5F8F7), surface = Color(0xFFF5F8F7))) {
  Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
   TopAppBar(title = { Column { Text("TgFM", fontWeight = FontWeight.Bold); Text("Telegram File Manager", style = MaterialTheme.typography.labelMedium) } })
  }, bottomBar = {
   NavigationBar { AppTab.entries.forEachIndexed { i, destination ->
    NavigationBarItem(selected = s.tab == i, onClick = { vm.tab(i) }, icon = { Icon(when(destination) { AppTab.SELECTION -> UiIcons.Selection; AppTab.DOWNLOADS -> UiIcons.Download; AppTab.FILTERS -> UiIcons.Filters; AppTab.SETTINGS -> UiIcons.Settings }, contentDescription = null) }, label = { Text(destination.label) })
   } }
  }) { padding ->
   Column(Modifier.padding(padding).fillMaxSize()) {
    if (s.busy) {
     Column(Modifier.padding(horizontal = 16.dp)) {
      Text(s.progress, style = MaterialTheme.typography.bodySmall)
      s.fraction?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) } ?: LinearProgressIndicator(Modifier.fillMaxWidth())
      s.activeDownloads.values.forEach { active ->
       Text(active.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
       Text("${sizeLabel(active.bytes)} / ${sizeLabel(active.size)}", style = MaterialTheme.typography.labelSmall)
       active.uploaded?.let { Text("Inviati a FTP: ${sizeLabel(it)}", style = MaterialTheme.typography.labelSmall) }
       active.fraction?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
      }
      TextButton(onClick = vm::cancel) { Text("Annulla operazione") }
     }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("main-list"), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
     when (s.tab) {
      AppTab.SETTINGS.ordinal -> {
       item { Heading("Il tuo archivio Telegram", "Accedi con il tuo account e scegli un canale, anche privato.") }
       item { InfoCard {
        Text("Stato · ${s.connection}")
        Text(authLabel(s.auth), fontWeight = FontWeight.SemiBold)
        if (s.loginError.isNotBlank()) Text(s.loginError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
       } }
       item { InfoCard {
        Text("Cartella di lavoro", style = MaterialTheme.typography.titleMedium)
        Text(s.workPath, style = MaterialTheme.typography.bodySmall)
        if (!s.fileAccess) {
         Text("Telegram è in attesa dell’accesso alla cartella esterna. Su Android 11 o successivo: Impostazioni → Applicazioni → Accesso speciale → Accesso a tutti i file → TgFM. Questo accesso non compare nei permessi ordinari.", style = MaterialTheme.typography.bodySmall)
         Button(onClick = onStorageAccess, enabled = !s.busy) { Text("Concedi accesso ai file") }
        }
        OutlinedButton(onClick = onChooseFolder, enabled = !s.busy && s.fileAccess) { Text("Scegli cartella") }
        Text("I nuovi file e la cache saranno salvati qui. Cambiare cartella non sposta né cancella i file della precedente.", style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
         Switch(s.clearBeforeDownload, vm::clearBeforeDownload, enabled = !s.busy)
         Text("Cancella i download precedenti", modifier = Modifier.weight(1f))
        }
        Text(if (s.clearBeforeDownload) "Salva e Condividi eliminano prima i file gestiti da TgFM nella cartella attuale. FTP non svuota la cartella." else "I file già salvati per lo stesso allegato sono riutilizzati. Gli altri omonimi vengono sovrascritti all'avvio del download; in caso di errore la copia precedente è persa.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { confirmAction = "cache" }, enabled = !s.busy && s.fileAccess) { Text("Svuota cache temporanea") }
       } }
       item { InfoCard {
        Text("FTP", style = MaterialTheme.typography.titleMedium)
        Text(if (s.ftp.host.isBlank()) "Nessun server configurato" else "${if (s.ftp.tls) "FTPS" else "FTP"} · ${s.ftp.host}:${s.ftp.port}${s.ftp.directory}", style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
         Checkbox(s.ftp.keep, { vm.saveFtp(s.ftp.copy(keep = it)) }, enabled = !s.busy && s.ftp.host.isNotBlank())
         Text("Conserva al termine")
        }
        Text(if (s.ftp.keep) "I file restano nella cartella di lavoro. Fino a 4 trasferimenti contemporanei." else "Un file alla volta. Dopo la conferma FTP viene eliminata la copia locale, anche se era già presente in Download. In caso di errore resta disponibile o nella cache.", style = MaterialTheme.typography.bodySmall)
        Row { OutlinedButton(onClick = { editFtp = true }, enabled = !s.busy) { Text("Configura FTP") }
         TextButton(onClick = vm::testFtp, enabled = !s.busy && s.ftp.host.isNotBlank()) { Text("Verifica accesso") } }
       } }
       if (s.auth in listOf("notStarted", "authorizationStateClosed") || (s.auth == "authorizationStateWaitTdlibParameters" && s.loginError.isNotBlank())) {
        item { InfoCard {
         Text("Credenziali dell'app Telegram", style = MaterialTheme.typography.titleMedium)
         Text("Ottieni API ID e API hash da my.telegram.org → API development tools. Configura accesso avvia un nuovo login, poi inserisci telefono, codice ed eventuale password 2FA.", style = MaterialTheme.typography.bodySmall)
         OutlinedTextField(apiId, { apiId = it }, label = { Text("API ID") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
         OutlinedTextField(apiHash, { apiHash = it }, label = { Text("API hash") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
         Button(onClick = { vm.connect(apiId, apiHash); apiHash = "" }, enabled = !s.busy && s.fileAccess && apiId.isNotBlank() && apiHash.isNotBlank()) { Text("Configura accesso") }

        } }
       }
       if (s.authInfo.isNotEmpty()) item { Text(s.authInfo) }
       if (s.auth !in listOf("notStarted", "authorizationStateClosed", "authorizationStateReady", "authorizationStateClosing", "authorizationStateLoggingOut")) {
        item { TextButton(onClick = vm::reconfigure, enabled = !s.busy) { Text("Ricomincia / modifica credenziali API") } }
       }
       if (s.auth in listOf("authorizationStateWaitPhoneNumber", "authorizationStateWaitCode", "authorizationStateWaitPassword", "authorizationStateWaitEmailAddress", "authorizationStateWaitEmailCode")) {
        item { InfoCard {
         val password = s.auth == "authorizationStateWaitPassword"
         OutlinedTextField(authInput, { authInput = it }, label = { Text(when(s.auth) { "authorizationStateWaitPhoneNumber" -> "Telefono · +39…"; "authorizationStateWaitPassword" -> "Password 2FA"; "authorizationStateWaitEmailAddress" -> "E-mail"; else -> "Codice" }) },
          visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
          keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else if (s.auth == "authorizationStateWaitPhoneNumber") KeyboardType.Phone else KeyboardType.Text), singleLine = true, modifier = Modifier.fillMaxWidth())
         Button(onClick = { vm.authenticate(authInput); authInput = "" }, enabled = !s.busy && authInput.isNotBlank()) { Text("Continua") }
         if (s.auth == "authorizationStateWaitCode") TextButton(onClick = vm::resendCode, enabled = !s.busy) { Text("Richiedi un altro codice") }
        } }
       }
       if (s.auth == "authorizationStateReady") {
        item { InfoCard {
         Text("Canale attivo", style = MaterialTheme.typography.labelMedium)
         Text(s.channel?.title ?: "Nessun canale", style = MaterialTheme.typography.titleMedium)
         Button(onClick = vm::loadChannels, enabled = !s.busy) { Text("Aggiorna elenco canali e gruppi") }
        } }
        item {
         SearchField(channelQuery, { channelQuery = it }, "Cerca canale o gruppo per nome")
         Text("${s.favorites.size} preferiti · ${visibleChannels.size} canali e gruppi visibili", style = MaterialTheme.typography.labelMedium)
         Text("Spunta i preferiti. Puoi cambiare canale toccandone il nome in Selezione.", style = MaterialTheme.typography.bodySmall)
         if (visibleChannels.isEmpty()) Text("Nessun canale trovato. Modifica o cancella la ricerca.")
        }
        items(visibleChannels, key = { "channel:${it.id}" }) { channel ->
         val favorite = s.favorites.any { it.id == channel.id }
         OutlinedCard(Modifier.fillMaxWidth()) {
          Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
           Checkbox(favorite, { checked ->
            if (checked) vm.addFavorite(channel)
            else if (s.rules.any { it.channelId == channel.id }) removeFavoriteId = channel.id
            else vm.removeFavorite(channel)
           }, enabled = !s.busy, modifier = Modifier.testTag("favorite:${channel.id}"))
           TextButton(onClick = { if (favorite) vm.chooseChannel(channel) else vm.addFavorite(channel) }, enabled = !s.busy, modifier = Modifier.weight(1f)) {
            Text(channel.title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
           }
           if (s.channel?.id == channel.id) Text("Attivo", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
          }
         }
        }
        item { TextButton(onClick = { confirmAction = "logout" }, enabled = !s.busy) { Text("Disconnetti account") } }
       }

      }
      AppTab.FILTERS.ordinal -> {
       item { Heading("Regole di selezione", "I criteri di una regola si combinano. Le esclusioni prevalgono; ogni modifica richiede conferma.") }
       item { Button(onClick = { editJson = RuleJson.encode(Rule(channelId = s.channel?.id ?: s.favorites.firstOrNull()?.id)).toString() }, enabled = !s.busy && s.favorites.isNotEmpty()) { Text("Nuova regola") } }
       if (s.rules.isEmpty()) item { Text("Nessuna regola salvata. Crea un filtro oppure leggi un canale e scegli un esempio qui sotto.") }
       if (s.favorites.isEmpty()) item { Text("Aggiungi almeno un canale preferito in Configura per assegnare i filtri.") }
       items(s.rules, key = { it.id }) { rule ->
        OutlinedCard(Modifier.fillMaxWidth().testTag("rule:${rule.id}")) {
         Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
           Text(rule.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
           Text((s.favorites.firstOrNull { it.id == rule.channelId }?.title ?: "Da assegnare") + " · " + (if (rule.exclude) "Escludi" else "Includi"), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
           Text(ruleSummary(rule), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          IconButton(onClick = { editJson = RuleJson.encode(rule).toString() }, enabled = !s.busy, modifier = Modifier.size(48.dp)) { Icon(UiIcons.Edit, "Modifica ${rule.title}", modifier = Modifier.size(20.dp)) }
          IconButton(onClick = { confirmJson = RuleJson.encode(rule.copy(enabled = !rule.enabled)).toString(); confirmAction = "rule" }, enabled = !s.busy && rule.channelId != null, modifier = Modifier.size(48.dp)) {
           Icon(if (rule.enabled) UiIcons.Pause else UiIcons.Play, if (rule.enabled) "Disattiva ${rule.title}" else "Attiva ${rule.title}", modifier = Modifier.size(20.dp), tint = if (rule.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
          }
          IconButton(onClick = { confirmJson = RuleJson.encode(rule).toString(); confirmAction = "delete" }, enabled = !s.busy, modifier = Modifier.size(48.dp)) { Icon(UiIcons.Delete, "Elimina ${rule.title}", modifier = Modifier.size(20.dp)) }
         }
        }
       }
       item { Heading("Configura da esempi", "Segna più file desiderati e indesiderati, poi genera le regole una sola volta. Le date diventano variabili; gli indesiderati controllano le proposte senza creare una regola per variante.") }
       item { InfoCard {
        Text("${desired.size} desiderati · ${unwanted.size} indesiderati")
        Button(onClick = { reviewExamples = true }, enabled = !s.busy && s.channel != null && (desired.isNotEmpty() || unwanted.isNotEmpty())) { Text("Proponi regole comuni") }
        TextButton(onClick = { desiredKeys = arrayListOf(); unwantedKeys = arrayListOf() }, enabled = !s.busy) { Text("Azzera esempi") }
       } }
       item {
        SearchField(exampleQuery, { exampleQuery = it }, "Cerca esempi per nome file")
        Row(verticalAlignment = Alignment.CenterVertically) {
         Checkbox(onlyLabelledExamples, { onlyLabelledExamples = it })
         Text("Solo esempi già contrassegnati")
        }
        Text("${visibleExamples.size}/${s.attachments.size} file visibili · ${desired.size + unwanted.size} esempi contrassegnati in totale", style = MaterialTheme.typography.labelMedium)
        if (visibleExamples.isEmpty()) Text(if (s.attachments.isEmpty()) "Leggi prima un canale in Selezione." else "Nessun file trovato. Modifica la ricerca o disattiva Solo esempi già contrassegnati.")
       }
       items(visibleExamples, key = { "example:${it.key}" }) { a ->
        OutlinedCard(Modifier.fillMaxWidth()) {
         Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) { Text(a.name, maxLines = 2, overflow = TextOverflow.Ellipsis); Text("${a.category.label} · .${a.extension}", style = MaterialTheme.typography.labelSmall) }
          IconToggleButton(a.key in desiredKeys, {
           desiredKeys = ArrayList(if (a.key in desiredKeys) desiredKeys - a.key else desiredKeys + a.key)
           unwantedKeys = ArrayList(unwantedKeys - a.key)
          }, enabled = !s.busy) { Icon(UiIcons.Check, if (a.key in desiredKeys) "Rimuovi esempio desiderato" else "Desiderato", tint = if (a.key in desiredKeys) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
          IconToggleButton(a.key in unwantedKeys, {
           unwantedKeys = ArrayList(if (a.key in unwantedKeys) unwantedKeys - a.key else unwantedKeys + a.key)
           desiredKeys = ArrayList(desiredKeys - a.key)
          }, enabled = !s.busy) { Icon(UiIcons.Close, if (a.key in unwantedKeys) "Rimuovi esempio indesiderato" else "Indesiderato", tint = if (a.key in unwantedKeys) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
         }
        }
       }

      }
      AppTab.SELECTION.ordinal -> {
       item {
        Box {
         TextButton(onClick = { selectedChannelMenu = true }, enabled = !s.busy && s.favorites.isNotEmpty(), contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) {
          Text(s.channel?.title ?: "Scegli un preferito", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
          Icon(UiIcons.Down, "Cambia canale preferito")
         }
         DropdownMenu(expanded = selectedChannelMenu, onDismissRequest = { selectedChannelMenu = false }) {
          s.favorites.forEach { favorite -> DropdownMenuItem(text = { Text(favorite.title) }, onClick = { vm.chooseChannel(favorite); selectedChannelMenu = false }) }
         }
        }
        if (s.connection !in setOf("Connesso", "Aggiornamento")) Text("${s.connection} · puoi consultare gli allegati già letti.", style = MaterialTheme.typography.labelSmall)
       }
       item { SearchField(selectionQuery, { selectionQuery = it }, "Cerca per nome file") }
       item { PostPeriodSelector(s.postPeriod, vm::postPeriod, enabled = !s.busy) }
       item { Button(onClick = vm::scan, enabled = !s.busy && s.auth == "authorizationStateReady" && s.channel != null && s.postPeriod.error() == null) { Text("Leggi canale") } }
       if (s.scannedPeriod.isNotBlank()) item {
        Text("${if (s.scanComplete) "Lettura completata" else "Lettura parziale"} · ${s.scannedPeriod}", style = MaterialTheme.typography.labelSmall)
       }
       item {
        ExposedDropdownMenuBox(expanded = viewMenuExpanded, onExpandedChange = { viewMenuExpanded = it }) {
         OutlinedTextField(value = s.attachmentView.label, onValueChange = {}, readOnly = true,
          label = { Text("Visualizza") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = viewMenuExpanded) },
          modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth())
         ExposedDropdownMenu(expanded = viewMenuExpanded, onDismissRequest = { viewMenuExpanded = false }) {
          AttachmentView.entries.forEach { view ->
           DropdownMenuItem(text = { Text(view.label) }, onClick = { vm.attachmentView(view); viewMenuExpanded = false })
          }
         }
        }
       }
       item { Text("${s.selected.size} selezionati su ${s.attachments.size}", fontWeight = FontWeight.Bold) }
       item { Row { TextButton(onClick = { vm.selectAll(false, true) }, enabled = !s.busy) { Text("Seleziona tutti") }; TextButton(onClick = { vm.selectAll(false, false) }, enabled = !s.busy) { Text("Deseleziona tutti") } } }
       if (selectionFiles.isEmpty()) item { Text(when {
        s.attachments.isEmpty() -> "Nessun allegato letto."
        selectionQuery.isNotBlank() -> "Nessun file trovato nella vista corrente. Modifica la ricerca o scegli Mostra tutti i file."
        s.attachmentView == AttachmentView.SELECTED -> "Nessun file selezionato. Scegli un'altra vista per modificare la selezione."
        else -> "Nessun file selezionato o affine. Scegli Mostra tutti i file per la selezione manuale."
       }) }
       items(selectionFiles, key = { it.key }) { a ->
        val match = FilterEngine.classify(a, s.activeRules)
        OutlinedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
         Checkbox(a.key in s.selected, { vm.toggle(a.key) }, enabled = !s.busy)
         Column(Modifier.weight(1f)) {
          Text(a.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
          Text("${a.category.label} · .${a.extension} · ${sizeLabel(a.size)}", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(match.label, color = when (match) { Match.CERTAIN -> MaterialTheme.colorScheme.primary; Match.EXCLUDED -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant }, style = MaterialTheme.typography.labelMedium)
          if (a.date > 0) Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(a.date * 1000)), style = MaterialTheme.typography.labelSmall)
          if (a.caption.isNotBlank()) Text(a.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
         }
        } }
       }
       item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { confirmAction = "download" }, enabled = !s.busy && s.selected.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Salva") }
        Button(onClick = { confirmAction = "share" }, enabled = !s.busy && s.selected.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Condividi") }
       } }
       item { OutlinedButton(onClick = { confirmAction = "ftp-selection" }, enabled = !s.busy && s.selected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Salva su FTP") } }
       item { Text("La selezione manuale vale solo per questa operazione, anche per i file esclusi. Non modifica i filtri.", style = MaterialTheme.typography.bodySmall) }
      }
      AppTab.DOWNLOADS.ordinal -> {
       item { Heading("Pronti da usare", "File nella cartella di lavoro. Seleziona quelli da condividere o inviare a FTP.") }
       item { TextButton(onClick = vm::refreshLocalFiles, enabled = !s.busy) { Text("Aggiorna elenco file") } }
       item { Text("${s.downloadedSelection.size}/${s.downloads.size} selezionati · ${sizeLabel(s.downloads.filter { it.path in s.downloadedSelection }.sumOf { it.size })}", fontWeight = FontWeight.Bold) }
       if (!s.busy && s.progress.isNotBlank()) item { Text(s.progress, style = MaterialTheme.typography.bodySmall) }
       item { Row { TextButton(onClick = { vm.selectAll(true, true) }, enabled = !s.busy) { Text("Seleziona tutti") }; TextButton(onClick = { vm.selectAll(true, false) }, enabled = !s.busy) { Text("Deseleziona tutti") } } }
       item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { onShare(null) }, enabled = !s.busy && s.downloadedSelection.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Condividi") }
        OutlinedButton(onClick = { confirmAction = "ftp-downloads" }, enabled = !s.busy && s.downloadedSelection.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Salva su FTP") }
        IconButton(onClick = { deleteFiles = vm.selectedDownloads() }, enabled = !s.busy && s.downloadedSelection.isNotEmpty()) { Icon(UiIcons.Delete, "Elimina selezionati", tint = MaterialTheme.colorScheme.error) }
       } }
       item { Text("Condividi concede accesso temporaneo ai file. Le app destinatarie possono imporre limiti di numero, tipo e dimensione.", style = MaterialTheme.typography.bodySmall) }
       if (s.downloads.isEmpty()) item { Text("La cartella dei download è vuota.") }
       items(s.downloads, key = { it.path }) { file ->
        OutlinedCard(Modifier.fillMaxWidth().testTag("download:${file.path}")) {
         Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
          Checkbox(file.path in s.downloadedSelection, { vm.toggle(file.path, true) }, enabled = !s.busy)
          Column(Modifier.weight(1f)) {
           Text(java.io.File(file.path).name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
           Text("${java.io.File(file.path).extension.uppercase().ifBlank { file.mime }} · ${sizeLabel(file.size)}", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          IconButton(onClick = { onShare(file) }, enabled = !s.busy) { Icon(UiIcons.Eye, "Apri ${file.originalName}") }
          IconButton(onClick = { deleteFiles = listOf(file) }, enabled = !s.busy) { Icon(UiIcons.Delete, "Elimina ${file.originalName}", tint = MaterialTheme.colorScheme.error) }
         }
        }
       }
       if (s.retry.isNotEmpty()) item { OutlinedButton(onClick = { onDownload(true) }, enabled = !s.busy) { Text("Riprova ${s.retry.size} download non completati") } }
      }
     }
     if (s.failures.isNotEmpty()) item { InfoCard { Text("Problemi da verificare", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold); s.failures.forEach { Text(it, style = MaterialTheme.typography.bodySmall) } } }
     item { Spacer(Modifier.height(12.dp)) }
    }
   }
  }
  if (s.ready && !s.busy && !s.fileAccess && !permissionPromptHandled) AlertDialog(
   onDismissRequest = { permissionPromptHandled = true }, title = { Text("Accesso ai file necessario") },
   text = { Text(if (android.os.Build.VERSION.SDK_INT >= 30) "Per usare la cartella esterna, apri l'accesso speciale Android e abilita TgFM in Accesso a tutti i file. Poi torna nell'app per configurare Telegram." else "Consenti ad Android l'accesso ai file, poi torna nell'app per configurare Telegram.") },
   confirmButton = { TextButton(onClick = { permissionPromptHandled = true; onStorageAccess() }) { Text("Concedi accesso") } },
   dismissButton = { TextButton(onClick = { permissionPromptHandled = true }) { Text("Non ora") } })
  if (reviewExamples) AlertDialog(onDismissRequest = { reviewExamples = false }, title = { Text("Regole comuni proposte") }, text = {
   Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("${desired.size + unwanted.size} esempi → ${learning.rules.size} regole. Nessun salvataggio automatico.")
    learning.rules.forEach { rule ->
     Text((if (rule.exclude) "Escludi: " else "Includi: ") + rule.template, fontWeight = FontWeight.Bold)
     Text("${s.attachments.count { FilterEngine.classify(it, listOf(rule)) in setOf(Match.CERTAIN, Match.EXCLUDED) }} allegati nel canale soddisfano questa regola.")
    }
    learning.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
    if (learning.rules.isNotEmpty()) {
     Text("Anteprima dopo il salvataggio (comprese le regole già presenti)", fontWeight = FontWeight.Bold)
     val combined = s.activeRules + learning.rules
     val missedDesired = desired.count { FilterEngine.classify(it, combined) != Match.CERTAIN }
     val includedUnwanted = unwanted.count { FilterEngine.classify(it, combined) == Match.CERTAIN }
     if (missedDesired > 0 || includedUnwanted > 0) Text("Attenzione alle regole già salvate: $missedDesired desiderati non inclusi; $includedUnwanted indesiderati inclusi. Rivedi le vecchie regole.", color = MaterialTheme.colorScheme.error)
     val previewFiles = AttachmentViews.preview(s.attachments, combined)
     Text("${previewFiles.size} file verrebbero selezionati")
     if (previewFiles.isEmpty()) Text("Nessun file verrebbe selezionato con queste regole.")
     previewFiles.forEach { a -> Text(a.name, style = MaterialTheme.typography.bodySmall) }
    }
    Text("I nomi senza una data riconoscibile richiedono un modello manuale. Le regole già presenti saranno conservate e quelle identiche non saranno duplicate.")
   }
  }, confirmButton = { TextButton(onClick = { vm.saveLearnedRules(learning.rules); reviewExamples = false }, enabled = !s.busy && learning.rules.isNotEmpty()) { Text("Conferma e salva regole") } }, dismissButton = { TextButton(onClick = { reviewExamples = false }) { Text("Torna agli esempi") } })
  editJson?.let { json -> RuleEditor(RuleJson.decode(JSONObject(json)), s.favorites, s.channel?.id, s.attachments, onDismiss = { editJson = null }, onConfirm = { vm.saveRule(it); editJson = null }) }
  removeFavoriteId?.let { id ->
   val favorite = s.favorites.firstOrNull { it.id == id }
   val count = s.rules.count { it.channelId == id }
   if (favorite != null) AlertDialog(onDismissRequest = { removeFavoriteId = null }, title = { Text("Rimuovere dai preferiti?") },
    text = { Text("Rimuovendo ${favorite.title} saranno eliminati anche i suoi $count filtri. Gli altri canali e filtri resteranno invariati.") },
    confirmButton = { TextButton(onClick = { removeFavoriteId = null; vm.removeFavorite(favorite) }, enabled = !s.busy) { Text("Rimuovi") } },
    dismissButton = { TextButton(onClick = { removeFavoriteId = null }) { Text("Annulla") } })
  }
  if (editFtp) FtpEditor(s.ftp, onDismiss = { editFtp = false }, onConfirm = { vm.saveFtp(it); editFtp = false })
  deleteFiles?.let { files -> AlertDialog(onDismissRequest = { deleteFiles = null }, title = { Text("Eliminare ${files.size} file locali?") },
   text = { Text("Saranno eliminati dalla cartella di lavoro. Le copie su Telegram o FTP non vengono modificate.") },
   confirmButton = { TextButton(onClick = { vm.deleteDownloads(files); deleteFiles = null }, enabled = !s.busy) { Text("Elimina", color = MaterialTheme.colorScheme.error) } },
   dismissButton = { TextButton(onClick = { deleteFiles = null }) { Text("Annulla") } }) }
  if (confirmAction.isNotEmpty()) AlertDialog(onDismissRequest = { confirmAction = "" }, title = { Text(when(confirmAction) {
   "download" -> "Salvare i file selezionati?"; "share" -> "Salvare e condividere i file?"; "cache" -> "Svuotare la cache?"
   "ftp-selection", "ftp-downloads" -> "Inviare i file a FTP?"; "logout" -> "Disconnettere Telegram?"; "delete" -> "Eliminare la regola?"; else -> "Confermare la modifica?"
  }) }, text = { Text(when(confirmAction) {
   "download", "share" -> (if (s.clearBeforeDownload) "I precedenti file gestiti da TgFM saranno eliminati prima del salvataggio." else "I file già disponibili saranno riutilizzati. Un omonimo sostituito sarà perso anche se il nuovo download fallisce.") + if (confirmAction == "share") " Dopo il salvataggio si aprirà la condivisione Android." else ""
   "ftp-selection", "ftp-downloads" -> "I file omonimi sul server vengono sovrascritti; un'interruzione può lasciare un file remoto incompleto. " + if (s.ftp.keep) "I file locali saranno conservati." else "Dopo la conferma FTP le copie locali saranno eliminate. In caso di errore non saranno eliminate automaticamente."
   "cache" -> "Elimina i file temporanei TDLib, compresi quelli rimasti da trasferimenti interrotti. I file salvati in Download restano."
   "logout" -> "La sessione Telegram sarà chiusa. I filtri e i download locali resteranno disponibili."
   else -> "La modifica sarà salvata e le selezioni automatiche saranno ricalcolate."
  }) }, confirmButton = { TextButton(onClick = {
   when(confirmAction) { "download" -> onDownload(false); "share" -> onSelectionShare(); "ftp-selection" -> onFtp(false); "ftp-downloads" -> onFtp(true); "cache" -> vm.clearCache(); "logout" -> vm.logout(); "delete" -> confirmJson?.let { vm.deleteRule(RuleJson.decode(JSONObject(it))) }; "rule" -> confirmJson?.let { vm.saveRule(RuleJson.decode(JSONObject(it))) } }
   confirmAction = ""
  }, enabled = !s.busy) { Text("Conferma") } }, dismissButton = { TextButton(onClick = { confirmAction = "" }) { Text("Annulla") } })
 }
}

@Composable private fun Heading(title: String, subtitle: String) { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun InfoCard(content: @Composable ColumnScope.() -> Unit) { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content) } }
fun sizeLabel(size: Long): String = when { size <= 0 -> "dimensione non disponibile"; size < 1024 -> "$size B"; size < 1024 * 1024 -> String.format(Locale.ITALY, "%.1f KB", size / 1024.0); else -> String.format(Locale.ITALY, "%.1f MB", size / (1024.0 * 1024)) }
private fun authLabel(state: String) = when(state) { "authorizationStateReady" -> "Account collegato"; "authorizationStateWaitPhoneNumber" -> "Inserisci il numero di telefono"; "authorizationStateWaitCode" -> "In attesa del codice"; "authorizationStateWaitPassword" -> "Verifica in due passaggi"; "notStarted", "authorizationStateClosed" -> "Accesso da configurare"; else -> "Autenticazione in corso" }
private fun ruleSummary(r: Rule) = listOfNotNull(r.template.takeIf { it.isNotBlank() }, r.contains.takeIf { it.isNotBlank() }?.let { "Contiene: $it" }, r.extensions.takeIf { it.isNotEmpty() }?.joinToString(", "), r.mime.takeIf { it.isNotBlank() }, r.category?.label).joinToString(" · ")

@Composable private fun RuleEditor(rule: Rule, favorites: List<Channel>, activeChannelId: Long?, examples: List<Attachment>, onDismiss: () -> Unit, onConfirm: (Rule) -> Unit) {
 var title by rememberSaveable(rule.id) { mutableStateOf(rule.title) }
 var template by rememberSaveable(rule.id) { mutableStateOf(FilterEditing.nameOnly(rule.template)) }
 var contains by rememberSaveable(rule.id) { mutableStateOf(rule.contains) }
 var extensions by rememberSaveable(rule.id) { mutableStateOf(rule.extensions.joinToString(", ")) }
 var mime by rememberSaveable(rule.id) { mutableStateOf(rule.mime) }
 var exclude by rememberSaveable(rule.id) { mutableStateOf(rule.exclude) }
 var channelId by rememberSaveable(rule.id) { mutableStateOf(rule.channelId ?: activeChannelId ?: favorites.firstOrNull()?.id) }
 var channelMenu by remember { mutableStateOf(false) }
 var category by rememberSaveable(rule.id) { mutableStateOf(rule.category?.name ?: "") }
 val draft = rule.copy(title = title.trim(), template = template.trim(), contains = contains.trim(), extensions = extensions.split(',').map { it.trim().lowercase(Locale.ROOT).removePrefix(".") }.filter { it.isNotEmpty() }.toSet(), mime = mime.trim().lowercase(Locale.ROOT), exclude = exclude, category = Category.entries.firstOrNull { it.name == category }, channelId = channelId)
 val error = FilterEngine.validate(draft) ?: if (favorites.none { it.id == channelId }) "Scegli un canale preferito per il filtro." else null
 AlertDialog(onDismissRequest = onDismiss, title = { Text("Modifica e conferma regola") }, text = {
  Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
   Box {
    OutlinedButton(onClick = { channelMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(favorites.firstOrNull { it.id == channelId }?.title ?: "Assegna a un preferito"); Icon(UiIcons.Down, null) }
    DropdownMenu(expanded = channelMenu, onDismissRequest = { channelMenu = false }) {
     favorites.forEach { favorite -> DropdownMenuItem(text = { Text(favorite.title) }, onClick = { channelId = favorite.id; channelMenu = false }) }
    }
   }
   OutlinedTextField(title, { title = it }, label = { Text("Nome della regola") }, singleLine = true)
   Row(verticalAlignment = Alignment.CenterVertically) { Switch(exclude, { exclude = it }); Text(if (exclude) "Esclusione esplicita" else "Inclusione") }
   OutlinedTextField(if (template.contains("{data}")) template.substringBefore("{data}") else "", { prefix ->
    template = FilterEditing.withDateRoot(template, prefix)
   }, label = { Text("Radice esatta prima della data") }, placeholder = { Text("La_Gente_") }, supportingText = { Text("Includi il separatore finale, per esempio La_Gente_. Il nome deve iniziare proprio così.") })
   OutlinedTextField(template, { template = it }, label = { Text("Modello del nome senza estensione") }, placeholder = { Text("pino_binio_{data}") })
   Text("La_Gente_{data} accetta il nome esatto con qualunque estensione. Limita il formato solo con i criteri di tipo qui sotto. {data}: 14Nov, 14_Novembre_2026, 2026-11-14. Nessun segmento aggiuntivo ammesso.", style = MaterialTheme.typography.labelSmall)
   OutlinedTextField(contains, { contains = it }, label = { Text("Testo contenuto nel nome") })
   OutlinedTextField(extensions, { extensions = it }, label = { Text("Estensioni (vuoto = tutte)") }, placeholder = { Text("pdf, zip, epub") })
   OutlinedTextField(mime, { mime = it }, label = { Text("MIME (opzionale)") }, placeholder = { Text("application/pdf") })
   Text("Categoria Telegram (indipendente dall’estensione)", style = MaterialTheme.typography.labelMedium)
   Column { (listOf("" to "Qualsiasi") + Category.entries.map { it.name to it.label }).forEach { (value, label) ->
    Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(category == value, { category = value }); Text(label) }
   } }
   val channelExamples = examples.filter { it.chatId == channelId }
   val count = channelExamples.count { FilterEngine.classify(it, listOf(draft)) in setOf(Match.CERTAIN, Match.EXCLUDED) }
   Text("Anteprima: $count/${channelExamples.size} allegati del canale. I criteri compilati devono essere tutti soddisfatti.")
   error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
  }
 }, confirmButton = { TextButton(onClick = { onConfirm(draft) }, enabled = error == null) { Text("Conferma e salva") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } })
}

@Composable private fun SearchField(value: String, onValueChange: (String) -> Unit, label: String) {
 OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, leadingIcon = { Icon(UiIcons.Search, null) },
  modifier = Modifier.fillMaxWidth(), singleLine = true,
  trailingIcon = { if (value.isNotEmpty()) TextButton(onClick = { onValueChange("") }) { Text("Svuota") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun PostPeriodSelector(value: PostPeriod, onChange: (PostPeriod) -> Unit, enabled: Boolean) {
 var expanded by remember { mutableStateOf(false) }
 var picker by rememberSaveable { mutableStateOf<String?>(null) }
 Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
  ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
   OutlinedTextField(value.mode.label, {}, readOnly = true, enabled = enabled,
    label = { Text("Post pubblicati") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = enabled).fillMaxWidth().testTag("post-period"))
   ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
    PostPeriodMode.entries.forEach { mode -> DropdownMenuItem(text = { Text(mode.label) }, onClick = { onChange(value.copy(mode = mode)); expanded = false }) }
   }
  }
  if (value.mode == PostPeriodMode.DAY || value.mode == PostPeriodMode.RANGE) {
   val format = DateTimeFormatter.ofPattern("dd/MM/yyyy")
   Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedButton(onClick = { picker = "first" }, enabled = enabled, modifier = Modifier.weight(1f).testTag("post-first-date")) {
     Text("${if (value.mode == PostPeriodMode.DAY) "Giorno" else "Dal"} ${value.first.format(format)}")
    }
    if (value.mode == PostPeriodMode.RANGE) OutlinedButton(onClick = { picker = "last" }, enabled = enabled, modifier = Modifier.weight(1f).testTag("post-last-date")) {
     Text("Al ${value.last.format(format)}")
    }
   }
  }
  Text("Data del post Telegram, nel fuso orario del telefono. Scegli il periodo prima di Leggi canale.", style = MaterialTheme.typography.labelSmall)
  value.error()?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
 }
 picker?.let { target -> key(target) {
  val day = if (target == "first") value.first else value.last
  val dateState = rememberDatePickerState(initialSelectedDateMillis = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), yearRange = 1970..2037)
  DatePickerDialog(onDismissRequest = { picker = null }, confirmButton = {
   TextButton(onClick = {
    dateState.selectedDateMillis?.let { millis ->
     val selected = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
     onChange(if (target == "first") value.copy(first = selected) else value.copy(last = selected))
    }
    picker = null
   }, enabled = dateState.selectedDateMillis != null) { Text("Conferma data") }
  }, dismissButton = { TextButton(onClick = { picker = null }) { Text("Annulla") } }) { DatePicker(dateState) }
 } }
}

@Composable private fun FtpEditor(settings: FtpSettings, onDismiss: () -> Unit, onConfirm: (FtpSettings) -> Unit) {
 var host by remember { mutableStateOf(settings.host) }
 var port by remember { mutableStateOf(settings.port.toString()) }
 var user by remember { mutableStateOf(settings.user) }
 var password by remember { mutableStateOf(settings.password) }
 var directory by remember { mutableStateOf(settings.directory) }
 var tls by remember { mutableStateOf(settings.tls) }
 var keep by remember { mutableStateOf(settings.keep) }
 val draft = FtpSettings(host.trim(), port.toIntOrNull() ?: 0, user.trim(), password, directory.trim(), tls, keep)
 val error = runCatching { draft.validate() }.exceptionOrNull()?.message
 AlertDialog(onDismissRequest = onDismiss, title = { Text("Configura FTP") }, text = {
  Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
   OutlinedTextField(host, { host = it }, label = { Text("Host") }, singleLine = true)
   OutlinedTextField(port, { port = it }, label = { Text("Porta") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
   OutlinedTextField(user, { user = it }, label = { Text("Utente") }, singleLine = true)
   OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
   OutlinedTextField(directory, { directory = it }, label = { Text("Cartella sul server") }, singleLine = true)
   Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(tls, { tls = it }); Text("FTPS esplicito (TLS)") }
   Text(if (tls) "Connessione cifrata; il certificato del server deve essere valido." else "FTP invia credenziali e file senza cifratura. Usa FTPS se il server lo supporta.", style = MaterialTheme.typography.bodySmall)
   Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(keep, { keep = it }); Text("Conserva al termine") }
   Text("Modalità passiva. La cartella remota deve già esistere. L'invio può iniziare durante il download; serve comunque spazio per il file completo.", style = MaterialTheme.typography.bodySmall)
   error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
  }
 }, confirmButton = { TextButton(onClick = { onConfirm(draft) }, enabled = error == null) { Text("Salva configurazione") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } })
}
