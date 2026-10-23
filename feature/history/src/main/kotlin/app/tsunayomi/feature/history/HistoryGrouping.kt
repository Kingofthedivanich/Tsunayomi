package app.tsunayomi.feature.history

import app.tsunayomi.core.model.HistoryItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal sealed interface DayBucket {
    data object Today : DayBucket
    data object Yesterday : DayBucket
    data class Date(val date: LocalDate) : DayBucket
}

/** Groups newest-first history into days in [zone], keeping the order. */
internal fun groupByDay(items: List<HistoryItem>, today: LocalDate, zone: ZoneId): List<Pair<DayBucket, List<HistoryItem>>> =
    items.groupBy { item ->
        val date = Instant.ofEpochMilli(item.lastReadAt).atZone(zone).toLocalDate()
        when (date) {
            today -> DayBucket.Today
            today.minusDays(1) -> DayBucket.Yesterday
            else -> DayBucket.Date(date)
        }
    }.toList()
