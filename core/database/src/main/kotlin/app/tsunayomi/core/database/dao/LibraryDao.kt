package app.tsunayomi.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import app.tsunayomi.core.database.entity.ChapterEntity
import app.tsunayomi.core.database.entity.MangaEntity
import kotlinx.coroutines.flow.Flow

/** A library entry with the counters the library screen sorts and badges by. */
data class LibraryMangaRow(
    @Embedded val manga: MangaEntity,
    @ColumnInfo(name = "unread_count") val unreadCount: Int,
    @ColumnInfo(name = "total_count") val totalCount: Int,
    @ColumnInfo(name = "last_read_at") val lastReadAt: Long?,
)

/** The newest history entry of one manga. */
data class HistoryRow(
    @ColumnInfo(name = "manga_id") val mangaId: Long,
    @ColumnInfo(name = "source_id") val sourceId: Long,
    val title: String,
    @ColumnInfo(name = "cover_url") val coverUrl: String?,
    @ColumnInfo(name = "chapter_id") val chapterId: Long,
    @ColumnInfo(name = "chapter_name") val chapterName: String,
    @ColumnInfo(name = "chapter_read") val chapterRead: Boolean,
    @ColumnInfo(name = "last_page_read") val lastPageRead: Int,
    @ColumnInfo(name = "last_read_at") val lastReadAt: Long,
)

/** A chapter found by a library update, with what the Updates tab shows about its title. */
data class UpdateRow(
    @Embedded val chapter: ChapterEntity,
    @ColumnInfo(name = "manga_title") val mangaTitle: String,
    @ColumnInfo(name = "manga_cover_url") val mangaCoverUrl: String?,
    @ColumnInfo(name = "manga_source_id") val mangaSourceId: Long,
)

@Dao
interface LibraryDao {
    @Query(
        """
        SELECT c.*, m.title AS manga_title, m.cover_url AS manga_cover_url, m.source_id AS manga_source_id
        FROM chapter c JOIN manga m ON m.id = c.manga_id
        WHERE m.favorite = 1 AND c.fetched_at > 0
        ORDER BY c.fetched_at DESC, c.source_order DESC
        LIMIT 500
        """,
    )
    fun observeUpdates(): Flow<List<UpdateRow>>

    @Query("SELECT id FROM manga WHERE favorite = 1 ORDER BY title COLLATE NOCASE")
    suspend fun favoriteIds(): List<Long>

    @Query(
        """
        SELECT m.*,
            (SELECT COUNT(*) FROM chapter c WHERE c.manga_id = m.id AND c.read = 0) AS unread_count,
            (SELECT COUNT(*) FROM chapter c WHERE c.manga_id = m.id) AS total_count,
            (SELECT MAX(h.last_read_at) FROM history h JOIN chapter c ON c.id = h.chapter_id
                WHERE c.manga_id = m.id) AS last_read_at
        FROM manga m
        WHERE m.favorite = 1
        """,
    )
    fun observeLibrary(): Flow<List<LibraryMangaRow>>

    @Query(
        """
        SELECT m.id AS manga_id, m.source_id, m.title, m.cover_url, c.id AS chapter_id, c.name AS chapter_name,
            c.read AS chapter_read, c.last_page_read, h.last_read_at
        FROM history h
        JOIN chapter c ON c.id = h.chapter_id
        JOIN manga m ON m.id = c.manga_id
        WHERE h.last_read_at = (
            SELECT MAX(h2.last_read_at) FROM history h2 JOIN chapter c2 ON c2.id = h2.chapter_id
            WHERE c2.manga_id = m.id
        )
        GROUP BY m.id
        ORDER BY h.last_read_at DESC
        """,
    )
    fun observeHistory(): Flow<List<HistoryRow>>

    @Query("DELETE FROM history WHERE chapter_id IN (SELECT id FROM chapter WHERE manga_id = :mangaId)")
    suspend fun deleteHistoryForManga(mangaId: Long)

    @Query("DELETE FROM history")
    suspend fun clearHistory()
}
