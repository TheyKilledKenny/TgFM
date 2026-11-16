package it.tgfm.telegram

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

/** TDLib queues pre-auth options until setTdlibParameters opens the database. */
class TelegramInitialization(private val send: suspend (JSONObject) -> JSONObject) {
 suspend fun initialize(parameters: JSONObject) = coroutineScope {
  // Enqueue before parameters so TDLib applies them before creating its file managers.
  // Do NOT await either reply here: those replies depend on setTdlibParameters.
  val storage = async(start = CoroutineStart.UNDISPATCHED) {
   send(request("setOption", "name" to "store_all_files_in_files_directory", "value" to request("optionValueBoolean", "value" to true)))
  }
  val optimizer = async(start = CoroutineStart.UNDISPATCHED) {
   send(request("setOption", "name" to "use_storage_optimizer", "value" to request("optionValueBoolean", "value" to false)))
  }
  send(parameters)
  storage.await()
  optimizer.await()
  send(request("setOption", "name" to "ignore_file_names", "value" to request("optionValueBoolean", "value" to false)))
  Unit
 }
}
