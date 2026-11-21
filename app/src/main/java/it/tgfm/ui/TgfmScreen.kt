package it.tgfm.ui

import it.tgfm.R
import it.tgfm.Words

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
@Composable fun TgfmScreen(vm: AppViewModel, language: String, onLanguage: (String) -> Unit, onShare: (Downloaded?) -> Unit, onChooseFolder: () -> Unit, onStorageAccess: () -> Unit, onSelectionShare: () -> Unit, onFtp: (Boolean, Boolean, String) -> Unit, onDownload: (Boolean) -> Unit) {
 val s by vm.state.collectAsStateWithLifecycle()
 var collapsedRuleGroups by rememberSaveable { mutableStateOf(listOf<String>()) }
 var settingsPage by rememberSaveable { mutableStateOf("") }
 var examplesPage by rememberSaveable { mutableStateOf(false) }
 var progressDetails by rememberSaveable { mutableStateOf(false) }
 var attachmentDetails by remember { mutableStateOf<Attachment?>(null) }
 var ftpPath by rememberSaveable { mutableStateOf("") }
 var archiveContents by rememberSaveable { mutableStateOf(false) }
 var ftpKeep by rememberSaveable { mutableStateOf(false) }
 val listState = remember(s.tab, settingsPage, examplesPage, if (s.tab == AppTab.SELECTION.ordinal) s.channel?.id else null) { LazyListState() }
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
 LaunchedEffect(confirmAction) { if(confirmAction.isNotEmpty()) archiveContents = s.archiveDefault }

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
 val back: () -> Unit = {
  when { editJson != null -> editJson = null; editFtp -> editFtp = false; reviewExamples -> reviewExamples = false; examplesPage -> examplesPage = false; else -> settingsPage = "" }
 }
 val hasBack = editJson != null || editFtp || (s.tab == AppTab.FILTERS.ordinal && (examplesPage || reviewExamples)) || (s.tab == AppTab.SETTINGS.ordinal && settingsPage.isNotEmpty())
 BackHandler(enabled = hasBack, onBack = back)
 val pageTitle = when {
  editJson != null -> Words.get(R.string.edit_rule)
  editFtp -> Words.get(R.string.ftp_server)
  s.tab == AppTab.FILTERS.ordinal && reviewExamples -> Words.get(R.string.confirm_rules)
  s.tab == AppTab.FILTERS.ordinal && examplesPage -> Words.get(R.string.configure_from_examples)
  s.tab == AppTab.SETTINGS.ordinal -> when(settingsPage) { "account" -> "Telegram"; "favorites" -> Words.get(R.string.favorite_channels); "storage" -> Words.get(R.string.files_and_folder); else -> Words.get(R.string.settings) }
  else -> AppTab.entries[s.tab].label
 }
 val openFtp: (Boolean) -> Unit = { local -> ftpPath = ""; ftpKeep = s.ftp.keep; confirmAction = if (local) "ftp-downloads" else "ftp-selection" }
 MaterialTheme(typography = Typography(
  titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
  titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
  bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
  bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
  bodySmall = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
  labelLarge = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
  labelMedium = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
  labelSmall = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
 ), colorScheme = lightColorScheme(primary = Color(0xFF006C67), secondary = Color(0xFF486462), background = Color(0xFFF5F8F7), surface = Color(0xFFF5F8F7))) {
  Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
   TopAppBar(title = { Column { Text("TgFM", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(pageTitle, style = MaterialTheme.typography.titleLarge) } },
    navigationIcon = { if (hasBack) IconButton(onClick = back) { Icon(UiIcons.Back, Words.get(R.string.back)) } })
  }, bottomBar = {
   Column {
    if (editJson == null && !editFtp) when (s.tab) {
     AppTab.SELECTION.ordinal -> CommandBar(Words.get(R.string.selected, s.selected.size, sizeLabel(s.attachments.filter { it.key in s.selected }.sumOf { it.size }))) {
      CommandButton(Words.get(R.string.save), !s.busy && s.selected.isNotEmpty(), Modifier.weight(.8f)) { confirmAction = "download" }
      CommandButton(Words.get(R.string.share), !s.busy && s.selected.isNotEmpty(), Modifier.weight(1f)) { confirmAction = "share" }
      CommandButton(Words.get(R.string.save_to_ftp), !s.busy && s.selected.isNotEmpty(), Modifier.weight(1.25f)) { openFtp(false) }
     }
     AppTab.DOWNLOADS.ordinal -> CommandBar(Words.get(R.string.selected, s.downloadedSelection.size, sizeLabel(s.downloads.filter { it.path in s.downloadedSelection }.sumOf { it.size }))) {
      CommandButton(Words.get(R.string.share), !s.busy && s.downloadedSelection.isNotEmpty(), Modifier.weight(1f)) { confirmAction = "share-local" }
      CommandButton(Words.get(R.string.save_to_ftp), !s.busy && s.downloadedSelection.isNotEmpty(), Modifier.weight(1.15f)) { openFtp(true) }
      IconButton(onClick = { deleteFiles = vm.selectedDownloads() }, enabled = !s.busy && s.downloadedSelection.isNotEmpty()) { Icon(UiIcons.Delete, Words.get(R.string.delete_selected), tint = if (!s.busy && s.downloadedSelection.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)) }
     }
     AppTab.FILTERS.ordinal -> if (reviewExamples) CommandBar {
      CommandButton(Words.get(R.string.confirm_and_save), !s.busy && learning.rules.isNotEmpty(), Modifier.weight(1f)) { vm.saveLearnedRules(learning.rules); reviewExamples = false; examplesPage = false }
      TextButton(onClick = { reviewExamples = false }, modifier = Modifier.weight(1f)) { Text(Words.get(R.string.back_to_examples)) }
     } else if (examplesPage) CommandBar(Words.get(R.string.wanted_unwanted, desired.size, unwanted.size)) {
      CommandButton(Words.get(R.string.suggest_common_rules), !s.busy && s.channel != null && (desired.isNotEmpty() || unwanted.isNotEmpty()), Modifier.weight(1f)) { reviewExamples = true }
      TextButton(onClick = { desiredKeys = arrayListOf(); unwantedKeys = arrayListOf() }, enabled = !s.busy) { Text(Words.get(R.string.reset)) }
     }
    }
    NavigationBar { AppTab.entries.forEachIndexed { i, destination ->
     NavigationBarItem(selected = s.tab == i, onClick = { editJson = null; editFtp = false; reviewExamples = false; examplesPage = false; settingsPage = ""; vm.tab(i) }, icon = { Icon(when(destination) { AppTab.SELECTION -> UiIcons.Selection; AppTab.DOWNLOADS -> UiIcons.Download; AppTab.FILTERS -> UiIcons.Filters; AppTab.SETTINGS -> UiIcons.Settings }, contentDescription = null, modifier = Modifier.size(22.dp)) }, label = { Text(destination.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) })
    } }
   }
  }) { padding ->
   Column(Modifier.padding(padding).fillMaxSize()) {
    if (s.busy) {
     Column(Modifier.padding(horizontal = 16.dp)) {
      Text(s.progress, style = MaterialTheme.typography.bodySmall)
      s.fraction?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) } ?: LinearProgressIndicator(Modifier.fillMaxWidth())
      if (progressDetails) s.activeDownloads.values.forEach { active ->
       Text(active.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
       Text("${sizeLabel(active.bytes)} / ${sizeLabel(active.size)}", style = MaterialTheme.typography.labelSmall)
       active.uploaded?.let { Text(Words.get(R.string.sent_to_ftp, sizeLabel(it)), style = MaterialTheme.typography.labelSmall) }
       active.fraction?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
       if (s.activeDownloads.isNotEmpty()) TextButton(onClick = { progressDetails = !progressDetails }) { Text(if (progressDetails) Words.get(R.string.hide_details) else Words.get(R.string.details, s.activeDownloads.size)) }
       TextButton(onClick = vm::cancel) { Text(Words.get(R.string.cancel_operation)) }
      }
     }
    }
    if (editJson != null) {
     RuleEditor(RuleJson.decode(JSONObject(editJson!!)), s.favorites, s.channel?.id, s.attachments, onDismiss = { editJson = null }, onConfirm = { vm.saveRule(it); editJson = null })
    } else if (editFtp) {
     FtpEditor(s.ftp, onDismiss = { editFtp = false }, onConfirm = { vm.saveFtp(it); editFtp = false }, onTest = vm::testFtp, enabled = !s.busy)
    } else LazyColumn(Modifier.fillMaxSize().testTag("main-list"), state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
     when (s.tab) {
      AppTab.SETTINGS.ordinal -> {
       when(settingsPage) {
        "" -> {
         if (!s.fileAccess) item { InfoCard {
          Text(Words.get(R.string.allow_file_access_to_get_started), style = MaterialTheme.typography.titleMedium)
          Text(Words.get(R.string.tgfm_needs_access_to_save_attachments_in))
          Button(onClick = onStorageAccess, enabled = !s.busy) { Text(Words.get(R.string.grant_file_access)) }
         } }
         item {
          val options = listOf("" to Words.get(R.string.system_language), "en" to "English", "it" to "Italiano")
          var expanded by remember { mutableStateOf(false) }
          Box {
           SettingsLink(Words.get(R.string.app_language), options.firstOrNull { it.first == language }?.second ?: options.first().second, UiIcons.Settings) { expanded = true }
           DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (tag, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { expanded = false; onLanguage(tag) }) }
           }
          }
         }
         item { SettingsLink("Telegram", if (s.loginError.isNotBlank()) s.loginError else authLabel(s.auth), UiIcons.Selection) { settingsPage = "account" } }
         item { SettingsLink(Words.get(R.string.favorite_channels), Words.get(R.string.favorites, s.favorites.size, s.channel?.title ?: Words.get(R.string.no_active_channel)), UiIcons.Filters) { settingsPage = "favorites" } }
         item { SettingsLink(Words.get(R.string.files_and_folder), s.workPath + if (s.clearBeforeDownload) Words.get(R.string.delete_previous_files) else Words.get(R.string.keep_previous_files), UiIcons.Download) { settingsPage = "storage" } }
         item { SettingsLink(Words.get(R.string.ftp_server), if (s.ftp.host.isBlank()) Words.get(R.string.not_configured) else Words.get(R.string.keep_files, s.ftp.host, if (s.ftp.keep) Words.get(R.string.on) else Words.get(R.string.off)), UiIcons.Settings) { editFtp = true } }
        }
        "storage" -> {
       item { InfoCard {
        Text(Words.get(R.string.working_folder), style = MaterialTheme.typography.titleMedium)
        Text(s.workPath, style = MaterialTheme.typography.bodySmall)
        if (!s.fileAccess) {
         Text(Words.get(R.string.telegram_needs_access_to_external_storage_on), style = MaterialTheme.typography.bodySmall)
         Button(onClick = onStorageAccess, enabled = !s.busy) { Text(Words.get(R.string.grant_file_access)) }
        }
        OutlinedButton(onClick = onChooseFolder, enabled = !s.busy && s.fileAccess) { Text(Words.get(R.string.choose_folder)) }
        Text(Words.get(R.string.new_files_and_the_cache_will_be), style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
         Switch(s.clearBeforeDownload, vm::clearBeforeDownload, enabled = !s.busy)
         Text(Words.get(R.string.clear_previous_downloads), modifier = Modifier.weight(1f))
        }
        Text(if (s.clearBeforeDownload) Words.get(R.string.save_and_share_first_delete_files_managed) else Words.get(R.string.files_already_saved_for_the_same_attachment), style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
         Switch(s.archiveDefault, vm::archiveDefault, enabled = !s.busy)
         Text(Words.get(R.string.share_and_send_archive_contents), Modifier.weight(1f))
        }
        Text(Words.get(R.string.you_can_change_this_choice_before_sending), style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
         Switch(s.showCovers, vm::showCovers, enabled = !s.busy)
         Column(Modifier.weight(1f)) {
          Text(Words.get(R.string.use_previous_photos_as_covers))
          Text(Words.get(R.string.selection_shows_the_photo_immediately_before_a), style = MaterialTheme.typography.bodySmall)
         }
        }
        TextButton(onClick = { confirmAction = "cache" }, enabled = !s.busy && s.fileAccess) { Text(Words.get(R.string.clear_temporary_cache)) }
       } }
        }
        "account" -> {
       item { InfoCard {
        Text(Words.get(R.string.status, Words.get(s.connection)))
        Text(authLabel(s.auth), fontWeight = FontWeight.SemiBold)
        if (s.loginError.isNotBlank()) Text(s.loginError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
       } }
         if (!s.fileAccess) item { Text(Words.get(R.string.give_tgfm_access_to_the_working_folder)); Button(onClick = onStorageAccess, enabled = !s.busy) { Text(Words.get(R.string.grant_file_access)) } }
       if (s.auth in listOf("notStarted", "authorizationStateClosed") || (s.auth == "authorizationStateWaitTdlibParameters" && s.loginError.isNotBlank())) {
        item { InfoCard {
         Text(Words.get(R.string.telegram_api_credentials), style = MaterialTheme.typography.titleMedium)
         Text(Words.get(R.string.get_your_api_id_and_api_hash), style = MaterialTheme.typography.bodySmall)
         OutlinedTextField(apiId, { apiId = it }, label = { Text("API ID") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
         OutlinedTextField(apiHash, { apiHash = it }, label = { Text("API hash") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
         Button(onClick = { vm.connect(apiId, apiHash); apiHash = "" }, enabled = !s.busy && s.fileAccess && apiId.isNotBlank() && apiHash.isNotBlank()) { Text(Words.get(R.string.set_up_access)) }

        } }
       }
       s.authState?.let(vm::authDescription)?.takeIf { it.isNotEmpty() }?.let { item { Text(it) } }
       if (s.auth !in listOf("notStarted", "authorizationStateClosed", "authorizationStateReady", "authorizationStateClosing", "authorizationStateLoggingOut")) {
        item { TextButton(onClick = vm::reconfigure, enabled = !s.busy) { Text(Words.get(R.string.start_over_change_api_credentials)) } }
       }
       if (s.auth in listOf("authorizationStateWaitPhoneNumber", "authorizationStateWaitCode", "authorizationStateWaitPassword", "authorizationStateWaitEmailAddress", "authorizationStateWaitEmailCode")) {
        item { InfoCard {
         val password = s.auth == "authorizationStateWaitPassword"
         OutlinedTextField(authInput, { authInput = it }, label = { Text(when(s.auth) { "authorizationStateWaitPhoneNumber" -> Words.get(R.string.phone_with_country_code); "authorizationStateWaitPassword" -> Words.get(R.string.two_step_password); "authorizationStateWaitEmailAddress" -> Words.get(R.string.email); else -> Words.get(R.string.code_detail) }) },
          visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
          keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else if (s.auth == "authorizationStateWaitPhoneNumber") KeyboardType.Phone else KeyboardType.Text), singleLine = true, modifier = Modifier.fillMaxWidth())
         Button(onClick = { vm.authenticate(authInput); authInput = "" }, enabled = !s.busy && authInput.isNotBlank()) { Text(Words.get(R.string.action_continue)) }
         if (s.auth == "authorizationStateWaitCode") TextButton(onClick = vm::resendCode, enabled = !s.busy) { Text(Words.get(R.string.request_another_code)) }
        } }
       }
         if (s.auth == "authorizationStateReady") item { TextButton(onClick = { confirmAction = "logout" }, enabled = !s.busy) { Text(Words.get(R.string.sign_out)) } }
        }
        "favorites" -> {
       if (s.auth == "authorizationStateReady") {
        item { InfoCard {
         Text(Words.get(R.string.active_channel), style = MaterialTheme.typography.labelMedium)
         Text(s.channel?.title ?: Words.get(R.string.no_channel), style = MaterialTheme.typography.titleMedium)
         Button(onClick = vm::loadChannels, enabled = !s.busy) { Text(Words.get(R.string.refresh_channels_and_groups)) }
        } }
        item {
         SearchField(channelQuery, { channelQuery = it }, Words.get(R.string.search_channels_or_groups_by_name))
         Text(Words.get(R.string.favorites_channels_and_groups_shown, s.favorites.size, visibleChannels.size), style = MaterialTheme.typography.labelMedium)
         Text(Words.get(R.string.check_your_favorites_tap_the_channel_name), style = MaterialTheme.typography.bodySmall)
         if (visibleChannels.isEmpty()) Text(Words.get(R.string.no_channel_found_change_or_clear_the))
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
           if (s.channel?.id == channel.id) Text(Words.get(R.string.active), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
          }
         }
        }
       } else item { Text(Words.get(R.string.connect_telegram_to_load_your_channels_and)); TextButton(onClick = { settingsPage = "account" }) { Text(Words.get(R.string.connect_telegram)) } }
        }
       }
      }
      AppTab.FILTERS.ordinal -> {
       if (reviewExamples) {
        item { Text(Words.get(R.string.examples_rules, desired.size + unwanted.size, learning.rules.size), style = MaterialTheme.typography.titleMedium) }
        items(learning.rules) { rule -> InfoCard { Text(if (rule.exclude) Words.get(R.string.exclude_detail) else Words.get(R.string.include_detail), color = MaterialTheme.colorScheme.primary); Text(rule.template); Text(Words.count(R.plurals.attachments_matching_this_rule, s.attachments.count { FilterEngine.classify(it, listOf(rule)) in setOf(Match.CERTAIN, Match.EXCLUDED) }), style = MaterialTheme.typography.bodySmall) } }
        items(learning.warnings) { Text(it, color = MaterialTheme.colorScheme.error) }
        if (learning.rules.isNotEmpty()) {
         val combined = s.activeRules + learning.rules
         val missedDesired = desired.count { FilterEngine.classify(it, combined) != Match.CERTAIN }
         val includedUnwanted = unwanted.count { FilterEngine.classify(it, combined) == Match.CERTAIN }
         if (missedDesired > 0 || includedUnwanted > 0) item { Text(Words.get(R.string.with_your_existing_rules_wanted_examples_missed, missedDesired, includedUnwanted), color = MaterialTheme.colorScheme.error) }
         val previewFiles = AttachmentViews.preview(s.attachments, combined)
         item { Text(Words.count(R.plurals.files_that_would_be_selected, previewFiles.size), style = MaterialTheme.typography.titleMedium); Text(Words.get(R.string.preview_includes_existing_rules), style = MaterialTheme.typography.bodySmall) }
         items(previewFiles) { a -> Text(a.name, modifier = Modifier.padding(vertical = 8.dp)); HorizontalDivider() }
        }
        item { Text(Words.get(R.string.names_without_a_recognizable_date_need_a), style = MaterialTheme.typography.bodySmall) }
       } else if (!examplesPage) {
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
         Button(onClick = { editJson = RuleJson.encode(Rule(channelId = s.channel?.id ?: s.favorites.firstOrNull()?.id)).toString() }, enabled = !s.busy && s.favorites.isNotEmpty()) { Text(Words.get(R.string.new_rule)) }
         OutlinedButton(onClick = { examplesPage = true }) { Text(Words.get(R.string.from_examples)) }
        } }
        if (s.rules.isEmpty()) item { Text(Words.get(R.string.no_saved_rules_create_a_filter_or)) }
        if (s.favorites.isEmpty()) item { Text(Words.get(R.string.add_a_favorite_channel_in_settings_to)) }
        val ruleGroups = s.rules.groupBy { it.channelId }
        if(ruleGroups.size > 1) item {
         val allCollapsed = ruleGroups.keys.all { it.toString() in collapsedRuleGroups }
         TextButton(onClick = { collapsedRuleGroups = if(allCollapsed) emptyList() else ruleGroups.keys.map { it.toString() } }) {
          Icon(if(allCollapsed) UiIcons.Down else UiIcons.Up, null, Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text(if(allCollapsed) Words.get(R.string.expand_all) else Words.get(R.string.collapse_all))
         }
        }
        ruleGroups.forEach { (channelId, rules) ->
         val groupKey = channelId.toString()
         val collapsed = groupKey in collapsedRuleGroups
         item(key = "rule-group:$groupKey") {
          Row(Modifier.fillMaxWidth().padding(top = 8.dp).background(MaterialTheme.colorScheme.surfaceContainerLow)
           .semantics { stateDescription = if(collapsed) Words.get(R.string.collapsed) else Words.get(R.string.expanded) }
           .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = if(collapsed) Words.get(R.string.expand_rules) else Words.get(R.string.collapse_rules)) {
            collapsedRuleGroups = if(collapsed) collapsedRuleGroups - groupKey else collapsedRuleGroups + groupKey
           }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
           Column(Modifier.weight(1f)) {
            Text(s.favorites.firstOrNull { it.id == channelId }?.title ?: Words.get(R.string.unassigned), style = MaterialTheme.typography.titleSmall)
            Text(Words.get(R.string.rules_active, rules.size, rules.count { it.enabled }), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
           }
           Icon(if(collapsed) UiIcons.Down else UiIcons.Up, null, Modifier.size(20.dp))
          }
         }
         if(!collapsed) items(rules, key = { it.id }) { rule ->
          Column(Modifier.fillMaxWidth().testTag("rule:${rule.id}").padding(top = 6.dp)) {
           Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(rule.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(if (rule.exclude) Words.get(R.string.exclude_detail) else Words.get(R.string.include_detail), style = MaterialTheme.typography.labelMedium, color = if (rule.exclude) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
           }
           Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
             Text(ruleSummary(rule), style = MaterialTheme.typography.bodySmall)
             if (rule.extensions.isEmpty() && rule.mime.isBlank() && rule.category == null) Text(Words.get(R.string.all_formats), style = MaterialTheme.typography.labelSmall)
             if (!rule.enabled) Text(Words.get(R.string.disabled), style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { editJson = RuleJson.encode(rule).toString() }, enabled = !s.busy) { Icon(UiIcons.Edit, Words.get(R.string.edit, rule.title), Modifier.size(20.dp)) }
            IconButton(onClick = { confirmJson = RuleJson.encode(rule.copy(enabled = !rule.enabled)).toString(); confirmAction = "rule" }, enabled = !s.busy && rule.channelId != null) { Icon(if (rule.enabled) UiIcons.Pause else UiIcons.Play, if (rule.enabled) Words.get(R.string.disable, rule.title) else Words.get(R.string.enable, rule.title), Modifier.size(20.dp), tint = if (rule.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = { confirmJson = RuleJson.encode(rule).toString(); confirmAction = "delete" }, enabled = !s.busy) { Icon(UiIcons.Delete, Words.get(R.string.delete, rule.title), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) }
           }
           HorizontalDivider()
          }
         }
        }
        item { ExpandableInfo(Words.get(R.string.how_rules_work_together)) { Text(Words.get(R.string.a_file_must_meet_every_criterion_in), style = MaterialTheme.typography.bodySmall) } }
       } else {
       item { Text(Words.get(R.string.files_already_read, s.channel?.title ?: Words.get(R.string.no_channel)), style = MaterialTheme.typography.bodySmall) }
       item {
        SearchField(exampleQuery, { exampleQuery = it }, Words.get(R.string.search_examples_by_filename))
        Row(verticalAlignment = Alignment.CenterVertically) {
         Checkbox(onlyLabelledExamples, { onlyLabelledExamples = it })
         Text(Words.get(R.string.marked_examples_only))
        }
        Text(Words.get(R.string.files_shown_total_marked_examples, visibleExamples.size, s.attachments.size, desired.size + unwanted.size), style = MaterialTheme.typography.labelMedium)
        if (visibleExamples.isEmpty()) Text(if (s.attachments.isEmpty()) Words.get(R.string.read_a_channel_in_selection_first) else Words.get(R.string.no_files_found_change_the_search_or))
       }
       items(visibleExamples, key = { "example:${it.key}" }) { a ->
        Column(Modifier.fillMaxWidth()) {
         Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(a.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
          IconToggleButton(a.key in desiredKeys, {
           desiredKeys = ArrayList(if (a.key in desiredKeys) desiredKeys - a.key else desiredKeys + a.key)
           unwantedKeys = ArrayList(unwantedKeys - a.key)
          }, enabled = !s.busy) { Icon(UiIcons.Check, if (a.key in desiredKeys) Words.get(R.string.unmark_wanted_example) else Words.get(R.string.wanted), tint = if (a.key in desiredKeys) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
          IconToggleButton(a.key in unwantedKeys, {
           unwantedKeys = ArrayList(if (a.key in unwantedKeys) unwantedKeys - a.key else unwantedKeys + a.key)
           desiredKeys = ArrayList(desiredKeys - a.key)
          }, enabled = !s.busy) { Icon(UiIcons.Close, if (a.key in unwantedKeys) Words.get(R.string.unmark_unwanted_example) else Words.get(R.string.unwanted), tint = if (a.key in unwantedKeys) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
         }
         HorizontalDivider()
        }
       }

       }
      }
      AppTab.SELECTION.ordinal -> {
       item {
        Text(Words.get(R.string.channel_or_group), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
         TextButton(onClick = { selectedChannelMenu = true }, enabled = !s.busy && s.favorites.isNotEmpty(), contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) {
          Text(s.channel?.title ?: Words.get(R.string.choose_a_favorite), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
          Icon(UiIcons.Down, Words.get(R.string.switch_favorite_channel))
         }
         DropdownMenu(expanded = selectedChannelMenu, onDismissRequest = { selectedChannelMenu = false }) {
          s.favorites.forEach { favorite -> DropdownMenuItem(text = { Text(favorite.title) }, onClick = { vm.chooseChannel(favorite); selectedChannelMenu = false }) }
         }
        }
        if (s.connection !in setOf(R.string.connected, R.string.updating)) Text(Words.get(R.string.previously_read_attachments_are_available, Words.get(s.connection)), style = MaterialTheme.typography.labelSmall)
       }
       item { PostPeriodSelector(s.postPeriod, vm::postPeriod, enabled = !s.busy) {
        Button(onClick = vm::scan, enabled = !s.busy && s.auth == "authorizationStateReady" && s.channel != null && s.postPeriod.error() == null) { Text(Words.get(R.string.read)) }
       } }
       val scanned = s.scannedPeriod
       if (scanned != null) item {
        Text("${if (s.scanComplete) Words.get(R.string.scan_complete) else Words.get(R.string.partial_scan)} · ${scanned.first.description(scanned.second)}", style = MaterialTheme.typography.labelSmall)
       }
       item { HorizontalDivider(Modifier.padding(vertical = 6.dp)) }
       item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(Words.get(R.string.show), style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.weight(1f)) {
         TextButton(onClick = { viewMenuExpanded = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = .06f))) {
          Text(viewLabel(s.attachmentView), Modifier.weight(1f)); Icon(UiIcons.Down, null, Modifier.size(20.dp))
         }
         DropdownMenu(expanded = viewMenuExpanded, onDismissRequest = { viewMenuExpanded = false }) {
          AttachmentView.entries.forEach { view -> DropdownMenuItem(text = { Text(viewLabel(view)) }, onClick = { vm.attachmentView(view); viewMenuExpanded = false }) }
         }
        }
       } }
       item { SearchField(selectionQuery, { selectionQuery = it }, Words.get(R.string.search)) }
       item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(Words.get(R.string.shown_read, selectionFiles.size, s.attachments.size), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        val allSelected = s.attachments.isNotEmpty() && s.selected.size == s.attachments.size
        TextButton(onClick = { vm.selectAll(false, !allSelected) }, enabled = !s.busy && s.attachments.isNotEmpty()) { Text(if (allSelected) Words.get(R.string.deselect_all) else Words.get(R.string.select_all)) }
       } }
       if (selectionFiles.isEmpty()) item { Text(when {
        s.attachments.isEmpty() -> Words.get(R.string.no_attachments_read_yet)
        selectionQuery.isNotBlank() -> Words.get(R.string.no_files_in_this_view_change_the)
        s.attachmentView == AttachmentView.SELECTED -> Words.get(R.string.no_files_selected_choose_a_different_view)
        else -> Words.get(R.string.no_selected_or_similar_files_choose_show)
       }) }
       items(selectionFiles, key = { it.key }) { a ->
        val match = FilterEngine.classify(a, s.activeRules)
        val selected = a.key in s.selected
        val cover = a.cover?.takeIf { s.showCovers && (it.fileId != 0 || it.miniature.isNotBlank()) }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)
         .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .055f) else Color.Transparent)
         .testTag("attachment:${a.key}")) {
         Box(Modifier.width(if (s.showCovers) 64.dp else 48.dp).fillMaxHeight().heightIn(min = 48.dp)
          .toggleable(value = selected, enabled = !s.busy, role = androidx.compose.ui.semantics.Role.Checkbox,
           onValueChange = { vm.toggle(a.key) })
          .semantics { contentDescription = Words.get(R.string.select, a.name) }.padding(3.dp), contentAlignment = Alignment.Center) {
          if (cover != null) CoverThumbnail(cover, vm, Modifier.fillMaxSize())
          Box(Modifier.align(if (cover == null) Alignment.Center else Alignment.BottomEnd)
           .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.extraSmall)) {
           Checkbox(checked = selected, onCheckedChange = null, enabled = !s.busy)
          }
         }
         Column(Modifier.weight(1f).heightIn(min = 48.dp)
          .clickable(onClickLabel = Words.get(R.string.open_preview)) { attachmentDetails = a }
          .padding(horizontal = 8.dp, vertical = 5.dp), verticalArrangement = Arrangement.Center) {
          Text(a.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
          val summary = a.caption.ifBlank { if (s.showCovers) a.cover?.caption.orEmpty() else "" }
          if (summary.isNotBlank()) Text(summary, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
          Row(verticalAlignment = Alignment.CenterVertically) {
           Text(sizeLabel(a.size) + if (a.date > 0) " · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Words.locale).format(Date(a.date * 1000)) else "",
            style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
           MatchIcon(match)
          }
         }
        }
        HorizontalDivider()
       }
       item { ExpandableInfo(Words.get(R.string.manual_selection_and_details)) { Text(Words.get(R.string.manual_selection_applies_to_this_operation_and), style = MaterialTheme.typography.bodySmall) } }
      }
      AppTab.DOWNLOADS.ordinal -> {
       item { Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(s.workPath, style = MaterialTheme.typography.bodySmall); Text(Words.count(R.plurals.saved_files, s.downloads.size), style = MaterialTheme.typography.bodySmall) }
        IconButton(onClick = vm::refreshLocalFiles, enabled = !s.busy) { Icon(UiIcons.Refresh, Words.get(R.string.refresh_file_list)) }
       } }
       item { Row(verticalAlignment = Alignment.CenterVertically) {
        Text(Words.get(R.string.files_on_device), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        val allSelected = s.downloads.isNotEmpty() && s.downloadedSelection.size == s.downloads.size
        TextButton(onClick = { vm.selectAll(true, !allSelected) }, enabled = !s.busy && s.downloads.isNotEmpty()) { Text(if (allSelected) Words.get(R.string.deselect_all) else Words.get(R.string.select_all)) }
       } }
       if (s.downloads.isEmpty()) item { Text(Words.get(R.string.the_downloads_folder_is_empty)) }
       items(s.downloads, key = { it.path }) { file ->
        Column(Modifier.fillMaxWidth().testTag("download:${file.path}")) {
         Text(java.io.File(file.path).name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 8.dp, end = 8.dp))
         Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(file.path in s.downloadedSelection, { vm.toggle(file.path, true) }, enabled = !s.busy, modifier = Modifier.semantics { contentDescription = Words.get(R.string.select, file.originalName) })
          Text("${java.io.File(file.path).extension.uppercase().ifBlank { file.mime }} · ${sizeLabel(file.size)}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
          IconButton(onClick = { onShare(file) }, enabled = !s.busy) { Icon(UiIcons.Eye, Words.get(R.string.open, file.originalName), Modifier.size(20.dp)) }
          IconButton(onClick = { deleteFiles = listOf(file) }, enabled = !s.busy) { Icon(UiIcons.Delete, Words.get(R.string.delete, file.originalName), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) }
         }
         HorizontalDivider()
        }
       }
       if (s.retry.isNotEmpty()) item { OutlinedButton(onClick = { onDownload(true) }, enabled = !s.busy) { Text(Words.get(R.string.retry_incomplete_downloads, s.retry.size)) } }
      }
     }
     if (s.failures.isNotEmpty()) item { InfoCard { Text(Words.get(R.string.issues_to_review), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold); s.failures.forEach { Text(it, style = MaterialTheme.typography.bodySmall) } } }
     item { Spacer(Modifier.height(12.dp)) }
    }
   }
  }
  attachmentDetails?.let { a -> AlertDialog(onDismissRequest = { attachmentDetails = null }, title = { Text(Words.get(R.string.file_details)) }, text = {
   Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(a.name, fontWeight = FontWeight.SemiBold)
    Text("${a.category.label} · .${a.extension} · ${sizeLabel(a.size)}")
    if (a.date > 0) Text(DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Words.locale).format(Date(a.date * 1000)))
    Text(FilterEngine.classify(a, s.activeRules).label)
    if(s.showCovers) a.cover?.let { cover ->
     CoverThumbnail(cover, vm, Modifier.fillMaxWidth().heightIn(max = 220.dp).height(180.dp))
     if(cover.caption.isNotBlank() && cover.caption != a.caption) {
      Text(Words.get(R.string.cover_caption), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
      androidx.compose.foundation.text.selection.SelectionContainer { Text(cover.caption) }
     }
    }
    if (a.caption.isNotBlank()) androidx.compose.foundation.text.selection.SelectionContainer { Text(a.caption) }
   }
  }, confirmButton = { TextButton(onClick = { attachmentDetails = null }) { Text(Words.get(R.string.close)) } }) }
  if (s.ready && !s.busy && !s.fileAccess && !permissionPromptHandled) AlertDialog(
   onDismissRequest = { permissionPromptHandled = true }, title = { Text(Words.get(R.string.file_access_required)) },
   text = { Text(if (android.os.Build.VERSION.SDK_INT >= 30) Words.get(R.string.open_android_special_app_access_and_enable) else Words.get(R.string.allow_file_access_in_android_then_return)) },
   confirmButton = { TextButton(onClick = { permissionPromptHandled = true; onStorageAccess() }) { Text(Words.get(R.string.grant_access)) } },
   dismissButton = { TextButton(onClick = { permissionPromptHandled = true }) { Text(Words.get(R.string.not_now)) } })
  removeFavoriteId?.let { id ->
   val favorite = s.favorites.firstOrNull { it.id == id }
   val count = s.rules.count { it.channelId == id }
   if (favorite != null) AlertDialog(onDismissRequest = { removeFavoriteId = null }, title = { Text(Words.get(R.string.remove_from_favorites)) },
    text = { Text(Words.get(R.string.removing_also_deletes_its_filters_other_channels, favorite.title, count)) },
    confirmButton = { TextButton(onClick = { removeFavoriteId = null; vm.removeFavorite(favorite) }, enabled = !s.busy) { Text(Words.get(R.string.remove)) } },
    dismissButton = { TextButton(onClick = { removeFavoriteId = null }) { Text(Words.get(R.string.cancel)) } })
  }
  deleteFiles?.let { files -> AlertDialog(onDismissRequest = { deleteFiles = null }, title = { Text(Words.count(R.plurals.delete_selected_local_files, files.size)) },
   text = { Text(Words.get(R.string.files_will_be_deleted_from_the_working)) },
   confirmButton = { TextButton(onClick = { vm.deleteDownloads(files); deleteFiles = null }, enabled = !s.busy) { Text(Words.get(R.string.delete_detail), color = MaterialTheme.colorScheme.error) } },
   dismissButton = { TextButton(onClick = { deleteFiles = null }) { Text(Words.get(R.string.cancel)) } }) }
  s.archiveRename?.let { suggested ->
   var name by remember(suggested) { mutableStateOf(suggested) }
   AlertDialog(onDismissRequest = { vm.answerArchiveName(null) }, title = { Text(Words.get(R.string.archive_already_exists)) },
    text = { OutlinedTextField(name, { name = it }, label = { Text(Words.get(R.string.new_archive_name)) }, singleLine = true) },
    confirmButton = { TextButton(onClick = { vm.answerArchiveName(name.trim()) }) { Text(Words.get(R.string.action_continue)) } },
    dismissButton = { TextButton(onClick = { vm.answerArchiveName(null) }) { Text(Words.get(R.string.cancel)) } })
  }
  if(s.remoteCleanup.isNotEmpty() && !s.busy) AlertDialog(onDismissRequest = { vm.remoteCleanup(false) }, title = { Text(Words.get(R.string.ftp_upload_interrupted)) },
   text = { Text(Words.get(R.string.the_server_may_contain_complete_or_partial, s.remoteCleanup.size) + s.remoteCleanup.take(8).joinToString("\n") + if(s.remoteCleanup.size > 8) "\n…" else "") },
   confirmButton = { TextButton(onClick = { vm.remoteCleanup(true) }) { Text(Words.get(R.string.delete_from_ftp)) } },
   dismissButton = { TextButton(onClick = { vm.remoteCleanup(false) }) { Text(Words.get(R.string.keep)) } })
  if (confirmAction.isNotEmpty()) AlertDialog(onDismissRequest = { confirmAction = "" }, title = { Text(when(confirmAction) {
   "share-local" -> Words.get(R.string.share_files); "download" -> Words.get(R.string.save_selected_files); "share" -> Words.get(R.string.save_and_share_files); "cache" -> Words.get(R.string.clear_cache)
   "ftp-selection", "ftp-downloads" -> Words.get(R.string.send_files_to_ftp); "logout" -> Words.get(R.string.sign_out_of_telegram); "delete" -> Words.get(R.string.delete_rule); else -> Words.get(R.string.confirm_change)
  }) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(when(confirmAction) {
   "download", "share" -> (if (s.clearBeforeDownload) Words.get(R.string.files_previously_managed_by_tgfm_will_be) else Words.get(R.string.existing_copies_of_the_same_attachment_will)) + if (confirmAction == "share") Words.get(R.string.android_sharing_will_open_after_saving) else ""
   "ftp-selection", "ftp-downloads" -> Words.get(R.string.files_with_the_same_name_are_overwritten) + if (ftpKeep) Words.get(R.string.local_files_will_be_kept) else Words.get(R.string.local_copies_will_be_deleted_after_ftp)
   "share-local" -> Words.get(R.string.keep_shared_archives_on_the_device_while)
   "cache" -> Words.get(R.string.deletes_temporary_tdlib_files_including_interrupted_transfers)
   "logout" -> Words.get(R.string.your_telegram_session_will_close_your_filters)
   else -> Words.get(R.string.your_changes_will_be_saved_and_automatic)
  })
   if (confirmAction in setOf("share", "share-local", "ftp-selection", "ftp-downloads")) {
    val hasArchive = if(confirmAction in setOf("share-local", "ftp-downloads")) vm.selectedDownloads().any { ArchiveNames.isArchive(it.originalName) }
     else s.attachments.any { it.key in s.selected && ArchiveNames.isArchive(it.name) }
    if(hasArchive) {
     Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(archiveContents, { archiveContents = it }); Text(Words.get(R.string.use_archive_contents)) }
     Text(if(archiveContents) Words.get(R.string.all_files_inside_without_extracted_copies_on) else Words.get(R.string.send_original_archives_with_multipart_volumes_kept), style = MaterialTheme.typography.bodySmall)
    }
   }
   if (confirmAction in setOf("ftp-selection", "ftp-downloads")) {
    Text(Words.get(R.string.destination, if(s.ftp.tls) "ftps" else "ftp", s.ftp.host, s.ftp.port, s.ftp.directory.trimEnd('/'), ftpPath.trim('/')), style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(ftpPath, { ftpPath = it }, label = { Text(Words.get(R.string.subfolder_for_this_upload)) }, placeholder = { Text(Words.get(R.string.copies_november)) }, singleLine = true)

    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(ftpKeep, { ftpKeep = it }); Text(Words.get(R.string.keep_on_device)) }
    Text(if (ftpKeep) Words.get(R.string.archives_stay_on_the_device) else Words.get(R.string.one_file_at_a_time_after_a), style = MaterialTheme.typography.bodySmall)
    Text(Words.get(R.string.this_choice_applies_only_to_this_upload), style = MaterialTheme.typography.bodySmall)
   }
  } }, confirmButton = { TextButton(onClick = {
   vm.archiveMode(archiveContents)
   when(confirmAction) { "share-local" -> onShare(null); "download" -> onDownload(false); "share" -> onSelectionShare(); "ftp-selection" -> onFtp(false, ftpKeep, ftpPath); "ftp-downloads" -> onFtp(true, ftpKeep, ftpPath); "cache" -> vm.clearCache(); "logout" -> vm.logout(); "delete" -> confirmJson?.let { vm.deleteRule(RuleJson.decode(JSONObject(it))) }; "rule" -> confirmJson?.let { vm.saveRule(RuleJson.decode(JSONObject(it))) } }
   confirmAction = ""
  }, enabled = !s.busy) { Text(Words.get(R.string.confirm)) } }, dismissButton = { TextButton(onClick = { confirmAction = "" }) { Text(Words.get(R.string.cancel)) } })
 }
}

@Composable private fun Heading(title: String, subtitle: String) { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun InfoCard(content: @Composable ColumnScope.() -> Unit) { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content) } }
fun sizeLabel(size: Long): String = when { size <= 0 -> Words.get(R.string.size_unavailable); size < 1024 -> "$size B"; size < 1024 * 1024 -> String.format(Words.locale, "%.1f KB", size / 1024.0); else -> String.format(Words.locale, "%.1f MB", size / (1024.0 * 1024)) }
private fun authLabel(state: String) = when(state) { "authorizationStateReady" -> Words.get(R.string.account_connected); "authorizationStateWaitPhoneNumber" -> Words.get(R.string.enter_your_phone_number); "authorizationStateWaitCode" -> Words.get(R.string.waiting_for_code); "authorizationStateWaitPassword" -> Words.get(R.string.two_step_verification); "notStarted", "authorizationStateClosed" -> Words.get(R.string.sign_in_setup_required); else -> Words.get(R.string.signing_in) }
private fun ruleSummary(r: Rule) = listOfNotNull(r.template.takeIf { it.isNotBlank() }, r.contains.takeIf { it.isNotBlank() }?.let { Words.get(R.string.contains, it) }, r.extensions.takeIf { it.isNotEmpty() }?.joinToString(", "), r.mime.takeIf { it.isNotBlank() }, r.category?.label).joinToString(" · ")

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
 val error = FilterEngine.validate(draft) ?: if (favorites.none { it.id == channelId }) Words.get(R.string.choose_a_favorite_channel_for_this_filter) else null
 EditorPage(actions = {
  CommandButton(Words.get(R.string.confirm_and_save), error == null, Modifier.weight(1f)) { onConfirm(draft) }
  TextButton(onClick = onDismiss) { Text(Words.get(R.string.cancel)) }
 }) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
   Box {
    OutlinedButton(onClick = { channelMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(favorites.firstOrNull { it.id == channelId }?.title ?: Words.get(R.string.assign_to_a_favorite)); Icon(UiIcons.Down, null) }
    DropdownMenu(expanded = channelMenu, onDismissRequest = { channelMenu = false }) {
     favorites.forEach { favorite -> DropdownMenuItem(text = { Text(favorite.title) }, onClick = { channelId = favorite.id; channelMenu = false }) }
    }
   }
   OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text(Words.get(R.string.rule_name)) }, singleLine = true)
   Row(verticalAlignment = Alignment.CenterVertically) { Switch(exclude, { exclude = it }); Text(if (exclude) Words.get(R.string.explicit_exclusion) else Words.get(R.string.inclusion)) }
   OutlinedTextField(if (template.contains("{data}")) template.substringBefore("{data}") else "", { prefix ->
    template = FilterEditing.withDateRoot(template, prefix)
   }, modifier = Modifier.fillMaxWidth(), label = { Text(Words.get(R.string.exact_prefix_before_date)) }, placeholder = { Text("La_Gente_") }, supportingText = { Text(Words.get(R.string.include_the_final_separator_for_example_la)) })
   OutlinedTextField(template, { template = it }, modifier = Modifier.fillMaxWidth(), label = { Text(Words.get(R.string.filename_pattern_without_extension)) }, placeholder = { Text("pino_binio_{data}") })
   Text(Words.get(R.string.la_gente_data_matches_that_exact_name), style = MaterialTheme.typography.labelSmall)
   ExpandableInfo(Words.get(R.string.more_criteria_format_and_contents)) {
   OutlinedTextField(contains, { contains = it }, modifier = Modifier.fillMaxWidth(), label = { Text(Words.get(R.string.text_in_filename)) })
   OutlinedTextField(extensions, { extensions = it }, modifier = Modifier.fillMaxWidth(), label = { Text(Words.get(R.string.extensions_empty_all)) }, placeholder = { Text("pdf, zip, epub") })
   OutlinedTextField(mime, { mime = it }, modifier = Modifier.fillMaxWidth(), label = { Text(Words.get(R.string.mime_type_optional)) }, placeholder = { Text("application/pdf") })
   Text(Words.get(R.string.telegram_category_independent_of_extension), style = MaterialTheme.typography.labelMedium)
   Column { (listOf("" to Words.get(R.string.any)) + Category.entries.map { it.name to it.label }).forEach { (value, label) ->
    Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(category == value, { category = value }); Text(label) }
   } }
   }
   val channelExamples = examples.filter { it.chatId == channelId }
   val count = channelExamples.count { FilterEngine.classify(it, listOf(draft)) in setOf(Match.CERTAIN, Match.EXCLUDED) }
   Text(Words.get(R.string.preview_channel_attachments_all_specified_criteria_must, count, channelExamples.size))
   error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
  }
 }
}

@Composable private fun SearchField(value: String, onValueChange: (String) -> Unit, label: String) {
 OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, leadingIcon = { Icon(UiIcons.Search, null) },
  modifier = Modifier.fillMaxWidth(), singleLine = true,
  trailingIcon = { if (value.isNotEmpty()) TextButton(onClick = { onValueChange("") }) { Text(Words.get(R.string.clear)) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun PostPeriodSelector(value: PostPeriod, onChange: (PostPeriod) -> Unit, enabled: Boolean, readAction: @Composable () -> Unit = {}) {
 var expanded by remember { mutableStateOf(false) }
 var picker by rememberSaveable { mutableStateOf<String?>(null) }
 Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
   Text("Post", style = MaterialTheme.typography.bodyMedium)
   Box(Modifier.weight(1f)) {
    TextButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("post-period")) { Text(value.mode.label, Modifier.weight(1f)); Icon(UiIcons.Down, null, Modifier.size(20.dp)) }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
     PostPeriodMode.entries.forEach { mode -> DropdownMenuItem(text = { Text(mode.label) }, onClick = { onChange(value.copy(mode = mode)); expanded = false }) }
    }
   }
   readAction()
  }
  if (value.mode == PostPeriodMode.DAY || value.mode == PostPeriodMode.RANGE) {
   val format = DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.SHORT).withLocale(Words.locale)
   Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedButton(onClick = { picker = "first" }, enabled = enabled, modifier = Modifier.weight(1f).testTag("post-first-date")) {
     Text("${if (value.mode == PostPeriodMode.DAY) Words.get(R.string.day) else Words.get(R.string.from)} ${value.first.format(format)}")
    }
    if (value.mode == PostPeriodMode.RANGE) OutlinedButton(onClick = { picker = "last" }, enabled = enabled, modifier = Modifier.weight(1f).testTag("post-last-date")) {
     Text(Words.get(R.string.to, value.last.format(format)))
    }
   }
  }
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
   }, enabled = dateState.selectedDateMillis != null) { Text(Words.get(R.string.confirm_date)) }
  }, dismissButton = { TextButton(onClick = { picker = null }) { Text(Words.get(R.string.cancel)) } }) { DatePicker(dateState) }
 } }
}

@Composable private fun FtpEditor(settings: FtpSettings, onDismiss: () -> Unit, onConfirm: (FtpSettings) -> Unit, onTest: () -> Unit, enabled: Boolean) {
 var host by remember { mutableStateOf(settings.host) }
 var port by remember { mutableStateOf(settings.port.toString()) }
 var user by remember { mutableStateOf(settings.user) }
 var password by remember { mutableStateOf(settings.password) }
 var directory by remember { mutableStateOf(settings.directory) }
 var tls by remember { mutableStateOf(settings.tls) }
 var keep by remember { mutableStateOf(settings.keep) }
 val draft = FtpSettings(host.trim(), port.toIntOrNull() ?: 0, user.trim(), password, directory.trim(), tls, keep)
 val error = runCatching { draft.validate() }.exceptionOrNull()?.message
 EditorPage(actions = {
  CommandButton(Words.get(R.string.save_settings), enabled && error == null, Modifier.weight(1f)) { onConfirm(draft) }
  TextButton(onClick = onDismiss) { Text(Words.get(R.string.cancel)) }
 }) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
   Text(Words.get(R.string.connection), style = MaterialTheme.typography.titleMedium)
   OutlinedTextField(host, { host = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Host") }, singleLine = true)
   OutlinedTextField(port, { port = it }, modifier = Modifier.fillMaxWidth(), label = { Text(Words.get(R.string.port)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
   OutlinedTextField(user, { user = it }, modifier = Modifier.fillMaxWidth(), label = { Text(Words.get(R.string.username)) }, singleLine = true)
   OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
   OutlinedTextField(directory, { directory = it }, modifier = Modifier.fillMaxWidth(), label = { Text(Words.get(R.string.server_folder)) }, singleLine = true)
   Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(tls, { tls = it }); Text(Words.get(R.string.explicit_ftps_tls)) }
   Text(if (tls) Words.get(R.string.encrypted_connection_the_server_certificate_must_be) else Words.get(R.string.ftp_sends_credentials_and_files_without_encryption), style = MaterialTheme.typography.bodySmall)
   Text(Words.get(R.string.after_upload), style = MaterialTheme.typography.titleMedium)
   Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(keep, { keep = it }); Text(Words.get(R.string.keep_after_upload)) }
   Text(if (keep) Words.get(R.string.files_stay_in_the_working_folder_up) else Words.get(R.string.one_file_at_a_time_after_ftp), style = MaterialTheme.typography.bodySmall)
   Text(Words.get(R.string.passive_mode_the_remote_folder_must_already), style = MaterialTheme.typography.bodySmall)
   error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
  }
   TextButton(onClick = onTest, enabled = enabled && settings.host.isNotBlank() && draft == settings) { Text(Words.get(R.string.check_access)) }
   if (draft != settings) Text(Words.get(R.string.save_changes_before_checking_access), style = MaterialTheme.typography.bodySmall)
 }
}
