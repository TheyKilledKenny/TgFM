package it.tgfm.core

import java.util.UUID

enum class Category(val label: String) { DOCUMENT("Documento"), IMAGE("Immagine"), VIDEO("Video"), AUDIO("Audio"), STICKER("Sticker"), ANIMATION("Animazione"), OTHER("Altro") }
enum class Match(val label: String) { CERTAIN("Certa"), POSSIBLE("Possibile"), NONE("Non corrispondente"), EXCLUDED("Escluso") }
data class Attachment(val chatId: Long, val messageId: Long, val fileId: Int, val name: String, val mime: String = "", val size: Long = 0, val date: Long = 0, val caption: String = "", val category: Category = Category.DOCUMENT) {
 val key: String get() = "$chatId:$messageId:$fileId"
 val extension: String get() = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
}
data class Rule(val id: String = UUID.randomUUID().toString(), val title: String = "", val enabled: Boolean = true, val exclude: Boolean = false, val template: String = "", val contains: String = "", val extensions: Set<String> = emptySet(), val mime: String = "", val category: Category? = null)
data class Channel(val id: Long, val title: String)
data class Downloaded(val path: String, val originalName: String, val mime: String, val size: Long, val sourceKey: String = "")
