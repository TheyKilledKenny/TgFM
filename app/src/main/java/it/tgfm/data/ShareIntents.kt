package it.tgfm.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import it.tgfm.core.Downloaded
import it.tgfm.core.commonMime
import java.io.File

object ShareIntents {
 fun build(context: Context, files: List<Downloaded>, open: Boolean = false, contents: Boolean = false): Intent {
  require(files.isNotEmpty()) { "Seleziona almeno un file." }
  val mimeTypes = mutableListOf<String>()
  val seen = mutableSetOf<String>()
  val uris = files.flatMap { file ->
   check(File(file.path).isFile) { "File non disponibile: ${file.originalName}" }
   val source = File(file.path)
   if (contents && !open && it.tgfm.core.ArchiveNames.isArchive(source.name)) {
    val volumes = ArchiveReader.volumes(source)
    if (!seen.add(volumes.joinToString { it.absolutePath })) emptyList() else {
     ArchiveReader.entries(volumes).map { entry ->
      mimeTypes += ArchiveProvider.mime(entry.name)
      ArchiveProvider.uri(context, volumes, entry)
     }
    }
   } else if (!open && it.tgfm.core.ArchiveNames.isArchive(source.name)) {
    val volumes = ArchiveReader.volumes(source)
    if (!seen.add(volumes.joinToString { it.absolutePath })) emptyList() else volumes.map { volume ->
     mimeTypes += ArchiveProvider.mime(volume.name)
     WorkFileProvider.uri(context, volume)
    }
   } else { mimeTypes += file.mime; listOf(WorkFileProvider.uri(context, source)) }
  }
  require(uris.isNotEmpty()) { "L’archivio non contiene file condivisibili." }
  return Intent(if (open) Intent.ACTION_VIEW else if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
   type = commonMime(mimeTypes)
   if (open) setDataAndType(uris.first(), type)
   else if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first())
   else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
   clipData = ClipData.newRawUri("File TgFM", uris.first()).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
   addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
 }
}
