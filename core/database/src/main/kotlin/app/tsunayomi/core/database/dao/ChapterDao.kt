package app.tsunayomi.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.tsunayomi.core.database.entity.ChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapter WHERE manga_id = :mangaId ORDER BY source_order")
    fun observeByManga(mangaId: Long): Flow<List<ChapterEntity>>

    @Upsert
    suspend fun upsert(chapters: List<ChapterEntity>)

    @Query("UPDATE chapter SET read = :read, last_page_read = :lastPageRead WHERE id = :id")
    suspend fun updateProgress(id: Long, read: Boolean, lastPageRead: Int)
}
