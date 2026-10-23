package app.tsunayomi.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.tsunayomi.core.database.entity.ChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ChapterDao {
    @Query("SELECT * FROM chapter WHERE manga_id = :mangaId ORDER BY source_order")
    abstract fun observeByManga(mangaId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapter WHERE manga_id = :mangaId ORDER BY source_order")
    abstract suspend fun getByManga(mangaId: Long): List<ChapterEntity>

    @Query("SELECT * FROM chapter WHERE id = :id")
    abstract suspend fun getById(id: Long): ChapterEntity?

    @Query("UPDATE chapter SET read = :read, last_page_read = :lastPageRead WHERE id = :id")
    abstract suspend fun updateProgress(id: Long, read: Boolean, lastPageRead: Int)

    @Query("UPDATE chapter SET read = :read, last_page_read = 0 WHERE id IN (:ids)")
    abstract suspend fun setRead(ids: List<Long>, read: Boolean)

    @Insert
    protected abstract suspend fun insert(chapters: List<ChapterEntity>): List<Long>

    @Update
    protected abstract suspend fun update(chapters: List<ChapterEntity>)

    @Query("DELETE FROM chapter WHERE id IN (:ids)")
    protected abstract suspend fun delete(ids: List<Long>)

    /**
     * Replaces the chapter list with [incoming] (matched by URL) while keeping read state and progress.
     * Chapters that disappeared from the source are removed together with their history.
     * Returns the ids of chapters that are new; they are stamped with [now] unless this is the title's first list.
     */
    @Transaction
    open suspend fun sync(mangaId: Long, incoming: List<ChapterEntity>, now: Long = System.currentTimeMillis()): List<Long> {
        val existing = getByManga(mangaId).associateBy { it.url }
        val firstList = existing.isEmpty()
        val incomingUrls = incoming.mapTo(HashSet()) { it.url }
        delete(existing.values.filter { it.url !in incomingUrls }.map { it.id })

        val (known, fresh) = incoming.partition { it.url in existing }
        update(
            known.map { chapter ->
                val old = existing.getValue(chapter.url)
                chapter.copy(
                    id = old.id,
                    read = old.read,
                    lastPageRead = old.lastPageRead,
                    bookmark = old.bookmark,
                    fetchedAt = old.fetchedAt,
                )
            },
        )
        return insert(fresh.map { it.copy(fetchedAt = if (firstList) 0 else now) })
    }
}
