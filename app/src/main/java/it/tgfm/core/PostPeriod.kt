package it.tgfm.core

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class PostPeriodMode(val label: String) {
 TODAY("Oggi"), ALL("Tutti i file"), DAY("Un giorno preciso"), RANGE("Intervallo di date")
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
  val format = DateTimeFormatter.ofPattern("dd/MM/yyyy")
  return when (mode) {
   PostPeriodMode.TODAY -> "Oggi (${today.format(format)})"
   PostPeriodMode.ALL -> "Tutti i file"
   PostPeriodMode.DAY -> first.format(format)
   PostPeriodMode.RANGE -> "${first.format(format)} – ${last.format(format)}"
  }
 }
 fun window(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): PostWindow {
  if (mode == PostPeriodMode.ALL) return PostWindow()
  val from = if (mode == PostPeriodMode.TODAY) today else first
  val through = if (mode == PostPeriodMode.RANGE) last else from
  require(!through.isBefore(from)) { "La data finale deve essere uguale o successiva a quella iniziale." }
  val start = from.atStartOfDay(zone).toEpochSecond()
  val end = through.plusDays(1).atStartOfDay(zone).toEpochSecond()
  require(start >= 0 && end - 1 <= Int.MAX_VALUE) { "Scegli date comprese tra il 1970 e il 2037." }
  return PostWindow(start, end)
 }
 fun error(): String? = try { window(); null } catch (e: IllegalArgumentException) { e.message }
}
