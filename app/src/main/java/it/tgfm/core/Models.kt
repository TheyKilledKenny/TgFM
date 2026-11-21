package it.tgfm.core

import it.tgfm.R
import it.tgfm.Words

import java.util.UUID

enum class Category(private val labelId: Int) { DOCUMENT(R.string.document), IMAGE(R.string.image), VIDEO(R.string.video), AUDIO(R.string.audio), STICKER(R.string.sticker), ANIMATION(R.string.animation), OTHER(R.string.other);
 val label: String get() = Words.get(labelId)
}
enum class Match(private val labelId: Int) { CERTAIN(R.string.exact_match), POSSIBLE(R.string.possible_match), NONE(R.string.no_match), EXCLUDED(R.string.excluded);
 val label: String get() = Words.get(labelId)
}
data class Attachment(val chatId: Long, val messageId: Long, val fileId: Int, val name: String, val mime: String = "", val size: Long = 0, val date: Long = 0, val caption: String = "", val category: Category = Category.DOCUMENT, val parts: List<Attachment> = emptyList(), val cover: CoverImage? = null) {
 val key: String get() = "$chatId:$messageId:$fileId"
 val extension: String get() = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
}
data class Rule(val id: String = UUID.randomUUID().toString(), val title: String = "", val enabled: Boolean = true, val exclude: Boolean = false, val template: String = "", val contains: String = "", val extensions: Set<String> = emptySet(), val mime: String = "", val category: Category? = null, val channelId: Long? = null)
data class Channel(val id: Long, val title: String)
data class Downloaded(val path: String, val originalName: String, val mime: String, val size: Long, val sourceKey: String = "")

data class ChannelConfiguration(val favorites: List<Channel> = emptyList(), val channel: Channel? = null, val rules: List<Rule> = emptyList())
