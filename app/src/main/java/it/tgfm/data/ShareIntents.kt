package it.tgfm.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import it.tgfm.core.Downloaded
import it.tgfm.core.commonMime
import java.io.File

object ShareIntents {
 fun build(context: Context, files: List<Downloaded>, open: Boolean = false): Intent {
  require(files.isNotEmpty()) { "Seleziona almeno un file." }
  val uris = files.map { file ->
   check(File(file.path).isFile) { "File non disponibile: ${file.originalName}" }
   WorkFileProvider.uri(context, File(file.path))
  }
  return Intent(if (open) Intent.ACTION_VIEW else if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
   type = commonMime(files.map { it.mime })
   if (open) setDataAndType(uris.first(), type)
   else if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first())
   else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
   clipData = ClipData.newRawUri("File TgFM", uris.first()).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
   addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
 }
}
