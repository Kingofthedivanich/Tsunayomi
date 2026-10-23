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

/** A new chapter in the Updates tab. */
data class UpdateItem(
    val chapter: Chapter,
    val mangaTitle: String,
    val mangaCoverUrl: String?,
    val fetchedAt: Long,
)

enum class DownloadState { Queued, Downloading, Translating, Done, Failed }

data class ChapterDownload(
    val chapterId: Long,
    val state: DownloadState,
    val pagesDone: Int,
    val pagesTotal: Int,
    /** For [DownloadState.Failed]; for [DownloadState.Done], a non-fatal note (e.g. translation could not run). */
    val error: String?,
)

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
