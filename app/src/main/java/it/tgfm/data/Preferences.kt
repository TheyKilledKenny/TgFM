package it.tgfm.data

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
 fun encode(r: Rule) = JSONObject().put("id", r.id).put("title", r.title).put("enabled", r.enabled).put("exclude", r.exclude).put("template", r.template).put("contains", r.contains).put("extensions", JSONArray(r.extensions.toList())).put("mime", r.mime).put("category", r.category?.name ?: "")
 fun decode(j: JSONObject) = Rule(j.getString("id"), j.getString("title"), j.optBoolean("enabled", true), j.optBoolean("exclude"), j.optString("template"), j.optString("contains"), j.optJSONArray("extensions").strings().toSet(), j.optString("mime"), Category.entries.firstOrNull { it.name == j.optString("category") })
}
fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }
class Preferences(private val context: Context) {
 private val rulesKey = stringPreferencesKey("rules")
 private val channelKey = stringPreferencesKey("channel")
 private val clearDownloadsKey = booleanPreferencesKey("clear_before_download")
 val clearBeforeDownload = context.store.data.map { it[clearDownloadsKey] ?: false }
 suspend fun saveClearBeforeDownload(value: Boolean) { context.store.edit { it[clearDownloadsKey] = value } }
 private val showAllKey = booleanPreferencesKey("show_all")
 val showAll = context.store.data.map { it[showAllKey] ?: true }
 suspend fun saveShowAll(value: Boolean) { context.store.edit { it[showAllKey] = value } }
 val rules = context.store.data.map { prefs -> JSONArray(prefs[rulesKey] ?: "[]").let { a -> (0 until a.length()).map { RuleJson.decode(a.getJSONObject(it)) } } }
 val channel = context.store.data.map { prefs -> prefs[channelKey]?.let { JSONObject(it) }?.let { Channel(it.getLong("id"), it.getString("title")) } }.distinctUntilChanged()
 suspend fun saveRules(rules: List<Rule>) { context.store.edit { it[rulesKey] = JSONArray(rules.map(RuleJson::encode)).toString() } }
 suspend fun saveChannel(channel: Channel?) { context.store.edit { if (channel == null) it.remove(channelKey) else it[channelKey] = JSONObject().put("id", channel.id).put("title", channel.title).toString() } }
}
