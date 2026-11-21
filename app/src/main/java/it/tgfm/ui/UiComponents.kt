package it.tgfm.ui

import it.tgfm.R
import it.tgfm.Words

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import it.tgfm.core.AttachmentView
import it.tgfm.core.Match

/** Shared compact commands remain above section navigation, outside scrolling lists. */
@Composable internal fun CommandBar(summary: String? = null, actions: @Composable RowScope.() -> Unit) {
 Surface {
  Column(Modifier.fillMaxWidth().testTag("command-bar")) {
   HorizontalDivider()
   Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
    summary?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
   }
  }
 }
}

@Composable internal fun CommandButton(label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
 Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
  Text(label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge)
 }
}

@Composable internal fun EditorPage(actions: @Composable RowScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
 Column(Modifier.fillMaxSize().imePadding()) {
  Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
  CommandBar(actions = actions)
 }
}

@Composable internal fun SettingsLink(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
 Column {
  Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
   Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
   Column(Modifier.weight(1f)) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
   }
   Icon(UiIcons.Next, null, Modifier.size(20.dp))
  }
  HorizontalDivider()
 }
}

@Composable internal fun ExpandableInfo(title: String, content: @Composable ColumnScope.() -> Unit) {
 var expanded by rememberSaveable { mutableStateOf(false) }
 Column(Modifier.fillMaxWidth()) {
  TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (expanded) Words.get(R.string.expanded) else Words.get(R.string.closed) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
   Text(title, modifier = Modifier.weight(1f)); Icon(if (expanded) UiIcons.Up else UiIcons.Down, null, Modifier.size(20.dp))
  }
  if (expanded) Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
 }
}

@Composable internal fun MatchIcon(match: Match) {
 when(match) {
  Match.CERTAIN -> Icon(UiIcons.Check, match.label, Modifier.size(20.dp), tint = Color(0xFF16803C))
  Match.EXCLUDED -> Icon(UiIcons.Close, match.label, Modifier.size(20.dp), tint = Color(0xFFC44536))
  else -> Text("?", color = Color(0xFF947000), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { contentDescription = match.label })
 }
}

internal fun viewLabel(view: AttachmentView): String = when(view) {
 AttachmentView.SELECTED -> Words.get(R.string.selected_only)
 AttachmentView.SELECTED_AND_SIMILAR -> Words.get(R.string.selected_and_similar)
 AttachmentView.ALL -> Words.get(R.string.all_files)
}
