package it.tgfm

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** Resource context shared by the UI and background transfers. */
object Words {
 private lateinit var app: Context
 @Volatile private lateinit var localized: Context
 val locale: Locale get() = localized.resources.configuration.locales[0]

 fun initialize(context: Context) {
  app = context
  refresh()
 }

 fun preference(context: Context): String = if (Build.VERSION.SDK_INT >= 33) {
  context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags().substringBefore(',')
 } else context.getSharedPreferences("language", Context.MODE_PRIVATE).getString("tag", "").orEmpty()

 fun contextFor(context: Context): Context {
  val override = preference(context)
  val system = if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java).systemLocales
   else Resources.getSystem().configuration.locales
  val locale = resolve(override, (0 until system.size()).map { system[it] })
  val config = Configuration(context.resources.configuration)
  config.setLocale(locale)
  return context.createConfigurationContext(config)
 }

 internal fun resolve(override: String, system: List<Locale>): Locale {
  val requested = if (override.isBlank()) system else listOf(Locale.forLanguageTag(override))
  return requested.firstOrNull { it.language == "it" || it.language == "en" } ?: Locale.ENGLISH
 }

 fun refresh() { localized = contextFor(app) }

 fun choose(activity: Activity, tag: String) {
  if (preference(activity) == tag) return
  if (Build.VERSION.SDK_INT >= 33) {
   activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
  } else {
   activity.getSharedPreferences("language", Context.MODE_PRIVATE).edit().putString("tag", tag).apply()
   activity.recreate()
  }
  refresh()
 }

 fun count(id: Int, quantity: Int): String = localized.resources.getQuantityString(id, quantity, quantity)

 fun get(id: Int, vararg args: Any?): String =
  if (args.isEmpty()) localized.getString(id) else localized.getString(id, *args)
}
