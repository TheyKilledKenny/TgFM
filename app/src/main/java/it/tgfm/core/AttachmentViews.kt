package it.tgfm.core

import it.tgfm.R
import it.tgfm.Words

enum class AttachmentView(private val labelId: Int) {
 SELECTED(R.string.show_selected_only),
 SELECTED_AND_SIMILAR(R.string.show_selected_and_similar_files),
 ALL(R.string.show_all_files);
 val label: String get() = Words.get(labelId)
 companion object {
  fun fromStored(value: String?, legacyShowAll: Boolean? = null): AttachmentView =
   entries.firstOrNull { it.name == value } ?: if (legacyShowAll == false) SELECTED_AND_SIMILAR else ALL
 }
}

object AttachmentViews {
 /** Use actual checkboxes, including manual selections of explicitly excluded files.
  * Keep the channel order within both groups; selected files always come first. */
 fun visible(files: List<Attachment>, selected: Set<String>, rules: List<Rule>, view: AttachmentView): List<Attachment> {
  val (checked, unchecked) = files.partition { it.key in selected }
  return checked + when (view) {
   AttachmentView.SELECTED -> emptyList()
   AttachmentView.SELECTED_AND_SIMILAR -> unchecked.filter { FilterEngine.classify(it, rules) == Match.POSSIBLE }
   AttachmentView.ALL -> unchecked
  }
 }

 /** Predict the automatic selection after saving rules. Similar/excluded files are not selected. */
 fun preview(files: List<Attachment>, rules: List<Rule>): List<Attachment> =
  files.filter { FilterEngine.classify(it, rules) == Match.CERTAIN }
}
