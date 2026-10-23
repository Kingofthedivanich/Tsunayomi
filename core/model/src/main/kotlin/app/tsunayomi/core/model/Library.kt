package app.tsunayomi.core.model

data class LibraryManga(
    val manga: Manga,
    val unreadCount: Int,
    /** Last time any chapter was read, or null if never opened. */
    val lastReadAt: Long?,
    val categoryIds: Set<Long>,
)

enum class LibrarySort {
    Title,
    LastRead,
    DateAdded,
    Unread,
}

data class HistoryItem(
    val mangaId: Long,
    val sourceId: Long,
    val title: String,
    val coverUrl: String?,
    val chapterId: Long,
    val chapterName: String,
    val chapterRead: Boolean,
    val lastPageRead: Int,
    val lastReadAt: Long,
)
