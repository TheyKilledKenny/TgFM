package it.tgfm.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.tgfm.core.*
import it.tgfm.data.RuleJson
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TgfmScreen(vm: AppViewModel, onShare: (Downloaded?) -> Unit, onSave: () -> Unit, onDownload: (Boolean) -> Unit) {
 val s by vm.state.collectAsStateWithLifecycle()
 var editJson by rememberSaveable { mutableStateOf<String?>(null) }
 var confirmJson by rememberSaveable { mutableStateOf<String?>(null) }
 var confirmAction by rememberSaveable { mutableStateOf("") }
 var desiredKeys by rememberSaveable { mutableStateOf<List<String>>(arrayListOf()) }
 var unwantedKeys by rememberSaveable { mutableStateOf<List<String>>(arrayListOf()) }
 var exampleQuery by rememberSaveable { mutableStateOf("") }
 var onlyLabelledExamples by rememberSaveable { mutableStateOf(false) }
 var channelQuery by rememberSaveable { mutableStateOf("") }
 val visibleExamples = remember(s.attachments, exampleQuery, desiredKeys, unwantedKeys, onlyLabelledExamples) {
  ListSearch.examples(s.attachments, exampleQuery, (desiredKeys + unwantedKeys).toSet(), onlyLabelledExamples)
 }
 val visibleChannels = remember(s.channels, channelQuery) { ListSearch.channels(s.channels, channelQuery) }
 var reviewExamples by rememberSaveable { mutableStateOf(false) }
 val desired = s.attachments.filter { it.key in desiredKeys }
 val unwanted = s.attachments.filter { it.key in unwantedKeys }
 val learning = remember(desired, unwanted) { FilterEngine.learn(desired, unwanted) }
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
    NavigationBarItem(selected = s.tab == i, onClick = { vm.tab(i) }, icon = { Text(destination.symbol) }, label = { Text(destination.label) })
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
       active.fraction?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
      }
      TextButton(onClick = vm::cancel) { Text("Annulla operazione") }
     }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
     when (s.tab) {
      AppTab.SETTINGS.ordinal -> {
       item { Heading("Il tuo archivio Telegram", "Accedi con il tuo account e scegli un canale, anche privato.") }
       item { InfoCard { Text("Stato · ${s.connection}"); Text(authLabel(s.auth), fontWeight = FontWeight.SemiBold) } }
       if (s.auth in listOf("notStarted", "authorizationStateClosed", "authorizationStateWaitTdlibParameters")) {
        item { InfoCard {
         Text("Credenziali dell'app Telegram", style = MaterialTheme.typography.titleMedium)
         Text("Ottieni API ID e API hash da my.telegram.org → API development tools. Restano cifrati su questo dispositivo.", style = MaterialTheme.typography.bodySmall)
         OutlinedTextField(apiId, { apiId = it }, label = { Text("API ID") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
         OutlinedTextField(apiHash, { apiHash = it }, label = { Text("API hash") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
         Button(onClick = { vm.connect(apiId, apiHash); apiHash = "" }, enabled = !s.busy && apiId.isNotBlank() && apiHash.isNotBlank()) { Text("Configura accesso") }
         TextButton(onClick = vm::reconnect, enabled = !s.busy) { Text("Riutilizza credenziali salvate") }
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
         Text("Canale selezionato", style = MaterialTheme.typography.labelLarge)
         Text(s.channel?.title ?: "Nessun canale", style = MaterialTheme.typography.titleMedium)
         Button(onClick = vm::loadChannels, enabled = !s.busy) { Text("Aggiorna elenco canali") }
        } }
        item {
         SearchField(channelQuery, { channelQuery = it }, "Cerca canale per nome")
         Text("${visibleChannels.size}/${s.channels.size} canali", style = MaterialTheme.typography.labelMedium)
         if (s.channels.isNotEmpty() && visibleChannels.isEmpty()) Text("Nessun canale trovato. Modifica o cancella la ricerca.")
        }
        items(visibleChannels, key = { "channel:${it.id}" }) { channel ->
         OutlinedButton(onClick = { vm.chooseChannel(channel) }, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text((if (channel.id == s.channel?.id) "✓ " else "") + channel.title) }
        }
        item { TextButton(onClick = { confirmAction = "logout" }, enabled = !s.busy) { Text("Disconnetti account") } }
       }
       item { InfoCard { Text("Archivio privato", style = MaterialTheme.typography.titleMedium); Row(verticalAlignment = Alignment.CenterVertically) {
         Switch(s.clearBeforeDownload, vm::clearBeforeDownload, enabled = !s.busy)
         Text("Cancella i download precedenti", modifier = Modifier.weight(1f))
        }
        Text(if (s.clearBeforeDownload) "Prima di un nuovo download la cartella viene svuotata." else "Mantieni i file precedenti. I file con lo stesso nome vengono sovrascritti all’avvio del relativo download; in caso di errore il file precedente è perso.", style = MaterialTheme.typography.bodyMedium)
        Text("Salva sposta i file selezionati; Condividi concede soltanto accesso temporaneo.", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = vm::recover, enabled = !s.busy) { Text("Recupera trasferimento interrotto") }
       } }
      }
      AppTab.FILTERS.ordinal -> {
       item { Heading("Regole di selezione", "I criteri di una regola si combinano. Le esclusioni prevalgono; ogni modifica richiede conferma.") }
       item { Button(onClick = { editJson = RuleJson.encode(Rule()).toString() }, enabled = !s.busy) { Text("Nuova regola") } }
       if (s.rules.isEmpty()) item { Text("Nessuna regola salvata. Crea un filtro oppure leggi un canale e scegli un esempio qui sotto.") }
       items(s.rules, key = { it.id }) { rule -> InfoCard {
        Text((if (rule.exclude) "Esclusione · " else "Inclusione · ") + rule.title, style = MaterialTheme.typography.titleMedium)
        Text(ruleSummary(rule), style = MaterialTheme.typography.bodySmall)
        Text(if (rule.enabled) "Attiva" else "Disattivata", color = MaterialTheme.colorScheme.primary)
        val count = s.attachments.count { FilterEngine.classify(it, listOf(rule)) in setOf(Match.CERTAIN, Match.EXCLUDED) }
        Text("Anteprima: $count/${s.attachments.size} allegati", style = MaterialTheme.typography.labelMedium)
        Row {
         TextButton(onClick = { editJson = RuleJson.encode(rule).toString() }, enabled = !s.busy) { Text("Modifica") }
         TextButton(onClick = { confirmJson = RuleJson.encode(rule.copy(enabled = !rule.enabled)).toString(); confirmAction = "rule" }, enabled = !s.busy) { Text(if (rule.enabled) "Disattiva" else "Attiva") }
         TextButton(onClick = { confirmJson = RuleJson.encode(rule).toString(); confirmAction = "delete" }, enabled = !s.busy) { Text("Elimina") }
        }
       } }
       item { Heading("Configura da più esempi", "Segna più file desiderati e indesiderati, poi genera le regole una sola volta. Le date diventano variabili; gli indesiderati controllano le proposte senza creare una regola per variante.") }
       item { InfoCard {
        Text("${desired.size} desiderati · ${unwanted.size} indesiderati")
        Button(onClick = { reviewExamples = true }, enabled = !s.busy && (desired.isNotEmpty() || unwanted.isNotEmpty())) { Text("Proponi regole comuni") }
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
       items(visibleExamples, key = { "example:${it.key}" }) { a -> InfoCard {
        Text(a.name); Text("${a.category.label} · .${a.extension}", style = MaterialTheme.typography.bodySmall)
        Row {
         TextButton(onClick = {
          desiredKeys = ArrayList(if (a.key in desiredKeys) desiredKeys - a.key else desiredKeys + a.key)
          unwantedKeys = ArrayList(unwantedKeys - a.key)
         }, enabled = !s.busy) { Text(if (a.key in desiredKeys) "✓ Desiderato" else "Desiderato") }
         TextButton(onClick = {
          unwantedKeys = ArrayList(if (a.key in unwantedKeys) unwantedKeys - a.key else unwantedKeys + a.key)
          desiredKeys = ArrayList(desiredKeys - a.key)
         }, enabled = !s.busy) { Text(if (a.key in unwantedKeys) "✓ Indesiderato" else "Indesiderato") }
        }
       } }

      }
      AppTab.SELECTION.ordinal -> {
       item { Heading(s.channel?.title ?: "Selezione file", "Leggi i metadati, verifica le corrispondenze e scegli cosa scaricare.") }
       item { Button(onClick = vm::scan, enabled = !s.busy && s.auth == "authorizationStateReady" && s.channel != null) { Text("Leggi canale") } }
       item { Row(verticalAlignment = Alignment.CenterVertically) { Switch(s.showAll, vm::showAll); Spacer(Modifier.width(8.dp)); Text("Mostra tutti gli allegati") } }
       item { Text("${s.selected.size} selezionati su ${s.attachments.size}", fontWeight = FontWeight.Bold) }
       item { Row { TextButton(onClick = { vm.selectAll(false, true) }, enabled = !s.busy) { Text("Seleziona tutti") }; TextButton(onClick = { vm.selectAll(false, false) }, enabled = !s.busy) { Text("Deseleziona tutti") } } }
       val visible = s.attachments.filter { s.showAll || FilterEngine.classify(it, s.rules) in setOf(Match.CERTAIN, Match.POSSIBLE) }
       if (visible.isEmpty()) item { Text(if (s.attachments.isEmpty()) "Nessun allegato letto." else "Nessuna corrispondenza. Attiva Mostra tutti per la selezione manuale.") }
       items(visible, key = { it.key }) { a ->
        val match = FilterEngine.classify(a, s.rules)
        InfoCard { Row(verticalAlignment = Alignment.CenterVertically) {
         Checkbox(a.key in s.selected, { vm.toggle(a.key) }, enabled = !s.busy)
         Column(Modifier.weight(1f)) {
          Text(a.name, fontWeight = FontWeight.SemiBold)
          Text("${a.category.label} · ${a.mime.ifBlank { ".${a.extension}" }} · ${sizeLabel(a.size)}", style = MaterialTheme.typography.bodySmall)
          Text(match.label, color = when (match) { Match.CERTAIN -> MaterialTheme.colorScheme.primary; Match.EXCLUDED -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant }, style = MaterialTheme.typography.labelLarge)
          if (a.date > 0) Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(a.date * 1000)), style = MaterialTheme.typography.labelSmall)
          if (a.caption.isNotBlank()) Text(a.caption, maxLines = 3, style = MaterialTheme.typography.bodySmall)
         }
        } }
       }
       item { Button(onClick = { confirmAction = "download" }, enabled = !s.busy && s.selected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Scarica selezionati (${s.selected.size})") } }
       item { Text("La selezione manuale vale solo per questa operazione, anche per i file esclusi. Non modifica i filtri.", style = MaterialTheme.typography.bodySmall) }
      }
      AppTab.DOWNLOADS.ordinal -> {
       item { Heading("Pronti da usare", "La stessa selezione viene usata per Condividi e Salva.") }
       item { Text("${s.downloadedSelection.size}/${s.downloads.size} selezionati · ${sizeLabel(s.downloads.filter { it.path in s.downloadedSelection }.sumOf { it.size })}", fontWeight = FontWeight.Bold) }
       if (!s.busy && s.progress.isNotBlank()) item { Text(s.progress, style = MaterialTheme.typography.bodySmall) }
       item { Row { TextButton(onClick = { vm.selectAll(true, true) }, enabled = !s.busy) { Text("Seleziona tutti") }; TextButton(onClick = { vm.selectAll(true, false) }, enabled = !s.busy) { Text("Deseleziona tutti") } } }
       item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { onShare(null) }, enabled = !s.busy && s.downloadedSelection.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Condividi") }
        Button(onClick = onSave, enabled = !s.busy && s.downloadedSelection.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Salva") }
       } }
       item { Text("Salva sposta i file nella cartella scelta e li rimuove da questo elenco. Le app destinatarie della condivisione possono imporre limiti di numero, tipo e dimensione.", style = MaterialTheme.typography.bodySmall) }
       if (s.downloads.isEmpty()) item { Text("La cartella dei download è vuota.") }
       items(s.downloads, key = { it.path }) { file -> InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
         Checkbox(file.path in s.downloadedSelection, { vm.toggle(file.path, true) }, enabled = !s.busy)
         Column(Modifier.weight(1f)) { Text(java.io.File(file.path).name, fontWeight = FontWeight.SemiBold); Text("${file.mime.ifBlank { "File" }} · ${sizeLabel(file.size)}", style = MaterialTheme.typography.bodySmall) }
        }
        TextButton(onClick = { onShare(file) }, enabled = !s.busy) { Text("Apri file") }
       } }
       if (s.retry.isNotEmpty()) item { OutlinedButton(onClick = { onDownload(true) }, enabled = !s.busy) { Text("Riprova ${s.retry.size} download non completati") } }
      }
     }
     if (s.failures.isNotEmpty()) item { InfoCard { Text("Problemi da verificare", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold); s.failures.forEach { Text(it, style = MaterialTheme.typography.bodySmall) } } }
     item { Spacer(Modifier.height(12.dp)) }
    }
   }
  }
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
     val combined = s.rules + learning.rules
     val missedDesired = desired.count { FilterEngine.classify(it, combined) != Match.CERTAIN }
     val includedUnwanted = unwanted.count { FilterEngine.classify(it, combined) == Match.CERTAIN }
     if (missedDesired > 0 || includedUnwanted > 0) Text("Attenzione alle regole già salvate: $missedDesired desiderati non inclusi; $includedUnwanted indesiderati inclusi. Rivedi le vecchie regole.", color = MaterialTheme.colorScheme.error)
     s.attachments.forEach { a -> Text("${FilterEngine.classify(a, combined).label} · ${a.name}", style = MaterialTheme.typography.bodySmall) }
    }
    Text("I nomi senza una data riconoscibile richiedono un modello manuale. Le regole già presenti saranno conservate e quelle identiche non saranno duplicate.")
   }
  }, confirmButton = { TextButton(onClick = { vm.saveLearnedRules(learning.rules); reviewExamples = false }, enabled = !s.busy && learning.rules.isNotEmpty()) { Text("Conferma e salva regole") } }, dismissButton = { TextButton(onClick = { reviewExamples = false }) { Text("Torna agli esempi") } })
  editJson?.let { json -> RuleEditor(RuleJson.decode(JSONObject(json)), s.attachments, onDismiss = { editJson = null }, onConfirm = { vm.saveRule(it); editJson = null }) }
  if (confirmAction.isNotEmpty()) AlertDialog(onDismissRequest = { confirmAction = "" }, title = { Text(when(confirmAction) { "download" -> "Scaricare i file selezionati?"; "logout" -> "Disconnettere Telegram?"; "delete" -> "Eliminare la regola?"; else -> "Confermare la modifica?" }) }, text = { Text(when(confirmAction) { "download" -> if (s.clearBeforeDownload) "Hai attivato Cancella i download precedenti: la cartella sarà svuotata prima di scaricare ${s.selected.size} allegati." else "Saranno scaricati ${s.selected.size} allegati. Gli altri file restano; quelli con lo stesso nome saranno cancellati all’avvio del relativo download e saranno persi in caso di errore."; "logout" -> "La sessione Telegram sarà chiusa. I filtri e i download locali resteranno disponibili."; else -> "La modifica sarà salvata e le selezioni automatiche saranno ricalcolate." }) }, confirmButton = { TextButton(onClick = {
   when(confirmAction) { "download" -> onDownload(false); "logout" -> vm.logout(); "delete" -> confirmJson?.let { vm.deleteRule(RuleJson.decode(JSONObject(it))) }; "rule" -> confirmJson?.let { vm.saveRule(RuleJson.decode(JSONObject(it))) } }
   confirmAction = ""
  }, enabled = !s.busy) { Text("Conferma") } }, dismissButton = { TextButton(onClick = { confirmAction = "" }) { Text("Annulla") } })
 }
}

@Composable private fun Heading(title: String, subtitle: String) { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun InfoCard(content: @Composable ColumnScope.() -> Unit) { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) } }
fun sizeLabel(size: Long): String = when { size <= 0 -> "dimensione non disponibile"; size < 1024 -> "$size B"; size < 1024 * 1024 -> String.format(Locale.ITALY, "%.1f KB", size / 1024.0); else -> String.format(Locale.ITALY, "%.1f MB", size / (1024.0 * 1024)) }
private fun authLabel(state: String) = when(state) { "authorizationStateReady" -> "Account collegato"; "authorizationStateWaitPhoneNumber" -> "Inserisci il numero di telefono"; "authorizationStateWaitCode" -> "In attesa del codice"; "authorizationStateWaitPassword" -> "Verifica in due passaggi"; "notStarted", "authorizationStateClosed" -> "Accesso da configurare"; else -> "Autenticazione in corso" }
private fun ruleSummary(r: Rule) = listOfNotNull(r.template.takeIf { it.isNotBlank() }, r.contains.takeIf { it.isNotBlank() }?.let { "Contiene: $it" }, r.extensions.takeIf { it.isNotEmpty() }?.joinToString(", "), r.mime.takeIf { it.isNotBlank() }, r.category?.label).joinToString(" · ")

@Composable private fun RuleEditor(rule: Rule, examples: List<Attachment>, onDismiss: () -> Unit, onConfirm: (Rule) -> Unit) {
 var title by rememberSaveable(rule.id) { mutableStateOf(rule.title) }
 var template by rememberSaveable(rule.id) { mutableStateOf(FilterEditing.nameOnly(rule.template)) }
 var contains by rememberSaveable(rule.id) { mutableStateOf(rule.contains) }
 var extensions by rememberSaveable(rule.id) { mutableStateOf(rule.extensions.joinToString(", ")) }
 var mime by rememberSaveable(rule.id) { mutableStateOf(rule.mime) }
 var exclude by rememberSaveable(rule.id) { mutableStateOf(rule.exclude) }
 var category by rememberSaveable(rule.id) { mutableStateOf(rule.category?.name ?: "") }
 val draft = rule.copy(title = title.trim(), template = template.trim(), contains = contains.trim(), extensions = extensions.split(',').map { it.trim().lowercase(Locale.ROOT).removePrefix(".") }.filter { it.isNotEmpty() }.toSet(), mime = mime.trim().lowercase(Locale.ROOT), exclude = exclude, category = Category.entries.firstOrNull { it.name == category })
 val error = FilterEngine.validate(draft)
 AlertDialog(onDismissRequest = onDismiss, title = { Text("Modifica e conferma regola") }, text = {
  Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
   Text("Categoria Telegram (indipendente dall’estensione)", style = MaterialTheme.typography.labelLarge)
   Column { (listOf("" to "Qualsiasi") + Category.entries.map { it.name to it.label }).forEach { (value, label) ->
    Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(category == value, { category = value }); Text(label) }
   } }
   val count = examples.count { FilterEngine.classify(it, listOf(draft)) in setOf(Match.CERTAIN, Match.EXCLUDED) }
   Text("Anteprima: $count/${examples.size} allegati. I criteri compilati devono essere tutti soddisfatti.")
   error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
  }
 }, confirmButton = { TextButton(onClick = { onConfirm(draft) }, enabled = error == null) { Text("Conferma e salva") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } })
}

@Composable private fun SearchField(value: String, onValueChange: (String) -> Unit, label: String) {
 OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) },
  modifier = Modifier.fillMaxWidth(), singleLine = true,
  trailingIcon = { if (value.isNotEmpty()) TextButton(onClick = { onValueChange("") }) { Text("Svuota") } })
}
