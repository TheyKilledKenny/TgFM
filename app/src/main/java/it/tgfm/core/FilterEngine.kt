package it.tgfm.core

import it.tgfm.R
import it.tgfm.Words

import java.util.Locale

/** All criteria within a rule are ANDed; rules are ORed. Exclusions always win. */
object FilterEngine {
 private const val MONTH = "(?:gen(?:naio)?|feb(?:braio)?|mar(?:zo)?|apr(?:ile)?|mag(?:gio)?|giu(?:gno)?|lug(?:lio)?|ago(?:sto)?|set(?:tembre)?|ott(?:obre)?|nov(?:embre)?|dic(?:embre)?|jan(?:uary)?|february|march|april|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|november|dec(?:ember)?)"
 private const val SEP = "[_. -]"
 private const val DATE = "(?:[0-9]{1,2}${SEP}?${MONTH}(?:${SEP}?[0-9]{4}|[0-9]{2})?|[0-9]{4}${SEP}[0-9]{2}${SEP}[0-9]{2}|[0-9]{1,2}${SEP}[0-9]{1,2}${SEP}[0-9]{4}|[0-9]{8})"
 private fun norm(s: String) = s.trim().lowercase(Locale.ROOT)
 fun validate(r: Rule): String? = when {
  r.title.isBlank() -> Words.get(R.string.enter_a_rule_name)
  r.template.isBlank() && r.contains.isBlank() && r.extensions.isEmpty() && r.mime.isBlank() && r.category == null -> Words.get(R.string.specify_at_least_one_criterion)
  r.template.replace("{data}", "").let { '{' in it || '}' in it } -> Words.get(R.string.the_only_supported_placeholder_is_data)
  r.mime.isNotBlank() && !Regex("[\\w.+-]+/(?:[\\w.+-]+|\\*)").matches(r.mime) -> Words.get(R.string.invalid_mime_type_try_application_pdf_or)
  else -> null
 }
 fun templateMatches(template: String, name: String): Boolean {
  val pattern = FilterEditing.nameOnly(template).split("{data}").joinToString(DATE) { Regex.escape(it) }
  return Regex("^$pattern$", RegexOption.IGNORE_CASE).matches(FilterEditing.fileNameOnly(name))
 }
 private fun typeMatches(r: Rule, a: Attachment): Boolean =
  (r.extensions.isEmpty() || a.extension in r.extensions.map { norm(it).removePrefix(".") }) &&
  (r.category == null || r.category == a.category) &&
  (r.mime.isBlank() || if (r.mime.endsWith("/*")) norm(a.mime).startsWith(norm(r.mime).dropLast(1)) else norm(r.mime) == norm(a.mime))
 private fun matches(r: Rule, a: Attachment): Boolean = validate(r) == null && typeMatches(r, a) &&
  (r.template.isBlank() || templateMatches(r.template, a.name)) &&
  (r.contains.isBlank() || norm(FilterEditing.fileNameOnly(a.name)).contains(norm(r.contains)))
 fun classify(a: Attachment, rules: List<Rule>): Match {
  val active = rules.filter { it.enabled && (it.channelId == null || it.channelId == a.chatId) }
  if (active.any { it.exclude && matches(it, a) }) return Match.EXCLUDED
  val include = active.filterNot { it.exclude }
  if (include.any { matches(it, a) }) return Match.CERTAIN
  if (include.any { r ->
    validate(r) == null && typeMatches(r, a) && when {
     r.template.isNotBlank() -> {
      val stem = norm(r.template).substringBefore("{data}").substringBeforeLast('.', norm(r.template).substringBefore("{data}")).trim('_', '-', ' ')
      stem.length >= 4 && (norm(a.name).contains(stem) || similarity(stem, norm(a.name).take(stem.length)) >= 0.8)
     }
     r.contains.length >= 4 -> similarity(norm(r.contains), norm(a.name).substringBeforeLast('.')) >= 0.75
     else -> false
    }
   }) return Match.POSSIBLE
  return Match.NONE
 }
 private fun similarity(a: String, b: String): Double {
  if (a.isEmpty() || b.isEmpty()) return 0.0
  var row = IntArray(b.length + 1) { it }
  a.forEachIndexed { i, ca ->
   val next = IntArray(b.length + 1); next[0] = i + 1
   b.forEachIndexed { j, cb -> next[j + 1] = minOf(next[j] + 1, row[j + 1] + 1, row[j] + if (ca == cb) 0 else 1) }
   row = next
  }
  return 1.0 - row.last().toDouble() / maxOf(a.length, b.length)
 }
 /** Proposals are drafts, never persisted implicitly. */
 fun suggest(a: Attachment, exclude: Boolean): Rule {
  val date = Regex("(?<![A-Za-zÀ-ÿ0-9])$DATE(?![A-Za-zÀ-ÿ0-9])", RegexOption.IGNORE_CASE)
  val stem = FilterEditing.fileNameOnly(a.name)
  val proposed = date.replace(stem, "{data}")
  return Rule(title = (if (exclude) Words.get(R.string.exclude) else Words.get(R.string.include)) + stem, exclude = exclude, template = proposed, extensions = emptySet(), category = a.category)
 }

 /** Group labelled examples by the complete stable name around dates. Never widen to a
  * substring that would accept unlabelled prefixes, city editions or negative examples. */
 fun learn(desired: List<Attachment>, unwanted: List<Attachment>): LearningResult {
  val conflicts = desired.map { it.key }.toSet().intersect(unwanted.map { it.key }.toSet())
  if (conflicts.isNotEmpty()) return LearningResult(emptyList(), listOf(Words.get(R.string.an_example_is_marked_as_both_wanted)))
  val warnings = mutableListOf<String>()
  val drafts = mutableListOf<Rule>()
  // With positive examples, negatives constrain the inclusions rather than becoming
  // one exclusion per variant. Negative-only training can propose grouped exclusions.
  val labelledGroups = if (desired.isNotEmpty()) listOf(desired to false) else listOf(unwanted to true)
  for ((examples, exclude) in labelledGroups) {
   val groups = examples.groupBy { a -> val r = suggest(a, exclude); Triple(r.template.lowercase(Locale.ROOT), r.extensions, r.category) }
   for ((_, group) in groups) {
    val rule = suggest(group.first(), exclude)
    if (!rule.template.contains("{data}")) {
     warnings += Words.get(R.string.no_variable_part_found_in_create_or, group.first().name)
     continue
    }
    val opposite = if (exclude) desired else unwanted
    val overlaps = opposite.filter { classify(it, listOf(rule)) in setOf(Match.CERTAIN, Match.EXCLUDED) }
    if (overlaps.isNotEmpty()) {
     warnings += Words.get(R.string.ambiguous_pattern_also_matches_examples_with_the, rule.template)
     continue
    }
    drafts += rule.copy(title = (if (exclude) Words.get(R.string.exclude) else Words.get(R.string.include)) + rule.template)
   }
  }
  return LearningResult(drafts, warnings)
 }
 fun sameCriteria(a: Rule, b: Rule): Boolean =
  a.copy(id = "", title = "", template = FilterEditing.nameOnly(a.template)) ==
   b.copy(id = "", title = "", template = FilterEditing.nameOnly(b.template))
}

data class LearningResult(val rules: List<Rule>, val warnings: List<String>)
