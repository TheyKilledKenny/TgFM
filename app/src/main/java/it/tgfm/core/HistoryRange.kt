package it.tgfm.core

/** Half-open history intervals. IDs distinguish messages posted in the same second. */
data class HistoryPoint(val date: Long, val id: Long = 0) : Comparable<HistoryPoint> {
 override fun compareTo(other: HistoryPoint) = compareValuesBy(this, other, HistoryPoint::date, HistoryPoint::id)
}

data class HistoryRange(val start: HistoryPoint, val end: HistoryPoint) {
 init { require(start < end) }
 operator fun contains(point: HistoryPoint) = point >= start && point < end
 fun minus(other: HistoryRange): List<HistoryRange> {
  if (other.end <= start || end <= other.start) return listOf(this)
  return buildList {
   if (start < other.start) add(HistoryRange(start, other.start))
   if (other.end < end) add(HistoryRange(other.end, end))
  }
 }
 fun missing(covered: List<HistoryRange>) =
  covered.fold(listOf(this)) { gaps, span -> gaps.flatMap { it.minus(span) } }
}

fun mergeHistory(ranges: List<HistoryRange>): List<HistoryRange> = buildList {
 for (span in ranges.sortedBy { it.start }) {
  val last = lastOrNull()
  if (last == null || last.end < span.start) add(span)
  else if (last.end < span.end) this[lastIndex] = HistoryRange(last.start, span.end)
 }
}
