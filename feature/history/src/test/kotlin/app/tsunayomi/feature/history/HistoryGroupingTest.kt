package app.tsunayomi.feature.history

import app.tsunayomi.core.model.HistoryItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class HistoryGroupingTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 10, 23)

    private fun item(id: Long, at: LocalDateTime) = HistoryItem(
        mangaId = id, sourceId = 0, title = "M$id", coverUrl = null, chapterId = id, chapterName = "Ch",
        chapterRead = false, lastPageRead = 0, lastReadAt = at.atZone(zone).toInstant().toEpochMilli(),
    )

    @Test
    fun `groups into today, yesterday and dates in order`() {
        val items = listOf(
            item(1, LocalDateTime.of(2026, 10, 23, 9, 0)),
            item(2, LocalDateTime.of(2026, 10, 23, 0, 5)),
            item(3, LocalDateTime.of(2026, 10, 22, 23, 59)),
            item(4, LocalDateTime.of(2026, 10, 1, 12, 0)),
        )

        val groups = groupByDay(items, today, zone)

        assertEquals(
            listOf(DayBucket.Today, DayBucket.Yesterday, DayBucket.Date(LocalDate.of(2026, 10, 1))),
            groups.map { it.first },
        )
        assertEquals(listOf(1L, 2L), groups[0].second.map { it.mangaId })
    }
}
