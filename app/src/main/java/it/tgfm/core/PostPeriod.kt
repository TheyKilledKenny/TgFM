package it.tgfm.core

import it.tgfm.R
import it.tgfm.Words

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class PostPeriodMode(private val labelId: Int) {
 TODAY(R.string.today), ALL(R.string.all_files), DAY(R.string.specific_day), RANGE(R.string.date_range);
 val label: String get() = Words.get(labelId)
}

/** The end is exclusive: calendar days can last 23 or 25 hours in the device's zone. */
data class PostWindow(val start: Long? = null, val endExclusive: Long? = null) {
 fun contains(date: Long) = (start == null || date >= start) && (endExclusive == null || date < endExclusive)
 fun isBefore(date: Long) = start != null && date < start
}

data class PostPeriod(
 val mode: PostPeriodMode = PostPeriodMode.TODAY,
 val first: LocalDate = LocalDate.now(),
 val last: LocalDate = first
) {
 fun description(today: LocalDate = LocalDate.now()): String {
  val format = DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.SHORT).withLocale(Words.locale)
  return when (mode) {
   PostPeriodMode.TODAY -> Words.get(R.string.today_detail, today.format(format))
   PostPeriodMode.ALL -> Words.get(R.string.all_files)
   PostPeriodMode.DAY -> first.format(format)
   PostPeriodMode.RANGE -> "${first.format(format)} – ${last.format(format)}"
  }
 }
 fun window(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): PostWindow {
  if (mode == PostPeriodMode.ALL) return PostWindow()
  val from = if (mode == PostPeriodMode.TODAY) today else first
  val through = if (mode == PostPeriodMode.RANGE) last else from
  require(!through.isBefore(from)) { Words.get(R.string.the_end_date_must_be_on_or) }
  val start = from.atStartOfDay(zone).toEpochSecond()
  val end = through.plusDays(1).atStartOfDay(zone).toEpochSecond()
  require(start >= 0 && end - 1 <= Int.MAX_VALUE) { Words.get(R.string.choose_dates_between_1970_and_2037) }
  return PostWindow(start, end)
 }
 fun error(): String? = try { window(); null } catch (e: IllegalArgumentException) { e.message }
}
