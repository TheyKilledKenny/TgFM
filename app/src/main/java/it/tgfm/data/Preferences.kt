package it.tgfm.data

import it.tgfm.R
import it.tgfm.Words

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import it.tgfm.core.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import org.json.JSONArray
import org.json.JSONObject

private val Context.store by preferencesDataStore("tgfm")
object RuleJson {
 fun encode(r: Rule) = JSONObject().put("id", r.id).put("title", r.title).put("enabled", r.enabled).put("exclude", r.exclude).put("template", r.template).put("contains", r.contains).put("extensions", JSONArray(r.extensions.toList())).put("mime", r.mime).put("category", r.category?.name ?: "").put("channel_id", r.channelId ?: JSONObject.NULL)
 fun decode(j: JSONObject) = Rule(j.getString("id"), j.getString("title"), j.optBoolean("enabled", true), j.optBoolean("exclude"), j.optString("template"), j.optString("contains"), j.optJSONArray("extensions").strings().toSet(), j.optString("mime"), Category.entries.firstOrNull { it.name == j.optString("category") }, if (j.has("channel_id") && !j.isNull("channel_id")) j.getLong("channel_id") else null)
}
fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }
class Preferences(context: Context, private val dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> = context.store) {
 private val coversKey = booleanPreferencesKey("show_previous_photo_covers")
 val showCovers = dataStore.data.map { it[coversKey] ?: false }.distinctUntilChanged()
 suspend fun saveShowCovers(value: Boolean) { dataStore.edit { it[coversKey] = value } }
 private val archiveKey = booleanPreferencesKey("archive_contents")
 val archiveContents = dataStore.data.map { it[archiveKey] ?: false }
 suspend fun saveArchiveContents(value: Boolean) { dataStore.edit { it[archiveKey] = value } }
 private val rulesKey = stringPreferencesKey("rules")
 private val channelKey = stringPreferencesKey("channel")
 private val favoritesKey = stringPreferencesKey("favorite_channels")
 private val clearDownloadsKey = booleanPreferencesKey("clear_before_download")
 val clearBeforeDownload = dataStore.data.map { it[clearDownloadsKey] ?: false }
 suspend fun saveClearBeforeDownload(value: Boolean) { dataStore.edit { it[clearDownloadsKey] = value } }
 private val showAllKey = booleanPreferencesKey("show_all")
 private val attachmentViewKey = stringPreferencesKey("attachment_view")
 val attachmentView = dataStore.data.map { AttachmentView.fromStored(it[attachmentViewKey], it[showAllKey]) }.distinctUntilChanged()
 suspend fun saveAttachmentView(value: AttachmentView) { dataStore.edit {
  it[attachmentViewKey] = value.name
  it.remove(showAllKey)
 } }
 private fun decode(prefs: androidx.datastore.preferences.core.Preferences): ChannelConfiguration {
  val active = prefs[channelKey]?.let { JSONObject(it) }?.let { Channel(it.getLong("id"), it.getString("title")) }
  val favoritesJson = prefs[favoritesKey]
  val favorites = favoritesJson?.let { raw -> JSONArray(raw).let { array ->
   (0 until array.length()).map { array.getJSONObject(it).let { Channel(it.getLong("id"), it.getString("title")) } }
  } } ?: listOfNotNull(active)
  val rules = JSONArray(prefs[rulesKey] ?: "[]").let { array -> (0 until array.length()).map {
   val rule = RuleJson.decode(array.getJSONObject(it))
   if (favoritesJson == null && rule.channelId == null && active != null) rule.copy(channelId = active.id) else rule
  } }
  return ChannelConfiguration(favorites.distinctBy { it.id }, favorites.firstOrNull { it.id == active?.id }, rules)
 }
 private fun channelJson(channel: Channel) = JSONObject().put("id", channel.id).put("title", channel.title)
 private suspend fun editConfiguration(change: (ChannelConfiguration) -> ChannelConfiguration): ChannelConfiguration {
  val saved = dataStore.edit { prefs ->
   val config = change(decode(prefs))
   prefs[favoritesKey] = JSONArray(config.favorites.map(::channelJson)).toString()
   if (config.channel == null) prefs.remove(channelKey) else prefs[channelKey] = channelJson(config.channel).toString()
   prefs[rulesKey] = JSONArray(config.rules.map(RuleJson::encode)).toString()
  }
  return decode(saved)
 }
 val configuration = dataStore.data.map(::decode).distinctUntilChanged()
 val rules = configuration.map { it.rules }.distinctUntilChanged()
 val channel = configuration.map { it.channel }.distinctUntilChanged()
 suspend fun saveRules(rules: List<Rule>) = editConfiguration { config ->
  require(rules.all { it.channelId == null || config.favorites.any { c -> c.id == it.channelId } }) { Words.get(R.string.assign_filters_to_a_favorite_channel) }
  config.copy(rules = rules)
 }
 // Used also to migrate the former single-channel configuration.
 suspend fun saveChannel(channel: Channel?) = editConfiguration { config ->
  config.copy(channel = channel, favorites = if (channel == null || config.favorites.any { it.id == channel.id }) config.favorites else config.favorites + channel)
 }
 suspend fun addFavorite(channel: Channel) = editConfiguration { config ->
  config.copy(favorites = if (config.favorites.any { it.id == channel.id }) config.favorites else config.favorites + channel,
   channel = config.channel ?: channel)
 }
 suspend fun removeFavorite(id: Long) = editConfiguration { config ->
  val favorites = config.favorites.filterNot { it.id == id }
  config.copy(favorites = favorites, rules = config.rules.filterNot { it.channelId == id },
   channel = if (config.channel?.id == id) favorites.firstOrNull() else config.channel)
 }
 suspend fun selectFavorite(id: Long) = editConfiguration { config ->
  config.copy(channel = requireNotNull(config.favorites.firstOrNull { it.id == id }) { Words.get(R.string.choose_a_favorite_channel) })
 }
 suspend fun updateFavoriteTitles(channels: List<Channel>) = editConfiguration { config ->
  val byId = channels.associateBy { it.id }
  config.copy(favorites = config.favorites.map { byId[it.id] ?: it }, channel = config.channel?.let { byId[it.id] ?: it })
 }
}
