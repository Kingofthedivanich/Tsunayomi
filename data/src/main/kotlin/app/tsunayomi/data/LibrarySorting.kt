package app.tsunayomi.data

import app.tsunayomi.core.model.LibraryManga
import app.tsunayomi.core.model.LibrarySort

object LibrarySorting {
    fun sort(items: List<LibraryManga>, sort: LibrarySort, ascending: Boolean): List<LibraryManga> {
        val byTitle = compareBy<LibraryManga, String>(String.CASE_INSENSITIVE_ORDER) { it.manga.title }
        val comparator = when (sort) {
            LibrarySort.Title -> byTitle
            // Never-read entries count as oldest.
            LibrarySort.LastRead -> compareBy<LibraryManga> { it.lastReadAt ?: 0L }.then(byTitle)
            LibrarySort.DateAdded -> compareBy<LibraryManga> { it.manga.dateAdded }.then(byTitle)
            LibrarySort.Unread -> compareBy<LibraryManga> { it.unreadCount }.then(byTitle)
        }
        return items.sortedWith(if (ascending) comparator else comparator.reversed())
    }

    /** Case-insensitive match on title or author. */
    fun filter(items: List<LibraryManga>, query: String): List<LibraryManga> {
        val q = query.trim()
        if (q.isEmpty()) return items
        return items.filter {
            it.manga.title.contains(q, ignoreCase = true) || it.manga.author?.contains(q, ignoreCase = true) == true
        }
    }
}
