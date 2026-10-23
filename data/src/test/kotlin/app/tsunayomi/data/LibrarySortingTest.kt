package app.tsunayomi.data

import app.tsunayomi.core.model.LibraryManga
import app.tsunayomi.core.model.LibrarySort
import app.tsunayomi.core.model.Manga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LibrarySortingTest {
    private fun item(title: String, unread: Int = 0, lastRead: Long? = null, added: Long = 0, author: String? = null) =
        LibraryManga(
            manga = Manga(id = title.hashCode().toLong(), sourceId = 0, url = title, title = title, coverUrl = null, author = author, dateAdded = added),
            unreadCount = unread,
            lastReadAt = lastRead,
            categoryIds = emptySet(),
        )

    private val items = listOf(
        item("beta", unread = 5, lastRead = 100, added = 3),
        item("Alpha", unread = 0, lastRead = null, added = 2, author = "Someone"),
        item("gamma", unread = 2, lastRead = 300, added = 1),
    )

    private fun titles(list: List<LibraryManga>) = list.map { it.manga.title }

    @Test
    fun `title sort ignores case`() {
        assertEquals(listOf("Alpha", "beta", "gamma"), titles(LibrarySorting.sort(items, LibrarySort.Title, ascending = true)))
    }

    @Test
    fun `last read descending puts never-read last`() {
        assertEquals(listOf("gamma", "beta", "Alpha"), titles(LibrarySorting.sort(items, LibrarySort.LastRead, ascending = false)))
    }

    @Test
    fun `unread and date added`() {
        assertEquals(listOf("beta", "gamma", "Alpha"), titles(LibrarySorting.sort(items, LibrarySort.Unread, ascending = false)))
        assertEquals(listOf("gamma", "Alpha", "beta"), titles(LibrarySorting.sort(items, LibrarySort.DateAdded, ascending = true)))
    }

    @Test
    fun `filter matches title and author`() {
        assertEquals(listOf("Alpha"), titles(LibrarySorting.filter(items, "some")))
        assertEquals(listOf("gamma"), titles(LibrarySorting.filter(items, " GAM ")))
        assertEquals(3, LibrarySorting.filter(items, "").size)
    }
}
